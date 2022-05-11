package javaxt.express.email;

import java.util.*;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;

//******************************************************************************
//**  EmailMessage
//******************************************************************************
/**
 *   Used to create and send an email message
 *
 ******************************************************************************/

public class EmailMessage {

    private String subject = "UNTITLED";
    private String content = "";
    private String contentType = "text/plain";
    private InternetAddress from;
    private ArrayList<InternetAddress> to;
    private Message message;

  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public EmailMessage(Session session) {
        this.message = new MimeMessage(session);
        this.to = new ArrayList<>();
    }

    public void setSubject(String subject){
        this.subject = subject;
    }

    public void setContent(String content){
        this.content = content;
    }

    public void setContent(String content, String contentType){
        this.content = content;
        this.contentType = contentType;
    }

    public void setFrom(String emailAddress, String alias) throws Exception {
        from = new InternetAddress(emailAddress, alias);
    }

    public void addRecipient(String emailAddress) throws Exception {
        to.add(new InternetAddress(emailAddress));
    }

    public void send() throws Exception {
        message.addFrom(new InternetAddress[]{from});
        for (InternetAddress recipient : to){
            message.addRecipient(RecipientType.TO, recipient);
        }
        message.setSubject(subject);
        message.setContent(content, contentType);
        Transport.send(message);
    }
}