package javaxt.express.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


//******************************************************************************
//**  StatusLogger
//******************************************************************************
/**
 *  Used to print status messages
 *
 ******************************************************************************/

public class StatusLogger {

    private long startTime;
    private AtomicLong totalRecords;
    private String statusText = "0 records processed (0 records per second)";
    private ScheduledExecutorService executor;
    private Runnable r;


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
                long currTime = System.currentTimeMillis();
                double elapsedTime = (currTime-startTime)/1000; //seconds
                long x = recordCounter.get();

                String rate = "0";
                try{
                    long r = Math.round(x/elapsedTime);
                    if (totalRecords!=null && totalRecords.get()>0){
                        if (r>totalRecords.get()) r = totalRecords.get();
                    }
                    rate = StringUtils.format(r);
                }
                catch(Exception e){}

                int len = statusText.length();
                for (int i=0; i<len; i++){
                    System.out.print("\b");
                }

                statusText = StringUtils.format(x) + " records processed (" + rate + " records per second)";


                if (totalRecords!=null && totalRecords.get()>0){
                    double p = ((double) x / (double) totalRecords.get());
                    int currPercent = (int) Math.round(p*100);
                    statusText += " " + x + "/" + totalRecords.get() + " " + currPercent + "%";
                }

                while (statusText.length()<len) statusText += " ";


                System.out.print(statusText);
            }
        };


        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(r, 0, 1, TimeUnit.SECONDS);
    }


  //**************************************************************************
  //** setTotalRecords
  //**************************************************************************
    public void setTotalRecords(long n){
        totalRecords.set(n);
        r.run();
    }


  //**************************************************************************
  //** shutdown
  //**************************************************************************
    public void shutdown(){

      //Send one last status update
        r.run();

      //Clean up
        executor.shutdown();
    }
}