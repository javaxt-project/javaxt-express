package javaxt.express.ws;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.ServletException;
import javaxt.express.api.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubSelect;
import javaxt.json.*;

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
    private HashMap<String, List<String>> parameters;
    private Field[] fields;
    private Filter filter;
    private Sort sort;
    private Long limit;
    private Long offset;
    private Long id;
    private static String[] approvedFunctions = new String[]{
        "min", "max", "count", "avg", "sum"
    };
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate a new instance of this class.
   *  @param service The first "directory" found in the path.
   *  @param path The URL path, excluding the servlet context.
   */
    public ServiceRequest(String service, String path, HttpServletRequest request){
        
        this.service = service;
        this.request = request;
        this.url = new javaxt.utils.URL(request.getURL());
        this.parameters = url.getParameters();
        
        
      //Parse path
        if (path!=null){
            String[] arr = path.split("/");
            
            
          //Generate a method name using the request method and first "directory" 
          //in the path. Example: "GET /config/users" would yield the "getUsers"
          //from the "config" service.
            if (arr.length>1){
                String name = arr[1];
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
            
            

            java.util.ArrayList<String> str = new java.util.ArrayList<String>();
            for (int i=1; i<arr.length; i++){
                str.add(arr[i]);
            }
            this.path = str.toArray(new String[str.size()]);
        }
        
        
      //Get ID
        id = getPath(1).toLong();
        if (id==null) id = new javaxt.utils.Value(request.getParameter("id")).toLong();
        
        

        
        
      //Get offset and limit
        offset = getParameter("offset").toLong();
        limit = getParameter("limit").toLong();
        Long page = getParameter("page").toLong();
        if (offset==null && page!=null){
            if (limit==null) limit = 25L;
            offset = (page*limit)-limit;        
        }
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
            List<String> parameters = this.parameters.get(key.toLowerCase());
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
            List<String> parameters = this.parameters.get(key.toLowerCase());
            if (parameters!=null) return true;
        }
        return false;
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
        if (fields.isEmpty())  return null;
        
        
        try{
            
          //
            java.util.ArrayList<Field> arr = new java.util.ArrayList<Field>();
            
          //Parse fields parameter using JSQLParser
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            Select select = (Select) parserManager.parse(new StringReader("SELECT " + fields + " FROM T"));
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            
            
          //Iterate through the fields and update the array
            java.util.Iterator<SelectItem> it = plainSelect.getSelectItems().iterator();
            while (it.hasNext()){

                SelectExpressionItem si = (SelectExpressionItem) it.next();
                Expression expression = si.getExpression();
                String alias = si.getAlias().getName();
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
        java.util.ArrayList<Field> arr = new java.util.ArrayList<Field>();
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
        filter = new Filter(new JSONObject(getParameter("filter").toString()));
        return filter;
    }
    

  //**************************************************************************
  //** getWhere
  //**************************************************************************
    public String getWhere(){
        return getParameter("where").toString();
    }
    
    
  //**************************************************************************
  //** getOrderBy
  //**************************************************************************
  /** Used to parse the orderby parameters found the url query string.
   */
    public Sort getSort(){
        if (sort!=null) return sort;
        
        
        String orderBy = getParameter("orderby").toString();
        //TODO: &sort=[{"property":"dob","direction":"ASC"}]
        if (orderBy==null) return null;
        
        
        LinkedHashMap<String, String> fields = new LinkedHashMap<String, String>();
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
                    b = null;
                }
                
                fields.put(a, b);
            }
        }
        if (fields.isEmpty()) return null;
        
        sort = new Sort(fields);
        return sort;
    }
}