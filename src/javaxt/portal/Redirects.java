package javaxt.portal;
import javaxt.http.servlet.HttpServletRequest;

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
    private java.util.List<String> sortedKeys;
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

        sortedKeys = new java.util.ArrayList<String>(redirects.keySet());
        java.util.Collections.sort(sortedKeys, new StringComparator());
    }

    public String getRedirect(HttpServletRequest request){
        return getRedirect(request.getURL());
    }

    public String getRedirect(javaxt.utils.URL url){
        return getRedirect(url.toString());
    }

    public String getRedirect(java.net.URL url){
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
        java.util.Iterator<String> it = sortedKeys.iterator();
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



  /** Used to compare to strings based on their length. Longer strings will
   *  appear first.
   */
    private class StringComparator implements java.util.Comparator<String> {
        public int compare(String t1, String t2) {
            return new Integer(t2.length()).compareTo(new Integer(t1.length()));
        }
    }

}