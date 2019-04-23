package javaxt.express.ws;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import javaxt.json.JSONObject;
import javaxt.sql.*;
import javaxt.utils.Console;
import javaxt.express.api.Sort;
import javaxt.http.servlet.ServletException;
import javaxt.json.JSONArray;


//******************************************************************************
//**  WebService
//******************************************************************************
/**
 *   Implementations of this class are used to generate responses to web
 *   service requests.
 *
 ******************************************************************************/

public abstract class WebService {

    private ConcurrentHashMap<String, DomainClass> classes =
        new ConcurrentHashMap<String, DomainClass>();


    public Console console = new Console();

    private class DomainClass {
        private Class c;
        private boolean readOnly;
        public DomainClass(Class c, boolean readOnly){
            this.c = c;
            this.readOnly = readOnly;
        }
        public boolean isReadOnly(){
            return readOnly;
        }
    }


  //**************************************************************************
  //** addClass
  //**************************************************************************
  /** Adds a class to the list of classes that support CRUD operations.
   */
    public void addClass(Class c){
        addClass(c, false);
    }

    public void addClass(Class c, boolean readOnly){
        if (!Model.class.isAssignableFrom(c)){
            throw new IllegalArgumentException();
        }

        String name = c.getSimpleName();
        String pkg = c.getPackage().getName();
        if (name.startsWith(pkg)) name = name.substring(pkg.length()+1);
        name = name.toLowerCase();
        int idx = name.lastIndexOf(".");
        if (idx>0) name = name.substring(idx+1);

        synchronized(classes){
            classes.put(name, new DomainClass(c, readOnly));
            classes.notify();
        }
    }


  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
    public ServiceResponse getServiceResponse(ServiceRequest request, Database database)
        throws ServletException {



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
                                return getServiceResponse(e);
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
            DomainClass c = getClass(className);
            if (c!=null) return get(c.c, request);


            if (className.endsWith("ies")){ //Categories == Category
                c = getClass(className.substring(0, className.length()-3) + "y");
            }
            else if (className.endsWith("ses")){ //Classes == Class
                c = getClass(className.substring(0, className.length()-2));
            }
            else if (className.endsWith("s")){ //Sources == Source
                c = getClass(className.substring(0, className.length()-1));
            }
            if (c!=null) return list(c.c, request, database);

        }
        else if (method.startsWith("save")){
            String className = method.substring(4);
            DomainClass c = getClass(className);
            if (c!=null){
                if (c.isReadOnly()){
                    return new ServiceResponse(403, "Write access forbidden.");
                }
                else{
                    return save(c.c, request);
                }
            }
        }
        else if (method.startsWith("delete")){
            String className = method.substring(6);
            DomainClass c = getClass(className);
            if (c!=null){
                if (c.isReadOnly()){
                    return new ServiceResponse(403, "Delete access forbidden.");
                }
                else{
                    return delete(c.c, request);
                }
            }
        }

        return new ServiceResponse(501, "Not Implemented.");
    }



  //**************************************************************************
  //** get
  //**************************************************************************
  /** Used to retrieve an object from the database. Returns a JSON object.
   */
    private ServiceResponse get(Class c, ServiceRequest request) {
        try{
            Object obj = newInstance(c, request.getID());
            Method toJson = getMethod("toJson", c);
            return new ServiceResponse((JSONObject) toJson.invoke(obj));
        }
        catch(Exception e){
            return getServiceResponse(e);
        }
    }


  //**************************************************************************
  //** list
  //**************************************************************************
  /** Used to retrieve a shallow list of objects from the database.
   */
    private ServiceResponse list(Class c, ServiceRequest request, Database database){


      //Get tableName associated with the Model
        String tableName;
        try{
            Object obj = c.newInstance();
            java.lang.reflect.Field field = obj.getClass().getSuperclass().getDeclaredField("tableName");
            field.setAccessible(true);
            tableName = (String) field.get(obj);
        }
        catch(Exception e){
            return getServiceResponse(e);
        }


      //Excute query and generate response
        Connection conn = null;
        try{


          //Build sql string
            StringBuilder str = new StringBuilder("select ");
            javaxt.express.api.Field[] fields = request.getFields();
            if (fields==null) str.append(" * ");
            else{
                for (int i=0; i<fields.length; i++){
                    if (i>0) str.append(",");
                    String fieldName = fields[i].toString();
                    //TODO: camelcase to underscore
                    str.append(fieldName);
                }
            }

            str.append(" from ");
            str.append(tableName);

            Sort sort = request.getSort();
            if (!sort.isEmpty()){
                str.append(" order by ");
                java.util.Iterator<String> it = sort.getKeySet().iterator();
                while (it.hasNext()){
                    String colName = it.next();
                    String direction = sort.get(colName);
                    str.append(colName);
                    str.append(" ");
                    str.append(direction);
                    if (it.hasNext()) str.append(",");
                }
            }

            Long offset = request.getOffset();
            if (offset!=null){
                str.append(" offset ");
                str.append(offset);
            }

            Long limit = request.getLimit();
            if (limit==null) limit = 100L;
            if (limit!=null){
                str.append(" limit ");
                str.append(limit);
            }


            long x = 0;
            JSONArray cols = new JSONArray();
            StringBuilder json = new StringBuilder("{\"rows\":[");

            conn = database.getConnection();
            for (Recordset rs : conn.getRecordset(str.toString())){
                JSONArray row = new JSONArray();
                for (Field field : rs.getFields()){
                    String fieldName = underscoreToCamelCase(field.getName());
                    if (x==0) cols.add(fieldName);
                    Value val = field.getValue();


                  //Special case for json objects
                    if (!val.isNull()){
                        Object obj = val.toObject();
                        Package pkg = obj.getClass().getPackage();
                        String packageName = pkg==null ? "" : pkg.getName();
                        if (!packageName.startsWith("java")){
                            String s = obj.toString().trim();
                            if (s.startsWith("{") && s.endsWith("}")){
                                try{
                                    val = new Value(new JSONObject(s));
                                }
                                catch(Exception e){}
                            }
                            else if (s.startsWith("[") && s.endsWith("]")){
                                try{
                                    val = new Value(new JSONArray(s));
                                }
                                catch(Exception e){}
                            }
                        }
                    }


                    row.add(val);
                }
                if (x>0) json.append(",");
                json.append(row.toString());
                x++;
            }
            conn.close();
            json.append("]");


            json.append(",\"cols\":");
            json.append(cols.toString());
            json.append("}");
            ServiceResponse response = new ServiceResponse(json.toString());
            response.setContentType("application/json");
            return response;
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return getServiceResponse(e);
        }
    }


  //**************************************************************************
  //** save
  //**************************************************************************
  /** Used to create or update an object in the database. Returns the object
   *  ID.
   */
    private ServiceResponse save(Class c, ServiceRequest request) {
        try{
            JSONObject json = new JSONObject(new String(request.getPayload(), "UTF-8"));
            if (json.isEmpty()) throw new Exception("JSON is empty.");



          //Create new instance of the class
            Object obj;
            Long id = json.get("id").toLong();
            if (id!=null){
                obj = newInstance(c, id);
                Method update = c.getDeclaredMethod("update", JSONObject.class);
                update.invoke(obj, new Object[]{json});
            }
            else{
                obj = newInstance(c, json);
            }


          //Call the save method
            Method save = getMethod("save", c);
            save.invoke(obj);



          //Return response
            Method getID = getMethod("getID", c);
            return new ServiceResponse(((Long)getID.invoke(obj))+"");
        }
        catch(Exception e){
            return getServiceResponse(e);
        }
    }


  //**************************************************************************
  //** delete
  //**************************************************************************
  /** Used to delete an object in the database. Returns a 200 status code if
   *  the object was successfully deleted.
   */
    private ServiceResponse delete(Class c, ServiceRequest request) {
        try{

          //Create new instance of the class
            Object obj = newInstance(c, request.getID());

          //Delete object
            Method delete = getMethod("delete", c);
            delete.invoke(obj);


            return new ServiceResponse(200);
        }
        catch(Exception e){
            return getServiceResponse(e);
        }
    }


  //**************************************************************************
  //** getClass
  //**************************************************************************
  /** Returns a class from the list of know/supported classes for a given
   *  class name.
   */
    private DomainClass getClass(String className){
        synchronized(classes){
            return classes.get(className);
        }
    }


  //**************************************************************************
  //** getMethod
  //**************************************************************************
  /** Returns a declared (public) method defined in a given class.
   */
    private Method getMethod(String name, Class clazz){
        while (clazz != null) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }


  //**************************************************************************
  //** newInstance
  //**************************************************************************
  /** Returns a new instance for a given class using an ID and a connection to
   *  the database.
   */
    private Object newInstance(Class c, long id) throws Exception {
        Constructor constructor = c.getDeclaredConstructor(new Class[]{Long.TYPE});
        return constructor.newInstance(new Object[]{id});
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


  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
  /** Returns a ServiceResponse for a given Exception.
   */
    private ServiceResponse getServiceResponse(Exception e){
        if (e instanceof java.lang.reflect.InvocationTargetException){
            return new ServiceResponse(e.getCause());
        }
        else{
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** underscoreToCamelCase
  //**************************************************************************
  /** Used to convert a string with underscores (e.g. user_id) into camel case
   *  (e.g. userID). Credit: https://stackoverflow.com/a/17061543/
   */
    private static String underscoreToCamelCase(String input){
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("_(.)");
        java.util.regex.Matcher m = p.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1).toUpperCase());
        }
        m.appendTail(sb);

        String str = sb.toString();
        if (str.endsWith("Id") && input.toLowerCase().endsWith("_id")){
            str = str.substring(0, str.length()-2) + "ID";
        }
        return str;
    }
}