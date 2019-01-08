package javaxt.express.subscription;

public abstract class Subscriber {
    
    public abstract void notify(java.util.Date date, String event, String className, long id);
    
}