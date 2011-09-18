package javaxt.portal;

//******************************************************************************
//**  Redirects Class
//******************************************************************************
/**
 *   Used to parse the "redirects.txt" file found in the wiki directory and
 *   updates requested URLs.
 *
 ******************************************************************************/

public class Redirects {

    private java.util.HashMap<String, String> redirects = new java.util.HashMap<String, String>();
    private javaxt.io.File file;
    private long lastUpdate;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class. */

    public Redirects(javaxt.io.Directory share) {
        file = new javaxt.io.File(share.toString() + "wiki/redirects.txt");
        parseRedirects();
    }


  //**************************************************************************
  //** parseRedirects
  //**************************************************************************
  /** Used to parse the "redirects.txt" file and updates the redirects hashmap.
   *  Also updates the lastUpdate timestamp used in the getRedirect() method.
   */
    private void parseRedirects(){
        redirects.clear();
        lastUpdate = file.getDate().getTime();
        for (String entry : file.getText().split("\r\n")){
            entry = entry.trim();
            if (entry.length()==0 || entry.startsWith("#") || entry.startsWith("//")){
                //skip line
            }
            else{
                while(entry.contains("\t\t")) entry = entry.replace("\t\t", "\t");
                String[] arr = entry.split("\t");
                if (arr.length>1){
                    redirects.put(arr[0], arr[1]);
                }
            }
        }
    }

    public String getRedirect(javax.servlet.http.HttpServletRequest request){
        javaxt.utils.URL url = new javaxt.utils.URL(request.getRequestURL().toString());
        url.setQueryString(request.getQueryString());
        return getRedirect(url.toString());
    }

    public String getRedirect(javaxt.utils.URL url){
        return getRedirect(url.toString());
    }


  //**************************************************************************
  //** getRedirect
  //**************************************************************************
  /** Returns a redirected/updated url. Returns null if there is no redirect.
   */
    public String getRedirect(String url){

      //Check timestamp of the redirect file. Reparse as needed.
        if (file.getDate().getTime()!=lastUpdate) parseRedirects();


      //Loop through all the redirects and find a suitable match
        java.util.Iterator<String> it = redirects.keySet().iterator();
        while (it.hasNext()){
            String key = it.next();
            if (url.toUpperCase().contains(key.toUpperCase())){
                String replacement = redirects.get(key);
                int x = url.toUpperCase().indexOf(key.toUpperCase());
                int y = key.length();

                String a = url.substring(0, x);
                String b = url.substring(x+y);
                return a + replacement + b;
            }
        }


      //If not match is found, return a null
        return null;
    }

}