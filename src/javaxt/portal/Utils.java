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
  //** Private Constructor
  //**************************************************************************
  /** Prevent users from instantiating this class. */

    private Utils() {}


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Returns the path to the app root.
   */
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


  //**************************************************************************
  //** getURL
  //**************************************************************************
  /** Returns the url associated with a given request. Note that the standard
   *  request.getRequestURL() method returns the ContextPath even when the
   *  app is deployed as the default web application. For example, the "JavaXT"
   *  web app is deployed as the default web app. When clients request
   *  "http://www.javaxt.com" the request.getRequestURL() returns
   *  "http://www.javaxt.com/JavaXT". This method will return the correct url,
   *  namely "http://www.javaxt.com".
   */
    public static javaxt.utils.URL getURL(javax.servlet.http.HttpServletRequest request){


        String protocol = request.getProtocol();
        if (protocol==null) protocol = "http";
        else{
            if (protocol.contains("/")) protocol = protocol.substring(0, protocol.indexOf("/"));
            protocol = protocol.toLowerCase();
        }

      //Get Host
        String host = request.getServerName();

        Integer port = request.getServerPort();
        if (port!=null && port>0 && port!=80) host += ":" + port;

      //Get Path
        String path = request.getRequestURI();
        if (path==null) path = "";

      //Update Path: Special case when app is deployed as root
        if (path.length()>0){
            if (Utils.getPath(request).equals("/")){
                String contextPath = request.getContextPath();
                if (path.startsWith(contextPath)){
                    path = path.substring(contextPath.length());
                }
            }
        }



      //Get Query String
        String query = request.getQueryString();
        if (query==null) query = "";
        if (query.length()>0) query = "?" + query;


      //Assemble URL
        String url = protocol + "://" + host + path + query;
        //System.out.println("\r\nURL: " + url + "\r\nPath: " + Utils.getPath(request));
        return new javaxt.utils.URL(url);
    }

}