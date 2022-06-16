package javaxt.express;
import javaxt.json.*;
import java.util.*;

//******************************************************************************
//**  Config Class
//******************************************************************************
/**
 *   Provides thread-safe methods used to get and set application variables.
 *
 ******************************************************************************/

public class Config {

    private List<JSONObject> config;

    public Config(){
        config = new LinkedList<>();
        config.add(new JSONObject());
    }


  //**************************************************************************
  //** init
  //**************************************************************************
  /** Used to initialize the config with a given JSON document. This will
   *  replace any previously assigned config values.
   */
    public void init(JSONObject json){
        synchronized(config){
            config.set(0, json);
            config.notify();
        }
    }


  //**************************************************************************
  //** get
  //**************************************************************************
  /** Returns the value for a given key.
   */
    public JSONValue get(String key){
        synchronized(config){
            return config.get(0).get(key);
        }
    }


  //**************************************************************************
  //** get
  //**************************************************************************
  /** Returns a nested value associated with the given keys
   */
    public JSONValue get(String... path){
        synchronized(config){
            return config.get(0).get(path);
        }
    }


  //**************************************************************************
  //** set
  //**************************************************************************
  /** Used to set the value for a given key.
   */
    public void set(String key, Object value){
        synchronized(config){
            config.get(0).set(key, value);
            config.notify();
        }
    }


  //**************************************************************************
  //** has
  //**************************************************************************
  /** Returns true if the config has a given key.
   */
    public boolean has(String key){
        synchronized(config){
            return config.get(0).has(key);
        }
    }


  //**************************************************************************
  //** getKeys
  //**************************************************************************
  /** Returns a list of keys found in the config.
   */
    public ArrayList<String> getKeys(){
        ArrayList<String> keys = new ArrayList<String>();
        synchronized(config){
            Iterator<String> it = config.get(0).keys();
            while (it.hasNext()){
                keys.add(it.next());
            }
        }
        return keys;
    }


  //**************************************************************************
  //** isEmpty
  //**************************************************************************
  /** Returns true if there are no entries in the config.
   */
    public boolean isEmpty(){
        return getKeys().isEmpty();
    }


  //**************************************************************************
  //** getDatabase
  //**************************************************************************
    public javaxt.sql.Database getDatabase(){
        JSONValue val = get("database");
        if (val==null) return null;
        if (val.toObject() instanceof javaxt.sql.Database){
            return (javaxt.sql.Database) val.toObject();
        }
        else{
            javaxt.sql.Database database = getDatabase(val);
            if (database!=null) setDatabase(database);
            return database;
        }
    }

    public javaxt.sql.Database getDatabase(JSONValue val){
        return getDatabase(val.toJSONObject());
    }

    public javaxt.sql.Database getDatabase(JSONObject json){
        if (json==null) return null;
        javaxt.sql.Database database = new javaxt.sql.Database();
        database.setDriver(json.get("driver").toString());
        database.setHost(json.get("host").toString());
        database.setName(json.get("name").toString());
        database.setUserName(json.get("username").toString());
        database.setPassword(json.get("password").toString());
        if (json.has("maxConnections")){
            database.setConnectionPoolSize(json.get("maxConnections").toInteger());
        }
        setDatabase(database);
        return database;
    }


  //**************************************************************************
  //** setDatabase
  //**************************************************************************
    public void setDatabase(javaxt.sql.Database database){
        set("database", database);
    }


  //**************************************************************************
  //** toJson
  //**************************************************************************
  /** Returns the current config in JSON notation.
   */
    public JSONObject toJson(){
        JSONObject json = new JSONObject();
        synchronized(config){
            JSONObject currConfig = config.get(0);
            Iterator<String> it = currConfig.keys();
            while (it.hasNext()){
                String key = it.next();
                JSONValue val = currConfig.get(key);
                Object obj = val.toObject();
                if (obj instanceof javaxt.sql.Database){
                    javaxt.sql.Database database = (javaxt.sql.Database) obj;
                    JSONObject db = new JSONObject();
                    String host = database.getHost();
                    Integer port = database.getPort();
                    if (port!=null && port>0) host += ":" + port;

                    db.set("driver", database.getDriver().getVendor());
                    db.set("host", host);
                    db.set("name", database.getName());
                    db.set("username", database.getUserName());
                    db.set("password", database.getPassword());
                    db.set("maxConnections", database.getConnectionPoolSize());
                    json.set(key, db);
                }
                else{
                    json.set(key, obj);
                }
            }
        }

        return json;
    }
}