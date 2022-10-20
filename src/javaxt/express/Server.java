package javaxt.express;
import javaxt.express.cms.Content;

import javaxt.http.servlet.HttpServlet;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;
import javaxt.http.servlet.ServletException;

import javaxt.io.Jar;
import static javaxt.utils.Console.*;

import java.util.*;
import java.io.IOException;


//******************************************************************************
//**  Express Server
//******************************************************************************
/**
 *   Command line application used to start an web server and serve content.
 *
 ******************************************************************************/

public class Server {

  //**************************************************************************
  //** main
  //**************************************************************************
  /** Command line interface used to start the server.
   */
    public static void main(String[] arr) {
        HashMap<String, String> args = console.parseArgs(arr);


      //Get jar file
        Jar jar = new Jar(Server.class);
        javaxt.io.File jarFile = new javaxt.io.File(jar.getFile());
        String version = jar.getVersion();



      //Process command line args
        if (args.containsKey("-version")){
            if (version==null) version = "Unknown";
            System.out.println(version);
        }
        else if (args.containsKey("-deploy")){
            Deploy.main(arr);
        }
        else if (args.containsKey("-start")){


          //Get directory
            String dir = getValue(args, "-d", "-dir", "-directory", "-web").toString();
            javaxt.io.Directory web;
            try{
                if (dir.endsWith("\"")) dir = dir.substring(0, dir.length()-1);

                java.io.File f = new java.io.File(dir);
                if (f.isFile()) f = f.getParentFile();
                web = new javaxt.io.Directory(f);
                if (!web.exists()) throw new Exception();
            }
            catch(Exception e){
                System.err.println("Directory (\"-dir\") is required.");
                return;
            }


          //Get config file
            javaxt.io.File configFile = (args.containsKey("-config")) ?
            getFile(args.get("-config"), jarFile) :
            new javaxt.io.File(jar.getFile().getParentFile(), "config.json");


          //Get servlet
            HttpServlet servlet;
            javaxt.utils.Value start = getValue(args, "-start");
            if (start.equals("cms")) servlet = new WebSite(web, configFile);
            else servlet = new WebApp(web);



          //Get port (optional)
            Integer port = getValue(args, "-p", "-port").toInteger();
            if (port==null) port = 8080;



          //Get number of threads (optional)
            Integer numThreads = getValue(args, "-t", "-threads").toInteger();
            if (numThreads==null) numThreads = 250;



          //Start server
            try {
                javaxt.http.Server server = new javaxt.http.Server(port, numThreads, servlet);
                server.start();
            }
            catch (Exception e) {
                System.out.println("Server could not start because of an " + e.getClass());
                System.exit(1);
            }
        }
        else{

        }
    }


  //**************************************************************************
  //** WebSite
  //**************************************************************************
    private static class WebSite extends javaxt.express.cms.WebSite {
        private WebSite(javaxt.io.Directory dir, javaxt.io.File configFile){
            super(dir);
        }

      /** Returns an html snippet found in the given file. Overrides the native
       *  getContent method to support custom tags (e.g. "index").
       */
        public Content getContent(HttpServletRequest request, javaxt.io.File file){

          //Get path from url
            String path = request.getURL().getPath().toLowerCase();


          //Remove leading and trailing "/" characters
            if (path.startsWith("/")) path = path.substring(1);
            if (path.endsWith("/")) path = path.substring(0, path.length()-1);


          //Return content
            if (path.equals("wiki")){
                String html = file.getText();
                Date date = file.getDate();
                if (file.getName(false).equals("index")){
                    Content content = getIndex(file);
                    javaxt.utils.Date d = new javaxt.utils.Date(content.getDate());
                    if (d.isAfter(new javaxt.utils.Date(date))){
                        date = d.getDate();
                    }
                    html = html.replace("<%=index%>", content.getHTML());
                }

                return new Content(html, date);
            }
            else{
                return super.getContent(request, file);
            }
        }
    }


  //**************************************************************************
  //** WebApp
  //**************************************************************************
    private static class WebApp extends HttpServlet {
        private FileManager fileManager;
        public WebApp(javaxt.io.Directory web){
            fileManager = new FileManager(web);
        }
        public void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
            //response.setHeader("Server", server);
            fileManager.sendFile(request, response);
        }
    }


  //**************************************************************************
  //** getValue
  //**************************************************************************
    private static javaxt.utils.Value getValue(HashMap<String, String> args, String ...keys){
        for (String key : keys){
            if (args.containsKey(key)){
                return new javaxt.utils.Value(args.get(key));
            }
        }
        return new javaxt.utils.Value(null);
    }

  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a File for a given path
   *  @param path Full canonical path to a file or a relative path (relative
   *  to the jarFile)
   */
    public static javaxt.io.File getFile(String path, javaxt.io.File jarFile){
        javaxt.io.File file = new javaxt.io.File(path);
        if (!file.exists()){
            file = new javaxt.io.File(jarFile.MapPath(path));
        }
        return file;
    }
}