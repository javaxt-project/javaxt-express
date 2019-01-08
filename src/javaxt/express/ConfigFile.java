package javaxt.express;
import javaxt.json.JSONObject;
import javaxt.encryption.AES256;

//******************************************************************************
//**  ConfigFile
//******************************************************************************
/**
 *   Used to access and save configuration information to an encrypted JSON 
 *   file.
 *
 ******************************************************************************/

public class ConfigFile {
    
    private javaxt.io.File file;
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public ConfigFile(javaxt.io.File file){
        this.file = file;
    }
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public ConfigFile(String path){
        this(new javaxt.io.File(path));
    }
    
    
  //**************************************************************************
  //** exists
  //**************************************************************************
  /** Returns true if the file exists. 
   */
    public boolean exists(){
        return file.exists();
    }
    
    
    
    public javaxt.io.Directory getDirectory(){
        return file.getDirectory();
    }
    
    
  //**************************************************************************
  //** delete
  //**************************************************************************
  /** Used to delete the config file, if it exists.
   */
    public void delete(){
        file.delete();
    }

    
  //**************************************************************************
  //** getConfig
  //**************************************************************************
  /** Used to decrypt and parse a config file (json document).
   */
    public JSONObject getConfig(String username, String password) 
        throws java.security.InvalidKeyException, Exception {
        
        return new JSONObject(
            AES256.decrypt(
                file.getBytes().toByteArray(), 
                generatePassword(username, password)
            )
        );
    }
    
    
  //**************************************************************************
  //** save
  //**************************************************************************
  /** Used to encrypt and save the config file. 
   */
    public void save(JSONObject config, String username, String password) 
        throws java.security.InvalidKeyException, Exception {
        file.write(AES256.encrypt(
                config.toString(),
                generatePassword(username, password)
            )
        );
    }
    
    
  //**************************************************************************
  //** generatePassword
  //**************************************************************************
  /** Used to generate a password/key used to encrypt/decrypt the config file.
   */
    private static String generatePassword(String username, String password) throws Exception {
        return ( javaxt.utils.Base64.encode(
            (username + "/" + password).getBytes("UTF-8")
        ));
    }
    
}