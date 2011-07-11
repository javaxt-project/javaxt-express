package javaxt.portal;

//******************************************************************************
//**  Portal User
//******************************************************************************
/**
 *   Enter class description here
 *
 ******************************************************************************/

public class User {

    private String username;
    private String password;
    private javaxt.io.File keystore;

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of User. */

    public User(String username, String password, javaxt.io.File keystore) {

        if (username!=null) username = username.trim();
        if (password!=null) password = password.trim();

        this.username = username;
        this.password = password;
        this.keystore = keystore;


    }


    public void authenticate() throws Exception {

        if (keystore==null || keystore.exists()==false)
            throw new Exception("Keystore not found");

        boolean foundUser = false;
        User[] users = getUsers();
        if (users!=null){
            for (int i=0; i<users.length; i++){
                User user = users[i];
                if (user.equals(this)){
                    foundUser = true;
                    break;
                }
            }
        }
        
        if (!foundUser)
            throw new Exception("Invalid Username or Password");
    }


    private User[] getUsers(){
        String text = keystore.getText();
        if (text!=null){
            String[] arr = text.trim().split("\r\n");
            User[] users = new User[arr.length];
            for (int i=0; i<arr.length; i++){
                String[] col = arr[i].trim().split("\t");
                String username = col[0];
                String password = col[1];
                users[i] = new User(username,password,keystore);
            }
            return users;
        }
        return null;
    }


    
    public boolean equals(Object obj){
        if (obj!=null){
            if (obj instanceof User){
                User user = (User) obj;

                if (user.username==null || user.password==null) return false;
                if (this.username==null || this.password==null) return false;

                if (user.username.length()<5 || user.password.length()<5) return false;
                if (this.username.length()<5 || this.password.length()<5) return false;
                

                if (user.username.equals(this.username) &&
                    user.password.equals(this.password)){
                    return true;
                }
            }
        }
        return false;
    }



}