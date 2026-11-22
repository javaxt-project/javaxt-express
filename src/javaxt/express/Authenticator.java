package javaxt.express;

import java.util.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import javaxt.http.servlet.*;
import javaxt.express.email.*;
import javaxt.json.JSONObject;


//******************************************************************************
//**  Authenticator
//******************************************************************************
/**
 *   This class is an implementation of a javaxt.http.servlet.Authenticator
 *   used to authenticate requests in a javaxt.http.servlet.HttpServlet.
 *   Instances of this class are passed to the setAuthenticator() method in
 *   the HttpServlet class. Once the HttpServlet has an Authenticator, it can
 *   be used to retrieve credentials, get user principle, etc. Example:
 *
    <pre>
    public class WebApp extends HttpServlet {
        public WebApp(){
            setAuthenticator(new javaxt.express.Authenticator());
        }

        public void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

            String[] credentials = getCredentials(); //Calls Authenticator!
            String username = credentials[0];
            String password = credentials[1];
        }
    }
    </pre>
 *
 *   The getCredentials() method provided by this class supports both "BASIC"
 *   and "NTLM" authentication methods.
 *
 *   The following snippit demonstrates how to perform "BASIC" authentication
 *   with a username and password. This example assumes that there is a User
 *   class that implements the java.security.Principal interface. The getUser()
 *   and setUser() methods in this example are used to update an internal cache.
 *   The cache is used to map a username to a User and is primarily used to
 *   help reduce database queries. By default, entries in the cache are
 *   invalidated after 30 seconds.
 *
    <pre>
        setAuthenticator(new javaxt.express.Authenticator(){

            public java.security.Principal getPrinciple(){

                User user = (User) getUser();
                if (user!=null) return user;

                try{

                    String[] credentials = getCredentials();
                    String username = credentials[0];
                    String password = credentials[1];

                    if (username!=null && password!=null){

                        //TODO: Find user in the database
                    }
                }
                catch(Exception e){
                }

                setUser(user);
                return user;
            }

        });
    </pre>
 *
 *   With "NTLM" authentication, only the username is returned by the
 *   getCredentials() method. The password will be null. This is because the
 *   user has already been authenticated by another provider (e.g. AD). All
 *   that's left to do is to map the username to a User/Principal via the
 *   getPrinciple() method.
 *
 *   Additional authentication methods can be added by overriding the
 *   getCredentials() method.
 *
 *   This class also includes a handleRequest() method that can be used to
 *   simplify authentication workflows used in JavaXT web applications.
 *
 ******************************************************************************/

public class Authenticator implements javaxt.http.servlet.Authenticator, Cloneable {


  //Local variables (per request)
    private String authType;
    private String authInfo;
    private String[] credentials;
    private HttpServletRequest request; //set in newInstance
    private long cacheGeneration = -1;  //see getUser and setUser
    private boolean cacheHit;           //true if getUser() returned a verified login
    private JSONObject payload;         //see getParameter



  /** Cache of successful logins. Created with the Authenticator passed to
   *  setAuthenticator() and shared by the per-request copies made in
   *  newInstance() (clone is shallow).
   */
    private Cache cache = new Cache();


  /** Shared by the per-request copies, like the cache. On by default. */
    private volatile LoginThrottle loginThrottle = new LoginThrottle();


  /** See setTrustedProxies. Shared by the per-request copies. Localhost by
   *  default.
   */
    private volatile String[] trustedProxies = ServiceRequest.LOCALHOST.clone();


  /** Security codes (null if disabled). Shared by the per-request copies. */
    private volatile EmailAuthentication emailAuth;


  /** See getCacheTTL and setCacheTTL. In ms (the API uses seconds). */
    private volatile long cacheTTL = DEFAULT_CACHE_TTL;


  /** Default for getCacheTTL(), in ms. */
    private static final long DEFAULT_CACHE_TTL = 30000; //30 seconds


  /** Session tokens start with this, so they can't be mistaken for passwords
   *  (and a password with this prefix is never checked as one).
   */
    public static final String SESSION_PREFIX = "xts_";


  /** Trust tokens (trusted browsers) start with this. */
    public static final String TRUST_PREFIX = "xtt_";


  /** Services a password alone can call when security codes are required. */
    private static final Set<String> LOGIN_STEPS = new HashSet<>(
        Arrays.asList("login", "startsession", "getcodestatus", "verifycode",
        "logoff", "logout"));


  /** The security code steps answered by handleRequest() (see isCodeStep). */
    private static final Set<String> CODE_STEPS = new HashSet<>(
        Arrays.asList("startsession", "getcodestatus", "verifycode", "endsession"));


  /** Services handled by handleLoginSteps(): the code steps, and "login". */
    private static final Set<String> TFA_STEPS = new HashSet<>(CODE_STEPS);
    static{ TFA_STEPS.add("login"); }


  /** Message for users when a code couldn't be sent (details are logged). */
    private static final String SEND_FAILED =
        "We couldn't send your security code. Please try again in a few minutes.";


    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final byte[] HMAC_KEY = new byte[32];
    static {
        new java.security.SecureRandom().nextBytes(HMAC_KEY);
    }


  //**************************************************************************
  //** newInstance
  //**************************************************************************
  /** Creates a new instance of this class. This method is called by the
   *  HttpServletRequest class every time an HTTP request is made to the
   *  server.
   */
    public Authenticator newInstance(HttpServletRequest request){
        try{
            Authenticator authenticator = (Authenticator) this.clone();
            authenticator.request = request;
            authenticator.authType = null;
            authenticator.authInfo = null;
            authenticator.credentials = null;
            authenticator.cacheGeneration = -1;
            authenticator.cacheHit = false;
            authenticator.payload = null;
            return authenticator;
        }
        catch(CloneNotSupportedException e){
            throw new RuntimeException(e); //can't happen: we implement Cloneable
        }
    }


  //**************************************************************************
  //** enableEmailAuthentication
  //**************************************************************************
  /** Used to enable an email authentication layer (e.g. send one-time
   *  security codes via email).
   */
    public EmailAuthentication enableEmailAuthentication(
        EmailService emailService, EmailTemplate emailTemplate){

        EmailVerification emailVerification = new EmailVerification(emailService, emailTemplate);
        if (emailAuth!=null) emailAuth.emailVerification = emailVerification;
        else emailAuth = new EmailAuthentication(emailVerification);
        return emailAuth;
    }


  //**************************************************************************
  //** isEmailAuthenticationEnabled
  //**************************************************************************
  /** Returns true if an email authentication layer has been enabled.
   */
    public boolean isEmailAuthenticationEnabled(){
        return emailAuth!=null;
    }


  //**************************************************************************
  //** authenticate
  //**************************************************************************
  /** Used to authenticate an HTTP request. If the getPrinciple() method fails
   *  to return a user, this method throws a ServletException. Override as
   *  needed.
   */
    public void authenticate() throws ServletException {

      //Get credentials. These are optional: getPrinciple() decides how a
      //request is authenticated (e.g. a default user, or a custom header).
        String[] credentials = getCredentials();
        boolean hasUsername = credentials!=null && credentials[0]!=null;


      //Check whether the client is locked out. This has to happen before the
      //credentials are checked, including the cache: otherwise a client that
      //is locked out could still test guesses (a correct guess would be let
      //in by the cache, a wrong one rejected with a 429). Session tokens are
      //exempt: they're random 256-bit values, not guesses, and an ended or
      //expired one (e.g. after a restart, when sessions are lost) would
      //otherwise lock the real user out of logging in again.
        LoginThrottle throttle = loginThrottle;
        boolean session = hasUsername && emailAuth!=null && isSessionToken(credentials[1]);
        boolean track = hasUsername && throttle!=null && !isLogout(credentials) && !session;
        String ip = null;
        if (track){
            ip = getClientIP();
            long wait = throttle.getRetryAfter(credentials[0], ip, now());
            if (wait>0) throw new LockedException(wait);
        }


      //Check credentials
        java.security.Principal user = getPrinciple();


      //Session tokens are only valid if getUser() found the session (or a
      //cached login for it). Otherwise, getPrinciple() checked the token as a
      //password: refuse it, since session tokens aren't subject to lockouts.
        if (session && !cacheHit) user = null;


      //Update the throttle
        if (track){
            if (user==null) throttle.onFailure(credentials[0], ip, credentials[1], now());
            else if (cacheHit) throttle.onCachedLogin(ip); //see LoginThrottle
            else throttle.onSuccess(credentials[0], ip);
        }
        else if (session && user!=null && throttle!=null){
            throttle.onCachedLogin(getClientIP()); //verified earlier, like a cached login
        }

        if (user==null) throw new ServletException(hasUsername ? 403 : 401, "Unauthorized");


      //Security codes: a password alone only opens the login steps
        if (isCodeRequired()) throw new CodeRequiredException();


      //Disabled accounts (the password was right: not a failed login)
        if (user instanceof User && !isEnabled((User) user)){
            throw new ServletException(403, "Not Authorized");
        }
    }


  //**************************************************************************
  //** getPrinciple
  //**************************************************************************
  /** Returns the java.security.Principal associated with an HTTP request.
   *  This method should be overridden as shown in the example above.
   *  Otherwise, this method will return null and any calls to the
   *  authenticate() method will throw a ServletException.
   */
    public java.security.Principal getPrinciple(){
        User user = getUser();
        return (user instanceof java.security.Principal) ? (java.security.Principal) user : null;
    }


  //**************************************************************************
  //** getCredentials
  //**************************************************************************
  /** Returns an array representing user credentials associated with an HTTP
   *  request. In the case of "BASIC" authentication, the credentials contain
   *  the username and password. In the case of "NTLM" authentication, the
   *  credentials only contain a username. Override as needed.
   */
    public String[] getCredentials() {
        if (credentials==null){

            String authType = getAuthType();
            if (authType==null) authType = "";

            if (authType.equals("BASIC")){


              //Decode the string
                String auth = new String(
                    javaxt.utils.Base64.decode(authInfo)
                );


              //Parse credentials
                String username = auth.substring(0, auth.indexOf(":"));
                String password = auth.substring(auth.indexOf(":")+1);
                credentials = new String[]{username, password};

            }
            else if (authType.equals("NTLM")){
                byte[] msg = javaxt.utils.Base64.decode(authInfo);


                int off = 0, length, offset;
                if (msg[8] == 3) {
                    off = 30;
                    length = msg[off+17]*256 + msg[off+16];
                    offset = msg[off+19]*256 + msg[off+8];
                    //String computerName = new String(msg, offset, length);
                    //System.out.println("computerName: " + computerName);


                  //Get domain name
                    length = msg[off+1]*256 + msg[off];
                    offset = msg[off+3]*256 + msg[off+2];
                    String str = new String(msg, offset, length);
                    StringBuilder domainName = new StringBuilder();
                    for (int i=0; i<str.length(); i++){
                        int c = str.charAt(i);
                        if (c!=0) domainName.append((char) c);
                    }
                    if (domainName.length()==0) domainName = null;


                  //Get username
                    length = msg[off+9]*256 + msg[off+8];
                    offset = msg[off+11]*256 + msg[off+10];
                    str = new String(msg, offset, length);
                    StringBuilder username = new StringBuilder();
                    for (int i=0; i<str.length(); i++){
                        int c = str.charAt(i);
                        if (c!=0) username.append((char) c);
                    }
                    if (username.length()==0) username = null;


                    if (domainName!=null && username!=null){
                        credentials = new String[]{username.toString(), null};
                    }
                }
            }
        }
        return credentials;
    }


  //**************************************************************************
  //** getAuthType
  //**************************************************************************
  /** Returns the authentication scheme used to authenticate clients (e.g.
   *  "BASIC", "NTLM", etc). By default, this class parses the "Authorization"
   *  HTTP request header to determine the authentication scheme. Override as
   *  needed.
   */
    public String getAuthType(){
        if (authType==null){
            String authorization = request.getHeader("Authorization");
            if (authorization!=null){
                int idx = authorization.indexOf(" ");
                authType = authorization.substring(0, idx).toUpperCase();
                authInfo = authorization.substring(idx+1);
            }
        }
        return authType;
    }


  //**************************************************************************
  //** isUserInRole
  //**************************************************************************
  /** This method is a legacy feature from the Java Servlet API. Returns
   *  false. Override as needed.
   */
    public boolean isUserInRole(String role){
        return false;
    }


  //**************************************************************************
  //** isEnabled
  //**************************************************************************
  /** Returns true if a user may log in. Called by authenticate() after the
   *  credentials have been checked, so disabled users are refused with a 403
   *  without it counting as a failed login, and the lockouts still apply
   *  first. That includes the login steps (no code is sent).
   *  Returns true by default. Override to refuse disabled accounts.
   */
    public boolean isEnabled(User user){
        return true;
    }


  //**************************************************************************
  //** getUsername
  //**************************************************************************
  /** Returns the first entry in the array returned by getCredentials().
   *  Override as needed.
   */
    public String getUsername(){
        String[] credentials = getCredentials();
        return (credentials!=null) ? credentials[0] : null;
    }


  //**************************************************************************
  //** getUser
  //**************************************************************************
  /** Returns a User from the cache. The keys returned from getCacheKey()
   *  is used to find the User in the cache. Override as needed.
   */
    public User getUser(){

      //Note the cache generation before the caller loads a user. See setUser.
        cacheGeneration = cache.getGeneration();
        cacheHit = false;

        String key = getCacheKey();
        if (key==null) return null;
        User user = cache.get(key, now(), cacheTTL);


      //Sessions (security codes)
        EmailAuthentication emailAuth = this.emailAuth;
        if (emailAuth!=null){
            String[] credentials = getCredentials();
            if (credentials!=null && isSessionToken(credentials[1])){
                Session session = emailAuth.getSession(credentials[0], credentials[1], now());
                if (session==null) return null;
                if (user==null){
                    user = getSessionUser(session.user);
                    if (user!=null) setUser(user);
                }
            }
        }

        cacheHit = (user!=null);
        return user;
    }


  //**************************************************************************
  //** setUser
  //**************************************************************************
  /** Used to add a User to the cache, keyed by getCacheKey().
   */
    public void setUser(User user){
        if (user==null) return;
        String key = getCacheKey();
        if (key==null) return;
        long generation = cacheGeneration>-1 ? cacheGeneration : cache.getGeneration();
        cache.put(key, user, now(), cacheTTL, generation);
    }


  //**************************************************************************
  //** getCacheKey
  //**************************************************************************
  /** Returns the key used to cache a login for the request credentials.
   *  Return null to skip the cache. Override with care. The key must include
   *  whatever is verified by getPrinciple (e.g. the password or session
   *  token). Otherwise, a cached login would let in requests with the same
   *  username and any password.
   */
    public String getCacheKey(){
        String[] credentials = getCredentials();
        if (credentials==null || credentials[0]==null) return null;
        return hmac(getAuthType(), credentials[0], credentials[1]);
    }


  //**************************************************************************
  //** getCacheTTL
  //**************************************************************************
  /** Returns how long a successful login is cached in secongs. The TTL counts
   *  from the time the credentials were verified (cache hits don't extend
   *  it).  See setCacheTTL.
   */
    public int getCacheTTL(){
        return (int) (cacheTTL/1000L);
    }


  //**************************************************************************
  //** setCacheTTL
  //**************************************************************************
  /** Sets how long a successful login is cached. Use 0 to disable caching.
   *  Default is 30 seconds.
   */
    public void setCacheTTL(int seconds){
        if (seconds<0) throw new IllegalArgumentException("seconds must be at least 0");
        this.cacheTTL = seconds*1000L;
    }


  //**************************************************************************
  //** invalidate
  //**************************************************************************
  /** Removes cached logins for a user. Call after changing a user (e.g.
   *  password, permissions, or status) so the change applies to the next
   *  request instead of after getCacheTTL() seconds.
   *  @param userID Value returned by the User's getID() method.
   */
    public void invalidate(Long userID){
        if (userID==null) return;
        invalidate(user -> userID.equals(user.getID()));
    }


  //**************************************************************************
  //** invalidate
  //**************************************************************************
  /** Removes cached logins for users that match the given filter.
   */
    public void invalidate(java.util.function.Predicate<User> filter){
        if (filter!=null) cache.invalidate(filter);
    }



  //**************************************************************************
  //** getRequest
  //**************************************************************************
  /** Returns the HttpServletRequest used to instantiate this instance of the
   *  Authenticator.
   */
    public HttpServletRequest getRequest(){
        return request;
    }


  //**************************************************************************
  //** handleRequest
  //**************************************************************************
  /** Used to process an authentication workflow. Returns true if a response
   *  was generated for the client. Example usage:
   <pre>
    public void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        Authenticator authenticator = (Authenticator) getAuthenticator(request);
        if (authenticator.handleRequest(service, response)) return;

        //If we're still here, generate a response!
    }
   </pre>
   *
   *  @param service Directive, typically extracted from the first path segment
   *  after the servlet path in the request URL (case-insensitive). Options:
   *  <ul>
   *  <li>"login": authenticates the request, then sends the user (see
   *  sendLoginResponse).</li>
   *  <li>"logoff" (or "logout"): forgets the cached login, ends the session
   *  (session tokens), and returns a 401 so the browser drops the credentials
   *  (add "prompt=true" to ask the browser for new ones).</li>
   *  <li>"whoami": returns the username of an authenticated request (plain
   *  text), or a 400. For XHR clients that can't read their own
   *  credentials.</li>
   *  <li>With security codes (see enableEmailAuthentication), the login
   *  steps: "startsession", "getcodestatus", "verifycode" and "endsession",
   *  and "login" answers {"tfa": true} for a password (see below).</li>
   *  </ul>
   *  <h3>Login steps (security codes)</h3>
   *  Parameters come from the query string or a JSON payload. Errors are
   *  plain text ("Error: message"), 429 with "Retry-After" when locked out.
   *  <p>
   *  Password + code (the default). The browser sends the password (BASIC)
   *  with each step:
   *  </p>
   *  <ul>
   *  <li>login (password): {"tfa": true}, i.e. call startsession next. Other
   *  logins (session tokens, see isCodeExempt) are answered as usual.</li>
   *  <li>startsession (password, optional "trustToken"): {"session": token}
   *  if the browser is trusted. Otherwise starts sending a code and returns
   *  {"codeSent": true}.</li>
   *  <li>getcodestatus (password): {"status": "sending"}, "sent", "failed"
   *  (with an "error" message for the user), or "none".</li>
   *  <li>verifycode (password, "code"): {"session": token, "trustToken":
   *  token, "trustHours": 12}. The browser keeps the trust token to skip the
   *  code next time (no trustToken if EmailAuthentication.getTrustHours() is
   *  0). Wrong codes count as failed logins.</li>
   *  <li>endsession (session token): ends the session. Logoff does too.</li>
   *  </ul>
   *  <p>
   *  Code only (EmailAuthentication.setCodeOnly). No credentials: the steps
   *  take a "username" (e.g. an email address, see getCodeUser):
   *  </p>
   *  <ul>
   *  <li>startsession ("username", optional "trustToken"): like above. Sends
   *  are limited per IP (see EmailAuthentication.setCodeSendLimit).</li>
   *  <li>getcodestatus ("username"): like above.</li>
   *  <li>verifycode ("username", "code"): like above.</li>
   *  </ul>
   *  Either way, the browser then logs in with the username and the session
   *  token instead of a password (e.g. javaxt.dhtml.Authentication.login).
   *  Apps with their own endpoints for these steps (e.g. to run extra checks
   *  before sending a code, see startCodeLogin) shouldn't pass them to this
   *  method, so clients can't skip the app's checks by calling the built-in
   *  steps directly (see isCodeStep).
   *  @param response An HttpServletResponse object to be updated.
   *  @return Returns true if this method was able to process the directive and
   *  update the HttpServletResponse. Nothing else should be written to the
   *  response. If false is returned, continue processing the request and
   *  generate your own response.
   */
    public boolean handleRequest(String service, HttpServletResponse response)
        throws ServletException, IOException {

        if (service==null) return false;
        service = service.toLowerCase();


      //Security code steps, if enabled
        if (handleLoginSteps(service, response)) return true;



      //Send NTLM response as needed
        boolean ntlm = "NTLM".equals(getAuthType());
        if (ntlm){
            String ua = request.getHeader("user-agent");
            if (ua!=null){
                if (ua.contains("MSIE ") || ua.contains("Trident/") || ua.contains("Edge/") || ua.contains("Edg/")){
                    if (sendNTLMResponse(request, response)) return true;
                }
                else{
                    ntlm = false;
                }
            }
        }


        if (service.equals("login")){
            if (getUsername()==null && ntlm){
                response.setStatus(401, "Access Denied");
                response.setHeader("WWW-Authenticate", "NTLM");
            }
            else{
                try{
                    request.authenticate();
                    sendLoginResponse((User) request.getUserPrincipal(), response);
                }
                catch(ServletException e){
                    sendError(e, response);
                }
            }
            return true;
        }
        else if (service.equals("logoff") || service.equals("logout")){

          //Remove the cached login for these credentials (if any), and end
          //the session if the credentials are a session token
            cache.remove(getCacheKey());
            String[] credentials = getCredentials();
            if (credentials!=null) endSession(credentials[1]);

            if (ntlm){
                response.setStatus(401, "Access Denied");
                response.setHeader("WWW-Authenticate", "NTLM");
            }
            else{
                response.setStatus(401, "Access Denied");

                Boolean prompt = new javaxt.utils.Value(request.getParameter("prompt")).toBoolean(); //<--Hack for Firefox
                if (prompt!=null && prompt==true){
                    response.setHeader("WWW-Authenticate", "Basic realm=\"" +
                    "This site is restricted. Please enter your username and password.\"");
                }

                response.setHeader("Cache-Control", "no-cache, no-transform");
                response.setContentType("text/plain");
                response.write("Unauthorized");
            }
            return true;
        }
        else if (service.equals("whoami")){
            String username = getUsername();
            boolean authenticated = false;
            if (username!=null && !username.equals("logout")){
                try{
                    request.authenticate();
                    authenticated = request.getUserPrincipal()!=null;
                }
                catch(Exception e){}
            }

            if (authenticated){
                send(response, 200, "text/plain", username, null);
            }
            else{
                response.setStatus(400, "Bad Request");
                response.write("");
            }
            return true;
        }

        return false;
    }


  //**************************************************************************
  //** handleLoginSteps
  //**************************************************************************
  /** Answers the login steps when security codes are enabled (see
   *  handleRequest). Returns true if a response was written, false if the
   *  service isn't one of the steps.
   */
    private boolean handleLoginSteps(String service, HttpServletResponse response)
        throws IOException {

        EmailAuthentication emailAuth = this.emailAuth;
        if (emailAuth==null || service==null) return false;

        String method = service.toLowerCase();
        if (!TFA_STEPS.contains(method)) return false;


        String[] credentials = getCredentials();
        boolean session = credentials!=null && isSessionToken(credentials[1]);


        if (method.equals("endsession")){
            if (session) endSession(credentials[1]);
            send(response, 200, "text/plain", "", null);
            return true;
        }

        if (emailAuth.codeOnly){
            if (method.equals("login")) return false; //session token: regular login
            handleCodeLogin(method, response);
            return true;
        }

        if (method.equals("login")){
            if (credentials==null || credentials[0]==null || session || isCodeExempt()) return false;
        }
        else if (session){
            sendError(response, 400, "Already logged in.");
            return true;
        }


      //Check the password (authenticate() is only run once per request). With
      //no credentials, this is a 401 challenge: browsers send cached
      //credentials only for the path they logged in on, until challenged.
        User user;
        try{
            request.authenticate();
            user = (User) request.getUserPrincipal();
        }
        catch(ServletException e){
            sendError(e, response);
            return true;
        }


        long now = now();
        if (method.equals("login")){
            JSONObject json = new JSONObject();
            json.set("tfa", true);
            sendJson(response, json);
        }
        else if (method.equals("startsession")){
            startSession(user, credentials[0], getParameter("trustToken"), now, response);
        }
        else if (method.equals("getcodestatus")){
            sendJson(response, emailAuth.getCodeStatus(getKey(user)));
        }
        else{
            verifyCode(user, credentials[0], getParameter("code"), now, response);
        }
        return true;
    }


  //**************************************************************************
  //** handleCodeLogin
  //**************************************************************************
  /** Login steps for code-only logins (see handleLoginSteps).
   */
    private void handleCodeLogin(String method, HttpServletResponse response) throws IOException {
        EmailAuthentication emailAuth = this.emailAuth;
        long now = now();
        String username = normalize(getParameter("username"));
        if (username==null) username = normalize(getParameter("email"));


      //Limit how many codes a client can have sent
        if (method.equals("startsession")){
            long wait = checkSendLimit(request);
            if (wait>0){
                sendLockedResponse(new LockedException(wait, LoginThrottle.getRateMessage(wait)), response);
                return;
            }
        }


      //Get the user
        User user;
        try{
            Pending pending = username==null ? null : emailAuth.pending.get(username);
            user = pending!=null ? pending.user : getCodeUser(username);
        }
        catch(IllegalArgumentException e){
            sendError(response, 400, e.getMessage());
            return;
        }
        catch(Exception e){
            sendError(response, 500, e.getMessage());
            return;
        }


        if (method.equals("startsession")){
            if (user==null){ //not allowed to log in: don't say so
                JSONObject json = new JSONObject();
                json.set("codeSent", true);
                sendJson(response, json);
                return;
            }
            emailAuth.addPending(username, user, now);
            startSession(user, username, getParameter("trustToken"), now, response);
        }
        else if (method.equals("getcodestatus")){
            JSONObject json = user==null ? new JSONObject() : emailAuth.getCodeStatus(getKey(user));
            if (user==null) json.set("status", "none");
            sendJson(response, json);
        }
        else{ //verifycode
            if (user==null) user = new EmailUser(username, null); //"no code was sent" from verifyCode
            if (verifyCode(user, username, getParameter("code"), now, response)){
                emailAuth.pending.remove(username);
            }
        }
    }


  //**************************************************************************
  //** sendNTLMResponse
  //**************************************************************************
  /** Returns true if an NTLM response was returned to the client
   */
    public static boolean sendNTLMResponse(HttpServletRequest request, HttpServletResponse response){
        String authorization = request.getHeader("Authorization");
        if (authorization==null){
            response.setStatus(response.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "NTLM");
            response.setContentLength(0);
            return true;
        }
        else{
            byte[] msg = javaxt.utils.Base64.decode(authorization.substring(5));
            if (msg[8] == 1) {

              //Send NTLM type2 response
                response.setStatus(response.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "NTLM " + NTLM_TYPE_2);
                response.setContentLength(0);
                return true;
            }
        }
        return false;
    }



    private static String NTLM_TYPE_2;
    static{
        byte z = 0;


        byte[] msg1 = {
            (byte) 'N', (byte) 'T', (byte) 'L', (byte) 'M', //ntlm
            (byte) 'S', (byte) 'S', (byte) 'P', //ssp
            z, (byte) 2, //type 2
            z, z, z, z, z, z, z, (byte) 40, z, z, z,
            (byte) 1, (byte) 130,
            (byte) 8, //super important!
            z, z, (byte) 2, (byte) 2,
            (byte) 2, z, z, z, z, z, z, z, z, z, z, z, z
        };

        NTLM_TYPE_2 = javaxt.utils.Base64.encode(msg1).trim();
    }


  //**************************************************************************
  //** sendLoginResponse
  //**************************************************************************
  /** Used to send a response after a successful login. By default, returns
   *  the user's ID, as plain text. Override as needed (e.g. return user info
   *  as JSON).
   */
    public void sendLoginResponse(User user, HttpServletResponse response) throws IOException {
        send(response, 200, "text/plain", String.valueOf(user.getID()), null);
    }


  //**************************************************************************
  //** sendError
  //**************************************************************************
  /** Used to send an authentication error to the client (e.g. 401 status with
   *  a "WWW-Authenticate" header for browsers that haven't sent their
   *  credentials, a 429 status with a "Retry-After" header, etc).
   */
    private static void sendError(ServletException e, HttpServletResponse response) throws IOException {

        if (e instanceof LockedException){
            sendLockedResponse((LockedException) e, response);
            return;
        }
        int status = e.getStatusCode();
        if (status==401){
            response.setHeader("WWW-Authenticate", "Basic realm=\"Access Denied\"");
        }
        else if (status!=403){
            status = 403;
        }
        String message = e.getMessage();
        send(response, status, "text/plain", message==null ? "Unauthorized" : message, null);
    }


  //**************************************************************************
  //** sendLockedResponse
  //**************************************************************************
  /** Used to respond to a client that is locked out after too many failed
   *  logins. Sends a status 429 with a "Retry-After" header and a plain text
   *  message (e.g. "Too many failed login attempts. Try again in 2 minutes.").
   */
    public static void sendLockedResponse(LockedException e,
        HttpServletResponse response) throws IOException {
        response.setStatus(429, "Too Many Requests");
        response.setHeader("Retry-After", e.getRetryAfterHeader());
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setContentType("text/plain");
        response.write(e.getMessage());
    }


  //**************************************************************************
  //** isSessionToken
  //**************************************************************************
  /** Returns true if a given string starts with a session prefix. Does not
   *  check whether the token is valid.
   */
    public static boolean isSessionToken(String credential){
        return credential!=null && credential.startsWith(SESSION_PREFIX);
    }


  //**************************************************************************
  //** endSession
  //**************************************************************************
  /** Used to end an authentication session associated with a session token.
   */
    public void endSession(String token){
        if (emailAuth!=null && isSessionToken(token)) emailAuth.sessions.remove(hash(token));
    }


  //**************************************************************************
  //** endSessions
  //**************************************************************************
  /** Used to end all sessions for a given user. Provides an exception for a
   *  specific session token.
   */
    public void endSessions(User user, String exceptToken){
        if (emailAuth==null || user==null) return;
        String key = getKey(user);
        String except = isSessionToken(exceptToken) ? hash(exceptToken) : null;
        emailAuth.sessions.entrySet().removeIf(e -> e.getValue().key.equals(key) && !e.getKey().equals(except));
    }


  //**************************************************************************
  //** revoke
  //**************************************************************************
  /** Ends a user's sessions and forgets their trusted browsers, e.g. when
   *  their password changes or they're disabled. Does nothing if security
   *  codes aren't required.
   *  @param keepSession Session to keep (e.g. the one that changed the
   *  password), or null.
   */
    public void revoke(User user, String keepSession) throws Exception {
        if (emailAuth==null || user==null) return;
        deleteTrustTokens(user);
        endSessions(user, keepSession);
    }



  //**************************************************************************
  //** now
  //**************************************************************************
  /** Returns the current time in ms. Used for cache timestamps. Override in
   *  tests to control the clock.
   */
    protected long now(){
        return System.currentTimeMillis();
    }


  //**************************************************************************
  //** getClientIP
  //**************************************************************************
  /** Returns the client IP address for the request, using the trusted
   *  proxies (see setTrustedProxies and ServiceRequest.getClientIP). Override
   *  as needed.
   */
    protected String getClientIP(){
        return ServiceRequest.getClientIP(request, trustedProxies);
    }


  //**************************************************************************
  //** setTrustedProxies
  //**************************************************************************
  /** Sets the addresses of reverse proxies whose "X-Forwarded-For" header can
   *  be trusted. Default is localhost (IPv4 and IPv6). Pass no addresses to
   *  trust none.
   */
    public void setTrustedProxies(String... proxies){
        if (proxies==null) proxies = new String[0];
        String[] arr = new String[proxies.length];
        for (int i=0; i<arr.length; i++) arr[i] = ServiceRequest.normalizeIP(proxies[i]);
        trustedProxies = arr;
    }


  //**************************************************************************
  //** getTrustedProxies
  //**************************************************************************
  /** Returns the addresses of reverse proxies whose "X-Forwarded-For" header
   *  can be trusted (see setTrustedProxies).
   */
    public String[] getTrustedProxies(){
        return trustedProxies.clone();
    }


  //**************************************************************************
  //** setLoginThrottle
  //**************************************************************************
  /** Used to lock out clients after repeated login failures. A LoginThrottle
   *  with default settings is used unless this method is called. Pass null to
   *  disable.
   */
    public void setLoginThrottle(LoginThrottle throttle){
        this.loginThrottle = throttle;
    }


  //**************************************************************************
  //** getLoginThrottle
  //**************************************************************************
  /** Returns the LoginThrottle used by this Authenticator, or null if it's
   *  disabled.
   */
    public LoginThrottle getLoginThrottle(){
        return loginThrottle;
    }


  //**************************************************************************
  //** checkLockout
  //**************************************************************************
  /** Returns a LockedException if the client of a request is locked out of
   *  password checks for a user (too many failed logins, see LoginThrottle),
   *  or null.
   */
    public LockedException checkLockout(String username, HttpServletRequest request){
        if (loginThrottle==null) return null;
        long wait = loginThrottle.getRetryAfter(username,
            ServiceRequest.getClientIP(request, trustedProxies), now());
        return wait>0 ? new LockedException(wait) : null;
    }


  //**************************************************************************
  //** isLogout
  //**************************************************************************
  /** Returns true if the credentials are the "logout" placeholder used to
   *  log users out (see handleRequest). These aren't counted as failures.
   */
    private static boolean isLogout(String[] credentials){
        return "logout".equals(credentials[0]) && "logout".equals(credentials[1]);
    }


  //**************************************************************************
  //** recordPasswordCheck
  //**************************************************************************
  /** Records the result of a password check made outside the authenticator
   *  (see checkLockout).
   */
    public void recordPasswordCheck(String username, String password, boolean isValid,
        HttpServletRequest request){
        if (loginThrottle==null) return;
        String ip = ServiceRequest.getClientIP(request, trustedProxies);
        if (isValid) loginThrottle.onSuccess(username, ip);
        else loginThrottle.onFailure(username, ip, password, now());
    }


  //**************************************************************************
  //** isCodeStep
  //**************************************************************************
  /** Returns true if the service endpoint matched one of the security code
   *  steps expected by handleRequest() (e.g. "startsession", "getcodestatus",
   *  "verifycode" or "endsession"). Comparison is case-insensitive. Apps with
   *  their own endpoints for these steps (e.g. to run extra checks before
   *  sending a code, see startCodeLogin) use it to keep the built-in ones out
   *  of reach. Example:
   *  <pre>
   *  if (!authenticator.isCodeStep(service) &amp;&amp; authenticator.handleRequest(service, response)) return;
   *  </pre>
   */
    public boolean isCodeStep(String service){
        return service!=null && CODE_STEPS.contains(service.toLowerCase());
    }


  //**************************************************************************
  //** startSession
  //**************************************************************************
  /** Second step of a login with a security code, after the password (or
   *  the first step of a code-only login).
   */
    private void startSession(User user, String username, String trustToken, long now,
        HttpServletResponse response) throws IOException {


        if (emailAuth.trustTTL>0 && trustToken!=null && trustToken.startsWith(TRUST_PREFIX)){
            boolean trusted;
            try{
                trusted = isTrusted(user, hash(trustToken), now);
            }
            catch(Exception e){
                sendError(response, 500, e.getMessage());
                return;
            }
            if (trusted){
                JSONObject json = new JSONObject();
                json.set("session", emailAuth.createSession(user, username, now));
                sendJson(response, json);
                return;
            }
        }


        String key = getKey(user);
        emailAuth.codeStatus.put(key, new CodeStatus("sending", null, now()));
        try{
            emailAuth.getSender().execute(() -> {
                CodeStatus status;
                try{
                    sendCode(user);
                    status = new CodeStatus("sent", null, now());
                }
                catch(IllegalStateException | IllegalArgumentException e){
                    status = new CodeStatus("failed", e.getMessage(), now()); //e.g. no email on file
                }
                catch(Throwable e){
                    System.err.println("[Authenticator] Failed to send a security code to " +
                    key + ": " + e);
                    status = new CodeStatus("failed", SEND_FAILED, now());
                }
                emailAuth.codeStatus.put(key, status);
            });
        }
        catch(RejectedExecutionException e){
            emailAuth.codeStatus.put(key, new CodeStatus("failed", SEND_FAILED, now()));
        }


        JSONObject json = new JSONObject();
        json.set("codeSent", true);
        sendJson(response, json);
    }


  //**************************************************************************
  //** verifyCode
  //**************************************************************************
  /** Last step of a login with a security code. Returns true if a session
   *  was started.
   */
    private boolean verifyCode(User user, String username, String code, long now,
        HttpServletResponse response) throws IOException {

        EmailAuthentication emailAuth = this.emailAuth;


      //Check the code
        try{
            if (code==null || code.trim().isEmpty()) throw new IllegalArgumentException(
            "Please enter the security code.");
            checkCode(user, username, code.trim(), getClientIP(), now);
        }
        catch(LockedException e){
            sendLockedResponse(e, response);
            return false;
        }
        catch(IllegalAccessException e){ //too many wrong codes
            sendError(response, 429, e.getMessage());
            return false;
        }
        catch(IllegalArgumentException | IllegalStateException e){
            sendError(response, 400, e.getMessage());
            return false;
        }
        catch(Exception e){
            sendError(response, 500, e.getMessage());
            return false;
        }


      //Start a session and trust this browser
        try{
            JSONObject json = new JSONObject();
            json.set("session", emailAuth.createSession(user, username, now));
            if (emailAuth.trustTTL>0){
                String token = TRUST_PREFIX + newToken();
                saveTrustToken(user, hash(token), now+emailAuth.trustTTL);
                json.set("trustToken", token);
            }
            json.set("trustHours", (int) (emailAuth.trustTTL/3600000L));
            sendJson(response, json);
            return true;
        }
        catch(Exception e){
            sendError(response, 500, e.getMessage());
            return false;
        }
    }


  //**************************************************************************
  //** checkCode
  //**************************************************************************
  /** Checks a security code (see verifyCode), with lockouts. Wrong codes
   *  count as failed logins, under their own name: with a password, every
   *  step re-sends the (right) password, which would otherwise reset the
   *  count. BASIC usernames can't contain a colon, so this can't be a real
   *  user.
   */
    private void checkCode(User user, String username, String code, String ip, long now)
        throws Exception {

        LoginThrottle throttle = loginThrottle;
        String codeKey = username + ":code";
        if (throttle!=null){
            long wait = throttle.getRetryAfter(codeKey, ip, now);
            if (wait>0) throw new LockedException(wait);
        }

        try{
            verifyCode(user, code);
        }
        catch(IllegalAccessException | IllegalArgumentException | IllegalStateException e){
            if (throttle!=null) throttle.onFailure(codeKey, ip, code, now);
            throw e;
        }

        if (throttle!=null) throttle.onSuccess(codeKey, ip);
        emailAuth.codeStatus.remove(getKey(user));
    }


  //**************************************************************************
  //** checkSendLimit
  //**************************************************************************
  /** Counts a request for a code from the client of a request, and returns 0
   *  if it's allowed, or how long (ms) the client must wait.
   */
    public long checkSendLimit(HttpServletRequest request){
        if (emailAuth==null) throw new IllegalStateException("Email Authentication is not enabled");
        if (loginThrottle==null) return 0;
        return loginThrottle.checkRate(
            "sendcode:" + ServiceRequest.getClientIP(request, trustedProxies),
            emailAuth.sendMax, emailAuth.sendWindow, now()
        );
    }


  //**************************************************************************
  //** startCodeLogin
  //**************************************************************************
  /** Sends a security code to a user. This method is for code-only logins
   *  with the app's own endpoints. The user is kept until the code is
   *  verified (see finishCodeLogin). If a code was already sent and hasn't
   *  expired, no new code is sent.
   */
    public void startCodeLogin(User user) throws Exception {
        if (emailAuth==null) throw new IllegalStateException("Email Authentication is not enabled");
        String username = normalize(getName(user));
        if (username==null) throw new IllegalArgumentException("Email is required");
        emailAuth.addPending(username, user, now());
        try{
            sendCode(user);
        }
        catch(Exception e){
            emailAuth.pending.remove(username);
            throw e;
        }
    }


  //**************************************************************************
  //** finishCodeLogin
  //**************************************************************************
  /** Checks the code sent by startCodeLogin(), and starts a session for the
   *  user. Returns the session token. The browser logs in with the username
   *  and the token (see getSession). Wrong codes count as failed logins.
   *  @throws LockedException After too many wrong codes from the client (429).
   *  @throws IllegalAccessException After too many wrong codes for this code
   *  (a new code is needed, 429).
   *  @throws IllegalArgumentException If the code is wrong or expired (400).
   */
    public String finishCodeLogin(String username, String code, HttpServletRequest request)
        throws Exception {
        if (emailAuth==null) throw new IllegalStateException("Email Authentication is not enabled");
        username = normalize(username);
        if (username==null) throw new IllegalArgumentException("Email is required");
        Pending pending = emailAuth.pending.get(username);
        User user = pending!=null ? pending.user : new EmailUser(username, null);
        String ip = ServiceRequest.getClientIP(request, trustedProxies);
        long now = now();
        checkCode(user, username, code, ip, now);
        emailAuth.pending.remove(username);
        return emailAuth.createSession(user, username, now);
    }


  //**************************************************************************
  //** getSession
  //**************************************************************************
  /** Returns the User associated with a username and session token. Usernames
   *  are case-insensitive.
   */
    public User getSession(String username, String token){
        EmailAuthentication emailAuth = this.emailAuth;
        if (emailAuth==null) return null;
        Session s = emailAuth.getSession(username==null ? null : username.trim(), token, now());
        return s==null ? null : getSessionUser(s.user);
    }


  //**************************************************************************
  //** getCodeUser
  //**************************************************************************
  /** Returns an EmailUser for a code-only login.
   *  @param username By default, any valid email address.
   *  @throws IllegalArgumentException If the username isn't valid.
   */
    public User getCodeUser(String username) throws Exception {
        if (!javaxt.express.email.EmailUtils.isValidEmail(username)){
            throw new IllegalArgumentException("Email is required");
        }
        return new EmailUser(username, null);
    }


  //**************************************************************************
  //** sendCode
  //**************************************************************************
  /** Used to send a verification email with a security code. Calls getEmail()
   *  and getDisplayName() get the user's email and name.
   */
    public void sendCode(User user) throws Exception {
        getEmailVerification().sendVerificationEmail(
            getEmail(user), getDisplayName(user), new JSONObject()
        );
    }


  //**************************************************************************
  //** verifyCode
  //**************************************************************************
  /** Checks a security code sent to a given User.
   *  @throws IllegalArgumentException If the code is wrong or expired.
   *  @throws IllegalAccessException After too many wrong codes (a new code
   *  is needed).
   */
    public void verifyCode(User user, String code) throws Exception {
        getEmailVerification().verifyCode(getEmail(user), code);
    }


  //**************************************************************************
  //** getDisplayName
  //**************************************************************************
  /** Returns the name associated with a given user. Override as needed.
   */
    public String getDisplayName(User user){
        return (user instanceof java.security.Principal) ?
            ((java.security.Principal) user).getName() : null;
    }


  //**************************************************************************
  //** getEmail
  //**************************************************************************
  /** Returns the email address associated with a given user. Override as
   *  needed.
   */
    public String getEmail(User user) throws Exception {
        if (user instanceof EmailUser) return ((EmailUser) user).getEmail();

        String name = (user instanceof java.security.Principal) ?
            ((java.security.Principal) user).getName() : null;

        if (name!=null && name.contains("@")) return name.trim();

        throw new IllegalStateException(
        "There's no email address on file for your account, so a security code can't be sent.");
    }


  //**************************************************************************
  //** getEmailVerification
  //**************************************************************************
  /** Returns the EmailVerification used to send and check codes (created
   *  by enableEmailAuthentication).
   *  @throws IllegalStateException If there isn't one.
   */
    protected EmailVerification getEmailVerification(){
        EmailVerification ev = emailAuth==null ? null : emailAuth.emailVerification;
        if (ev==null) throw new IllegalStateException(
        "Security codes can't be sent (email isn't configured, see enableEmailAuthentication).");
        return ev;
    }


  //**************************************************************************
  //** saveTrustToken
  //**************************************************************************
  /** Saves a trust token (a browser that entered a valid code). Only its hash
   *  is passed in. Kept in memory by default (lost on restart). Override, with
   *  isTrusted() and deleteTrustTokens(), to keep them in a database. Use
   *  getRequest() and getClientIP() for details about the browser.
   *  @param expires When the token expires (ms since the epoch).
   */
    public void saveTrustToken(User user, String tokenHash, long expires) throws Exception {
        EmailAuthentication emailAuth = this.emailAuth;
        long now = now();
        emailAuth.trustTokens.values().removeIf(t -> t.expires<=now);
        emailAuth.trustTokens.put(tokenHash, new TrustToken(getKey(user), expires));
    }


  //**************************************************************************
  //** isTrusted
  //**************************************************************************
  /** Returns true if a trust token (its hash) is valid for the user.
   */
    public boolean isTrusted(User user, String tokenHash, long now) throws Exception {
        TrustToken t = emailAuth.trustTokens.get(tokenHash);
        return t!=null && t.expires>now && t.key.equals(getKey(user));
    }


  //**************************************************************************
  //** deleteTrustTokens
  //**************************************************************************
  /** Forgets all of a user's trusted browsers (see revoke).
   */
    public void deleteTrustTokens(User user) throws Exception {
        String key = getKey(user);
        emailAuth.trustTokens.values().removeIf(t -> t.key.equals(key));
    }


  //**************************************************************************
  //** getSessionUser
  //**************************************************************************
  /** Returns the user for a request with a session token. By default, the
   *  user who logged in. Override to reload the user (e.g. from a database)
   *  so changes apply to active sessions, or return null to refuse the
   *  request (e.g. the user was deleted).
   */
    public User getSessionUser(User user){
        return user;
    }


  //**************************************************************************
  //** isCodeExempt
  //**************************************************************************
  /** Returns true if the request doesn't need a security code, even with a
   *  password (e.g. a trusted service on the same machine). Returns false by
   *  default. Override as needed.
   */
    public boolean isCodeExempt(){
        return false;
    }


  //**************************************************************************
  //** getService
  //**************************************************************************
  /** Returns the service (web method) of the request, used to decide which
   *  requests a password alone can make when security codes are required
   *  (the login steps, see handleRequest). By default, the last
   *  segment of the URL path (e.g. "login" for "/app/login"). Override if
   *  the app routes requests differently.
   */
    protected String getService(){
        if (request==null) return null;
        String path = request.getURL().getPath();
        if (path==null) return null;
        while (path.endsWith("/")) path = path.substring(0, path.length()-1);
        int idx = path.lastIndexOf("/");
        return idx<0 ? path : path.substring(idx+1);
    }


  /** Returns true if the request only has a password, and security codes are
   *  required for it (see authenticate).
   */
    private boolean isCodeRequired(){
        if (emailAuth==null) return false;
        String[] credentials = getCredentials();
        if (credentials==null || isSessionToken(credentials[1])) return false;
        if (isCodeExempt()) return false;
        String service = getService();
        return service==null || !LOGIN_STEPS.contains(service.toLowerCase());
    }


  /** Returns a key for the user (ID, or name if there's no ID). */
    private static String getKey(User user){
        Long id = user.getID();
        if (id!=null) return "id:" + id;
        String name = (user instanceof java.security.Principal) ?
            ((java.security.Principal) user).getName() : null;
        return "name:" + (name==null ? "" : name.toLowerCase());
    }


  /** Returns a parameter from the JSON payload or query string. The payload
   *  is read once per request (the body can only be read once).
   */
    private String getParameter(String key){
        if (payload==null){
            payload = new JSONObject();
            try{
                byte[] b = request.getBody();
                if (b!=null && b.length>0){
                    payload = new JSONObject(new String(b, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            catch(Exception e){
            }
        }
        if (payload.has(key)){
            String value = payload.get(key).toString();
            if (value!=null) return value;
        }
        return request.getParameter(key);
    }


    private static void sendJson(HttpServletResponse response, JSONObject json) throws IOException {
        send(response, 200, "application/json", json.toString(), null);
    }

    private static void sendError(HttpServletResponse response, int status, String message) throws IOException {
        send(response, status, "text/plain", "Error: " + message, null);
    }

    private static void send(HttpServletResponse response, int status, String contentType,
        String body, String retryAfter) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-cache, no-transform");
        if (retryAfter!=null) response.setHeader("Retry-After", retryAfter);
        response.setContentType(contentType);
        response.write(body);
    }


  //**************************************************************************
  //** hmac
  //**************************************************************************
  /** Returns a Base64 encoded HMAC-SHA256 of the given values, or null if it
   *  can't be computed. Each value is length-prefixed (nulls are marked) so
   *  different combinations of values can't produce the same input.
   */
    private static String hmac(String... values){
        javax.crypto.Mac mac = HMAC.get();
        if (mac==null) return null;
        for (String value : values){
            if (value==null){
                mac.update(new byte[]{0,0,0,0,0});
            }
            else{
                byte[] b = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                mac.update((byte) 1);
                mac.update(new byte[]{(byte)(b.length>>>24), (byte)(b.length>>>16), (byte)(b.length>>>8), (byte)b.length});
                mac.update(b);
            }
        }
        return Base64.getEncoder().encodeToString(mac.doFinal());
    }


    private static final ThreadLocal<javax.crypto.Mac> HMAC = ThreadLocal.withInitial(() -> {
        try{
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(HMAC_KEY, "HmacSHA256"));
            return mac;
        }
        catch(Exception e){
            return null; //every JVM has HmacSHA256, but fail safe (no caching)
        }
    });


    private static String getName(User user){
        return (user instanceof java.security.Principal) ? ((java.security.Principal) user).getName() : null;
    }

    private static String normalize(String username){
        if (username==null) return null;
        username = username.trim().toLowerCase();
        return username.isEmpty() ? null : username;
    }


  /** Returns a random 256-bit token (base64url). */
    private static String newToken(){
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }


  //**************************************************************************
  //** hash
  //**************************************************************************
  /** Returns a SHA-256 hash (hex) of a token. Only hashes of session and
   *  trust tokens are kept.
   */
    public static String hash(String token){
        try{
            byte[] b = java.security.MessageDigest.getInstance("SHA-256").digest(
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder str = new StringBuilder();
            for (byte x : b) str.append(String.format("%02x", x));
            return str.toString();
        }
        catch(java.security.NoSuchAlgorithmException e){
            throw new RuntimeException(e); //every JVM has SHA-256
        }
    }


  //**************************************************************************
  //** CodeRequiredException Class
  //**************************************************************************
  /** Thrown by authenticate() when a request needs a session token (security
   *  codes are required, see enableEmailAuthentication) but only has a
   *  password. This isn't a failed login: the password was right.
   */
    public static class CodeRequiredException extends ServletException {
        public CodeRequiredException(){
            super(403, "Security code required");
        }
    }


  //**************************************************************************
  //** EmailUser Class
  //**************************************************************************
  /** A user identified by an email address, for code-only logins (see
   *  getCodeUser and startCodeLogin). getName() returns the email address
   *  (lowercase). getInfo() returns details the app attached to the user
   *  (e.g. the name entered on the login page), or null. getID() returns
   *  null.
   */
    public static class EmailUser implements User, java.security.Principal {
        private final String email;
        private final JSONObject info;
        public EmailUser(String email, JSONObject info){
            if (email==null || email.trim().isEmpty()) throw new IllegalArgumentException("Email is required");
            this.email = email.trim().toLowerCase();
            this.info = info;
        }
        public String getName(){ return email; }
        public String getEmail(){ return email; }
        public JSONObject getInfo(){ return info; }
        public Long getID(){ return null; }
        public String toString(){ return email; }
    }


  //**************************************************************************
  //** EmailAuthentication Class
  //**************************************************************************
  /** Used to manage email authentication settings and sessions (see
   *  enableEmailAuthentication). By default, a code is required after the
   *  password, a code is valid for 10 minutes, a browser that entered one is
   *  trusted for 12 hours, and sessions end after 30 minutes of inactivity.
   */
    public static class EmailAuthentication {
        private volatile EmailVerification emailVerification;
        private volatile long trustTTL = 12*3600000L;
        private volatile long sessionTTL = 12*3600000L;
        private volatile long sessionIdle = 30*60000L;
        private volatile boolean codeOnly;
        private volatile int sendMax = 3;
        private volatile long sendWindow = 10*60000L;
        private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>(); //code-only logins
        private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, TrustToken> trustTokens = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CodeStatus> codeStatus = new ConcurrentHashMap<>();
        private final AtomicLong lastSweep = new AtomicLong();
        private Executor sender;

        private EmailAuthentication(EmailVerification emailVerification){
            this.emailVerification = emailVerification;
        }


      /** Used to let users log in with a security code alone: no password
       *  (e.g. "email me a sign-in code"). The login steps (see
       *  handleRequest) then take a "username" (e.g. an email
       *  address, see getCodeUser) instead of a password. Any valid email
       *  address can log in unless getCodeUser() is overridden. Off by default
       *  (a code is required after the password).
       *  <p>
       *  setCodeOnly(true) also sets the trust hours to 0 (see setTrustHours):
       *  with no password, a trust token alone would log the user in. Call
       *  setTrustHours() afterwards to trust browsers anyway.
       *  </p>
       */
        public void setCodeOnly(boolean codeOnly){
            this.codeOnly = codeOnly;
            if (codeOnly) trustTTL = 0;
        }


      /** Returns true if users log in with a security code alone (see
       *  setCodeOnly).
       */
        public boolean isCodeOnly(){
            return codeOnly;
        }


      /** Sets how long (minutes) an emailed security code is valid. Default is
       *  10 minutes.
       */
        public void setCodeMinutes(int minutes){
            if (minutes<1) throw new IllegalArgumentException("minutes must be at least 1");
            emailVerification.setPendingSessionExpiry(minutes*60000L);
        }


      /** Sets how many wrong tries a code allows before a new code is needed.
       *  Default is 5. (Wrong codes also count as failed logins, see
       *  LoginThrottle.)
       */
        public void setMaxVerifyAttempts(int attempts) {
            emailVerification.setMaxVerifyAttempts(attempts);
        }


      /** Sets how long (hours) a browser stays trusted after entering a security
       *  code, i.e. how often users need a code. Default is 12. Use 0 to require
       *  a code at every login, or 24 for once a day.
       */
        public void setTrustHours(int hours){
            if (hours<0) throw new IllegalArgumentException("hours must be at least 0");
            trustTTL = hours*3600000L;
        }


      /** Returns how long (hours) a browser stays trusted after entering a
       *  security code (0 if a code is needed at every login).
       */
        public int getTrustHours(){
            return (int) (trustTTL/3600000L);
        }


      /** Sets how long (minutes) a session lasts without requests. Default is 30.
       *  Should be longer than the web app's own idle logoff, if any.
       */
        public void setSessionIdleMinutes(int minutes){
            if (minutes<1) throw new IllegalArgumentException("minutes must be at least 1");
            sessionIdle = minutes*60000L;
        }


      /** Sets the longest a session can last (hours), however active. Default is
       *  12.
       */
        public void setMaxSessionHours(int hours){
            if (hours<1) throw new IllegalArgumentException("hours must be at least 1");
            sessionTTL = hours*3600000L;
        }


      /** Sets how many codes a client (IP address) can request in a given
       *  time, for code-only logins (see setCodeOnly and checkSendLimit).
       *  Default is 3 per 10 minutes. Requests beyond that get a 429. Uses the
       *  LoginThrottle (no limit if it's disabled).
       */
        public void setCodeSendLimit(int max, int minutes){
            if (max<1) throw new IllegalArgumentException("max must be at least 1");
            if (minutes<1) throw new IllegalArgumentException("minutes must be at least 1");
            sendMax = max;
            sendWindow = minutes*60000L;
        }


      /** Returns an Executor with 2 daemon threads, used to send codes in the
       *  background (sending email can be slow, and shouldn't keep the JVM
       *  from exiting).
       */
        private synchronized Executor getSender(){
            if (sender==null){
                sender = Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "Authenticator-codes");
                    t.setDaemon(true);
                    return t;
                });
            }
            return sender;
        }

        private String createSession(User user, String username, long now){
            removeExpired(now);
            String token = SESSION_PREFIX + newToken();
            sessions.put(hash(token), new Session(user, username, now));
            return token;
        }

      /** Returns the session for a token and username, or null. Counts as
       *  activity (resets the idle timer).
       */
        private Session getSession(String username, String token, long now){
            if (username==null || !isSessionToken(token)) return null;
            String key = hash(token);
            Session s = sessions.get(key);
            if (s==null) return null;
            if (isExpired(s, now)){
                sessions.remove(key, s);
                return null;
            }
            if (!s.username.equalsIgnoreCase(username)) return null;
            s.lastUsed = now;
            return s;
        }

      /** Remembers the user a code was sent to (code-only logins), until the
       *  code is verified. Forgotten after an hour.
       */
        private void addPending(String username, User user, long now){
            pending.values().removeIf(p -> now-p.time>3600000L);
            pending.put(username, new Pending(user, now));
        }

        private JSONObject getCodeStatus(String key){
            JSONObject json = new JSONObject();
            CodeStatus status = codeStatus.get(key);
            json.set("status", status==null ? "none" : status.status);
            if (status!=null && status.error!=null) json.set("error", status.error);
            return json;
        }

        private boolean isExpired(Session s, long now){
            return now-s.lastUsed>=sessionIdle || now-s.created>=sessionTTL;
        }

        private void removeExpired(long now){
            long prev = lastSweep.get();
            if (now-prev<60000) return;
            if (!lastSweep.compareAndSet(prev, now)) return;
            sessions.values().removeIf(s -> isExpired(s, now));
            codeStatus.values().removeIf(s -> now-s.time>3600000L);
        }
    }


  //**************************************************************************
  //** Session Class
  //**************************************************************************
    private static class Session {
        final User user;
        final String key;
        final String username;
        final long created;
        volatile long lastUsed;
        Session(User user, String username, long now){
            this.user = user;
            this.key = getKey(user);
            this.username = username;
            this.created = now;
            this.lastUsed = now;
        }
    }


  //**************************************************************************
  //** Pending Class
  //**************************************************************************
    private static class Pending {
        final User user;
        final long time;
        Pending(User user, long time){
            this.user = user;
            this.time = time;
        }
    }


  //**************************************************************************
  //** TrustToken Class
  //**************************************************************************
    private static class TrustToken {
        final String key;
        final long expires;
        TrustToken(String key, long expires){
            this.key = key;
            this.expires = expires;
        }
    }


  //**************************************************************************
  //** CodeStatus Class
  //**************************************************************************
    private static class CodeStatus {
        final String status;
        final String error;
        final long time;
        CodeStatus(String status, String error, long time){
            this.status = status;
            this.error = error;
            this.time = time;
        }
    }


  //**************************************************************************
  //** Cache Class
  //**************************************************************************
  /** Thread-safe cache of successful logins.
   */
    private static class Cache {

        private static final long SWEEP_INTERVAL = 60000;
        private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
        private final AtomicLong generation = new AtomicLong();
        private final AtomicLong lastSweep = new AtomicLong();

        private static class Entry {
            final User user;
            final long time;
            Entry(User user, long time){
                this.user = user;
                this.time = time;
            }
        }

        long getGeneration(){
            return generation.get();
        }

        User get(String key, long now, long ttl){
            if (ttl<=0) return null;
            removeExpired(now, ttl);
            Entry entry = map.get(key);
            if (entry==null) return null;
            if (now-entry.time<ttl) return entry.user;
            map.remove(key, entry); //expired
            return null;
        }

      /** Adds an entry, unless the cache was invalidated after the given
       *  generation. (invalidate() increments the generation before it
       *  sweeps, so either it removes our entry or we see the new value.)
       */
        void put(String key, User user, long now, long ttl, long gen){
            if (ttl<=0) return;
            Entry entry = new Entry(user, now);
            map.put(key, entry);
            if (generation.get()!=gen) map.remove(key, entry);
        }

        void remove(String key){
            if (key!=null) map.remove(key);
        }

        void invalidate(java.util.function.Predicate<User> filter){
            generation.incrementAndGet();
            map.values().removeIf(e -> filter.test(e.user));
        }

      /** Removes expired entries, at most once per SWEEP_INTERVAL (or TTL if
       *  shorter). Entries found expired on lookup are removed right away.
       */
        private void removeExpired(long now, long ttl){
            long prev = lastSweep.get();
            if (now-prev<Math.min(ttl, SWEEP_INTERVAL)) return;
            if (!lastSweep.compareAndSet(prev, now)) return; //another thread is sweeping
            map.values().removeIf(e -> now-e.time>=ttl);
        }
    }


    public static class LoginThrottle {

        private final Rule pairRule = new Rule(3, minutes(2, 5, 15, 60), 24*3600000L);
        private final Rule ipRule = new Rule(10, minutes(2, 5, 15, 60), 24*3600000L);
        private final Rule userRule = new Rule(50, minutes(2, 5, 15), 60*60000L);
        private volatile int maxEntries = 100000;

        private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>(); //see checkRate
        private final AtomicLong lastSweep = new AtomicLong();
        private static final long SWEEP_INTERVAL = 60000;


        public LoginThrottle(){}


      /** Returns the number of failed logins allowed for a username + IP pair
       *  before it's locked out. Default is 3.
       */
        public int getFreeAttempts(){
            return pairRule.freeAttempts;
        }


      /** Sets the number of failed logins allowed for a username + IP pair
       *  before it's locked out. Must be at least 1.
       */
        public void setFreeAttempts(int freeAttempts){
            pairRule.freeAttempts = atLeast(1, freeAttempts, "freeAttempts");
        }


      /** Returns the lockouts (minutes) for the username + IP and IP rules: the
       *  first entry applies to the first failure past the free attempts, the
       *  second to the next one, and so on. The last entry repeats. Default is
       *  [2, 5, 15, 60].
       */
        public int[] getRetryMinutes(){
            return toMinutes(pairRule.schedule);
        }


      /** Sets the lockouts (minutes) for the username + IP and IP rules. The
       *  last entry repeats, so a single entry gives a fixed interval. Examples:
       *  <ul>
       *  <li>setRetryMinutes(2): 2, 2, 2, ...</li>
       *  <li>setRetryMinutes(2, 5): 2, 5, 5, ...</li>
       *  <li>setRetryMinutes(2, 5, 15, 60): 2, 5, 15, 60, 60, ...</li>
       *  </ul>
       *  Entries must be at least 1.
       */
        public void setRetryMinutes(int... minutes){
            long[] schedule = toSchedule(minutes, "retryMinutes");
            pairRule.schedule = schedule;
            ipRule.schedule = schedule;
        }


      /** Returns how long (hours) after the last failure the username + IP and
       *  IP counts are forgotten. Default is 24.
       */
        public int getResetHours(){
            return (int) (pairRule.reset/3600000L);
        }

      /** Sets how long (hours) after the last failure the username + IP and IP
       *  counts are forgotten. Must be at least 1.
       */
        public void setResetHours(int hours){
            long ms = atLeast(1, hours, "resetHours")*3600000L;
            pairRule.reset = ms;
            ipRule.reset = ms;
        }


      /** Returns the number of failed logins allowed from an IP (any username)
       *  before it's locked out. Default is 10.
       */
        public int getIPFreeAttempts(){
            return ipRule.freeAttempts;
        }


      /** Sets the number of failed logins allowed from an IP (any username)
       *  before it's locked out. Must be at least 1.
       */
        public void setIPFreeAttempts(int freeAttempts){
            ipRule.freeAttempts = atLeast(1, freeAttempts, "ipFreeAttempts");
        }


      /** Returns the number of failed logins allowed for a username (any IP)
       *  before it's locked out. Default is 50.
       */
        public int getUserFreeAttempts(){
            return userRule.freeAttempts;
        }


      /** Sets the number of failed logins allowed for a username (any IP) before
       *  it's locked out. Must be at least 1.
       */
        public void setUserFreeAttempts(int freeAttempts){
            userRule.freeAttempts = atLeast(1, freeAttempts, "userFreeAttempts");
        }


      /** Returns the lockouts (minutes) for the username rule (any IP). Works
       *  like getRetryMinutes(). Default is [2, 5, 15]: short, since this rule
       *  can lock out the real user during an attack.
       */
        public int[] getUserRetryMinutes(){
            return toMinutes(userRule.schedule);
        }


      /** Sets the lockouts (minutes) for the username rule (any IP). Works like
       *  setRetryMinutes(). Entries must be at least 1.
       */
        public void setUserRetryMinutes(int... minutes){
            userRule.schedule = toSchedule(minutes, "userRetryMinutes");
        }


      /** Returns how long (minutes) after the last failure the username count
       *  (any IP) is forgotten. Default is 60.
       */
        public int getUserResetMinutes(){
            return (int) (userRule.reset/60000L);
        }


      /** Sets how long (minutes) after the last failure the username count (any
       *  IP) is forgotten. Must be at least 1.
       */
        public void setUserResetMinutes(int minutes){
            userRule.reset = atLeast(1, minutes, "userResetMinutes")*60000L;
        }



      /** Returns the max number of failure counts kept in memory. Beyond that,
       *  counts that aren't locked out are dropped. Default is 100,000.
       */
        public int getMaxEntries(){
            return maxEntries;
        }

      /** Sets the max number of failure counts kept in memory. Must be at least
       *  1.
       */
        public void setMaxEntries(int maxEntries){
            this.maxEntries = atLeast(1, maxEntries, "maxEntries");
        }


        private static int atLeast(int min, int value, String name){
            if (value<min) throw new IllegalArgumentException(name + " must be at least " + min);
            return value;
        }

        private static long[] minutes(int... minutes){
            return toSchedule(minutes, "minutes");
        }

        private static long[] toSchedule(int[] minutes, String name){
            if (minutes==null || minutes.length==0){
                throw new IllegalArgumentException(name + " must have at least one entry");
            }
            long[] schedule = new long[minutes.length];
            for (int i=0; i<minutes.length; i++){
                schedule[i] = atLeast(1, minutes[i], name)*60000L;
            }
            return schedule;
        }

        private static int[] toMinutes(long[] schedule){
            int[] minutes = new int[schedule.length];
            for (int i=0; i<schedule.length; i++) minutes[i] = (int) (schedule[i]/60000L);
            return minutes;
        }


      /** Returns how long (ms) a client must wait before its credentials can be
       *  checked, or 0 if they can be checked now.
       *  @param username Username, as it's used for lookups (e.g. lowercase if
       *  usernames are case-insensitive).
       *  @param ip Client IP address (see Authenticator.getClientIP). Null is
       *  treated as one "unknown" address.
       *  @param now Current time (ms), e.g. Authenticator.now().
       */
        public long getRetryAfter(String username, String ip, long now){
            long wait = 0;
            wait = Math.max(wait, getWait(pairKey(username, ip), pairRule, now));
            wait = Math.max(wait, getWait(ipKey(ip), ipRule, now));
            wait = Math.max(wait, getWait(userKey(username), userRule, now));
            return wait;
        }


      /** Records a failed login. Call after checking credentials that turned out
       *  to be invalid (not when the check was skipped because of a lockout).
       *  @param password The password (or other secret) that was tried. Only a
       *  hash is kept, to count a repeated wrong password once.
       *  @param now Current time (ms), e.g. Authenticator.now().
       */
        public void onFailure(String username, String ip, String password, long now){
            removeExpired(now);
            long attempt = hash(username, password);


          //Count each distinct attempt once. The username+ip counter remembers
          //recent attempts, so a repeat from the same client counts nowhere.
            Counter pair = getCounter(pairKey(username, ip), now);
            synchronized(pair){
                pairRule.reset(pair, now);
                if (pair.hasAttempt(attempt)) return;
                pair.addAttempt(attempt);
                pairRule.fail(pair, now);
            }

            Counter byIP = getCounter(ipKey(ip), now);
            synchronized(byIP){
                ipRule.reset(byIP, now);
                ipRule.fail(byIP, now);
            }

            Counter byUser = getCounter(userKey(username), now);
            boolean locked;
            synchronized(byUser){
                userRule.reset(byUser, now);
                boolean wasLocked = byUser.lockedUntil>now;
                userRule.fail(byUser, now);
                locked = !wasLocked && byUser.lockedUntil>now;
            }
            if (locked){
                log("Too many failed logins for \"" + username + "\" across IP addresses. Locked for " +
                ((byUser.lockedUntil-now)/1000) + " seconds.");
            }
        }


      /** Records a successful login. Resets the failure counts for the username
       *  + IP pair and for the IP. The username count (failures spread over many
       *  IPs) is not reset.
       */
        public void onSuccess(String username, String ip){
            counters.remove(pairKey(username, ip));
            counters.remove(ipKey(ip));
        }



      /** Records a request whose credentials were verified earlier (e.g. a login
       *  cache hit) rather than checked now. Resets the failure count for the IP
       *  (the client has valid credentials, which matters when many users share
       *  an IP, e.g. an office network), but not the count for the username + IP
       *  pair: otherwise a client could clear failures from another password
       *  check (e.g. a "change password" service) just by sending requests with
       *  credentials it already has.
       */
        public void onCachedLogin(String ip){
            counters.remove(ipKey(ip));
        }


      /** Used to limit the volume of an action (e.g. emails sent per IP). Records
       *  an attempt for the key and returns 0 if it's allowed: fewer than max
       *  attempts in the last window ms. Otherwise, returns how long (ms) until
       *  the next attempt is allowed, and doesn't record it. Example:
       *  <pre>
       *  long wait = throttle.checkRate("sendcode:" + ip, 3, 10*60000L, now());
       *  if (wait>0) throw new LockedException(wait, "Too many requests.");
       *  </pre>
       *  Keys are separate from the login failure counts. Use a prefix that
       *  names the action, as above.
       *  @param now Current time (ms), e.g. Authenticator.now().
       */
        public long checkRate(String key, int max, long window, long now){
            atLeast(1, max, "max");
            if (window<1) throw new IllegalArgumentException("window must be at least 1");
            removeExpired(now);
            Window w = windows.computeIfAbsent(key, k -> new Window());
            synchronized(w){
                w.window = window;
                while (!w.times.isEmpty() && w.times.peekFirst()<=now-window) w.times.pollFirst();
                if (w.times.size()>=max){
                    return Math.max(1, w.times.peekFirst()+window-now);
                }
                w.times.addLast(now);
                return 0;
            }
        }



      /** Returns a message for a client that has to wait after too many failed
       *  logins, e.g. "Too many failed login attempts. Try again in 2 minutes."
       */
        private static String getMessage(long wait){
            return "Too many failed login attempts. Try again in " + getWaitTime(wait) + ".";
        }


      /** Returns a message for a client over a volume limit (see checkRate),
       *  e.g. "Too many requests. Try again in 4 minutes."
       */
        private static String getRateMessage(long wait){
            return "Too many requests. Try again in " + getWaitTime(wait) + ".";
        }


      /** Returns a wait time for a message, e.g. "2 minutes" or "1 second". */
        private static String getWaitTime(long wait){
            long seconds = Math.max(1, (wait+999)/1000);
            if (seconds<60) return seconds + (seconds==1 ? " second" : " seconds");
            long minutes = (seconds+59)/60;
            return minutes + (minutes==1 ? " minute" : " minutes");
        }


        private static class Rule {
            volatile int freeAttempts;
            volatile long[] schedule; //lockouts (ms), last entry repeats
            volatile long reset;

            Rule(int freeAttempts, long[] schedule, long reset){
                this.freeAttempts = freeAttempts;
                this.schedule = schedule;
                this.reset = reset;
            }

          /** Forgets old failures. Call while holding the counter's lock. */
            void reset(Counter c, long now){
                if (c.failures>0 && now-c.lastFailure>=reset && c.lockedUntil<=now){
                    c.failures = 0;
                    c.lockedUntil = 0;
                    c.clearAttempts();
                }
            }

          /** Records a failure: after freeAttempts failures, locks the counter
           *  for the next entry in the schedule (the last entry repeats).
           */
            void fail(Counter c, long now){
                c.failures++;
                c.lastFailure = now;
                if (c.failures>=freeAttempts){
                    long[] schedule = this.schedule;
                    int i = Math.min(c.failures-freeAttempts, schedule.length-1);
                    c.lockedUntil = Math.max(c.lockedUntil, now+schedule[i]);
                }
            }

            boolean isExpired(Counter c, long now){
                return c.lockedUntil<=now && now-c.lastFailure>=reset;
            }
        }


      /** Recent attempts for a volume limit (see checkRate), oldest first. */
        private static class Window {
            final java.util.ArrayDeque<Long> times = new java.util.ArrayDeque<>();
            long window;
            boolean isExpired(long now){
                Long last = times.peekLast();
                return last==null || last<=now-window;
            }
        }


        private static class Counter {
            final Rule rule;
            int failures;
            long lastFailure;
            long lockedUntil;
            private final long[] attempts = new long[8]; //recent attempt hashes
            private int numAttempts;

            Counter(Rule rule){
                this.rule = rule;
            }

            boolean hasAttempt(long attempt){
                for (int i=0; i<Math.min(numAttempts, attempts.length); i++){
                    if (attempts[i]==attempt) return true;
                }
                return false;
            }

            void addAttempt(long attempt){
                attempts[numAttempts % attempts.length] = attempt;
                numAttempts++;
            }

            void clearAttempts(){
                numAttempts = 0;
            }
        }


        private long getWait(String key, Rule rule, long now){
            Counter c = counters.get(key);
            if (c==null) return 0;
            synchronized(c){
                return Math.max(0, c.lockedUntil-now);
            }
        }

        private Counter getCounter(String key, long now){
            Rule rule = key.startsWith("p:") ? pairRule : key.startsWith("i:") ? ipRule : userRule;
            return counters.computeIfAbsent(key, k -> new Counter(rule));
        }


      /** Removes counters with nothing left to remember, at most once per
       *  SWEEP_INTERVAL (or right away if there are more than maxEntries). If
       *  that's not enough, unlocked counters are dropped to stay under
       *  maxEntries (locked ones are kept).
       */
        private void removeExpired(long now){
            boolean full = counters.size()>maxEntries || windows.size()>maxEntries;
            long prev = lastSweep.get();
            if (!full && now-prev<SWEEP_INTERVAL) return;
            if (!lastSweep.compareAndSet(prev, now)) return; //another thread is sweeping

            counters.values().removeIf(c -> {
                synchronized(c){ return c.rule.isExpired(c, now); }
            });
            windows.values().removeIf(w -> {
                synchronized(w){ return w.isExpired(now); }
            });
            if (windows.size()>maxEntries){
                log("Too many rate limit entries (" + windows.size() + "). Dropping them.");
                windows.clear();
            }

            if (counters.size()>maxEntries){
                log("Too many login failure counters (" + counters.size() + "). Dropping unlocked ones.");
                counters.values().removeIf(c -> {
                    synchronized(c){ return c.lockedUntil<=now; }
                });
            }
        }


        private static String pairKey(String username, String ip){
            return "p:" + username + "\n" + ip;
        }

        private static String ipKey(String ip){
            return "i:" + ip;
        }

        private static String userKey(String username){
            return "u:" + username;
        }

      /** Returns a 64-bit HMAC of an attempt (username and password), computed
       *  with a random key generated when the JVM starts.
       */
        private static long hash(String username, String password){
            try{
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(KEY, "HmacSHA256"));
                byte[] u = (username==null ? "" : username).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                mac.update(new byte[]{(byte)(u.length>>>24), (byte)(u.length>>>16), (byte)(u.length>>>8), (byte)u.length});
                mac.update(u);
                if (password!=null){
                    mac.update((byte) 1);
                    mac.update(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                byte[] b = mac.doFinal();
                return java.nio.ByteBuffer.wrap(b).getLong();
            }
            catch(java.lang.Exception e){
                return System.nanoTime(); //can't happen, but fail safe (counts every attempt)
            }
        }

        private static final byte[] KEY = new byte[32];
        static {
            new java.security.SecureRandom().nextBytes(KEY);
        }


        private static void log(String msg){
            System.out.println("[LoginThrottle] " + msg);
        }

    }


  //**************************************************************************
  //** LockedException
  //**************************************************************************
  /** ServletException with a 429 (Too Many Requests) status, thrown when a
   *  client is locked out. Servlets can use getRetryAfter() to set a
   *  "Retry-After" response header.
   */
    public static class LockedException extends ServletException {
        private final long retryAfter;
        public LockedException(long retryAfter){
            this(retryAfter, LoginThrottle.getMessage(retryAfter));
        }

        public LockedException(long retryAfter, String message){
            super(429, message);
            this.retryAfter = retryAfter;
        }

      /** Returns how long (ms) the client must wait. */
        public long getRetryAfter(){
            return retryAfter;
        }

      /** Returns the value for a "Retry-After" response header (seconds). */
        public String getRetryAfterHeader(){
            return String.valueOf(Math.max(1, (retryAfter+999)/1000));
        }
    }

}
