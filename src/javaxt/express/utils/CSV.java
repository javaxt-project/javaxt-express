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

    public static final String UTF8_BOM = "\uFEFF";

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