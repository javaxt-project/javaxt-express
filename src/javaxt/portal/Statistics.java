package javaxt.portal;

//******************************************************************************
//**  Statistics Class
//******************************************************************************
/**
 *   Enter class description here
 *
 ******************************************************************************/

public class Statistics {


  /** Hashmap containing statistics from individual log files. The key
   *  represents the date of the log file. The corresponding value is itself a
   *  hashmap containing statistics.
   */
    private java.util.TreeMap<String, java.util.HashMap<String, Integer>> stats;
    private javaxt.io.Directory logDirectory;
    private String projectName;
    private javaxt.utils.Date lastUpdate;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of Statistics. */

    public Statistics(String projectName, javaxt.io.Directory logDirectory) {
        this.projectName = projectName;
        this.logDirectory = logDirectory;
        updateStats();
    }


  //**************************************************************************
  //** updateStats
  //**************************************************************************
  /** Used to update the stats hashmap. It's generally a good idea to update
   *  the stats periodically. 
   */
    private void updateStats(){

        javaxt.utils.Date currTime = new javaxt.utils.Date();
        String currID = currTime.toString("yyyyMMdd");
        if (lastUpdate!=null){
            if (currTime.compareTo(lastUpdate, "seconds")<60) return;
        }

        if (stats==null){
            stats = new java.util.TreeMap<String, java.util.HashMap<String, Integer>>();
        }


        for (javaxt.io.File file : logDirectory.getFiles("*.log")){
            LogFile logFile = new LogFile(file);
            String id = logFile.getID()+"";



            if (!stats.containsKey(id) || id==currID){
                stats.put(id, parseLogFile(logFile));
            }

        }

        lastUpdate = currTime;

    }


    private java.util.HashMap<String, Integer> parseLogFile(LogFile logFile){

      //Get downloads. Example: "download/?/javaxt-core/javaxt-core_v"
        String[] urls = new String[]{
            "download/?/" + projectName + "/" + projectName + "_v",
            "downloads/" + projectName + "/" + projectName + "_v"
        };
        java.util.HashMap<String, Integer> downloads = logFile.getDownloads(urls);

        return downloads;
    }


  //**************************************************************************
  //** getTotalDownloads
  //**************************************************************************
  /** Returns the total number of downloads for a given key.
   *  @param key Keys include "total", "win", "mac", "linux", "bot", "spadac"
   */
    public int getTotalDownloads(String key){

        updateStats();

        int x = 0;
        java.util.Iterator<String> it = stats.keySet().iterator();
        while (it.hasNext()){
            Integer i = stats.get(it.next()).get(key);
            if (i!=null) x = x+i;
        }
        return x;
    }


  //**************************************************************************
  //** getTotalDownloads
  //**************************************************************************
  /** Returns the total number of downloads, excluding bots.
   */
    public int getTotalDownloads(){
        return getTotalDownloads(false);
    }


  //**************************************************************************
  //** getTotalDownloads
  //**************************************************************************
  /** Returns the total number of downloads, including bots.
   */
    public int getTotalDownloads(boolean includeBots){
        if (includeBots) return getTotalDownloads("total");
        else return getTotalDownloads("total") - getTotalDownloads("bot");
    }


  //**************************************************************************
  //** getLastDownload
  //**************************************************************************
  /** Returns the last download date, excluding bots.
   */
    public javaxt.utils.Date getLastDownload(){


        updateStats();

        int lastDownload = 0;
        java.util.Iterator<String> it = stats.keySet().iterator();
        while (it.hasNext()){

            String date = it.next();
            java.util.HashMap<String, Integer> dailyStats = stats.get(date);
            int totalDownloads = dailyStats.get("total") - dailyStats.get("bot");
            if (totalDownloads>0) {
                int d = javaxt.utils.string.toInt(date);
                if (d>lastDownload) lastDownload = d;
            }
        }         

        if (lastDownload>0){
            try{
                return new javaxt.utils.Date(lastDownload + "");
            }
            catch(Exception e){
            }
        }
        return null;
    }





    public String getDownloadDetails(){

        //int x = 0;
        int mac = 0;
        int win = 0;
        int linux = 0;
        int bot = 0;
        int spadac = 0;
        int other = 0;

        StringBuffer str = new StringBuffer();
        str.append("-----------------------------------------------------------------------\r\n");
        str.append("Date    \tWin\tLinux\tMac\tOther\t|\tBot\tSpadac\r\n");
        str.append("-----------------------------------------------------------------------\r\n");


        java.util.Iterator<String> it = stats.keySet().iterator();
        while (it.hasNext()){

            String date = it.next();
            java.util.HashMap<String, Integer> dailyStats = stats.get(date);



            int total = dailyStats.get("total") - dailyStats.get("bot");
            int o = (total-(dailyStats.get("win")+dailyStats.get("linux")+dailyStats.get("mac")));


            if (total>0) {

                str.append(date);
                str.append("\t");
                //str.append(total);
                //str.append("\t");
                if (dailyStats.get("win")>0) str.append(dailyStats.get("win"));
                str.append("\t");
                if (dailyStats.get("linux")>0) str.append(dailyStats.get("linux"));
                str.append("\t");
                if (dailyStats.get("mac")>0) str.append(dailyStats.get("mac"));
                str.append("\t");
                if (o>0) str.append(o);
                str.append("\t|\t");
                if (dailyStats.get("bot")>0) str.append(dailyStats.get("bot"));
                str.append("\t");
                if (dailyStats.get("spadac")>0) str.append(dailyStats.get("spadac"));
                str.append("\r\n");

                //x+=total;
                mac += dailyStats.get("mac");
                win += dailyStats.get("win");
                bot += dailyStats.get("bot");
                linux += dailyStats.get("linux");
                spadac += dailyStats.get("spadac");
                other += o;
            }
        }
        str.append("-----------------------------------------------------------------------\r\n");
        str.append("        \t"); //date footer
        //str.append(x);
        //str.append("\t");
        str.append(win);
        str.append("\t");
        str.append(linux);
        str.append("\t");
        str.append(mac);
        str.append("\t");
        str.append(other);
        str.append("\t|\t");
        str.append(bot);
        str.append("\t");
        str.append(spadac);
        str.append("\r\n");
        return str.toString();
    }


    
  //**************************************************************************
  //** toString
  //**************************************************************************

    public String toString(){

        int x = getTotalDownloads("total");
        int win = getTotalDownloads("win");
        int mac = getTotalDownloads("mac");
        int linux = getTotalDownloads("linux");
        int bot = getTotalDownloads("bot");
        int spadac = getTotalDownloads("spadac");


        StringBuffer str = new StringBuffer();
        str.append((x-bot) + " Downloads! Excludes " + bot + " bot downloads.\r\n");
        str.append("Windows: " + win + "\r\n");
        str.append("Linux: " + linux + "\r\n");
        str.append("Mac: " + mac + "\r\n");
        str.append("Other: " + (x-(win+linux+mac+bot)) + "\r\n");
        str.append("SPADAC: " + spadac);

        return str.toString();
    }

    


}