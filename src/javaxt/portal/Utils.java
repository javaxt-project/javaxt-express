package javaxt.portal;

//******************************************************************************
//**  Utils Class
//******************************************************************************
/**
 *   Enter class description here
 *
 ******************************************************************************/

public class Utils {


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of Utils. */

    private Utils() {

    }


    public static String getPath(javax.servlet.http.HttpServletRequest request){

        String Path = request.getContextPath();
        try{
            String host = new java.net.URL(request.getRequestURL().toString()).getHost();
            if (host.contains(".")){
                host = host.substring(0, host.lastIndexOf("."));
                if (host.contains(".")){
                    host = host.substring(host.lastIndexOf(".")+1);
                }
            }

            if (host.equalsIgnoreCase(Path.replace("/", ""))){
                Path = "/";
            }

            if (!Path.endsWith("/")) Path+="/";

        }
        catch(Exception e){}

        return Path;

    }

}