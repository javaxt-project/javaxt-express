package javaxt.express;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import com.yahoo.platform.yui.compressor.CssCompressor;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Level;
import javaxt.utils.Console;

//******************************************************************************
//**  WebDeploy
//******************************************************************************
/**
 *   Command line app used to package and deploy a web application. Assumes
 *   jar files are found in the /dist folder of the input directory and
 *   web-related files are in the /web directory.
 *
 ******************************************************************************/

public class Deploy {

    private static javaxt.io.Directory input;
    private static javaxt.io.Directory output;
    private java.util.HashSet<javaxt.io.File> minifiedFiles = new java.util.HashSet<>();


  //**************************************************************************
  //** Command Line Interface
  //**************************************************************************
  /** Used to instantiate the class via command line args.
   */
    public static void main(String[] arr) {
        java.util.HashMap<String, String> args = Console.parseArgs(arr);
        

        input = new javaxt.io.Directory(args.get("-deploy"));
        output = new javaxt.io.Directory(args.get("-target"));
        
        
        System.out.print(
            "\r\n"+
            "------------------------------------------\r\n"+
            " Please select from the following options:\r\n"+
            "------------------------------------------\r\n"+
            "  1. Deploy Web App\r\n"+
            "  2. Deploy Web Services\r\n"+
            "  3. Both\r\n"
        );
        while (true){
            System.out.print("\r\n> ");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            try {
                int x = Integer.parseInt(br.readLine());
                if (x<1 || x>3) throw new IllegalArgumentException();
                
                new Deploy(
                    input,
                    output,
                    x
                );
                break;
            }
            catch (IllegalArgumentException e) { //NumberFormatException
                System.out.println("  ERROR: Invalid Entry!");
            }
            catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate the class and deploy the web applications.
   *
   *  @param input Input directory containing both the WebApp and WebServices
   *  projects (e.g. "C:\Users\Peter\Java\WebApp\").
   *
   *  @param output Output directory (e.g. "X:\WebApp\").
   */
    public Deploy(javaxt.io.Directory input, javaxt.io.Directory output, int options){
        long buildNumber = new javaxt.utils.Date().toLong();

      //Deploy web app
        if (options==1 || options==3){
            System.out.println("\r\nDeploying Web App...");
            
            
          //Copy web directory
            java.util.HashSet<javaxt.io.File> filters = new java.util.HashSet<>();
            javaxt.io.Directory inputWeb = new javaxt.io.Directory(input+"web");
            javaxt.io.Directory outputWeb = new javaxt.io.Directory(output+"web");
            for (String fileName : new String[]{"index.html","login.html","sites.html","main.html"}){
                javaxt.io.File inputFile = new javaxt.io.File(inputWeb + fileName);
                javaxt.io.File outputFile = new javaxt.io.File(outputWeb + fileName);                
                
                if (!inputFile.exists()) continue;
                
                java.util.HashSet<javaxt.io.File> filter = updateHtml(inputFile, outputFile);
                java.util.Iterator<javaxt.io.File> it = filter.iterator();
                while (it.hasNext()){
                    filters.add(it.next());
                }
            }
            copyFiles(inputWeb, outputWeb, filters);
            
            

          //Copy apps
            java.util.HashSet<javaxt.io.File> filter = updateApps(input, output);
            javaxt.io.Directory inputAppDir = new javaxt.io.Directory(input + "dist/apps");
            javaxt.io.Directory outputAppDir = new javaxt.io.Directory(output + "apps");
            copyApps(inputAppDir, outputAppDir, filter);
            
            
          //Create build file
            new javaxt.io.File(outputWeb, "build.txt").write(buildNumber + "");
        }

        
      //Deploy web services
        if (options==2 || options==3){
            System.out.println("\r\nDeploying Web Services...");
            copyJars(input, output);
            new javaxt.io.File(output, "build.txt").write(buildNumber + "");
        }

        
        
        
        System.out.println("\r\nDone!");
    }


    private class Script {
        private java.util.Date lastModified;
        private StringBuffer src = new StringBuffer();
        private boolean minified = false;
    }


    
  //**************************************************************************
  //** updateHtml
  //**************************************************************************
  /** Used to consolidate javascripts and style sheets sourced in a given html
   *  file. Goal is to reduce server load by combining files found in a common
   *  directories. HTML is updated to source file paths to the consolidated
   *  scripts and style sheets. 
   */
    private java.util.HashSet<javaxt.io.File> updateHtml(javaxt.io.File inputFile, javaxt.io.File outputFile){

        javaxt.io.Directory inputDir = inputFile.getDirectory();
        javaxt.io.Directory outputDir = outputFile.getDirectory();
        
        
        org.w3c.dom.Document xhtml = inputFile.getXML();
        org.w3c.dom.Node head = javaxt.xml.DOM.getElementsByTagName("head", xhtml)[0];


        
        
        java.util.HashSet<javaxt.io.File> filter = new java.util.HashSet<javaxt.io.File>();
        int x = inputDir.toString().length();

        

        
      //Consolidate javascript includes
        java.util.LinkedHashMap<String, Script> scripts = new java.util.LinkedHashMap<String, Script>();
        for (org.w3c.dom.Node node : javaxt.xml.DOM.getElementsByTagName("script", xhtml)){
            String src = javaxt.xml.DOM.getAttributeValue(node.getAttributes(), "src").trim();
            if (src.length()>0){
                javaxt.io.File js = new javaxt.io.File(inputFile.MapPath(src));
                if (js.exists()){
                    String path = js.getPath().substring(x);

                    if (path.length()==0) path = js.getName();
                    else  path = path.substring(0, path.length() - 1) + ".js";
                    //System.out.println(js.getName() + " --> " + path);

                    Script script = scripts.get(path);
                    if (script==null){
                        script = new Script();
                        scripts.put(path, script);
                    }

                    
                    StringBuffer str = script.src;
                    str.append(js.getText());
                    str.append("\r\n");


                    java.util.Date lastModified = script.lastModified;
                    if (lastModified==null || js.getLastModifiedTime().after(lastModified)){
                        script.lastModified = js.getLastModifiedTime();
                    }

                    script.minified = js.getName().endsWith(".min.js");
                    
                    filter.add(js);
                }
                
                node.getParentNode().removeChild(node);
            }
        }



        
        
      //Update links to javascripts
        java.util.Iterator<String> it = scripts.keySet().iterator();
        while (it.hasNext()){
            
            String path = it.next();
            Script script = scripts.get(path);
            String src = script.src.toString();
            java.util.Date lastModified = script.lastModified;
            javaxt.io.File js = new javaxt.io.File(outputDir + path);
            
            //if (!minifiedFiles.contains(js)){
            
                if (!script.minified){
                    System.out.println("Compressing " + path + "...");
                    java.io.Reader in = new java.io.StringReader(src);
                    java.io.Writer out = new java.io.StringWriter();
                    try {
                        JavaScriptCompressor compressor = new JavaScriptCompressor(in, new YuiCompressorErrorReporter());
                        in.close();

                        //Writer out, int linebreak, boolean munge, boolean verbose, boolean preserveAllSemiColons, boolean disableOptimizations
                        compressor.compress(out, -1, true, false, false, false);
                        src = out.toString();
                        out.close();

                        minifiedFiles.add(js);
                    } 
                    catch(Exception e){
                        e.printStackTrace();
                        try{in.close();}catch(Exception ex){}
                        try{out.close();}catch(Exception ex){}
                    }
                }


              //Create/Update javascript file as needed
                System.out.println(path + " --> " + js);
                if (!js.exists() || lastModified.after(js.getLastModifiedTime()) || !js.getText().equals(src)){
                    js.write(src);
                    js.setLastModifiedTime(lastModified);
                }
            
            //}
            
          //Insert path to consolidated javascript file
            org.w3c.dom.Node node = xhtml.createElement("script");
            org.w3c.dom.NamedNodeMap attr = node.getAttributes();
            org.w3c.dom.Attr att = xhtml.createAttribute("type");
            att.setValue("text/javascript");
            attr.setNamedItem(att);
            att = xhtml.createAttribute("src");
            att.setValue(path.replace("\\", "/"));
            attr.setNamedItem(att);
            head.appendChild(node);
        }


      //Remove comments
        org.w3c.dom.NodeList nodes = head.getChildNodes();
        for (int i=0; i<nodes.getLength(); i++){
            org.w3c.dom.Node node = nodes.item(i);
            if (node.getNodeType()==8){
                node.getParentNode().removeChild(node);
            }
        }


      //Remove extra line spaces
        String text = javaxt.xml.DOM.getText(xhtml);
        while (text.contains("\r\n\r\n")) text = text.replace("\r\n\r\n", "\r\n");
        while (text.contains("/><")) text = text.replace("/><", "/>\r\n<");

      //Remove XML header
        if (text.startsWith("<?xml")) text = text.substring(text.indexOf(">")+1);


      //Update script tags. Browsers don't understand tags that end in "/>"
        StringBuffer html = new StringBuffer();
        for (String str : text.split("<script ")){
            if (html.length()==0) html.append(str);
            else{
                String a = str.substring(0, str.indexOf(">")+1);
                String b = str.substring(str.indexOf(">")+1);

                if (a.substring(a.length()-2).equals("/>")){
                    a = a.substring(0, a.length()-2) + "></script>";
                }

                str = "<script " + a + b;
                html.append(str);
            }
        }
        text = html.toString();


      //Save html to file
        outputFile.write(text);


      //Return list of files that were consolidated
        return filter;
    }


  //**************************************************************************
  //** updateApps
  //**************************************************************************
  /** Used to consolidate javascripts and style sheets sourced in a xml config
   *  file. Goal is to reduce server load by combining files found in a common
   *  directories. XML is updated to source file paths to the consolidated
   *  scripts and style sheets. 
   */
    private java.util.HashSet<javaxt.io.File> updateApps(javaxt.io.Directory input, javaxt.io.Directory output){
        

        javaxt.io.Directory dir = new javaxt.io.Directory(input + "dist/apps");
        java.util.HashSet<javaxt.io.File> filter = new java.util.HashSet<javaxt.io.File>();

        
      //Find xml and consolidate 
        java.util.LinkedHashMap<String, Script> scripts = new java.util.LinkedHashMap<>();
        java.util.List files = dir.getChildren(true, "*.xml", false);
        Object obj;
        while (true){
            synchronized (files) {
                while (files.isEmpty()) {
                  try {
                      files.wait();
                  }
                  catch (InterruptedException e) {
                      break;
                  }
                }
                obj = files.remove(0);
                files.notifyAll();
            }

            if (obj==null){
                break;
            }
            else{

                if (obj instanceof javaxt.io.File){
                    javaxt.io.File file = (javaxt.io.File) obj;
                    
                    javaxt.io.Directory parentDir = file.getDirectory();
                    if (parentDir.getName().equalsIgnoreCase("web")){
                        
                        javaxt.io.Directory appDir = parentDir.getParentDirectory();
                        String appName = appDir.getName();
                        javaxt.io.Directory webDir = parentDir;
                        int x = webDir.toString().length();
                        
                        System.out.println(appName);
                        
                        String relPath = "apps" + javaxt.io.Directory.PathSeparator + parentDir.toString().substring(dir.toString().length());
                        System.out.println(relPath);

                        javaxt.io.Directory outputDir = new javaxt.io.Directory(output + relPath);
                        
                        
                        org.w3c.dom.Document xml = file.getXML();
                        org.w3c.dom.Node outerNode = javaxt.xml.DOM.getOuterNode(xml);
                        if (outerNode.getNodeName().equals("application")){
                            
                            org.w3c.dom.Node[] includes = javaxt.xml.DOM.getElementsByTagName("includes", outerNode);
                            if (includes.length>0){
                                org.w3c.dom.Node include = includes[0];
                                for (org.w3c.dom.Node node : javaxt.xml.DOM.getElementsByTagName("script", include)){
                                    
                                    String src = javaxt.xml.DOM.getAttributeValue(node, "src");
                                    
                                    if (src.length()>0){
                                        
                                        if (src.toLowerCase().startsWith(appName.toLowerCase() + "/")){ //ex. "locomotive/Application.js"
                                            src = src.substring(appName.length() + 1);
                                        }
                                        else{
                                            //???
                                        }
                                        
                                        System.out.println(src);
                                        
                                        javaxt.io.File js = new javaxt.io.File(webDir + src);
                                        if (js.exists()){

                                            
                                            String path = js.getPath().substring(x);
                                            if (path.length()==0) path = appName + ".js";
                                            else  path = path.substring(0, path.length() - 1) + ".js";
                                            

                                            System.out.println(js.getName() + " --> " + path);

                                            Script script = scripts.get(path);
                                            if (script==null){
                                                script = new Script();
                                                scripts.put(path, script);
                                            }


                                            StringBuffer str = script.src;
                                            str.append(js.getText());
                                            str.append("\r\n");


                                            java.util.Date lastModified = script.lastModified;
                                            if (lastModified==null || js.getLastModifiedTime().after(lastModified)){
                                                script.lastModified = js.getLastModifiedTime();
                                            }

                                            script.minified = js.getName().endsWith(".min.js");

                                            filter.add(js);
                                        }

                                        node.getParentNode().removeChild(node);
                                    }                                    

                                    
                                }
                                
                                
                              //Update links to javascripts
                                java.util.Iterator<String> it = scripts.keySet().iterator();
                                while (it.hasNext()){

                                    String path = it.next();
                                    Script script = scripts.get(path);
                                    String src = script.src.toString();
                                    java.util.Date lastModified = script.lastModified;
                                    javaxt.io.File js = new javaxt.io.File(outputDir + path);


                                    if (!script.minified){
                                        System.out.println("Compressing " + path + "...");
                                        java.io.Reader in = new java.io.StringReader(src);
                                        java.io.Writer out = new java.io.StringWriter();
                                        try {
                                            JavaScriptCompressor compressor = new JavaScriptCompressor(in, new YuiCompressorErrorReporter());
                                            in.close();

                                            //Writer out, int linebreak, boolean munge, boolean verbose, boolean preserveAllSemiColons, boolean disableOptimizations
                                            compressor.compress(out, -1, true, false, false, false);
                                            src = out.toString();
                                            out.close();

                                        } 
                                        catch(Exception e){
                                            e.printStackTrace();
                                            try{in.close();}catch(Exception ex){}
                                            try{out.close();}catch(Exception ex){}
                                        }
                                    }


                                  //Create/Update javascript file as needed
                                    System.out.println(path + " --> " + js);
                                    if (!js.exists() || lastModified.after(js.getLastModifiedTime()) || !js.getText().equals(src)){
                                        js.write(src);
                                        js.setLastModifiedTime(lastModified);
                                    }

                                  //Insert path to consolidated javascript file
                                    org.w3c.dom.Node node = xml.createElement("script");
                                    org.w3c.dom.NamedNodeMap attr = node.getAttributes();
                                    org.w3c.dom.Attr att = xml.createAttribute("type");
                                    att.setValue("text/javascript");
                                    attr.setNamedItem(att);
                                    att = xml.createAttribute("src");
                                    att.setValue(appName.toLowerCase() + "/" + path.replace("\\", "/"));
                                    attr.setNamedItem(att);
                                    include.appendChild(node);
                                }


                                
                                javaxt.io.File outputFile = new javaxt.io.File(outputDir, file.getName());
                                System.out.println(outputFile);
                                outputFile.write(xml);
                            }
                            

                            
                            
                        }
                    }
                }
            }
        }
        
        return filter;
    }

    
  //**************************************************************************
  //** copyFiles
  //**************************************************************************
  /** Copies files
   */
    private void copyFiles(javaxt.io.Directory input, javaxt.io.Directory output, java.util.HashSet<javaxt.io.File> filter){

        int x = input.toString().length();

        java.util.List files = input.getChildren(true, null, false);
        Object obj;
        while (true){
            synchronized (files) {
                while (files.isEmpty()) {
                  try {
                      files.wait();
                  }
                  catch (InterruptedException e) {
                      break;
                  }
                }
                obj = files.remove(0);
                files.notifyAll();
            }

            if (obj==null){
                break;
            }
            else{

                if (obj instanceof javaxt.io.File){
                    javaxt.io.File file = (javaxt.io.File) obj;
                    String path = file.getPath();
                    String ext = file.getExtension().toLowerCase();
                    String parentDir = path.substring(0, path.length()-1);
                    parentDir = parentDir.substring(parentDir.lastIndexOf(file.PathSeparator)+1);
                    
                    System.out.println(filter.contains(file) + "\t" + file);

                    if (!filter.contains(file) &&
                        !(parentDir.equalsIgnoreCase("test") || parentDir.equalsIgnoreCase("src") || parentDir.equalsIgnoreCase(".svn")) &&
                        !(ext.equals("org") || ext.equals("svn-base"))) {


                        javaxt.io.File copyTo = new javaxt.io.File(output + path.substring(x) + file.getName());
                        if (!copyTo.exists() || file.getLastModifiedTime().after(copyTo.getLastModifiedTime())) {
                            //System.out.println(copyTo);
                            file.copyTo(copyTo, true);
                        }
                    }
                }
                else{

                }

            }
        }
    }

    
  //**************************************************************************
  //** copyApps
  //**************************************************************************
  /** Copies "web" directories associated with apps.
   */
    private void copyApps(javaxt.io.Directory input, javaxt.io.Directory output, java.util.HashSet<javaxt.io.File> filter){

        int x = input.toString().length();
        
        for (javaxt.io.Directory appDir : input.getSubDirectories()){
            javaxt.io.Directory webDir = new javaxt.io.Directory(appDir + "web");
            java.util.List files = webDir.getChildren(true, null, false);
            Object obj;
            while (true){
                synchronized (files) {
                    while (files.isEmpty()) {
                      try {
                          files.wait();
                      }
                      catch (InterruptedException e) {
                          break;
                      }
                    }
                    obj = files.remove(0);
                    files.notifyAll();
                }

                if (obj==null){
                    break;
                }
                else{

                    if (obj instanceof javaxt.io.File){
                        javaxt.io.File file = (javaxt.io.File) obj;
                        String path = file.getPath();
                        String ext = file.getExtension().toLowerCase();
                        String parentDir = path.substring(0, path.length()-1);
                        parentDir = parentDir.substring(parentDir.lastIndexOf(file.PathSeparator)+1);

                        System.out.println(filter.contains(file) + "\t" + file);

                        if (!filter.contains(file) &&
                            !(parentDir.equalsIgnoreCase("test") || parentDir.equalsIgnoreCase("src") || parentDir.equalsIgnoreCase(".svn")) &&
                            !(ext.equals("org") || ext.equals("svn-base"))) {


                            javaxt.io.File copyTo = new javaxt.io.File(output + path.substring(x) + file.getName());
                            if (!copyTo.exists() || file.getLastModifiedTime().after(copyTo.getLastModifiedTime())) {
                                //System.out.println(copyTo);
                                file.copyTo(copyTo, true);
                            }
                        }
                    }
                    else{

                    }

                }
            }
        }
    }
    
    

  //**************************************************************************
  //** copyJars
  //**************************************************************************
  /** Copies jar files found in the input directory or any subdirectories.
   */
    private void copyJars(javaxt.io.Directory input, javaxt.io.Directory output){
        
        System.out.println(input);
        
        
      //Copy keystore
        for (javaxt.io.File file : input.getFiles("*.jks")){
            javaxt.io.File copyTo = new javaxt.io.File(output + file.getName());
            if (!copyTo.exists() || file.getLastModifiedTime().after(copyTo.getLastModifiedTime())) {
                System.out.println(copyTo);
                file.copyTo(copyTo, true);
            }
        }
        
        
        javaxt.io.Directory dist = new javaxt.io.Directory(input + "dist");
        int x = dist.toString().length();
        
        
        
      //Copy jars from the lib directory
        java.util.HashMap<String, javaxt.io.File> libs = new java.util.HashMap<String, javaxt.io.File>();
        javaxt.io.Directory lib = new javaxt.io.Directory(dist + "lib");
        for (javaxt.io.File file : lib.getFiles("*.jar")){
            String fileName = file.getName();
            libs.put(fileName, file);

            String path = file.toString();

            javaxt.io.File copyTo = new javaxt.io.File(output + path.substring(x));
            if (!copyTo.exists() || file.getLastModifiedTime().after(copyTo.getLastModifiedTime())) {
                System.out.println(copyTo);
                file.copyTo(copyTo, true);
            }
        }
        
        
        
        
      //Copy jars 
        java.util.List files = dist.getChildren(true, null, false);
        Object obj;
        while (true){
            synchronized (files) {
                while (files.isEmpty()) {
                  try {
                      files.wait();
                  }
                  catch (InterruptedException e) {
                      break;
                  }
                }
                obj = files.remove(0);
                files.notifyAll();
            }

            if (obj==null){
                break;
            }
            else{

                if (obj instanceof javaxt.io.File){
                    javaxt.io.File file = (javaxt.io.File) obj;
                    String fileName = file.getName();
                    String fileExt = file.getExtension().toLowerCase();
                    if (fileExt.equals("jar")){ //what else?
                        if (!libs.containsKey(fileName)){
                            
                            String path = file.getPath();

                            javaxt.io.File copyTo = new javaxt.io.File(output + path.substring(x) + file.getName());
                            if (!copyTo.exists() || file.getLastModifiedTime().after(copyTo.getLastModifiedTime())) {
                                System.out.println(copyTo);
                                file.copyTo(copyTo, true);
                            }
                            
                            
                        }
                    }
                }
            }
        }
        

    }
    
    
    
    private static class YuiCompressorErrorReporter implements ErrorReporter {
        public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
            if (line < 0) {
                log(Level.WARNING, message);
            } else {
                log(Level.WARNING, line + ':' + lineOffset + ':' + message);
            }
        }

        public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
            if (line < 0) {
                log(Level.SEVERE, message);
            } else {
                log(Level.SEVERE, line + ':' + lineOffset + ':' + message);
            }
        }

        public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset) {
            error(message, sourceName, line, lineSource, lineOffset);
            return new EvaluatorException(message);
        }

        private static void log(Level l, String msg){
            System.out.println(l + " " + msg);
        }
    }
}