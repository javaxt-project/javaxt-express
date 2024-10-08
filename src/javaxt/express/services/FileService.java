package javaxt.express.services;

import javaxt.express.*;
import javaxt.express.ServiceRequest.Sort;
import javaxt.http.servlet.FormInput;
import javaxt.http.servlet.FormValue;

import javaxt.json.*;
import javaxt.io.File;
import javaxt.io.Directory;

import java.util.*;


//******************************************************************************
//**  FileService
//******************************************************************************
/**
 *   Provides a set of web methods used to manage and view files on the server
 *
 ******************************************************************************/

public class FileService extends WebService {

    private javaxt.io.Directory baseDir;
    private static int numDigits = (Long.MAX_VALUE+"").length();
    private static String zeros = getZeros();
    private static String getZeros(){
        String str = "";
        for (int i=0; i<numDigits; i++) str+="0";
        return str;
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public FileService(){
        baseDir = null;
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public FileService(javaxt.io.Directory baseDir){
        this.baseDir = baseDir;
    }


  //**************************************************************************
  //** getList
  //**************************************************************************
  /** Returns a ServiceResponse with JSON object representing files and
   *  folders in a given path. Optional parameters include filter, sort,
   *  hidden, offset, and limit.
   */
    public ServiceResponse getList(ServiceRequest request) throws Exception {

      //Parse params
        String path = getPath(request);
        Object filter = null;
        String _filter = request.getParameter("filter").toString();
        if (_filter!=null){
            try{
                JSONArray arr = new JSONArray(_filter);
                String[] filters = new String[arr.length()];
                for (int i=0; i<arr.length(); i++){
                    filters[i] = arr.get(i).toString();
                }
                filter = filters;
            }
            catch(Exception e){
                filter = _filter;
            }
        }
        Boolean recursiveSearch = request.getParameter("recursiveSearch").toBoolean();
        if (recursiveSearch==null) recursiveSearch = false;
        if (recursiveSearch && filter==null) recursiveSearch = false;


      //Get directory
        Directory dir = getDirectory(path);


      //Get items
        List list = getList(dir, filter, recursiveSearch);


        boolean isDriveList = dir==null;


      //Get sort
        String sortBy = "name";
        String direction = "ASC";
        Sort sort = request.getSort();
        if (sort!=null && !sort.isEmpty()){
            sortBy = sort.getKeySet().iterator().next();
            direction = sort.get(sortBy);
            sortBy = sortBy.toLowerCase();
        }


      //Check whether to show hidden files and folders
        Boolean showHidden = request.getParameter("hidden").toBoolean();
        if (showHidden==null) showHidden = false;


        TreeMap<String, JSONArray> files = new TreeMap<>();
        TreeMap<String, JSONArray> folders = new TreeMap<>();
        long totalSize = 0;
        for (int i=0; i<list.size(); i++){
            Object obj = list.get(i);
            String name;
            Date date;
            Long size;
            String type;
            boolean isHidden;
            boolean isFolder;

            if (obj instanceof File){
                File f = (File) obj;
                date = f.getDate();
                name = f.getName();
                type = f.getContentType();
                size = f.getSize();
                isHidden = f.isHidden();
                isFolder = false;
            }
            else if (obj instanceof Directory){
                Directory d = (Directory) obj;

                if (isDriveList){
                    name = d.getPath();
                    date = null;
                    type = "Drive";
                    isHidden = false;
                }
                else{
                    name = d.getName();
                    date = d.getDate();
                    type = "Folder";
                    isHidden = d.isHidden();
                }
                size = 0L;
                isFolder = true;
            }
            else{
                continue;
            }

            if (!isHidden) isHidden = name.startsWith(".");
            if (isHidden && !showHidden) continue;


            totalSize+=size;


            JSONArray item = new JSONArray();
            item.add(name);
            item.add(type);
            item.add(date);
            item.add(size);
            if (recursiveSearch){
                if (isFolder){
                    item.add(obj.toString());
                }
                else{
                    File f = (File) obj;
                    item.add(f.getDirectory().toString());
                }
            }


            String key = "";
            if (sortBy.equals("date")){
                if (date==null){
                        //"yyyyMMddHHmmssSSS"
                    key = "00000000000000000";
                }
                else{
                    javaxt.utils.Date d = new javaxt.utils.Date(date);
                    key = d.toLong() + "";
                }
            }
            else if (sortBy.equals("size")){
                String s = size+"";
                int x = numDigits-s.length();
                key = zeros.substring(0, x) + s;
            }
            else if (sortBy.equals("type")){
                key = type;
            }
            key += "|" + name.toLowerCase();


            if (isFolder){
                folders.put(key, item);
            }
            else{
                files.put(key, item);
            }
        }


        Long offset = request.getOffset();
        Long limit = request.getLimit();
        long start = offset==null? 0 : offset;
        long end = limit==null? Long.MAX_VALUE : start+limit;



        Long x = 0L;
        JSONArray arr = new JSONArray();
        if (sortBy.equals("name")){
            Iterator<String> it;
            if (direction.equalsIgnoreCase("DESC")){
                it = files.descendingKeySet().iterator();
                while (it.hasNext()){
                    String k = it.next();
                    JSONArray item = files.get(k);
                    if (x>=start && x<end){
                        arr.add(item);
                    }
                    x++;
                }
                if (x<end){
                    it = folders.descendingKeySet().iterator();
                    while (it.hasNext()){
                        String k = it.next();
                        JSONArray item = folders.get(k);
                        if (x>=start && x<end){
                            arr.add(item);
                        }
                        x++;
                    }
                }
            }
            else{
                it = folders.keySet().iterator();
                while (it.hasNext()){
                    String k = it.next();
                    JSONArray item = folders.get(k);
                    if (x>=start && x<end){
                        arr.add(item);
                    }
                    x++;
                }
                if (x<end){
                    it = files.keySet().iterator();
                    while (it.hasNext()){
                        String k = it.next();
                        JSONArray item = files.get(k);
                        if (x>=start && x<end){
                            arr.add(item);
                        }
                        x++;
                    }
                }
            }
        }
        else{
            TreeMap<String, JSONArray> items;
            if (files.size()>folders.size()){
                items = files;
                items.putAll(folders);
            }
            else{
                items = folders;
                items.putAll(files);
            }

            Iterator<String> it;
            if (direction.equalsIgnoreCase("DESC")){
                it = items.descendingKeySet().iterator();
            }
            else{
                it = items.keySet().iterator();
            }
            while (it.hasNext()){
                String k = it.next();
                JSONArray item = items.get(k);
                if (x>=start && x<end){
                    arr.add(item);
                }
                x++;
            }
        }


      //Return json response
        JSONObject json = new JSONObject();
        if (baseDir==null){
            json.set("dir", dir);
            json.set("pathSeparator", Directory.PathSeparator);
        }
        else{
            String a = baseDir.toString().replace("\\", "/");
            String b = dir.toString().replace("\\", "/");
            String d = b.substring(a.length());
            if (!d.startsWith("/")) d = "/" + d;
            json.set("dir", d);
            json.set("pathSeparator", "/");
        }
        json.set("items", arr);
        json.set("count", files.size()+folders.size());
        json.set("size", totalSize);

        return new ServiceResponse(json);
    }


  //**************************************************************************
  //** getDirectory
  //**************************************************************************
    private Directory getDirectory(String path) throws Exception {

        Directory dir;
        if (path==null) path = "";
        else path = path.trim();
        if (path.isEmpty()){
            if (baseDir==null){
                //isDriveList = true;
                dir = null;
            }
            else{
                dir = baseDir;
            }
        }
        else{
            if (baseDir==null){
                dir = new Directory(path);
            }
            else{
                path = getPath(path);
                dir = new Directory(path);
            }
        }
        return dir;
    }


  //**************************************************************************
  //** getList
  //**************************************************************************
    private List getList(Directory dir, Object filter, boolean recursiveSearch){

        List list;
        if (dir==null){
            list = new LinkedList<>();
            for (Directory d : Directory.getRootDirectories()){
                if (recursiveSearch){
                    for (File file : d.getFiles(filter, true)){
                        list.add(file);
                    }
                }
                else{
                    list.add(d);
                }
            }
        }
        else{
            if (recursiveSearch){
                list = new LinkedList<>();
                for (File file : dir.getFiles(filter, true)){
                    list.add(file);
                }
            }
            else{
                list = dir.getChildren(false, filter);
            }
        }
        return list;
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a ServiceResponse with an InputStream associated with a file.
   *  Headers for the ServiceResponse are set with file metadata (e.g. content
   *  type, file size, and date). Caller can add additional response headers
   *  such as the content disposition to make the file "downloadable". See
   *  ServiceResponse.setContentDisposition() for more information.
   */
    public ServiceResponse getFile(ServiceRequest request) throws Exception {

        String path = getPath(request);
        if (path.isEmpty()) return new ServiceResponse(400, "Path is required");


        File file = getFile(path);
        if (!file.exists()) return new ServiceResponse(404); //Not Found

        try{
            ServiceResponse response = new ServiceResponse(file.getInputStream());
            response.set("name", file.getName());
            //response.setContentDisposition(file.getName());
            response.setContentLength(file.getSize());
            response.setContentType(file.getContentType());
            response.setDate(new javaxt.utils.Date(file.getDate()));
            return response;
        }
        catch(Exception e){
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** getPhysicalFile
  //**************************************************************************
  /** Returns a File associated with a given ServiceRequest
   */
    public File getPhysicalFile(ServiceRequest request){
        String path = getPath(request);
        return getFile(path);
    }


//  //**************************************************************************
//  //** moveFile
//  //**************************************************************************
//    public ServiceResponse moveFile(ServiceRequest request) throws Exception {
//
//      //Get source file
//        String path = getPath(request);
//        if (path.isEmpty()) return new ServiceResponse(400, "Path is required");
//        File sourceFile = getFile(path);
//        if (!sourceFile.exists()) return new ServiceResponse(404);
//
//      //Get destination file
//        String destination = request.getParameter("destination").toString();
//        if (destination==null) destination = "";
//        else destination = destination.trim();
//        if (destination.isEmpty()) return new ServiceResponse(400, "destination is required");
//        File destinationFile = getFile(destination);
//
//      //Get overwrite flag
//        Boolean overwrite = request.getParameter("overwrite").toBoolean();
//        if (overwrite==null) overwrite = false;
//        if (destinationFile.exists() && !overwrite){
//            return new ServiceResponse(400, "Destination file exists");
//        }
//
//      //Move file and return response
//        File f = sourceFile.moveTo(destinationFile, overwrite);
//        if (f.equals(destinationFile)){
//            return new ServiceResponse(200, "Successfully moved file");
//        }
//        else{
//            return new ServiceResponse(500, "Failed to move file");
//        }
//    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
    private File getFile(String path){

        File file;
        if (baseDir==null){
            file = new File(path);
        }
        else{
            path = getPath(path);
            if (path.endsWith("/")) path = path.substring(0, path.length()-1);
            file = new File(path);
        }
        return file;
    }


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Returns a string representing a fully qualified path to a file or
   *  directory. Throws a RuntimeException if the relative path contains
   *  illegal path directives (e.g. ".."). Note that the returned path always
   *  ends with a "/" character. Trim as needed (e.g. file path).
   *  @param path Either a relative path or a fully qualified path to a file
   *  or directory.
   */
    private String getPath(String path){
        path = path.replace("\\", "/");


      //Check if path starts with baseDir. Trim as needed.
        String a = baseDir.toString().replace("\\", "/");
        String b = path;
        if (!b.endsWith("/")) b += "/";
        if (b.startsWith(a)){
            path = path.substring(a.length());
        }


      //Remove leading "/" character
        if (path.startsWith("/")) path = path.substring(1).trim();


        String[] arr = (path.length()>0) ? path.split("/") : new String[0];
        path = baseDir.toString();

      //Append relative path to the baseDir
        for (String str : arr){
            str = str.trim();
            if (str.equals("") || str.equals(".") || str.equals("..")){
                throw new RuntimeException("Illegal path");
            }
            path += str + "/";
        }

        return path;
    }


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Returns the "path" parameter in the ServiceRequest
   */
    private String getPath(ServiceRequest request){

        //TODO: check url path

        String path = request.getParameter("path").toString();

        if (path==null) path = "";
        else path = path.trim();

        return path;
    }


  //**************************************************************************
  //** upload
  //**************************************************************************
  /** Used to upload files to the server
   *  @param request ServiceRequest with "multipart/form-data" encoded data
   */
    public ServiceResponse upload(ServiceRequest request) throws Exception {
        return upload(request, null);
    }


  //**************************************************************************
  //** upload
  //**************************************************************************
  /** Used to upload files to the server. The ServiceRequest must contain
   *  "multipart/form-data" encoded data. The form data must includes a "path"
   *  variable and at least one file.
   *  @param request ServiceRequest with "multipart/form-data" encoded data
   *  @param callback Optional callback. Called after a file has been uploaded.
   *  If the request payload contains multiple files, the callback will be
   *  called multiple times - once for each file after it had been uploaded.
   *  The callback record will contain the following fields:
   *  <ul>
   *  <li>file: A javaxt.io.File representing uploaded file on the server</li>
   *  <li>path: String representing the relative or absolute path to the
   *  upload directory</li>
   *  <li>op: String representing the operation that was performed (e.g.
   *  "create" or "update")</li>
   *  </ul>
   *  Example:
   <pre>
    fileService.upload(request, (Record record)->{

        String path = record.get("path").toString();
        javaxt.io.File file = (javaxt.io.File) record.get("file").toObject();
        path += file.getName();

        NotificationService.notify(record.get("op").toString(), "File", new javaxt.utils.Value(path));
    });
   </pre>
   */
    public ServiceResponse upload(ServiceRequest request, Callback callback) throws Exception {


        javaxt.io.Directory dir = null;

        java.util.Iterator<FormInput> it = request.getRequest().getFormInputs();
        while (it.hasNext()){
            FormInput input = it.next();
            if (input.isFile()){
                String fileName = input.getFileName();
                FormValue value = input.getValue();
                if (dir!=null){
                    javaxt.io.File file = new javaxt.io.File(dir, fileName);
                    String op = file.exists() ? "update" : "create";

                  //Save file
                    value.toFile(file.toFile());


                  //Call the callback
                    if (callback!=null){
                        String path;
                        if (baseDir==null){
                            path = dir.toString();
                        }
                        else{
                            String a = baseDir.toString().replace("\\", "/");
                            String b = dir.toString().replace("\\", "/");
                            path = b.substring(a.length());
                            if (!path.startsWith("/")) path = "/" + path;
                        }

                        javaxt.utils.Record record = new javaxt.utils.Record();
                        record.set("op", op);
                        record.set("path", path);
                        record.set("file", file);

                        callback.call(record);
                    }
                }
            }
            else{
                String param = input.getName();

                if (param.equalsIgnoreCase("path")){
                    String path = input.getValue().toString();
                    if (path==null) path = "";
                    else path = path.trim();
                    dir = getDirectory(path);
                }
            }
        }
        return new ServiceResponse(200);
    }


  //**************************************************************************
  //** Callback Interface
  //**************************************************************************
    public static interface Callback {
        public void call(javaxt.utils.Record record);
    }

}