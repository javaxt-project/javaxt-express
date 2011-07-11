package javaxt.portal;

//******************************************************************************
//**  Download Class
//******************************************************************************
/**
 *   Used to serve up files and images. 
 *
 ******************************************************************************/

public class Download {

    private javax.servlet.http.HttpServletRequest request;
    private javax.servlet.http.HttpServletResponse response;
    private javaxt.io.Directory share;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Instantiates the class and writes response to the ServletOutputStream.
   * @param share Path to the base directory.
   * @param request HttpServletRequest containing request headers and query string.
   * @param response HttpServletResponse where the file will be written to.
   */
    public Download(javax.servlet.http.HttpServletRequest request,
            javax.servlet.http.HttpServletResponse response, javaxt.io.Directory share){

        this.request = request;
        this.response = response;
        this.share = share;
        processRequest();
    }


  //**************************************************************************
  //** processRequest
  //**************************************************************************
  /** Handles downloads. */

    private void processRequest(){


      //Get requested file from the querystring
        String filename = null;
        java.util.Enumeration<String> it = request.getParameterNames();
        while (it.hasMoreElements()){
            String key = it.nextElement();
            if (key.equalsIgnoreCase("img") || key.equalsIgnoreCase("image") ||
                key.equalsIgnoreCase("file") || key.equalsIgnoreCase("filename")){
                filename = request.getParameter(key);
                if (filename!=null){
                    break;
                }
            }
        }
        if (filename==null) filename = request.getQueryString();


      //If no file found in the quesrystring, use the path from the url
        if (filename==null){
            String Path = javaxt.portal.Utils.getPath(request);
            String path = new javaxt.utils.URL(request.getRequestURL().toString()).getPath();
            path = path.substring(path.indexOf(Path)).substring(Path.length());

            if (path.length()>1 && path.startsWith("/")) path = path.substring(1);
            
          //Special case when app is deployed as root
            String contextPath = request.getContextPath();
            if (contextPath.length()>1){
                contextPath = contextPath.substring(1);
                if (Path.equals("/") && path.startsWith(contextPath)){
                    if (path.contains("/")){
                        path = path.substring(path.indexOf("/"));
                        if (path.startsWith("/")){
                            if (path.length()>1) path = path.substring(1);
                            else path = "";
                        }
                    }
                    else path = "";
                }
            }


            if (!path.endsWith("/")) filename = path;
        }


      //Remove any leading path separators
        if (filename!=null){
            if (filename.startsWith("/")||filename.startsWith("\\")){
                filename = filename.substring(1);
            }
        }

        //System.out.println("Download:  " + share.toString() + filename);

      //Validate the filename/path
        if (filename==null || filename.equals("") || filename.contains("..") ||
            filename.toLowerCase().contains("keystore")){
            response.setStatus(400);
            return;
        }
        else{
          //Make sure none of the directories/files in the path are "hidden"
            for (String path : filename.replace("\\", "/").split("/")){
                if (path.trim().startsWith(".")){
                    response.setStatus(400);
                    return;
                }
            }
        }


        


        javaxt.io.File file = new javaxt.io.File(share.toString() + filename);
        if (!file.exists()) file = new javaxt.io.File(share.toString() + "downloads/" + filename);
        String contentType = file.getContentType();
        boolean isImage = (contentType.startsWith("image") || contentType.startsWith("text"));


        if (!file.exists()){
            response.setStatus(404);
            return;
        }

        long fileSize = file.getSize();
        filename = file.getName();



      //Process Cache Directives
        boolean useCache = true;
        String cache = request.getParameter("cache");
        if (cache!=null){
            cache = cache.toLowerCase();
            if (cache.equals("off") || cache.equals("false") || cache.equals("none")){
                useCache = false;
            }
        }
        if (useCache){
            javaxt.utils.Date date = new javaxt.utils.Date(file.getDate());
            date.setTimeZone("GMT");
            String eTag = "W/\"" + fileSize + "-" + date.getTime() + "\"";
            String matchTag = request.getHeader("if-none-match");
            String cacheControl = request.getHeader("cache-control");

            if (matchTag==null) matchTag = "";
            if (cacheControl==null) cacheControl = "";
            if (cacheControl.equalsIgnoreCase("no-cache")==false && eTag.equalsIgnoreCase(matchTag)){
                response.setStatus(304);
                return;
            }

            response.setHeader("ETag", eTag);
            response.setHeader("Last-Modified", date.toString("EEE, dd MMM yyyy HH:mm:ss zzz")); //Sat, 23 Oct 2010 13:04:28 GMT
        }
        else{
            response.setHeader ("Cache-Control", "no-cache");
        }



      //Set Response Headers
        response.setHeader ("Content-Type", contentType);
        response.setHeader ("Content-Length", fileSize + "");
        if (!isImage){
            response.setHeader ("Content-Disposition", "attachment;filename=\"" + filename + "\"");
        }



      //Dump file contents to servlet output stream
        try{          
            java.io.InputStream inputStream = file.getInputStream();
            javax.servlet.ServletOutputStream outputStream = response.getOutputStream();
            byte[] b = new byte[1024];
            int x=0;
            while ( (x = inputStream.read(b)) != -1) {
               outputStream.write(b,0,x);
            }
            inputStream.close();
            outputStream.close();


        }
        catch(Exception e){
            
            StringBuffer msg = new StringBuffer();
            msg.append(e.getMessage() + "<br/>\r\n");
            StackTraceElement[] error = e.getStackTrace();
            for (int i=0; i<error.length; i++){
                msg.append(error[i].toString() + "<br/>\r\n");
            }
            try{
                response.sendError(500, msg.toString());
            }
            catch(Exception ex){
                //response.setStatus(500);
            }
        }
    }
}