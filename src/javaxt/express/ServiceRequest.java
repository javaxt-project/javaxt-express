package javaxt.express;
import java.io.StringReader;
import java.util.*;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.ServletException;
import javaxt.json.*;
import static javaxt.utils.Console.console;
import javaxt.express.utils.StringUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.select.*;

//******************************************************************************
//**  ServiceRequest
//******************************************************************************
/**
 *   Used to encapsulate an HttpServletRequest and simplify the
 *   parsing/extracting of parameters from the raw HTTP request.
 *
 ******************************************************************************/

public class ServiceRequest {

    private HttpServletRequest request;
    private String service;
    private String method = "";
    private String[] path;
    private java.security.Principal user;
    private javaxt.utils.URL url;
    private byte[] payload;
    private JSONObject json;
    private HashMap<String, List<String>> parameters; //<- Don't use this directly! Use the static getParameter() and setParameter() methods
    private Field[] fields;
    private Filter filter;
    private Sort sort;
    private Long limit;
    private Long offset;
    private Long id;
    private boolean readOnly = false;
    private boolean parseJson = false;
    private static String[] approvedFunctions = new String[]{
        "min", "max", "count", "avg", "sum"
    };


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public ServiceRequest(HttpServletRequest request){
        this(null, request);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public ServiceRequest(String service, HttpServletRequest request){
        this.service = service;
        this.request = request;
        this.url = new javaxt.utils.URL(request.getURL());
        this.parameters = url.getParameters();



      //Parse path, excluding servlet and service path
        setPath(request.getPathInfo());



      //Get offset and limit
        updateOffsetLimit();
    }


  //**************************************************************************
  //** getService
  //**************************************************************************
  /** Returns the service name from the http request. Service requests follow
   *  the convention: "http://localhost/servlet/service/path". For example
   *  "http://localhost/myapp/admin/user". In this example, the servlet path
   *  is "myapp" and the service name is "admin". Note that the servlet path
   *  is optional and may include multiple "directories". This method makes it
   *  easier to find the service name from the url.
   */
    public String getService(){
        return service;
    }


  //**************************************************************************
  //** getMethod
  //**************************************************************************
  /** Returns a method name for the HTTP request. Examples:
   *  <ul>
   *  <li>GET "http://localhost/user" returns "getUser"</li>
   *  <li>DELETE "http://localhost/user" returns "deleteUser"</li>
   *  <li>POST or PUT "http://localhost/user" returns "saveUser"</li>
   *  </ul>
   *
   *  If the request is read-only, "POST", "PUT", and "DELETE" requests will
   *  be re-mapped to "GET".
   *
   *  <p>
   *  If the URL contains a "servlet" path or a "service" path, will return
   *  the first object in the path after the servlet and/or service. Consider
   *  this example: "http://localhost/myapp/admin/user" In this example, the
   *  servlet path is "myapp" and the service path is "admin" and and so the
   *  method name is derived from "user".
   *  </p>
   *
   *  Note that this method is used by the WebService class to map service
   *  requests to REST service endpoints and execute CRUD operations.
   */
    public String getMethod(){
        return method;
    }


  //**************************************************************************
  //** setReadOnly
  //**************************************************************************
  /** Used to disable insert, update, and delete operations. "POST", "PUT",
   *  and "DELETE" requests are remapped to "GET". See getMethod() for more
   *  information on how requests are mapped.
   */
    public void setReadOnly(boolean readOnly){
        if (readOnly==this.readOnly) return;
        this.readOnly = readOnly;
        setPath(request.getPathInfo());
    }


  //**************************************************************************
  //** isReadOnly
  //**************************************************************************
  /** Returns true if insert, update, and delete operations have been disabled.
   *  See setReadOnly() for more information. Default is false.
   */
    public boolean isReadOnly(){
        return readOnly;
    }


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Returns a part of the url path at a given index AFTER the service
   *  name. For example, index 0 for "http://localhost/servlet/service/a/b/c"
   *  would yield "a".
   */
    public javaxt.utils.Value getPath(int i){
        if (path==null || i>=path.length) return new javaxt.utils.Value(null);
        else return new javaxt.utils.Value(path[i]);
    }


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Returns a part of the url path AFTER the service name. For example,
   *  "http://localhost/servlet/service/a/b/c" would yield "/a/b/c".
   */
    public String getPath(){
        if (path==null) return null;
        StringBuilder str = new StringBuilder();
        for (String s : path){
            str.append("/");
            str.append(s);
        }
        return str.toString();
    }


  //**************************************************************************
  //** setPath
  //**************************************************************************
  /** Used to update the path of the current URL. This method can be used to
   *  coerce a request to route to different web methods (see getMethod).
   *  @param path URL path, excluding servlet and service path. For example,
   *  if a URL follows the follows a pattern like
   *  "http://localhost/servlet/service/a/b/c" the path is "/a/b/c".
   */
    public void setPath(String path){
        if (path!=null){
            path = path.substring(1);
            boolean addPath = service==null;
            ArrayList<String> arr = new ArrayList<>();
            for (String str : path.split("/")){
                if (addPath) arr.add(str);

                if (str.equalsIgnoreCase(service)){
                    addPath = true;
                }
            }
            this.path = arr.toArray(new String[arr.size()]);
        }


      //Generate a method name using the request method and first "directory"
      //in the path. Example: "GET /config/users" would yield the "getUsers"
      //from the "config" service.
        String name = getPath(0).toString();
        if (name!=null){
            name = name.substring(0, 1).toUpperCase() + name.substring(1);

            if (readOnly){
                this.method = "get" + name;
            }
            else{
                String method = request.getMethod();
                if (method.equals("GET")){
                    this.method = "get" + name;
                }
                else if (method.equals("PUT") || method.equals("POST")){
                    this.method = "save" + name;
                }
                else if (method.equals("DELETE")){
                    this.method = "delete" + name;
                }
            }
        }


      //Get ID
        id = getPath(1).toLong();
        if (id==null) id = getParameter("id").toLong();
    }


  //**************************************************************************
  //** getID
  //**************************************************************************
  /** Returns the ID associated with the request. Assuming the service request
   *  follows the convention "http://localhost/servlet/service/object", the ID
   *  for the "http://localhost/photos/config/user/54" is 54. If an ID is not
   *  found in the path or is invalid, then the id parameter in the query
   *  string is returned. Example: "http://localhost/photos/config/user?id=54"
   */
    public Long getID(){
        return id;
    }


  //**************************************************************************
  //** getURL
  //**************************************************************************
  /** Returns the original url used to make the request.
   */
    public javaxt.utils.URL getURL(){
        return url;
    }


  //**************************************************************************
  //** getParameter
  //**************************************************************************
  /** Returns the value associated with a parameter in the request. Performs a
   *  case insensitive search for the keyword in the query string. In addition,
   *  will search the JSON payload of the request if parseJson is set to true.
   *  If the value is "null" then a null value is returned.
   *  @param key Query string parameter name. Performs a case insensitive
   *  search for the keyword.
   */
    public javaxt.utils.Value getParameter(String key){
        String val = null;
        if (key!=null){
            List<String> parameters = getParameter(key, this.parameters);
            if (parameters!=null){
                val = parameters.get(0).trim();
                if (val.length()>0){
                    if (val.equalsIgnoreCase("null")) val = null;
                }
                else{
                    val = null;
                }
            }

            if (val==null && parseJson){ //&& !getRequest().getMethod().equals("GET")
                JSONObject json = getJson();
                if (json!=null && json.has(key)){
                    return new javaxt.utils.Value(json.get(key).toObject());
                }
            }

        }
        return new javaxt.utils.Value(val);
    }


  //**************************************************************************
  //** hasParameter
  //**************************************************************************
  /** Returns true if the request contains a given parameter in the request.
   *  Performs a case insensitive search for the keyword in the query string.
   *  In addition, will search the JSON payload of the request if parseJson is
   *  set to true.
   */
    public boolean hasParameter(String key){
        if (key!=null){
            List<String> parameters = getParameter(key, this.parameters);
            if (parameters!=null) return true;
            if (parseJson){ //&& !getRequest().getMethod().equals("GET")
                JSONObject json = getJson();
                if (json!=null && json.has(key)){
                    return true;
                }
            }
        }
        return false;
    }


  //**************************************************************************
  //** setParameter
  //**************************************************************************
  /** Used to update a parameter extracted from the original request. Performs
   *  a case insensitive search for the keyword in the query string.
   */
    public void setParameter(String key, String val){
        if (key!=null){

            if (val==null){
                removeParameter(key, this.parameters);
                return;
            }


          //Get parameters
            List<String> parameters = getParameter(key, this.parameters);


          //Special case for classes that override the hasParameter and
          //getParameter methods.
            if (parameters==null && hasParameter(key)){
                parameters = new ArrayList<>();
                parameters.add(getParameter(key).toString());
                setParameter(key, parameters, this.parameters);
            }



          //Add or update value
            if (parameters==null){
                if (val!=null){
                    parameters = new ArrayList<>();
                    parameters.add(val);
                    setParameter(key, parameters, this.parameters);
                }
            }
            else{
                if (val!=null) parameters.set(0, val);
            }


          //Update offset and limit as needed
            String k = key.toLowerCase();
            if (k.equals("offset") || k.equals("limit") || k.equals("page")){
                updateOffsetLimit();
            }
        }
    }


  //**************************************************************************
  //** getParameterNames
  //**************************************************************************
  /** Returns a list of all the parameter keywords found in this request.
   */
    public String[] getParameterNames(){
        HashSet<String> keys = new HashSet<>();
        Iterator<String> it = this.parameters.keySet().iterator();
        while (it.hasNext()) keys.add(it.next());
        if (parseJson){
            JSONObject json = getJson();
            if (json!=null){
                for (String key : json.keySet()){
                    keys.add(key);
                }
            }
        }
        return keys.toArray(new String[keys.size()]);
    }


    private static List<String> getParameter(String key, HashMap<String, List<String>> parameters){
        return javaxt.utils.URL.getParameter(key, parameters);
    };

    private static void setParameter(String key, List<String> values, HashMap<String, List<String>> parameters){
        javaxt.utils.URL.setParameter(key, values, parameters);
    }
    private static void removeParameter(String key, HashMap<String, List<String>> parameters){
        javaxt.utils.URL.removeParameter(key, parameters);
    }



  //**************************************************************************
  //** updateOffsetLimit
  //**************************************************************************
    private void updateOffsetLimit(){
        offset = getParameter("offset").toLong();
        limit = getParameter("limit").toLong();
        Long page = getParameter("page").toLong();
        if (offset==null && page!=null){
            if (limit==null) limit = 25L;
            offset = (page*limit)-limit;
        }
    }


  //**************************************************************************
  //** getOffset
  //**************************************************************************
  /** Returns the value of the "offset" parameter in the request as a number.
   *  This parameter is used by the WebService class to paginate through list
   *  requests.
   */
    public Long getOffset(){
        return offset;
    }


  //**************************************************************************
  //** getLimit
  //**************************************************************************
  /** Returns the value of the "limit" parameter in the request as a number.
   *  This parameter is used by the WebService class to paginate through list
   *  requests.
   */
    public Long getLimit(){
        return limit;
    }


  //**************************************************************************
  //** getRequest
  //**************************************************************************
  /** Returns the original, unmodified HTTP request used to instantiate this
   *  class.
   */
    public HttpServletRequest getRequest(){
        return request;
    }


  //**************************************************************************
  //** setPayload
  //**************************************************************************
  /** Used to update the raw bytes representing the payload of the request
   */
    public void setPayload(byte[] payload){
        this.payload = payload;
        json = null;
    }


  //**************************************************************************
  //** getPayload
  //**************************************************************************
  /** Returns the raw bytes from the payload of the request
   */
    public byte[] getPayload(){
        if (payload==null){
            try{
                payload = request.getBody();
            }
            catch(Exception e){}
        }
        return payload;
    }


  //**************************************************************************
  //** getJson
  //**************************************************************************
  /** Returns the payload of the request as a JSON object
   */
    public JSONObject getJson(){
        if (json==null){

            byte[] b = getPayload();
            if (b!=null && b.length>0){
                try{
                    json = new JSONObject(new String(getPayload(), "UTF-8"));
                }
                catch(Exception e){}
            }

        }
        return json;
    }


  //**************************************************************************
  //** parseJson
  //**************************************************************************
  /** Calling this method will expand parameter searches into the payload of
   *  the request. See getParameter() for more info.
   */
    public void parseJson(){
        parseJson = true;
        if (id==null) id = getParameter("id").toLong();
        updateOffsetLimit();
    }


  //**************************************************************************
  //** getUser
  //**************************************************************************
  /** Returns the user associated with the request
   */
    public java.security.Principal getUser(){
        if (user==null){
            user = request.getUserPrincipal();
        }
        return user;
    }


  //**************************************************************************
  //** getCredentials
  //**************************************************************************
    public String[] getCredentials(){
        return request.getCredentials();
    }


  //**************************************************************************
  //** authenticate
  //**************************************************************************
    public void authenticate() throws ServletException {
        request.authenticate();
    }


  //**************************************************************************
  //** isCacheable
  //**************************************************************************
  /** Returns true if a given eTag matches the "if-none-match" request header.
   *  Additional checks are performed against other cache related headers
   *  (e.g. "cache-control" and "if-modified-since"). If true is returned,
   *  the corresponding HTTP response can be set to a 304 "Not Modified".
   *  @param eTag A custom string that acts as a unique identifier (including
   *  version) for an HTTP response. ETags are frequently used when sending
   *  static data such as files. In the context of dynamic web services, the
   *  same concept can be applied for static, or semi-static responses (e.g.
   *  static keywords, images in a database, etc).
   *  @param date A date associated with the HTTP response. The date should be
   *  in GMT and in "EEE, dd MMM yyyy HH:mm:ss zzz" format (e.g.
   *  "Sat, 23 Oct 2010 13:04:28 GMT").
   */
    public boolean isCacheable(String eTag, String date){

        String matchTag = request.getHeader("if-none-match");
        String cacheControl = request.getHeader("cache-control");
        if (matchTag==null) matchTag = "";
        if (cacheControl==null) cacheControl = "";
        if (cacheControl.equalsIgnoreCase("no-cache")==false){
            if (eTag.equalsIgnoreCase(matchTag)){
                return true;
            }
            else{
              //Internet Explorer 6 uses "if-modified-since" instead of "if-none-match"
                matchTag = request.getHeader("if-modified-since");
                if (matchTag!=null){
                    for (String tag: matchTag.split(";")){
                        if (tag.trim().equalsIgnoreCase(date)){
                            return true;
                        }
                    }
                }

            }
        }

        return false;
    }


  //**************************************************************************
  //** getFields
  //**************************************************************************
  /** Returns an array of ServiceRequest.Fields by parsing the "fields"
   *  parameter in the HTTP request (e.g. "?fields=id,firstName,lastName").
   *  Note that fields may contain approved SQL functions (e.g. "min", "max",
   *  "count", "avg", "sum"). Fields with unsupported functions are ignored.
   */
    public Field[] getFields(){
        if (fields!=null) return fields;
        String fields = getParameter("fields").toString();


      //If fields are empty, simply return an ampty array
        if (fields==null || fields.length()==0){
            this.fields = new Field[0];
            return this.fields;
        }


      //Parse the fields
        try{

          //
            ArrayList<Field> arr = new ArrayList<>();

          //Parse fields parameter using JSQLParser
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            Select select = (Select) parserManager.parse(new StringReader("SELECT " + fields + " FROM T"));
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();


          //Iterate through the fields and update the array
            Iterator<SelectItem> it = plainSelect.getSelectItems().iterator();
            while (it.hasNext()){

                SelectExpressionItem si = (SelectExpressionItem) it.next();
                Expression expression = si.getExpression();
                String alias = si.getAlias()==null ? null : si.getAlias().getName();
                boolean addField = true;


              //Check if the expression contains a function
                String functionName = null;
                try{
                    Function f = (Function) expression;
                    functionName = f.getName().toLowerCase();

                }
                catch(Exception e){
                    try{
                        SubSelect ss = (SubSelect) expression;
                        functionName = "SELECT";
                    }
                    catch(Exception ex){
                    }
                }



              //If the expression contains a function, check whether the function
              //is allowed.
                if (functionName!=null){
                    addField = false;

                    if (functionName.startsWith("st_")){
                        addField = true;
                    }
                    else{
                        for (String fn : approvedFunctions){
                            if (fn.equals(functionName)){
                                addField = true;
                                break;
                            }
                        }
                    }
                }


                if (addField){
                    Field field = new Field(expression.toString());
                    field.setAlias(alias);
                    if (functionName!=null) field.isFunction(true);
                    arr.add(field);
                }
            }



            this.fields = arr.toArray(new Field[arr.size()]);

        }
        catch(Throwable e){
          //JSQLParser doesn't like one of the fields or JSqlParser is missing
          //from the class path. If so, fallback to the JavaXT SQL parser.
            this.fields = getFields(fields);
        }

        return this.fields;
    }


  //**************************************************************************
  //** getFields
  //**************************************************************************
  /** Fallback for JSqlParser
   */
    private Field[] getFields(String fields){
        javaxt.sql.Parser sqlParser = new javaxt.sql.Parser("SELECT " + fields + " FROM T");
        ArrayList<Field> arr = new ArrayList<>();
        for (javaxt.sql.Parser.SelectStatement stmt : sqlParser.getSelectStatements()){
            Field field = new Field(stmt.getField());
            field.setAlias(stmt.getAlias());
            field.isFunction(stmt.isFunction());
            arr.add(field);
        }
        return arr.toArray(new Field[arr.size()]);
    }



  //**************************************************************************
  //** getFilter
  //**************************************************************************
  /** Returns a ServiceRequest.Filter by parsing the query string associated
   *  this request. Examples:
   *  <ul>
   *  <li>http://localhost?id=1 (id = 1)</li>
   *  <li>http://localhost?id>=1 (id > 1)</li>
   *  <li>http://localhost?id!=1 (id &lt;> 1)</li>
   *  <li>http://localhost?id=1,2,3 (id in (1,2,3))</li>
   *  <li>http://localhost?id!=1,2,3 (id not in (1,2,3))</li>
   *  <li>http://localhost?active=true (active = true)</li>
   *  <li>http://localhost?name='Bob' (name = 'Bob')</li>
   *  <li>http://localhost?name='Bo%' (name like 'Bo%')</li>
   *  <li>http://localhost?name!='Bo%' (name not like 'Bo%')</li>
   *  </ul>
   *  Note that the operators can be transposed without altering the filter
   *  (e.g. ">=" is the same as "=>").
   *
   *  <p/>
   *  Alternatively, a Filter can be generated by parsing a "filter" parameter
   *  containing JSON object. In fact, if a "filter" parameter is provided, it
   *  will be used instead of the query string.
   */
    public Filter getFilter(){
        if (filter!=null) return filter;


      //Parse querystring
        HashMap<String, javaxt.utils.Value> params = new HashMap<>();
        if (hasParameter("filter")){

            String str = getParameter("filter").toString();
            if (str.startsWith("{") && str.endsWith("}")){
                JSONObject json = new JSONObject(str);
                for (String key : json.keySet()){
                    params.put(key, json.get(key));
                }
            }
            else{
                HashMap<String, List<String>> map = javaxt.utils.URL.parseQueryString(str);
                Iterator<String> it = map.keySet().iterator();
                while (it.hasNext()){
                    String key = it.next();
                    List<String> vals = map.get(key);
                    if (!vals.isEmpty()) params.put(key, new javaxt.utils.Value(vals.get(0)));
                }
            }
        }
        else{
            for (String key : getParameterNames()){
                params.put(key, getParameter(key));
            }
        }


      //Create filter
        filter = new Filter();
        Iterator<String> it = params.keySet().iterator();
        while (it.hasNext()){
            String key = it.next();
            String val = params.get(key).toString();

          //Skip reserved keywords
            key = key.trim();
            String k = key.toLowerCase();
            if (k.equals("fields") || k.equals("where") || k.equals("orderby") ||
                k.equals("limit") || k.equals("offset") || k.equals("page") ||
                k.equals("count") || k.equals("_") || k.isEmpty()){
                continue;
            }


          //Get characters before and after the "=" sign
            String a = key.length()==1 ? key : key.substring(key.length()-1);
            String b = val==null ? "" : val.substring(0, 1);



          //Get op and update key and val as needed
            String op;
            if (val==null) val = "null";
            if (val.contains(",") && !(val.startsWith("'") || val.startsWith("!'"))){

                op = "IN";
                if (b.equals("!")){
                    val = val.substring(1).trim();
                    op = "NOT IN";
                }
                if (a.equals("!")){
                    key = key.substring(0, key.length()-1);
                    op = "NOT IN";
                }


                StringBuilder str = new StringBuilder("(");
                int x = 0;
                String nullVal = null;
                for (String s : val.split(",")){
                    if (s.equalsIgnoreCase("NULL") || s.equalsIgnoreCase("!NULL")){
                        nullVal = s;
                    }
                    else{
                        if (x>0) str.append(",");
                        str.append(s);
                        x++;
                    }
                }
                str.append(")");

                if (nullVal!=null){
                    if (op.equals("IN")){
                        str.append(" OR ");
                        str.append(StringUtils.camelCaseToUnderScore(key));
                        str.append(" IS");
                        if (nullVal.startsWith("!")) str.append(" NOT");
                        str.append(" NULL");
                    }
                    else{ //not tested...
                        str.append(" AND ");
                        str.append(StringUtils.camelCaseToUnderScore(key));
                        str.append(" IS");
                        if (nullVal.startsWith("!")) str.append(" NOT");
                        str.append(" NULL");
                    }
                }

                val = str.toString();
            }
            else {

                if (a.equals("!") || b.equals("!")){
                    op = "<>";
                    if (b.equals("!")){
                        val = val.substring(1).trim();
                    }
                    if (a.equals("!")){
                        key = key.substring(0, key.length()-1);
                    }
                }

                else if (a.equals(">") || b.equals(">")){
                    op = ">=";
                    if (b.equals(">")){
                        val = val.substring(1).trim();
                    }
                    if (a.equals(">")){
                        key = key.substring(0, key.length()-1);
                    }
                }

                else if (a.equals("<") || b.equals("<")){
                    op = "<=";
                    if (b.equals("<")){
                        val = val.substring(1).trim();
                    }
                    if (a.equals("<")){
                        key = key.substring(0, key.length()-1);
                    }
                }

                else{
                    op = "=";
                }
            }


          //Special case for nulls
            if (val.equalsIgnoreCase("null")){
                if (op.equals("=")) op = "IS";
                if (op.equals("<>")) op = "IS NOT";
            }


          //Special case for like
            if (val.contains("%") && val.startsWith("'") && val.endsWith("'")){
                if (op.equals("=")) op = "LIKE";
                if (op.equals("<>")) op = "NOT LIKE";
            }


            if (op.equals("=")){
                if (val.toLowerCase().startsWith("startswith(") && val.endsWith(")")){
                    op = "LIKE";
                    val = "'" + val.substring(11, val.length()-1).replace("'", "''") + "%'";
                }
                else if (val.toLowerCase().startsWith("endswith(") && val.endsWith(")")){
                    op = "LIKE";
                    val = "'%" + val.substring(9, val.length()-1).replace("'", "''") + "'";
                }
                else if (val.toLowerCase().startsWith("contains(") && val.endsWith(")")){
                    op = "LIKE";
                    val = "'%" + val.substring(9, val.length()-1).replace("'", "''") + "%'";
                }
            }



            filter.set(key, op, val);
        }

        return filter;
    }


  //**************************************************************************
  //** getWhere
  //**************************************************************************
  /** Returns the value for the "where" parameter in the HTTP request.
   */
    public String getWhere(){
        return getParameter("where").toString();
    }


  //**************************************************************************
  //** getSort
  //**************************************************************************
  /** Returns an order by statement found in the request (e.g. "orderby" query
   *  string). The order by statement may be given as a comma delimited list
   *  of fields with optional sort direction for each field. Alternatively,
   *  the order by statement can be specified as a JSON array with a "property"
   *  and "direction" for each entry in the array. The order by statement is
   *  encapsulated as an instance of the Sort class.
   */
    public Sort getSort(){
        if (sort!=null) return sort;

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        String orderBy = getParameter("orderby").toString();


        if (orderBy!=null){
            if (orderBy.startsWith("[") && orderBy.endsWith("]")){
                //Example: &sort=[{"property":"dob","direction":"ASC"}]
                JSONArray arr = new JSONArray(orderBy);
                for (int i=0; i<arr.length(); i++){
                    JSONObject json = arr.get(i).toJSONObject();
                    String key = json.get("property").toString();
                    String dir = json.get("direction").toString();
                    if (key!=null){
                        if (dir==null) dir = "ASC";
                        fields.put(key, dir);
                    }
                }
            }
            else{
                for (String field : orderBy.split(",")){
                    field = field.trim();
                    if (field.length()>0){

                        String a, b;
                        String f = field.toUpperCase();
                        if (f.endsWith(" ASC") || f.endsWith(" DESC")){
                            int x = field.lastIndexOf(" ");
                            a = field.substring(0, x).trim();
                            b = field.substring(x).trim();
                        }
                        else{
                            a = field;
                            b = "ASC";
                        }

                        a = StringUtils.camelCaseToUnderScore(a).toUpperCase();
                        fields.put(a, b);
                    }
                }
            }
        }

        sort = new Sort(fields);
        return sort;
    }


  //**************************************************************************
  //** Sort Class
  //**************************************************************************
  /** Used to encapsulate an "order by" statement
   */
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


  //**************************************************************************
  //** Field Class
  //**************************************************************************
  /** Used to encapsulate an entry in a "select" statement
   */
    public class Field {
        private String col;
        private String table;
        private String alias;
        private boolean isFunction;

        public Field(String field){
            col = field;
            isFunction = false;
        }

        public void setAlias(String alias){
            this.alias = alias;
        }

        public boolean isFunction(){
            return isFunction;
        }

        public void isFunction(boolean isFunction){
            this.isFunction = isFunction;
        }

        public String toString(){
            String str = col;
            if (table!=null) str = table + "." + str;
            if (alias!=null) str += " as " + alias;
            return str;
        }

        public boolean equals(Object obj){
            if (obj instanceof String){
                String str = (String) obj;
                if (str.equalsIgnoreCase(col)) return true;
                if (str.equalsIgnoreCase(alias)) return true;
                if (str.equalsIgnoreCase(this.toString())) return true;
            }
            else if (obj instanceof Field){
                Field field = (Field) obj;
                return field.equals(this.toString());
            }
            return false;
        }
    }


  //**************************************************************************
  //** Filter Class
  //**************************************************************************
  /** A Filter consists of one or more Filter.Items. Each Filter.Item consists
   *  of a field/column (e.g. "id"), an operator (e.g. "="), and a value
   *  constraint (e.g. "1"). Together, the Items can be joined to generate a
   *  "where" clause for a SQL query.
   */
    public class Filter {
        private LinkedHashMap<String, Item> items = new LinkedHashMap<>();

        public class Item {
            private String col;
            private String op;
            private javaxt.utils.Value val;
            private Item(String col, String op, javaxt.utils.Value val){
                this.col = col;
                this.op = op;
                this.val = val;
            }
            public String getField(){
                return col;
            }
            public String getOperation(){
                return op;
            }
            public javaxt.utils.Value getValue(){
                return val;
            }
            public String toString(){
                return StringUtils.camelCaseToUnderScore(col) + " " + op + " " + val;
            }
            public JSONObject toJson(){
                JSONObject json = new JSONObject();
                json.set("col", col);
                json.set("op", op);
                json.set("val", val);
                return json;
            }
        }

        protected Filter(){}

        public void set(String col, Object val){
            set(col, "=", val);
        }

        public void set(String col, String op, Object val){
            String key = col.toLowerCase();
            javaxt.utils.Value v = (val instanceof javaxt.utils.Value) ?
                    (javaxt.utils.Value) val : new javaxt.utils.Value(val);

            if (v.isNull()){
                items.remove(key);
            }
            else{
                items.put(key, new Item(col, op, v));
            }
        }


        public javaxt.utils.Value get(String col){
            Item item = items.get(col.toLowerCase());
            if (item!=null){
                return item.val;
            }
            return new javaxt.utils.Value(null);
        }


        public boolean isEmpty(){
            return items.isEmpty();
        }

        public Item[] getItems(){
            ArrayList<Item> arr = new ArrayList<>();
            Iterator<String> it = items.keySet().iterator();
            while (it.hasNext()){
                String key = it.next();
                Item item = items.get(key);
                arr.add(item);
            }
            return arr.toArray(new Item[arr.size()]);
        }

        public JSONArray toJson(){
            JSONArray arr = new JSONArray();
            for (Item item : getItems()) arr.add(item.toJson());
            return arr;
        }
    }

}