package javaxt.express.utils;

import java.util.TimeZone;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


//******************************************************************************
//**  StatusLogger
//******************************************************************************
/**
 *  Used to print status messages to the standard output stream. Status
 *  messages are written every second and appear in the following format:
 *  <pre>0 records processed (0 records per second)</pre>
 *  A percent completion and an estimated time to completion (ETC) is appended
 *  to the status message if a "totalRecords" counter is given.<br/>
 *  The status logger is run in a separate thread. The "recordCounter" is
 *  updated by the caller. Example:
 <pre>
    AtomicLong recordCounter = new AtomicLong(0);
    StatusLogger statusLogger = new StatusLogger(recordCounter);
    while (true){
       //Execute some process then update the counter
       recordCounter.incrementAndGet();
    }
    statusLogger.shutdown();
 </pre>
 *
 ******************************************************************************/

public class StatusLogger {

    private long startTime;
    private AtomicLong totalRecords;
    private String statusText = "0 records processed (0 records per second)";
    private ScheduledExecutorService executor;
    private Runnable r;
    private boolean separateMessages = false;
    private TimeZone tz;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public StatusLogger(AtomicLong recordCounter){
        this(recordCounter, null);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public StatusLogger(AtomicLong recordCounter, AtomicLong totalRecords){
        startTime = System.currentTimeMillis();
        this.totalRecords = totalRecords==null ? new AtomicLong(0) : totalRecords;

        StatusLogger me = this;
        r = new Runnable(){
            public void run() {
                long currTime = System.currentTimeMillis();
                double elapsedTime = (currTime-startTime)/1000; //seconds
                long x = recordCounter.get();
                AtomicLong totalRecords = me.totalRecords;

                String rate = "0";
                long recordsPerSecond = 0;
                try{
                    recordsPerSecond = Math.round(x/elapsedTime);
                    if (totalRecords!=null && totalRecords.get()>0){
                        if (recordsPerSecond>totalRecords.get()) recordsPerSecond = totalRecords.get();
                    }
                    rate = StringUtils.format(recordsPerSecond);
                }
                catch(Exception e){}

                int len = statusText.length();
                if (!separateMessages){
                    for (int i=0; i<len; i++){
                        System.out.print("\b");
                    }
                }

                statusText = StringUtils.format(x) + " records processed (" + rate + " records per second)";


                if (totalRecords!=null && totalRecords.get()>0){
                    double p = ((double) x / (double) totalRecords.get());
                    int percentComplete = (int) Math.round(p*100);

                    String _etc = "---------- --:-- --";
                    if (elapsedTime>0 && recordsPerSecond>0){
                        int timeRemaining = (int) Math.round(((totalRecords.get()-x)/recordsPerSecond)/60);

                        javaxt.utils.Date etc = new javaxt.utils.Date();
                        etc.add(timeRemaining, "minutes");

                        if (percentComplete==100) etc = new javaxt.utils.Date();
                        if (tz!=null) etc.setTimeZone(tz);

                        _etc = etc.toString("yyyy-MM-dd HH:mm a");
                    }

                    statusText += " " + x + "/" + totalRecords.get() + " " + percentComplete + "% ETC: " + _etc;
                }

                while (statusText.length()<len) statusText += " ";


                System.out.print(statusText + (separateMessages ? "\r\n" : ""));
            }
        };


        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(r, 0, 1, TimeUnit.SECONDS);
    }


  //**************************************************************************
  //** setTotalRecords
  //**************************************************************************
  /** Used to set the total number of records expected to be processed. By
   *  setting the total record count, the status logger will print a percent
   *  completion status update.
   */
    public void setTotalRecords(long n){
        totalRecords.set(n);
        r.run();
    }


  //**************************************************************************
  //** getTotalRecords
  //**************************************************************************
  /** Returns the total number of records expected to be processed.
   */
    public Long getTotalRecords(){
        return totalRecords.get();
    }


  //**************************************************************************
  //** setTimeZone
  //**************************************************************************
  /** Used to set the timezone when reporting ETC (estimated time to
   *  completion). ETC is rendered only if the total record count is known
   *  (see setTotalRecords). If no timezone is specified, ETC will default
   *  to the system timezone.
   *  @param timezone Name of a timezone (e.g. "America/New York", "UTC", etc)
   */
    public void setTimeZone(String timezone){
        tz = javaxt.utils.Date.getTimeZone(timezone);
    }


  //**************************************************************************
  //** setTimeZone
  //**************************************************************************
  /** Used to set the timezone when reporting ETC (see above)
   */
    public void setTimeZone(TimeZone timezone){
        tz = timezone;
    }


  //**************************************************************************
  //** getTimeZone
  //**************************************************************************
  /** Returns the timezone used to report ETC (see setTimeZone). Will return
   *  null if a timezone has not been set.
   */
    public TimeZone getTimeZone(){
        return tz;
    }


  //**************************************************************************
  //** separateMessages
  //**************************************************************************
  /** By default, status messages are written to a single line and overwritten
   *  with every update. However, this may not be appropriate when an app is
   *  writing debug messages to the same output stream. In such cases, it's
   *  best to have the status logger write status updates to a new line.
   */
    public void separateMessages(boolean b){
        separateMessages = b;
    }


  //**************************************************************************
  //** shutdown
  //**************************************************************************
  /** Used to stop the status logger.
   */
    public void shutdown(){

      //Send one last status update
        r.run();

      //Clean up
        executor.shutdown();
    }
}