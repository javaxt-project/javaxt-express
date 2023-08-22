package javaxt.express.utils;
import java.util.ArrayList;

//******************************************************************************
//**  CSV
//******************************************************************************
/**
 *   Provides static methods used to parse comma and tab delimited files
 *
 ******************************************************************************/

public class CSV {

    public static final String TAB_DELIMITER = "\t";
    public static final String COMMA_DELIMITER = ",";
    //public static final String UTF8_BOM = "\uFEFF";

  //**************************************************************************
  //** Columns
  //**************************************************************************
  /** Used to represent column values
   */
    public static class Columns {
        private ArrayList<javaxt.utils.Value> cols;
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
        public int length(){
            return cols.size();
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
  /** Returns a substring for the given data, ending at the first line break
   *  that is not inside a quote. Example usage:
    <pre>

      //Get input stream
        javaxt.io.File file; //create file!
        java.io.InputStream is = file.getInputStream();

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

      //Close input stream
        is.close();
    </pre>
   */
    public static String readLine(java.io.InputStream is) throws java.io.IOException {

        StringBuilder str = new StringBuilder();
        boolean insideDoubleQuotes = false;
        int i = 0;
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
  //** getByteOrderMark
  //**************************************************************************
  /** Returns end position of the Byte Order Mark (BOM). Example usage:
    <pre>
        int bom = CSV.getByteOrderMark(header);
        if (bom>-1) header = header.substring(bom);
    </pre>
   */
    public static int getByteOrderMark(String str){

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
                col = col.substring(1, col.length()-1).trim();
                if (col.length()==0) col = null;
            }
        }
        return new javaxt.utils.Value(col);
    }
}