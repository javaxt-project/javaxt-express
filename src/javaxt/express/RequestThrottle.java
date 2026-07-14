package javaxt.express;

import java.util.*;
import java.util.concurrent.*;


//******************************************************************************
//**  RequestThrottle
//******************************************************************************
/**
 *   Sliding-window request throttle keyed by an arbitrary identifier (e.g.
 *   a client IP). Counts attempts to access a specific resource within a time
 *   window (e.g. login endpoint). Once an identifier exceeds the allowed
 *   number of attempts it is blocked until the oldest attempt rolls off the
 *   window.
 *
 *   The caller decides which attempts to count, so this supports different
 *   policies. For anti-brute-force protection, record only unsuccessful
 *   attempts and call reset() on success, so a legitimate user is never
 *   penalized for eventually succeeding. To cap request volume regardless of
 *   outcome (anti-spam), record every attempt and never reset.
 *
 *   Entries are pruned lazily as identifiers are accessed, and a background
 *   daemon thread periodically sweeps the whole map so stale entries from
 *   identifiers that never return (e.g. one-off IPs) don't accumulate.
 *
 <pre>

  //Create new throttle with 5 attempts over 15 minutes
    RequestThrottle throttle = new RequestThrottle(5, 15*60*1000L);


  //When processing a protected resource, get the client IP address
    String ip = ServiceRequest.getClientIP(request);


  //Throttle the request as needed
    if (!throttle.allow(ip)) {
        ServiceResponse response = new ServiceResponse(429, "Too many attempts");
        response.setHeader("Retry-After", throttle.retryAfterSeconds(ip)+"");
        return response;
    }


  //Check credentials. If login fails, record the IP address
    if (!validCredentials(...)) {
        throttle.recordAttempt(ip);
        return new ServiceResponse(401, "Invalid username or password");
    }


  //If we're still here, reset the throttle for the IP address
    throttle.reset(ip);

 </pre>
 *
 ******************************************************************************/

public class RequestThrottle {

    private final int maxAttempts;
    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> attempts;
    private final ScheduledExecutorService cleanupScheduler;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** @param maxAttempts Number of attempts allowed per identifier within the
   *  window before further attempts are blocked.
   *  @param windowSize Size of the window, in milliseconds. A client can fire
   *  all maxAttempts back-to-back instantly. maxAttempts+1 is blocked until
   *  the oldest attempt in the window ages out for a given key.
   */
    public RequestThrottle(int maxAttempts, long windowSize){
        this.maxAttempts = maxAttempts;
        this.windowMs = windowSize;
        this.attempts = new ConcurrentHashMap<>();


      //Periodically sweep the whole map (in addition to the lazy pruning done
      //on access) so stale entries from identifiers that never return don't
      //accumulate. Runs on a daemon thread so it never blocks JVM shutdown.
        long sweepMs = Math.max(windowMs, 60000L);
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RequestThrottle-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(this::pruneAll,
            sweepMs, sweepMs, TimeUnit.MILLISECONDS
        );
    }


  //**************************************************************************
  //** allow
  //**************************************************************************
  /** Returns true if the given identifier is allowed to attempt right now.
   */
    public synchronized boolean allow(String id){
        if (id==null) return true;
        prune(id);
        Deque<Long> hits = attempts.get(id);
        return hits==null || hits.size() < maxAttempts;
    }


  //**************************************************************************
  //** recordAttempt
  //**************************************************************************
  /** Records an attempt for the given identifier.
   */
    public synchronized void recordAttempt(String id){
        if (id==null) return;
        Deque<Long> hits = attempts.computeIfAbsent(id, k -> new ArrayDeque<>());
        hits.addLast(System.currentTimeMillis());
        prune(id);
    }


  //**************************************************************************
  //** reset
  //**************************************************************************
  /** Clears the attempt history for the given identifier (e.g. after a
   *  successful request).
   */
    public synchronized void reset(String id){
        if (id!=null) attempts.remove(id);
    }


  //**************************************************************************
  //** retryAfterSeconds
  //**************************************************************************
  /** Returns the number of seconds until the given identifier is allowed to
   *  try again (0 if it is not currently blocked). Suitable for a
   *  "Retry-After" header.
   */
    public synchronized long retryAfterSeconds(String id){
        if (id==null) return 0;
        prune(id);
        Deque<Long> hits = attempts.get(id);
        if (hits==null || hits.size() < maxAttempts) return 0;
        long msLeft = windowMs - (System.currentTimeMillis() - hits.peekFirst());
        return Math.max(1, (msLeft + 999) / 1000);
    }


  //**************************************************************************
  //** prune
  //**************************************************************************
  /** Drops attempts older than the window and removes empty entries.
   */
    private void prune(String id){
        Deque<Long> hits = attempts.get(id);
        if (hits==null) return;
        long cutoff = System.currentTimeMillis() - windowMs;
        while (!hits.isEmpty() && hits.peekFirst() < cutoff) hits.pollFirst();
        if (hits.isEmpty()) attempts.remove(id);
    }


  //**************************************************************************
  //** pruneAll
  //**************************************************************************
  /** Sweeps every identifier, dropping attempts older than the window and
   *  removing empty entries. Called periodically by the cleanup scheduler.
   */
    private synchronized void pruneAll(){
        try{
            long cutoff = System.currentTimeMillis() - windowMs;
            attempts.entrySet().removeIf(e -> {
                Deque<Long> hits = e.getValue();
                while (!hits.isEmpty() && hits.peekFirst() < cutoff) hits.pollFirst();
                return hits.isEmpty();
            });
        }
        catch(Exception e){}
    }


  //**************************************************************************
  //** close
  //**************************************************************************
  /** Stops the background cleanup thread. Optional -- the thread is a daemon,
   *  so it won't keep the JVM alive if left running.
   */
    public void close(){
        cleanupScheduler.shutdown();
    }

}