package javaxt.express.services;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.StringReader;
import java.math.BigDecimal;

import javaxt.express.User;
import javaxt.express.ws.*;

import javaxt.sql.*;
import javaxt.json.*;
import javaxt.utils.Console;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.*;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.CreateTable;

//******************************************************************************
//**  QueryService
//******************************************************************************
/**
 *   Provides a set of web methods used to query the database. Loosely based
 *   on the CartoDB SQL API: https://carto.com/developers/sql-api/reference/
 *
 ******************************************************************************/

public class QueryService {

    private javaxt.io.Directory jobDir;
    private javaxt.io.Directory logDir;
    private Map<String, QueryJob> jobs = new ConcurrentHashMap<>();
    private List<String> pendingJobs = new LinkedList<>();
    private List<String> completedJobs = new LinkedList<>();
    private java.util.List<SelectItem> selectCount;
    private Console console = new Console();


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public QueryService(Database database, javaxt.io.Directory jobDir, javaxt.io.Directory logDir){

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
            new Thread(new QueryProcessor(database)).start();
        }
    }


  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
    public ServiceResponse getServiceResponse(ServiceRequest request, Database database) {
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
  //** query
  //**************************************************************************
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
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            Iterator<SelectItem> it = plainSelect.getSelectItems().iterator();
            while (it.hasNext()){
                SelectItem selectItem = it.next();

                String functionName = null;
                if (selectItem instanceof SelectExpressionItem){
                    SelectExpressionItem si = (SelectExpressionItem) selectItem;
                    Expression expression = si.getExpression();

                    try{
                        net.sf.jsqlparser.expression.Function f = (net.sf.jsqlparser.expression.Function) expression;
                        functionName = f.getName().toUpperCase();

                    }
                    catch(Exception e){
                        try{
                            SubSelect ss = (SubSelect) expression;
                            functionName = "SELECT";
                        }
                        catch(Exception ex){
                        }
                    }
                }
                else if (selectItem instanceof AllColumns){}
                else if (selectItem instanceof AllTableColumns){}
                else{
                    throw new IllegalArgumentException("Unsupported select expression");
                }


                if (functionName!=null){
                    if (functionName.equals("COUNT")){
                        //throw new IllegalArgumentException(functionName + " statements are not currently supported");
                    }
                }
            }



          //Collect misc params
            JSONObject params = new JSONObject();
            params.set("format", request.getParameter("format").toString());
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
            QueryJob job = new QueryJob(user.getID(), select, offset, limit, params);
            if (createTempTable!=null) job.addTempTable(createTempTable);
            String key = job.getKey();
            job.log();


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

            if (format.equals("json")){
                str.append("{\"rows\":[");
            }
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
                    json.set("table", field.getTable());
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
            }


            if (format.equals("json")){
                JSONObject json = new JSONObject();
                for (Field field : fields){
                    Object val = field.getValue().toObject();
                    if (val==null){
                        val = "null";
                    }
                    else{
                        if (val instanceof String){
                            String s = (String) val;
                            if (s.trim().length()==0) val = "null";
                        }
                    }
                    json.set(field.getName(), val);
                }

                if (x>0) str.append(",");
                str.append(json.toString().replace("\"null\"", "null")); //<-- this is a bit of a hack...

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
            Iterator<String> it = jobs.keySet().iterator();
            while (it.hasNext()){
                String key = it.next();
                QueryJob job = jobs.get(key);
                long userID = job.userID;

              //Prevent non-admins seeing other user jobs
                if (user.getAccessLevel()<5){
                    if (userID!=user.getID()) continue;
                }

                arr.add(job.toJson());
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
        QueryJob job = getJob(id, user);
        if (job==null) return new ServiceResponse(404);
        return getJobResponse(job);
    }


  //**************************************************************************
  //** getJob
  //**************************************************************************
  /** Returns a job for a given jobID and user. Checks both the pending and
   *  completed job queues.
   */
    private QueryJob getJob(String jobID, User user){
        synchronized (jobs) {
            return jobs.get(user.getID() + ":" + jobID);
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
        QueryJob job = getJob(id, user);
        if (job==null) return new ServiceResponse(404);


        String key = job.getKey();
        synchronized(pendingJobs){
            pendingJobs.remove(key);
            pendingJobs.notify();
        }



        Connection conn = null;
        try{

          //Update job status
            job.status = "canceled";
            job.updated = new javaxt.utils.Date();


          //Cancel the query in the database
            conn = database.getConnection();
            Integer pid = getPid(job.getKey(), conn);
            if (pid!=null){
                boolean jobCanceled = false;

                Recordset rs = new Recordset();
                rs.open("SELECT pg_cancel_backend(" + pid + ")", conn);
                if (!rs.EOF) jobCanceled = rs.getValue(0).toBoolean();
                rs.close();

                if (!jobCanceled){
                    rs.open("SELECT pg_terminate_backend(" + pid + ")", conn);
                    if (!rs.EOF) jobCanceled = rs.getValue(0).toBoolean();
                    rs.close();
                }


                if (!jobCanceled){
                    throw new Exception();
                }
            }
            conn.close();


          //Update queue
            deleteJob(job);


          //return response
            return new ServiceResponse(job.toJson());
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(500, "failed to cancel query");
        }
    }


  //**************************************************************************
  //** getPid
  //**************************************************************************
  /** Returns process id for a given jobId
   */
    private Integer getPid(String key, Connection conn) throws SQLException {
        Integer pid = null;
        Recordset rs = new Recordset();
        rs.open("SELECT pid from pg_stat_activity where query like '--" + key + "%'", conn);
        if (!rs.EOF) pid = rs.getValue(0).toInteger();
        rs.close();
        return pid;
    }


  //**************************************************************************
  //** getTables
  //**************************************************************************
  /** Returns a list of tables and columns
   */
    private ServiceResponse getTables(ServiceRequest request, Database database) {
        Connection conn = null;
        try{

            JSONArray arr = new JSONArray();
            conn = database.getConnection();
            for (Table table : Database.getTables(conn)){

                if (table.getName().startsWith("device_location_")) continue; //skip partition tables

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

                JSONObject json = new JSONObject();
                json.set("name", table.getName());
                json.set("columns", columns);
                arr.add(json);
            }
            conn.close();


            JSONObject json = new JSONObject();
            json.set("tables", arr);
            return new ServiceResponse(json);
        }
        catch(Exception e){
            if (conn!=null) conn.close();
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
        private Long limit;
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
            this.limit = limit;
            this.created = new javaxt.utils.Date();
            this.updated = this.created.clone();
            this.status = "pending";

            String format = params.get("format").toString();
            if (format==null) format="";
            format = format.trim().toLowerCase();
            if (format.equals("csv") || format.equals("tsv")){
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

        public QueryProcessor(Database database){
            this.database = database;
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
                            Recordset rs = new Recordset();
                            rs.setFetchSize(1000);
                            rs.open("--" + job.getKey() + "\n" + query, conn);
                            while (rs.hasNext()){
                                writer.write(rs);
                                rs.moveNext();
                            }
                            rs.close();
                            if (job.isCanceled()) throw new Exception();


                          //Count total records as needed
                            if (job.countTotal()){
                                rs = new Recordset();
                                rs.open(job.getCountQuery(), conn);
                                if (!rs.EOF){
                                    Long ttl = rs.getValue(0).toLong();
                                    if (ttl!=null){
                                        writer.setCount(ttl);
                                    }
                                }
                                rs.close();
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


                                java.io.PrintStream ps = null;
                                try {
                                    ps = new java.io.PrintStream(file.toFile());
                                    e.printStackTrace(ps);
                                    ps.close();
                                }
                                catch (Exception ex) {
                                    if (ps!=null) ps.close();
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