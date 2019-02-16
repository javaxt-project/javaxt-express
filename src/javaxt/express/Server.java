package javaxt.express;
import javaxt.express.cms.Content;
import javaxt.express.cms.WebSite;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.utils.Console;

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
        java.util.HashMap<String, String> args = Console.parseArgs(arr);
        
        
        if (args.containsKey("-deploy")){
            Deploy.main(arr);
        }
        else{
            
          //Get port
            int port;
            try{ port = Integer.parseInt(args.get("-p")); }
            catch(Exception e){ 
                System.err.println("Port (\"-p\") is required.");
                return;
            }
        
        
          //Get directory
            javaxt.io.Directory dir;
            try{
                dir = new javaxt.io.Directory(args.get("-d"));
                if (!dir.exists()) throw new Exception();
            }
            catch(Exception e){
                System.err.println("Directory (\"-d\") is required.");
                return;
            }
        
        
          //Get number of threads
            int numThreads = 50;
            try{ numThreads = Integer.parseInt(args.get("-t")); }
            catch(Exception e){}
        
        
          //Start server
            try {
                javaxt.http.Server server = new javaxt.http.Server(port, numThreads, new Demo(dir));
                server.start();
            }
            catch (Exception e) {
                System.out.println("Server could not start because of an " + e.getClass());
                System.exit(1);
            }
        }
    }


  //**************************************************************************
  //** Demo WebSite
  //**************************************************************************
    private static class Demo extends WebSite {
        private Demo(javaxt.io.Directory dir){
            super(dir);
            super.setAuthor("ACME Inc");
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
                java.util.Date date = file.getDate();
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
}