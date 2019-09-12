package javaxt.express;

//******************************************************************************
//**  LogFile Class
//******************************************************************************
/**
 *   Used to parse a log file of web requests.
 *   See Logger.log(HttpServletRequest)
 *
 ******************************************************************************/

public class WebLog {

    private javaxt.io.File log;
    private Integer id;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public WebLog(javaxt.io.File log) {
        this.log = log;
        try{
            id = Integer.parseInt(log.getName(false));
        }
        catch(Exception e){
        }
    }


  //**************************************************************************
  //** getID
  //**************************************************************************
    public Integer getID(){
        return id;
    }


  //**************************************************************************
  //** toString
  //**************************************************************************
  /** Returns the contents of the log file as a string.
   */
    public String toString(){
        return log.toString();
    }


  //**************************************************************************
  //** Entry Class
  //**************************************************************************
  /** Used to represent and individual entry in a log file.
   */
    public class Entry {
        private String ip;
        private String host;
        private String url;
        private String path;
        private String protocol;
        private javaxt.utils.Date date;
        private String[] header;
        private String tld;
        private String domainName;
        private boolean skip = false;

        public Entry(String ip, String url, javaxt.utils.Date date, String header){
            this.ip = ip;
            this.url = url;
            this.date = date;
            this.header = header.split("\r\n");

          //Parse first line in the header to get path and protocol
            String[] arr = this.header[0].split(" ");
            this.path = arr[1];
            this.protocol = arr[2];


          //Parse host and extract domain name
            try{
                host = url.substring(url.indexOf("://")+3).toLowerCase();
                int idx = host.indexOf("/");
                if (idx>0) host = host.substring(0, idx);
                arr = host.split("\\.");
                tld = arr[arr.length-1];
                domainName = arr[arr.length-2];
            }
            catch(Exception e){}
        }

        public String[] getValues(String name){
            java.util.ArrayList<String> values = new java.util.ArrayList<String>();
            for (String row : header){

                if (row.contains(":")){
                    String key = row.substring(0, row.indexOf(":")).trim();
                    String value = row.substring(row.indexOf(":")+1).trim();

                    if (key.equalsIgnoreCase(name)){
                        values.add(value);
                    }
                }
            }
            return values.toArray(new String[values.size()]);
        }
        public String getValue(String name){
            String[] values = this.getValues(name);
            return (values.length>0) ? values[0] : "";
        }

      /** Flag used to indicate whether the entry can be skipped. For example,
       *  duplicate requests made within 5 minutes of each other or range
       *  requests made before 5/18/2013 should be ignored.
       */
        public boolean ignore(){
            return skip;
        }
        public String getURL(){
            return url;
        }
        public String getPath(){
            return path;
        }
        public String getProtocol(){
            return protocol;
        }
        public javaxt.utils.Date getDate(){
            return date.clone();
        }
        public String getDomainName(){
            return domainName;
        }
        public String getIP(){
            return ip;
        }
    }


  //**************************************************************************
  //** getEntries
  //**************************************************************************
    public javaxt.utils.Generator<Entry> getEntries(){

        return new javaxt.utils.Generator<Entry>() {

            @Override
            public void run() {


                java.util.HashMap<String, javaxt.utils.Date> map =
                new java.util.HashMap<String, javaxt.utils.Date>();


                String[] requests = log.getText().split("\r\n\r\n");
                for (int i=0; i<requests.length; i++){
                    try{
                        if (requests[i].startsWith("New Request From")){



                          //Flag used to indicate whether the user should consider
                          //skipping over the entry when procesing entries
                            boolean skip = false;


                          //Parse request metadata
                            String ip = null;
                            String url = null;
                            String op = null; //GET, POST, etc
                            javaxt.utils.Date date = null;
                            for (String row : requests[i].split("\r\n")){

                                if (row.contains(":")){
                                    String key = row.substring(0, row.indexOf(":")).trim();
                                    String value = row.substring(row.indexOf(":")+1).trim();


                                    if (key.equals("New Request From")){
                                        ip = value;
                                    }
                                    else if (key.equalsIgnoreCase("Timestamp")){
                                        try{
                                            date = new javaxt.utils.Date(value);
                                        }
                                        catch(Exception e){
                                            e.printStackTrace();
                                        }
                                    }
                                    else{
                                        op = key;
                                        url = value;
                                    }
                                }
                            }


                          //Check the last time the client requested this url. If
                          //it was less then 5 minutes ago, mark the entry as skipable
                            try {
                                String key = ip + "-" + url.toLowerCase();
                                if (map.containsKey(key)){
                                    javaxt.utils.Date d = map.get(key);
                                    long seconds = date.compareTo(d, "seconds");
                                    if (seconds<(60*5)) skip = true;
                                    else map.put(key, date);
                                }
                                else{
                                    map.put(key, date);
                                }
                            }
                            catch(Exception e){
                            }



                          //Parse request header
                            i=i+1;
                            String requestHeader = requests[i];
                            Entry entry = new Entry(ip, url, date, requestHeader);


                          //Check if this is a range request. If so mark it as skipable
                            String range = entry.getValue("Range");
                            if (range.contains("bytes=")){ //Range: bytes=658282-

                              //The JavaXT Server did not support range requests
                              //before 5/18/2013
                                if (id!=null && id<20130518) skip = true;
                            }


                          //Update skip flag
                            entry.skip = skip;


                          //Yield entry to caller
                            yield(entry);

                        }
                    }
                    catch(Exception e){
                        e.printStackTrace();
                        System.out.println(log.getName());
                        System.out.println(requests[i]);
                    }
                }

            }
        };
    }


  //**************************************************************************
  //** cleanLogFile
  //**************************************************************************
  /** Used to update the log file by removing any records for a given IP
   *  address.
   */
    public void cleanLogFile(String ipAddress){
        java.util.Date date = log.getDate();
        String[] requests = log.getText().split("\r\n\r\n");

        StringBuffer str = new StringBuffer();


        for (int i=0; i<requests.length; i++){
            if (requests[i].startsWith("New Request From")){

                String request = requests[i] + "\r\n\r\n" + requests[i+1] + "\r\n\r\n";
                i++;


                if (!request.startsWith("New Request From: " + ipAddress)){

                    //System.out.println(request);
                    str.append(request);
                }
            }

        }
        log.write(str.toString());
        log.setDate(date);
    }
}