package javaxt.express.subscription;

public class SubscriptionService implements Runnable {
  
    
  //Pool/Queue of elements for threads to process
    private static java.util.List pool = new java.util.LinkedList();
    private static java.util.List<Subscriber> subscribers = new java.util.LinkedList<Subscriber>();
    
    
    public static void subscribe(Subscriber subscriber){
        
        synchronized (subscribers){
            subscribers.add(subscriber);
            subscribers.notify();
        }
    }
    
    public static void publish(String event, String className, long id){
        synchronized (pool) {
            pool.add(new Object[]{new java.util.Date(), event, className, id});
            pool.notify();
        }
    }
    
    public void run() {

        while (true) {

            Object obj = null;
            synchronized (pool) {
                while (pool.isEmpty()) {
                    try {
                      pool.wait();
                    }
                    catch (InterruptedException e) {
                      return;
                    }
                }
                obj = pool.get(0);
                if (obj!=null) pool.remove(0);
                pool.notifyAll();
            }

            if (obj!=null){
                
                Object[] arr = (Object[]) obj;
                java.util.Date date = (java.util.Date) arr[0];
                String event = (String) arr[1];
                String className = (String) arr[2];
                Long id = (Long) arr[3];
                
                synchronized (subscribers){
                    for (Subscriber subscriber : subscribers){
                        subscriber.notify(date, event, className, id);
                    }
                }
            }
            else{
                return;
            }
        }
    }
    
}