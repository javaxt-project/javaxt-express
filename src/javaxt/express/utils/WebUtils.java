package javaxt.express.utils;
import java.util.Calendar;
import java.util.TimeZone;

public class WebUtils {

    private static final String z = "GMT";
    private static final TimeZone tz = TimeZone.getTimeZone(z);

    private WebUtils(){}

  //**************************************************************************
  //** getDate
  //**************************************************************************
  /** Used to convert a date to a string (e.g. "Mon, 20 Feb 2012 07:22:20 EST").
   */
    public static String getDate(long milliseconds){
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(milliseconds);
        return getDate(cal);
    }


  //**************************************************************************
  //** getDate
  //**************************************************************************
  /** Used to convert a date to a string (e.g. "Mon, 20 Feb 2012 07:22:20 EST").
   *  This method does not rely on the java.text.SimpleDateFormat for
   *  performance reasons.
   */
    public static String getDate(Calendar cal){

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