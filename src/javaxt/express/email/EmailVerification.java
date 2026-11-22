package javaxt.express.email;

import java.util.*;
import java.util.concurrent.*;
import java.security.SecureRandom;

import javaxt.json.*;
import static javaxt.express.email.EmailUtils.*;


//******************************************************************************
//**  EmailVerification
//******************************************************************************
/**
 *   Utility class used to help support email validation/verification workflows.
 *   Generates 6-digit verification codes, sends them via an HTML email
 *   template, and manages pending/verified sessions with configurable expiry.
 *   Includes brute-force protection (5 failed verification attempts before a
 *   new code is needed). Expired sessions are cleaned up automatically every
 *   5 minutes.
 *
 ******************************************************************************/

public class EmailVerification {


    private EmailService emailService;
    private EmailTemplate emailTemplate;
    private SecureRandom random = new SecureRandom();


    // Pending registrations awaiting email verification
    // Key: email, Value: RegistrationSession
    private ConcurrentHashMap<String, RegistrationSession> pendingSessions = new ConcurrentHashMap<>();

    // Verified registrations with session tokens
    // Key: sessionToken, Value: RegistrationSession (with code=null)
    private ConcurrentHashMap<String, RegistrationSession> verifiedSessions = new ConcurrentHashMap<>();

    // Session expiration times (milliseconds). Defaults shown; override via
    // the setters below.
    private long pendingSessionExpiry = 10 * 60 * 1000L;      // 10 minutes
    private long verifiedSessionExpiry = 24 * 60 * 60 * 1000L;  // 24 hours

    // Brute-force protection: max failed verification attempts before lockout
    private int maxVerifyAttempts = 5;

    // Brute-force protection: track failed verification attempts per email
    private ConcurrentHashMap<String, Integer> failedVerifyAttempts = new ConcurrentHashMap<>();

    // Cleanup scheduler
    private ScheduledExecutorService cleanupScheduler;

    // Cleanup callback for callers to hook into the periodic cleanup cycle
    private Runnable cleanupCallback;



  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class.
   *  @param emailService Used to create and send verification emails
   *  @param emailTemplate Used to create the emails: subject, sender, and the
   *  body with the code (see EmailTemplate).
   */
    public EmailVerification(EmailService emailService, EmailTemplate emailTemplate) {
        if (emailService==null || emailTemplate==null) throw new IllegalArgumentException();


      //Set class variables
        this.emailService = emailService;
        this.emailTemplate = emailTemplate;


      //Start cleanup task to remove expired sessions
        startCleanup();
    }


  //**************************************************************************
  //** setPendingSessionExpiry
  //**************************************************************************
  /** Sets how long an unverified (pending) code is valid, in milliseconds.
   *  Default is 10 minutes.
   */
    public void setPendingSessionExpiry(long milliseconds) {
        this.pendingSessionExpiry = milliseconds;
    }


  //**************************************************************************
  //** setVerifiedSessionExpiry
  //**************************************************************************
  /** Sets how long a verified session token is valid, in milliseconds.
   *  Default is 24 hours.
   */
    public void setVerifiedSessionExpiry(long milliseconds) {
        this.verifiedSessionExpiry = milliseconds;
    }


  //**************************************************************************
  //** setMaxVerifyAttempts
  //**************************************************************************
  /** Sets the number of failed verification attempts allowed before an email
   *  is locked out and must request a new code. Default is 5.
   */
    public void setMaxVerifyAttempts(int attempts) {
        this.maxVerifyAttempts = attempts;
    }



  //**************************************************************************
  //** getSession
  //**************************************************************************
  /** Returns a JSONObject with session details including email, token, and
   *  an info JSONObject that was used when calling sendVerificationEmail()
   */
    public JSONObject getSession(String email, String sessionToken){

        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            return null;
        }

        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        RegistrationSession session = verifiedSessions.get(sessionToken);
        if (session == null || session.isExpired()) {
            if (session != null) {
                verifiedSessions.remove(sessionToken);
            }
            return null;
        }

        // Verify email matches the session
        if (!email.equalsIgnoreCase(session.email)) return null;

        return getSession(session, sessionToken);
    }


  //**************************************************************************
  //** getSession
  //**************************************************************************
  /** Returns a JSONObject with session details (email, token, info) for the
   *  given token, or null if the token is unknown or expired. Unlike the
   *  two-argument overload, this looks the session up by token alone -- useful
   *  when the caller authenticates with a bearer token and doesn't already know
   *  the associated email.
   */
    public JSONObject getSession(String sessionToken){
        if (sessionToken == null || sessionToken.trim().isEmpty()) return null;
        RegistrationSession session = verifiedSessions.get(sessionToken);
        if (session == null || session.isExpired()) {
            if (session != null) verifiedSessions.remove(sessionToken);
            return null;
        }
        return getSession(session, sessionToken);
    }


  //**************************************************************************
  //** getSession
  //**************************************************************************
    private JSONObject getSession(RegistrationSession session, String sessionToken){
        JSONObject json = new JSONObject();
        json.set("token", sessionToken);
        json.set("email", session.email);
        json.set("info", session.info);
        return json;
    }


  //**************************************************************************
  //** generateSessionToken
  //**************************************************************************
  /** Generates a strong random 256-bit session token (64 hex chars).
   */
    public String generateSessionToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


  //**************************************************************************
  //** isValidSession
  //**************************************************************************
  /** Returns true if the session token is valid, not expired, and matches
   *  the given email address.
   */
    public boolean isValidSession(String email, String sessionToken) {
        JSONObject session = getSession(email, sessionToken);
        if (session == null || session.isEmpty()) {
            return false;
        }
        else{
            return true;
        }
    }


  //**************************************************************************
  //** invalidateSession
  //**************************************************************************
  /** Invalidates a verified session token (e.g. on logout). Returns true if a
   *  session was removed.
   */
    public boolean invalidateSession(String sessionToken){
        if (sessionToken == null) return false;
        return verifiedSessions.remove(sessionToken) != null;
    }


  //**************************************************************************
  //** sendVerificationEmail
  //**************************************************************************
  /** Sends a verification email with a 6-digit code. If a pending session
   *  already exists for this email, returns immediately without re-sending.
   *  Throws IllegalArgumentException if the email is invalid.
   *
   *  @param email The email address to send the verification code to
   *  @param name The name to use in the email greeting
   *  @param info Caller-provided metadata/information that gets stored with
   *  a registration session.
   */
    public void sendVerificationEmail(String email, String name, JSONObject info) throws Exception {


      //Validate email
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Email is required");
        }


      //Normalize
        email = email.trim().toLowerCase();


      //If a pending session already exists for this email and hasn't expired,
      //return success without sending another email. This prevents a bot from
      //spamming someone's inbox by hitting /start repeatedly.
        RegistrationSession existing = pendingSessions.get(email);
        if (existing != null && !existing.isExpired()) return;


      //Generate 6-digit code
        String code = generateCode();


      //Store pending session and reset failed attempts for this email
        RegistrationSession session = new RegistrationSession(code, email, info, pendingSessionExpiry);
        pendingSessions.put(email, session);
        failedVerifyAttempts.remove(email);


      //Create a map of values (HashMap allows a null name)
        Map<String, String> values = new HashMap<>();
        values.put("name", name);
        values.put("code", code);


      //Send email
        try {
            EmailMessage msg = emailTemplate.createEmail(email, values, emailService);
            msg.send();
        }
        catch (Exception e) {
            pendingSessions.remove(email);
            throw e;
        }
    }


  //**************************************************************************
  //** verify
  //**************************************************************************
  /** Verifies a security code and creates authenticated session. Throws an
   *  IllegalArgumentException that can be used to generate 400 response codes
   *  and IllegalAccessException for 429 response codes.
   */
    public JSONObject verify(String email, String code) throws Exception {

      //Check the code
        RegistrationSession session = checkCode(email, code);


      //Code is valid - create verified session (code=null indicates verified)
        String sessionToken = generateSessionToken();
        RegistrationSession verifiedSession = new RegistrationSession(null, session.email, session.info, verifiedSessionExpiry);
        verifiedSessions.put(sessionToken, verifiedSession);


      //Return session data as JSON
        return getSession(verifiedSession, sessionToken);
    }


  //**************************************************************************
  //** verifyCode
  //**************************************************************************
  /** Checks a security code, like verify(), without creating a session.
   *  Throws an IllegalArgumentException if the code is wrong or expired (400)
   *  or an IllegalAccessException after too many wrong codes (429).
   */
    public void verifyCode(String email, String code) throws Exception {
        checkCode(email, code);
    }


  //**************************************************************************
  //** generateCode
  //**************************************************************************
  /** Generates a 6-digit verification code
   */
    public String generateCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }


  //**************************************************************************
  //** checkCode
  //**************************************************************************
  /** Checks a code and returns its pending session, which is removed (a
   *  code can only be used once). See verify().
   */
    private RegistrationSession checkCode(String email, String code) throws Exception {


      //Validate required fields
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Email is required");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification code is required");
        }


      //Normalize
        email = email.trim().toLowerCase();
        code = code.trim();


      //Check if this email is locked out from too many failed attempts
        Integer attempts = failedVerifyAttempts.get(email);
        if (attempts != null && attempts >= maxVerifyAttempts) {
            pendingSessions.remove(email);
            failedVerifyAttempts.remove(email);
            throw new IllegalAccessException("Too many failed attempts. Please request a new code.");
        }


      //Look up pending session
        RegistrationSession session = pendingSessions.get(email);

        if (session == null) {
            throw new IllegalArgumentException("No registration found for this email. Please start over.");
        }

        if (session.isExpired()) {
            pendingSessions.remove(email);
            failedVerifyAttempts.remove(email);
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }


      //Verify code
        if (!session.code.equals(code)) {
            failedVerifyAttempts.merge(email, 1, Integer::sum);
            int remaining = maxVerifyAttempts - failedVerifyAttempts.get(email);
            if (remaining <= 0) {
                pendingSessions.remove(email);
                failedVerifyAttempts.remove(email);
                throw new IllegalAccessException("Too many failed attempts. Please request a new code.");
            }
            throw new IllegalArgumentException("Invalid verification code. Please try again.");
        }


      //Code is valid. Remove from pending and clear failed attempts.
        pendingSessions.remove(email);
        failedVerifyAttempts.remove(email);
        return session;
    }



  //**************************************************************************
  //** setCleanupCallback
  //**************************************************************************
  /** Sets a callback that is invoked during each periodic cleanup cycle.
   *  Callers can use this to perform their own cleanup tasks (e.g. removing
   *  expired form sessions).
   */
    public void setCleanupCallback(Runnable callback) {
        this.cleanupCallback = callback;
    }



  //**************************************************************************
  //** startCleanup
  //**************************************************************************
  /** Starts the cleanup task, on a daemon thread (so it doesn't keep the JVM
   *  from exiting).
   */
    private void startCleanup(){
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EmailVerification-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }


  //**************************************************************************
  //** cleanupExpiredSessions
  //**************************************************************************
  /** Removes expired sessions from both maps
   */
    private void cleanupExpiredSessions() {

        // Clean pending sessions
        pendingSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Clean verified sessions
        verifiedSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Clean failed attempt counters for emails with no pending session
        failedVerifyAttempts.entrySet().removeIf(entry -> !pendingSessions.containsKey(entry.getKey()));


        // Call cleanup callback if set
        if (cleanupCallback != null) {
            try {
                cleanupCallback.run();
            }
            catch (Exception e) {}
        }
    }


  //**************************************************************************
  //** RegistrationSession
  //**************************************************************************
  /** Holds registration data. When code is non-null, session is pending
   *  verification (10 min expiry). When code is null, session is verified
   *  (24 hour expiry).
   */
    private static class RegistrationSession {
        String code;  // null after verification
        String email;
        JSONObject info;
        long timestamp;
        long expiry;   // milliseconds this session is valid for

        RegistrationSession(String code, String email, JSONObject info, long expiry) {
            this.code = code;
            this.email = email;
            this.info = info;
            this.timestamp = System.currentTimeMillis();
            this.expiry = expiry;
        }

        boolean isVerified() {
            return code == null;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > expiry;
        }
    }
}
