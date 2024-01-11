package javaxt.express.notification;
import javaxt.utils.Value;

public interface Listener {

    public void processEvent(String event, String model, Value data, long timestamp);

}