package javaxt.express.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class StringUtils {
    private StringUtils(){}

    private static DecimalFormat df = new DecimalFormat("#.##");
    static { df.setMaximumFractionDigits(8); }


  //**************************************************************************
  //** camelCaseToUnderScore
  //**************************************************************************
  /** Used to convert a string in camel case (e.g. userID) into underscore
   *  (e.g. user_id). Credit: https://stackoverflow.com/a/50837880/
   */
    public static String camelCaseToUnderScore(String input) {
        StringBuffer result = new StringBuffer();
        boolean begin = true;
        boolean lastUppercase = false;
        for( int i=0; i < input.length(); i++ ) {
            char ch = input.charAt(i);
            if( Character.isUpperCase(ch) ) {
                // is start?
                if( begin ) {
                    result.append(ch);
                } else {
                    if( lastUppercase ) {
                        // test if end of acronym
                        if( i+1<input.length() ) {
                            char next = input.charAt(i+1);
                            if( Character.isUpperCase(next) ) {
                                // acronym continues
                                result.append(ch);
                            } else {
                                // end of acronym
                                result.append('_').append(ch);
                            }
                        } else {
                            // acronym continues
                            result.append(ch);
                        }
                    } else {
                        // last was lowercase, insert _
                        result.append('_').append(ch);
                    }
                }
                lastUppercase=true;
            } else {
                result.append(Character.toUpperCase(ch));
                lastUppercase=false;
            }
            begin=false;
        }
        return result.toString().toLowerCase();
    }


  //**************************************************************************
  //** underscoreToCamelCase
  //**************************************************************************
  /** Used to convert a string with underscores (e.g. user_id) into camel case
   *  (e.g. userID). Credit: https://stackoverflow.com/a/17061543/
   */
    public static String underscoreToCamelCase(String input){
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("_(.)");
        java.util.regex.Matcher m = p.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1).toUpperCase());
        }
        m.appendTail(sb);

        String str = sb.toString();
        if (str.endsWith("Id") && input.toLowerCase().endsWith("_id")){
            str = str.substring(0, str.length()-2) + "ID";
        }
        return str;
    }


  //**************************************************************************
  //** capitalize
  //**************************************************************************
  /** Used to capitalize the first letter in the given string.
   */
    public static String capitalize(String fieldName){
        return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }


  //**************************************************************************
  //** rtrim
  //**************************************************************************
  /** Used to remove whitespaces at the end of a given string
   */
    public static String rtrim(String s) {
        int i = s.length()-1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            i--;
        }
        return s.substring(0,i+1);
    }


  //**************************************************************************
  //** getElapsedTime
  //**************************************************************************
  /** Computes elapsed time between a given startTime and now. Returns a
   *  human-readable string representing the elapsed time.
   */
    public static String getElapsedTime(long startTime){
        long t = System.currentTimeMillis()-startTime;
        if (t<1000) return t + "ms";
        long s = Math.round(t/1000);
        if (s<60) return s + "s";
        long m = Math.round(s/60);
        return m + "m";
    }


  //**************************************************************************
  //** formatFileSize
  //**************************************************************************
    public static String formatFileSize(long size){
        if (size>0){
            size = size/1024;
            if (size<=1) return "1 KB";
            else{
                size = size/1024;
                if (size<=1) return "1 MB";
                else{
                    return format(Math.round(size)) + " MB";
                }
            }
        }
        return "";
    };


  //**************************************************************************
  //** format
  //**************************************************************************
    public static String format(double d){
        return df.format(d);
    }


  //**************************************************************************
  //** format
  //**************************************************************************
    public static String format(double value, int precision) {
        int scale = (int) Math.pow(10, precision);
        double d = (double) Math.round(value * scale) / scale;
        DecimalFormat df = new DecimalFormat("#.##");
        df.setMaximumFractionDigits(precision);
        return df.format(d);
    }


  //**************************************************************************
  //** format
  //**************************************************************************
    public static String format(BigDecimal value, int precision) {
        BigDecimal bd = value.add(BigDecimal.ZERO);
        bd = bd.setScale(precision, BigDecimal.ROUND_DOWN);
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(precision);
        df.setMinimumFractionDigits(0);
        df.setGroupingUsed(false);
        return df.format(bd);
    }


  //**************************************************************************
  //** format
  //**************************************************************************
  /** Used to format a number with commas.
   */
    public static String format(long l){
        return java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(l);
    }


}