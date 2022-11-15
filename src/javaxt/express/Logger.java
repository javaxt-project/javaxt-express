package javaxt.express;
import java.util.List;
import java.util.LinkedList;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import javaxt.http.servlet.HttpServletRequest;

//******************************************************************************
//**  Logger Class
//******************************************************************************
/**
 *   Used to log server activity to a text file. A new text file is created
 *   for each day.
 *
 ******************************************************************************/

public class Logger implements Runnable {

    private static List pool = new LinkedList();
    private File logDir;
    private FileChannel outChannel;
    private FileOutputStream outputFile;
    private int date;
    private java.util.TimeZone tz;
    private Long maxFileSize;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Logger(File logDir) {
        this(logDir, null, "UTC");
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Logger(File logDir, Long maxFileSize, String timezone) {
        if (!logDir.exists()) logDir.mkdirs();
        this.logDir = logDir;
        this.date = -1;
        this.maxFileSize = maxFileSize;
        this.tz = javaxt.utils.Date.getTimeZone(timezone);
    }


  //**************************************************************************
  //** log
  //**************************************************************************
  /** Used to log web requests
   */
    public void log(HttpServletRequest request) {

        String clientIP = request.getRemoteAddr();
        if (clientIP.startsWith("/") && clientIP.length()>1) clientIP = clientIP.substring(1);

        StringBuffer str = new StringBuffer();
        str.append("New Request From: " + clientIP + "\r\n");
        str.append(request.getMethod() + ": " + request.getURL() + "\r\n");
        str.append("TimeStamp: " + getDate().toString("yyyy-MM-dd HH:mm a") + "\r\n");
        str.append("\r\n");
        str.append(request.toString());
        log(str.toString());
    }


//  //**************************************************************************
//  //** log
//  //**************************************************************************
//  /** Used to log exceptions
//   */
//    public void log(Exception e){
//        String s = e.getClass().getName();
//        s = s.substring(s.lastIndexOf(".")+1);
//        String message = e.getLocalizedMessage();
//        String error = (message != null) ? (s + ": " + message) : s;
//        log(error);
//    }


  //**************************************************************************
  //** log
  //**************************************************************************
  /** Used to add a string to the log file. The string will be terminated with
   *  a line break.
   */
    public void log(String str){
        if (str!=null){
            if (!str.endsWith("\r\n")) str+= "\r\n";
            synchronized (pool) {
               pool.add(pool.size(), str);
               pool.notifyAll();
            }
        }
    }


  //**************************************************************************
  //** run
  //**************************************************************************
  /** Used to remove an entry from the queue and write it to a file.
   */
    public void run() {
        while (true) {

            String request = null;
            synchronized (pool) {
                while (pool.isEmpty()) {
                  try {
                    pool.wait();
                  }
                  catch (InterruptedException e) {
                      break;
                  }
                }
                request = (String) pool.remove(0);
            }



            try{
                byte[] b = request.getBytes();
                if (b.length>8*1024){
                    if (request.length()>250) request = request.substring(0, 250);
                    request += "...\r\n";
                    b = request.getBytes();
                }
                ByteBuffer output = ByteBuffer.allocateDirect(b.length);
                output.put(b);
                output.flip();
                getFileChannel().write(output);
            }
            catch(Exception e){
                //e.printStackTrace();
            }

        }
    }


    private FileChannel getFileChannel() throws Exception {

        javaxt.utils.Date d = getDate();
        int date = Integer.parseInt(d.toString("yyyyMMdd"));
        int year = d.getYear();

        File dir = new File(logDir, year+"");
        if (!dir.exists()) dir.mkdirs();


        if (date>this.date){
            this.date = date;

            if (outputFile!=null) outputFile.close();
            if (outChannel!=null) outChannel.close();

            File file = new File(dir, this.date + ".log");
            outputFile = new FileOutputStream(file, true);
            outChannel = outputFile.getChannel();
        }
        else{
            File file = new File(dir, this.date + ".log");
            if (maxFileSize!=null){
                if (file.length()>maxFileSize){
                    if (outputFile!=null) outputFile.close();
                    if (outChannel!=null) outChannel.close();
                    throw new Exception("Log file too big: " + file.length() + " vs " + maxFileSize);
                }
            }
        }
        return outChannel;
    }


    public javaxt.utils.Date getDate(){
        javaxt.utils.Date d = new javaxt.utils.Date();
        d.setTimeZone(tz);
        return d;
    }
}