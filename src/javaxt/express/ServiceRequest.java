package javaxt.express;

import java.util.*;
import java.io.StringReader;

//JavaXT includes
import javaxt.json.*;
import javaxt.sql.Model;
import javaxt.express.utils.StringUtils;
import static javaxt.utils.Console.console;
import javaxt.http.servlet.ServletException;
import javaxt.http.servlet.HttpServletRequest;

//JSQLParser includes
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


    private Map<String, String> keywords = Map.ofEntries(
        Map.entry("fields", "fields"),
        Map.entry("orderby", "orderby"),
        Map.entry("limit", "limit"),
        Map.entry("offset", "offset"),

      //Used by the list method in the WebService class
        Map.entry("format", "format"),
        Map.entry("count", "count"),

      //Legacy - may be removed in the future
        Map.entry("filter", "filter"),
        Map.entry("where", "where"),
        Map.entry("page", "page")
    );


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



      //Parse payload if it contains URL encoded form data
        String contentType = request.getContentType();
        if (contentType!=null){
            if (contentType.equalsIgnoreCase("application/x-www-form-urlencoded")){
                byte[] b = getPayload();
                if (b!=null && b.length>0){
                    try{
                        LinkedHashMap<String, List<String>> params =
                        javaxt.utils.URL.parseQueryString(new String(b, "UTF-8"));
                        Iterator<String> it = params.keySet().iterator();
                        while (it.hasNext()){
                            String key = it.next();
                            List<String> values = params.get(key);
                            List<String> currValues = this.parameters.get(key);
                            if (currValues==null){
                                this.parameters.put(key, values);
                            }
                            else{
                                for (String val : values){
                                    currValues.add(val);
                                }
                            }
                        }
                    }
                    catch(Exception e){}
                }
            }
        }



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
            if (path.startsWith("/")) path = path.substring(1);
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
        if (name!=null && name.length()>0){
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
   *  If the value is an empty string or "null" then a null value is returned.
   *  @param key Query string parameter name. Performs a case insensitive
   *  search for the keyword.
   */
    public javaxt.utils.Value getParameter(String key){
        Object val = null;
        if (key!=null){


          //Search the URL querystring
            List<String> parameters = getParameter(key, this.parameters);
            if (parameters!=null){
                LinkedHashSet<String> vals = new LinkedHashSet<>();
                for (String v : parameters){
                    if (v.length()>0){
                        if (v.equalsIgnoreCase("null")) v = null;
                    }
                    else{
                        v = null;
                    }
                    if (v!=null) vals.add(v);
                }
                if (!vals.isEmpty()){
                    if (vals.size()==1){
                        val = vals.iterator().next();
                    }
                    else{
                        val = vals.toArray(new String[vals.size()]);
                    }
                }
            }


          //If nothing found in the querystring, search the payload as directed
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
            if (k.equals(getKeyword("offset")) ||
                k.equals(getKeyword("limit")) ||
                k.equals(getKeyword("page"))){
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
        LinkedHashSet<String> keys = new LinkedHashSet<>();
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
        offset = getParameter(getKeyword("offset")).toLong();
        limit = getParameter(getKeyword("limit")).toLong();
        Long page = getParameter(getKeyword("page")).toLong();
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
  //** getFormat
  //**************************************************************************
  /** Returns the value of the "format" parameter in the request.
   */
    protected String getFormat(){
        String format = getParameter(getKeyword("format")).toString();
        if (format==null) return "";
        else return format.toLowerCase();
    }


  //**************************************************************************
  //** getCount
  //**************************************************************************
  /** Returns the value of the "count" parameter in the request.
   */
    protected boolean getCount(){
        Boolean count = getParameter(getKeyword("count")).toBoolean();
        if (count==null) return false;
        else return count;
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
                    json = new JSONObject(new String(b, "UTF-8"));
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
  /** Returns the credentials associated with an HTTP request. The credentials
   *  will vary based on the security authentication scheme used to
   *  authenticate clients (e.g. "BASIC", "DIGEST", "NTLM", etc). In the case
   *  of "BASIC" authentication, the credentials typically include a username
   *  and password. In the case of "NTLM" authentication, the credentials may
   *  only contain a username. What is actually returned is up to the
   *  javaxt.http.servlet.Authenticator used to authenticate the request.
   */
    public String[] getCredentials(){
        return request.getCredentials();
    }


  //**************************************************************************
  //** authenticate
  //**************************************************************************
  /** Used to authenticate a client request. Authentication is performed by a
   *  javaxt.http.servlet.Authenticator. If no Authenticator is defined or if
   *  the Authenticator fails to authenticate the client, this method throws a
   *  ServletException.
   */
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
   */
    public Field[] getFields(){
        if (fields!=null) return fields;
        String fields = getParameter(getKeyword("fields")).toString();


      //If fields are empty, simply return an ampty array
        if (fields==null || fields.length()==0){
            this.fields = new Field[0];
            return this.fields;
        }


      //Parse the fields
        this.fields = getFields(fields);

        return this.fields;
    }


  //**************************************************************************
  //** getFields
  //**************************************************************************
  /** Used to parse a given String into an array of Fields.
   *  @param fields A comma delimited list of fields (e.g. "id,firstName,lastName")
   */
    public Field[] getFields(String fields){
        ArrayList<Field> arr = new ArrayList<>();

        try{

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



                String column = expression.toString();
                boolean isFunction = functionName!=null;
                if (!isFunction) column = StringUtils.camelCaseToUnderScore(column);

                Field field = new Field(column);
                field.setAlias(alias);
                field.isFunction(isFunction);
                field.setFunctionName(functionName);
                arr.add(field);
            }

        }
        catch(Throwable e){

          //JSQLParser doesn't like one of the fields or JSqlParser is missing
          //from the class path. If so, fallback to the JavaXT SQL parser.
            arr.clear();
            javaxt.sql.Parser sqlParser = new javaxt.sql.Parser("SELECT " + fields + " FROM T");
            for (javaxt.sql.Parser.SelectStatement stmt : sqlParser.getSelectStatements()){
                String column = stmt.getField();
                boolean isFunction = stmt.isFunction();
                if (!isFunction) column = StringUtils.camelCaseToUnderScore(column);

                Field field = new Field(column);
                field.setAlias(stmt.getAlias());
                field.isFunction(isFunction);
                arr.add(field);
            }
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
        LinkedHashMap<String, javaxt.utils.Value> params = new LinkedHashMap<>();
        if (hasParameter(getKeyword("filter"))){

            String str = getParameter(getKeyword("filter")).toString();
            if (str.startsWith("{") && str.endsWith("}")){
                JSONObject json = new JSONObject(str);
                for (String key : json.keySet()){
                    params.put(key, json.get(key));
                }
            }
            else{
                LinkedHashMap<String, List<String>> map = javaxt.utils.URL.parseQueryString(str);
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
        HashSet<String> reservedKeywords = getKeywords();
        Iterator<String> it = params.keySet().iterator();
        while (it.hasNext()){
            String key = it.next().trim();
            if (key.isEmpty()) continue;
            if (key.equals("_")) continue;
            if (reservedKeywords.contains(key.toLowerCase())) continue;


          //Parse val
            String[] vals;
            javaxt.utils.Value v = params.get(key);
            if (v.isNull()){
                vals = new String[]{null};
            }
            else{
                Object o = v.toObject();
                if (v.isArray()){
                    vals = (String[]) o;
                }
                else{
                    if (o instanceof JSONArray){
                        JSONArray arr = (JSONArray) o;
                        vals = new String[arr.length()];
                        for (int i=0; i<vals.length; i++){
                            vals[i] = arr.get(i).toString();
                        }
                    }
                    else{
                        vals = new String[]{o.toString()};
                    }
                }
            }


          //Set filter
            for (String val : vals){


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

                        op = ">="; //add "=" (legacy: assumes params are from url)
                        if (parseJson && json!=null){
                            if (json.has(key)) op = ">";
                        }

                        if (b.equals(">")){
                            val = val.substring(1).trim();
                        }
                        if (a.equals(">")){
                            key = key.substring(0, key.length()-1);
                        }
                    }

                    else if (a.equals("<") || b.equals("<")){

                        op = "<="; //add "=" (legacy: assumes params are from url)
                        if (parseJson && json!=null){
                            if (json.has(key)) op = "<";
                        }

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


                String col = StringUtils.camelCaseToUnderScore(key);
                filter.add(col, op, val);
            }
        }

        return filter;
    }


  //**************************************************************************
  //** getWhere
  //**************************************************************************
  /** Returns the value for the "where" parameter in the HTTP request.
   */
    public String getWhere(){
        return getParameter(getKeyword("where")).toString();
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
        String orderBy = getParameter(getKeyword("orderby")).toString();


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
  //** getKeywords
  //**************************************************************************
  /** Returns a copy of all the keywords used to identify parameters (e.g.
   *  "fields", "orderby", "offset", "limit", etc)
   */
    public HashSet<String> getKeywords(){
        HashSet<String> reservedKeywords = new HashSet<>();
        for (String key : keywords.keySet()){
            reservedKeywords.add(getKeyword(key).toLowerCase());
        }
        return reservedKeywords;
    }


  //**************************************************************************
  //** setKeyword
  //**************************************************************************
  /** Used to add or update an entry in the keyword map. For example, the
   *  default keyword used to identify fields in the getFields() method is
   *  "fields" and the default keyword used to identify sorting in getSort()
   *  if "orderby". This method provides a mechanism for overridding these
   *  defaults. For example, suppose in your application you wish to use
   *  "columns" instead of "fields" for the getFields() method. You would set
   *  the keyword to "columns" and type to "fields".
   *  @param keyword A parameter keyword.
   *  @param type What the given parameter should map to (e.g. "fields")
   */
    public void setKeyword(String keyword, String type){
        if (keyword==null) return;
        keyword = keyword.trim();
        if (keyword.isEmpty()) return;
        keywords.put(type.toLowerCase(), keyword.toLowerCase());
    }


  //**************************************************************************
  //** getKeyword
  //**************************************************************************
  /** Returns a keyword for a given type (see setKeyword for more information)
   */
    public String getKeyword(String type){
        if (type==null) return null;
        return keywords.get(type.toLowerCase());
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
        private String functionName;

        public Field(String field){
            col = field;
            isFunction = false;
        }

        public String getColumn(){
            return col;
        }

        public void setAlias(String alias){
            this.alias = alias;
        }

        public String getAlias(){
            return alias;
        }

        public boolean isFunction(){
            return isFunction;
        }

        public void isFunction(boolean isFunction){
            this.isFunction = isFunction;
        }

        public void setFunctionName(String functionName){
            this.functionName = functionName;
        }

        public String getFunctionName(){
            return functionName;
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
        private LinkedHashMap<String, ArrayList<Item>> items = new LinkedHashMap<>();

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
            public void setField(String col){
                this.col = col;
            }
            public String getOperation(){
                return op;
            }
            public void setOperation(String op){
                this.op = op;
            }
            public javaxt.utils.Value getValue(){
                return val;
            }
            public void setValue(javaxt.utils.Value val){
                if (val==null) val = new javaxt.utils.Value(null);
                this.val = val;
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

      /** Used to add, update, or remove an item in the filter. If an entry
       *  does not exist for a given col, a new item is created. If an entry
       *  does exist it is update or deleted, depending on whether val is null.
       */
        public void set(String col, String op, Object val){
            String key = col.toLowerCase();
            javaxt.utils.Value v = (val instanceof javaxt.utils.Value) ?
                    (javaxt.utils.Value) val : new javaxt.utils.Value(val);

            if (v.isNull()){
                items.remove(key);
            }
            else{
                ArrayList<Item> arr = new ArrayList<>();
                arr.add(new Item(col, op, v));
                items.put(key, arr);
            }
        }

      /** Used to add an entry to the filter. Note that this may result in
       *  multiple entries for a given col. Use with caution.
       */
        public void add(String col, String op, Object val){
            String key = col.toLowerCase();
            javaxt.utils.Value v = (val instanceof javaxt.utils.Value) ?
                    (javaxt.utils.Value) val : new javaxt.utils.Value(val);
            if (!v.isNull()){
                ArrayList<Item> arr = items.get(key);
                if (arr==null){
                    arr = new ArrayList<>();
                    items.put(key, arr);
                }
                arr.add(new Item(col, op, v));
            }
        }

      /** Returns a value for a given key in the filter. Always returns a
       *  javaxt.utils.Value object. Check the isNull() method to test for null
       *  values and the isArray() method to test if the javaxt.utils.Value
       *  object contains multiple values. Example:
       <pre>
            javaxt.utils.Value val = item.get("test");
            if (val.isNull()){
                console.log(val);
            }
            else{
                if (val.isArray()){
                    javaxt.utils.Value[] vals = (javaxt.utils.Value[]) val.toObject();
                    for (javaxt.utils.Value v : vals){
                        console.log(v);
                    }
                }
                else{
                    console.log(val);
                }
            }
       </pre>
       */
        public javaxt.utils.Value get(String col){
            ArrayList<Item> arr = items.get(col.toLowerCase());
            if (arr==null || arr.isEmpty()){
                return new javaxt.utils.Value(null);
            }
            else{
                if (arr.size()==1){
                    Item item = arr.get(0);
                    if (item!=null){
                        return item.val;
                    }
                    return new javaxt.utils.Value(null);
                }
                else{
                    javaxt.utils.Value[] vals = new javaxt.utils.Value[arr.size()];
                    for (int i=0; i<vals.length; i++){
                        vals[i] = arr.get(i).val;
                    }
                    return new javaxt.utils.Value(vals);
                }
            }
        }


      /** Used to remove an item from the filter
       */
        public void remove(String col){
            String key = col.toLowerCase();
            items.remove(key);
        }


      /** Used to remove all the items from the filter
       */
        public void removeAll(){
            items.clear();
        }


      /** Returns true if the filter is empty
       */
        public boolean isEmpty(){
            return items.isEmpty();
        }


      /** Returns all the items in the filter
       */
        public Item[] getItems(){
            ArrayList<Item> arr = new ArrayList<>();
            Iterator<String> it = items.keySet().iterator();
            while (it.hasNext()){
                String key = it.next();
                for (Item item : items.get(key)){
                    arr.add(item);
                }
            }
            return arr.toArray(new Item[arr.size()]);
        }


      /** Returns a JSON representation of the filter
       */
        public JSONArray toJson(){
            JSONArray arr = new JSONArray();
            for (Item item : getItems()) arr.add(item.toJson());
            return arr;
        }
    }


  //**************************************************************************
  //** getSelectStatement
  //**************************************************************************
  /** Returns a SQL "select" statement for the current request. Compiles the
   *  select statement using an array of Fields returned by the getFields()
   *  method. Prepends a table name to each field, if given. If no fields are
   *  found in the request, a "select *" statement is returned. Otherwise, a
   *  select statement is returned, starting with "select ".
   *
   *  @param tableName If given, fields that are not functions are prepended
   *  with a table name. This parameter is optional.
   */
    public String getSelectStatement(String tableName){
        StringBuilder sql = new StringBuilder("select ");
        Field[] fields = getFields();
        if (fields==null || fields.length==0) sql.append("*");
        else{
            for (int i=0; i<fields.length; i++){
                if (i>0) sql.append(", ");
                Field field = fields[i];
                if (field.isFunction()){
                    sql.append(field.toString());
                }
                else{
                    if (tableName!=null && !tableName.isEmpty()){
                        sql.append(tableName + ".");
                    }
                    sql.append(StringUtils.camelCaseToUnderScore(field.getColumn()));
                    String alias = field.getAlias();
                    if (alias!=null && !alias.isBlank()){
                        sql.append(" as ");
                        sql.append(alias.trim());
                    }
                }
            }
        }
        return sql.toString();
    }


  //**************************************************************************
  //** getSelectStatement
  //**************************************************************************
  /** Returns a SQL "select" statement for the current request. Compiles the
   *  "select" statement using using an array of Fields returned by the
   *  getFields() method. Prepends a table name to each field by mapping
   *  fields to models.
   *
   *  @param model One or more Model classes used to identify fields. If a
   *  field is matched to a model the field name (column name) is prepended
   *  with a table name. Order is important.
   */
    public String getSelectStatement(Class... model){

      //Get fields
        Field[] fields = getFields();
        if (fields==null) fields = new Field[0];


      //Get models
        ArrayList<Class> models = new ArrayList<>();
        for (Class cls : model){
            if (Model.class.isAssignableFrom(cls)){
                models.add(cls);
            }
            else{
                throw new IllegalArgumentException(
                cls.getSimpleName() + " is not a javaxt.sql.Model");
            }
        }


      //If there are no models, call the other getSelectStatement() method
        if (models.isEmpty()){
            HashMap<String, Object> tablesAndFields = null;
            return "select " + getSelectStatement(tablesAndFields);
        }


      //Compile "select" statement
        String[] select = new String[fields.length];
        ArrayList<String> unmatchedTables = new ArrayList<>();
        for (Class cls : models){
            try{
                int numMatches = 0;
                HashMap<String, Object> tablesAndFields = WebService.getTableAndFields(cls);
                String[] selectStatements = getSelectStatements(fields, tablesAndFields);
                for (int i=0; i<selectStatements.length; i++){
                    String selectStatement = selectStatements[i];
                    if (selectStatement==null) continue;
                    if (select[i]==null){
                        select[i] = selectStatement;
                        numMatches++;
                    }
                }

                if (numMatches==0){
                    String tableName = (String) tablesAndFields.get("tableName");
                    unmatchedTables.add(tableName);
                }

            }
            catch(Exception e){}
        }



        StringBuilder sql = new StringBuilder();
        for (String s : select){
            if (s==null) continue;
            if (sql.length()>0) sql.append(", ");
            sql.append(s);
        }


        if (sql.length()==0){
            if (models.size()>1){
                for (String tableName : unmatchedTables){
                    if (sql.length()>0) sql.append(", ");
                    sql.append(tableName + ".*");
                }
                return "select " + sql.toString();
            }
            else{
                return "select *";
            }
        }
        else{
            return "select " + sql.toString();
        }
    }


  //**************************************************************************
  //** getSelectStatement
  //**************************************************************************
    protected String getSelectStatement(HashMap<String, Object> tablesAndFields){
        StringBuilder sql = new StringBuilder();
        for (String select : getSelectStatements(getFields(), tablesAndFields)){
            if (select==null) continue;
            if (sql.length()>0) sql.append(", ");
            sql.append(select);
        }
        if (sql.length()==0) return "*";
        else return sql.toString();
    }


  //**************************************************************************
  //** getSelectStatements
  //**************************************************************************
  /** Used to match fields to columns in the database. Returns an array with
   *  the exact same size as the given fields array. Note that entries in the
   *  array may contain null values, indicating that the field was not mapped.
   */
    private String[] getSelectStatements(Field[] fields, HashMap<String, Object> tablesAndFields){
        if (fields==null || fields.length==0) return new String[0];

        String[] select = new String[fields.length];
        for (int i=0; i<fields.length; i++){
            Field field = fields[i];

            if (field.isFunction()){
                select[i] = field.toString();
            }
            else{


              //Find table that corresponds to the field
                String tableName = null;
                if (tablesAndFields!=null){
                    String col = field.getColumn();

                    HashMap<String, String> fieldMap = (HashMap<String, String>) tablesAndFields.get("fieldMap");
                    Iterator<String> it = fieldMap.keySet().iterator();
                    while (it.hasNext()){
                        String fieldName = it.next();
                        String columnName = fieldMap.get(fieldName);
                        if (col.equalsIgnoreCase(fieldName) || col.equalsIgnoreCase(columnName)){
                            tableName = (String) tablesAndFields.get("tableName");
                            break;
                        }
                    }



                  //If tablesAndFields are given, yet we can't find a table for
                  //the field, and field is not a function, is it a valid field?
                    if (tableName==null) continue;
                }



              //Add field, along with the table name and alias
                StringBuilder sql = new StringBuilder();
                if (tableName!=null && !tableName.isEmpty()){
                    sql.append(tableName + ".");
                }
                sql.append(StringUtils.camelCaseToUnderScore(field.getColumn()));
                String alias = field.getAlias();
                if (alias!=null && !alias.isBlank()){
                    sql.append(" as ");
                    sql.append(alias.trim());
                }
                select[i] = sql.toString();
            }
        }

        return select;
    }


  //**************************************************************************
  //** getWhereStatement
  //**************************************************************************
  /** Returns a SQL "where" statement for the current request. Compiles the
   *  "where" statement using Filter class returned by the getFilter() method.
   *  If the Filter is empty, will use raw value for the "where" parameter
   *  returned by the getWhere() method instead.
   *
   *  Returns an empty string if the Filter is empty and the "where" parameter
   *  is not defined. Otherwise, a where statement is returned, starting with
   *  a white space " " for convenience.
   *
   *  @param c Model classes used to validate fields in the filter. This
   *  parameter is optional.
   */
    public String getWhereStatement(Class... c){
        ArrayList<HashMap<String, Object>> arr = new ArrayList<>();
        try{
            for (Class cls : c){
                if (Model.class.isAssignableFrom(cls)){
                    try{
                        arr.add(WebService.getTableAndFields(cls));
                    }
                    catch(Exception e){}
                }
            }
        }
        catch(Exception e){}
        String where = getWhereStatement(arr);
        if (where!=null) return " where " + where;
        else return "";
    }

    protected String getWhereStatement(HashMap<String, Object> tablesAndFields){
        ArrayList<HashMap<String, Object>> arr = new ArrayList<>();
        arr.add(tablesAndFields);
        return getWhereStatement(arr);
    }

    private String getWhereStatement(ArrayList<HashMap<String, Object>> tablesAndFields){
        String where = null;
        Filter filter = getFilter();
        if (!filter.isEmpty()){


            ArrayList<String> a2 = new ArrayList<>();
            Iterator<String> it = filter.items.keySet().iterator();
            while (it.hasNext()){
                ArrayList<String> arr = new ArrayList<>();
                String key = it.next();
                for (Filter.Item item : filter.items.get(key)){
                    String name = item.getField();
                    String op = item.getOperation();
                    String v = item.getValue().toString();


                  //Check if the column name is a function
                    Field[] fields = getFields(name);
                    Field field = null;
                    if (fields!=null && fields.length>0){
                        field = fields[0];
                        if (field.isFunction()){
                            arr.add("(" + item.toString() + ")");
                            continue;
                        }
                    }



                    if (tablesAndFields==null || tablesAndFields.isEmpty()){

                      //Set column name
                        String col;
                        if (field!=null) col = field.getColumn();
                        else col = StringUtils.camelCaseToUnderScore(name);

                      //Update value
                        if (v!=null && v.contains(" ")){
                            if (!(v.startsWith("'") && v.endsWith("'"))){
                                v = "'" + v.replace("'","''") + "'";
                            }
                        }

                        arr.add("(" + col + " " + op + " " + v + ")");

                    }
                    else{


                      //Check if the column name corresponds to a field in the
                      //database. If so, append table name to the column.
                        boolean foundField = false;
                        for (HashMap<String, Object> map : tablesAndFields){

                            String tableName = (String) map.get("tableName");
                            HashMap<String, String> fieldMap = (HashMap<String, String>) map.get("fieldMap");
                            HashSet<String> stringFields = (HashSet<String>) map.get("stringFields");
                            HashSet<String> arrayFields = (HashSet<String>) map.get("arrayFields");


                            Iterator<String> i2 = fieldMap.keySet().iterator();
                            while (i2.hasNext()){
                                String fieldName = i2.next();
                                String columnName = fieldMap.get(fieldName);
                                if (name.equalsIgnoreCase(fieldName) || name.equalsIgnoreCase(columnName)){
                                    foundField = true;

                                  //Wrap value is single quote as needed
                                    if (v!=null && stringFields.contains(fieldName)){
                                        if (!(v.startsWith("'") && v.endsWith("'"))){
                                            if (op.equals("IN")){
                                                //TODO: split by commas and add quotes
                                            }
                                            else{
                                                v = "'" + v.replace("'","''") + "'";
                                            }
                                        }
                                    }


                                  //Compile statement and update arr
                                    if (arrayFields.contains(fieldName)){

                                      //Special case for arrays
                                        if (op.equals("=")){
                                            arr.add("(" + v + " = ANY(" + tableName + "." + columnName + "))");
                                        }
                                        else if (op.equals("IN")){
                                            if (v==null){

                                            }
                                            else{

                                              //Split up "in" statement with a bunch of "or" statements
                                                if (v.startsWith("(") && v.endsWith(")")){
                                                    v = v.substring(1, v.length()-1);
                                                }
                                                StringBuilder str = new StringBuilder("(");
                                                String[] a = v.split(","); //very weak!
                                                for (int i=0; i<a.length; i++){
                                                    if (i>0) str.append(" OR ");
                                                    String s = a[i];
                                                    if (stringFields.contains(fieldName)){
                                                        if (!(s.startsWith("'") && s.endsWith("'"))){
                                                            s = "'" + s.replace("'","''") + "'";
                                                        }
                                                    }
                                                    str.append("(" + s + " = ANY(" + tableName + "." + columnName + "))");
                                                }
                                                str.append(")");
                                                arr.add(str.toString());
                                            }
                                        }
                                        else{
                                            //Not sure what other array operations we can support...
                                        }

                                    }
                                    else{

                                      //Most statements are generated here
                                        arr.add("(" + tableName + "." + columnName + " " + op + " " + v + ")");

                                    }


                                    break;
                                }
                            }
                            if (foundField) break;
                        }
                        //console.log(foundField, name, tableName);
                    }
                }

                if (!arr.isEmpty()){
                    if (arr.size()>1){
                        a2.add("(" + String.join(" and ", arr) + ")");
                    }
                    else{
                        a2.add(arr.get(0));
                    }
                }
            }


            if (!a2.isEmpty()){
                where = String.join(" and ", a2);
            }
        }


      //Fallback to the where parameter in the request (legacy)
        if (where==null){
            where = getWhere();
            if (where!=null){
                where = where.trim();
                if (where.toLowerCase().startsWith("where")){
                    where = where.substring(5).trim();
                    if (where.isEmpty()) where = null;
                }
            }
        }

        //console.log(where);
        return where;
    }


  //**************************************************************************
  //** getOrderByStatement
  //**************************************************************************
  /** Returns a SQL order by statement for the current request. Compiles the
   *  order by statement using Sort class returned by the getSort() method.
   *  Returns an empty string if a sort was not defined. Otherwise, the order
   *  by statement returned, starting with a white space " " for convenience.
   */
    public String getOrderByStatement(){
        Sort sort = getSort();
        if (!sort.isEmpty()){
            StringBuilder sql = new StringBuilder();
            sql.append(" order by ");
            java.util.Iterator<String> it = sort.getKeySet().iterator();
            while (it.hasNext()){
                String colName = it.next();
                String direction = sort.get(colName);
                sql.append(colName);
                sql.append(" ");
                sql.append(direction);
                if (it.hasNext()) sql.append(", ");
            }
            return sql.toString();
        }
        return "";
    }


  //**************************************************************************
  //** getOffsetLimitStatement
  //**************************************************************************
  /** Returns a SQL offset and limit statement for the current request. These
   *  statements are used for pagination. Note that different database vendors
   *  use different keywords to specify offset and limit. The given Driver is
   *  used to determine which keywords to use. Returns an empty string if
   *  limit and is offset are not defined. Otherwise, the limit and/or offset
   *  statement is returned, starting with a white space " " for convenience.
   *  @param driver An instance of a javaxt.sql.Driver class. This parameter
   *  is optional.
   */
    public String getOffsetLimitStatement(javaxt.sql.Driver driver){
        if (driver==null) driver = new javaxt.sql.Driver("","","");

        StringBuilder sql = new StringBuilder();

      //Get offset
        Object offset = getOffset();
        if (offset!=null){
            Long x = (Long) offset;
            if (x<1) offset = "";
            else{
                StringBuilder str = new StringBuilder();
                str.append(" offset ");
                str.append(offset);

                if (driver.equals("Oracle")){
                    str.append(" rows"); //OFFSET 20 ROWS
                }

                offset = str.toString();
            }
        }
        else{
            offset = "";
        }


      //Get limit
        Object limit = getLimit();
        if (limit!=null){
            StringBuilder str = new StringBuilder();
            if (driver.equals("Oracle")){
                str.append(" fetch next ");
                str.append(limit);
                str.append(" only");
            }
            else { //PostgreSQL and H2
                str.append(" limit ");
                str.append(limit);
            }

            limit = str.toString();
        }
        else{
            limit = "";
        }


      //Append offset and limit
        if (driver.equals("H2")){
            sql.append(limit);
            sql.append(offset);
        }
        else{
            sql.append(offset);
            sql.append(limit);
        }

        return sql.toString();
    }

}