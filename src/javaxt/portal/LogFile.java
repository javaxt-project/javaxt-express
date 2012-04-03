package javaxt.portal;

//******************************************************************************
//**  LogFile Class
//******************************************************************************
/**
 *   Used to parse a log file generated by the akula proxy server.
 *
 ******************************************************************************/

public class LogFile {


    private javaxt.io.File log;

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of LogFile. */

    public LogFile(javaxt.io.File log) {
        this.log = log;
    }
    

    public Integer getID(){
        return javaxt.utils.string.toInt(log.getName(false));
    }


  //**************************************************************************
  //** getDownloads
  //**************************************************************************
  /** Returns the total number of downloads for a given url. 
   *
   *  @param url A string representing a url or url fragment. Returns download
   *  statistics for any requests that contain the input url.
   *
   *  @return
   */

    public java.util.HashMap<String, Integer> getDownloads(String[] urls){

        String[] requests = log.getText().split("\r\n\r\n");

        int x = 0;
        int mac = 0;
        int win = 0;
        int linux = 0;
        int bot = 0;
        int spadac = 0;


        
        for (int i=0; i<requests.length; i++){
            if (requests[i].startsWith("New Request From")){

                for (String url : urls){

                    if (requests[i].toLowerCase().contains(url)){

                        x++;

                        try{
                            //javaxt.utils.Date date = new javaxt.utils.Date(requests[i].substring(requests[i].indexOf("TimeStamp:")+11).trim());
                            //int month = date.getMonth();
                            //Integer count = months.get(month);
                            //if (count==null) count = 1;
                            //else count++;
                            //months.put(month, count);
                        }
                        catch(Exception e){
                            e.printStackTrace();
                        }




                        String HTTP_USER_AGENT = null;

                        for (String row : requests[i+1].split("\r\n")){

                            if (row.contains(":")){
                                String key = row.substring(0, row.indexOf(":")).trim();
                                String value = row.substring(row.indexOf(":")+1).trim();


                                if (key.equalsIgnoreCase("User-Agent")){
                                    HTTP_USER_AGENT = value;
                                    if (value.contains("spider") || value.contains("slurp")){
                                        //printHeader = false;
                                    }
                                }
                                else if(key.equalsIgnoreCase("VIA") && value.contains("SPAT")){
                                    spadac++;
                                }

                                //System.out.println(key + " " + value);
                            }
                        }

                        //if (printHeader){

                            //System.out.println(requests[i+1]);

                            if (HTTP_USER_AGENT!=null){
                                nl.bitwalker.useragentutils.UserAgent client = new nl.bitwalker.useragentutils.UserAgent(HTTP_USER_AGENT);
                                if (client.getBrowser().getBrowserType().equals(nl.bitwalker.useragentutils.BrowserType.WEB_BROWSER)){
                                    //System.out.println("\r\n");
                                    //System.out.println("Browser: " + client.getBrowser().getName() + " " + " (" + client.getBrowser().getBrowserType() + ")");
                                    //System.out.println ("OS: " + client.getOperatingSystem().getName());

                                    String os = client.getOperatingSystem().getName();
                                    if (os.startsWith("Windows")){
                                        win++;
                                    }
                                    else if (os.startsWith("Mac")){
                                        mac++;
                                    }
                                    else if (os.startsWith("Linux")){
                                        linux++;
                                        //System.out.println(HTTP_USER_AGENT);
                                    }

                                }
                                else{
                                    bot++;
                                }
                            }
                            else{
                                //System.out.println(requests[i+1]);
                            }

                        //}



                        //System.out.println("\r\n" + requests[i+1]);

                    }

                }

            }
        }



        java.util.HashMap<String, Integer> downloads = new java.util.HashMap<String, Integer>();
        downloads.put("total", x);
        downloads.put("win", win);
        downloads.put("mac", mac);
        downloads.put("linux", linux);
        downloads.put("bot", bot);
        downloads.put("spadac", spadac);

        return downloads;

    }


}