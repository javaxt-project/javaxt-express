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
        msg.setFrom(emailService.getUserName(), companyName);
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
}