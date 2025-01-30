package javaxt.express.utils;
import java.util.*;

//******************************************************************************
//**  Date Utils
//******************************************************************************
/**
 *   Provides static methods used to support web applications
 *
 ******************************************************************************/

public class DateUtils {


    private static final String z = "GMT";
    private static final TimeZone tz = TimeZone.getTimeZone(z);
    private static final TimeZone utc = javaxt.utils.Date.getTimeZone("utc");
    private final static long  jvm_diff;
    static {
        jvm_diff = System.currentTimeMillis()*1000_000-System.nanoTime();
    }


  //**************************************************************************
  //** getCurrentTime
  //**************************************************************************
  /** Returns current time in nanoseconds
   */
    public static long getCurrentTime(){
        return System.nanoTime()+jvm_diff;
    }


  //**************************************************************************
  //** getMilliseconds
  //**************************************************************************
  /** Converts a timestamp in nanoseconds to milliseconds
   */
    public static long getMilliseconds(long nanoseconds){
        return nanoseconds / 1000000;
    }


  //**************************************************************************
  //** getUTC
  //**************************************************************************
    public static TimeZone getUTC(){
        return utc;
    }


  //**************************************************************************
  //** getDate
  //**************************************************************************
  /** Used to convert a UNIX timestamp in milliseconds to a string in GMT
   *  (e.g. "Mon, 20 Feb 2012 13:04:28 GMT"). Note that this method does not
   *  rely on the java.text.SimpleDateFormat for performance reasons.
   *  @param milliseconds Milliseconds since January 1, 1970, 00:00:00 UTC
   */
    public static String getDate(long milliseconds){
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(milliseconds);
        return getDate(cal);
    }

    
  //**************************************************************************
  //** getDate
  //**************************************************************************
  /** Used to convert a Calendar to a string in GMT (see above)
   */
    public static String getDate(Calendar cal){

        //DO NOT USE java.text.SimpleDateFormat for performance reasons!!!


        if (!cal.getTimeZone().equals(tz)){
            cal = (java.util.Calendar) cal.clone();
            cal.setTimeZone(tz);
        }

        StringBuffer str = new StringBuffer(29);
        switch(cal.get(Calendar.DAY_OF_WEEK)){
            case Calendar.MONDAY:    str.append("Mon, "); break;
            case Calendar.TUESDAY:   str.append("Tue, "); break;
            case Calendar.WEDNESDAY: str.append("Wed, "); break;
            case Calendar.THURSDAY:  str.append("Thu, "); break;
            case Calendar.FRIDAY:    str.append("Fri, "); break;
            case Calendar.SATURDAY:  str.append("Sat, "); break;
            case Calendar.SUNDAY:    str.append("Sun, "); break;
        }

        int i = cal.get(Calendar.DAY_OF_MONTH);
        str.append(i<10 ? "0"+i : i);

        switch (cal.get(Calendar.MONTH)) {
            case Calendar.JANUARY:   str.append(" Jan "); break;
            case Calendar.FEBRUARY:  str.append(" Feb "); break;
            case Calendar.MARCH:     str.append(" Mar "); break;
            case Calendar.APRIL:     str.append(" Apr "); break;
            case Calendar.MAY:       str.append(" May "); break;
            case Calendar.JUNE:      str.append(" Jun "); break;
            case Calendar.JULY:      str.append(" Jul "); break;
            case Calendar.AUGUST:    str.append(" Aug "); break;
            case Calendar.SEPTEMBER: str.append(" Sep "); break;
            case Calendar.OCTOBER:   str.append(" Oct "); break;
            case Calendar.NOVEMBER:  str.append(" Nov "); break;
            case Calendar.DECEMBER:  str.append(" Dec "); break;
        }

        str.append(cal.get(Calendar.YEAR));
        str.append(" ");

        i = cal.get(Calendar.HOUR_OF_DAY);
        str.append(i<10 ? "0"+i+":" : i+":");

        i = cal.get(Calendar.MINUTE);
        str.append(i<10 ? "0"+i+":" : i+":");

        i = cal.get(Calendar.SECOND);
        str.append(i<10 ? "0"+i+" " : i+" ");

        str.append(z);
        return str.toString();

        //new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        //return f.format(date);
    }
}