package javaxt.express.cms;
import javaxt.express.FileManager;
import javaxt.express.utils.DateUtils;
import javaxt.http.servlet.*;
import javaxt.utils.Console;
import java.io.IOException;
import java.util.*;

//******************************************************************************
//**  WebSite Servlet
//******************************************************************************
/**
 *   Servlet used to serve up files and images for a website. HTML pages are
 *   assembled on-the-fly using an HTML template and content files. Keywords
 *   in the content files and template are substituted at runtime. Assembled
 *   files are cached by clients using last modified dates.
 *
 ******************************************************************************/

public abstract class WebSite extends HttpServlet {

    protected static Console console = new Console();
    private javaxt.io.Directory web;
    private FileManager fileManager;
    private javaxt.io.File template;
    private Tabs tabs;
    private String companyName;
    private String companyAcronym;
    private String author;
    private String keywords;
    private Redirects redirects;


    private String[] fileExtensions = new String[]{
    ".html", ".txt"
    };

    /** */
    private String[] defaultFileNames = new String[]{
        "home", "index", "Overview"
    };

    private String[] contentFolders = new String[]{
        "content",
        "documentation", //javaxt.com
        "wiki" //legacy
    };


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate the website.
   *
   *  @param web Directory that contains html files, css, javascript, images,
   *  etc. Assumes the template, tabs, and redirects are found in the style
   *  folder.
   *
   *  @param servletPath URL path to the website (relative to the hostname).
   */
    public WebSite(javaxt.io.Directory web, String servletPath){
        this.web = web;
        this.template = new javaxt.io.File(web + "style/template.html");
        this.tabs = new Tabs(new javaxt.io.File(web + "style/tabs.txt"));
        this.redirects = new Redirects(new javaxt.io.File(web + "style/redirects.txt"));
        setServletPath(servletPath);
        this.fileManager = new FileManager(web);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public WebSite(javaxt.io.Directory web){
        this(web, "/");
    }


  //**************************************************************************
  //** getWebDirectory
  //**************************************************************************
    public javaxt.io.Directory getWebDirectory(){
        return web;
    }


  //**************************************************************************
  //** getFileManager
  //**************************************************************************
    public FileManager getFileManager(){
        return fileManager;
    }


  //**************************************************************************
  //** setCompanyName
  //**************************************************************************
    public void setCompanyName(String companyName){
        this.setCompanyName(companyName, null);
    }

    public void setCompanyName(String companyName, String companyAcronym){
        this.companyName = companyName;
        this.companyAcronym = companyAcronym;
    }


  //**************************************************************************
  //** setAuthor
  //**************************************************************************
    public void setAuthor(String author){
        this.author = author;
    }


  //**************************************************************************
  //** getCopyright
  //**************************************************************************
  /** Returns the copyright text (e.g. "Copyright &copy; 2012"). Classes that
   *  extend this class can override this method.
   */
    protected String getCopyright(){
       return "Copyright &copy; " + getYear();
    }


  //**************************************************************************
  //** getYear
  //**************************************************************************
    protected int getYear(){
        return new javaxt.utils.Date().getYear();
    }


  //**************************************************************************
  //** processRequest
  //**************************************************************************
    public void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        long t = System.currentTimeMillis();


      //Redirect as needed
        java.net.URL url = request.getURL();
        if (redirect(url, response)) return;



      //Upgrade to HTTPS if we can...
        if (this.supportsHttps()){
            response.setHeader("Content-Security-Policy", "upgrade-insecure-requests");
            String upgradeRequest = request.getHeader("Upgrade-Insecure-Requests");
            if (upgradeRequest!=null && upgradeRequest.equals("1")){
                if (!url.getProtocol().equalsIgnoreCase("https")){
                    String location = url.toString();
                    location = "https" + location.substring(location.indexOf(":"));

                    response.setStatus(307);
                    //response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                    response.setHeader("Vary", "Upgrade-Insecure-Requests");
                    response.setHeader("Location", location);
                    return;
                }
            }
        }


      //Get path from URL, excluding servlet path and leading "/" character
        String path = getPath(url);



      //Special case for Certbot. When generating certificates using the
      //certonly command, Certbot creates a hidden directory in the web root.
      //The web server must return the files in this hidden directory. However,
      //the filemanager does not allow access to hidden directories so we need
      //to handle these requests manually.
        if (path.startsWith(".well-known")){
            console.log(path);
            java.io.File file = new java.io.File(web + path);
            console.log(file + "\t" + file.exists());

          //Send file
            if (file.exists()){
                response.write(file, javaxt.io.File.getContentType(file.getName()), true);
            }
            else{
                response.setStatus(404);
                response.setContentType("text/plain");
            }
            return;
        }



      //Send static file if we can
        javaxt.io.File file = getFile(path);
        if (file!=null){

          //Check whether the file ends in a ".html" or ".txt" extension. If so,
          //check whether the file is static or if needs to be wrapped
          //in a template.
            boolean sendFile = true;
            String ext = file.getExtension().toLowerCase();
            if (ext.equals("html")){

              //Don't send html files unless they end with a </html> tag
                sendFile = !isSnippet(file);
            }
            else if (ext.equals("txt")){

              //Don't send text files from any of the content folders (e.g. wiki directory)
                String filePath = file.getDirectory().toString();
                for (String folderName : contentFolders){
                    int idx = filePath.indexOf("/" + folderName + "/");
                    if (idx>-1){
                        sendFile = false;
                        break;
                    }
                }
            }

            if (sendFile){
                sendFile(file, fileManager, request, response);
                return;
            }
        }
        else{

          //Check whether the url path ends with a file extension. Return an error
            int idx = path.lastIndexOf("/");
            if (idx>-1) path = path.substring(idx);
            idx = path.lastIndexOf(".");
            if (idx>-1){
                //console.log(path);
                response.sendError(404);
                return;
            }
        }




      //If we're still here, generate html response
        sendHTML(request, response);
        //console.log("processRequest", System.currentTimeMillis()-t);
    }


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a static file to the client (e.g. css, javascript, images,
   *  zip files, etc). By default, this method simple calls the following:
      <pre>
        fileManager.sendFile(file, request, response);
      </pre>
   *
   *  Callers can override this method and add additional logic (e.g. auditing,
   *  authorization, logging, etc).
   */
    protected void sendFile(javaxt.io.File file, FileManager fileManager,
        HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException {

        fileManager.sendFile(file, request, response);
    }


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Returns the path part of a url, excluding servlet path and leading "/"
   *  character
   */
    private String getPath(java.net.URL url){
        String path = url.getPath();
        String servletPath = getServletPath();
        if (!servletPath.endsWith("/")) servletPath += "/";
        path = path.substring(path.indexOf(servletPath)).substring(servletPath.length());
        if (path.startsWith("/")) path = path.substring(1);
        return path;
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a path to a static file (e.g. css, javascript, images, zip, etc)
   */
    private javaxt.io.File getFile(String path){


      //Restrict access to the "bin" directory
        if (path.toLowerCase().startsWith("bin/")){
            return null;
        }


      //Construct a list of possible file paths
        ArrayList<String> files = new ArrayList<>();
        files.add(path);
        files.add("downloads/" + path);


      //Loop through possible file combinations
        for (String str : files){
            java.io.File file = fileManager.getFile(str);
            if (file!=null) return new javaxt.io.File(file);
        }

        return null;
    }


  //**************************************************************************
  //** sendHTML
  //**************************************************************************
  /** Used to construct an html document from a template and an html snippet.
   */
    private void sendHTML(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        long t = System.currentTimeMillis();
        String servletPath = getServletPath();
        if (!servletPath.endsWith("/")) servletPath += "/";


      //Get the html file
        java.net.URL url = request.getURL();
        javaxt.io.File file = getHtmlFile(url); //<--Watch for NPE!


      //Check whether the client wants the raw file content or if we should
      //wrap the content in a template (default).
        boolean useTemplate = true;
        String templateParam = request.getParameter("template");
        if (templateParam!=null){
            if (templateParam.equals("false")){
                useTemplate = false;
            }
        }
        if (useTemplate){
            if (template==null || !template.exists()) useTemplate = false;
        }




      //Calculate last modified date and estimated file fize
        TreeSet<Long> dates = new TreeSet<>();
        if (file!=null) dates.add(file.getDate().getTime());
        if (useTemplate){
            dates.add(template.getDate().getTime());
            dates.add(tabs.getLastModified());
        }




      //Get content
        Content content = getContent(request, file);
        if (content==null){
            content = new Content("404", new Date());
            content.setStatusCode(404);
        }
        dates.add(content.getDate().getTime());
        String html = content.getHTML();




      //Update HTML. Note that there are currently two major bottlenecks here:
      //(1) html parser in "useTemplate" block and (2) the updateLinks method
      //Both can be mitigated with some simple caching
        long t0 = System.currentTimeMillis();
        if (useTemplate){


          //Instantiate html parser
            long t1 = System.currentTimeMillis();
            javaxt.html.Parser document = new javaxt.html.Parser(html);


          //Extract Title
            String title = null;
            try{
                javaxt.html.Element el = document.getElementByTagName("title");
                html = html.replace(el.getOuterHTML(), "");
                title = el.getInnerText().trim();
            }
            catch(Exception e){}


            if (title==null){
                try{
                    title = document.getElementByTagName("h1").getInnerHTML();
                }
                catch(Exception e){}
            }
            if (title==null){
                if (companyName!=null && companyAcronym!=null){
                    title = companyAcronym + " - " + companyName;
                }
                else{
                    if (file!=null){
                        title = file.getName(false);
                        for (String fileName : defaultFileNames){
                            if (title.equalsIgnoreCase(fileName)){
                                title = file.getDirectory().getName();
                                break;
                            }
                        }
                    }
                }
            }
            if (title==null) title = "";



          //Extract Description
            String description = null;
            try{
                javaxt.html.Element el = document.getElementByTagName("description");
                html = html.replace(el.getOuterHTML(), "");
                description = el.getInnerHTML();
            }
            catch(Exception e){}
            if (description==null) description = "";



          //Extract Keywords
            String keywords = null;
            try{
                javaxt.html.Element el = document.getElementByTagName("keywords");
                html = html.replace(el.getOuterHTML(), "");
                keywords = el.getInnerHTML();
            }
            catch(Exception e){}
            if (keywords==null) keywords = this.keywords;
            if (keywords==null) keywords = "";
            //console.log("parser", System.currentTimeMillis()-t1);


            html = template.getText().replace("<%=content%>", html);
            html = html.replace("<%=title%>", title);
            html = html.replace("<%=description%>", description);
            html = html.replace("<%=keywords%>", keywords);
            html = html.replace("<%=author%>", author==null ? "" : author);

            html = html.replace("<%=companyName%>", companyName==null ? "": companyName);
            html = html.replace("<%=year%>", getYear()+"");
            html = html.replace("<%=copyright%>", getCopyright());

            html = html.replace("<%=tabs%>", getTabs(url.getPath(), tabs));
            html = html.replace("<%=breadcrumbs%>", getBreadcrumbs(request));
            html = html.replace("<%=sidebar%>", getSidebar(request));

            html = html.replace("<%=Path%>", servletPath);
            html = updateLinks(html, dates, template);

            //console.log("useTemplate", System.currentTimeMillis()-t0);
        }
        else{

            html = html.replace("<%=Path%>", servletPath);
            html = updateLinks(html, dates, file);
            //console.log("updateLinks", System.currentTimeMillis()-t0);
        }




      //Remove any orphan tags
        if (html.contains("<%=") && html.contains("%>")){
            StringBuilder str = new StringBuilder();
            String[] arr = html.split("<%=");
            for (int i=0; i<arr.length; i++){
                String s = arr[i];
                if (i>0){
                    int idx = s.indexOf("%>");
                    if (idx>-1) s = s.substring(idx+2);
                }
                str.append(s);
            }
            html = str.toString();
        }



      //Trim the html
        html = html.trim();
        //console.log("html", System.currentTimeMillis()-t);



      //Get last modified date
        long lastModified = dates.last();


      //Set response headers
        response.setStatus(content.getStatusCode());
        response.setContentType("text/html");

        
      //Send response
        response.write(html, lastModified);
        //console.log("sendHTML", System.currentTimeMillis()-t);
    }


  //**************************************************************************
  //** updateLinks
  //**************************************************************************
  /** Updates links in "script" and "link" tags with a querystring representing
   *  the last modified date of the file.
   */
    private String updateLinks(String html, TreeSet<Long> dates, javaxt.io.File htmlFile){

      //Generate a list of supported tags
        HashMap<String, String> tagsWithLinks = new HashMap();
        tagsWithLinks.put("script", "src");
        tagsWithLinks.put("link", "href");


      //Get elements that match the supported tags
        ArrayList<javaxt.html.Element> elements = new ArrayList<>();
        javaxt.html.Parser document = new javaxt.html.Parser(html);
        for (String tagName : tagsWithLinks.keySet()){
            String linkAttr = tagsWithLinks.get(tagName);

            for (javaxt.html.Element el : document.getElementsByTagName(tagName)){
                String url = el.getAttribute(linkAttr);

                if (!(url==null || url.isEmpty())){
                    String t = url.toLowerCase();
                    if (!t.startsWith("http://") && !t.startsWith("https://") && !t.startsWith("//")){
                        elements.add(el);
                    }
                }

            }
        }
        if (elements.isEmpty()) return html;


      //Generate an XML document
        StringBuilder str = new StringBuilder();
        str.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n");
        str.append("<links>");
        for (javaxt.html.Element el : elements){
            str.append("\r\n");
            str.append(el.toString());
            if (!el.isClosed()){
                str.append("</" + el.getName() + ">");
            }
        }
        str.append("\r\n</links>");
        org.w3c.dom.Document xml = javaxt.xml.DOM.createDocument(str.toString());


      //Update links in the XML
        try{
            long lastUpdate = fileManager.updateLinks(htmlFile, xml);
            dates.add(lastUpdate);
        }
        catch(Exception e){
            throw new RuntimeException(e);
        }



      //Update html document
        org.w3c.dom.Node outerNode = javaxt.xml.DOM.getOuterNode(xml);
        org.w3c.dom.Node[] nodes = javaxt.xml.DOM.getNodes(outerNode.getChildNodes());
        for (int i=0; i<nodes.length; i++){
            org.w3c.dom.Node node = nodes[i];
            String orgTag = elements.get(i).getOuterHTML();
            String newTag = javaxt.xml.DOM.getText(node);


          //Replace any self-enclosing script tags as needed
            String nodeName = node.getNodeName().toLowerCase();
            if (newTag.endsWith("/>") && nodeName.equals("script")){
                newTag = newTag.substring(0, newTag.length()-2);
                newTag += "></" + nodeName + ">";
            }


            html = html.replace(orgTag, newTag);

        }
        return html;
    }


  //**************************************************************************
  //** getContent
  //**************************************************************************
  /** Returns an html snippet found in the given file. This method can be
   *  overridden to generate dynamic content or to support custom tags.
   */
    protected Content getContent(HttpServletRequest request, javaxt.io.File file){
        if (file==null || !file.exists()){
            return null;
        }
        else{
            return new Content(file.getText("UTF-8"), file.getDate());
        }
    }


  //**************************************************************************
  //** getHtmlFile
  //**************************************************************************
  /** Maps the requested URL to an html snippet found in an html or txt file.
   *  Returns null if suitable a file is not found.
   */
    private javaxt.io.File getHtmlFile(java.net.URL url){

      //Get path from url
        String path = url.getPath();
        String servletPath = getServletPath();
        if (!servletPath.endsWith("/")) servletPath += "/";
        path = path.substring(path.indexOf(servletPath)).substring(servletPath.length());


      //Remove leading and trailing "/" characters
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith("/")) path = path.substring(0, path.length()-1);


      //Check whether the url points directly to a file (minus the file extension)
      //or if the url points to a directory. If so, return the file.
        String folderPath = web.toString();
        javaxt.io.File file = getFile(path, folderPath);
        if (file!=null) return file;



      //If we are still here, check whether the url is missing a content folder
      //in its path (e.g. "documentation", "wiki").

        for (String folderName : contentFolders){

            folderPath = web + folderName + "/";

            file = getFile(path, folderPath);
            if (file!=null) return file;
        }


        return null;
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
    private javaxt.io.File getFile(String path, String folderPath){

      //Check whether the url points directly to a file (minus the file extension)
        if (path.length()>0){
            //System.out.println("Checking: " + folderPath + path + ".*");
            for (String fileExtension : fileExtensions){
                javaxt.io.File file = new javaxt.io.File(folderPath + path + fileExtension);
                if (file.exists()){
                    if (isSnippet(file)) return file;
                }
            }
        }



      //Check whether the url points to a directory. If so, check whether the
      //directory has a welcome file (e.g. index.html, Overview.txt, etc).
        javaxt.io.Directory dir = new javaxt.io.Directory(folderPath + path);
        //System.out.println("Search: " + dir + " <--" + dir.exists());
        if (dir.exists()){
            for (String fileName : defaultFileNames){
                for (String fileExtension : fileExtensions){

                    javaxt.io.File file = new javaxt.io.File(dir, fileName + fileExtension);
                    if (file.exists()){
                        if (isSnippet(file)) return file;
                    }
                }
            }
        }

        return null;
    }


  //**************************************************************************
  //** isSnippet
  //**************************************************************************
    private boolean isSnippet(javaxt.io.File file){
        String str = file.getText("UTF-8").trim();
        return !str.endsWith("</html>");
    }




  //**************************************************************************
  //** getIndex
  //**************************************************************************
  /** Returns an html snippet with paths to all the html/txt files found in
   *  the given file path. Note that the file date is updated to reflect the
   *  most current file.
   */
    protected Content getIndex(javaxt.io.File file){


      //Get relative path to the file
        javaxt.io.Directory dir = file.getDirectory();
        String path = dir.toString();
        path = path.substring(web.toString().length());
        path = path.replace("\\", "/");
        if (!path.startsWith("/")) path = "/" + path;
        if (!path.endsWith("/")) path += "/";


      //Generate list of files and dates
        List<javaxt.io.File> files = new LinkedList<>();
        TreeSet<Long> dates = new TreeSet<>();
        dates.add(file.getDate().getTime());
        for (javaxt.io.File f : dir.getFiles(fileExtensions, true)){
            if (!f.equals(file)){
                files.add(f);
                dates.add(f.getDate().getTime());
            }
        }


      //Build table of contents using ul/li tags
        StringBuffer toc = new StringBuffer();
        toc.append("<ul>\r\n");
        String prevPath = "";
        int len = dir.getPath().length();
        Iterator<javaxt.io.File> it = files.iterator();
        while (it.hasNext()){

            javaxt.io.File f = it.next();
            String fileName = f.getName(false);
            String relPath = f.getDirectory().getPath().substring(len).replace("\\", "/");

            String link = path;
            if (relPath.length()>0){
                link += relPath;
            }
            link += fileName;


            String li = "<li><a href=\"" + link + "\">" + fileName.replace("_", " ") + "</a></li>\r\n";


            if (relPath.equals(prevPath)){
                toc.append(li);
            }
            else{
                String[] prevDirs = prevPath.split("/");
                String[] currDirs = relPath.split("/");

              //Close previous UL tags
                if (prevPath.length()>0){


                  //Compute number of tags to close
                    int numTags = prevDirs.length;
                    for (int i=0; i<prevDirs.length; i++){
                        String prevDir = prevDirs[i];
                        String currDir = (i<currDirs.length-1 ? currDirs[i] : "");
                        if (prevDir.equals(currDir)){
                            numTags--;
                        }
                        else{
                            break;
                        }
                    }

                  //Close the tags
                    for (int j=0; j<numTags; j++){
                        toc.append("</ul>\r\n");
                    }

                }



              //Compute number of tags to open
                int numTags = currDirs.length;
                if (prevPath.length()>0){
                    for (int i=0; i<currDirs.length; i++){
                        String currDir = currDirs[i];
                        String prevDir = (i<prevDirs.length-1 ? prevDirs[i] : "");
                        if (currDir.equals(prevDir)){
                            numTags--;
                        }
                        else{
                            break;
                        }
                    }
                }


              //Open new tags
                for (int i=0; i<numTags; i++){
                    int offset = (currDirs.length)-numTags;
                    int idx = offset+i;
                    String dirName = currDirs[idx];


                    String tag = null;
                    if (idx==0){
                        tag = "h2";
                    }


                    toc.append("<li>");

                    if (tag!=null) toc.append("<" + tag + ">");
                    toc.append(dirName.replace("_", " "));
                    if (tag!=null) toc.append("</" + tag + ">");

                    toc.append("</li>\r\n");


                    toc.append("<ul>\r\n");
                }



                toc.append(li);





                prevPath = relPath;
            }


          //Close tags
            if (!it.hasNext()){


              //Compute number of tags to close
                String[] currDirs = relPath.split("/");
                int numTags = currDirs.length;


              //Close the tags
                for (int j=0; j<numTags; j++){
                    toc.append("</ul>\r\n");
                }
            }


        }
        toc.append("</ul>\r\n");




      //Update the date of the file to the most recent file in the directory
        Date lastModified = new Date(dates.last());
        //if (!lastModified.equals(file.getDate())) System.out.println("Update file date: " + lastModified);
        //file.setDate(lastModified);


        return new Content(toc.toString(), lastModified);
    }


  //**************************************************************************
  //** getTabs
  //**************************************************************************
  /** Returns an html fragment used to render tabs.
   */
    private String getTabs(String reqPath, Tabs tabs){


      //Get tab entries
        LinkedHashMap<String, String> items = tabs.getItems();
        Iterator<String> it = items.keySet().iterator();


        String servletPath = getServletPath();
        if (!servletPath.endsWith("/")) servletPath += "/";

      //Create html fragment
        StringBuilder str = new StringBuilder();
        while (it.hasNext()){
            String text = it.next();
            String link = items.get(text).replace("<%=Path%>", servletPath);
            boolean isActive = isActiveTab(text, link, reqPath);
            //System.out.println("|" + reqPath + "| vs |" + link + "|" + (isActive? " <--" : ""));


            str.append("<a href=\"" + link + "\">");
            str.append("<div");
            if (isActive) str.append(" class=\"active\"");
            str.append(">");
            str.append(text);
            str.append("</div>");
            str.append("</a>");
        }


        return str.toString();
    }


  //**************************************************************************
  //** isActiveTab
  //**************************************************************************
  /** Returns true if a given tab should be marked as active.
   *  @param tabLabel Tab label as defined in tabs.txt
   *  @param tabLink Tab URL as defined in tabs.txt
   *  @param reqPath Relative path to the file on the server (relative to the web
   *  directory).
   */
    protected boolean isActiveTab(String tabLabel, String tabLink, String reqPath){
        boolean isActive = false;
        if (reqPath.startsWith(tabLink)){
            String servletPath = getServletPath();
            if (!servletPath.endsWith("/")) servletPath += "/";
            if (tabLink.equals(servletPath)){
                isActive = reqPath.equals(servletPath);
            }
            else{
                isActive = true;
            }
        }
        return isActive;
    }


  //**************************************************************************
  //** getBreadcrumbs
  //**************************************************************************
  /** Returns an html fragment used to render breadcrumb navigation links.
   *  Breadcrumb navigation helps the user to understand their location in the
   *  website by providing a breadcrumb trail back to the start page.
   */
    protected String getBreadcrumbs(HttpServletRequest request){
        StringBuilder str = new StringBuilder();

        String path = request.getPath();
        if (path.contains("?")) path = path.substring(0, path.indexOf("?"));
        if (!path.endsWith("/")) path += "/";

        String servletPath = getServletPath();
        if (!servletPath.endsWith("/")) servletPath += "/";

        int idx = path.indexOf(servletPath);
        if (idx>-1){
            String[] arr = path.substring(idx + servletPath.length()).split("/");
            for (int i=0; i<arr.length; i++){
                String text = arr[i].replace("_", " ");
                if (i<arr.length-1){
                    String link = servletPath + String.join("/", Arrays.copyOfRange(arr, 0, i+1));
                    str.append("<a href=\"" + link + "\">");
                    str.append("<div>");
                    str.append(text);
                    str.append("</div>");
                    str.append("</a>");
                }
                else{
                    str.append("<div>");
                    str.append(text);
                    str.append("</div>");
                }
            }
        }

        return str.toString();
    }


  //**************************************************************************
  //** getSidebar
  //**************************************************************************
  /** Returns an html fragment used to render a sidebar.
   */
    protected String getSidebar(HttpServletRequest request){
        return "";
    }


  //**************************************************************************
  //** getRedirect
  //**************************************************************************
  /** Returns true if a 301 response has been returned to the client.
   */
    private boolean redirect(java.net.URL url, HttpServletResponse response)
    throws ServletException, IOException {

        String redirect = redirects.getRedirect(url);
        if (redirect!=null){
            response.sendRedirect(redirect, true);
            return true;
        }

        return false;
    }
}