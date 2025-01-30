package javaxt.express.services;
import javaxt.express.utils.*;
import javaxt.express.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.SQLException;

import javaxt.sql.*;
import javaxt.json.*;
import static javaxt.utils.Console.console;

import net.sf.jsqlparser.parser.*;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.expression.LongValue;

//******************************************************************************
//**  QueryService
//******************************************************************************
/**
 *   WebService used to query a database. Queries are executed asynchronously.
 *   Clients submit a query and by executing an HTTP POST request to "/job"
 *   which, in turn returns a job ID. Clients can get job status and, when
 *   ready, the query results by executinig an HTTP GET request to "/job/{ID}".
 *   Query results maybe encapsulated using csv, tsv, jsv, or json (default).
 *   The format is specified when submitting a new job. Additional routes and
 *   capabilties are documented in the getServiceResponse() method.
 *   <p/>
 *   Note that this class requires JSqlParser which is used to validate
 *   queries and mitigate SQL injection by only allowing "select" statements.
 *   The validation logic is not foolproof and it is therefore recommended to
 *   restrict access to this service to authorized, trusted users (e.g. admins).
 *
 ******************************************************************************/

public class QueryService extends WebService {

    private Database database;
    private javaxt.io.Directory jobDir;
    private javaxt.io.Directory logDir;
    private Map<String, QueryJob> jobs = new ConcurrentHashMap<>();
    private List<String> pendingJobs = new LinkedList<>();
    private List<String> completedJobs = new LinkedList<>();
    private java.util.List<SelectItem> selectCount;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to create a new instance of this class
   *  @param database Database connection info
   *  @param jobDir Temp directory used to save query results (required)
   *  @param logDir Directory used to log queries (optional)
   */
    public QueryService(Database database, javaxt.io.Directory jobDir, javaxt.io.Directory logDir){
        this.database = database;

      //Set path to the jobs directory
        if (jobDir!=null) if (!jobDir.exists()) jobDir.create();
        if (jobDir==null || !jobDir.exists()){
            throw new IllegalArgumentException("Invalid \"jobDir\"");
        }
        this.jobDir = jobDir;



      //Set path to the log directory
        if (logDir!=null) if (!logDir.exists()) logDir.create();
        if (logDir!=null && logDir.exists()){
            this.logDir = logDir;
        }



      //Delete any orphan sql jobs
        for (javaxt.io.Directory dir : jobDir.getSubDirectories()){
            dir.delete();
        }



      //Test whether JSqlParser is in the classpath and parse a default "select count(*)" statement
        try{
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            Select select = (Select) parserManager.parse(new StringReader("SELECT count(*) FROM T"));
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            selectCount = plainSelect.getSelectItems();
        }
        catch(Throwable t){
            throw new IllegalArgumentException("Failed to instantiate JSqlParser");
        }



      //Spawn threads used to execute queries
        int numThreads = 1; //TODO: Make configurable...
        for (int i=0; i<numThreads; i++){
            new Thread(new QueryProcessor(database, this)).start();
        }
    }


  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
  /** Used to generate a response to an HTTP request. The default routes are
   *  as follows:
   *  <ul>
   *  <li>POST /job - Used to create a new query job and return a jobID</li>
   *  <li>GET /job/{jobID} - Returns query results or job status for a given jobID</li>
   *  <li>DELETE /job/{jobID} - Used to cancel query for a given jobID </li>
   *  <li>GET /jobs - Returns a list of all query jobs associated with the user</li>
   *  <li>GET /tables - Returns a list of all the tables in the database</li>
   *  </ul>
   *  If no path is provided in the request, the method will wait until the
   *  query is executed before returning a response. This may result in long
   *  wait times and the client might hang up or disconnect before the query
   *  is done (not recommended).
   */
    public ServiceResponse getServiceResponse(ServiceRequest request, Database database) {
        if (database==null) database = this.database;

        String path = request.getPath(0).toString();
        if (path!=null){
            if (path.equals("jobs")){
                return list(request);
            }
            else if (path.equals("job")){
                String method = request.getRequest().getMethod();
                if (method.equals("GET")){
                    return getJob(request);
                }
                else if (method.equals("POST")){
                    return query(request, true);
                }
                else if (method.equals("DELETE")){
                    return cancel(request, database);
                }
                else{
                    return new ServiceResponse(501, "Not implemented");
                }
            }
            else if (path.equals("tables")){
                return getTables(request, database);
            }
            else{
                return new ServiceResponse(501, "Not implemented");
            }
        }
        else{
            return query(request, false);
        }
    }


  //**************************************************************************
  //** notify
  //**************************************************************************
  /** Called whenever a job is created, updated, or deleted. You can override
   *  this method in your application (e.g. relay job status via websockets).
   */
    public void notify(QueryJob job){}


  //**************************************************************************
  //** query
  //**************************************************************************
  /** Used to create a query job.
   *  @param request Parameters include
   *  <ul>
   *  <li>query or q - SQL select statement (required)</li>
   *  <li>offset - Used to specify a start row (optional)</li>
   *  <li>limit - Used to specify number of rows to return (optional). Default is 25.</li>
   *  <li>format - Used to specify output format (optional). Default is json.</li>
   *  <li>metadata - Returns column info (optional). Default is false.</li>
   *  <li>count - Returns total number of records regardless of offset and/or
   *  limit (optional). Default is false.
   *  </li>
   *  </ul>
   */
    private ServiceResponse query(ServiceRequest request, boolean async) {
        try{

          //Get query
            String query = getParameter("q", request).toString();
            if (query==null) query = getParameter("query", request).toString();
            if (query==null) throw new IllegalArgumentException("Query is required");


          //Get Offset and Limit
            Long offset = getParameter("offset", request).toLong();
            Long limit = getParameter("limit", request).toLong();
            if (limit==null) limit = 25L;
            if (offset==null){
                Long page = getParameter("page", request).toLong();
                if (page!=null) offset = (page*limit)-limit;
            }



          //Parse sql statement using JSQLParser
            Select select = null;
            CreateTable createTempTable = null;
            Statements statements = CCJSqlParserUtil.parseStatements(query);
            if (statements!=null){

                Iterator<Statement> it = statements.getStatements().iterator();
                while (it.hasNext()){
                    Statement statement = it.next();
                    if (statement instanceof CreateTable){
                        CreateTable createTable = (CreateTable) statement;
                        boolean isTemporaryTable = false;
                        Iterator<String> i2 = createTable.getCreateOptionsStrings().iterator();
                        while (i2.hasNext()){
                            String option = i2.next();
                            if (option.equalsIgnoreCase("TEMPORARY")) isTemporaryTable = true;
                            break;
                        }

                        if (isTemporaryTable){
                            if (select!=null) throw new IllegalArgumentException("Temporary table must be created before the SELECT statement");
                            if (createTempTable!=null) throw new IllegalArgumentException("Only 1 temp table allowed");
                            createTempTable = createTable;
                        }
                        else{
                            throw new IllegalArgumentException("CREATE TABLE statements not allowed");
                        }
                    }
                    else if (statement instanceof Select){
                        if (select!=null) throw new IllegalArgumentException("Only 1 SELECT statement allowed");
                        select = (Select) statement;
                    }
                    else{
                        throw new IllegalArgumentException(statement.getClass().getSimpleName() + " statements not allowed");
                    }
                }
            }




          //Check whether the select statement has illegal or unsupported functions
            checkSelect((PlainSelect) select.getSelectBody());



          //Collect misc params
            JSONObject params = new JSONObject();
            params.set("format", getParameter("format", request).toString());
            Boolean addMetadata = getParameter("metadata", request).toBoolean();
            if (addMetadata!=null && addMetadata==true){
                params.set("metadata", true);
            }
            Boolean count = getParameter("count", request).toBoolean();
            if (count!=null && count==true){
                params.set("count", true);
            }



          //Create job
            User user = (User) request.getUser();
            Long userID = user==null ? 0 : user.getID();
            QueryJob job = new QueryJob(userID, select, offset, limit, params);
            if (createTempTable!=null) job.addTempTable(createTempTable);
            String key = job.getKey();
            job.log();
            notify(job);


          //Update list of jobs
            synchronized(jobs) {
                jobs.put(key, job);
                jobs.notify();
            }


          //Update pendingJobs
            synchronized(pendingJobs) {
                pendingJobs.add(key);
                pendingJobs.notify();
            }


          //Generate response
            if (async){
                return new ServiceResponse(job.toJson());
            }
            else{
                synchronized (completedJobs) {
                    while (!completedJobs.contains(key)) {
                        try {
                            completedJobs.wait();
                        }
                        catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                return getJobResponse(job);
            }
        }
        catch(Exception e){
            if (e instanceof net.sf.jsqlparser.JSQLParserException){
                e = new Exception("Unsupported or Invalid SQL Statement");
            }
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** checkSelect
  //**************************************************************************
  /** Used to check whether the select statement has illegal or unsupported
   *  functions
   */
    protected void checkSelect(PlainSelect plainSelect){}


  //**************************************************************************
  //** Writer
  //**************************************************************************
  /** Used to generate json, csv, tsv, etc using records from the database
   */
    private class Writer {

        private String format;
        private StringBuilder str;
        private long x = 0;
        private Long elapsedTime;
        private Long count;
        private JSONArray metadata;
        private boolean addMetadata = false;
        private boolean isClosed = false;

        public Writer(String format, boolean addMetadata){
            str = new StringBuilder();
            this.format = format;
            this.addMetadata = addMetadata;
        }

        public void write(Recordset rs){
            if (isClosed) return; //throw exception?

            Field[] fields = rs.getFields();
            if (x==0){

                metadata = new JSONArray();
                int count = 1;
                for (Field field : fields) {
                    JSONObject json = new JSONObject();
                    json.set("id", count);
                    json.set("name", field.getName());
                    json.set("type", field.getType());
                    json.set("class", field.getClassName());
                    json.set("table", field.getTableName());
                    metadata.add(json);
                    count++;
                }

                if (format.equals("tsv") || format.equals("csv")){
                    String s = format.equals("tsv") ? "\t" : ",";
                    for (int i=0; i<fields.length; i++){
                        if (i>0) str.append(s);
                        str.append(fields[i].getName());
                    }
                    str.append("\r\n");
                }
                else if (format.equals("jsv")){
                    str.append("{\"cols\":[");
                    for (int i=0; i<fields.length; i++){
                        if (i>0) str.append(",");
                        str.append("\"");
                        str.append(fields[i].getName());
                        str.append("\"");
                    }
                    str.append("],");
                    str.append("\"rows\":[");
                }
                else if (format.equals("json")){
                    str.append("{\"rows\":[");
                }
            }


            if (format.equals("json")){
                JSONObject json = new JSONObject();
                JSONObject values = DbUtils.getJson(fields);
                for (Field field : fields){
                    String fieldName = field.getName();
                    String key = StringUtils.underscoreToCamelCase(fieldName.toLowerCase());
                    Object val = field.getValue().toObject();
                    if (val==null){
                        json.set(fieldName, "null");
                    }
                    else{
                        json.set(fieldName, values.get(key));
                    }
                }

                if (x>0) str.append(",");
                str.append(json.toString().replace("\"null\"", "null")); //<-- this is a bit of a hack...

            }
            else if (format.equals("jsv")){
                JSONArray arr = new JSONArray();
                JSONObject values = DbUtils.getJson(fields);
                for (Field field : fields){
                    String fieldName = field.getName();
                    String key = StringUtils.underscoreToCamelCase(fieldName.toLowerCase());
                    arr.add(values.get(key));
                }

                if (x>0) str.append(",");
                str.append(arr.toString().replace("\"null\"", "null")); //<-- same logic as json...

            }
            else if (format.equals("tsv") || format.equals("csv")){
                String s = format.equals("tsv") ? "\t" : ",";
                for (int i=0; i<fields.length; i++){
                    if (i>0) str.append(s);
                    Object value = fields[i].getValue().toObject();
                    if (value==null){
                        value = "";
                    }
                    else{
                        if (value instanceof String){
                            String v = (String) value;
                            if (v.contains(s)){
                                value = "\"" + v + "\"";
                            }
                        }
                        else if (value instanceof javaxt.utils.Date) {
                            value = ((javaxt.utils.Date) value).toISOString();
                        }
                        else if (value instanceof java.util.Date) {
                            value = new javaxt.utils.Date(((java.util.Date) value)).toISOString();
                        }
                        else if (value instanceof java.util.Calendar) {
                            value = new javaxt.utils.Date(((java.util.Calendar) value)).toISOString();
                        }

                    }
                    str.append(value);
                }
                str.append("\r\n");
            }

            x++;
        }


        public void includeMetadata(boolean b){
            addMetadata = b;
        }


        public void setElapsedTime(long elapsedTime){
            this.elapsedTime = elapsedTime;
        }


        public void setCount(long count){
            this.count = count;
        }


        public void close(){
            isClosed = true;

            if (format.equals("json")){
                if (x>0){
                    str.append("]");


                    if (addMetadata){
                        if (metadata!=null){
                            str.append(",\"metadata\":");
                            str.append(metadata);
                        }
                    }


                    if (count!=null){
                        str.append(",\"total_rows\":");
                        str.append(count);
                    }

                    if (this.elapsedTime!=null){
                        double elapsedTime = (double)(this.elapsedTime)/1000d;
                        BigDecimal time = new BigDecimal(elapsedTime).setScale(3, BigDecimal.ROUND_HALF_UP);
                        str.append(",\"time\":");
                        str.append(time);
                    }

                    str.append("}");

                }
                else{
                    str.append("{}");
                }
            }
            else if (format.equals("jsv")){
                if (x>0){
                    str.append("]}");
                }
                else{
                    str.append("{}");
                }
            }

        }


        public String toString(){
            if (!isClosed) close();
            return str.toString();
        }
    }


  //**************************************************************************
  //** list
  //**************************************************************************
  /** Returns an unordered list of jobs
   */
    private ServiceResponse list(ServiceRequest request) {

        User user = (User) request.getUser();
        JSONArray arr = new JSONArray();
        synchronized (jobs) {
            for (String key : jobs.keySet()){
                QueryJob job = jobs.get(key);
                if (job.userID==user.getID()){
                    arr.add(job.toJson());
                }
            }
        }

        return new ServiceResponse(arr);
    }


  //**************************************************************************
  //** getJob
  //**************************************************************************
  /** Used to return the status or results for a given jobID. Example:
   *  [GET] sql/job/{jobID}
   */
    private ServiceResponse getJob(ServiceRequest request) {
        String id = request.getPath(1).toString();
        User user = (User) request.getUser();
        Long userID = user==null ? 0 : user.getID();
        QueryJob job = getJob(id, userID);
        if (job==null) return new ServiceResponse(404);
        return getJobResponse(job);
    }


  //**************************************************************************
  //** getJob
  //**************************************************************************
  /** Returns a job for a given jobID and user. Checks both the pending and
   *  completed job queues.
   */
    private QueryJob getJob(String jobID, long userID){
        synchronized (jobs) {
            return jobs.get(userID + ":" + jobID);
        }
    }


  //**************************************************************************
  //** getJobResponse
  //**************************************************************************
  /** Used to generate a ServiceResponse for a given job. If a job has failed
   *  or is complete, returns the output of the job. If the job is pending or
   *  running, simply returns the job status.
   */
    private ServiceResponse getJobResponse(QueryJob job){
        ServiceResponse response;
        if (job.status.equals("failed")){
            javaxt.io.File file = job.getOutput();
            String str = file.getText();
            response = new ServiceResponse(500, str);
            deleteJob(job);
        }
        else if (job.status.equals("complete")){
            javaxt.io.File file = job.getOutput();
            String str = file.getText();
            response = new ServiceResponse(str);
            response.setContentType(file.getContentType());
            deleteJob(job);
        }
        else{
            response = new ServiceResponse(job.status);
        }
        return response;
    }


  //**************************************************************************
  //** deleteJob
  //**************************************************************************
  /** Removes a job from the queue and deletes any output files that might
   *  have been created with the job.
   */
    private void deleteJob(QueryJob job){

        String key = job.getKey();
        synchronized(pendingJobs){
            pendingJobs.remove(key);
            pendingJobs.notify();
        }

        synchronized (completedJobs) {
            completedJobs.remove(key);
            completedJobs.notify();
        }

        synchronized (jobs) {
            jobs.remove(key);
            jobs.notify();
        }

        javaxt.io.File file = job.getOutput();
        file.delete();
    }


  //**************************************************************************
  //** cancel
  //**************************************************************************
  /** Used to cancel a pending or running job.
   */
    private ServiceResponse cancel(ServiceRequest request, Database database) {
        String id = request.getPath(1).toString();
        User user = (User) request.getUser();
        Long userID = user==null ? 0 : user.getID();
        QueryJob job = getJob(id, userID);
        if (job==null) return new ServiceResponse(404);


        String key = job.getKey();
        synchronized(pendingJobs){
            pendingJobs.remove(key);
            pendingJobs.notify();
        }



        try (Connection conn = database.getConnection()) {

          //Update job status
            job.status = "canceled";
            job.updated = new javaxt.utils.Date();
            notify(job);


          //Cancel the query in the database
            if (database.getDriver().equals("PostgreSQL")){
                Integer pid = getPid(job.getKey(), conn);
                if (pid!=null){
                    boolean jobCanceled = false;

                    javaxt.sql.Record record = conn.getRecord("SELECT pg_cancel_backend(" + pid + ")");
                    if (record!=null) jobCanceled = record.get(0).toBoolean();

                    if (!jobCanceled){
                        record = conn.getRecord("SELECT pg_terminate_backend(" + pid + ")");
                        if (record!=null) jobCanceled = record.get(0).toBoolean();
                    }


                    if (!jobCanceled){
                        throw new Exception();
                    }
                }
            }

          //Update queue
            deleteJob(job);


          //return response
            return new ServiceResponse(job.toJson());
        }
        catch(Exception e){
            return new ServiceResponse(500, "failed to cancel query");
        }
    }


  //**************************************************************************
  //** getPid
  //**************************************************************************
  /** Returns process id for a given jobId
   */
    private Integer getPid(String key, Connection conn) throws SQLException {
        javaxt.sql.Record record = conn.getRecord(
        "SELECT pid from pg_stat_activity where query like '--" + key + "%'");
        return record==null ? null : record.get(0).toInteger();
    }


  //**************************************************************************
  //** getTables
  //**************************************************************************
  /** Returns a list of tables and columns
   */
    public ServiceResponse getTables(ServiceRequest request, Database database) {
        try {
            JSONArray arr = new JSONArray();
            for (Table table : database.getTables()){


              //Get schema
                String schema = table.getSchema();
                if (schema!=null){

                  //Skip PostgreSQL metadata tables
                    if (schema.equalsIgnoreCase("information_schema")) continue;
                    if (schema.toLowerCase().startsWith("pg_")) continue;
                }


              //Get columns
                JSONArray columns = new JSONArray();
                for (Column column : table.getColumns()){

                    JSONObject col = new JSONObject();
                    col.set("name", column.getName());
                    col.set("type", column.getType());
                    if (column.isPrimaryKey()){
                        col.set("primaryKey", true);
                    }
                    columns.add(col);
                }


              //Update array
                JSONObject json = new JSONObject();
                json.set("name", table.getName());
                json.set("schema", schema);
                json.set("columns", columns);
                arr.add(json);
            }


            JSONObject json = new JSONObject();
            json.set("tables", arr);
            return new ServiceResponse(json);
        }
        catch(Exception e){
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** getParameter
  //**************************************************************************
  /** Used to extract a parameter either from the URL query string or the json
   *  in the request payload.
   */
    private javaxt.utils.Value getParameter(String name, ServiceRequest request){
        if (request.getRequest().getMethod().equals("GET")){
            return request.getParameter(name);
        }
        else{
            JSONObject json = request.getJson();
            if (json.has(name)){
                return new javaxt.utils.Value(json.get(name).toObject());
            }
            else{
                return request.getParameter(name);
            }
        }
    }


  //**************************************************************************
  //** QueryJob
  //**************************************************************************
    public class QueryJob {

        private String id;
        private long userID;
        private Select select;
        private Long offset;
        private LongValue limit;
        private javaxt.utils.Date created;
        private javaxt.utils.Date updated;
        private String status;
        private String format;
        private boolean countTotal = false;
        private boolean addMetadata = false;
        private CreateTable tempTable;


        public QueryJob(long userID, Select select, Long offset, Long limit, JSONObject params) {
            this.id = UUID.randomUUID().toString();
            this.userID = userID;
            this.select = select;
            this.offset = offset;
            this.limit = limit==null ? null : new LongValue(limit);
            this.created = new javaxt.utils.Date();
            this.updated = this.created.clone();
            this.status = "pending";

            String format = params.get("format").toString();
            if (format==null) format="";
            format = format.trim().toLowerCase();
            if (format.equals("csv") || format.equals("tsv") || format.equals("jsv")){
                this.format = format;
            }
            else this.format = "json";


            if (params.has("count")){
                countTotal = params.get("count").toBoolean();
            }

            if (params.has("metadata")){
                addMetadata = params.get("metadata").toBoolean();
            }
        }

        public String getID(){
            return id;
        }

        public long getUserID(){
            return userID;
        }

        public String getStatus(){
            return status;
        }

        public void addTempTable(CreateTable stmt){
            tempTable = stmt;
        }

        public CreateTable getTempTable(){
            return tempTable;
        }

        public String getKey(){
            return userID + ":" + id;
        }

        public boolean isCanceled(){
            return status.equals("canceled");
        }

        public String getQuery(){
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

          //Update offset and limit
            if (offset!=null){
                Offset o = plainSelect.getOffset();
                if (o==null) o = new Offset();
                o.setOffset(offset);
                plainSelect.setOffset(o);
            }
            if (limit!=null){
                Limit l = plainSelect.getLimit();
                if (l==null){
                    l = new Limit();
                    l.setRowCount(limit);
                    plainSelect.setLimit(l);
                }
            }
            String query = plainSelect.toString();




          //Prepend any "with" clause that might be present
            if (select.getWithItemsList()!=null){
                java.util.Iterator<WithItem> i2 = select.getWithItemsList().iterator();
                query = "with " + i2.next() + " \r\n" + query;
            }

            return query;
        }

        public String getCountQuery(){

            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            plainSelect.setSelectItems(selectCount);
            String query = plainSelect.toString();


          //Add prepend any "with" clause that might be present
            if (!select.getWithItemsList().isEmpty()){
                java.util.Iterator<WithItem> i2 = select.getWithItemsList().iterator();
                query = "with " + i2.next() + " \r\n" + query;
            }

            return query;
        }

        public boolean countTotal(){
            if (countTotal){
                if (format.equals("json")) return true;
            }
            return false;
        }

        public boolean addMetadata(){
            return addMetadata;
        }

        public String getOutputFormat(){
            return format;
        }

        public javaxt.io.File getOutput(){
            return new javaxt.io.File(jobDir.toString() + userID + "/" + id + "." + format);
        }


        public String getContentType(){
            if (format.equals("tsv")){
                return "text/plain";
            }
            else if (format.equals("csv"))
                return "text/csv";
            else{
                return "application/json";
            }
        }


        public void log(){
            if (logDir!=null){
                javaxt.io.File file = new javaxt.io.File(logDir.toString() + userID + "/" + id + ".json");
                file.write(toJson().toString());
            }
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.set("user_id", userID);
            json.set("job_id", id);
            json.set("status", status);
            json.set("query", getQuery());
            json.set("created_at", created);
            json.set("updated_at", updated);
            return json;
        }
    }


  //**************************************************************************
  //** QueryProcessor
  //**************************************************************************
  /** Thread used to execute queries
   */
    private class QueryProcessor implements Runnable {
        private Database database;
        private QueryService queryService;

        public QueryProcessor(Database database, QueryService queryService){
            this.database = database;
            this.queryService = queryService;
        }

        public void run() {

            while (true) {

                Object obj = null;
                synchronized (pendingJobs) {
                    while (pendingJobs.isEmpty()) {
                        try {
                          pendingJobs.wait();
                        }
                        catch (InterruptedException e) {
                          return;
                        }
                    }
                    obj = pendingJobs.get(0);
                    if (obj!=null) pendingJobs.remove(0);
                    pendingJobs.notifyAll();
                }

                if (obj!=null){

                  //Find query job
                    String key = (String) obj;
                    QueryJob job = null;
                    synchronized (jobs) {
                        job = jobs.get(key);
                    }

                    if (job!=null && !job.isCanceled()){
                        Connection conn = null;
                        try{

                          //Update job status and set start time
                            job.status = "running";
                            job.updated = new javaxt.utils.Date();
                            long startTime = System.currentTimeMillis();
                            queryService.notify(job);


                          //Open database connection
                            conn = database.getConnection();


                          //Create temp table as needed
                            CreateTable createTempTable = job.getTempTable();
                            if (createTempTable!=null){
                                conn.execute("--" + job.getKey() + "\n" + createTempTable.toString());
                                if (job.isCanceled()){
                                    conn.execute("DROP TABLE " + createTempTable.getTable().getName());
                                    throw new Exception();
                                }
                            }


                          //Execute query and generate response
                            String query = job.getQuery();
                            Writer writer = new Writer(job.getOutputFormat(), job.addMetadata());
                            Recordset rs = conn.getRecordset("--" + job.getKey() + "\n" + query);
                            while (rs.next()){
                                writer.write(rs);
                            }
                            rs.close();
                            if (job.isCanceled()) throw new Exception();


                          //Count total records as needed
                            if (job.countTotal()){
                                javaxt.sql.Record record = conn.getRecord(job.getCountQuery());
                                if (record!=null){
                                    Long ttl = record.get(0).toLong();
                                    if (ttl!=null){
                                        writer.setCount(ttl);
                                    }
                                }
                            }
                            if (job.isCanceled()) throw new Exception();



                          //Drop temp table
                            if (createTempTable!=null){
                                conn.execute("DROP TABLE " + createTempTable.getTable().getName());
                            }
                            if (job.isCanceled()) throw new Exception();


                          //Close database connection
                            conn.close();



                          //Set elapsed time
                            writer.setElapsedTime(System.currentTimeMillis()-startTime);


                          //Write output to a file
                            javaxt.io.File file = job.getOutput();
                            file.write(writer.toString());


                          //Update job status
                            job.status = "complete";
                            job.updated = new javaxt.utils.Date();
                            queryService.notify(job);
                        }
                        catch(Exception e){
                            if (conn!=null) conn.close();
                            javaxt.io.File file = job.getOutput();
                            if (job.isCanceled()){
                                file.delete();
                            }
                            else{
                                job.status = "failed";
                                job.updated = new javaxt.utils.Date();
                                queryService.notify(job);


                                java.io.PrintStream ps = null;
                                try {
                                    file.create();
                                    ps = new java.io.PrintStream(file.toFile());
                                    e.printStackTrace(ps);
                                    ps.close();
                                }
                                catch (Exception ex) {
                                    if (ps!=null) ps.close();
                                    file.write(e.getMessage());
                                }
                            }
                        }


                      //Add job to the completedJobs
                        if (!job.isCanceled()){
                            synchronized(completedJobs){
                                completedJobs.add(job.getKey());
                                completedJobs.notify();
                            }
                        }
                    }
                }
                else{
                    return;
                }
            }
        }
    }
}