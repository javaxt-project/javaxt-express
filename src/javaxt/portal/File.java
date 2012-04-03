package javaxt.portal;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;
import javaxt.http.servlet.ServletException;

//******************************************************************************
//**  File Class
//******************************************************************************
/**
 *   Used to represent a file found on the web server. 
 *
 ******************************************************************************/

public class File {

    private javaxt.io.File file;

  //**************************************************************************
  //** Constructor
  //**************************************************************************

    public File(HttpServletRequest request, javaxt.io.Directory share, String Path)
    throws ServletException {

        
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

      //If no file found in the querystring, use the path from the url
        if (filename==null){
            String path = request.getURL().getPath();
            path = path.substring(path.indexOf(Path)).substring(Path.length());
            if (!path.endsWith("/")) filename = path;
        }


      //Remove any leading path separators
        if (filename!=null){
            if (filename.startsWith("/")||filename.startsWith("\\")){
                filename = filename.substring(1);
            }
        }



      //Validate the filename/path
        if (filename==null || filename.equals("") || filename.contains("..") ||
            filename.toLowerCase().contains("keystore")){
            throw new ServletException(400);
        }
        else{
          //Make sure none of the directories/files in the path are "hidden"
            for (String path : filename.replace("\\", "/").split("/")){
                if (path.trim().startsWith(".")){
                    throw new ServletException(400);
                }
            }
        }



        file = new javaxt.io.File(share.toString() + filename);
        if (!file.exists()) file = new javaxt.io.File(share.toString() + "downloads/" + filename);

        //System.out.println("Download:  " + file);

        if (!file.exists()){
            throw new ServletException();
        }

    }

    
    public javaxt.io.File getFile(){
        return file;
    }

  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /**  Used to send a static file to the client.
   */
    public void send(HttpServletResponse response) throws java.io.IOException {
        String contentType = file.getContentType();
        if (file.getExtension().equalsIgnoreCase("xml")) contentType = "text/xml";
        response.write(file.toFile(), contentType, true);
    }

}
