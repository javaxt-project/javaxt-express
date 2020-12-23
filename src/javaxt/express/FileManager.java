package javaxt.express;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

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
    private static final String z = "GMT";
    private static final TimeZone tz = TimeZone.getTimeZone(z);


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public FileManager(javaxt.io.Directory web){
        this.web = web;
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client.
   */
    public void sendFile(HttpServletRequest request, HttpServletResponse response)
        throws IOException{

      //Get path from url, excluding servlet path and leading "/" character
        String path = request.getPathInfo();
        if (path!=null) path = path.substring(1);

      //Send file
        sendFile(path, request, response);
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client.
   */
    public void sendFile(String path, HttpServletRequest request, HttpServletResponse response)
        throws IOException {


        if (path==null) path = "";



      //Construct a list of possible file paths
        java.util.ArrayList<String> files = new java.util.ArrayList<String>();
        files.add(web + path);
        if (path.length()>0 && !path.endsWith("/")) path+="/";
        for (String welcomeFile : welcomeFiles){
            files.add(web + path + welcomeFile);
        }



      //Loop through all the possible file combinations
        for (String str : files){

          //Ensure that the path doesn't have any illegal directives
            str = str.replace("\\", "/");
            if (str.contains("..") || str.contains("/.") ||
                str.toLowerCase().contains("/keystore")){
                continue;
            }



          //Send file if it exists
            java.io.File file = new java.io.File(str);
            if (file.exists() && file.isFile() && !file.isHidden()){
                _sendFile(file, request, response);
                return;
            }

        }


      //If we're still here, throw an error
        response.setStatus(404);
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client.
   */
    public void sendFile(java.io.File file, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        if (file.exists() && file.isFile() && !file.isHidden()){
            _sendFile(file, request, response);
        }
        else{
            response.setStatus(404);
        }
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client.
   */
    public void sendFile(javaxt.io.File file, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        this.sendFile(file.toFile(), request, response);
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a file to the client. Does not check if the file exists or
   *  is valid. Only returns 200 and 3XX responses. It is up to the caller to
   *  pass in a valid file and return errors to the client (e.g. 404).
   */
    private void _sendFile(java.io.File file, HttpServletRequest request, HttpServletResponse response)
        throws IOException {

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


              //Convert html to xml. Assumes the html file is xhtml.
              //Note that the parser will be extremely slow if there is
              //a !DOCTYPE declaration.
                javaxt.io.File htmlFile = new javaxt.io.File(file);
                org.w3c.dom.Document xml = htmlFile.getXML();
                java.util.TreeSet<Long> dates = new java.util.TreeSet<Long>();
                dates.add(file.lastModified());


              //Update links to scripts
                for (org.w3c.dom.Node node : javaxt.xml.DOM.getElementsByTagName("script", xml)){
                    String src = javaxt.xml.DOM.getAttributeValue(node, "src");
                    if (src.length()>0)
                    try{
                        javaxt.io.File jsFile = new javaxt.io.File(htmlFile.MapPath(src));
                        if (jsFile.exists()){

                          //Append version number to the path
                            long lastModified = jsFile.getLastModifiedTime().getTime();
                            long currVersion = new javaxt.utils.Date(lastModified).toLong();
                            javaxt.xml.DOM.setAttributeValue(node, "src" , src + "?v=" + currVersion);
                            dates.add(lastModified);
                        }
                    }
                    catch(Exception e){
                        //System.out.println("Invalid path? " + src);
                    }
                }


              //Update links to css files
                for (org.w3c.dom.Node node : javaxt.xml.DOM.getElementsByTagName("link", xml)){
                    String href = javaxt.xml.DOM.getAttributeValue(node, "href");
                    String type = javaxt.xml.DOM.getAttributeValue(node, "type");
                    String rel = javaxt.xml.DOM.getAttributeValue(node, "rel");
                    boolean isStyleSheet = type.equalsIgnoreCase("text/css");
                    if (!isStyleSheet) isStyleSheet = rel.equalsIgnoreCase("stylesheet");
                    if (href.length()>0 && isStyleSheet){

                        try{
                            javaxt.io.File cssFile = new javaxt.io.File(htmlFile.MapPath(href));
                            if (cssFile.exists()){

                              //Append version number to the path
                                long lastModified = cssFile.getLastModifiedTime().getTime();
                                long currVersion = new javaxt.utils.Date(lastModified).toLong();
                                javaxt.xml.DOM.setAttributeValue(node, "href" , href + "?v=" + currVersion);
                                dates.add(lastModified);
                            }
                        }
                        catch(Exception e){
                            //System.out.println("Invalid path? " + href);
                        }
                    }
                }


              //Replace all self enclosing tags
                try{
                    org.w3c.dom.Node outerNode = javaxt.xml.DOM.getOuterNode(xml);
                    org.w3c.dom.NodeList nodeList = outerNode.getChildNodes();
                    for (int i=0; i<nodeList.getLength(); i++){
                        org.w3c.dom.Node node = nodeList.item(i);
                        String nodeName = node.getNodeName().toLowerCase();
                        if (nodeName.equals("head") || nodeName.equals("body")){
                            updateNodes(node.getChildNodes(), xml);
                        }
                    }
                }
                catch(Exception e){
                    //e.printStackTrace();
                }


              //Convert xml to string
                String html = javaxt.xml.DOM.getText(xml);
                html = html.replace("<!-- -->", ""); //replace empty comments
                html = html.substring(html.indexOf(">")+1); //remove xml header



              //Set content type and send response
                response.setContentType("text/html");
                sendResponse(html, dates.last(), request, response);
                return;
            }
            else if (ext.equals("xml")){


              //Check whether the xml file is a javaxt-specific file
              //with CSS and JS includes. If so, add version numbers to
              //the js and css files sourced in the xml document.
                javaxt.io.File xmlFile = new javaxt.io.File(file);
                org.w3c.dom.Document xml = xmlFile.getXML();
                String outerNode = javaxt.xml.DOM.getOuterNode(xml).getNodeName();
                if (outerNode.equals("application") || outerNode.equals("includes")){
                    java.util.TreeSet<Long> dates = new java.util.TreeSet<Long>();
                    dates.add(file.lastModified());


                  //Update links to scripts
                    for (org.w3c.dom.Node node : javaxt.xml.DOM.getElementsByTagName("script", xml)){
                        String src = javaxt.xml.DOM.getAttributeValue(node, "src");
                        if (src.length()>0)
                        try{


                            javaxt.io.File jsFile = new javaxt.io.File(xmlFile.MapPath(src));


                            if (!jsFile.exists()){
                                if (!src.startsWith("/")){
                                    jsFile = new javaxt.io.File(request.getRealPath(src));
                                }
                            }


                            if (jsFile.exists()){

                              //Append version number to the path
                                long lastModified = jsFile.getLastModifiedTime().getTime();
                                long currVersion = new javaxt.utils.Date(lastModified).toLong();
                                javaxt.xml.DOM.setAttributeValue(node, "src", src + "?v=" + currVersion);
                                dates.add(lastModified);
                            }
                        }
                        catch(Exception e){
                            System.out.println("Invalid path? " + src);
                        }
                    }


                  //Update links to css files
                    for (org.w3c.dom.Node node : javaxt.xml.DOM.getElementsByTagName("link", xml)){
                        String href = javaxt.xml.DOM.getAttributeValue(node, "href");
                        String type = javaxt.xml.DOM.getAttributeValue(node, "type");
                        if (href.length()>0 && type.equalsIgnoreCase("text/css")){

                            try{
                                javaxt.io.File cssFile = new javaxt.io.File(xmlFile.MapPath(href));


                                if (!cssFile.exists()){
                                    if (!href.startsWith("/")){
                                        cssFile = new javaxt.io.File(request.getRealPath(href));
                                    }
                                }


                                if (cssFile.exists()){

                                  //Append version number to the path
                                    long lastModified = cssFile.getLastModifiedTime().getTime();
                                    long currVersion = new javaxt.utils.Date(lastModified).toLong();
                                    javaxt.xml.DOM.setAttributeValue(node, "href" , href + "?v=" + currVersion);
                                    dates.add(lastModified);
                                }
                            }
                            catch(Exception e){
                                System.out.println("Invalid path? " + href);
                            }
                        }
                    }


                  //Set content type and send response
                    response.setContentType("application/xml");
                    sendResponse(javaxt.xml.DOM.getText(xml), dates.last(), request, response);
                    return;
                }

            }
        }


      //Send file
        response.write(file, javaxt.io.File.getContentType(file.getName()), true);

    }


  //**************************************************************************
  //** sendResponse
  //**************************************************************************
  /** Sends a given string to the client. Transparently handles caching using
   *  "ETag" and "Last-Modified" headers.
   */
    private void sendResponse(String html, long date, HttpServletRequest request,
        HttpServletResponse response) throws IOException {


      //Set response headers
        long size = html.length();
        String eTag = "W/\"" + size + "-" + date + "\"";
        response.setHeader("ETag", eTag);
        response.setHeader("Last-Modified", getDate(date)); //Sat, 23 Oct 2010 13:04:28 GMT
        //this.setHeader("Cache-Control", "max-age=315360000");
        //this.setHeader("Expires", "Sun, 30 Sep 2018 16:23:15 GMT  ");



      //Return 304/Not Modified response if we can...
        String matchTag = request.getHeader("if-none-match");
        String cacheControl = request.getHeader("cache-control");
        if (matchTag==null) matchTag = "";
        if (cacheControl==null) cacheControl = "";
        if (cacheControl.equalsIgnoreCase("no-cache")==false){
            if (eTag.equalsIgnoreCase(matchTag)){
                //System.out.println("Sending 304 Response!");
                response.setStatus(304);
                return;
            }
            else{
              //Internet Explorer 6 uses "if-modified-since" instead of "if-none-match"
                matchTag = request.getHeader("if-modified-since");
                if (matchTag!=null){
                    for (String tag: matchTag.split(";")){
                        if (tag.trim().equalsIgnoreCase(response.getHeader("Last-Modified"))){
                            //System.out.println("Sending 304 Response!");
                            response.setStatus(304);
                            return;
                        }
                    }
                }

            }
        }


        response.write(html);
    }


  //**************************************************************************
  //** updateNodes
  //**************************************************************************
  /** Adds empty comment blocks to "childless" nodes to prevent self-enclosing
   *  tags.
   */
    private void updateNodes(org.w3c.dom.NodeList nodes, org.w3c.dom.Document xml){
        for (int i=0; i<nodes.getLength(); i++){
            org.w3c.dom.Node node = nodes.item(i);
            if (node.getNodeType()==1){
                if (javaxt.xml.DOM.hasChildren(node)){
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
    private void updateNode(org.w3c.dom.Node node, org.w3c.dom.Document xml){
        try{
            node.appendChild(xml.createComment(" "));
        }
        catch(Exception e){
            System.out.println(node.getNodeName());
        }
    }


  //**************************************************************************
  //** getDate
  //**************************************************************************
  /** Used to convert a date to a string (e.g. "Mon, 20 Feb 2012 07:22:20 EST").
   *  This method does not rely on the java.text.SimpleDateFormat for
   *  performance reasons.
   */
    private static String getDate(Calendar cal){

        if (!cal.getTimeZone().equals(tz)){
            cal = (java.util.Calendar) cal.clone();
            cal.setTimeZone(tz);
        }

        StringBuffer str = new StringBuffer(29);
        switch(cal.get(Calendar.DAY_OF_WEEK)){
            case Calendar.MONDAY:    str.append("Mon, "); break;
            case Calendar.TUESDAY:   str.append("Tue, "); break;
            case Calendar.WEDNESDAY: str.append("Wed, "); break;
            case Calendar.THURSDAY:  str.append("Thu, "); break;
            case Calendar.FRIDAY:    str.append("Fri, "); break;
            case Calendar.SATURDAY:  str.append("Sat, "); break;
            case Calendar.SUNDAY:    str.append("Sun, "); break;
        }

        int i = cal.get(Calendar.DAY_OF_MONTH);
        str.append(i<10 ? "0"+i : i);

        switch (cal.get(Calendar.MONTH)) {
            case Calendar.JANUARY:   str.append(" Jan "); break;
            case Calendar.FEBRUARY:  str.append(" Feb "); break;
            case Calendar.MARCH:     str.append(" Mar "); break;
            case Calendar.APRIL:     str.append(" Apr "); break;
            case Calendar.MAY:       str.append(" May "); break;
            case Calendar.JUNE:      str.append(" Jun "); break;
            case Calendar.JULY:      str.append(" Jul "); break;
            case Calendar.AUGUST:    str.append(" Aug "); break;
            case Calendar.SEPTEMBER: str.append(" Sep "); break;
            case Calendar.OCTOBER:   str.append(" Oct "); break;
            case Calendar.NOVEMBER:  str.append(" Nov "); break;
            case Calendar.DECEMBER:  str.append(" Dec "); break;
        }

        str.append(cal.get(Calendar.YEAR));
        str.append(" ");

        i = cal.get(Calendar.HOUR_OF_DAY);
        str.append(i<10 ? "0"+i+":" : i+":");

        i = cal.get(Calendar.MINUTE);
        str.append(i<10 ? "0"+i+":" : i+":");

        i = cal.get(Calendar.SECOND);
        str.append(i<10 ? "0"+i+" " : i+" ");

        str.append(z);
        return str.toString();

        //new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        //return f.format(date);
    }


    private String getDate(long milliseconds){
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(milliseconds);
        return getDate(cal);
    }

}