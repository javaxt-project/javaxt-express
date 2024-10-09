package javaxt.express;

import java.util.*;
import java.io.IOException;

import javaxt.json.*;
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


    public void setContentType(String contentType){
        this.contentType = contentType;
    }

    public String getContentType(){
        return contentType;
    }

    public void setContentDisposition(String fileName){
        if (fileName==null) contentDisposition = null;
        else contentDisposition = "attachment;filename=\"" + fileName + "\"";
    }

    public String getContentDisposition(){
        return contentDisposition;
    }

    public void setDate(javaxt.utils.Date date){
        this.date = date;
    }

    public javaxt.utils.Date getDate(){
        if (date==null) return null;
        return date.clone();
    }

    public void setContentLength(long contentLength){
        this.contentLength = contentLength;
    }

    public Long getContentLength(){
        return contentLength;
    }

    //e.g. "no-cache, no-transform"
    public void setCacheControl(String cacheControl){
        this.cacheControl = cacheControl;
    }
    //e.g. "no-cache, no-transform"
    public String getCacheControl(){
        return cacheControl;
    }

//    public void setStatus(int status){
//        this.status = status;
//    }

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

    //"WWW-Authenticate", "Basic realm=\"Access Denied\""
    public void setAuthMessage(String msg){
        authMessage = msg;
    }

    public String getAuthMessage(){
        return authMessage;
    }

    public void set(String key, Object val){
        properties.put(key, val);
    }

    public Object get(String key){
        return properties.get(key);
    }


    public Object getResponse() {
        return response;
    }


  //**************************************************************************
  //** send
  //**************************************************************************
  /** Used to send the response to a client by generating an
   *  HttpServletResponse
   *  @param response An HttpServletResponse to write to
   */
    public void send(HttpServletResponse response) throws IOException {
        send(response, null);
    }


  //**************************************************************************
  //** send
  //**************************************************************************
  /** Used to send the response to a client by generating an
   *  HttpServletResponse
   *  @param response An HttpServletResponse to write to
   *  @param req The ServiceRequest associated with this response
   */
    public void send(HttpServletResponse response, ServiceRequest req) throws IOException {

        HttpServletRequest request = req==null ? null : req.getRequest();



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
            response.setContentType(this.getContentType());
            response.setStatus(status);


          //Set cache directives
            String eTag = (String) properties.get("ETag");
            if (eTag!=null) response.setHeader("ETag", eTag);
            String lastModified = (String) properties.get("Last-Modified");
            if (lastModified!=null) response.setHeader("Last-Modified", lastModified);
            //this.setHeader("Expires", "Sun, 30 Sep 2018 16:23:15 GMT  ");
            if (cacheControl!=null) response.setHeader("Cache-Control", cacheControl);
            if (status==304) return;



          //Set authentication header as needed
            String authType = request==null ? null : request.getAuthType();
            if (authMessage!=null && authType!=null){
                //"WWW-Authenticate", "Basic realm=\"Access Denied\""
                if (authType.equalsIgnoreCase("BASIC")){
                    response.setHeader("WWW-Authenticate", "Basic realm=\"" + authMessage + "\"");
                }
            }


          //Send body
            Object obj = this.getResponse();
            if (obj instanceof javaxt.io.File){
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
                String contentDisposition = this.getContentDisposition();
                if (contentDisposition!=null) response.setHeader("Content-Disposition", contentDisposition);


              //Set fileName and contentType. Note that when a fileName is
              //provided, the server returns a "Content-Disposition" header
              //which we may be different than what the caller spefified in
              //this response. To avoid ambiguities, we'll rely exclusively
              //on whatever the user specified for the "Content-Disposition"
                String contentType = file.getContentType();
                String fileName = null;


              //Send file
                response.write(file.toFile(), fileName, contentType, true);
            }
            else if (obj instanceof java.io.InputStream){
              //Set Content-Length response header
                if (contentLength!=null){
                    response.setHeader("Content-Length", contentLength+"");
                }

                try (java.io.InputStream inputStream = (java.io.InputStream) obj){
                    response.write(inputStream, true);
                }
            }
            else{
                response.write((byte[]) obj, true);
            }

        }
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