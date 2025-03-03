package javaxt.express.utils;
import java.util.HashMap;
import java.util.ArrayList;

//******************************************************************************
//**  CSV
//******************************************************************************
/**
 *   Provides static methods used to parse tabular data stored in plain text
 *   where records (aka rows) are separated with a line break and columns are
 *   delimited with a character (comma, tab, pipe, etc). CSV files is an
 *   example of such tabular data which uses commas to separate values in a
 *   row. <p/>
 *
 *   Here's an example of how to parse a CSV file using the static methods
 *   found in this class:
 <pre>
    javaxt.io.File csvFile = new javaxt.io.File("/temp/employees.csv");
    try (java.io.BufferedReader br = csvFile.getBufferedReader("UTF-8")){


      //Read header
        String header = CSV.readLine(br);

      //Remove the Byte Order Mark (BOM) if there is one
        int bom = CSV.getByteOrderMark(header);
        if (bom>-1) header = header.substring(bom);


      //Parse header
        ArrayList&lt;String> headers = new ArrayList&lt;>();
        for (javaxt.utils.Value col : CSV.getColumns(header, ",")){
            headers.add(col.toString());
        }


      //Read rows
        String row;
        while (!(row=CSV.readLine(br)).isEmpty()){


          //Parse row
            CSV.Columns columns = CSV.getColumns(row, ",");
            for (int i=0; i&lt;columns.length(); i++){
                String colName = headers.get(i);
                String colValue = columns.get(i).toString();
                System.out.println(colName + ": " + colValue);
            }

            System.out.println("---------------------------");
        }

    }
 </pre>
 *
 *
 ******************************************************************************/

public class CSV {

    public static final String TAB_DELIMITER = "\t";
    public static final String COMMA_DELIMITER = ",";


  //**************************************************************************
  //** Columns
  //**************************************************************************
  /** Class used to encapsulate columns in a row
   */
    public static class Columns implements Iterable<javaxt.utils.Value> {

        private ArrayList<javaxt.utils.Value> cols;
        private HashMap<String, Integer> header;

        public Columns(){
            cols = new ArrayList<>();
        }

        public void add(javaxt.utils.Value col){
            cols.add(col);
        }

        public javaxt.utils.Value get(int idx){
            try{
                return cols.get(idx);
            }
            catch(Exception e){
                return new javaxt.utils.Value(null);
            }
        }

        public javaxt.utils.Value get(String key){
            Integer idx = header.get(key.toLowerCase());
            if (idx==null) return new javaxt.utils.Value(null);
            return get(idx);
        }

        public void setHeader(Columns header){
            if (header==null) return;
            this.header = new HashMap<>();
            int x = 0;
            for (javaxt.utils.Value val : header){
                String str = val.toString();
                if (str!=null) str = str.toLowerCase();
                this.header.put(str, x);
                x++;
            }
        }

        public int length(){
            return cols.size();
        }

        @Override
        public java.util.Iterator<javaxt.utils.Value> iterator() {
            return cols.iterator();
        }
    }


  //**************************************************************************
  //** getColumns
  //**************************************************************************
  /** Returns column values for a given row
   */
    public static Columns getColumns(String row, String delimiter){
        Columns cols = new Columns();

        boolean insideDoubleQuotes = false;
        boolean isCSV = delimiter.equals(",");
        StringBuilder str = new StringBuilder();
        String c;

        for (int i=0; i<row.length(); i++){

            c = row.substring(i,i+1);

            if (c.equals("\"") && isCSV){
                if (!insideDoubleQuotes) insideDoubleQuotes = true;
                else insideDoubleQuotes = false;
            }

            if (c.equals(delimiter) && !insideDoubleQuotes){
                cols.add(getValue(str));
                str = new StringBuilder();
            }
            else{
                str.append(c);
            }
        }

      //Add last column
        cols.add(getValue(str));


        return cols;
    }


  //**************************************************************************
  //** readLine
  //**************************************************************************
  /** Returns a substring for the given data, ending at the first line break
   *  that is not inside a quote
   */
    public static String readLine(String data){

        StringBuilder str = new StringBuilder();
        boolean insideDoubleQuotes = false;
        for (int i=0; i<data.length(); i++){
            char c = data.charAt(i);
            if (c=='"'){
                if (insideDoubleQuotes) insideDoubleQuotes = false;
                else insideDoubleQuotes = true;
            }

            if (c=='\r' || c=='\n'){
                if (!insideDoubleQuotes) break;
            }
            str.append(c);
        }
        return str.toString();
    }


  //**************************************************************************
  //** readLine
  //**************************************************************************
  /** Returns a row of data from an InputStream. This method will read
   *  characters one at a time until it reaches a line break that is not
   *  inside a double quote. Depending on the source of the InputStream, this
   *  method may be significantly slower than the other readLine() method that
   *  uses a BufferedReader. Example usage:
    <pre>

      //Create an input stream
        java.io.InputStream is = ...

      //Read header
        String header = CSV.readLine(is);
        int bom = CSV.getByteOrderMark(header);
        if (bom>-1) header = header.substring(bom);
        console.log(header);

      //Read rows
        String row;
        while (!(row=CSV.readLine(is)).isEmpty()){
            console.log(row);
        }

    </pre>
   */
    public static String readLine(java.io.InputStream is) throws java.io.IOException {

        StringBuilder str = new StringBuilder();
        boolean insideDoubleQuotes = false;
        int i;
        while((i=is.read())!=-1) {
            char c = (char) i;

            if ((c=='\r' || c=='\n') && str.length()==0) continue;

            if (c=='"'){
                if (insideDoubleQuotes) insideDoubleQuotes = false;
                else insideDoubleQuotes = true;
            }

            if (c=='\r' || c=='\n'){
                if (!insideDoubleQuotes) break;
            }
            str.append(c);
        }
        return str.toString();
    }


  //**************************************************************************
  //** readLine
  //**************************************************************************
  /** Returns a row of data from a BufferedReader. Unlike the BufferedReader
   *  readLine() method, this method will not stop at line breaks inside a
   *  double quote. Note that a BufferedReader is significantly faster than
   *  an InputStream when reading files. Example usage:
    <pre>

      //Open input stream from an javaxt.io.File
        try (java.io.BufferedReader is = file.getBufferedReader("UTF-8")){

          //Read header
            String header = CSV.readLine(is);
            int bom = CSV.getByteOrderMark(header);
            if (bom>-1) header = header.substring(bom);
            console.log(header);

          //Read rows
            String row;
            while (!(row=CSV.readLine(is)).isEmpty()){
                console.log(row);
            }

        }
    </pre>
   */
    public static String readLine(java.io.BufferedReader reader) throws java.io.IOException {

        StringBuilder str = new StringBuilder();
        boolean insideDoubleQuotes = false;
        int i;

        while((i=reader.read())!=-1) {
            char c = (char) i;

            if ((c=='\r' || c=='\n') && str.length()==0) continue;

            if (c=='"'){
                if (insideDoubleQuotes) insideDoubleQuotes = false;
                else insideDoubleQuotes = true;
            }

            if (c=='\r' || c=='\n'){
                if (!insideDoubleQuotes) break;
            }
            str.append(c);
        }
        return str.toString();
    }


  //**************************************************************************
  //** parseHeader
  //**************************************************************************
  /** Parses a header (e.g. first row in a file) into columns. Removes the
   *  Byte Order Mark (BOM) as needed.
   */
    public static Columns parseHeader(String header, String delimiter){
        int bom = CSV.getByteOrderMark(header);
        if (bom>-1) header = header.substring(bom);
        return CSV.getColumns(header, delimiter);
    }


  //**************************************************************************
  //** parseHeader
  //**************************************************************************
  /** Parses a header (e.g. first row in a file) into columns. Removes the
   *  Byte Order Mark (BOM) as needed.
   */
    public static Columns parseHeader(java.io.InputStream is, String delimiter) throws java.io.IOException {
        return parseHeader(CSV.readLine(is), delimiter);
    }


  //**************************************************************************
  //** parseHeader
  //**************************************************************************
  /** Parses a header (e.g. first row in a file) into columns. Removes the
   *  Byte Order Mark (BOM) as needed.
   */
    public static Columns parseHeader(java.io.BufferedReader reader, String delimiter) throws java.io.IOException {
        return parseHeader(CSV.readLine(reader), delimiter);
    }


  //**************************************************************************
  //** getByteOrderMark
  //**************************************************************************
  /** Returns end position of the Byte Order Mark (BOM). Example usage:
    <pre>
        int bom = CSV.getByteOrderMark(header);
        if (bom>-1) header = header.substring(bom);
    </pre>
   */
    public static int getByteOrderMark(String str){

        if (str.startsWith("\uFEFF")) return 1;


        if (str.length()<2) return -1;

        int a=-1, b=-1, c=-1, d=-1;
        if (str.length()>1){
            a = (int) str.charAt(0);
            b = (int) str.charAt(1);
            if (a==254 && b==255) return 2; //UTF-16 (BE)
            if (b==255 && b==254) return 2; //UTF-16 (LE)
        }

        if (str.length()>2){
            c = (int) str.charAt(2);
            if (a==239 && b==187 && c==191) return 3; //UTF-8
            if (a==43 && b==47 && c==118) return 3; //UTF-7
            if (a==247 && b==100 && c==76) return 3; //UTF-1
        }

        if (str.length()>3){
            d = (int) str.charAt(3);
            if (a==0 && b==0 && c==254 && d==255) return 4; //UTF-32 (BE)
            if (a==255 && b==254 && c==0 && d==0) return 4; //UTF-32 (LE)
        }

        return -1;
    }


  //**************************************************************************
  //** startsWithByteOrderMark
  //**************************************************************************
  /** Returns true if the given string starts with a Byte Order Mark (BOM)
   */
    public static boolean startsWithByteOrderMark(String str){
        return getByteOrderMark(str)>-1;
    }


  //**************************************************************************
  //** getValue
  //**************************************************************************
  /** Returns a value for a given column
   */
    private static javaxt.utils.Value getValue(StringBuilder str){
        String col = str.toString().trim();
        if (col.length()==0) col = null;
        if (col!=null){
            if (col.startsWith("\"") && col.endsWith("\"")){
                if (col.length()>1){
                    col = col.substring(1, col.length()-1).trim();
                    if (col.length()==0) col = null;
                }
                else{
                    col = null;
                }
            }
        }
        return new javaxt.utils.Value(col);
    }
}