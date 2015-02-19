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
            filename = filename.trim().replace("\\", "/");
        }



      //Validate the filename/path
        if (filename==null || filename.equals("") || filename.contains("..")
            || filename.toLowerCase().startsWith("bin/")
        ){
            throw new ServletException(404);
        }
        else{
          //Make sure none of the directories/files in the path are "hidden".
          //Any directory that statrs with a "." is considered hidden.
            for (String path : filename.split("/")){
                if (path.trim().startsWith(".")){
                    throw new ServletException(404);
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


  //**************************************************************************
  //** getLastFile
  //**************************************************************************
  /** Find the latest version of the jar file. Assumes version number is
   *  included in the file name (e.g. "javaxt-core_v1.3.0.zip")
   *  @param jar Name of the jar file (e.g. "javaxt-core.jar")
   */
    public static javaxt.io.File getLastFile(String jar, javaxt.io.Directory dir){


        if (jar.toLowerCase().endsWith(".jar") || jar.toLowerCase().endsWith(".zip")){
            jar = jar.substring(0, jar.lastIndexOf("."));
        }


        int major = 0;
        int minor = 0;
        int patch = 0;
        javaxt.io.File lastFile = null;
        for (javaxt.io.File file : dir.getFiles("*.zip")){
            String fileName = file.getName(false);
            if (fileName.toLowerCase().startsWith(jar.toLowerCase())){
                String version = fileName.substring(fileName.indexOf("_v")+2);
                String[] arr = version.split("\\.");
                int a, b, c;
                a=b=c=0;
                for (int i=0; i<arr.length; i++){
                    int x = 0;
                    try{ x=Integer.parseInt(arr[i]); } catch(Exception e){}
                    if (i==0) a = x;
                    else if(i == 1) b = x;
                    else if(i == 2) c = x;
                }
                if (a>major){
                    major = a;
                    minor = b;
                    patch = c;
                    lastFile = file;
                }
                else{
                    if (b>minor){
                        major = a;
                        minor = b;
                        patch = c;
                        lastFile = file;
                    }
                    else{
                        if (c>patch){
                            major = a;
                            minor = b;
                            patch = c;
                            lastFile = file;
                        }
                        else{

                        }
                    }
                }
                //System.out.println(version + " vs " + major + "." + minor + "." + patch + " " + lastFile.getName(false));
            }
        }
        
        return lastFile;
    }
}