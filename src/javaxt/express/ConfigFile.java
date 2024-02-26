package javaxt.express;
import javaxt.json.JSONObject;
import javaxt.encryption.AES256;

//******************************************************************************
//**  ConfigFile
//******************************************************************************
/**
 *   Used to access and save configuration information stored in a JSON file.
 *   Provides an option to encrypt the JSON file.
 *
 ******************************************************************************/

public class ConfigFile {

    private javaxt.io.File file;
    private JSONObject config;


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


  //**************************************************************************
  //** getDirectory
  //**************************************************************************
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
  /** Returns the current config (json document).
   */
    public JSONObject getConfig(){
        if (config==null) config = file.getJSONObject();
        return config;
    }


  //**************************************************************************
  //** getConfig
  //**************************************************************************
  /** Used to decrypt and parse a config file (json document).
   */
    public JSONObject getConfig(String username, String password)
        throws java.security.InvalidKeyException, Exception {
        if (config==null) config = new JSONObject(
            AES256.decrypt(
                file.getBytes().toByteArray(),
                generatePassword(username, password)
            )
        );
        return config;
    }


  //**************************************************************************
  //** save
  //**************************************************************************
  /** Used to save the config file.
   */
    public void save(){
        file.write(getConfig());
    }


  //**************************************************************************
  //** save
  //**************************************************************************
  /** Used to encrypt and save the config file.
   */
    public void save(String username, String password)
        throws java.security.InvalidKeyException, Exception {
        save(getConfig(), username, password);
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
  /** Used to generate a password/key used to encrypt/decrypt a config file.
   */
    private static String generatePassword(String username, String password) throws Exception {
        return ( javaxt.utils.Base64.encode(
            (username + "/" + password).getBytes("UTF-8")
        ));
    }


  //**************************************************************************
  //** updateDir
  //**************************************************************************
  /** Used to update a path to a directory defined in the config file.
   *  Resolves both canonical and relative paths (relative to the configFile).
   */
    public void updateDir(String key, boolean create){
        updateDir(key, getConfig(), file, create);
    }


  //**************************************************************************
  //** updateDir
  //**************************************************************************
  /** Used to update a path to a directory defined in a given config. Resolves
   *  both canonical and relative paths (relative to the configFile).
   */
    public void updateDir(String key, JSONObject config, boolean create){
        updateDir(key, config, file, create);
    }


  //**************************************************************************
  //** updateDir
  //**************************************************************************
  /** Used to update a path to a directory defined in a given config. Resolves
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

                  //Get directory
                    javaxt.io.Directory dir;
                    if (isRelativePath(path)){
                        path = configFile.MapPath(path);
                        dir = new javaxt.io.Directory(new java.io.File(path));
                    }
                    else{
                        dir = new javaxt.io.Directory(path);
                    }


                  //Get canonical path
                    if (dir.exists()){
                        try{
                            javaxt.io.Directory d = new javaxt.io.Directory(dir.toFile().getCanonicalFile());
                            if (!dir.toString().equals(d.toString())){
                                dir = d;
                            }
                        }
                        catch(Exception e){}
                    }


                  //Create directory as needed
                    if (!dir.exists() && create) dir.create();


                  //Update config
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
  /** Used to update a path to a file defined in the config file. Resolves
   *  both canonical and relative paths (relative to the configFile).
   */
    public void updateFile(String key){
        updateFile(key, getConfig(), file);
    }


  //**************************************************************************
  //** updateFile
  //**************************************************************************
  /** Used to update a path to a file defined a given config. Resolves both
   *  canonical and relative paths (relative to the configFile).
   */
    public void updateFile(String key, JSONObject config){
        updateFile(key, config, file);
    }


  //**************************************************************************
  //** updateFile
  //**************************************************************************
  /** Used to update a path to a file defined in a config. Resolves both
   *  canonical and relative paths (relative to a configFile).
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

                  //Get file
                    javaxt.io.File file = getFile(path, configFile);


                  //Get canonical path
                    if (file.exists()){
                        try{
                            javaxt.io.File f = new javaxt.io.File(file.toFile().getCanonicalFile());
                            if (!file.toString().equals(f.toString())){
                                file = f;
                            }
                        }
                        catch(Exception e){}
                    }


                  //Update config
                    config.set(key, file.toString());
                }
            }
        }
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a File for a given path. Resolves both canonical and relative
   *  paths (relative to the given file).
   *  @param path Full canonical path or a relative path
   *  @param file If a relative path is given, the path is resolved to the
   *  given file
   */
    public static javaxt.io.File getFile(String path, javaxt.io.File file){
        if (isRelativePath(path)){
            return new javaxt.io.File(file.MapPath(path));
        }
        else{
            return new javaxt.io.File(path);
        }
    }


  //**************************************************************************
  //** isRelativePath
  //**************************************************************************
  /** Returns true is the given path appears to be relative
   */
    private static boolean isRelativePath(String path){
        path = path.replace("\\", "/");
        if (path.startsWith("/")) return false;
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")){
            if (path.indexOf(":")==1) return false;
        }
        return true;
    }

}