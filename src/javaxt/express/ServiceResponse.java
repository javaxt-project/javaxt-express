package javaxt.express;

import java.util.*;
import java.io.IOException;

import javaxt.json.*;
import javaxt.express.utils.DateUtils;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;


//******************************************************************************
//**  ServiceResponse
//******************************************************************************
/**
 *   Used to encapsulate a response to a ServiceRequest
 *
 ******************************************************************************/

public class ServiceResponse {

    private String contentType = "text/plain";
    private String contentDisposition = null;
    private Long contentLength;
    private int status = 200;
    //private String statusMessage; //not generally used or required
    private javaxt.utils.Date date; //<--Used for eTags and 304 responses
    private String cacheControl;
    private String authMessage;
    private Object response;
    private HashMap<String, Object> properties = new HashMap<>();


    public ServiceResponse(byte[] response){
        this.response = response;
        this.contentLength = Long.valueOf(response.length);
    }

    public ServiceResponse(javaxt.io.File file){
        this.date = new javaxt.utils.Date(file.getDate());
        this.contentType = file.getContentType();
        this.contentLength = file.getSize();
        this.response = file;
    }

    public ServiceResponse(java.io.InputStream response){
        this.response = response;
    }

    public ServiceResponse(int status, String body){
        this(getBytes(body));
        this.status = status;
    }

    public ServiceResponse(String str){
        this(200, str);
    }

    public ServiceResponse(StringBuffer str){
        this(200, str.toString());
    }

    public ServiceResponse(StringBuilder str){
        this(200, str.toString());
    }

    public ServiceResponse(JSONObject json){
        this(200, json.toString());
        this.contentType = "application/json";
    }

    public ServiceResponse(JSONArray json){
        this(200, json.toString());
        this.contentType = "application/json";
    }

    public ServiceResponse(javaxt.sql.Model model){
        this(model.toJson());
    }

    public ServiceResponse(int status){
        this.status = status;
    }

    public ServiceResponse(Exception e){
        this((Throwable) e);
    }

    public ServiceResponse(Throwable e){

        this(500, (e.getMessage()==null || e.getMessage().trim().length()==0) ? "Unspecified Web Services Error" : e.getMessage());
        //e.printStackTrace();

        String s = e.getClass().getName();
        s = s.substring(s.lastIndexOf(".")+1);
        String message = e.getLocalizedMessage();
        StringBuilder error = new StringBuilder((message != null) ? (s + ": " + message) : s);

        //if (error.equalsIgnoreCase("NullPointerException")){
        for (StackTraceElement x : e.getStackTrace()){
            String err = x.toString();
            if (err.contains("org.eclipse.jetty")) break;
            error.append("\r\n");
            error.append(x);
        }

        System.out.println(error);
        response = getBytes(error.toString());
    }


  //**************************************************************************
  //** setContentType
  //**************************************************************************
  /** Used to set the "Content-Type" response header.
   *  @param contentType Content type (e.g. "text/plain", "application/json",
   *  "text/html; charset=utf-8", etc).
   */
    public void setContentType(String contentType){
        this.contentType = contentType;
    }


  //**************************************************************************
  //** setContentType
  //**************************************************************************
  /** Returns the "Content-Type" response header.
   */
    public String getContentType(){
        return contentType;
    }


  //**************************************************************************
  //** setContentDisposition
  //**************************************************************************
  /** Used to set the response to return an attachment with a given file name.
   *  @param fileName File name (e.g. "image.jpg", "document.docx", etc)
   */
    public void setContentDisposition(String fileName){
        if (fileName==null) contentDisposition = null;
        else contentDisposition = "attachment;filename=\"" + fileName + "\"";
    }


  //**************************************************************************
  //** getContentDisposition
  //**************************************************************************
  /** Returns the file name associated with this response. Returns null if the
   *  content disposition was not set.
   */
    public String getContentDisposition(){
        return contentDisposition;
    }


  //**************************************************************************
  //** setDate
  //**************************************************************************
  /** Used to set date/timestamp associated with this response. The date is
   *  critical for generating cacheable responses. See the send() method for
   *  more information.
   */
    public void setDate(javaxt.utils.Date date){
        this.date = date;
    }


  //**************************************************************************
  //** getDate
  //**************************************************************************
  /** Returns the date/timestamp associated with this response. Returns null
   *  if the response date is not set.
   */
    public javaxt.utils.Date getDate(){
        if (date==null) return null;
        return date.clone();
    }


  //**************************************************************************
  //** setContentLength
  //**************************************************************************
  /** Used to set the "Content-Length" response header. Note that the content
   *  length is not typically used by the send() method.
   *  @param contentLength Size of the response body, in bytes.
   */
    public void setContentLength(long contentLength){
        this.contentLength = contentLength;
    }


  //**************************************************************************
  //** getContentLength
  //**************************************************************************
  /** Returns the size of the response body, in bytes. Returns null if the
   *  content length is not set.
   */
    public Long getContentLength(){
        return contentLength;
    }


  //**************************************************************************
  //** setCacheControl
  //**************************************************************************
  /** Used to set the "Cache-Control" response header. This method is not
   *  commonly used. For example, the send() method will automatically set
   *  caching headers.
   *  @param cacheControl e.g. "no-cache, no-transform"
   */
    public void setCacheControl(String cacheControl){
        this.cacheControl = cacheControl;
    }


  //**************************************************************************
  //** getCacheControl
  //**************************************************************************
  /** Returns the "Cache-Control" response header (e.g. "no-cache").
   */
    public String getCacheControl(){
        return cacheControl;
    }

//    public void setStatus(int status){
//        this.status = status;
//    }


  //**************************************************************************
  //** getStatus
  //**************************************************************************
  /** Returns the 3-digit HTTP response code defined in the constructor when
   *  instantiating this class.
   */
    public int getStatus(){
        return status;
    }

//    public String getStatusMessage(){
//        return statusMessage;
//    }
//
//    public void setStatusMessage(String msg){
//        statusMessage = msg;
//    }
//


  //**************************************************************************
  //** setAuthMessage
  //**************************************************************************
  /** Used to set a custom message used in an authentication response header.
   *  @param msg An authentication message to send to clients (e.g. "Access
   *  Denied"). In BASIC authentication, the message will appear in the
   *  "WWW-Authenticate" response header (e.g. Basic realm="Access Denied").
   */
    public void setAuthMessage(String msg){
        authMessage = msg;
    }


  //**************************************************************************
  //** getAuthMessage
  //**************************************************************************
  /** Returns an authentication message (e.g. "Access Denied").
   */
    public String getAuthMessage(){
        return authMessage;
    }


  //**************************************************************************
  //** set
  //**************************************************************************
  /** Used to set a custom response header (e.g. ETag). This method is not
   *  commonly used and may be removed in a future release.
   */
    public void set(String key, Object val){
        properties.put(key, val);
    }


  //**************************************************************************
  //** get
  //**************************************************************************
  /** Returns a custom response header.
   */
    public Object get(String key){
        return properties.get(key);
    }


  //**************************************************************************
  //** getResponse
  //**************************************************************************
  /** Returns the object that will be used in the response body (e.g. String,
   *  Byte array, InputStream, File, etc). The response body is defined in the
   *  constructor when instantiating this class.
   */
    public Object getResponse() {
        return response;
    }


  //**************************************************************************
  //** send
  //**************************************************************************
  /** Used to send the response to a client. Note that responses with a fixed
   *  size and date are considered cacheable. Files, for example, have a fixed
   *  size and date and are cacheable. Strings and byte arrays with a date are
   *  cacheable. Cacheable responses include "ETag", "Last-Modified", and
   *  "Cache-Control" headers. If the "ETag" matches the "if-none-match"
   *  or if the "Last-Modified" matches the "if-modified-since" request
   *  headers then a 304 "Not Modified" is returned.
   *  @param response An javaxt.http.servlet.HttpServletResponse to write to.
   */
    public void send(HttpServletResponse response) throws IOException {

        HttpServletRequest request = response.getRequest();


        if (status==301 || status==307){
            response.setStatus(status);
            String location = new String((byte[]) this.getResponse());
            response.setHeader("Location", location);
            String msg =
            "<head>" +
            "<title>Document Moved</title>" +
            "</head>" +
            "<body>" +
            "<h1>Object Moved</h1>" +
            "This document may be found <a href=\"" + location + "\">here</a>" +
            "</body>";
            response.write(msg);
        }
        else{

          //Set general response headers
            response.setStatus(status);
            response.setContentType(contentType);



          //Add user-defined cache directives (not common)
            String eTag = (String) properties.get("ETag");
            if (eTag!=null) response.setHeader("ETag", eTag);
            String lastModified = (String) properties.get("Last-Modified");
            if (lastModified==null && date!=null) lastModified = DateUtils.getDate(date.getTime());
            if (lastModified!=null) response.setHeader("Last-Modified", lastModified);
            if (cacheControl!=null) response.setHeader("Cache-Control", cacheControl);
            if (status==304) return;



          //Set authentication header as needed
            String authType = request==null ? null : request.getAuthType();
            if (authMessage!=null && authType!=null){
                if (authType.equalsIgnoreCase("BASIC")){
                    response.setHeader("WWW-Authenticate", "Basic realm=\"" + authMessage + "\"");
                }
                else{
                    //Are there similar headers for non-BASIC auth?
                }
            }


          //Send body
            Object obj = this.getResponse();
            if (obj instanceof byte[]){ //string, json, etc
                byte[] b = (byte[]) obj;

                if (date!=null && status==200){ //send cacheable response
                    response.write(b, date.getTime());
                }
                else{ //send regular response
                    response.write(b);
                }

            }
            else if (obj instanceof javaxt.io.File){
                javaxt.io.File file = (javaxt.io.File) obj;


                if (date!=null && request!=null){
                    javaxt.utils.URL url = new javaxt.utils.URL(request.getURL());
                    long currVersion = date.toLong();
                    long requestedVersion = 0;
                    try{ requestedVersion = Long.parseLong(url.getParameter("v")); }
                    catch(Exception e){}

                    if (requestedVersion < currVersion){
                        url.setParameter("v", currVersion+"");
                        response.sendRedirect(url.toString(), true);
                        return;
                    }
                    else if (requestedVersion==currVersion){
                        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
                    }
                }



              //Set "Content-Disposition" header as needed
                if (contentDisposition!=null) response.setHeader("Content-Disposition", contentDisposition);


              //Set fileName and contentType. Note that when a fileName is
              //provided, the server returns a "Content-Disposition" header
              //which we may be different than what the caller spefified in
              //this response. To avoid ambiguities, we'll rely exclusively
              //on whatever the user specified for the "Content-Disposition"
                if (contentType==null) contentType = file.getContentType();
                String fileName = null;


              //Send file
                response.write(file.toFile(), fileName, contentType, true);

            }
            else if (obj instanceof java.io.InputStream){
                java.io.InputStream inputStream = (java.io.InputStream) obj;
                boolean compressOutput = true;


              //Set Content-Length response header as needed
                if (contentLength!=null){
                    response.setHeader("Content-Length", contentLength+"");
                    compressOutput = false;
                }


              //Send response
                try{
                    response.write(inputStream, compressOutput);
                }
                catch(Exception e){
                    try{inputStream.close();}catch(Exception ex){}
                }

            }
            else{
                //??
            }

        }
    }


  //**************************************************************************
  //** send
  //**************************************************************************
  /** Used to send the response to a client.
   */
    public void send(HttpServletResponse response, ServiceRequest req) throws IOException {
        send(response);
    }


  //**************************************************************************
  //** send
  //**************************************************************************
  /** Used to send the response to a client.
   *  @param response A javax.servlet.http.HttpServletResponse to write to.
   *  @param req A javaxt.express.ServiceRequest used to initiate the response.
   */
    public void send(javax.servlet.http.HttpServletResponse response, ServiceRequest req) throws IOException {
        send(new HttpServletResponse(req.getRequest(), response));
    }


  //**************************************************************************
  //** send
  //**************************************************************************
  /** Used to send the response to a client.
   *  @param response A jakarta.servlet.http.HttpServletResponse to write to.
   *  @param req A javaxt.express.ServiceRequest used to initiate the response.
   */
    public void send(jakarta.servlet.http.HttpServletResponse response, ServiceRequest req) throws IOException {
        send(new HttpServletResponse(req.getRequest(), response));
    }


  //**************************************************************************
  //** getBytes
  //**************************************************************************
    private static byte[] getBytes(String str){
        try{
            return str.getBytes("UTF-8");
        }
        catch(Exception e){
            return null;
        }
    }
}