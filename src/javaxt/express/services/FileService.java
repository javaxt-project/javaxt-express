package javaxt.express.services;

import javaxt.express.*;
import javaxt.express.ServiceRequest.Sort;
import javaxt.http.servlet.FormInput;
import javaxt.http.servlet.FormValue;

import javaxt.json.*;
import javaxt.io.File;
import javaxt.io.Directory;
import static javaxt.utils.Console.console;

import java.util.*;

//******************************************************************************
//**  FileService
//******************************************************************************
/**
 *   Provides a set of web methods used to manage and view files on the server
 *
 ******************************************************************************/

public class FileService {

    private static int numDigits = (Long.MAX_VALUE+"").length();
    private static String zeros = getZeros();
    private static String getZeros(){
        String str = "";
        for (int i=0; i<numDigits; i++) str+="0";
        return str;
    }

    private javaxt.io.Directory uploadDir;

  //**************************************************************************
  //** setUploadDirectory
  //**************************************************************************
    public void setUploadDirectory(javaxt.io.Directory uploadDir){
        this.uploadDir = uploadDir;
    }

  //**************************************************************************
  //** getUploadDirectory
  //**************************************************************************
    public javaxt.io.Directory getUploadDirectory(){
        return uploadDir;
    }
    

  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
    public ServiceResponse getServiceResponse(javaxt.express.ServiceRequest request) {

        try{

            String path = request.getPath(0).toString();
            if (path==null || path.equals("")) path = "list";
            else path = path.toLowerCase();
            String method = request.getRequest().getMethod();


            if (path.equals("list")){
                if (method.equals("GET") || method.equals("POST")){
                    return list(request);
                }
            }
            else if (path.equals("upload")){
                if (method.equals("POST")){
                    return upload(request);
                }
            }

            return new ServiceResponse(501, "Not Implemented");
        }
        catch(Exception e){
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** list
  //**************************************************************************
    private ServiceResponse list(javaxt.express.ServiceRequest req) throws Exception {
        ServiceRequest request = new ServiceRequest(req);
        String path = request.getParameter("path").toString();


        if (path==null) path = "";
        else path = path.trim();

      //Get items
        List list;
        if (path.length()==0){
            list = new LinkedList<>();
            for (Directory dir : Directory.getRootDirectories()){
                list.add(dir);
            }
        }
        else{
            Directory dir = new Directory(path);
            path = dir.toString();
            list = dir.getChildren();
        }


        String sortBy = "name";
        String direction = "ASC";
        Sort sort = request.getSort();
        if (sort!=null && !sort.isEmpty()){
            sortBy = sort.getKeySet().iterator().next();
            direction = sort.get(sortBy);
            sortBy = sortBy.toLowerCase();
        }



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
                boolean isDrive = path.length()==0;
                if (isDrive){
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


        JSONObject json = new JSONObject();
        json.set("dir", path);
        json.set("items", arr);
        json.set("count", files.size()+folders.size());
        json.set("size", totalSize);
        json.set("pathSeparator", Directory.PathSeparator);
        return new ServiceResponse(json);
    }


  //**************************************************************************
  //** upload
  //**************************************************************************
    private ServiceResponse upload(javaxt.express.ServiceRequest req) throws Exception {
        if (uploadDir==null) return new ServiceResponse(501);

        java.util.Iterator<FormInput> it = req.getRequest().getFormInputs();
        while (it.hasNext()){
            FormInput input = it.next();
            if (input.isFile()){
                String fileName = input.getFileName();
                FormValue value = input.getValue();
                value.toFile(new java.io.File(uploadDir.toFile(), fileName));
            }
        }

        return new ServiceResponse(200);
    }


  //**************************************************************************
  //** ServiceRequest
  //**************************************************************************
    private class ServiceRequest {
        private javaxt.express.ServiceRequest request;

        public ServiceRequest(javaxt.express.ServiceRequest request){
            this.request = request;
        }

        public String getMethod(){
            return request.getRequest().getMethod();
        }

        public Long getOffset(){
            return request.getOffset();
        }

        public Long getLimit(){
            return request.getLimit();
        }

        public Sort getSort(){
            return request.getSort();
        }

        public javaxt.utils.Value getParameter(String name){
            if (getMethod().equals("GET")){
                return request.getParameter(name);
            }
            else{

                if (request.hasParameter(name)){
                    return request.getParameter(name);
                }
                else{
                    try{
                        JSONObject json = request.getJson();
                        if (json.has(name)){
                            return new javaxt.utils.Value(json.get(name).toObject());
                        }
                    }
                    catch(Exception e){
                        //Invalid JSON?
                    }
                    return new javaxt.utils.Value(null);
                }

            }
        }
    }
}