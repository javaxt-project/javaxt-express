package javaxt.portal;
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


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of Logger. */

    public Logger(File logDir) {
        this.logDir = logDir;
        this.date = -1;
    }


  //**************************************************************************
  //** log
  //**************************************************************************
  /** Writes to the pool */

    public static void log(String client, HttpServletRequest request) {
        
        StringBuffer str = new StringBuffer();
        str.append("New Request From: " + client + "\r\n");
        str.append(request.getMethod() + ": " + request.getURL() + "\r\n");
        str.append("TimeStamp: " + new java.util.Date() + "\r\n");
        str.append("\r\n");
        str.append(request.toString());
        log(str.toString());
    }

    public static void log(Exception e){
        String s = e.getClass().getName();
        s = s.substring(s.lastIndexOf(".")+1);
        String message = e.getLocalizedMessage();
        String error = (message != null) ? (s + ": " + message) : s;
        log(error);
    }

    
    private static void log(String str){
        if (str!=null){
            if (!str.endsWith("\r\n")) str+= "\r\n";
            synchronized (pool) {
               pool.add(pool.size(), str);
               pool.notifyAll();
            }
        }
    }




  //**************************************************************************
  //** Run
  //**************************************************************************
  /** Used to remove RequestHeader from the queue and write it to a file.
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

if (request.contains("User-Agent: EMR Bootstrap Service")) continue;

            try{                
                byte[] b = request.getBytes();
                ByteBuffer output = ByteBuffer.allocateDirect(b.length);
                output.put(b);
                output.flip();
                getFileChannel().write(output);
            }
            catch(Exception e){
                e.printStackTrace();
            }

        }
    }


    private FileChannel getFileChannel() throws Exception {
                
        int date = Integer.parseInt(new SimpleDateFormat("yyyyMMdd").format(new java.util.Date()));
        if (date>this.date){
            this.date = date;

            if (outputFile!=null) outputFile.close();
            if (outChannel!=null) outChannel.close();

            File file = new File(logDir, this.date + ".log");
            outputFile = new FileOutputStream(file, true);
            outChannel = outputFile.getChannel();
        }
        return outChannel;
    }

}