package javaxt.portal;
import java.util.zip.*;
import javaxt.http.servlet.*;

//******************************************************************************
//**  Documentation Class
//******************************************************************************
/**
 *   Used to convert a JEL Doclet into HTML fragments
 *
 ******************************************************************************/

public class Documentation {

    private HttpServletRequest request;
    private String jar;
    private com.jeldoclet.Parser parser;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate the JELParser for a given jar file. The jar file is
   *  actually a zip file found in the input directory. This is the expected
   *  pattern:
   <pre>
      <dir> + <jar> + "/" + <jar> + "*.zip"
   </pre>
   *  Example:
   <pre>
      "/share/downloads/javaxt-core/javaxt-core_v1.3.0.zip"
   </pre>
   *  If there are multiple zip files in the directory, this method will assume
   *  that the last file is the most current. This assumption is based on a
   *  common convention of including a revision or version number appended to
   *  the jar name (e.g. "javaxt-core_v1.3.0.zip").
   *  <br/>
   *  Once a zip file is identified, an attempt is made to find a corresponding
   *  JELDoclet xml file. If the xml file is not found, an attempt is made to
   *  extract the xml file from the zip file. Another assumption is made here,
   *  specifically that the xml file found in the "/docs" subdirectory contains
   *  the JELDoclet xml file.
   *  <br/>
   *  Once the the JELDoclet xml is found, the method will invoke the JELParser.
   *
   *  @param jar Name of the jar file (e.g. "javaxt-core.jar")
   *  @param dir Path to the jar directory
   *  @param request HttpServletRequest
   */
    public Documentation(String jar, javaxt.io.Directory dir, HttpServletRequest request) {

        if (jar.toLowerCase().endsWith(".jar") || jar.toLowerCase().endsWith(".zip")){
            jar = jar.substring(0, jar.lastIndexOf("."));
        }
        this.jar = jar;
        this.request = request;
        this.parser = getJELParser(getJavaDoc(dir));

    }


  //**************************************************************************
  //** getIndex
  //**************************************************************************
  /** Used to construct an html fragment with a index of all the classes found
   *  in the jar file.
   */
    public String getIndex(String title) throws Exception {

        StringBuffer str = new StringBuffer();
        boolean showIndex = true;
        if (request.getQueryString()!=null){
            if (request.getParameter("jar")!=null &&
                request.getParameter("package")!=null &&
                request.getParameter("class")!=null){
                showIndex=false;
                return getClassInfo();
            }
        }
        if (showIndex){

            String path = request.getURL().getPath();

            if (title==null) str.append("<h1>JavaDocs</h1>");
            else str.append("<h1>" + title + "</h1>");


            for (com.jeldoclet.Package p : parser.getPackages()){
                
                String packageName = p.getName();
                if (!packageName.startsWith("javaxt.")) continue;
                
                
                str.append("<div class=\"packageName\">" + packageName + "</div>");

                str.append("<table>");
                com.jeldoclet.Class[] classes = p.getClasses();
                for (int j=0; j<classes.length; j++){
                    com.jeldoclet.Class Class = classes[j];
                    String className = Class.getName();

                    if (!Class.isNested()){
                        str.append("<tr>");


                        str.append("<td valign=\"top\">");
                        str.append("<a href=\"" + path + "?jar=" + jar + "&package=" + packageName + "&class=" + className + "\">"+className+"</a>");
                        str.append("</td>");
                        str.append("<td>");

                        String description = Class.getDescription();
                        if (description==null) description="";
                        if (description.contains(".")) description = description.substring(0, description.indexOf("."));
                        str.append("<div style=\"padding-left:20px;\" class=\"smgrytxt\">" + description + "</div>");

                        str.append("</td>");
                        str.append("</tr>");

                    }
                }
                str.append("</table>");
            }
        }

        return str.toString();

    }



  //**************************************************************************
  //** getClass
  //**************************************************************************
  /**  Used to generate html documentation for a given class. Expects three
   *   querystring parameters in the requested URL:
   *   <ul>
   *   <li> jar: The name of the jar file (e.g. javaxt-core.jar) </li>
   *   <li> class: The name of the class (e.g. File) </li>
   *   <li> package: The name of the package (e.g. javaxt.io) </li>
   *   <ul>
   */
    public String getClassInfo() throws Exception {

      //Parse QueryString Parameters
        String jarFile = request.getParameter("jar");
        if (!jarFile.toLowerCase().endsWith(".jar")) jarFile+=".jar";
        String className = request.getParameter("class");
        String packageName = request.getParameter("package");

        return getClassInfo(className, packageName);
    }


  //**************************************************************************
  //** getClass
  //**************************************************************************
  /** Used to generate html documentation for a given class.
   *  @param className The name of the class (e.g. File)
   *  @param packageName The name of the package (e.g. javaxt.io)
   */
    public String getClassInfo(String className, String packageName) throws Exception{

      //Get class info

        com.jeldoclet.Class theClass = parser.getPackage(packageName).getClass(className);
        com.jeldoclet.Method[] methods = theClass.getMethods();
        com.jeldoclet.Method[] constructors = theClass.getConstructors();

        className = theClass.getName();
        packageName = theClass.getPackageName();

      //Get Description
        String classDescription = theClass.getDescription();
        if (classDescription==null  || classDescription.trim().equalsIgnoreCase("Enter class description here"))
            classDescription = "No description available.";

      //Print Class Name & Description
        StringBuffer str = new StringBuffer();
        str.append("<h1>" + className + " Class</h1>");
        str.append(classDescription);

      //Update Title (via javascript)
        str.append("<script type=\"text/javascript\">");
        str.append("document.title = '" + className + " Class - " + packageName + "." + className + "';"); //
        str.append("</script>");



      //Print Constructors
        str.append("<h2>Constructors</h2>");
        if (constructors.length<1){
            str.append("There are no constructors. You can call the methods directly.");
        }
        for (int i=0; i<constructors.length; i++){
            com.jeldoclet.Method method = constructors[i];
            com.jeldoclet.Parameter[] parameters = method.getParameters();


            str.append("<div>");
            str.append("<span class=\"methodVisibility\">"+method.getVisibility() + "</span> <span class=\"type\">" + method.getType() + "</span> <span class=\"constructorName\">" + method.getName()+"</span>( ");
            for (int j=0; j<parameters.length; j++){
                str.append("<span class=\"type\">" + parameters[j].getType() + "</span> <span class=\"parameterName\">" + parameters[j].getName()+"</span>");
                if (j<parameters.length-1) str.append(", ");
            }
            str.append(" )");
            str.append("</div>");

        }



      //Print methods
        str.append("<h2>Methods</h2>");
        for (int i=0; i<methods.length; i++){
            com.jeldoclet.Method method = methods[i];
            if (method.isPublic() && !method.isDeprecated()){
                com.jeldoclet.Parameter[] parameters = method.getParameters();

                String description = method.getDescription();
                if (description==null) description = "";

                boolean showParams = false;

                str.append("<div>");
                //str.append("<span class=\"methodVisibility\">"+method.getVisibility() + "</span> <span class=\"type\">" + method.getType() + "</span> <span class=\"methodName\">" + method.getName()+"</span>( ");
                str.append("<span class=\"methodName\">" + method.getName()+"</span>( ");
                for (int j=0; j<parameters.length; j++){
                    str.append("<span class=\"type\">" + parameters[j].getType() + "</span> <span class=\"parameterName\">" + parameters[j].getName()+"</span>");
                    if (j<parameters.length-1) str.append(", ");

                    if (parameters[j].getDescription()!=null){
                        if (parameters[j].getDescription().length()>0)showParams = true;
                    }
                }
                str.append(" )");
                str.append(" returns <span class=\"type\">" + method.getType() + "</span>");
                str.append("</div>");


              //Print description and parameter info
                str.append("<div style=\"padding-top:4px;padding-left:20px;padding-bottom:15px;color:#676767;\">");
                str.append(description);
                if (showParams){

                    //str.append("Parameters:");
                    str.append("<table style=\"margin-top:7px;\">");
                    for (int j=0; j<parameters.length; j++){
                        str.append("<tr>");
                        str.append("<td valign=\"top\" class=\"type\">" + parameters[j].getName() + "</td>");

                        String paramDesc = parameters[j].getDescription();
                        if (paramDesc.contains("<pre>")){
                            paramDesc = paramDesc.replace("<pre>",
                                    "</td></tr>" +
                                    "<tr><td colspan=\"2\" valign=\"top\" class=\"parameterDesc\"><pre>");
                        }

                        str.append("<td valign=\"top\" class=\"parameterDesc\" style=\"padding-left:15px;\">" + paramDesc + "</td>");
                        str.append("</tr>");
                    }
                    str.append("</table>");
                }
                str.append("</div>");
            }
        }

        return str.toString();
    }



  //**************************************************************************
  //** getTree
  //**************************************************************************
  /** Used to return a javascript array used to populate a tree control. See
   *  the javaxt.dhtml.control.Tree javascript for more details.
   */
    public String getTree(){

        StringBuffer str = new StringBuffer();
        str.append("[");
        str.append("['" + jar + ".jar', '#',");


        for (com.jeldoclet.Package p : parser.getPackages()){

            String packageName = p.getName();
            if (!packageName.startsWith("javaxt.")) continue;
            
            str.append("['" + packageName + "', '#', ");

            com.jeldoclet.Class[] classes = p.getClasses();
            for (int j=0; j<classes.length; j++){
                com.jeldoclet.Class myClass = classes[j];

                if (!myClass.isNested()){

                    str.append("['" + myClass.getName() + "', '#', ");
                    /*
                    com.jeldoclet.Method[] methods = myClass.getMethods();
                    for (int k=0; k<methods.length; k++){
                        com.jeldoclet.Method method = methods[k];
                        str.append("\t['" + method.getName() + "', '#'], ");
                    }
                    */
                    str.append("], ");
                }

            }


            str.append("], ");
        }

        str.append("]];");

        String arr = str.toString().trim();
        if (arr.endsWith(",")) arr = arr.substring(0,arr.length()-1).trim();

        return arr;

    }


  //**************************************************************************
  //** mergeTrees
  //**************************************************************************
  /** Static method used to combine two or more trees into one large array.
   */
    public static String mergeTrees(String[] trees){

        StringBuffer str = new StringBuffer();
        str.append("[");
        for (int i=0; i<trees.length; i++){
            String tree = trees[i];
            tree = tree.trim();
            if (tree.endsWith(";")) tree = tree.substring(0, tree.length()-1).trim();
            if (tree.startsWith("[[") && tree.endsWith("]]")){
                tree = tree.substring(1, tree.length()-1);
                str.append(tree);
                if (i<trees.length-1) str.append(",");
            }
        }
        str.append("];");
        return str.toString();

    }

    /*
    public static String mergeTrees(java.util.List<String> trees){
        String[] arr = new String[trees.size()];
        for (int i=0; i<arr.length; i++){
            arr[i] = trees.get(i);
        }
        return mergeTrees(arr);
    }
    */


  //**************************************************************************
  //** getJELParser
  //**************************************************************************
  /** Used to return a JELParser for a given xml file. The JELParser is stored
   *  in server memory using an application variable.
   */
    private com.jeldoclet.Parser getJELParser(javaxt.io.File javadoc){

        ServletContext application = request.getSession().getServletContext();
        com.jeldoclet.Parser parser = (com.jeldoclet.Parser) application.getAttribute(jar);
        java.util.Date lastUpdate = (java.util.Date) application.getAttribute(jar + "-timestamp");
        if (parser==null || lastUpdate==null || javadoc.getDate().after(lastUpdate)){
            parser = new com.jeldoclet.Parser(javadoc);
            application.setAttribute(jar, parser);
            application.setAttribute(jar + "-timestamp", javadoc.getDate());
        }
        return parser;
    }


  //**************************************************************************
  //** getJavaDoc
  //**************************************************************************
  /** Used to return a JelDoclet XML file found in the input directory.
   *  Extracts xml files from zip files as needed. See constructor for more
   *  information.
   */
    private javaxt.io.File getJavaDoc(javaxt.io.Directory dir){

      //Find the javadoc associated with the jar file
        javaxt.io.File lastFile = File.getLastFile(jar, dir);
        if (lastFile==null) return null;
        javaxt.io.File javadoc = new javaxt.io.File(dir, lastFile.getName(false) + ".xml");
        //System.out.println(javadoc);


      //If the javadoc doesn't exist, extract it from the zip file associated with latest release
        if (!javadoc.exists()){
            javaxt.io.File zipFile = lastFile;
            try {
                byte[] buf = new byte[1024];
                ZipInputStream zipinputstream = new ZipInputStream(zipFile.getInputStream());
                while (true) {
                    ZipEntry zipentry = zipinputstream.getNextEntry();
                    if (zipentry==null) break;

                    String entryName = zipentry.getName();
                    if (entryName.startsWith("doc/") && entryName.endsWith(".xml")){

                      //Delete old xml files found in this directory
//                        for (javaxt.io.File file : dir.getFiles(jar + "*.zip")){
//                            file = new javaxt.io.File(file.getParentDirectory(), file.getName(false) + ".xml");
//                            file.delete();
//                        }


                      //Extract the xml file
                        int n;
                        java.io.FileOutputStream fileoutputstream = javadoc.getOutputStream();
                        while ((n = zipinputstream.read(buf, 0, 1024)) > -1){
                            fileoutputstream.write(buf, 0, n);
                        }

                        fileoutputstream.close();
                        zipinputstream.closeEntry();
                        break;
                    }
                }
                zipinputstream.close();
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }

        if (javadoc.exists()){
            return javadoc;
        }
        else{
            return null;
        }
    }

}