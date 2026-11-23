package javaxt.express.email;

import java.util.*;


//******************************************************************************
//**  EmailTemplate
//******************************************************************************
/**
 *   Used to represent an email template and generate email messages with
 *   security codes.
 *
 ******************************************************************************/

public class EmailTemplate {

    private javaxt.io.File templateFile;
    private long templateDate;
    private String htmlTemplate; //don't use directly! call getTemplate()
    private String emailSubject;
    private String companyName;
    private Map<String, String> fieldMap;
    private Map<String, javaxt.io.File> inlineImages;
    private volatile String from;     //see setFrom
    private volatile String fromName;
    private volatile String replyTo;  //see setReplyTo


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class with a template file. Checks for
   *  file updates so changes/updates are always live.
   *  @param templateFile HTML email template file.
   *  @param fieldMap Maps logical field names to placeholder strings in the
   *  template: "code" (required) and "name" (optional). Example:
      <pre>
        Map&lt;String, String&gt; fieldMap = new HashMap&lt;&gt;();
        fieldMap.put("name", "{{name}}");
        fieldMap.put("code", "{{code}}");
      </pre>
   */
    public EmailTemplate(javaxt.io.File templateFile, Map<String, String> fieldMap){
        checkFieldMap(fieldMap);
        this.templateFile = templateFile;
        this.templateDate = -1;
        this.fieldMap = fieldMap;
        this.inlineImages = new HashMap<>();
        getTemplate();
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class with a template (string). Unlike
   *  a file template, this template is static.
   */
    public EmailTemplate(String htmlTemplate, Map<String, String> fieldMap) {
        if (htmlTemplate==null) throw new IllegalArgumentException();
        checkFieldMap(fieldMap);
        this.fieldMap = fieldMap;
        this.inlineImages = new HashMap<>();
        parseTemplate(htmlTemplate);
    }


  //**************************************************************************
  //** setFrom
  //**************************************************************************
  /** Sets the sender address of the emails created with this template. By
   *  default, emails are sent from the EmailService's username, if it's an
   *  email address. Use this when the  username isn't an email address, or
   *  to send from another address the account is allowed to use (e.g. a
   *  "noreply" address). Pass null to go back to the default.
   */
    public void setFrom(String emailAddress){
        setFrom(emailAddress, null);
    }


  //**************************************************************************
  //** setFrom
  //**************************************************************************
  /** Sets the sender address and name of the emails created with this
   *  template. The name is optional: without one, the name is the template's author (&lt;meta
   *  name="author"&gt;)
   */
    public void setFrom(String emailAddress, String name){
        this.from = toAddress(emailAddress, "sender");
        this.fromName = (name==null || name.trim().isEmpty()) ? null : name.trim();
    }


  //**************************************************************************
  //** getFrom
  //**************************************************************************
  /** Returns the sender address set with setFrom(), or null.
   */
    public String getFrom(){
        return from;
    }


  //**************************************************************************
  //** getFromName
  //**************************************************************************
  /** Returns the sender name set with setFrom(), or null.
   */
    public String getFromName(){
        return fromName;
    }


  //**************************************************************************
  //** setReplyTo
  //**************************************************************************
  /** Sets the address replies go to. By default, replies go to the sender.
   *  Pass null to go back to the default.
   */
    public void setReplyTo(String emailAddress){
        this.replyTo = toAddress(emailAddress, "reply-to");
    }


  //**************************************************************************
  //** getReplyTo
  //**************************************************************************
  /** Returns the reply-to address set with setReplyTo(), or null.
   */
    public String getReplyTo(){
        return replyTo;
    }


  //**************************************************************************
  //** createEmail
  //**************************************************************************
  /** Returns an email for the given address, ready to send: the template
   *  with the placeholders filled in (see the field map), the subject, and
   *  the sender (the EmailService's username, with the author as the name).
   *  @param values Values for the fields, e.g. "name" and "code". The name is
   *  HTML-escaped (null becomes "").
   */
    public EmailMessage createEmail(String emailAddress, Map<String, String> values,
        EmailService emailService) throws Exception {


      //Compile email
        String htmlBody = getTemplate();
        if (fieldMap!=null && values!=null){

            String nameField = fieldMap.get("name"); //optional
            if (nameField!=null) htmlBody = htmlBody.replace(nameField, escapeHtml(values.get("name"))); //null becomes ""

            String code = values.get("code");
            if (code!=null) htmlBody = htmlBody.replace(fieldMap.get("code"), code);
        }


      //Create email message
        EmailMessage msg = emailService.createEmail();
        String sender = getSender(emailService);
        if (sender==null) throw new Exception(
            "No sender address: the SMTP username (\"" + emailService.getUserName() +
            "\") isn't an email address. Use EmailTemplate.setFrom().");
        String name = fromName!=null ? fromName : companyName;
        msg.setFrom(sender, name!=null ? name : "JavaXT");
        if (replyTo!=null) msg.setReplyTo(replyTo);
        msg.addRecipient(emailAddress);
        msg.setSubject(emailSubject);
        msg.setContent(htmlBody, "text/html");
        for (String cid : inlineImages.keySet()){
            javaxt.io.File file = inlineImages.get(cid);
            msg.addInlineImage(cid, file);
        }
        return msg;
    }


  //**************************************************************************
  //** getSender
  //**************************************************************************
  /** Returns the address emails created with this template are sent from.
   */
    private String getSender(EmailService emailService){
        if (from!=null) return from;
        String u = emailService==null ? null : emailService.getUserName();
        if (u!=null) u = u.trim();
        return EmailUtils.isValidEmail(u) ? u : null;
    }


  //**************************************************************************
  //** getTemplate
  //**************************************************************************
  /** Used to parse and return an html email template. Ensures that the
   *  template is always fresh.
   */
    private synchronized String getTemplate(){
        if (templateFile==null) return htmlTemplate; //see parseTemplate
        long t = templateFile.exists() ? templateFile.getDate().getTime() : 0;
        if (t>templateDate){
            parseTemplate(templateFile.getText());
            templateDate = t;
        }
        return htmlTemplate;
    }


  //**************************************************************************
  //** parseTemplate
  //**************************************************************************
  /** Sets the html template, company name, email subject, and inline images
   *  (images need a templateFile).
   */
    private synchronized void parseTemplate(String html){

        htmlTemplate = html;

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


      //Extract images
        if (templateFile!=null)
        for (javaxt.html.Element element : parser.getElementsByTagName("img")){
            try{
                String src = element.getAttribute("src");
                String path = templateFile.MapPath(src);
                javaxt.io.File file = new javaxt.io.File(path);
                if (file.exists()){
                    String cid = file.getName(false);
                    inlineImages.put(cid, file);
                    htmlTemplate = htmlTemplate.replace(" src=\"" + src + "\"", " src=\"cid:"+cid+"\"");
                }
            }
            catch(Exception e){
                //e.printStackTrace();
            }
        }

    }


  //**************************************************************************
  //** checkFieldMap
  //**************************************************************************
  /** Used to validate a given fieldMap. Throws an IllegalArgumentException if
   *  the map is missing a "code" placeholder. Otherwise emails would go out
   *  without the code.
   */
    private static void checkFieldMap(Map<String, String> fieldMap){
        if (fieldMap==null || fieldMap.get("code")==null || fieldMap.get("code").isEmpty()){
            throw new IllegalArgumentException("fieldMap needs a \"code\" placeholder, e.g. {{code}}");
        }
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
  //** toAddress
  //**************************************************************************
  /** Returns a trimmed email address, or null if it's null or empty.
   */
    private static String toAddress(String emailAddress, String what){
        if (emailAddress==null || emailAddress.trim().isEmpty()) return null;
        emailAddress = emailAddress.trim();
        if (!EmailUtils.isValidEmail(emailAddress)){
            throw new IllegalArgumentException("Invalid " + what + " address: " + emailAddress);
        }
        return emailAddress;
    }
}