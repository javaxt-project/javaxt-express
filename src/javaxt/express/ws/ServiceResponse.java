package javaxt.express.ws;
import javaxt.json.*;


public class ServiceResponse {

    //private String id;
    private String contentType = "text/plain";
    private String contentDisposition = null;
    private Long contentLength;
    private int status = 200;
    //private String statusMessage; //not generally used or required
    private javaxt.utils.Date date; //<--Used for eTags and 304 responses
    private String authMessage;
    private Object response;

    public ServiceResponse(byte[] response){
        this.response = response;
        this.contentLength = Long.valueOf(response.length);
    }

    public ServiceResponse(javaxt.io.File file){
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

    public ServiceResponse(Throwable e){

        this(500, (e.getMessage()==null || e.getMessage().trim().length()==0) ? "Unspecified Web Services Error" : e.getMessage());
e.printStackTrace();
        String s = e.getClass().getName();
        s = s.substring(s.lastIndexOf(".")+1);
        String message = e.getLocalizedMessage();
        String error = (message != null) ? (s + ": " + message) : s;

        if (error.equalsIgnoreCase("NullPointerException")){
            for (StackTraceElement x : e.getStackTrace()){
                error+="\n"+x;
            }
        }
        response = getBytes(error);
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
    

    public Object getResponse() {
        return response;
    }
    
    
    
    private static byte[] getBytes(String str){
        try{
            return str.getBytes("UTF-8");
        }
        catch(Exception e){
            return null;
        }
    }
}