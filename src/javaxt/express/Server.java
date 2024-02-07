package javaxt.express;
import javaxt.express.cms.Content;

import javaxt.http.servlet.HttpServlet;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;
import javaxt.http.servlet.ServletException;

import javaxt.sql.*;
import javaxt.json.*;
import javaxt.io.Jar;
import static javaxt.utils.Console.*;

import java.util.*;
import java.io.IOException;
import javaxt.express.utils.DbUtils;


//******************************************************************************
//**  Express Server
//******************************************************************************
/**
 *   Command line application used to start a web server and serve content.
 *
 ******************************************************************************/

public class Server {

  //**************************************************************************
  //** main
  //**************************************************************************
  /** Command line interface used to start the server.
   *  @param arguments Command line arguments
   */
    public static void main(String[] arguments) throws Exception {
        HashMap<String, String> args = parseArgs(arguments);
        Jar jar = new Jar(Server.class);


      //Process command line args
        if (args.containsKey("-version")){
            String version = jar.getVersion();
            if (version==null) version = "Unknown";
            System.out.println(version);
        }
        else if (args.containsKey("-deploy")){
            Deploy.main(arguments);
        }
        else if (args.containsKey("-start")){
            javaxt.utils.Value start = getValue(args, "-start");
            Config config = getConfig(args, jar);
            try {


              //Get servlet
                HttpServlet servlet;
                if (start.equals("website") || start.equals("cms")){
                    servlet = new WebSite(config, args);
                }
                else if (start.equals("webservice") || start.equals("webservices")){
                    servlet = new WebServices(config, args);
                }
                else{
                    servlet = new WebServer(config, args);
                }


              //Start server
                int port = config.get("webserver").get("port").toInteger();
                int numThreads = config.get("webserver").get("numThreads").toInteger();
                javaxt.http.Server server = new javaxt.http.Server(port, numThreads, servlet);
                server.start();

            }
            catch (Exception e) {
                System.out.println("Server could not start because of an " +
                e.getClass() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        else{
            System.out.println("Unsupported command");
        }
    }


  //**************************************************************************
  //** WebSite
  //**************************************************************************
  /** Returns an HTTP servlet used serve content using the Express Content
   *  Management System (CMS).
   */
    private static class WebSite extends javaxt.express.cms.WebSite {
        private WebSite(Config config, HashMap<String, String> args){
            super(getDirectory(config));
        }

        private static javaxt.io.Directory getDirectory(Config config){
            javaxt.io.Directory web = null;
            try{
                web = (javaxt.io.Directory)
                config.get("webserver").get("webDir").toObject();
            }
            catch(Exception e){}
            if (web==null) throw new IllegalArgumentException(
            "Invalid directory. Use the -dir argument to specify a path to a CMS folder.");
            return web;
        }


      /* Override the native getContent method to generate custom HTML for wiki
       * pages. Performs custom keyword substitution for the "<%=index%>" tag.
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
  //** WebServer
  //**************************************************************************
  /** Returns an HTTP servlet used serve static files using the FileManager.
   *  The FileManager ensures that files are properly cached by the browser.
   */
    private static class WebServer extends HttpServlet {
        private FileManager fileManager;

        public WebServer(Config config, HashMap<String, String> args){

          //Get directory
            javaxt.io.Directory web = null;
            try{
                web = (javaxt.io.Directory)
                config.get("webserver").get("webDir").toObject();
            }
            catch(Exception e){}
            if (web==null) throw new IllegalArgumentException(
            "Invalid directory. Use the -dir argument to specify a path to a web folder.");


          //If we're still here, instantiate the file manager
            fileManager = new FileManager(web);
        }


        public void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
            fileManager.sendFile(request, response);
        }
    }


  //**************************************************************************
  //** WebServices
  //**************************************************************************
  /** Returns an HTTP servlet used execute CRUD operations (create, read,
   *  update, delete) using a given set of models.
   */
    private static class WebServices extends HttpServlet {

        private WebService ws;
        private Database database;

        public WebServices(Config config, HashMap<String, String> args) throws Exception  {

          //Parse inputs
            String input = getValue(args, "-model", "-models").toString();
            javaxt.io.File inputFile;
            try {
                inputFile = new javaxt.io.File(input);
                if (!inputFile.exists()) throw new Exception();
            }
            catch(Exception e){
                throw new IllegalArgumentException("Models are required");
            }

            database = config.getDatabase();
            if (database==null) throw new IllegalArgumentException("Invalid or missing database");


          //Get models
            javaxt.orm.Model[] models = new javaxt.orm.Parser(inputFile.getText()).getModels();


          //Initialize schema (create tables, indexes, etc)
            String schema = new javaxt.orm.Schema(models).getSQLScript();
            if (database.getDriver().equals("H2")){

              //Set H2 to PostgreSQL mode
                Properties properties = database.getProperties();
                if (properties==null){
                    properties = new java.util.Properties();
                    database.setProperties(properties);
                }
                properties.setProperty("MODE", "PostgreSQL");
                properties.setProperty("DATABASE_TO_LOWER", "TRUE");
                properties.setProperty("DEFAULT_NULL_ORDERING", "HIGH");


              //Update list of non-reserved keywords (e.g. "KEY", "VALUE", "YEAR")
                HashSet<String> nonKeyWords = new HashSet<>();
                Set<String> resevedKeyWords = new HashSet<>(
                    Arrays.asList(database.getReservedKeywords())
                );

                for (javaxt.orm.Model model : models){

                    String str = model.getTableName().toUpperCase();
                    if (resevedKeyWords.contains(str)) nonKeyWords.add(str);

                    for (javaxt.orm.Field field : model.getFields()){
                        str = field.getColumnName().toUpperCase();
                        if (resevedKeyWords.contains(str)) nonKeyWords.add(str);
                    }
                }

                if (!nonKeyWords.isEmpty()){
                    properties.setProperty("NON_KEYWORDS", String.join(",", nonKeyWords));
                }
            }
            DbUtils.initSchema(database, schema, null);



          //Enable metadata caching
            database.enableMetadataCache(true);


          //Inititalize connection pool
            database.initConnectionPool();


          //Compile models into classes
            Class[] classes = new javaxt.orm.Compiler(models).getClasses();


          //Instantiate web services
            ws = new Demo();


          //Initialize classes and register them with the web service
            for (Class c : classes){
                Model.init(c, database.getConnectionPool());
                ws.addModel(c);
            }

        }


        public void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

          //Add CORS support
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers","*");
            response.addHeader("Access-Control-Allow-Methods", "*");

          
            ServiceRequest req = new ServiceRequest(request);
            ServiceResponse rsp = ws.getServiceResponse(req, database);
            rsp.send(response);
        }

        private class Demo extends WebService {}
    }


  //**************************************************************************
  //** getConfig
  //**************************************************************************
    private static Config getConfig(HashMap<String, String> args, Jar jar){

        javaxt.io.File jarFile = new javaxt.io.File(jar.getFile());


      //Get config file
        javaxt.io.File configFile = (args.containsKey("-config")) ?
        getFile(args.get("-config"), jarFile) :
        new javaxt.io.File(jar.getFile().getParentFile(), "config.json");


      //Initialize config
        Config config = new Config();
        if (configFile.exists()){
            try{
                config.init(configFile.getJSONObject());
            }
            catch(Exception e){}
        }


      //Get web config
        JSONObject webConfig = config.get("webserver").toJSONObject();
        if (webConfig==null){
            webConfig = new JSONObject();
            config.set("webserver", webConfig);
        }



      //Set directory for the web server
        try{
            String dir = getValue(args, "-d", "-dir", "-directory", "-web", "-share").toString();
            if (dir.endsWith("\"")) dir = dir.substring(0, dir.length()-1);

            java.io.File f = new java.io.File(dir);
            if (f.isFile()) f = f.getParentFile();
            javaxt.io.Directory web = new javaxt.io.Directory(f);
            webConfig.set("webDir", web);
        }
        catch(Exception e){}


      //Set port for the web server
        Integer port = getValue(args, "-p", "-port").toInteger();
        if (port==null && !webConfig.has("port")) webConfig.set("port", 8080);
        else webConfig.set("port", port);


      //Set number of threads for the web server
        Integer numThreads = getValue(args, "-t", "-threads").toInteger();
        if (numThreads==null && !webConfig.has("numThreads")) webConfig.set("numThreads", 250);
        else webConfig.set("numThreads", numThreads);


      //Get database config
        Database database = config.getDatabase();
        if (database==null){
            String db = getValue(args, "-database", "-data").toString();
            if (db!=null){
                try{

                  //Check if the database arg represents a path to a directory
                    String path = db;
                    javaxt.io.Directory dir = new javaxt.io.Directory(path);
                    if (dir.exists()){
                        try{
                            java.io.File f = new java.io.File(path);
                            javaxt.io.Directory d = new javaxt.io.Directory(f.getCanonicalFile());
                            if (!dir.toString().equals(d.toString())){
                                dir = d;
                            }
                        }
                        catch(Exception e){
                        }
                    }
                    else{
                        path = (configFile.exists()) ? configFile.MapPath(path)
                                : jarFile.MapPath(path);

                        dir = new javaxt.io.Directory(new java.io.File(path));
                    }

                  //If the directory doesn't exist, throw an error. Do not try
                  //to create a new directory because the path may be invalid.
                    if (!dir.exists()) throw new Exception();



                  //Create a new database using H2
                    database = new Database();
                    database.setDriver("H2");
                    database.setHost(dir.toString().replace("\\", "/") + "database");

                }
                catch(Exception e){

                  //Try parsing connection string
                    if (args.containsKey("-database")){
                        try{
                            database = new Database(db);
                        }
                        catch(Exception ex){}
                    }
                }


              //Update database config
                if (database!=null){
                    int maxConnections = Math.min(25, webConfig.get("numThreads").toInteger());
                    database.setConnectionPoolSize(maxConnections);
                    config.setDatabase(database);
                }
            }

        }


        return config;
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