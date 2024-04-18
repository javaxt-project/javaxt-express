package javaxt.express.notification;

import java.util.*;
import javaxt.utils.Value;
import javaxt.express.utils.DateUtils;

//******************************************************************************
//**  NotificationService
//******************************************************************************
/**
 *   Provides static methods used to post and consume events.
 *
 ******************************************************************************/

public class NotificationService {

    private static List events = null;
    private static List<Listener> listeners;


  //**************************************************************************
  //** notify
  //**************************************************************************
  /** Used to post an event and share it with any registered event listeners
   *  @param event Type of event (e.g. "create", "update", "delete")
   *  @param model Subject of the event (e.g. "User", "File", etc)
   *  @param data Additional information related to the event (e.g. User ID)
   */
    public static void notify(String event, String model, Value data){
        if (events==null) return;

        if (data==null) data = new Value(null);

        long timestamp = DateUtils.getCurrentTime();
        synchronized(events){
            events.add(new Object[]{event, model, data, timestamp});
            events.notify();
        }
    }


  //**************************************************************************
  //** start
  //**************************************************************************
  /** Used to start the notification engine
   */
    public static void start(){
        start(4);
    }

    public static void start(int numThreads){
        if (events!=null) return;

        listeners = new LinkedList();

        events = new LinkedList();
        for (int i=0; i<numThreads; i++){
            Thread thread = new Thread(new NotificationProcessor());
            thread.start();
        }
    }


  //**************************************************************************
  //** stop
  //**************************************************************************
  /** Used to stop the notification engine
   */
    public static void stop(){

        synchronized(listeners){
            listeners.clear();
            listeners.notify();
        }

        synchronized(events){
            events.clear();
            events.add(0, null);
            events.notify();
        }
        events = null;
    }


  //**************************************************************************
  //** addListener
  //**************************************************************************
  /** Used to add an event listener that will consume events
   */
    public static void addListener(Listener listener){
        synchronized(listeners){
            listeners.add(listener);
            listeners.notify();
        }
    }


  //**************************************************************************
  //** NotificationProcessor
  //**************************************************************************
  /** Thread used to pass events to listeners
   */
    private static class NotificationProcessor implements Runnable {

        public void run() {

            while (true) {

                Object obj = null;
                synchronized (events) {
                    while (events.isEmpty()) {
                        try {
                            events.wait();
                        }
                        catch (InterruptedException e) {
                            return;
                        }
                    }
                    obj = events.get(0);
                    if (obj!=null) events.remove(0);
                    events.notifyAll();
                }

                if (obj!=null){
                    Object[] arr = (Object[]) obj;
                    String event = (String) arr[0];
                    String model = (String) arr[1];
                    Value data = (Value) arr[2];
                    Long timestamp = (Long) arr[3];


                    for (Listener listener : listeners){
                        try{
                            listener.processEvent(event, model, data, timestamp);
                        }
                        catch(Exception e){
                        }
                    }

                }
                else{
                    return;
                }
            }
        }
    }

}