package javaxt.portal;
import java.io.IOException;
import javaxt.http.servlet.HttpServlet;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;
import javaxt.http.servlet.ServletException;

//******************************************************************************
//**  Wiki Servlet
//******************************************************************************
/**
 *   Extensible Servlet used to serve up files and images for a website.
 *
 ******************************************************************************/

public abstract class Wiki extends HttpServlet {

    
    private javaxt.io.Directory share;
    private javaxt.io.Directory wiki;
    private javaxt.io.File template;
    private String companyName = "";
    private String companyAcronym = "";
    private String author = "";
    private String keywords = "";

    private String Path = "/";
    private javaxt.portal.Redirects redirects;


    public Wiki(){
    }    
    
    public Wiki(javaxt.io.Directory share, javaxt.io.Directory wiki, javaxt.io.File template){
        init(share, wiki, template);
    }

    public void init(javaxt.io.Directory share, javaxt.io.Directory wiki, javaxt.io.File template){
        this.share = share;
        this.wiki  = wiki;
        this.template = template;
    }
    
    public void setCompanyName(String companyName){
        this.setCompanyName(companyName, null);
    }

    public void setCompanyName(String companyName, String companyAcronym){
        this.companyName = companyName;
        if (companyAcronym==null) companyAcronym = "";
        this.companyAcronym = companyAcronym;
    }

    public void setAuthor(String author){
        this.author = author;
    }



  //**************************************************************************
  //** getJavaDoc
  //**************************************************************************
  /** Returns a JavaDoc associated with this request. This method is unique to
   *  websites that have java class documentation. Classes that extend this
   *  class can override this method.
   */
    protected JavaDoc getJavaDoc(HttpServletRequest request, Content content){
        return null;
    }


  //**************************************************************************
  //** getCopyright
  //**************************************************************************
  /** Returns the copyright text (e.g. "Copyright &copy; 2012"). Classes that
   *  extend this class can override this method.
   */
    protected String getCopyright(){
       /*
       int y1 = 2009;
       int y2 = new javaxt.utils.Date().getYear();
       String CopyrightYear = "" + y1 + "";
       if (y2>y1){
           CopyrightYear += "-" + y2;
       }
       return "Copyright &copy; " + CopyrightYear;
       */
       return "Copyright &copy; " + new javaxt.utils.Date().getYear();
    }
    


    protected String getIndex(Content content, String service){
        return content.getIndex();
    }




  //**************************************************************************
  //** processRequest
  //**************************************************************************
  /**  Used to process http requests. Classes that extend this class often
   *   override this method.
   */
    public void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {


        String path = request.getURL().getPath();
        if (path.startsWith("/")){
            if (path.length()>1) path = path.substring(1);
            else path = "";
        }


        String service = path.toLowerCase();
        if (service.contains("/")) service = service.substring(0, service.indexOf("/"));


        if (service.equals("download") || service.equals("downloads") || 
            service.equals("images") || service.equals("style") || service.equals("html") ||
            service.equals("javascript") || service.equals("favicon.ico")){
            sendFile(service, request, response);
        }
        else if (service.equals("session")){
            String action = request.getParameter("action");
            if (action!=null){
                if (action.equalsIgnoreCase("setSelectedNodes")){

                    String param = request.getParameter("id");
                    if (param==null){
                        request.getSession().setAttribute("selectedNodes", "");
                    }
                    else{
                        request.getSession().setAttribute("selectedNodes", param);
                    }
                }
            }
        }
        else if (service.equals("h1")){

            //h1 = 36
            //search = 12

            javaxt.io.Image image = new javaxt.io.Image(request.getParameter("text"), "Lucida Sans Unicode", 36, 0, 51, 102);
            byte[] b = image.getByteArray("png");

            response.setHeader ("Content-Type", "image/png");
            response.setHeader ("Content-Length", b.length + "");
            //response.setHeader ("Cache-Control", "no-cache");

            response.write(b);
        }
        else{
            try{
                sendWiki(Path, service, request, response, true);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }

    }
    
    



  //**************************************************************************
  //** sendFile
  //**************************************************************************
  /**  Used to send a static file to the client.
   */
    private void sendFile(String service, HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        try{
            new javaxt.portal.File(request, share, Path).send(response);
        }
        catch (ServletException e){
            this.sendWiki(Path, service, request, response, true);
        }
    }


  //**************************************************************************
  //** sendWiki
  //**************************************************************************
  /**  Used to send html content to the client.
   */
    private void sendWiki(String Path, String service, HttpServletRequest request, HttpServletResponse response, boolean useCache)
    throws ServletException, IOException {

        String redirect = getRedirect(request);
        if (redirect!=null){
            response.sendRedirect(redirect, true);
            return;
        }
        
        
      //Redirect to Https as needed
        if (this.supportsHttps()){
            javaxt.utils.URL url = new javaxt.utils.URL(request.getURL());
            response.setHeader("Content-Security-Policy", "upgrade-insecure-requests");
            String upgradeRequest = request.getHeader("Upgrade-Insecure-Requests");
            if (upgradeRequest!=null && upgradeRequest.equals("1")){
                if (!url.getProtocol().equalsIgnoreCase("https")){
                    url.setProtocol("https");

                    response.setStatus(307);
                    //response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                    response.setHeader("Vary", "Upgrade-Insecure-Requests");
                    response.setHeader("Location", url.toString());
                    return;
                }
            }
        } 


        javaxt.portal.Navbar navbar = new javaxt.portal.Navbar(request, service, wiki, Path);
        javaxt.portal.Content content = new javaxt.portal.Content(request, wiki, Path);
        javaxt.io.File file = content.getFile();


      //If the file name is "wiki.txt", chances are there is an <%=index%>
      //directive. Generate the index now. This will automatically update the
      //last modified date of the file. This, in turn, will help determine the
      //correct content date and avoid incorrect 304 responses.
        String index = "";
        if (file.getName().equalsIgnoreCase("wiki.txt")){
            index = getIndex(content, service);
        }


      //Set content date. Use the last mod date from either the source file or the template
        javaxt.utils.Date date;
        javaxt.utils.Date fileDate = file.exists() ? new javaxt.utils.Date(file.getDate()) : null;
        javaxt.utils.Date templateDate = new javaxt.utils.Date(template.getDate());
        if (fileDate==null || fileDate.isBefore(templateDate)) date = templateDate;
        else date = fileDate;
        
        if (navbar.getFile().exists()){
            javaxt.utils.Date navDate = new javaxt.utils.Date(navbar.getFile().getDate());
            if (date.isBefore(navDate)) date = navDate;
        }
        date.setTimeZone("GMT");


        
        if (!file.exists()){
            response.setStatus(404);
            useCache = false;
        }


      //Create eTag using the combined, uncompressed size of the file and template
        String eTag = "W/\"" + (file.getSize()+template.getSize()) + "-" + date.getTime() + "\"";


      //Return 304/Not Modified response if we can...
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
                            if (tag.trim().equalsIgnoreCase(date.toString("EEE, dd MMM yyyy HH:mm:ss zzz"))){
                                //System.out.println("Sending 304 Response!");
                                response.setStatus(304);
                                return;
                            }
                        }
                    }

                }
            }
        }




      //Generate html response
        String title = content.getTitle();
        if (title==null) title = companyAcronym + " - " + companyName;
        String description = content.getDescription();
        if (description==null) description = "";
        String keywords = content.getKeywords();
        if (keywords==null) keywords = this.keywords;
        String text = content.toString();


      //Update content for documentation
        JavaDoc doc = getJavaDoc(request, content);
        if (doc!=null){
            title = doc.title;
            text = doc.text;
        }






        String html = template.getText("UTF-8");
        html = html.replace("<%=content%>", text);

        html = html.replace("<%=title%>", title);
        html = html.replace("<%=description%>", description);
        html = html.replace("<%=keywords%>", keywords);
        html = html.replace("<%=author%>", author);
        html = html.replace("<%=Path%>", Path);
        html = html.replace("<%=companyName%>", companyName);
        
        html = html.replace("<%=copyright%>", getCopyright());
        html = html.replace("<%=service%>", service);
        html = html.replace("<%=index%>", index);

        html = html.replace("<%=tabs%>", getTabs(request.getURL().getPath(), navbar));




        /*
        text.append("    <div class=\"sidebar\">");
        javaxt.html.Parser.Element[] h2 = html.getElementsByTagName("h2");
        javaxt.html.Parser.Element[] link = html.getElementsByTagName("a");
        if (h2.length>0){
            text.append("      <div class=\"block block-one\">");
            text.append("        <div class=\"wrap1\">");
            text.append("         <div class=\"wrap2\">");
            text.append("          <div class=\"wrap3\">");
            text.append("            <div class=\"title\"><h3>Quick Nav</h3></div>");
            text.append("                <div class=\"inner\">");
            text.append("                  <ul class=\"sidebar-list\">");
            for (int i=0; i<h2.length; i++){
                text.append("                 <li><a href=\"#\">" + h2[i].innerHTML + "</a></li>");
            }
            text.append("                  </ul>");
            text.append("            </div>");
            text.append("          </div>");
            text.append("         </div>");
            text.append("        </div>");
            text.append("      </div>");
        }
        text.append("    </div>");
        */


      //Convert the html to a byte array
        byte[] rsp = html.getBytes("UTF-8");



        //response.setHeader("Date", date.toString("EEE, dd MMM yyyy HH:mm:ss zzz"));
        //response.setHeader("Server", serverName);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html");//response.setHeader("Content-Type", format);
        response.setContentLength(rsp.length); //response.setHeader("Content-Length", rsp.length + "");
        response.setHeader("ETag", eTag);
        response.setHeader("Last-Modified", date.toString("EEE, dd MMM yyyy HH:mm:ss zzz")); //Sat, 23 Oct 2010 13:04:28 GMT



      //Dump file contents to servlet output stream
        response.write(rsp);

    }


  //**************************************************************************
  //** getTabs
  //**************************************************************************
  /**  Used to construct an html fragment used to render tabs.
   *  @param path Path of the requested URL
   */
    private String getTabs(String path, javaxt.portal.Navbar navbar){

      //Get tab entries
        java.util.ArrayList<String[]> items = navbar.getItems();


      //Identify which tab to highlight
        if (!path.endsWith("/")) path+="/";
        path = path.toLowerCase();
        String activeLink = null;
        for (String[] item : items){
            String link = item[1].trim().toLowerCase();
            String text = item[0].trim();
            
            if (path.equals(link) || path.equals(link + "/")){
                activeLink = text;
                break;
            }
        }
        if (activeLink==null){
            activeLink = "wiki";
            for (String[] item : items){
                String link = item[1].trim().toLowerCase();
                String text = item[0].trim();

                if (text.equalsIgnoreCase("Home")) continue; //<--Hack for microsites like http://kartographia/mapproxy to default to "wiki"

                if (link.equals("/") || link.equals("")){
                    if (path.equals("/")){
                        activeLink = text;
                        break;
                    }
                }
                else{
                    if (path.startsWith(link) || path.startsWith(link + "/")){
                        activeLink = text;
                        break;
                    }
                }
            }
        }

      //Create html fragment
        StringBuffer str = new StringBuffer();
        for (String[] item : items){
            String link = item[1].trim();
            String text = item[0].trim();

            String active = "";
            if (text.equalsIgnoreCase(activeLink)) active = " active";

            str.append("<li class=\"unremovable" + active + "\">");
            str.append("<a href=\"" + link + "\"><b>" + text.toUpperCase() + "</b></a>");
            str.append("</li>");
        }

        return str.toString();
    }


  //**************************************************************************
  //** getRedirect
  //**************************************************************************
    private String getRedirect(HttpServletRequest request){
        if (redirects==null) redirects = new javaxt.portal.Redirects(share);
        return redirects.getRedirect(request);
    }



    public static class JavaDoc{
        private String title;
        private String text;
        public JavaDoc(String title, String text){
            this.title = title;
            this.text = text;
        }
    }
}