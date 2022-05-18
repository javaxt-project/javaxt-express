package javaxt.express;
import javaxt.express.ServiceRequest.Sort;
import javaxt.express.ServiceRequest.Field;
import javaxt.express.ServiceRequest.Filter;
import javaxt.express.utils.*;

import javaxt.sql.*;
import javaxt.json.*;
import javaxt.utils.Console;
import javaxt.http.servlet.ServletException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

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


    public static Console console = new Console(); //do not replace with static import!

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
      //the getDeclaredMethod will only find methods declared in the current
      //Class, not inherited from supertypes. So we may need to traverse up the
      //concrete class hierarchy if necessary.
        for (Method m : this.getClass().getDeclaredMethods()){
            if (Modifier.isPrivate(m.getModifiers())) continue;

            if (m.getName().equalsIgnoreCase(method)){
                if (m.getReturnType().equals(ServiceResponse.class)){

                    Class<?>[] params = m.getParameterTypes();
                    if (params.length>1){

                        if (ServiceRequest.class.isAssignableFrom(params[0])){

                            Object[] inputs = null;
                            if (params.length==1){
                                inputs = new Object[]{request};
                            }
                            else if (params.length==2){
                                if (Database.class.isAssignableFrom(params[1])){
                                    inputs = new Object[]{request, database};
                                }
                            }

                            if (inputs!=null){
                                try{
                                    m.setAccessible(true);
                                    return (ServiceResponse) m.invoke(this, inputs);
                                }
                                catch(Exception e){
                                    return getServiceResponse(e);
                                }
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
            if (c!=null) return get(c.c, request, database);


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
                    return save(c.c, request, database);
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
                    return delete(c.c, request, database);
                }
            }
        }

        return new ServiceResponse(501, "Not Implemented.");
    }


  //**************************************************************************
  //** getRecordset
  //**************************************************************************
  /** Protected method that subclasses can override to apply filters or add
   *  constraints when retrieving objects from the database. This method is
   *  called before "get", "create", "update", "delete" requests.
   */
    protected Recordset getRecordset(ServiceRequest request, String op, Class c, String sql, Connection conn) throws Exception {
        Recordset rs = new Recordset();
        if (op.equals("list")) rs.setFetchSize(1000);
        rs.open(sql, conn);
        return rs;
    }


  //**************************************************************************
  //** get
  //**************************************************************************
  /** Used to retrieve an object from the database. Returns a JSON object.
   */
    private ServiceResponse get(Class c, ServiceRequest request, Database database) {
        Connection conn = null;
        try{

          //Get model
            Object obj;
            Long id = request.getID();
            if (id==null){
                Method get = getMethod("get", c);
                String[] keys = request.getParameterNames();
                ArrayList<Object> params = new ArrayList<>();
                for (String key : keys){
                    if (key.equals("_")) continue;
                    params.add(key + "=");
                    params.add(request.getParameter(key).toString());
                }
                Object[] arr = params.toArray(new Object[params.size()]);
                obj = get.invoke(null, new Object[]{arr});
            }
            else{
                obj = newInstance(c, id);
            }
            if (obj==null) return new ServiceResponse(404);



          //Apply filter
            id = null;
            conn = database.getConnection();
            Recordset rs = getRecordset(request, "get", c, "select id from " +
            getTableName(obj) + " where id=" + getMethod("getID", c).invoke(obj), conn);
            if (!rs.EOF) id = rs.getValue(0).toLong();
            rs.close();
            conn.close();
            if (id==null) return new ServiceResponse(404);


          //Return response
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


      //Get tableName and spatial fields associated with the Model
        String tableName;
        HashSet<String> spatialFields = new HashSet<>();
        try{
            Object obj = c.newInstance();
            java.lang.reflect.Field field = obj.getClass().getSuperclass().getDeclaredField("tableName");
            field.setAccessible(true);
            tableName = (String) field.get(obj);


            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()){
                Class fieldType = f.getType();
                String packageName = fieldType.getPackage()==null ? "" :
                                     fieldType.getPackage().getName();

                if (packageName.startsWith("javaxt.geospatial.geometry") ||
                    packageName.startsWith("com.vividsolutions.jts.geom") ||
                    packageName.startsWith("org.locationtech.jts.geom")){
                    spatialFields.add(f.getName());
                }
            }
        }
        catch(Exception e){
            return getServiceResponse(e);
        }


      //Excute query and generate response
        Connection conn = null;
        try{


          //Build sql string
            StringBuilder str = new StringBuilder("select ");
            Field[] fields = request.getFields();
            if (fields==null) str.append(" * ");
            else{
                for (int i=0; i<fields.length; i++){
                    if (i>0) str.append(",");
                    Field field = fields[i];
                    String fieldName = field.toString();
                    if (field.isFunction()){
                        str.append(fieldName);
                    }
                    else{
                        fieldName = StringUtils.camelCaseToUnderScore(fieldName);
                        str.append(fieldName);
                    }
                }
            }

            str.append(" from ");
            str.append(tableName);


            Filter filter = request.getFilter();
            if (!filter.isEmpty()){
                //System.out.println(filter.toJson().toString(4));
                str.append(" where ");
                Filter.Item[] items = filter.getItems();
                for (int i=0; i<items.length; i++){
                    if (i>0) str.append(" and ");
                    str.append("(");
                    str.append(items[i].toString());
                    str.append(")");
                }
            }
            else{
                String where = request.getWhere();
                if (where!=null){
                    str.append(" where ");
                    str.append(where);
                }
            }


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
            Recordset rs = getRecordset(request, "list", c, str.toString(), conn);
            while (rs.hasNext()){
                JSONArray row = new JSONArray();

                JSONObject record = DbUtils.getJson(rs);
                for (javaxt.sql.Field field : rs.getFields()){
                    String fieldName = field.getName().toLowerCase();
                    fieldName = StringUtils.underscoreToCamelCase(fieldName);
                    if (x==0) cols.add(fieldName);

                    JSONValue val = record.get(fieldName);
                    if (!val.isNull()){
                        if (spatialFields.contains(fieldName)){
                            if (database.getDriver().equals("PostgreSQL")){
                                val = new JSONValue(createGeom(val.toString()));
                            }
                        }
                    }
                    row.add(val);
                }

                if (x>0) json.append(",");
                json.append(row.toString());
                rs.moveNext();
                x++;
            }
            rs.close();
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
    private ServiceResponse save(Class c, ServiceRequest request, Database database) {
        Connection conn = null;
        try{
            JSONObject json = new JSONObject(new String(request.getPayload(), "UTF-8"));
            if (json.isEmpty()) throw new Exception("JSON is empty.");


          //Create new instance of the class
            Object obj;
            Long id = json.get("id").toLong();
            boolean isNew = false;
            if (id!=null){
                obj = newInstance(c, id);
                Method update = c.getDeclaredMethod("update", JSONObject.class);
                update.invoke(obj, new Object[]{json});
            }
            else{
                obj = newInstance(c, json);
                isNew = true;
            }


          //Apply filter
            if (!isNew){
                conn = database.getConnection();
                Recordset rs = getRecordset(request, "save", c, "select id from " +
                getTableName(obj) + " where id=" + getMethod("getID", c).invoke(obj), conn);
                if (!rs.EOF) id = rs.getValue(0).toLong();
                rs.close();
                conn.close();
                if (id==null) return new ServiceResponse(404);
            }


          //Call the save method
            Method save = getMethod("save", c);
            save.invoke(obj);


          //Get id
            Method getID = getMethod("getID", c);
            id = (Long) getID.invoke(obj);


          //Fire event
            if (isNew) onCreate(obj, request); else onUpdate(obj, request);


          //Return response
            return new ServiceResponse(id+"");
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return getServiceResponse(e);
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

          //Apply filter
            Long id = null;
            conn = database.getConnection();
            Recordset rs = getRecordset(request, "delete", c, "select id from " +
            getTableName(c.newInstance()) + " where id=" + request.getID(), conn);
            if (!rs.EOF) id = rs.getValue(0).toLong();
            rs.close();
            conn.close();
            if (id==null) return new ServiceResponse(404);


          //Create new instance of the class
            Object obj = newInstance(c, id);

          //Delete object
            Method delete = getMethod("delete", c);
            delete.invoke(obj);

          //Fire event
            onDelete(obj, request);

          //Return response
            return new ServiceResponse(200);
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return getServiceResponse(e);
        }
    }


    public void onCreate(Object obj, ServiceRequest request){};
    public void onUpdate(Object obj, ServiceRequest request){};
    public void onDelete(Object obj, ServiceRequest request){};


  //**************************************************************************
  //** getClass
  //**************************************************************************
  /** Returns a class from the list of known/supported classes for a given
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
  //** getTableName
  //**************************************************************************
  /** Returns the "tableName" private variable associated with a model
   */
    private String getTableName(Object obj) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getSuperclass().getDeclaredField("tableName");
        field.setAccessible(true);
        String tableName = (String) field.get(obj);
        return tableName;
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
  //** createGeom
  //**************************************************************************
  /** Used to create a geometry from a EWKT formatted string returned from
   *  PostgreSQL/PostGIS
   */
    public Object createGeom(String hex) throws Exception {

        //byte[] b = WKBReader.hexToBytes(hex);
        //return new WKBReader().read(b);


        Class c;
        try{
            c = Class.forName("com.vividsolutions.jts.io.WKBReader");
        }
        catch(ClassNotFoundException e){
            try{
                c = Class.forName("org.locationtech.jts.io.WKBReader");
            }
            catch(ClassNotFoundException ex){
                throw new Exception("JTS not found!");
            }
        }


        Method hexToBytes, read;
        hexToBytes = read = null;
        for (Method method : c.getDeclaredMethods()) {
            String methodName = method.getName();
            if (methodName.equals("hexToBytes")) {
                hexToBytes = method;
            }
            else if (methodName.equals("read")) {
                Parameter[] parameters = method.getParameters();
                if (parameters.length==1 && parameters[0].getType().equals(byte[].class)){
                    read = method;
                }
            }
        }
        byte[] b = (byte[]) hexToBytes.invoke(null, new Object[]{hex});
        return read.invoke(c.newInstance(), new Object[]{b});
    }
}