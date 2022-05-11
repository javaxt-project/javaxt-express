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
  /** Used to set the hostname and port of the server
   */
    public void setHost(String host, int port){
        setHost(host);
        setPort(port);
    }


  //**************************************************************************
  //** setHost
  //**************************************************************************
  /** Used to set the hostname or IP address of the server
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
  /** Returns the hostname or IP address of the server
   */
    public String getHost(){
        return host;
    }


  //**************************************************************************
  //** setPort
  //**************************************************************************
    public void setPort(int port){
        this.port = port;
        properties.setProperty("mail.smtp.port", port+"");
    }


  //**************************************************************************
  //** getPort
  //**************************************************************************
    public Integer getPort(){
        return port;
    }


  //**************************************************************************
  //** createEmail
  //**************************************************************************
    public EmailMessage createEmail(){
        return new EmailMessage(getSession());
    }


  //**************************************************************************
  //** getSession
  //**************************************************************************
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