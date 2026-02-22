package javaxt.express.email;

public class EmailUtils {
    private EmailUtils(){}


  //**************************************************************************
  //** isValidEmail
  //**************************************************************************
  /** Validates email format
   */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }
}