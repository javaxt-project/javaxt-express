package javaxt.express;
import javaxt.json.*;
import java.util.*;

//******************************************************************************
//**  Config Class
//******************************************************************************
/**
 *   Provides thread-safe, static methods used to get and set application 
 *   variables.
 *
 ******************************************************************************/


public class Config {

    //private static javaxt.io.Jar jar;
    private static List<JSONObject> config;
    static {
        //jar = new javaxt.io.Jar(new Config());
        config = new LinkedList<JSONObject>();
        config.add(new JSONObject());
    }
    protected Config(){}
    
    
  //**************************************************************************
  //** init
  //**************************************************************************
  /** Used to initialize the config with a given JSON document. This will
   *  replace any previously assigned config values.
   */
    public static void init(JSONObject json){
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
    public static JSONValue get(String key){
        synchronized(config){
            return config.get(0).get(key);
        }
    }

    
  //**************************************************************************
  //** set
  //**************************************************************************
  /** Used to set the value for a given key.
   */
    public static void set(String key, Object value){
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
    public static boolean has(String key){
        synchronized(config){
            return config.get(0).has(key);
        }
    }
    
    
  //**************************************************************************
  //** getKeys
  //**************************************************************************
  /** Returns a list of keys found in the config.
   */
    public static ArrayList<String> getKeys(){
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
    public static boolean isEmpty(){
        return getKeys().isEmpty();
    }
    
    
  //**************************************************************************
  //** getDatabase
  //**************************************************************************
    public static javaxt.sql.Database getDatabase(){
        JSONValue val = get("database");
        if (val==null) return null;
        if (val.toObject() instanceof javaxt.sql.Database){
            return (javaxt.sql.Database) val.toObject();
        }
        else{
            JSONObject json = val.toJSONObject();
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
    }

    
  //**************************************************************************
  //** setDatabase
  //**************************************************************************
    public static void setDatabase(javaxt.sql.Database database){
        set("database", database);
    }
    
    
//  //**************************************************************************
//  //** initSchema
//  //**************************************************************************
//    public static void initSchema(Connection conn) throws SQLException {
//        
//        String schema = jar.getEntry("javaxt.photos", "Database.sql").getText();
//        ArrayList<String> statements = new ArrayList<String>();
//        for (String s : schema.split(";")){
//
//            StringBuffer str = new StringBuffer();
//            for (String i : s.split("\r\n")){
//                if (!i.trim().startsWith("--") && !i.trim().startsWith("COMMENT ")){
//                    str.append(i + "\r\n");
//                }
//            }
//
//            String cmd = str.toString().trim();
//            if (cmd.length()>0){
//                statements.add(str.toString() + ";");
//            }
//        }
//        
//        
//        java.sql.Statement stmt = conn.getConnection().createStatement();
//        for (String cmd : statements){
//
//          //Print table name
//            if (cmd.startsWith("CREATE TABLE")){
//                String tableName = cmd.substring(cmd.indexOf("TABLE")+5, cmd.indexOf("(")).trim();
//                if (tableName.startsWith("\"") && tableName.endsWith("\"")){ 
//                    tableName = tableName.substring(1, tableName.length()-1);
//                }
//                System.out.println("CREATE " + tableName);
//            }
//
//          //Execute statment
//            stmt.execute(cmd);
//        }
//        stmt.close();
//    }
    
    
  //**************************************************************************
  //** toJson
  //**************************************************************************
  /** Returns the current config in JSON notation.
   */
    public static JSONObject toJson(){
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