package javaxt.portal;
import javaxt.http.servlet.HttpServletRequest;

//******************************************************************************
//**  Navbar Class
//******************************************************************************
/**
 *   Enter class description here
 *
 ******************************************************************************/

public class Navbar {


    private javaxt.io.File file;
    private String Path;

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of Navbar. 
   *  @param service First directory represented in the request url.
   */

    public Navbar(HttpServletRequest request, String service, javaxt.io.Directory share, String Path) {

        
      //Set Path variable
        this.Path = Path;

        file = new javaxt.io.File(share.toString() + service + "/navbar.txt");
        if (!file.exists()) file = new javaxt.io.File(share.toString() + "navbar.txt");

    }



    

    public javaxt.io.File getFile(){
        return file;
    }

    public java.util.ArrayList<String[]> getItems(){
        java.util.ArrayList<String[]> list = new java.util.ArrayList<String[]>();
        for (String row : file.getText().split("\n")){
            row = row.trim();
            if (row.length()>0 && row.contains("\t") &&
                    !(row.startsWith("<!--") || row.startsWith("#") || row.startsWith("//"))){
                String text = row.substring(0, row.indexOf("\t")).trim();
                String link = row.substring(row.indexOf("\t")).trim().replace("<%=Path%>", Path);
                list.add(new String[]{text, link});
            }
        }
        return list;
    }

    public String toString(){
        
        StringBuffer str = new StringBuffer();
        java.util.Iterator<String[]> it = getItems().iterator();
        while (it.hasNext()){
            String[] item = it.next();
            String text = item[0];
            String link = item[1];
            str.append("<a href=\"" + link + "\">" + text + "</a>");
            if (it.hasNext()) str.append(" | ");
        }
        return str.toString();
    }

}