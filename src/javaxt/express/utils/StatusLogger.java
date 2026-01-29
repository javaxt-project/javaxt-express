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
 *  to the status message if a "totalRecords" counter is given.
 *  <p>
 *  The status logger is run in a separate thread. The "recordCounter" is
 *  updated by the caller. Example:
 *  </p>
 <pre>
  //Instantite the StatusLogger with a record counter
    AtomicLong recordCounter = new AtomicLong(0);
    StatusLogger statusLogger = new StatusLogger(recordCounter);

  //Execute some process then update the counter
    ...
    recordCounter.incrementAndGet();
    ...

  //Shutdown the StatusLogger when finished processing
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


        r = new Runnable(){
            public void run() {
                print(recordCounter);
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
    public synchronized void setTotalRecords(long n){
        if (n<0) return;
        totalRecords.set(n);
        r.run();
    }


  //**************************************************************************
  //** getTotalRecords
  //**************************************************************************
  /** Returns the total number of records expected to be processed.
   */
    public synchronized Long getTotalRecords(){
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


  //**************************************************************************
  //** print
  //**************************************************************************
    private synchronized void print(AtomicLong recordCounter){
        long currTime = System.currentTimeMillis();
        double elapsedTime = (currTime-startTime)/1000; //seconds
        long x = recordCounter.get();
        long total = totalRecords.get();


        String rateText = "0 records per second";
        long recordsPerSecond = 0;
        double recordsPerSecondDouble = 0;
        try{
            if (elapsedTime > 0 && x > 0) {
                recordsPerSecondDouble = x / elapsedTime;
                recordsPerSecond = Math.round(recordsPerSecondDouble);
            }
            if (total>0){
                if (recordsPerSecond>total) recordsPerSecond = total;
            }

            // Display seconds per record if rate is less than 1 record per second
            if (recordsPerSecondDouble < 1.0 && x > 0) {
                double secondsPerRecord = elapsedTime / x;
                rateText = StringUtils.format(secondsPerRecord) + " seconds per record";
            } else {
                rateText = StringUtils.format(recordsPerSecond) + " records per second";
            }
        }
        catch(Exception e){}

        int len = statusText.length();
        if (!separateMessages){
            for (int i=0; i<len; i++){
                System.out.print("\b");
            }
        }

        statusText = StringUtils.format(x) + " records processed (" + rateText + ")";


        if (total>0){
            // Ensure x doesn't exceed total (can happen due to timing)
            long displayX = Math.min(x, total);
            double p = ((double) displayX / (double) total);
            int percentComplete = (int) Math.round(p*100);

            String _etc = "---------- --:-- --";
            if (elapsedTime>0 && recordsPerSecond>0){
                long recordsRemaining = Math.max(0, total - displayX);
                if (recordsRemaining > 0) {
                    int timeRemaining = (int) Math.round(((double) recordsRemaining / recordsPerSecond) / 60);

                    javaxt.utils.Date etc = new javaxt.utils.Date();
                    etc.add(timeRemaining, "minutes");

                    if (tz!=null) etc.setTimeZone(tz);
                    _etc = etc.toString("yyyy-MM-dd HH:mm a");
                }
            }

            statusText += " " + displayX + "/" + total + " " + percentComplete + "% ETC: " + _etc;
        }

        while (statusText.length()<len) statusText += " ";


        System.out.print(statusText + (separateMessages ? "\r\n" : ""));
        System.out.flush(); // Ensure output is written immediately
    }
}