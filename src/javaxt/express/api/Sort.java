package javaxt.express.api;
import java.util.LinkedHashMap;

public class Sort {
    LinkedHashMap<String, String> fields;
    public Sort(LinkedHashMap<String, String> fields){
        this.fields = fields;
    }
    public LinkedHashMap<String, String> getFields(){
        return fields;
    }
    public java.util.Set<String> getKeySet(){
        return fields.keySet();
    }
    public String get(String key){
        return fields.get(key);
    }
    public boolean isEmpty(){
        return fields.isEmpty();
    }
}