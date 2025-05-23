package javaxt.express;

import java.util.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;


import javaxt.io.Directory;
import static javaxt.xml.DOM.*;
import javaxt.utils.ThreadPool;
import javaxt.express.utils.DateUtils;
import static javaxt.utils.Console.console;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


//******************************************************************************
//**  FileManager
//******************************************************************************
/**
 *   Used to serve up static files (html, javascript, css, images, etc).
 *   Ensures static files are cached properly by browsers by appending
 *   version numbers to css and js files. Also updates js and css resources
 *   referenced inside html files and application-specific xml files. Redirects
 *   requests to the most recent version of each file. Sends 304 responses as
 *   required.
 *
 ******************************************************************************/

public class FileManager {

    private javaxt.io.Directory web;
    private String[] welcomeFiles = new String[]{"index.html", "index.htm", "default.htm"};


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to create an instance of this class.
   *  @param web Directory from which to serve files (aka web root folder)
   */
    public FileManager(javaxt.io.Directory web){
        this.web = web;
    }


  //**************************************************************************
  //** getDirectory
  //**************************************************************************
  /** Returns the root directory.
   */
    public javaxt.io.Directory getDirectory(){
        return web;
    }


  //**************************************************************************
  //** getFileUpdates
  //**************************************************************************
  /** Used to monitor the web directory for changes. Calls an EventMonitor
   *  whenever a file is created, updated, moved, or deleted. Example:
   <pre>
    fileManager.getFileUpdates((Directory.Event event) -> {
        String op;
        int eventID = event.getEventID();
        if (eventID==Directory.Event.CREATE){
            op = "create";
        }
        else if (eventID==Directory.Event.DELETE){
            op = "delete";
        }
        else{
            op = "update";
        }
        java.io.File file = new java.io.File(event.getFile());
        console.log(op, file);
    });
    </pre>
    */
    public void getFileUpdates(EventMonitor eventMonitor){
        if (eventMonitor==null) return;
        new Thread(() -> {
            try{
                List events = web.getEvents();
                while (true){
                    Directory.Event event;
                    synchronized (events) {
                        while (events.isEmpty()) {
                            try {
                                events.wait();
                            }
                            catch (InterruptedException e) {}
                        }
                        event = (Directory.Event) events.remove(0);
                    }

                    try{
                        if (isValidPath(event.getFile())){
                            if (event.getEventID()==Directory.Event.DELETE){
                                eventMonitor.processEvent(event);
                            }
                            else{
                                java.io.File f = new java.io.File(event.getFile());
                                if (f.isFile() && !f.isHidden()){
                                    eventMonitor.processEvent(event);
                                }
                            }

                        }
                    }
                    catch(Exception e){}
                }
            }
            catch(Exception e){}
        }).start();
    }


  //**************************************************************************
  //** EventMonitor Interface
  //**************************************************************************
  /** Implementations of this class are used by the getFileUpdates() method
   *  to process events (e.g. file created, updated, moved or deleted).
   */
    public static interface EventMonitor {
        public void processEvent(Directory.Event event);
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a file that best matches the given HttpServletRequest. See the
   *  other getFile() method for more information.
   */
    public java.io.File getFile(HttpServletRequest request){

        String path = request.getPathInfo();
        return getFile(path);
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a file that best matches the given path. If the path represents
   *  a directory, searches for welcome files in the directory (e.g.
   *  "index.html"). Returns null if a file is not found.
   *  @param path Relative path to a file or folder, relative to the web root.
   */
    public java.io.File getFile(String path){
        if (path==null) path = "";
        while (path.startsWith("/")) path = path.substring(1);


      //Construct a list of possible file paths
        ArrayList<String> files = new ArrayList<>();
        files.add(web + path);
        if (path.length()>0 && !path.endsWith("/")) path+="/";
        for (String welcomeFile : welcomeFiles){
            files.add(web + path + welcomeFile);
        }



      //Loop through all the possible file combinations
        for (String str : files){

          //Ensure that the path doesn't have any illegal directives
            if (!isValidPath(str)) continue;


          //Send file if it exists
            java.io.File file = new java.io.File(str);
            if (file.exists() && file.isFile() && !file.isHidden()){
                return file;
            }
        }

        return null;
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client. Determines which file to send via
   *  the URL path in the HTTP request.
   */
    public void sendFile(HttpServletRequest request, HttpServletResponse response)
        throws IOException{

        java.io.File file = getFile(request);
        sendFile(file, request, response);
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client. Determines which file to send via
   *  the given path.
   *  @param path Relative path to a file or folder, relative to the web root.
   */
    public void sendFile(String path, HttpServletRequest request, HttpServletResponse response)
        throws IOException {

        java.io.File file = getFile(path);
        sendFile(file, request, response);
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client.
   */
    public void sendFile(javaxt.io.File file, HttpServletRequest request, HttpServletResponse response)
        throws IOException {

        sendFile(file==null ? null : file.toFile(), request, response);
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client.
   */
    public void sendFile(java.io.File file, HttpServletRequest request, HttpServletResponse response)
        throws IOException {

      //Validate the given file. Send 404 response as needed.
        if (file==null || !file.isFile() || !file.exists() || file.isHidden()){
            response.setStatus(404);
            return;
        }


        String name = file.getName();
        int idx = name.lastIndexOf(".");
        if (idx > -1){
            String ext = name.substring(idx+1).toLowerCase();

            if (ext.equals("js") || ext.equals("css")){


              //Add version number to javascript and css files to ensure
              //proper caching. Otherwise, browsers like Chrome may not
              //return the correct file to the client.
                javaxt.utils.URL url = new javaxt.utils.URL(request.getURL());
                long currVersion = new javaxt.utils.Date(file.lastModified()).toLong();
                long requestedVersion = 0;
                try{ requestedVersion = Long.parseLong(url.getParameter("v")); }
                catch(Exception e){}

                if (requestedVersion < currVersion){
                    url.setParameter("v", currVersion+"");
                    response.sendRedirect(url.toString(), true);
                    return;
                }
                else if (requestedVersion==currVersion){
                    response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
                }

            }
            else if (ext.equals("htm") || ext.equals("html")){


              //Extract html from file
                javaxt.io.File htmlFile = new javaxt.io.File(file);
                String html = htmlFile.getText();



              //Instantiate html parser and get header
                javaxt.html.Parser parser = new javaxt.html.Parser(html);
                javaxt.html.Element head = parser.getElementByTagName("head");
                if (head==null){
                    long lastUpdate = htmlFile.getLastModifiedTime().getTime();
                    response.setContentType("text/html");
                    response.write(html, lastUpdate);
                    return;
                }
                String header = head.getOuterHTML();



              //Generate XML with scripts and links found in the header
                ArrayList<String> headerNodes = new ArrayList<>();
                HashMap<Integer, Integer> updates = new HashMap<>();
                StringBuilder str = new StringBuilder();
                str.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n");
                str.append("<links>\r\n");
                for (javaxt.html.Element el : head.getChildNodes()){

                    String tagName = el.getName();
                    if (tagName==null) continue;

                    tagName = tagName.toLowerCase();
                    String url = null;
                    if (tagName.equals("script")){
                        url = el.getAttribute("src");
                    }
                    else if (tagName.equals("link")){
                        url = el.getAttribute("href");
                    }

                    if (!(url==null || url.isEmpty())){

                        String t = url.toLowerCase();
                        if (!t.startsWith("http://") && !t.startsWith("https://") && !t.startsWith("//")){

                            str.append("\r\n");
                            str.append(el.toString());
                            if (!el.isClosed()){
                                str.append("</" + el.getName() + ">");
                            }

                            updates.put(updates.size(), headerNodes.size());
                        }

                    }
                    headerNodes.add(el.toString());
                }
                str.append("</links>");
                org.w3c.dom.Document xml = createDocument(str.toString());



              //Update links in the XML
                long lastUpdate;
                try{
                    lastUpdate = updateLinks(htmlFile, xml);
                }
                catch(Exception e){
                    throw new RuntimeException(e);
                }



              //Update header nodes
                Node[] nodes = getNodes(getOuterNode(xml).getChildNodes());

                HashSet<String> simpleNodes = new HashSet<>();
                for (Node node : nodes){
                    String nodeName = node.getNodeName().toLowerCase();
                    if (nodeName.equals("script")) {
                        String url = getAttributeValue(node, "src");
                        if (url.length()==0) continue;
                        simpleNodes.add("script|"+url);
                    }
                    else if (nodeName.equals("link")){
                        String url = getAttributeValue(node, "href");
                        if (url.length()==0) continue;
                        simpleNodes.add("link|"+url);
                    }
                }

                for (int i=0; i<nodes.length; i++){
                    Node node = nodes[i];
                    String nodeName = node.getNodeName().toLowerCase();


                  //Convert node into an HTML string
                    String txt = "";
                    if (nodeName.equals("scripts")){ //wildcard replacements
                        for (Node n : getElementsByTagName("script", node)){

                          //Skip node if a similar node is found in the header
                            String url = getAttributeValue(n, "src");
                            if (simpleNodes.contains("script|"+url)) continue;


                          //Add node
                            txt += updateTag(n) + "\r\n";
                        }
                    }
                    else if (nodeName.equals("links")){ //wildcard replacements
                        for (Node n : getElementsByTagName("link", node)){

                          //Skip node if a similar node is found in the header
                            String url = getAttributeValue(n, "href");
                            if (simpleNodes.contains("link|"+url)) continue;


                          //Add node
                            txt += updateTag(n) + "\r\n";
                        }
                    }
                    else{
                        txt = updateTag(node);
                    }


                  //Replace entry in headerNodes
                    int x = updates.get(i);
                    headerNodes.set(x, txt);
                }



              //Replace header in the html document
                str = new StringBuilder("<head>");
                for (String s : headerNodes){
                    str.append("\r\n");
                    str.append(s);

                }
                str.append("\r\n</head>");
                html = html.replace(header, str.toString());



              //Set content type and send response
                response.setContentType("text/html");
                response.write(html, lastUpdate);
                return;
            }
            else if (ext.equals("xml")){


              //Check whether the xml file is a javaxt-specific file
              //with CSS and JS includes. If so, add version numbers to
              //the js and css files sourced in the xml document.
                javaxt.io.File xmlFile = new javaxt.io.File(file);
                org.w3c.dom.Document xml = xmlFile.getXML();
                String outerNode = getOuterNode(xml).getNodeName();
                if (outerNode.equals("application") || outerNode.equals("includes")){


                  //Update links to scripts and css files
                    long lastUpdate;
                    try{
                        lastUpdate = updateLinks(xmlFile, xml);
                    }
                    catch(Exception e){
                        throw new RuntimeException(e);
                    }


                  //Set content type and send response
                    response.setContentType("application/xml");
                    response.write(getText(xml), lastUpdate);
                    return;
                }

            }
        }


      //Send file
        response.write(file, javaxt.io.File.getContentType(file.getName()), true);

    }


  //**************************************************************************
  //** updateLinks
  //**************************************************************************
  /** Used to update links to scripts and css files by appending a version
   *  number to the urls (?v=12345678). This operation is multi-threaded to
   *  improve performance.
   *  @return Long representing a timestamp associated with the most recent
   *  file update
   */
    public long updateLinks(javaxt.io.File xmlFile, org.w3c.dom.Document xml) throws Exception {

      //Generate list of nodes
        ArrayList<Node> includes = new ArrayList<>();
        for (Node node : getElementsByTagName("script", xml)) includes.add(node);
        for (Node node : getElementsByTagName("link", xml)) includes.add(node);


      //Update links
        long t = updateLinks(xmlFile, includes);



      //Replace nested nodes
        ArrayList<Node> orgNodes = new ArrayList<>();
        for (Node node : getElementsByTagName("script", xml)) orgNodes.add(node);
        for (Node node : getElementsByTagName("link", xml)) orgNodes.add(node);
        for (int i=0; i<includes.size(); i++){
            Node node = includes.get(i);
            Node orgNode = orgNodes.get(i);
            Node parentNode = orgNode.getParentNode();
            String nodeName = node.getNodeName().toLowerCase();
            if (nodeName.equals("scripts") || nodeName.equals("links")){
                node = xml.adoptNode(node);
                parentNode.insertBefore(node, orgNode);
                parentNode.removeChild(orgNode);
            }
        }


        return t;
    }


  //**************************************************************************
  //** updateLinks
  //**************************************************************************
    public long updateLinks(javaxt.io.File xmlFile, ArrayList<Node> nodes) throws Exception {

      //Start building unique list of file dates
        ConcurrentHashMap<Long, Boolean> uniqueDates = new ConcurrentHashMap<>();
        if (xmlFile.exists()) uniqueDates.put(xmlFile.getDate().getTime(), true);


      //Instantiate the ThreadPool
        ThreadPool pool = new ThreadPool(4){
            public void process(Object obj){
                Node node = (Node) obj;
                String nodeName = node.getNodeName().toLowerCase();
                if (nodeName.equals("script")){

                  //Get String src = getAttributeValue(node, "src");link
                    String src = getAttributeValue(node, "src");
                    if (src.length()==0) return;
                    //console.log(src);


                  //Update link
                    try{
                        String path = getPath(src);
                        if (path.contains("*")){ //Special case for wildcard links


                          //Create new xml document
                            StringBuilder str = new StringBuilder();
                            str.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n");
                            str.append("<scripts>\r\n");
                            for (String link : getWildcardLinks(src, path)){
                                str.append("<script src=\"");
                                str.append(link);
                                str.append("\"></script>\r\n");
                            }
                            str.append("</scripts>");
                            //console.log(str);


                          //Replace original node with new nodes
                            org.w3c.dom.Document xml = createDocument(str.toString());
                            synchronized(nodes){
                                int idx = nodes.indexOf(node);
                                nodes.set(idx, getOuterNode(xml));
                            }

                        }
                        else{ //Append version number to the path
                            javaxt.io.File jsFile = new javaxt.io.File(path);
                            if (jsFile.exists()){
                                long lastModified = jsFile.getLastModifiedTime().getTime();
                                long currVersion = new javaxt.utils.Date(lastModified).toLong();
                                setAttributeValue(node, "src" , src + "?v=" + currVersion);
                                addDate(lastModified);
                            }
                        }
                    }
                    catch(Exception e){
                        //e.printStackTrace();
                        //System.out.println("Invalid path? " + src);
                    }
                }
                else if (nodeName.equals("link")){

                  //Update links to css files
                    String href = getAttributeValue(node, "href");
                    String type = getAttributeValue(node, "type");
                    String rel = getAttributeValue(node, "rel");
                    boolean isStyleSheet = type.equalsIgnoreCase("text/css");
                    if (!isStyleSheet) isStyleSheet = rel.equalsIgnoreCase("stylesheet");
                    if (href.length()>0 && isStyleSheet){

                        try{
                            String path = getPath(href);
                            if (path.contains("*")){ //Special case for wildcard links


                              //Create new xml document
                                StringBuilder str = new StringBuilder();
                                str.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n");
                                str.append("<links>\r\n");
                                for (String link : getWildcardLinks(href, path)){

                                    str.append("<link href=\"");
                                    str.append(link);
                                    str.append("\" rel=\"stylesheet\" />\r\n");

                                }
                                str.append("</links>");
                                //console.log(str);


                              //Replace original node with new nodes
                                org.w3c.dom.Document xml = createDocument(str.toString());
                                synchronized(nodes){
                                    int idx = nodes.indexOf(node);
                                    nodes.set(idx, getOuterNode(xml));
                                }

                            }
                            else{
                                javaxt.io.File cssFile = new javaxt.io.File(path);
                                if (cssFile.exists()){ //Append version number to the path
                                    long lastModified = cssFile.getLastModifiedTime().getTime();
                                    long currVersion = new javaxt.utils.Date(lastModified).toLong();
                                    setAttributeValue(node, "href" , href + "?v=" + currVersion);
                                    addDate(lastModified);
                                }
                            }
                        }
                        catch(Exception e){
                            //e.printStackTrace();
                            //System.out.println("Invalid path? " + href);
                        }
                    }
                }
            }


            private ArrayList<String> getWildcardLinks(String src, String path) throws Exception {

              //Get file path
                javaxt.io.File f = new javaxt.io.File(path);
                javaxt.io.Directory d = f.getDirectory();
                String search = f.getName();


              //Build relative path to the files
                String basePath = src.substring(0, src.indexOf("*"));
                int x = d.toString().replace("\\", "/").lastIndexOf(basePath);


              //Find files and return links
                ArrayList<String> links = new ArrayList<>();
                for (javaxt.io.File file : d.getFiles(search, true)){
                    long lastModified = file.getLastModifiedTime().getTime();
                    long currVersion = new javaxt.utils.Date(lastModified).toLong();
                    addDate(lastModified);

                    String p = file.getDirectory().toString().replace("\\", "/").substring(x);
                    links.add(p + file.getName() + "?v=" + currVersion);

                  //Files copied/pasted from another source may retain their
                  //original timestamp. If these includes are older than any
                  //other files, the newly added files will be missed on the
                  //next load/refresh. As a workaround, we'll include a
                  //timestamp of the parent folder. Copied folders will have a
                  //timestamp of when the folder was copied.
                    addDate(file.getDirectory().getLastModifiedTime().getTime());
                }
                return links;
            }


            private String getPath(String relPath){
                if (relPath.startsWith("/")){
                    relPath = relPath.substring(1);
                    if (relPath.startsWith("/")) throw new RuntimeException();
                    return web + relPath;
                }
                else{
                    return xmlFile.MapPath(relPath);
                }
            }

            private void addDate(long lastModified) throws Exception {
                java.util.HashSet<Long> dates = (java.util.HashSet<Long>) get("dates");
                if (dates==null){
                    dates = new java.util.HashSet<>();
                    set("dates", dates);
                }
                dates.add(lastModified);
            }

            public void exit(){
                java.util.HashSet<Long> dates = (java.util.HashSet<Long>) get("dates");
                if (dates==null || dates.isEmpty()) return;
                synchronized(uniqueDates){
                    java.util.Iterator<Long> it = dates.iterator();
                    while (it.hasNext()){
                        uniqueDates.put(it.next(), true);
                    }
                    uniqueDates.notify();
                }
            }


        }.start();


      //Insert records
        for (Node node : nodes){
            pool.add(node);
        }


      //Notify the pool that we have finished added records and Wait for threads to finish
        pool.done();
        pool.join();


      //Get most recent file date
        java.util.TreeSet<Long> dates = new java.util.TreeSet<>();
        dates.addAll(uniqueDates.keySet());
        return dates.last();
    }


  //**************************************************************************
  //** updateNodes
  //**************************************************************************
  /** Adds empty comment blocks to "childless" nodes to prevent self-enclosing
   *  tags.
   */
    public void updateNodes(NodeList nodes, org.w3c.dom.Document xml){
        for (int i=0; i<nodes.getLength(); i++){
            Node node = nodes.item(i);
            if (node.getNodeType()==1){
                if (hasChildren(node)){
                    updateNodes(node.getChildNodes(), xml);
                }
                else{
                    updateNode(node, xml);
                }
            }
        }
    }


  //**************************************************************************
  //** updateNode
  //**************************************************************************
  /** Adds an empty comment block to a node to prevent self-enclosing tags.
   */
    public void updateNode(Node node, org.w3c.dom.Document xml){
        try{
            node.appendChild(xml.createComment(" "));
        }
        catch(Exception e){
            //System.out.println(node.getNodeName());
        }
    }


  //**************************************************************************
  //** updateTag
  //**************************************************************************
  /** Returns a HTML string for a given node. Replaces any self-enclosing tags
   *  as needed.
   */
    private String updateTag(Node node){
        String txt = getText(node);
        String nodeName = node.getNodeName().toLowerCase();
        if (txt.endsWith("/>") && nodeName.equals("script")){
            txt = txt.substring(0, txt.length()-2);
            txt += "></" + nodeName + ">";
        }
        return txt;
    }


  //**************************************************************************
  //** isValidPath
  //**************************************************************************
  /** Returns false if the given path contains illegal directives or to point
   *  hidden or protected files.
   */
    private static boolean isValidPath(String str){
        str = str.replace("\\", "/");
        if (str.contains("..") || str.contains("/.") ||
            str.toLowerCase().contains("/keystore")){
            return false;
        }
        return true;
    }
}