package javaxt.portal;

//******************************************************************************
//**  Portal Content
//******************************************************************************
/**
 *   Used to dynamically create HTML content from text files. 
 *
 ******************************************************************************/

public class Content {

    private javaxt.io.File file;
    private javaxt.io.Directory share;
    private javax.servlet.http.HttpServletRequest request;
    //private boolean isIndex = false;
    private String title;
    private String description;
    private String keywords;
    private String content = "";
    private String Path;

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class.
   *  @param share Path to the javaxt share directory
   *  @param request HttpServletRequest
   */
    public Content(javax.servlet.http.HttpServletRequest request, javaxt.io.Directory share){


        this.request = request;
        this.share = share;

      //Set Path variable
        Path = Utils.getPath(request);



      //Get path to wiki file
        String path = share + "wiki/";
        if (request.getServletPath().startsWith("/wiki/")){
            if (request.getQueryString()==null){
                path += "index.txt";
            }
            else{
                java.util.Enumeration params = request.getParameterNames();
                String relPath = (String) params.nextElement();
                if (relPath.startsWith("/") || relPath.startsWith("\\")){
                    relPath = relPath.substring(1);
                }
                if (relPath.endsWith("/") || relPath.endsWith("\\")){
                    relPath = relPath.substring(0, relPath.length()-1);
                }
                if (!relPath.endsWith(".txt")) relPath+=".txt";
                path += relPath;
            }
        }
        else{
            
            //String relPath = request.getServletPath().substring(1);
            String relPath = request.getServletPath();
            if (relPath.length()>1) relPath = relPath.substring(1);
            else{

              //Special case where the entire site is hosted from a servlet with a pattern of "/*"
                String service = new javaxt.utils.URL(request.getRequestURL().toString()).getPath();
                if (service.length()<Path.length()) service = Path;
                service = service.substring(service.indexOf(Path)).substring(Path.length());
                if (service.length()>1 && service.startsWith("/")) service = service.substring(1);
                if (service.length()>1 && service.endsWith("/")) service = service.substring(0, service.length()-1);


              //Special case when app is deployed as root
                String contextPath = request.getContextPath();
                if (contextPath.length()>1){
                    contextPath = contextPath.substring(1);
                    if (Path.equals("/") && service.startsWith(contextPath)){
                        if (service.contains("/")){
                            service = service.substring(service.indexOf("/"));
                            if (service.startsWith("/")){
                                if (service.length()>1) service = service.substring(1);
                                else service = "";
                            }
                        }
                        else service = "";
                    }
                }

                relPath = service;
                
            }

            //System.out.println("relPath: " + relPath);
            if (relPath.toLowerCase().endsWith("index.jsp")){

                if (relPath.equalsIgnoreCase("index.jsp")){
                    path+="home.txt";
                }
                else{
                    relPath = relPath.substring(0, relPath.length()-10);
                    path+=relPath + ".txt";
                }
            }
            else{

              //Special case for servlets where there is no index.jsp in the file path
                if (relPath.equals("") || relPath.equals("/")){
                    path+="home.txt";
                }
                else{
                    path+=relPath + ".txt";
                    //path=null;
                }
            }
        }
        
        //System.out.println("path: " + path);

      //Parse file and extract content
        if (path!=null) {
            file = new javaxt.io.File(path);
            if (file.exists()){

                content = file.getText().replace("<%=Path%>", Path);
                javaxt.html.Parser html = new javaxt.html.Parser(content);

              //Extract Title
                try{
                    javaxt.html.Parser.Element title = html.getElementByTagName("title");
                    content = content.replace(title.outerHTML, "");
                    this.title = title.innerHTML;
                }
                catch(Exception e){}
                
                if (title==null){
                    try{
                        this.title = html.getElementByTagName("h1").innerHTML;
                    }
                    catch(Exception e){}
                }

              //Extract Description
                try{
                    javaxt.html.Parser.Element description = html.getElementByTagName("description");
                    content = content.replace(description.outerHTML, "");
                    this.description = description.innerHTML;
                }
                catch(Exception e){}


              //Extract Keywords
                try{
                    javaxt.html.Parser.Element keywords = html.getElementByTagName("keywords");
                    content = content.replace(keywords.outerHTML, "");
                    this.keywords = keywords.innerHTML;
                }
                catch(Exception e){}
                
            }

            if (file.getName().equalsIgnoreCase("img.txt")){
                System.out.println("path: " + path);
                System.out.println("query: " + request.getQueryString());
                return;
            }
        }
        else{
            return;
        }


      //Create/Update wiki content
        /*
        try{
            String newContent = request.getParameterValues("content")[0];
            if (newContent!=null){
                
                javaxt.utils.URL currURL = new javaxt.utils.URL(request.getRequestURL().toString());
                javaxt.utils.URL prevURL = new javaxt.utils.URL(request.getHeader("Referer"));
                
                if (currURL.getHost().equalsIgnoreCase(prevURL.getHost())){
                    if (prevURL.getPath().endsWith("edit.jsp")){
                        javaxt.portal.User user = (javaxt.portal.User) request.getSession().getAttribute("PortalUser");
                        if (user!=null){ 
                            write(newContent);
                        }
                    }
                }
            }
        }
        catch(Exception e){
        }
        */
    }

    public void write(String text){
        if (file!=null)  file.write(text);
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Used to return the physical path to the file used to store this content.
   */
    public javaxt.io.File getFile(){
        return file;
    }




    public String getTitle(){
        return title;
    }
    
    public String getDescription(){
        return description;
    }
    public String getKeywords(){
        return keywords;
    }


  //**************************************************************************
  //** getIndex
  //**************************************************************************
  /** Returns a table of contents
   */
    public String getIndex(){

        StringBuffer toc = null;

        javaxt.io.Directory dir = new javaxt.io.Directory(share + "wiki/");
        javaxt.io.File[] files = dir.getFiles("*.txt", true);
        java.util.HashSet<String> dirs = new java.util.HashSet<String>();

        toc = new StringBuffer();
        toc.append("<ul>");
        for (int i=0; i<files.length; i++){
            if (!files[i].equals(file)){
                String relPath = files[i].getPath().replace(dir.toString(),"") + files[i].getName(false);
                relPath = relPath.replace("\\","/");
                if (relPath.contains("/")){

                    String currDir = relPath.substring(0, relPath.lastIndexOf("/"));
                    if (!dirs.contains(currDir)){

                        if (!dirs.isEmpty()) toc.append("</ul>");
                        toc.append("<li>" + currDir +"</li>");
                        toc.append("<ul>");

                        dirs.add(currDir);
                    }


                    toc.append("<li><a href=\"" + Path + relPath + "\">" + relPath +"</a></li>");
                }
            }
        }
        toc.append("</ul>");

        return toc.toString();
    }



  //**************************************************************************
  //** toString
  //**************************************************************************
  /** Used to return html content for this wiki file/entry.
   */
    public String toString(){


      //Get User
        User user = (User) request.getSession().getAttribute("PortalUser");





      //Set Default Content
        if (file==null || !file.exists()){
            if (user==null){
                content = "<h1>Page Not Found</h1>";
            }
        }


      //Parse Content
        javaxt.html.Parser html = new javaxt.html.Parser(content);
        javaxt.html.Parser.Element h1 = html.getElementByTagName("h1");
        if (h1!=null) content = content.replace(h1.outerHTML, "");


        StringBuffer text = new StringBuffer();
        text.append("<div class=\"content\">");
        text.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#FFFFFF;border-collapse:collapse;margin-left:0px;margin-right: 0px;\" bordercolor=\"#111111\" width=\"100%\" align=\"center\" height=\"100%\">");

        text.append("<tr>");
        text.append("<td valign=\"top\">");        

        if (h1!=null) text.append(h1.outerHTML);
        
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
        */


        /*
        if (link.length>0){
            text.append("      <div class=\"block block-one\">");
            text.append("        <div class=\"wrap1\">");
            text.append("         <div class=\"wrap2\">");
            text.append("          <div class=\"wrap3\">");
            text.append("            <div class=\"title\"><h3>Related Links</h3></div>");
            text.append("                <div class=\"inner\">");
            text.append("                  <ul class=\"sidebar-list\">");
            for (int i=0; i<link.length; i++){
                text.append("                    <li><a href=\"#\">" + link[i].innerHTML + "</a></li>");
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

        text.append(content);




        text.append("</td>");
        text.append("</tr>");


        
        if (user!=null){

            String relPath = this.getFile().toString().substring(share.toString().length());
            relPath = relPath.replace("\\", "/");

            text.append("<tr>");
            text.append("<td style=\"height:1px;\">");
            text.append("<table align=\"right\">");
            text.append("<tr>");
            text.append("<td class=\"smgrytxt\"><a href=\"" + Path + "wiki/edit.jsp?file=" + relPath + "\">Edit Page</a></td>");
            text.append("</tr>");
            text.append("</table>");
            text.append("</td>");
            text.append("</tr>");
        }

        text.append("</table>");
        text.append("</div>");


        return text.toString();
    }

    

}