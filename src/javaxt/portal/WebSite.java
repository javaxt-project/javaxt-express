package javaxt.portal;
import javaxt.http.servlet.*;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

//******************************************************************************
//**  WebSite Servlet
//******************************************************************************
/**
 *   Servlet used to serve up files and images for a website.
 *
 ******************************************************************************/

public abstract class WebSite extends HttpServlet {

    private static final String z = "GMT";
    private static final TimeZone tz = TimeZone.getTimeZone(z);
    
    private javaxt.io.Directory web;
    private javaxt.io.File template;
    private Tabs tabs;
    private String companyName;
    private String companyAcronym;
    private String author;
    private String keywords;

    private String Path = "/";
    private Redirects redirects;


    private String[] fileExtensions = new String[]{
    ".html", ".txt"
    };
    
    private String[] DefaultFileNames = new String[]{
    "home", "index", "Overview"
    };
    
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
    
    public WebSite(javaxt.io.Directory web){
        this.web = web;
        this.template = new javaxt.io.File(web + "style/template.html");
        this.tabs = new Tabs(new javaxt.io.File(web + "style/tabs.txt"));
        this.redirects = new Redirects(new javaxt.io.File(web + "style/redirects.txt"));
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
       return "Copyright &copy; " + new javaxt.utils.Date().getYear();
    }
    

  //**************************************************************************
  //** processRequest
  //**************************************************************************
    public void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        
        
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



        
      //Send static file if we can
        javaxt.io.File file = getFile(request);
        if (file!=null){

          //Check whether the file ends in a ".html" or ".txt" extension. If so, 
          //check whether the file is static or if needs to be wrapped 
          //in a template.
            boolean sendFile = true;
            String ext = file.getExtension().toLowerCase();
            if (ext.equals("html")){
                
              //TODO: Don't send html files unless they end with a </html> tag
                sendFile = false;
            }
            else if (ext.equals("txt")){
                
              //Don't send text files from the wiki directory
                String filePath = file.getDirectory().toString();
                int idx = filePath.indexOf("/wiki/");
                sendFile = (idx==-1);
            }
            
            if (sendFile){ 
                sendFile(file, response);
                return;
            }
        }      

        
        

        
        
      //If we're still here, generate html
        sendHTML(request, response);
    }
    


  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /** Used to send a static file to the client (e.g. css, javascript, images,
   *  zip files, etc).
   */
    private void sendFile(javaxt.io.File file, HttpServletResponse response)
    throws ServletException, IOException {
        
        
        
        if (file==null){
            response.sendError(404);
            return;
        }
        
        
        String contentType = file.getContentType(); //returns incorrect MIME type for xml
        if (file.getExtension().equalsIgnoreCase("xml")) contentType = "text/xml";
        response.write(file.toFile(), contentType, true);
        
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a path to a static file (e.g. css, javascript, images, zip, etc)
   */
    private javaxt.io.File getFile(HttpServletRequest request){
        
      //Get requested file from the querystring (Legacy)
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
        if (filename==null || filename.equals("") || filename.contains("..") ||
            filename.toLowerCase().startsWith("bin/")
        ){
            return null;
        }
        else{
          //Make sure none of the directories/files in the path are "hidden".
          //Any directory that statrs with a "." is considered hidden.
            for (String path : filename.split("/")){
                if (path.trim().startsWith(".")){
                    return null;
                }
            }
        }



        javaxt.io.File file = new javaxt.io.File(web + filename);
        if (!file.exists()) file = new javaxt.io.File(web + "downloads/" + filename);        
        if (!file.exists()) return null;
        return file;
    }


  //**************************************************************************
  //** sendHTML
  //**************************************************************************
  /** Used to construct an html document from a template and an html snippet.
   */
    private void sendHTML(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {


        
      //Get the html snippet
        javaxt.io.File file = getHtmlFile(request.getURL());
        if (file==null){
            response.sendError(404);
            return;
        }




      //Calculate last modified date
        java.util.TreeSet<Long> dates = new java.util.TreeSet<Long>();
        dates.add(file.getDate().getTime());
        dates.add(template.getDate().getTime());
        dates.add(tabs.getLastModified());
        long lastModified = dates.last();
        String date = getDate(lastModified); //"EEE, dd MMM yyyy HH:mm:ss zzz"

        



      //Create eTag using the combined, uncompressed size of the file and template
        String eTag = "W/\"" + (file.getSize()+template.getSize()) + "-" + lastModified + "\"";


      //Return 304/Not Modified response if we can...
        boolean useCache = true;
        if (useCache){
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
                            if (tag.trim().equalsIgnoreCase(date)){
                                //System.out.println("Sending 304 Response!");
                                response.setStatus(304);
                                return;
                            }
                        }
                    }

                }
            }
        }

        

        
        
        
      //Get file content
        String content = getContent(request, file);
        content = content.replace("<%=Path%>", Path);
        
        
        
      //Check whether the client wants the raw file content or if we should
      //wrap the content in a template (default).
        boolean useTemplate = true;
        String templateParam = request.getParameter("template");
        if (templateParam!=null){
            if (templateParam.equals("false")){
                useTemplate = false;
            }
        }


        

      //Generate html response
        String html;
        if (useTemplate){
            
            
          //Instantiate html parser
            javaxt.html.Parser document = new javaxt.html.Parser(content);

        
          //Extract Title
            String title = null;
            try{
                javaxt.html.Element el = document.getElementByTagName("title");
                content = content.replace(el.getOuterHTML(), "");
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
                    title = file.getName(false);
                    for (String fileName : DefaultFileNames){
                        if (title.equalsIgnoreCase(fileName)){
                            title = file.getDirectory().getName();
                            break;
                        }
                    }
                }
            }
        
        

          //Extract Description
            String description = null;
            try{
                javaxt.html.Element el = document.getElementByTagName("description");
                content = content.replace(el.getOuterHTML(), "");
                description = el.getInnerHTML();
            }
            catch(Exception e){}
            if (description==null) description = "";


        
          //Extract Keywords
            String keywords = null;
            try{
                javaxt.html.Element el = document.getElementByTagName("keywords");
                content = content.replace(el.getOuterHTML(), "");
                keywords = el.getInnerHTML();
            }
            catch(Exception e){}
            if (keywords==null) keywords = this.keywords;
            if (keywords==null) keywords = "";
            
            
            
            html = template.getText("UTF-8");
            html = html.replace("<%=content%>", content);
            html = html.replace("<%=title%>", title);
            html = html.replace("<%=description%>", description);
            html = html.replace("<%=keywords%>", keywords);
            html = html.replace("<%=author%>", author==null ? "" : author);
            html = html.replace("<%=Path%>", Path);
            html = html.replace("<%=companyName%>", companyName==null ? "": companyName);
            html = html.replace("<%=copyright%>", getCopyright());
            html = html.replace("<%=navbar%>", getNavBar(request, file));
            html = html.replace("<%=tabs%>", getTabs(request, file, tabs));
        }
        else{
            html = content;
        }



      //Convert the html to a byte array
        byte[] rsp = html.trim().getBytes("UTF-8");



      //Set response headers
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html");
        response.setContentLength(rsp.length);
        response.setHeader("ETag", eTag);
        response.setHeader("Last-Modified", date); //Sat, 23 Oct 2010 13:04:28 GMT


      //Send response
        response.write(rsp);
    }

    
  //**************************************************************************
  //** getContent
  //**************************************************************************
  /** Returns an html snippet found in the given file. This method can be 
   *  overridden to generate dynamic content or to support custom tags.
   */
    protected String getContent(HttpServletRequest request, javaxt.io.File file){
        return file.getText("UTF-8");
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
        String[] contentFolders = new String[]{"documentation", "wiki"};
        for (String folderName : contentFolders){
            
            folderPath = web + folderName + "/";
            
            file = getFile(path, folderPath);
            if (file!=null) return file;
        }
      
        
        return null;
    }
    
    
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
            for (String fileName : DefaultFileNames){
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
    protected String getIndex(javaxt.io.File file){

      //Get file path
        javaxt.io.Directory dir = file.getDirectory();
        String path = dir.getPath();
        int len = path.length();
        
        
      //Generate list of files and dates
        java.util.List<javaxt.io.File> files = new java.util.LinkedList<javaxt.io.File>();
        java.util.TreeSet<Long> dates = new java.util.TreeSet<Long>();
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
        java.util.Iterator<javaxt.io.File> it = files.iterator();
        while (it.hasNext()){
            
            javaxt.io.File f = it.next();
            String fileName = f.getName(false);
            String relPath = f.getDirectory().getPath().substring(len).replace("\\", "/");
            if (relPath.endsWith("/")) relPath = relPath.substring(0, relPath.length()-1);
            String link = "/" + relPath + "/" + fileName;

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
        java.util.Date lastModified = new java.util.Date(dates.last());
        if (!lastModified.equals(file.getDate())) System.out.println("Update file date: " + lastModified);
        file.setDate(lastModified);


        return toc.toString();
    }
    

  //**************************************************************************
  //** getTabs
  //**************************************************************************
  /** Returns an html fragment used to render tabs.
   */
    private String getTabs(HttpServletRequest request, javaxt.io.File file, Tabs tabs){

        
      //Get relative path to the file
        String path = file.getDirectory().toString();
        path = path.substring(web.toString().length());
        path = path.replace("\\", "/");
        if (!path.startsWith("/")) path = "/" + path;
        

      //Get tab entries
        java.util.LinkedHashMap<String, String> items = tabs.getItems();
        java.util.Iterator<String> it = items.keySet().iterator();


      //Create html fragment
        StringBuffer str = new StringBuffer();
        while (it.hasNext()){
            String text = it.next();
            String link = items.get(text).replace("<%=Path%>", Path);
            boolean isActive = isActiveTab(text, link, path);
            //System.out.println("|" + path + "| vs |" + link + "|" + (isActive? " <--" : ""));

            
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
   *  @param text Tab label as defined in tabs.txt
   *  @param link Tab URL as defined in tabs.txt
   *  @param path Relative path to the file on the server (relative to the web
   *  directory).
   */
    protected boolean isActiveTab(String text, String link, String path){
        boolean isActive = false;
        if (path.startsWith(link)){ 
            if (link.equals("/")){
                isActive = path.equals("/");
            }
            else{
                isActive = true; 
            }
        }
        return isActive;
    }

    
  //**************************************************************************
  //** getNavBar
  //**************************************************************************
  /** Returns an html fragment used to render a navigation bar.
   */
    protected String getNavBar(HttpServletRequest request, javaxt.io.File file){
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

    
  //**************************************************************************
  //** getDate
  //**************************************************************************
  /** Used to convert a date to a string (e.g. "Mon, 20 Feb 2012 07:22:20 EST").
   */
    private String getDate(long milliseconds){
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(milliseconds);
        return getDate(cal);
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
}