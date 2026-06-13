package javaxt.express.email;

import java.util.*;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.activation.DataHandler;


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
    private ArrayList<InlineImage> inlineImages;
    private Message message;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new email message bound to the given mail session.
   */
    public EmailMessage(Session session) {
        this.message = new MimeMessage(session);
        this.to = new ArrayList<>();
        this.inlineImages = new ArrayList<>();
    }


  //**************************************************************************
  //** setSubject
  //**************************************************************************
  /** Sets the email subject line.
   */
    public void setSubject(String subject){
        this.subject = subject;
    }


  //**************************************************************************
  //** setContent
  //**************************************************************************
  /** Sets the email body using the current content type.
   */
    public void setContent(String content){
        this.content = content;
    }


  //**************************************************************************
  //** setContent
  //**************************************************************************
  /** Sets the email body and its MIME content type (e.g. "text/html").
   */
    public void setContent(String content, String contentType){
        this.content = content;
        this.contentType = contentType;
    }


  //**************************************************************************
  //** setFrom
  //**************************************************************************
  /** Sets the sender's email address and display name.
   */
    public void setFrom(String emailAddress, String alias) throws Exception {
        from = new InternetAddress(emailAddress, alias);
    }


  //**************************************************************************
  //** addRecipient
  //**************************************************************************
  /** Adds a TO recipient to the email.
   */
    public void addRecipient(String emailAddress) throws Exception {
        to.add(new InternetAddress(emailAddress));
    }


  //**************************************************************************
  //** addInlineImage
  //**************************************************************************
  /** Attaches an image as an inline (CID-referenced) MIME part. Convenience
   *  overload that reads the image bytes and MIME type from a file.
   */
    public void addInlineImage(String cid, javaxt.io.File file){
        if (cid==null || file==null || !file.exists()){
            throw new IllegalArgumentException();
        }
        addInlineImage(cid, file.getBytes().toByteArray(), file.getContentType());
    }


  //**************************************************************************
  //** addInlineImage
  //**************************************************************************
  /** Attaches an image as an inline (CID-referenced) MIME part. The image is
   *  embedded in the message body rather than linked externally, so most
   *  clients render it without the "remote images blocked" prompt. Reference
   *  it from the HTML body with &lt;img src="cid:CID"&gt; where CID matches
   *  the value passed here.
   *  @param cid Content-ID used to reference the image from the HTML body
   *  @param data Raw image bytes
   *  @param mimeType Image MIME type (e.g. "image/png", "image/jpeg")
   */
    public void addInlineImage(String cid, byte[] data, String mimeType) {
        if (cid==null || data==null || mimeType==null){
            throw new IllegalArgumentException();
        }
        inlineImages.add(new InlineImage(cid, data, mimeType));
    }


  //**************************************************************************
  //** send
  //**************************************************************************
  /** Sends the email via the EmailService.
   */
    public void send() throws Exception {
        message.addFrom(new InternetAddress[]{from});
        for (InternetAddress recipient : to){
            message.addRecipient(RecipientType.TO, recipient);
        }
        message.setSubject(subject);

        if (inlineImages.isEmpty()){
            message.setContent(content, contentType);
        }
        else{
            MimeMultipart related = new MimeMultipart("related");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(content, contentType);
            related.addBodyPart(htmlPart);

            for (InlineImage img : inlineImages){
                MimeBodyPart imgPart = new MimeBodyPart();
                ByteArrayDataSource ds = new ByteArrayDataSource(img.data, img.mimeType);
                imgPart.setDataHandler(new DataHandler(ds));
                imgPart.setHeader("Content-ID", "<" + img.cid + ">");
                imgPart.setDisposition(MimeBodyPart.INLINE);
                related.addBodyPart(imgPart);
            }

            message.setContent(related);
        }

        Transport.send(message);
    }


  //**************************************************************************
  //** InlineImage
  //**************************************************************************
    private static class InlineImage {
        final String cid;
        final byte[] data;
        final String mimeType;
        InlineImage(String cid, byte[] data, String mimeType) {
            this.cid = cid;
            this.data = data;
            this.mimeType = mimeType;
        }
    }
}