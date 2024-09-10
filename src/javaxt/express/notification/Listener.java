package javaxt.express.notification;
import javaxt.utils.Value;

public interface Listener {


  //**************************************************************************
  //** processEvent
  //**************************************************************************
  /** Used to process an event published by the NotificationService
   *  @param event Type of event (e.g. "create", "update", "delete")
   *  @param model Subject of the event (e.g. "User", "File", etc)
   *  @param data Additional information related to the event (e.g. User ID)
   *  @param timestamp Timestamp of when the event was published. Units are
   *  in nanoseconds in UTC. Use the getMilliseconds() method in the
   *  javaxt.express.utils.DateUtils. to convert the nanoseconds to
   *  milliseconds.
   */
    public void processEvent(String event, String model, Value data, long timestamp);

}