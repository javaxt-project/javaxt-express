package javaxt.portal;
import javaxt.http.servlet.HttpServletRequest;

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
    private String title;
    private String description;
    private String keywords;
    private String content = "";
    private String Path;
    private final static String[] DefaultFileNames =
        new String[]{"home.txt", "index.txt", "Overview.txt"};
    private boolean parseFile = true;

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class.
   *  @param share Path to the web share (root directory)
   *  @param request HttpServletRequest
   */
    public Content(HttpServletRequest request, javaxt.io.Directory share, String Path){


        this.share = share;
        this.Path = Path;


        
      //Get path to wiki file
        String path = share.toString();
        String relPath = request.getURL().getPath();
        if (relPath.startsWith("/")) relPath = relPath.substring(1);
        if (relPath.endsWith("/")) relPath = relPath.substring(0, relPath.length()-1);



        if (relPath.equals("")){
            for (String fileName : DefaultFileNames){
                javaxt.io.File f = new javaxt.io.File(path + fileName);
                if (f.exists()){
                    file = f;
                    break;
                }
            }
        }
        else{
            file = new javaxt.io.File(path+relPath+".txt");
            if (!file.exists()){
                javaxt.io.Directory dir = new javaxt.io.Directory(path+relPath);
                if (dir.exists()){
                    for (String fileName : DefaultFileNames){
                        javaxt.io.File f = new javaxt.io.File(dir, fileName);
                        if (f.exists()){
                            file = f;
                            break;
                        }
                    }
                }
            }
        }

    }


  //**************************************************************************
  //** parseFile
  //**************************************************************************
  /** Used to parse the file contents. Note that the file is parsed only once.
   *  Parsing the file should be delayed until the contents are actually
   *  needed. For example, we don't want to parse the file if all we want is
   *  the date/time stamp of the file to return a 304 response.
   */
    private void parseFile(){

        if (!parseFile) return;

      //Parse file and extract content
        if (file.exists()) {

            content = file.getText().replace("<%=Path%>", Path);
            javaxt.html.Parser html = new javaxt.html.Parser(content);

          //Extract Title
            try{
                javaxt.html.Element title = html.getElementByTagName("title");
                content = content.replace(title.getOuterHTML(), "");
                this.title = title.getInnerHTML();
            }
            catch(Exception e){}

            if (title==null){
                try{
                    this.title = html.getElementByTagName("h1").getInnerHTML();
                }
                catch(Exception e){}
            }

          //Extract Description
            try{
                javaxt.html.Element description = html.getElementByTagName("description");
                content = content.replace(description.getOuterHTML(), "");
                this.description = description.getInnerHTML();
            }
            catch(Exception e){}


          //Extract Keywords
            try{
                javaxt.html.Element keywords = html.getElementByTagName("keywords");
                content = content.replace(keywords.getOuterHTML(), "");
                this.keywords = keywords.getInnerHTML();
            }
            catch(Exception e){}

        }

        parseFile = false;

    }


  //**************************************************************************
  //** write
  //**************************************************************************
  /** Used to write new content to the file.
   */
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


  //**************************************************************************
  //** getTitle
  //**************************************************************************
  /** Returns the content of the html "title" tag.
   */
    public String getTitle(){
        parseFile();
        return title;
    }


  //**************************************************************************
  //** getDescription
  //**************************************************************************
  /** Returns the content of the html "description" tag.
   */
    public String getDescription(){
        parseFile();
        return description;
    }


  //**************************************************************************
  //** getKeywords
  //**************************************************************************
  /** Returns the content of the html "keywords" tag.
   */
    public String getKeywords(){
        parseFile();
        return keywords;
    }


  //**************************************************************************
  //** getIndex
  //**************************************************************************
  /**  Returns a table of contents
   */
    public String getIndex(javaxt.io.Directory share, String Path){

        java.util.ArrayList<java.util.Date> dates = new java.util.ArrayList<java.util.Date>();
        dates.add(file.getDate());


        java.util.ArrayList<String> dirNames = new java.util.ArrayList<String>();

        StringBuffer toc = new StringBuffer();
        toc.append("<ul>\r\n");

        javaxt.io.File[] files = share.getFiles("*.txt", true);
        for (int i=0; i<files.length; i++){
            if (!files[i].equals(file)){

                dates.add(files[i].getDate());

                String relPath = files[i].getPath().replace(share.toString(),"") + files[i].getName(false);
                relPath = relPath.replace("\\","/");


                javaxt.io.Directory currDir = files[i].getParentDirectory();
                if (i==0 || !files[i-1].getParentDirectory().equals(currDir)){

                    dates.add(currDir.getDate());

                    String dirName = files[i].getPath().replace(share.toString(),"").replace("\\", "/").replace("_", " ");
                    if (dirName.endsWith("/")) dirName = dirName.substring(0, dirName.length()-1);
                    if (!dirNames.contains(dirName)) dirNames.add(dirName);


                  //Stylize the dirName
                    if (!dirName.contains("/")) dirName = "<h2>" + dirName + "</h2>";
                    else{

                        String h2 = "";
                        if (dirNames.size()>1){
                            String str = dirName.substring(0, dirName.indexOf("/"));
                            String prevDir = dirNames.get(dirNames.size()-2);
                            if (!prevDir.startsWith(str)){
                                h2 = "<h2>" + str + "</h2>";
                            }
                            
                        }

                        if (dirName.contains("/")) dirName = dirName.substring(dirName.indexOf("/")+1);


                        dirName = h2 + "<li>" + dirName + "</li>";
                    }

                    if (i>0) toc.append("</ul>\r\n");
                    toc.append(dirName + "\r\n");
                    toc.append("<ul>\r\n");
                }

                if (!currDir.equals(share)){
                    toc.append("<li><a href=\"" + Path + relPath + "\">" + files[i].getName(false).replace("_", " ") + "</a></li>\r\n");
                }

            }
        }
        toc.append("</ul>\r\n");


      //Update the date of the file to match the
        javaxt.utils.Date.sortDates(dates);
        java.util.Date mostRecentFile = dates.get(dates.size()-1);
        if (!mostRecentFile.equals(file.getDate())) System.out.println("Update file date: " + mostRecentFile);
        file.setDate(mostRecentFile);


        return toc.toString();
    }


    public String getIndex(){
        return this.getIndex(share, Path);
    }


  //**************************************************************************
  //** toString
  //**************************************************************************
  /** Used to return html content for this wiki file/entry.
   */
    public String toString(){


      //Set Default Content
        if (file==null || !file.exists()){
            title = "Page Not Found";
            content = "<h1>" + title + "</h1>";
        }


      //Parse Content
        parseFile();
        javaxt.html.Parser html = new javaxt.html.Parser(content);
        javaxt.html.Element h1 = html.getElementByTagName("h1");
        if (h1!=null) content = content.replace(h1.getOuterHTML(), "");


        StringBuffer text = new StringBuffer();
        text.append("<div class=\"content\">");
        text.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#FFFFFF;border-collapse:collapse;margin-left:0px;margin-right: 0px;\" bordercolor=\"#111111\" width=\"100%\" align=\"center\" height=\"100%\">");

        text.append("<tr>");
        text.append("<td valign=\"top\">");        

        if (h1!=null) text.append(h1.getOuterHTML());
        

        text.append(content);




        text.append("</td>");
        text.append("</tr>");



        text.append("</table>");
        text.append("</div>");


        return text.toString();
    }

    

}