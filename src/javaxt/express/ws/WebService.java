package javaxt.express.ws;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import javaxt.json.JSONObject;
import javaxt.sql.Connection;
import javaxt.sql.Database;
import javaxt.utils.Console;
import java.io.IOException;
import javaxt.http.servlet.ServletException;


//******************************************************************************
//**  WebService
//******************************************************************************
/**
 *   Implementations of this class are used to respond to web service requests.
 *
 ******************************************************************************/

public abstract class WebService {

    private ConcurrentHashMap<String, Class<?>> classes = 
        new ConcurrentHashMap<String, Class<?>>();

    public Console console = new Console();
    
    
  //**************************************************************************
  //** addClass
  //**************************************************************************
  /** Adds a class to the list of classes that support CRUD operations.
   */
    public void addClass(Class c){
        synchronized(classes){
            String name = c.getSimpleName();         
            
            
            String pkg = c.getPackage().getName();
            if (name.startsWith(pkg)) name = name.substring(pkg.length()+1);
            name = name.toLowerCase();
            int idx = name.lastIndexOf(".");
            if (idx>0) name = name.substring(idx+1);
            classes.put(name, c);
            classes.notify();
        }
    }
    
    
  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
    public ServiceResponse getServiceResponse(ServiceRequest request, Database database)
        throws ServletException, IOException {

        
        
      //Get requested method
        String method = request.getMethod().toLowerCase();

        
        
      //Check if the subclass has implemented the requested method. Note that 
      //the getDeclaredMethod will only find method declared in the current 
      //Class, not inherited from supertypes. So we may need to traverse up the 
      //concrete class hierarchy if necessary.
        for (Method m : this.getClass().getDeclaredMethods()){
            if (m.getName().equalsIgnoreCase(method)){
                if (m.getReturnType().equals(ServiceResponse.class)){
                    
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length==2){
                        if (params[0]==ServiceRequest.class && params[1]==Database.class){
                            try{
                                m.setAccessible(true);
                                return (ServiceResponse) m.invoke(this, new Object[]{request, database});
                            }
                            catch(Exception e){
                                return new ServiceResponse(e);
                            }
                        }
                    }
                }
            }
        }
        
        
      //If we're still here, see if the requested method corresponds to a 
      //standard CRUD operation. 
        if (method.startsWith("get")){
            String className = method.substring(3);
            Class c = getClass(className);
            if (c!=null) return get(c, request, database);
        }
        else if (method.startsWith("save")){
            String className = method.substring(4);
            Class c = getClass(className);
            if (c!=null) return save(c, request, database);
        }
        else if (method.startsWith("delete")){
            String className = method.substring(6);
            Class c = getClass(className);
            if (c!=null) return delete(c, request, database);
        }
        
        return new ServiceResponse(501, "Not Implemented.");
    }
    
    

    
    
  //**************************************************************************
  //** get
  //**************************************************************************
  /** Used to retrieve an object from the database. Returns a JSON object.
   */
    private ServiceResponse get(Class c, ServiceRequest request, Database database) {
        Connection conn = null;
        try{
          //Create new instance
            conn = database.getConnection();
            Object obj = newInstance(c, request.getID(), conn);
            conn.close();
            
          //Return json
            Method toJson = c.getDeclaredMethod("toJson");
            return new ServiceResponse((JSONObject) toJson.invoke(obj));
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(e);
        }
    }
    
    
  //**************************************************************************
  //** save
  //**************************************************************************
  /** Used to create or update an object in the database. Returns the object 
   *  ID.
   */
    private ServiceResponse save(Class c, ServiceRequest request, Database database) {
        Connection conn = null;
        try{
            JSONObject json = new JSONObject(new String(request.getPayload(), "UTF-8"));
            if (json.isEmpty()) throw new Exception("JSON is empty.");
            
            
          //Open database connection
            conn = database.getConnection();
            
            
          //Create new instance of the class
            Object obj;
            Long id = json.get("id").toLong();
            if (id!=null){
                obj = newInstance(c, id, conn);
                Method update = c.getDeclaredMethod("update", JSONObject.class);
                update.invoke(obj, new Object[]{json});
            }
            else{
                obj = newInstance(c, json);
            }
            
            
          //Call the save method
            Method save = c.getDeclaredMethod("save", Connection.class);
            save.invoke(obj, new Object[]{conn});
            
            
          //Close database connection
            conn.close();
            
            
          //Return response
            Method getID = c.getDeclaredMethod("getID");
            return new ServiceResponse(((Long)getID.invoke(obj))+"");
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** delete
  //**************************************************************************
  /** Used to delete an object in the database. Returns a 200 status code if
   *  the object was successfully deleted.
   */
    private ServiceResponse delete(Class c, ServiceRequest request, Database database) {
        Connection conn = null;
        try{
            
          //Open database connection
            conn = database.getConnection();
            
          //Create new instance of the class
            Object obj = newInstance(c, request.getID(), conn);
            
          //Delete object
            Method delete = c.getDeclaredMethod("delete", Connection.class);
            delete.invoke(obj, new Object[]{conn});
            
          //Close connection
            conn.close();
            
            return new ServiceResponse(200);
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(e);
        }
    }

    

    
    
  //**************************************************************************
  //** getClass
  //**************************************************************************
  /** Returns a class from the list of know/supported classes for a given 
   *  class name.
   */
    private Class getClass(String className){
        synchronized(classes){
            return classes.get(className);
        }
    }
    
    
  //**************************************************************************
  //** newInstance
  //**************************************************************************
  /** Returns a new instance for a given class using an ID and a connection to
   *  the database.
   */
    private Object newInstance(Class c, long id, Connection conn) throws Exception {
        Constructor constructor = c.getDeclaredConstructor(new Class[]{Long.TYPE, Connection.class});
        return constructor.newInstance(new Object[]{id, conn});
    }
    
    
  //**************************************************************************
  //** newInstance
  //**************************************************************************
  /** Returns a new instance for a given class using a JSON object.
   */
    private Object newInstance(Class c, JSONObject json) throws Exception {
        Constructor constructor = c.getDeclaredConstructor(new Class[]{JSONObject.class});
        return constructor.newInstance(new Object[]{json});
    }

}