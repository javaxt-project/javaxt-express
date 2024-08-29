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
 *   Abstract class used to map HTTP requests to either virtual or concrete
 *   methods found in the extending class.
 *
 ******************************************************************************/

public abstract class WebService {

    private ConcurrentHashMap<String, DomainClass> classes = new ConcurrentHashMap<>();


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
        public String toString(){
            return c.toString() + (readOnly ? " (readonly)" : "");
        }
    }


  //**************************************************************************
  //** addModel
  //**************************************************************************
  /** Register model that this service will support
   *  @param c A Java class that extends the javaxt.sql.Model abstract class.
   */
    public void addModel(Class c){
        addModel(c, false);
    }


  //**************************************************************************
  //** addModel
  //**************************************************************************
  /** Register model that this service will support
   *  @param c A Java class that extends the javaxt.sql.Model abstract class.
   *  @param readOnly If true, the CRUD operations will be disabled. PUT,
   *  POST, and DELETE requests will be handled the same as GET requests.
   */
    public void addModel(Class c, boolean readOnly){
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
  //** addClass
  //**************************************************************************
  /** @deprecated Use addModel instead
   */
    public void addClass(Class c){
        addModel(c);
    }


  //**************************************************************************
  //** addClass
  //**************************************************************************
  /** @deprecated Use addModel instead
   */
    public void addClass(Class c, boolean readOnly){
        addModel(c, readOnly);
    }


  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
  /** Returns a ServiceResponse for a given request.
   */
    public ServiceResponse getServiceResponse(ServiceRequest request)
        throws ServletException {
        return getServiceResponse(request, null);
    }


  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
  /** Returns a ServiceResponse for a given request and database.
   */
    public ServiceResponse getServiceResponse(ServiceRequest request, Database database)
        throws ServletException {


      //Get requested method. Note that the ServiceRequest typically prepends a
      //keyword to the request path (e.g. get, save, delete) depending on the
      //HTTP request method (e.g. GET, POST, PUT, DELETE)
        String methodName = request.getMethod();


      //Find a concrete implementation of the requested method in the subclass
        {

            boolean strictLookup = false;
            if (!strictLookup) methodName = methodName.toLowerCase();



          //Generate a list of all the service methods in the subclass. Service
          //methods are public methods that accept a ServiceRequest parameter
          //and return a ServiceResponse object. Implementation note: the
          //getDeclaredMethod() method will only find methods declared in the
          //current Class, not inherited from supertypes. So we may need to
          //traverse up the concrete class hierarchy if this becomes a requirement.
            LinkedHashMap<String, ArrayList<Method>> serviceMethods = new LinkedHashMap<>();
            for (Method m : this.getClass().getDeclaredMethods()){
                if (Modifier.isPrivate(m.getModifiers())) continue;

                if (m.getReturnType().equals(ServiceResponse.class)){

                    Class<?>[] params = m.getParameterTypes();
                    if (params.length>0){
                        if (ServiceRequest.class.isAssignableFrom(params[0])){
                            String key = m.getName();
                            if (!strictLookup) key = key.toLowerCase();
                            ArrayList<Method> methods = serviceMethods.get(key);
                            if (methods==null){
                                methods = new ArrayList<>();
                                serviceMethods.put(key, methods);
                            }
                            methods.add(m);
                        }
                    }
                }
            }



          //Experimental special case for anonymous classes
            if (this.getClass().isAnonymousClass()){
                for (Method m : this.getClass().getMethods()){
                    if (Modifier.isPrivate(m.getModifiers())) continue;

                    if (m.getReturnType().equals(ServiceResponse.class)){

                        Class<?>[] params = m.getParameterTypes();
                        if (params.length>0){
                            if (ServiceRequest.class.isAssignableFrom(params[0])){
                                String key = m.getName();
                                if (!strictLookup) key = key.toLowerCase();
                                ArrayList<Method> methods = serviceMethods.get(key);
                                if (methods==null){
                                    methods = new ArrayList<>();
                                    serviceMethods.put(key, methods);
                                    methods.add(m);
                                }
                                //methods.add(m);
                            }
                        }
                    }
                }
            }



          //Find service methods that implement the requested method
            ArrayList<Method> methods = null;
            if (serviceMethods.containsKey(methodName)){
                methods = serviceMethods.get(methodName);
            }
            else{
                int i = 0;
                if (methodName.startsWith("get")) i = 4;
                if (methodName.startsWith("save")) i = 5;
                if (methodName.startsWith("delete")) i = 6;

                if (i>0){
                    methodName = methodName.substring(i-1, i).toLowerCase() + methodName.substring(i);
                    methods = serviceMethods.get(methodName);
                }
            }


          //Return ServiceResponse
            if (methods!=null){
                for (Method m : methods){
                    Class<?>[] params = m.getParameterTypes();


                  //Check whether the method accepts a ServiceRequest
                  //or ServiceRequest + Database as inputs
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


                      //Ensure that we don't want to invoke this function!
                      //For example, the caller might want to call
                      //super.getServiceResponse(request, database);
                      //If so, we would end up in a recursion causing a
                      //stack overflow. Instead of calling getServiceResponse()
                      //let's just flow down to the CRUD handlers below.
                        StackTraceElement[] stackTrace = new Exception().getStackTrace();
                        StackTraceElement el = stackTrace[1];
                        if (m.getName().equals(el.getMethodName())){
                            break;
                        }


                      //If we're still here, call the requested method
                      //and return the response
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


      //If we're still here, see if the requested method corresponds to a
      //standard CRUD operation.
        String method = request.getMethod().toLowerCase(); //don't use methodName!
        if (method.startsWith("get")){

          //Find and return model
            String className = method.substring(3);
            DomainClass c = getClass(className);
            if (c!=null) return get(c.c, request, database);


          //Special case for plural-form of a model. Return list of models.
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

          //Find model and save
            String className = method.substring(4);
            DomainClass c = getClass(className);
            if (c!=null){
                if (c.isReadOnly()){
                    return get(c.c, request, database);
                }
                else{
                    return save(c.c, request, database);
                }
            }


          //Special case for plural-form of a model. Return list of models.
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
  /** Returns a Recordset that is used fetch records from the database and
   *  support CRUD operations. This is a protected method that extending
   *  classes can override to apply custom filters or add constraints when
   *  retrieving objects from the database. This method is called whenever an
   *  HTTP GET, POST, or DELETE request is made for a Model. It is perfectly
   *  acceptable to throw exceptions when overriding this method. When
   *  throwing exceptions, an IllegalArgumentException will return a HTTP 400
   *  error to the client and a SecurityException will return a 403 error. All
   *  other exceptions will return a 500 error.
   *  @param op Operation that is requesting the Recordset. Options include
   *  "list, "get", "save", and "delete".
   *  @param c The Model (Java class) associated with the request.
   *  @param sql The default SQL statement generated for the request.
   *  @param conn A database connection used to open the Recordset.
   */
    protected Recordset getRecordset(ServiceRequest request, String op, Class c,
        String sql, Connection conn) throws Exception {

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
        try{

          //Compile sql statement
            HashMap<String, Object> tablesAndFields = getTableAndFields(c);
            String tableName = (String) tablesAndFields.get("tableName");
            String sql = "select " + tableName + ".id from " +  tableName +
            " where ";


            Long id = request.getID();
            if (id==null){
                String where = request.getWhereStatement(tablesAndFields);
                if (where==null) return new ServiceResponse(404);
                else sql += where;
            }
            else{
                sql += tableName + ".id=" + id;
            }


          //Apply filter
            try (Connection conn = database.getConnection()){
                try (Recordset rs = getRecordset(request, "get", c, sql, conn)){
                    if (rs.EOF) id = null;
                    else id = rs.getValue("id").toLong();
                }
            }
            if (id==null) return new ServiceResponse(404);


            Object obj = newInstance(c, id);
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


      //Get tableName and fields associated with the Model
        HashMap<String, Object> tablesAndFields;
        HashSet<String> spatialFields;
        String tableName;
        try{
            tablesAndFields = getTableAndFields(c);
            tableName = (String) tablesAndFields.get("tableName");
            spatialFields = (HashSet<String>) tablesAndFields.get("spatialFields");
        }
        catch(Exception e){
            return getServiceResponse(e);
        }


      //Compile SQL statement
        StringBuilder sql = new StringBuilder("select ");
        sql.append(request.getSelectStatement(tablesAndFields));
        sql.append(" from ");
        sql.append(tableName);
        String where = request.getWhereStatement(tablesAndFields);
        if (where!=null){
            sql.append(" where ");
            sql.append(where);
        }

        Long offset = request.getOffset();
        if (offset==null || offset<1){
            sql.append(request.getOrderByStatement());
        }
        else{
          //Add ID (unique primary key) to the order by statement as needed.
          //This is important when paginating sorting on non-distinct columns (esp on H2)
            Sort sort = request.getSort();
            if (!sort.isEmpty()){
                sql.append(" order by ");
                boolean addID = true;
                java.util.Iterator<String> it = sort.getKeySet().iterator();
                while (it.hasNext()){
                    String colName = it.next();
                    String direction = sort.get(colName);
                    sql.append(colName);
                    sql.append(" ");
                    sql.append(direction);
                    if (it.hasNext()) sql.append(", ");
                    if (colName.equalsIgnoreCase("id")) addID = false;
                }
                if (addID) sql.append(", id");
            }
        }
        sql.append(request.getOffsetLimitStatement(database.getDriver()));
        //console.log(sql);


      //Get output format
        String format = request.getFormat();


      //Excute query and generate response
        try (Connection conn = database.getConnection()){
            try (Recordset rs = getRecordset(request, "list", c, sql.toString(), conn)){


                ServiceResponse response;
                if (format.equals("csv")){

                    StringBuilder csv = new StringBuilder();
                    long x = 0;
                    while (rs.next()){
                        if (x>0) csv.append("\r\n");


                      //Add header row as needed
                        if (x==0){
                            int i = 0;
                            HashSet<String> fieldNames = new HashSet<>();
                            for (javaxt.sql.Field field : rs.getFields()){
                                String fieldName = field.getName().toLowerCase();

                                if (fieldNames.contains(fieldName)) continue;
                                fieldNames.add(fieldName);

                                if (i>0) csv.append(",");
                                csv.append(fieldName);
                                i++;
                            }
                            csv.append("\r\n");
                        }


                      //Add data row
                        int i = 0;
                        HashSet<String> fieldNames = new HashSet<>();
                        for (javaxt.sql.Field field : rs.getFields()){
                            String fieldName = field.getName().toLowerCase();

                            if (fieldNames.contains(fieldName)) continue;
                            fieldNames.add(fieldName);

                            if (i>0) csv.append(",");
                            javaxt.sql.Value value = field.getValue();

                            if (!value.isNull()){
                                String val = value.toString();


                              //Update spatial data as needed
                                fieldName = StringUtils.underscoreToCamelCase(fieldName);
                                if (spatialFields.contains(fieldName)){
                                    if (database.getDriver().equals("PostgreSQL")){
                                        val = createGeom(val.toString()).toString();
                                    }
                                }


                                if (val.contains("\"") || val.contains(",")){
                                    val = "\"" + val + "\"";
                                }

                                csv.append(val);
                            }

                            i++;
                        }


                        x++;
                    }


                    response = new ServiceResponse(csv);
                    response.setContentType("text/csv");

                }
                else if (format.equals("json")){
                    StringBuilder arr = new StringBuilder("[");

                    long x = 0;
                    while (rs.next()){
                        JSONObject json = DbUtils.getJson(rs);

                      //Update spatial data as needed
                        for (String fieldName : json.keySet()){
                            JSONValue val = json.get(fieldName);
                            if (!val.isNull()){
                                if (spatialFields.contains(fieldName)){
                                    if (database.getDriver().equals("PostgreSQL")){
                                        val = new JSONValue(createGeom(val.toString()));
                                        json.set(fieldName, val);
                                    }
                                }
                            }
                        }

                        if (x>0) arr.append(",");
                        arr.append(json);
                        x++;
                    }
                    arr.append("]");

                    response = new ServiceResponse(arr.toString());
                    response.setContentType("application/json");

                }
                else {

                    long x = 0;
                    JSONArray cols = new JSONArray();
                    StringBuilder json = new StringBuilder("{\"rows\":[");

                    while (rs.next()){
                        JSONArray row = new JSONArray();

                        HashSet<String> fieldNames = new HashSet<>();
                        for (javaxt.sql.Field field : rs.getFields()){
                            String fieldName = field.getName().toLowerCase();
                            fieldName = StringUtils.underscoreToCamelCase(fieldName);

                            if (fieldNames.contains(fieldName)) continue;
                            fieldNames.add(fieldName);
                            if (x==0) cols.add(fieldName);

                            JSONObject f = field.toJson();
                            JSONValue val = f.get("value");


                          //Update spatial data as needed
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
                        x++;
                    }

                    json.append("]");


                  //Append columns
                    json.append(",\"cols\":");
                    json.append(cols.toString());


                  //Append count as needed
                    if (request.getCount()){
                        rs.close();
                        Record r = conn.getRecord("select count(id) from " +
                        tableName + (where==null ? "" : " where " + where));
                        if (r!=null){
                            json.append(",\"count\":");
                            json.append(r.get(0).toLong());
                        }
                    }

                    json.append("}");
                    response = new ServiceResponse(json.toString());
                    response.setContentType("application/json");
                }

                return response;
            }
        }
        catch(Exception e){
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
        try{

          //Parse json
            JSONObject json = request.getJson();
            if (json==null || json.isEmpty()) throw new Exception("JSON is empty.");
            Long id = json.get("id").toLong();
            boolean isNew = id==null;


          //Apply filter
            HashMap<String, Object> tablesAndFields = getTableAndFields(c);
            String tableName = (String) tablesAndFields.get("tableName");
            String sql = "select " + tableName + ".id from " + tableName +
            " where " + tableName + ".id=" + (id==null ? -1 : id);
            try (Connection conn = database.getConnection()){
                try (Recordset rs = getRecordset(request, "save", c, sql, conn)){
                    if (rs.EOF) id = null;
                    else id = rs.getValue("id").toLong();
                }
            }
            if (id==null && !isNew) return new ServiceResponse(404);



          //Reparse json (json may have changed in getRecordset)
            json = request.getJson();
            id = json.get("id").toLong();
            isNew = id==null;


          //Create new instance of the class
            Object obj;
            if (id!=null){
                obj = newInstance(c, id);
                beforeUpdate(obj, request);
                Method update = c.getDeclaredMethod("update", JSONObject.class);
                update.invoke(obj, new Object[]{json});
            }
            else{
                obj = newInstance(c, json);
                beforeCreate(obj, request);
                isNew = true;
            }



          //Call the save method
            Method save = getMethod("save", c);
            save.invoke(obj);


          //Get id
            Method getID = getMethod("getID", c);
            id = (Long) getID.invoke(obj);
            if (id==null) return new ServiceResponse(500, "Failed to retrieve ID on save");


          //Fire event
            if (isNew) onCreate(obj, request);
            else onUpdate(obj, request);


          //Return response
            return new ServiceResponse(id+"");
        }
        catch(Exception e){
            return getServiceResponse(e);
        }
    }


  //**************************************************************************
  //** delete
  //**************************************************************************
  /** Used to delete an object in the database. Returns a JSON representation
   *  of the object that was deleted.
   */
    private ServiceResponse delete(Class c, ServiceRequest request, Database database) {
        try (Connection conn = database.getConnection()){

          //Apply filter
            Long id = request.getID();
            try (Recordset rs = getRecordset(request, "delete", c,
                "select id from " + getTableName(c.newInstance()) +
                " where id=" + id, conn)){
                if (rs.EOF) id = null;
                else id = rs.getValue("id").toLong();
            }
            if (id==null) return new ServiceResponse(404);


          //Reparse request to get ID (id may have changed in getRecordset)
            Long newID = request.getParameter("id").toLong();
            if (newID!=null) id = newID;


          //Create new instance of the class
            Object obj = newInstance(c, id);


          //Fire event
            beforeDelete(obj, request);


          //Delete object
            Method delete = getMethod("delete", c);
            delete.invoke(obj);


          //Fire event
            onDelete(obj, request);


          //Return response
            Method toJson = getMethod("toJson", c);
            JSONObject json = (JSONObject) toJson.invoke(obj);
            return new ServiceResponse(json);
        }
        catch(Exception e){
            return getServiceResponse(e);
        }
    }


  //**************************************************************************
  //** beforeCreate
  //**************************************************************************
  /** This method is called immediately before a record is inserted into the
   *  database. Override this method to process the event.
   *  @param obj Object. If this method is called by this class, the Object
   *  will correspond to an instance of a javaxt.sql.Model
   */
    public void beforeCreate(Object obj, ServiceRequest request){};


  //**************************************************************************
  //** onCreate
  //**************************************************************************
  /** This method is called immediately after a record is inserted into the
   *  database. Override this method to process the event.
   *  @param obj Object. If this method is called by this class, the Object
   *  will correspond to an instance of a javaxt.sql.Model
   */
    public void onCreate(Object obj, ServiceRequest request){};


  //**************************************************************************
  //** beforeUpdate
  //**************************************************************************
  /** This method is called immediately before a record is updated in the
   *  database. Override this method to process the event.
   *  @param obj Object. If this method is called by this class, the Object
   *  will correspond to an instance of a javaxt.sql.Model
   */
    public void beforeUpdate(Object obj, ServiceRequest request){};


  //**************************************************************************
  //** onUpdate
  //**************************************************************************
  /** This method is called immediately after a record is updated in the
   *  database. Override this method to process the event.
   *  @param obj Object. If this method is called by this class, the Object
   *  will correspond to an instance of a javaxt.sql.Model
   */
    public void onUpdate(Object obj, ServiceRequest request){};


  //**************************************************************************
  //** beforeDelete
  //**************************************************************************
  /** This method is called immediately before a record is deleted in the
   *  database. Override this method to process the event.
   *  @param obj Object. If this method is called by this class, the Object
   *  will correspond to an instance of a javaxt.sql.Model
   */
    public void beforeDelete(Object obj, ServiceRequest request){};


  //**************************************************************************
  //** onDelete
  //**************************************************************************
  /** This method is called immediately after a record is deleted in the
   *  database. Override this method to process the event.
   *  @param obj Object. If this method is called by this class, the Object
   *  will correspond to an instance of a javaxt.sql.Model
   */
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
  //** getTableAndFields
  //**************************************************************************
  /** Returns a HashMap with the table name and fields associated with a given
   *  model.
   */
    protected static HashMap<String, Object> getTableAndFields(Class c) throws Exception {

        String tableName;
        HashMap<String, String> fieldMap = new HashMap<>();
        HashSet<String> stringFields = new HashSet<>();
        HashSet<String> arrayFields = new HashSet<>();
        HashSet<String> spatialFields = new HashSet<>();

        Object obj = c.newInstance(); //maybe clone instead?

      //Get tableName
        java.lang.reflect.Field field = obj.getClass().getSuperclass().getDeclaredField("tableName");
        field.setAccessible(true);
        tableName = (String) field.get(obj);


      //Get fieldMap
        field = obj.getClass().getSuperclass().getDeclaredField("fieldMap");
        field.setAccessible(true);
        HashMap<String, String> map = (HashMap<String, String>) field.get(obj);
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()){
            String fieldName = it.next();
            String columnName = map.get(fieldName);
            fieldMap.put(fieldName, columnName);
        }
        fieldMap.put("id", "id");


      //Get spatial fields
        for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()){
            Class fieldType = f.getType();
            String packageName = fieldType.getPackage()==null ? "" :
                                 fieldType.getPackage().getName();

            if (packageName.startsWith("javaxt.geospatial.geometry") ||
                packageName.startsWith("com.vividsolutions.jts.geom") ||
                packageName.startsWith("org.locationtech.jts.geom")){
                spatialFields.add(f.getName());
            }

            if (fieldType.equals(String.class) || fieldType.equals(String[].class)){
                stringFields.add(f.getName());
            }

            if (fieldType.isArray()){
                arrayFields.add(f.getName());
            }

        }

        HashMap<String, Object> p = new HashMap<>();
        p.put("tableName", tableName);
        p.put("fieldMap", fieldMap);
        p.put("stringFields", stringFields);
        p.put("arrayFields", arrayFields);
        p.put("spatialFields", spatialFields);
        return p;
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
        else if (e instanceof SecurityException){
            return new ServiceResponse(403, "Not Authorized");
        }
        else if (e instanceof IllegalArgumentException){
            return new ServiceResponse(400, e.getMessage());
        }
        else{
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** createGeom
  //**************************************************************************
  /** Used to create a JTS Geometry from a EWKT formatted string returned from
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