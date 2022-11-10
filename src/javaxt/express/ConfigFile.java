package javaxt.express;
import javaxt.json.JSONObject;
import javaxt.encryption.AES256;
import javaxt.json.JSONValue;

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




  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a File for a given path
   *  @param path Full canonical path to a file or a relative path (relative
   *  to the jarFile)
   */
    public static javaxt.io.File getFile(String path, javaxt.io.File jarFile){
        javaxt.io.File file = new javaxt.io.File(path);
        if (!file.exists()){
            file = new javaxt.io.File(jarFile.MapPath(path));
        }
        return file;
    }


  //**************************************************************************
  //** updateDir
  //**************************************************************************
  /** Used to update a path to a directory defined in a config file. Resolves
   *  both canonical and relative paths (relative to the configFile).
   */
    public static void updateDir(String key, JSONObject config, javaxt.io.File configFile, boolean create){
        if (config!=null && config.has(key)){
            String path = config.get(key).toString();
            if (path==null){
                config.remove(key);
            }
            else{
                path = path.trim();
                if (path.length()==0){
                    config.remove(key);
                }
                else{

                    javaxt.io.Directory dir = new javaxt.io.Directory(path);
                    if (dir.exists()){
                        try{
                            java.io.File f = new java.io.File(path);
                            javaxt.io.Directory d = new javaxt.io.Directory(f.getCanonicalFile());
                            if (!dir.toString().equals(d.toString())){
                                dir = d;
                            }
                        }
                        catch(Exception e){
                        }
                    }
                    else{
                        dir = new javaxt.io.Directory(new java.io.File(configFile.MapPath(path)));
                    }


                    if (!dir.exists() && create) dir.create();


                    if (dir.exists()){
                        config.set(key, dir.toString());
                    }
                    else{
                        config.remove(key);
                    }
                }
            }
        }
    }


  //**************************************************************************
  //** updateFile
  //**************************************************************************
  /** Used to update a path to a file defined in a config file. Resolves
   *  both canonical and relative paths (relative to the configFile).
   */
    public static void updateFile(String key, JSONObject config, javaxt.io.File configFile){
        if (config.has(key)){
            String path = config.get(key).toString();
            if (path==null){
                config.remove(key);
            }
            else{
                path = path.trim();
                if (path.length()==0){
                    config.remove(key);
                }
                else{

                    javaxt.io.File file = new javaxt.io.File(path);
                    if (file.exists()){
                        try{
                            java.io.File f = new java.io.File(path);
                            javaxt.io.File _file = new javaxt.io.File(f.getCanonicalFile());
                            if (!file.toString().equals(_file.toString())){
                                file = _file;
                            }
                        }
                        catch(Exception e){
                        }
                    }
                    else{
                        file = new javaxt.io.File(configFile.MapPath(path));
                    }

                    config.set(key, file.toString());
//                    if (file.exists()){
//                        config.set(key, file.toString());
//                    }
//                    else{
//                        config.remove(key);
//                    }
                }
            }
        }
    }

}