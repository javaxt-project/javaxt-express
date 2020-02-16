package javaxt.express;
import java.io.StringReader;
import java.util.*;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.ServletException;
import javaxt.json.*;
import javaxt.utils.Console;
import javaxt.express.utils.StringUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.select.*;

//******************************************************************************
//**  ServiceRequest
//******************************************************************************
/**
 *   Used to represent an HTTP request
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
    private static String[] approvedFunctions = new String[]{
        "min", "max", "count", "avg", "sum"
    };
    private Console console = new Console();


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
   *  "http://localhost/photos/config/user". In this example, the servlet path
   *  is "photos" and the service name is "config". Note that the servlet path
   *  is optional and may include multiple "directories". This method makes it
   *  easier to find the service name from the url.
   */
    public String getService(){
        return service;
    }


  //**************************************************************************
  //** getMethod
  //**************************************************************************
  /** Returns the method name from the http request. Service requests may
   *  include object/entity name in the path using the following convention:
   *  "http://localhost/servlet/service/object". For example
   *  "http://localhost/photos/config/user". In this example, the service name
   *  is "config" and the object/entity name is "user". If the http request
   *  method is "GET" then the method name is "getUser". If the http request
   *  method is "DELETE" then the method name is "deleteUser". If the http
   *  request method is "PUT" or "POST" then the method name is "saveUser".
   */
    public String getMethod(){
        return method;
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
  /** Used to set the url path
   *  @param path URL path, excluding servlet and service path
   */
    public void setPath(String path){
        if (path!=null){
            path = path.substring(1);
            boolean addPath = service==null;
            ArrayList<String> arr = new ArrayList<String>();
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


      //Get ID
        id = getPath(1).toLong();
        if (id==null) id = new javaxt.utils.Value(request.getParameter("id")).toLong();
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
  /** Returns the value of a specific variable supplied in the query string.
   *  @param key Query string parameter name. Performs a case insensitive
   *  search for the keyword.
   */
    public javaxt.utils.Value getParameter(String key){
        if (key!=null){
            List<String> parameters = getParameter(key, this.parameters);
            if (parameters!=null){
                String val = parameters.get(0).trim();
                if (val.length()>0) return new javaxt.utils.Value(val);
            }
            //request.getBody()
        }

        return new javaxt.utils.Value(null);
    }


  //**************************************************************************
  //** hasParameter
  //**************************************************************************
    public boolean hasParameter(String key){
        if (key!=null){
            List<String> parameters = getParameter(key, this.parameters);
            if (parameters!=null) return true;
        }
        return false;
    }


  //**************************************************************************
  //** setParameter
  //**************************************************************************
    public void setParameter(String key, String val){
        if (key!=null){
            key = key.toLowerCase();
            List<String> parameters = getParameter(key, this.parameters);



          //Special case for classes that override the hasParameter and
          //getParameter methods.
            if (parameters==null && hasParameter(key)){
                parameters = new ArrayList<String>();
                parameters.add(getParameter(key).toString());
                setParameter(key, parameters, this.parameters);
            }



          //Add or update value
            if (parameters==null){
                if (val!=null){
                    parameters = new ArrayList<String>();
                    parameters.add(val);
                    setParameter(key, parameters, this.parameters);
                }

            }
            else{
                parameters.set(0, val);
            }


          //Update offset and limit as needed
            if (key.equals("offset") || key.equals("limit") || key.equals("page")){
                updateOffsetLimit();
            }
        }
    }


  //**************************************************************************
  //** getParameterNames
  //**************************************************************************
    public String[] getParameterNames(){
        ArrayList<String> arr = new ArrayList<>();
        Iterator<String> it = this.parameters.keySet().iterator();
        while (it.hasNext()) arr.add(it.next());
        return arr.toArray(new String[arr.size()]);
    }


    private static List<String> getParameter(String key, HashMap<String, List<String>> parameters){
        return javaxt.utils.URL.getParameter(key, parameters);
    };

    private static void setParameter(String key, List<String> values, HashMap<String, List<String>> parameters){
        javaxt.utils.URL.setParameter(key, values, parameters);
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
    public Long getOffset(){
        return offset;
    }


  //**************************************************************************
  //** getLimit
  //**************************************************************************
    public Long getLimit(){
        return limit;
    }


  //**************************************************************************
  //** getRequest
  //**************************************************************************
    public HttpServletRequest getRequest(){
        return request;
    }


  //**************************************************************************
  //** getPayload
  //**************************************************************************
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
    public JSONObject getJson(){
        if (json==null){
            json = new JSONObject(new String(getPayload()));
        }
        return json;
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
  //** getFields
  //**************************************************************************
  /** Returns an array of fields
   */
    public Field[] getFields(){
        if (fields!=null) return fields;
        String fields = getParameter("fields").toString();
        if (fields==null || fields.length()==0) return null;


        try{

          //
            ArrayList<Field> arr = new ArrayList<Field>();

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
        catch(net.sf.jsqlparser.JSQLParserException e){
            //JSQLParser doesn't like one of the fields
            this.fields = getFields(fields);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        catch(Throwable e){
            System.err.println("Missing JSqlParser!");
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
    public Filter getFilter(){
        if (filter!=null) return filter;

        if (hasParameter("filter")){
            filter = new Filter(new JSONObject(getParameter("filter").toString()));
        }
        else{
            JSONObject filter = new JSONObject();
            Iterator<String> it = parameters.keySet().iterator();
            while (it.hasNext()){
                String key = it.next();
                String k = key.toLowerCase();
                if (k.equals("fields") || k.equals("where") || k.equals("orderby") ||
                    k.equals("limit") || k.equals("offset") || k.equals("page") ||
                    k.equals("count") || k.equals("_")){
                    continue;
                }
                filter.set(key, getParameter(key));
            }
            this.filter = new Filter(filter);
        }

        return filter;
    }


  //**************************************************************************
  //** getWhere
  //**************************************************************************
    public String getWhere(){
        return getParameter("where").toString();
    }


  //**************************************************************************
  //** getSort
  //**************************************************************************
  /** Used to parse the orderby parameters found the url query string.
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
                        field = field.toUpperCase();
                        if (field.endsWith(" ASC") || field.endsWith(" DESC")){
                            int x = field.lastIndexOf(" ");
                            a = field.substring(0, x).trim();
                            b = field.substring(x).trim();
                        }
                        else{
                            a = field;
                            b = "ASC";
                        }

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
    public class Filter {
        private ArrayList<Item> items;

        public class Item {
            String col;
            String op;
            String val;
            private Item(String col, String op, String val){
                this.col = col;
                this.op = op;
                this.val = val;
            }
            private Item(JSONObject item){
                col = item.get("col").toString();
                op = item.get("op").toString();
                val = item.get("val").toString();
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


        public Filter(JSONArray filters){
            items = new ArrayList<>();
            for (int i=0; i<filters.length(); i++){
                items.add(new Item(filters.get(i).toJSONObject()));
            }
        }

        public Filter(JSONObject filter){
            items = new ArrayList<>();
            java.util.Iterator<String> it = filter.keys();
            while (it.hasNext()){
                String key = it.next();
                String val = filter.get(key).toString();
                String op = "=";

                if (val.contains(",")){
                    if (val.startsWith("!")){
                        val = "(" + val.substring(1).trim() + ")";
                        op = "NOT IN";
                    }
                    else{
                        val = "(" + val + ")";
                        op = "IN";
                    }
                }
                else{
                    String str = val.substring(0, 2);
                    switch (str) {
                        case "<>":
                            op = str;
                            val = val.substring(2).trim();
                            break;
                        case "!=":
                            op = "<>";
                            val = val.substring(2).trim();
                            break;
                        case ">=":
                            op = str;
                            val = val.substring(2).trim();
                            break;
                        case "<=":
                            op = str;
                            val = val.substring(2).trim();
                            break;
                        default:


                            String s = val.substring(0, 1);
                            switch (s) {
                                case "=":
                                    op = s;
                                    val = val.substring(1).trim();
                                    break;
                                case ">":
                                    op = s;
                                    val = val.substring(1).trim();
                                    break;
                                case "!":
                                    op = "<>";
                                    val = val.substring(1).trim();
                                    break;
                                case "<":
                                    op = s;
                                    val = val.substring(1).trim();
                                    break;
                                default:

                                    break;
                            }


                            break;
                    }
                }


              //Special case for nulls
                if (val.equalsIgnoreCase("null")){
                    if (op.equals("=")) op = "IS";
                    if (op.equals("<>")) op = "IS NOT";
                }

                items.add(new Item(key, op, val));
            }
        }


        public boolean isEmpty(){
            return items.isEmpty();
        }

        public Item[] getItems(){
            return items.toArray(new Item[items.size()]);
        }

        public JSONArray toJson(){
            JSONArray arr = new JSONArray();
            for (Item item : items){
                arr.add(item.toJson());
            }
            return arr;
        }
    }

}