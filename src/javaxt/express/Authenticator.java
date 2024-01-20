package javaxt.express;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;
import javaxt.http.servlet.ServletException;


//******************************************************************************
//**  Authenticator
//******************************************************************************
/**
 *   This class is used to authenticate requests via the HttpServlet class.
 *   It supports both "BASIC" and "NTLM" authentication methods. Also includes
 *   a handleRequest() method that can be used to handle authentication
 *   workflows used in JavaXT Express web applications.
 *
 *   Instances of this class are passed to the setAuthenticator() method in the
 *   javaxt.http.servlet.HttpServlet class.
 *
 *   The following snippit can be used to perform "BASIC" authentication with
 *   a username and password. This example assumes that there is a "User" class
 *   that implements the java.security.Principal interface. The getUser() and
 *   setUser() methods are used to update an internal cache.
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
 ******************************************************************************/

public class Authenticator implements javaxt.http.servlet.Authenticator, Cloneable {


  //Local variables
    private String auth;
    private String[] credentials;
    private String authenticationScheme;
    private HttpServletRequest request;


  //Global variables
    private static final ConcurrentHashMap<String, Object[]> cache = new ConcurrentHashMap<>();
    private static final long cacheExpiration = 30000; //30 seconds


  //**************************************************************************
  //** newInstance
  //**************************************************************************
  /** Creates a new instance of this class. This method is called whenever a
   *  new HTTP request is made to the server (see HttpServletRequest class)
   */
    public Authenticator newInstance(HttpServletRequest request){


      //Parse "Authorization" header
        String authenticationScheme = null;
        String[] credentials = null;
        String authorization = request.getHeader("Authorization");
        if (authorization!=null){
            int idx = authorization.indexOf(" ");
            authenticationScheme = authorization.substring(0, idx).toUpperCase();
            if (authenticationScheme.equals("BASIC")){


              //Decode the string
                String auth = new String(
                    javaxt.utils.Base64.decode(authorization.substring(idx+1))
                );


              //Parse credentials
                String username = auth.substring(0, auth.indexOf(":"));
                String password = auth.substring(auth.indexOf(":")+1);
                credentials = new String[]{username, password};

            }
            else if (authenticationScheme.equals("NTLM")){
                byte[] msg = javaxt.utils.Base64.decode(authorization.substring(idx+1));


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


                    if (domainName!=null){
                        credentials = new String[]{username.toString(), null};
                    }
                }
            }
        }


        try{

          //Create new Authenticator
            Object obj = this.clone();


          //Get Authenticator class
            Class c = this.getClass();
            java.lang.reflect.Field field;
            try{
                c.getDeclaredField("request");
            }
            catch(NoSuchFieldException e){
                c = c.getSuperclass();
            }


          //Update private fields
            field = c.getDeclaredField("request");
            field.setAccessible(true);
            field.set(obj, request);

            field = c.getDeclaredField("authenticationScheme");
            field.setAccessible(true);
            field.set(obj, authenticationScheme);

            field = c.getDeclaredField("credentials");
            field.setAccessible(true);
            field.set(obj, credentials);

            return (Authenticator) obj;
        }
        catch(Exception e){
            e.printStackTrace();
            throw new RuntimeException();
        }
    }


  //**************************************************************************
  //** getPrinciple
  //**************************************************************************
  /** Returns the java.security.Principal associated with an HTTP request.
   *  Override this method!
   */
    public java.security.Principal getPrinciple(){
        return null;
    }


  //**************************************************************************
  //** getCredentials
  //**************************************************************************
  /** Returns the credentials associated with an HTTP request. In the case of
   *  "BASIC" authentication, the credentials contain the username and
   *  password. In the case of "NTLM" authentication, the credentials only
   *  contain a username.
   */
    public String[] getCredentials() {
        return credentials;
    }


  //**************************************************************************
  //** authenticate
  //**************************************************************************
  /** Used to authenticate a client request. If the Authenticator fails to
   *  authenticate the client, this method throws a ServletException.
   */
    public void authenticate() throws ServletException {
        java.security.Principal user = getPrinciple();
        if (user==null) throw new ServletException();
    }


  //**************************************************************************
  //** getAuthType
  //**************************************************************************
  /** Returns the authentication scheme used to authenticate clients (e.g.
   *  "BASIC" or "NTLM").
   */
    public String getAuthType(){
        return authenticationScheme;
    }


  //**************************************************************************
  //** isUserInRole
  //**************************************************************************
  /** This method is a legacy feature from the Java Servlet API.
   */
    public boolean isUserInRole(String role){
        return false;
    }


  //**************************************************************************
  //** getUser
  //**************************************************************************
    protected User getUser(){

        User user = null;
        String username = (credentials!=null) ? credentials[0] : null;
        if (username!=null){

          //Check if the credentials correspond to a logout request. See the
          //handleRequest() method for more information.
            //if (username.equals("logout") && password.equals("logout")) return;


            synchronized(cache){
                Object[] arr = cache.get(username);
                if (arr!=null){
                    long lastUpdate = (long) arr[1];
                    if (System.currentTimeMillis()-lastUpdate<cacheExpiration){
                        user = (User) arr[0];
                    }
                    else{
                        cache.remove(username);
                        cache.notifyAll();
                    }
                }
            }
        }

        return user;
    }


  //**************************************************************************
  //** setUser
  //**************************************************************************
    protected void setUser(User user){
        if (user!=null){
            String username = (credentials!=null) ? credentials[0] : null;
            if (username!=null){
                synchronized(cache){
                    cache.put(username, new Object[]{user, System.currentTimeMillis()});
                    cache.notifyAll();
                }
            }
        }
    }


  //**************************************************************************
  //** handleRequest
  //**************************************************************************
  /** Used to process an authentication workflow. Returns true if a response
   *  was returned to the client. Example usage:
   <pre>
    public void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        Authenticator authenticator = (Authenticator) getAuthenticator(request);
        if (!authenticator.handleRequest(service, response)){

            //TODO: Send a response (file, json, text, etc)
        }
    }
   </pre>
   */
    public boolean handleRequest(String service, HttpServletResponse response)
        throws ServletException, IOException {




      //Send NTLM response as needed
        boolean ntlm = (auth!=null && auth.equals("NTLM"));
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


        boolean requestHandled = true;
        if (service.equals("login")){
            if (credentials==null){
                if (ntlm){
                    response.setStatus(401, "Access Denied");
                    response.setHeader("WWW-Authenticate", "NTLM");
                }
                else{
                    response.setStatus(401, "Access Denied");
                    response.setHeader("WWW-Authenticate", "Basic realm=\"Access Denied\""); //<--Prompt the user for thier credentials
                    response.setHeader("Cache-Control", "no-cache, no-transform");
                    response.setContentType("text/plain");
                    response.write("Unauthorized");
                }
            }
            else{
                try{
                    request.authenticate();
                    User user = getUser();
                    response.setContentType("text/plain");
                    response.write(user.getID()+"");
                }
                catch(Exception e){
                    response.setStatus(403, "Not Authorized");
                    response.setHeader("Cache-Control", "no-cache, no-transform");
                    response.setContentType("text/plain");
                    response.write("Unauthorized");
                }
            }

        }
        else if (service.equals("logoff") || service.equalsIgnoreCase("logout")){
            String username = (credentials!=null) ? credentials[0] : null;
            if (username!=null){
                synchronized(cache){
                    cache.remove(username);
                    cache.notifyAll();
                }
            }

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
        }
        else if (service.equals("whoami")){

            User user = getUser();
            if (user==null){

              //If the request has credentials, try authenticating the user
                String username = (credentials!=null) ? credentials[0] : null;
                if (!(username==null || username.equals("logout"))){
                    try{
                        request.authenticate();
                        user = getUser();
                    }
                    catch(Exception e){}
                }
            }


            if (user==null){
                response.setStatus(400, "Bad Request");
                response.write("");
            }
            else{
                response.setHeader("Cache-Control", "no-cache, no-transform");
                response.setContentType("text/plain");
                response.write(user.getID()+"");
            }
        }
        else{
            requestHandled = false;
        }

        return requestHandled;
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

}