package javaxt.express.utils;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.*;
import javaxt.sql.*;
import javaxt.json.*;


//******************************************************************************
//**  DbUtils Class
//******************************************************************************
/**
 *   Provides static methods used to initialize a database, copy data from one
 *   database to another, find/remove duplicates records, etc.
 *
 ******************************************************************************/

public class DbUtils {


  //**************************************************************************
  //** initSchema
  //**************************************************************************
    public static void initSchema(Database database, String schema) throws Exception {
        initSchema(database, schema, null);
    }


  //**************************************************************************
  //** initSchema
  //**************************************************************************
  /** Used to execute SQL statements and populate a database with table, views,
   *  triggers, etc. If the target database does not exist, an attempt is made
   *  to create a new database. Currently only supports PostgreSQL and H2.
   *  @param database Connection info for the database
   *  @param schema String containing SQL statements. Assumes individual
   *  statements are delimited with a semicolon.
   *  @param tableSpace Default tablespace used to store tables, views, etc.
   *  If null, will use the default database tablespace. This option only
   *  applies to PostgreSQL
   */
    public static boolean initSchema(Database database, String schema, String tableSpace)
        throws Exception {
        boolean schemaInitialized = false;

      //Split schema into individual statements
        ArrayList<String> statements = new ArrayList<>();
        for (String s : schema.split(";")){

            StringBuilder str = new StringBuilder();
            for (String i : s.split("\r\n")){
                if (!i.trim().startsWith("--") && !i.trim().startsWith("COMMENT ")){
                    str.append(i + "\r\n");
                }
            }

            String cmd = str.toString().trim();
            if (cmd.length()>0){
                statements.add(StringUtils.rtrim(str.toString()) + ";");
            }
        }



      //Create database
        Driver driver = database.getDriver();
        if (driver.equals("H2")){

            javaxt.io.File db = new javaxt.io.File(database.getHost() + ".mv.db");
            boolean deleteOnError = !db.exists();


            ArrayList<String> arr = null;
            for (String statement : statements){
                String str = statement.trim().toUpperCase();
                if (arr==null){
                    if (str.startsWith("CREATE TABLE") || str.startsWith("CREATE SCHEMA")){
                        arr = new ArrayList<>();
                    }
                }


                if (arr!=null){

                  //Replace trigger functions
                    if (str.startsWith("CREATE TRIGGER")){
                        statement = "";
                    }


                    /*
                  //Replace geometry types
                  //...no longer needed in newer versions of H2
                    int idx = statement.toUpperCase().indexOf("geometry(Geometry,4326)".toUpperCase());
                    if (idx>0){
                        String a = statement.substring(0, idx) + "geometry";
                        String b = statement.substring(idx + "geometry(Geometry,4326)".length());
                        statement = a + b;
                    }
                    */


                  //Update spatial index statement
                    if (str.startsWith("CREATE INDEX") && str.contains("USING GIST")){
                        statement = str.replace("CREATE INDEX", "CREATE SPATIAL INDEX").replace("USING GIST", "");
                    }


                    arr.add(statement);
                }
            }



            try (Connection conn = database.getConnection()){
                conn.execute("CREATE domain IF NOT EXISTS text AS varchar");
                conn.execute("CREATE domain IF NOT EXISTS jsonb AS varchar");



              //Check if we have JTS in the class path. This is a good indicator
              //that we have spatial data in one of the models.
                String jtsPackage = null;
                for (String s : new String[]{"org.locationtech", "com.vividsolutions"}){
                    try{
                        Class.forName(s + ".jts.io.WKTReader"); //throws exception if not found
                        jtsPackage = s;
                        break;
                    }
                    catch(Exception e){}
                }


              //Create spatial functions as needed
                if (jtsPackage!=null){

                  //Create ST_GeomFromText function as needed
                    try{

                      //Test if function exists
                        conn.getRecord("select ST_GeomFromText('POINT(7 52)', 4326)").get(0).toString();

                    }
                    catch(Exception e){

                      //Create function
                        conn.execute("create alias ST_GeomFromText AS $$\n" +
                        jtsPackage + ".jts.geom.Geometry fromText(String wkt, int srid) throws SQLException {\n" +
                        "    if(wkt == null) {\n" +
                        "        return null;\n" +
                        "    }\n" +
                        "    try {\n" +

                                //Instantiate WKTReader
                        "       " + jtsPackage + ".jts.io.WKTReader wktReaderSRID = " +
                        "       new " + jtsPackage + ".jts.io.WKTReader(new " +
                                jtsPackage + ".jts.geom.GeometryFactory(new " +
                                jtsPackage + ".jts.geom.PrecisionModel(),srid));\n" +

                                //Get geometry
                        "       return wktReaderSRID.read(wkt);\n" +
                        "    } catch (" + jtsPackage + ".jts.io.ParseException ex) {\n" +
                        "        throw new SQLException(ex);\n" +
                        "    }\n" +
                        "}$$");


                        conn.execute("CREATE ALIAS ST_AsText AS '\n" +
                        "String geomAsText(org.h2.value.Value value) {\n" +
                        "    return value.toString();\n" +
                        "}\n" +
                        "';");

                    }



                }


                schemaInitialized = initSchema(arr, conn);
            }
            catch(Exception e){
                e.printStackTrace();
                if (deleteOnError){
                    String fileName = db.getName();
                    fileName = fileName.substring(0, fileName.indexOf("."));
                    for (javaxt.io.File file : db.getParentDirectory().getFiles(fileName + ".*.db")){
                        file.delete();
                    }
                }
                throw e;
            }
        }
        else if (driver.equals("PostgreSQL")){

          //Connect to the database
            Connection conn;
            try{ conn = database.getConnection(); }
            catch(Exception e){

              //Try to connect a new database. First, we'll try to connect to
              //a database called "postgres" on the PostgreSQL server. This is
              //the default database in most installations.
                Database db = database.clone();
                db.setName("postgres");
                try (Connection c2 = db.getConnection()) {


                  //Check if database exists
                    boolean createDatabase = true;
                    for (String dbName : Database.getCatalogs(c2)){
                        if (dbName.equalsIgnoreCase(database.getName())){
                            createDatabase = false;
                            break;
                        }
                    }


                  //Create new database as needed
                    if (createDatabase){
                        c2.execute("CREATE DATABASE " + database.getName());
                    }

                }
                catch(Exception ex){
                    ex.printStackTrace();
                    throw new Exception("Failed to connect to the database");
                }

                conn = database.getConnection();
            }



          //Generate list of SQL statements
            ArrayList<String> arr = new ArrayList<>();
            if (tableSpace!=null) arr.add("SET default_tablespace = " + tableSpace + ";");
            for (int i=0; i<statements.size(); i++){
                String statement = statements.get(i);
                String str = statement.trim().toLowerCase();
                if (str.startsWith("create function") ||
                    str.startsWith("create or replace function")){

                    while (i<statements.size()){
                        i++;
                        statement += "\r\n";
                        statement += statements.get(i);

                        str = statement.trim().toLowerCase();
                        if (str.contains("language plpgsql")){
                            arr.add(statement);
                            /*
                            System.out.println("------------------------------");
                            System.out.println(statement);
                            System.out.println("------------------------------");
                            */
                            break;
                        }
                    }

                }
                else{
                    arr.add(statement);
                }
            }



          //Create tables
            try{
                schemaInitialized = initSchema(arr, conn);
                conn.close();
            }
            catch(Exception e){
                if (conn!=null) conn.close();
                throw e;
            }
        }

        return schemaInitialized;
    }


  //**************************************************************************
  //** initSchema
  //**************************************************************************
  /** Used to create tables and foreign keys in the database.
   */
    private static boolean initSchema(ArrayList<String> statements, Connection conn)
        throws java.sql.SQLException {



      //Check whether the database contains tables defined in the schema
        Table[] tables = Database.getTables(conn);
        if (tables.length>0){
            for (String cmd : statements){
                String tableName = getTableName(cmd);
                if (tableName!=null){
                    tableName = tableName.replace("\"", "");
                    String schema = null;
                    if (tableName.contains(".")){
                        String[] arr = tableName.split("\\.");
                        schema = arr[0];
                        tableName = arr[1];
                    }

                    for (Table table : tables){
                        if (schema==null){
                            if (table.getName().equalsIgnoreCase(tableName)){
                                return false;
                            }
                        }
                        else{
                            if (table.getSchema()!=null){
                                if (table.getSchema().equalsIgnoreCase(schema) &&
                                    table.getName().equalsIgnoreCase(tableName)){
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }


      //Execute statments
        try (java.sql.Statement stmt = conn.getConnection().createStatement()){
            for (String cmd : statements){
                //String tableName = getTableName(cmd);
                //if (tableName!=null) console.log(tableName);
                try{
                    stmt.execute(cmd);
                }
                catch(java.sql.SQLException e){
                    System.out.println(cmd);
                    throw e;
                }
            }
        }
        return true;
    }


    private static String getTableName(String cmd){
        cmd = cmd.trim();
        if (cmd.startsWith("CREATE TABLE")){
            String tableName = cmd.substring(cmd.indexOf("TABLE")+5, cmd.indexOf("(")).trim();
            if (tableName.startsWith("\"") && tableName.endsWith("\"")) tableName = tableName.substring(1, tableName.length()-1);
            return tableName.trim();
        }
        return null;
    }

    public static LinkedHashMap<String, Boolean> getColumns(String tableName, Database sourceDB) throws Exception{
        LinkedHashMap<String, Boolean> columns = new LinkedHashMap<>();
        Connection conn = null;
        try{
            conn = sourceDB.getConnection();
          //Get columns
            for (Table table : Database.getTables(conn)){
                if (table.getName().equalsIgnoreCase(tableName)){
                    for (Column column : table.getColumns()){
                        columns.put(column.getName().toLowerCase(), false);
                    }
                    break;
                }
            }


          //Get geometry columns
            if (sourceDB.getDriver().equals("PostgreSQL")){
                Recordset rs = new Recordset();
                if (columns.isEmpty()){ //special case for views



                    rs.open("select \n" +
                    "    ns.nspname as schema_name, \n" +
                    "    cls.relname as table_name, \n" +
                    "    attr.attname as column_name,\n" +
                    "    trim(leading '_' from tp.typname) as datatype\n" +
                    "from pg_catalog.pg_attribute as attr\n" +
                    "join pg_catalog.pg_class as cls on cls.oid = attr.attrelid\n" +
                    "join pg_catalog.pg_namespace as ns on ns.oid = cls.relnamespace\n" +
                    "join pg_catalog.pg_type as tp on tp.typelem = attr.atttypid\n" +
                    "where \n" +
                    "    ns.nspname = 'public' and\n" +
                    "    cls.relname = '" + tableName + "' and \n" +
                    "    not attr.attisdropped and \n" +
                    "    cast(tp.typanalyze as text) = 'array_typanalyze' and \n" +
                    "    attr.attnum > 0\n" +
                    "order by \n" +
                    "    attr.attnum", conn);
                    while (rs.hasNext()){

                        String columnName = rs.getValue("column_name").toString().toLowerCase();
                        String dataType = rs.getValue("datatype").toString();
                        boolean isGeometry = (dataType.equalsIgnoreCase("geometry"));
                        columns.put(columnName, isGeometry);
                        rs.moveNext();
                    }

                    if (columns.isEmpty()){
                        rs.close();
                        conn.close();
                        throw new IllegalArgumentException("Invalid table name");
                    }

                }
                else{
                    rs.open("select column_name from information_schema.columns where table_name='" +
                    tableName + "' and udt_name='geometry'", conn);
                    while (rs.hasNext()){
                        String columnName = rs.getValue(0).toString().toLowerCase();
                        columns.put(columnName, true);
                        rs.moveNext();
                    }
                }
                rs.close();
            }
            conn.close();
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            throw e;
        }
        return columns;
    }



  //**************************************************************************
  //** executeBatch
  //**************************************************************************
    public static void executeBatch(ArrayList<String> statements, Connection conn) throws Exception {
        if (statements.isEmpty()) return;
        StringBuffer str = new StringBuffer();
        str.append("BEGIN;\n");
        for (String statement : statements){
            statement = statement.trim();
            str.append(statement);
            if (!statement.endsWith(";")) str.append(";");
            str.append("\n");
        }
        str.append("END;\n");
        conn.execute(str.toString());
    }


  //**************************************************************************
  //** copyTable
  //**************************************************************************
  /** Used to transfer records between 2 databases for a given table.
   */
    public static void copyTable(String tableName, String where, Database sourceDB, Database destDB, int pageSize, int numThreads) throws Exception {

        long startTime = System.currentTimeMillis();
        AtomicLong counter = new AtomicLong(0);
        LinkedHashMap<String, Boolean> columns = getColumns(tableName, sourceDB);
        long minID = 0;
        long maxID = 0;
        String t = tableName;
        if (t.equals("user")) t = "\"" + t + "\"";


      //Get min/max row ID

        try (Connection conn = sourceDB.getConnection()) {
            Recordset rs = new Recordset();
            rs.open("select min(id), max(id) from " + t + (where==null ? "" : " where " + where), conn);
            if (!rs.EOF){
                minID = rs.getValue(0).toLong();
                maxID = rs.getValue(1).toLong();
            }
            rs.close();

            //Long id = getLastRowID(tableName, where, destDB);
            //if (id!=null && id>minID) minID = id;
        }
        catch(Exception e){
            e.printStackTrace();
        }


      //Spawn threads
        long diff = maxID-minID;
        long numRowsPerThread = Math.round(diff/numThreads);
        long startRow = minID;
        ArrayList<Thread> threads = new ArrayList<Thread>();
        for (int i=0; i<numThreads; i++){
            long endRow = startRow+numRowsPerThread;
            //System.out.println(i + ":\t" + startRow + "-" + endRow);
            //if (i==numThreads-1) endRow = Long.MAX_VALUE;

            Thread thread = new Thread(new TableProcessor(t, where, sourceDB, destDB, columns, startRow, endRow, pageSize, counter));
            thread.setName("t"+i);
            threads.add(thread);
            thread.start();

            startRow = endRow+1;
        }




      //Start console logger
        Runnable statusLogger = new Runnable() {
            private String statusText = "000,000 records per second";
            public void run() {
                long currTime = System.currentTimeMillis();
                double elapsedTime = (currTime-startTime)/1000; //seconds
                long recordsPerSecond = Math.round((double) counter.get() / elapsedTime);

                for (int i=0; i<statusText.length(); i++){
                    System.out.print("\b");
                }
                statusText = pad(format(recordsPerSecond)) + " records per second";

                System.out.print(statusText);
            }
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(statusLogger, 0, 1, TimeUnit.SECONDS);



      //Wait for threads to complete
        while (true) {
            try {
                for (Thread thread : threads){
                    thread.join();
                }
                break;
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


      //Clean up
        executor.shutdown();
        threads.clear();



      //Update sequence
        if (destDB.getDriver().equals("PostgreSQL")){
            try (Connection conn = destDB.getConnection()) {
                conn.execute("SELECT setval('" + tableName + "_id_seq', (SELECT MAX(id) FROM " + t + "));");
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }


      //Print summary
        System.out.println("\r\n" +
            "Processed " +  format(counter.get()) + " records in " +
            format((System.currentTimeMillis()-startTime)/1000) + " seconds"
        );
    }


  //**************************************************************************
  //** TableProcessor
  //**************************************************************************
  /** Thread used to copy records from one database to another
   */
    private static class TableProcessor implements Runnable {

        private String tableName;
        private Database sourceDB;
        private Database destDB;
        private int pageSize;

        private AtomicLong counter;
        private long startRow;
        private long endRow;
        private String where;
        private LinkedHashMap<String, Boolean> columns;
        private String columnNames;
        private boolean hasGeometry = false;

        public TableProcessor(String tableName, String where,
            Database sourceDB, Database destDB,
            LinkedHashMap<String, Boolean> columns,
            long startRow, long endRow, int pageSize, AtomicLong counter
        )
        {
            this.tableName = tableName;
            this.sourceDB = sourceDB;
            this.destDB = destDB;
            this.columns = columns;
            this.pageSize = pageSize;
            this.counter = counter;
            this.startRow = startRow;
            this.endRow = endRow;
            this.where = where;
            this.columnNames = "";

            Iterator<String> it = columns.keySet().iterator();
            while (it.hasNext()){
                String columnName = it.next();
                if (columns.get(columnName)){
                    hasGeometry = true;
                    columnNames += "ST_AsText(" + columnName + ") as " + columnName;
                }
                else{
                    columnNames += columnName;
                }
                if (it.hasNext()) columnNames +=", ";
            }

        }


        public void run() {


            Connection c1 = null;
            Connection c2 = null;
            try{


                c1 = sourceDB.getConnection();
                c2 = destDB.getConnection();

              //Update startRow as needed
                try {
                    long orgStart = startRow;
                    String sql = "SELECT max(id)" +
                    " FROM " + tableName +
                    " WHERE " + (where==null? "" : ("(" + where + ") AND ")) +
                        "ID>" + startRow + " AND ID<" + endRow;
                    Recordset rs = new Recordset();
                    rs.open(sql, c2);
                    if (!rs.EOF){
                        if (!rs.getValue(0).isNull()){
                            this.startRow = rs.getValue(0).toLong();
                        }
                    }
                    rs.close();

                    //System.out.println(Thread.currentThread().getName() + ":\t" + startRow + "-" + endRow + "\t" + "was " + orgStart);
                }
                catch(Exception e){
                    if (c1!=null) c1.close();
                    if (c2!=null) c2.close();
                    e.printStackTrace();
                    return;
                }


              //Disable logging on the destDB table
                boolean unlog = false;
                if (sourceDB.getDriver().equals("PostgreSQL")){
                    try {
                        //TODO: Check if logging is disabled
                        //SELECT relname FROM pg_class WHERE relpersistence = 'u';

                        //c2.execute("alter table " + tableName + "SET UNLOGGED");
                        //unlog = true;
                    }
                    catch(Exception e){
                    }
                }


              //Open recordset to the destDB for writing
                Recordset r2 = new Recordset();
                r2.open("select * from " + tableName + " where id=-1", c2, false);
                r2.setBatchSize(1); //5000


                Recordset rs = new Recordset();
                while (true){

                    int x = 0;



                    String sql = "SELECT " + (hasGeometry? columnNames : "*") +
                    " FROM " + tableName +
                    " WHERE ID>=" + startRow +
                    " ORDER BY ID LIMIT " + pageSize;

                    if (where!=null){
                        sql = "SELECT " + (hasGeometry? columnNames : "*") +
                        " FROM " + tableName +
                        " WHERE ID>=" + startRow + " AND ID<=" + endRow + " AND " + where +
                        " ORDER BY ID LIMIT " + pageSize;
                    }

                    rs.setFetchSize(1000);
                    rs.open(sql, c1);
                    while (rs.hasNext()){

                        long id = rs.getValue("id").toLong();
                        if (id>endRow){
                            startRow = endRow;
                            break;
                        }



                        r2.addNew();
                        for (Field field : rs.getFields()){
                            String fieldName = field.getName();
                            boolean isGeometry = columns.get(fieldName.toLowerCase());
                            if (isGeometry){
                                r2.setValue(fieldName, new Function(
                                    "ST_GeomFromText(?, 4326)", new Object[]{
                                        rs.getValue(fieldName).toString()
                                    }
                                ));
                            }
                            else{
                                r2.setValue(fieldName, rs.getValue(fieldName));
                            }
                        }
                        try{
                            r2.update();
                        }
                        catch(java.sql.SQLException e){
                            if (rs.getBatchSize()>1) throw e;
                        }

                        x++;
                        startRow = id;
                        counter.getAndIncrement();
                        rs.moveNext();
                    }

                    rs.close();


                    if (x<pageSize) break;
                }
//System.out.println(Thread.currentThread().getName() + " is done!");


                r2.close();


              //Re-enable logging
                if (unlog){
                    try {
                        c2.execute("alter table " + tableName + "SET LOGGED");
                    }
                    catch(Exception e){
                    }
                }


                c1.close();
                c2.close();
            }
            catch(Exception e){
                if (c1!=null) c1.close();
                if (c2!=null) c2.close();
                throw new RuntimeException(e);
            }
        }

    }





  //**************************************************************************
  //** getLastRowID
  //**************************************************************************
  /** Returns the last row id for a given table in the destination database.
   */
    private static Long getLastRowID(String tableName, String where, Database destDB){
        Long id = null;
        Connection c2 = null;
        try{
            c2 = destDB.getConnection();
            Recordset rs = new Recordset();
            rs.open("select max(id) from " + tableName + (where==null ? "" : " where " + where), c2);
            if (!rs.EOF) id = rs.getValue(0).toLong();
            rs.close();
            c2.close();
        }
        catch(Exception e){
            if (c2!=null) c2.close();
        }
        return id;
    }


  //**************************************************************************
  //** findMismatch
  //**************************************************************************
  /** Used to find mismatched between the 2 databases for a given table.
   */
    public static void findMismatch(String tableName, Database sourceDB, Database destDB, int pageSize, long offset, AtomicLong rowID){


        Connection c1 = null;
        Connection c2 = null;
        try{
            c1 = sourceDB.getConnection();
            c2 = destDB.getConnection();

            Recordset r1 = new Recordset();
            Recordset r2 = new Recordset();


            Long sourceCount;
            Long destCount;
            boolean foundMismatch = false;

            while (true){

              //Reset counts
                sourceCount = null;
                destCount = null;


                String sql = "select count(id) from " + tableName + " where id>=" + offset + " and id<" + (offset+pageSize);


                r1.open(sql, c1);
                if (!r1.EOF) sourceCount = r1.getValue(0).toLong();
                r1.close();


                r2.open(sql, c2);
                if (!r2.EOF) destCount = r2.getValue(0).toLong();
                r2.close();



                if (sourceCount==null || sourceCount==null) break;
                else{
                    if (!sourceCount.equals(destCount)){
                        foundMismatch = true;
                        break;
                    }
                    else{
                        offset += pageSize;

                    }
                }

            }


            if (foundMismatch){


                if (sourceCount==null) sourceCount = 0L;
                if (destCount==null) destCount = 0L;
                long delta = sourceCount-destCount;


                //System.out.println("Found mismatch between " + offset + " and " + (offset+pageSize) + "\tdelta: " + delta);

                if (pageSize>1){
                    offset = offset-pageSize;
                    pageSize = Math.round(pageSize/10);
                    if (pageSize<1) pageSize=1;
                    findMismatch(tableName, sourceDB, destDB, pageSize, offset, rowID);
                }
                else{
                    rowID.set(offset);
                }
            }



            c1.close();
            c2.close();
        }
        catch(Exception e){
            if (c1!=null) c1.close();
            if (c2!=null) c2.close();
            e.printStackTrace();
        }
    }




  //**************************************************************************
  //** deleteDuplicates
  //**************************************************************************
  /** Used to find and delete duplicates in a given table.
   */
    public static void deleteDuplicates(String tableName, Database database,
        Long startRow, Long endRow, int pageSize, int numThreads){

        if (!database.getDriver().equals("PostgreSQL")){
            throw new IllegalArgumentException(database.getDriver().getVendor() + " not supported");
        }


        long startTime = System.currentTimeMillis();
        AtomicLong dupCounter = new AtomicLong(0);
        AtomicLong recordCounter = new AtomicLong(0);
        List dups = new LinkedList();
        long minID = 0;
        long maxID = 0;


      //Get min/max row ID
        Connection conn = null;
        try{
            conn = database.getConnection();

            Recordset rs = new Recordset();
            rs.open("select min(id), max(id) from " + tableName, conn);
            if (!rs.EOF){
                minID = rs.getValue(0).toLong();
                maxID = rs.getValue(1).toLong();
            }
            rs.close();
            if (startRow!=null && startRow>minID) minID = startRow;
            if (endRow!=null && endRow<maxID) maxID = endRow;

            conn.close();
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            e.printStackTrace();
        }



      //Spawn threads
        Thread dupProcessor = new Thread(new DupProcessor(tableName, database, dups));
        dupProcessor.start();
        long diff = maxID-minID;
        long numRowsPerThread = Math.round(diff/numThreads);
        startRow = minID;
        ArrayList<Thread> threads = new ArrayList<Thread>();
        for (int i=0; i<numThreads; i++){
            endRow = startRow+numRowsPerThread;
            System.out.println(i + ":\t" + startRow + "-" + endRow);
            //if (i==numThreads-1) endRow = Long.MAX_VALUE;

            Thread thread = new Thread(new DupFinder(tableName, database, startRow, endRow, pageSize, dupCounter, recordCounter, dups));
            threads.add(thread);
            thread.start();

            startRow = endRow;
        }




      //Start console logger
        Runnable statusLogger = new Runnable() {
            private String statusText = "Found 000,000,000 records at 000,000,000,000 records per second";
            public void run() {
                long currTime = System.currentTimeMillis();
                double elapsedTime = (currTime-startTime)/1000; //seconds
                long recordsPerSecond = Math.round((double) recordCounter.get() / elapsedTime);

                for (int i=0; i<statusText.length(); i++){
                    System.out.print("\b");
                }
                statusText = pad(format(dupCounter.get())) + " records at " + pad(format(recordsPerSecond)) + " records per second";

                System.out.print(statusText);
            }
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(statusLogger, 0, 1, TimeUnit.SECONDS);



      //Wait for threads to complete
        while (true) {
            try {
                for (Thread thread : threads){
                    thread.join();
                }
                break;
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


      //Notify DupProcessor that we are done searching
        synchronized(dups){
            dups.add(null);
            dups.notify();
        }




      //Wait for processor to complete
        while (true) {
            try {
                dupProcessor.join();
                break;
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }



      //Clean up
        executor.shutdown();
        threads.clear();


      //Print summary
        System.out.println("\r\n" +
            "Processed " +  format(recordCounter.get()) + " records in " +
            format((System.currentTimeMillis()-startTime)/1000) + " seconds"
        );
        System.out.println("Deleted " + format(dupCounter.get()) + " duplicates");

    }


  //**************************************************************************
  //** DupFinder
  //**************************************************************************
  /** Thread used to find duplicate records in a given table.
   */
    private static class DupFinder implements Runnable {

        private String tableName;
        private Database database;
        private long startRow;
        private long endRow;
        private int pageSize;
        private AtomicLong dupCounter;
        private AtomicLong recordCounter;
        private List dups;

        public DupFinder(String tableName, Database database,
            long startRow, long endRow, int pageSize,
            AtomicLong dupCounter, AtomicLong recordCounter, List dups)
        {
            this.tableName = tableName;
            this.database = database;
            this.pageSize = pageSize;
            this.dupCounter = dupCounter;
            this.recordCounter = recordCounter;
            this.startRow = startRow;
            this.endRow = endRow;
            this.dups = dups;
        }


        public void run() {


            Connection conn;
            try{
                conn = database.getConnection();
            }
            catch(Exception e){
                return;
            }

            int maxPoolSize = 5000;



            try{
                Recordset rs = new Recordset();
                while (startRow<endRow){


                    String sql =
                    "SELECT MIN(ctid) as ctid, id FROM " + tableName +
                    " WHERE ID>" + startRow + " AND ID<=" + (startRow+pageSize) +
                    " GROUP BY id HAVING COUNT(*) > 1";


                    rs.setFetchSize(1000);
                    rs.open(sql, conn);
                    if (rs.EOF){
                        startRow += pageSize;
                    }
                    else{
                        while (rs.hasNext()){
                            long id = rs.getValue("id").toLong();
                            String ctid = rs.getValue("ctid").toString();

                            if (id>endRow){
                                startRow = endRow;
                                break;
                            }


                            synchronized(dups){


                                while (dups.size()>maxPoolSize){
                                    try{
                                        dups.wait();
                                    }
                                    catch(java.lang.InterruptedException e){
                                        break;
                                    }
                                }

                                dups.add(new Object[]{id, ctid});
                                dups.notify();
                            }
                            dupCounter.incrementAndGet();


                            startRow = id;
                            rs.moveNext();
                        }
                        rs.close();
                    }
                    recordCounter.addAndGet(pageSize);
                }


              //Close connection
                conn.close();
            }
            catch(Exception e){

                if (conn!=null) conn.close();
                e.printStackTrace();
            }
        }
    }


  //**************************************************************************
  //** DupProcessor
  //**************************************************************************
  /** Thread used to delete duplicate records in a given table.
   */
    private static class DupProcessor implements Runnable {

        private String tableName;
        private Database database;
        private List dups;


        public DupProcessor(String tableName, Database database, List dups){
            this.tableName = tableName;
            this.database = database;
            this.dups = dups;
        }

        public void run() {

            Connection conn;
            try{
                conn = database.getConnection();
            }
            catch(Exception e){
                return;
            }

            ArrayList<String> stmts = new ArrayList<String>(1000);

            while (true) {

                Object obj = null;
                synchronized (dups) {
                    while (dups.isEmpty()) {
                        try {
                            dups.wait();
                        }
                        catch (InterruptedException e) {
                            return;
                        }
                    }
                    obj = dups.get(0);
                    if (obj!=null) dups.remove(0);
                    dups.notifyAll();
                }

                if (obj!=null){

                    Object[] arr = (Object[]) obj;
                    long id = (long) arr[0];
                    String ctid = (String) arr[1];

                    String stmt = "delete from " + tableName + " where id=" + id + " and ctid<>'" + ctid + "'";
                    stmts.add(stmt);

                    if (stmts.size()>=1000) executeBatch(stmts, conn);
                }
                else{
                    executeBatch(stmts, conn);
                    conn.close();
                    return;
                }
            }
        }

        private void executeBatch(ArrayList<String> stmts, Connection conn){
            if (!stmts.isEmpty()){
                try{
                    StringBuilder str = new StringBuilder();
                    str.append("BEGIN;\n");
                    for (String stmt : stmts){
                        str.append(stmt);
                        str.append(";\n");
                    }
                    str.append("END;\n");
                    conn.execute(str.toString());
                }
                catch(Exception e){
                    e.printStackTrace();
                }
                stmts.clear();
            }
        }

    }


  //**************************************************************************
  //** getJson
  //**************************************************************************
  /** Returns a JSON representation of a record in a Recordset. Column names
   *  are used as keys and the corresponding value is used as the value.
   *  Note that the column names are represented using camel case. If there
   *  are duplicate column names in the Recordset (e.g. "select user.id,
   *  contact.id from ...") the first column name and value is used (e.g.
   *  "user.id" column).
   */
    public static JSONObject getJson(Recordset rs){
        return getJson(rs.getFields());
    }

    public static JSONObject getJson(javaxt.sql.Record record){
        return getJson(record.getFields());
    }

    public static JSONObject getJson(javaxt.sql.Field[] fields){
        JSONObject json = new JSONObject();
        HashSet<String> fieldNames = new HashSet<>();
        for (javaxt.sql.Field field : fields){

            String fieldName = field.getName().toLowerCase();
            fieldName = StringUtils.underscoreToCamelCase(fieldName);

            if (fieldNames.contains(fieldName)) continue;
            fieldNames.add(fieldName);

            JSONObject f = field.toJson();
            json.set(fieldName, f.get("value"));
        }
        return json;
    }


  //**************************************************************************
  //** getJson
  //**************************************************************************
    public static String getJson(String sql, Connection conn, boolean separateRowsAndColumns) throws Exception {
        long x = 0;
        JSONArray cols = new JSONArray();
        StringBuilder json = new StringBuilder();

        if (separateRowsAndColumns){
            json.append("{\"rows\":[");
        }
        else{
            json.append("[");
        }

        Recordset rs = new Recordset();
        rs.open(sql, conn);
        while (rs.hasNext()){

            JSONObject record = DbUtils.getJson(rs);

            if (separateRowsAndColumns){
                JSONArray row = new JSONArray();
                for (javaxt.sql.Field field : rs.getFields()){
                    String fieldName = field.getName().toLowerCase();
                    fieldName = StringUtils.underscoreToCamelCase(fieldName);
                    if (x==0) cols.add(fieldName);

                    JSONValue val = record.get(fieldName);
                    row.add(val);
                }

                if (x>0) json.append(",");
                json.append(row.toString());

            }
            else{
                if (x>0) json.append(",");
                json.append(record.toString());
            }

            rs.moveNext();
            x++;
        }
        conn.close();
        json.append("]");


        if (separateRowsAndColumns){
            json.append(",\"cols\":");
            json.append(cols.toString());
            json.append("}");
        }
        return json.toString();
    }



  //**************************************************************************
  //** format
  //**************************************************************************
  /** Used to format a number with commas.
   */
    private static String format(long l){
        return java.text.NumberFormat.getNumberInstance(Locale.US).format(l);
    }


  //**************************************************************************
  //** pad
  //**************************************************************************
  /** Used to pad a number with white spaces.
   */
    private static String pad(String s){
        while(s.length()<7){
            s = " " + s;
        }
        return s;
    }
}