package javaxt.express.email;

import java.util.*;
import java.util.concurrent.*;
import java.security.SecureRandom;

import javaxt.json.*;
import static javaxt.express.email.EmailUtils.*;


//******************************************************************************
//**  EmailAuth
//******************************************************************************
/**
 *   Utility class used to help implement email-authentication (e.g. 2FA, MFA).
 *   Generates 6-digit verification codes, sends them via an HTML email
 *   template, and manages pending/verified sessions with configurable expiry.
 *   Includes IP-based rate limiting (3 requests per 10 minutes) and brute-
 *   force protection (5 failed verification attempts before lockout). Expired
 *   sessions and stale rate-limit entries are cleaned up automatically every
 *   5 minutes.
 *
 ******************************************************************************/

public class EmailAuth {


    private EmailService emailService;
    private SecureRandom random = new SecureRandom();


    // Pending registrations awaiting email verification
    // Key: email, Value: RegistrationSession
    private ConcurrentHashMap<String, RegistrationSession> pendingSessions = new ConcurrentHashMap<>();

    // Verified registrations with session tokens
    // Key: sessionToken, Value: RegistrationSession (with code=null)
    private ConcurrentHashMap<String, RegistrationSession> verifiedSessions = new ConcurrentHashMap<>();

    // Session expiration times (milliseconds)
    private static final long PENDING_SESSION_EXPIRY = 10 * 60 * 1000;  // 10 minutes
    private static final long VERIFIED_SESSION_EXPIRY = 24 * 60 * 60 * 1000;  // 24 hours

    // Rate limiting: max registration starts per IP within the window
    private static final int MAX_STARTS_PER_IP = 3;
    private static final long RATE_LIMIT_WINDOW = 10 * 60 * 1000;  // 10 minutes

    // Brute-force protection: max failed verification attempts before lockout
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    // Rate limiting: track timestamps of /start calls per IP
    private ConcurrentHashMap<String, List<Long>> startRateByIP = new ConcurrentHashMap<>();

    // Brute-force protection: track failed verification attempts per email
    private ConcurrentHashMap<String, Integer> failedVerifyAttempts = new ConcurrentHashMap<>();

    // Cleanup scheduler
    private ScheduledExecutorService cleanupScheduler;

    // Cleanup callback for callers to hook into the periodic cleanup cycle
    private Runnable cleanupCallback;


    private javaxt.io.File templateFile;
    private long templateDate;
    private String htmlTemplate; //don't use directly! call getTemplate()
    private String emailSubject;
    private String companyName;
    private Map<String, String> fieldMap;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class.
   *  @param emailService Used to create and send verification emails
   *  @param templateFile HTML email template file. The template is hot-
   *  reloaded when the file changes on disk. Company name is extracted from
   *  a &lt;meta name="author"&gt; tag and the email subject from &lt;title&gt;.
   *  @param fieldMap Maps logical field names to placeholder strings in the
   *  template. Must contain "name" and "code" keys. Example:
      <pre>
        Map.ofEntries(
            Map.entry("name", "{{name}}"),
            Map.entry("code", "{{code}}")
        )
      </pre>
   */
    public EmailAuth(EmailService emailService, javaxt.io.File templateFile, Map<String, String> fieldMap) {
        if (emailService==null || templateFile==null) throw new IllegalArgumentException();


      //Set class variables
        this.emailService = emailService;
        this.templateFile = templateFile;
        this.templateDate = -1;
        this.fieldMap = fieldMap;


      //Parse template
        getTemplate();


      //Start cleanup task to remove expired sessions
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
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
  //** isValidSession
  //**************************************************************************
  /** Returns true if the session token is valid, not expired, and matches
   *  the given email address.
   */
    public boolean isValidSession(String email, String sessionToken) {
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            return false;
        }
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        RegistrationSession session = verifiedSessions.get(sessionToken);

        if (session == null || session.isExpired()) {
            if (session != null) {
                verifiedSessions.remove(sessionToken);
            }
            return false;
        }

        // Verify email matches the session
        return email.equalsIgnoreCase(session.email);
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

        RegistrationSession(String code, String email, JSONObject info) {
            this.code = code;
            this.email = email;
            this.info = info;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isVerified() {
            return code == null;
        }

        boolean isExpired() {
            long expiry = isVerified() ? VERIFIED_SESSION_EXPIRY : PENDING_SESSION_EXPIRY;
            return System.currentTimeMillis() - timestamp > expiry;
        }
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
   *  @param info Caller-provided data returned after successful verify
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
        RegistrationSession session = new RegistrationSession(code, email, info);
        pendingSessions.put(email, session);
        failedVerifyAttempts.remove(email);


      //Compile email
        String htmlBody = getTemplate();
        if (fieldMap!=null){
            htmlBody = htmlBody.replace(fieldMap.get("name"), escapeHtml(name));
            htmlBody = htmlBody.replace(fieldMap.get("code"), code);
        }


      //Send email
        try {
            EmailMessage msg = emailService.createEmail();
            msg.setFrom(emailService.getUserName(), companyName);
            msg.addRecipient(email);
            msg.setSubject(emailSubject);
            msg.setContent(htmlBody, "text/html");
            msg.send();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to send verification email to " + email + " with code " + code);
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
        if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
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
            int remaining = MAX_VERIFY_ATTEMPTS - failedVerifyAttempts.get(email);
            if (remaining <= 0) {
                pendingSessions.remove(email);
                failedVerifyAttempts.remove(email);
                throw new IllegalAccessException("Too many failed attempts. Please request a new code.");
            }
            throw new IllegalArgumentException("Invalid verification code. Please try again.");
        }


      //Code is valid - create verified session (code=null indicates verified)
        String sessionToken = generateSessionToken();
        RegistrationSession verifiedSession = new RegistrationSession(null, session.email, session.info);
        verifiedSessions.put(sessionToken, verifiedSession);


      //Remove from pending and clear failed attempts
        pendingSessions.remove(email);
        failedVerifyAttempts.remove(email);


      //Return session data as JSON
        JSONObject json = new JSONObject();
        json.set("token", sessionToken);
        json.set("email", verifiedSession.email);
        json.set("info", verifiedSession.info);
        return json;
    }


  //**************************************************************************
  //** checkIPRateLimit
  //**************************************************************************
  /** Returns true if the IP has not exceeded the rate limit for /start calls.
   *  Allows MAX_STARTS_PER_IP requests per RATE_LIMIT_WINDOW.
   */
    public boolean checkIPRateLimit(String ip) {
        if (ip == null) return true;
        long now = System.currentTimeMillis();
        long cutoff = now - RATE_LIMIT_WINDOW;

        List<Long> timestamps = startRateByIP.computeIfAbsent(ip, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized(timestamps){
            timestamps.removeIf(t -> t < cutoff);
            if (timestamps.size() >= MAX_STARTS_PER_IP) {
                return false;
            }
            timestamps.add(now);
        }
        return true;
    }


  //**************************************************************************
  //** generateCode
  //**************************************************************************
  /** Generates a 6-digit verification code
   */
    private String generateCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }


  //**************************************************************************
  //** generateSessionToken
  //**************************************************************************
  /** Generates a secure session token
   */
    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }





  //**************************************************************************
  //** getTemplate
  //**************************************************************************
  /** Used to parse and return an html email template. Ensures that the
   *  template is always fresh.
   */
    private String getTemplate(){
        long t = templateFile.exists() ? templateFile.getDate().getTime() : 0;
        if (t>templateDate){
            htmlTemplate = templateFile.getText();

          //Extract companyName from <meta name="author" content="...">
            companyName = "JavaXT";
            javaxt.html.Parser parser = new javaxt.html.Parser(htmlTemplate);
            javaxt.html.Element meta = parser.getElementByAttributes("meta", "name", "author");
            if (meta != null) {
                String content = meta.getAttribute("content");
                if (content != null && !content.trim().isEmpty()) {
                    companyName = content.trim();
                }
            }


          //Extract emailSubject from <title>...</title>
            emailSubject = "Registration Verification Code";
            javaxt.html.Element title = parser.getElementByTagName("title");
            if (title != null) {
                String text = title.getInnerText();
                if (text != null && !text.trim().isEmpty()) {
                    emailSubject = text.trim();
                }
            }

            templateDate = t;
        }
        return htmlTemplate;
    }


  //**************************************************************************
  //** escapeHtml
  //**************************************************************************
  /** Escapes HTML special characters
   */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
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

        // Clean rate limit entries with no recent timestamps
        long cutoff = System.currentTimeMillis() - RATE_LIMIT_WINDOW;
        startRateByIP.entrySet().removeIf(entry -> {
            List<Long> timestamps = entry.getValue();
            synchronized(timestamps){
                timestamps.removeIf(t -> t < cutoff);
                return timestamps.isEmpty();
            }
        });

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

}
