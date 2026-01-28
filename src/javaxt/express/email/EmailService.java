package javaxt.express.email;
import java.util.*;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

//******************************************************************************
//**  EmailService
//******************************************************************************
/**
 *   Used to represent all of the information required to connect to an SMTP
 *   email server.
 *
 ******************************************************************************/

public class EmailService {

    private Properties properties;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private Authenticator authenticator;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate a new instance of this class
   *  @param host SMTP host (e.g. "smtp.mail.yahoo.com")
   *  @param port SMTP port
   *  @param username Username (typically an email address) authorized to send
   *  or receive email messages
   *  @param password Password associated with the username
   */
    public EmailService(String host, int port, String username, String password) {
        this.username = username;
        this.password = password;
        authenticator = new Authenticator(username, password);
        properties = new Properties();

        setHost(host, port);

        properties.setProperty("mail.smtp.submitter", username);
        properties.setProperty("mail.smtp.auth", "true");

        //properties.setProperty("mail.smtp.host", host);
        //properties.setProperty("mail.smtp.port", port+"");
        properties.setProperty("mail.smtp.ssl.enable", "true");
        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        //properties.setProperty("mail.debug", "true");
    }


  //**************************************************************************
  //** setHost
  //**************************************************************************
  /** Used to set the hostname and port of the SMTP server
   */
    public void setHost(String host, int port){
        setHost(host);
        setPort(port);
    }


  //**************************************************************************
  //** setHost
  //**************************************************************************
  /** Used to set the hostname or IP address of the SMTP server
   */
    public void setHost(String host){
        if (host==null){
            this.host = null;
        }
        else{
            host = host.trim();
            if (host.contains(":")){
                try{
                    this.host = host.substring(0, host.indexOf(":"));
                    this.port = Integer.valueOf(host.substring(host.indexOf(":")+1));
                }
                catch(Exception e){
                    this.host = host; //eg file paths
                }
            }
            else{
                this.host = host;
            }
        }
        properties.setProperty("mail.smtp.host", host);
    }


  //**************************************************************************
  //** getHost
  //**************************************************************************
  /** Returns the hostname or IP address of the SMTP server
   */
    public String getHost(){
        return host;
    }


  //**************************************************************************
  //** setPort
  //**************************************************************************
  /** Used to set the port of the SMTP server
   */
    public void setPort(int port){
        this.port = port;
        properties.setProperty("mail.smtp.port", port+"");
    }


  //**************************************************************************
  //** getPort
  //**************************************************************************
  /** Returns the port of the SMTP server
   */
    public Integer getPort(){
        return port;
    }


  //**************************************************************************
  //** getUserName
  //**************************************************************************
  /** Returns the username used to connect to the SMTP server
   */
    public String getUserName(){
        return username;
    }


  //**************************************************************************
  //** getPassword
  //**************************************************************************
  /** Returns the password used to connect to the SMTP server
   */
    public String getPassword(){
        return password;
    }


  //**************************************************************************
  //** enableTLS
  //**************************************************************************
  /** Used to enable TLS. By default, the EmailService is configured to
   *  communicate with an SMTP server over SSL. This method will configure
   *  properties commonly used with TLS and disable default SSL properties.
   */
    public void enableTLS(){
        properties.setProperty("mail.smtp.ssl.enable", "false");
        properties.remove("mail.smtp.socketFactory.class");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
    }


  //**************************************************************************
  //** disableTLS
  //**************************************************************************
  /** Used to disable TLS. Update properties commonly used with TLS and
   *  enables default SSL properties.
   */
    public void disableTLS(){
        properties.setProperty("mail.smtp.ssl.enable", "true");
        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        properties.remove("mail.smtp.starttls.enable");
        properties.remove("mail.smtp.starttls.required");
    }


  //**************************************************************************
  //** setProperty
  //**************************************************************************
  /** Used to set/update a property used to connect to a SMTP server.
   *  @param key Property name/key (e.g. "mail.smtp.ssl.enable")
   *  @param value Property value (e.g. "true)
   */
    public void setProperty(String key, String value){
        properties.setProperty(key, value);
    }


  //**************************************************************************
  //** getProperty
  //**************************************************************************
  /** Returns a property used to connect to a SMTP server.
   */
    public String getProperty(String key){
        return properties.getProperty(key);
    }


  //**************************************************************************
  //** createEmail
  //**************************************************************************
  /** Returns a new email message that can be sent via the SMPT server. Note
   *  that this method calls getSession() so you don't have to.
   */
    public EmailMessage createEmail(){
        return new EmailMessage(getSession());
    }


  //**************************************************************************
  //** getSession
  //**************************************************************************
  /** Returns a new email session.
   */
    public Session getSession() {
        return Session.getInstance(properties, authenticator);
    }


  //**************************************************************************
  //** Authenticator
  //**************************************************************************
    private class Authenticator extends jakarta.mail.Authenticator {
        private PasswordAuthentication authentication;

        public Authenticator(String username, String password) {
            authentication = new PasswordAuthentication(username, password);
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return authentication;
        }
    }
}