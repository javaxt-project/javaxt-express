package javaxt.express.services;
import javaxt.express.*;
import javaxt.express.ServiceRequest.Sort;

import javaxt.json.*;
import javaxt.io.File;
import javaxt.io.Directory;
import javaxt.utils.Console;

import java.util.*;

//******************************************************************************
//**  FileService
//******************************************************************************
/**
 *   Provides a set of web methods used to manage and view files on the server
 *
 ******************************************************************************/

public class FileService {

    private static Console console = new Console();
    private static int numDigits = (Long.MAX_VALUE+"").length();
    private static String zeros = getZeros();
    private static String getZeros(){
        String str = "";
        for (int i=0; i<numDigits; i++) str+="0";
        return str;
    }


  //**************************************************************************
  //** getServiceResponse
  //**************************************************************************
    public ServiceResponse getServiceResponse(javaxt.express.ServiceRequest req) {
        ServiceRequest request = new ServiceRequest(req);
        String path = request.getParameter("path").toString();

        try{

            String method = request.getMethod();
            if (method.equals("GET") || method.equals("POST")){
                return list(path, req.getSort(), request);
            }
            else{
                return new ServiceResponse(501, "Not implemented");
            }

        }
        catch(Exception e){
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** list
  //**************************************************************************
    private ServiceResponse list(String path, Sort sort, ServiceRequest request) throws Exception {
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
        if (sort!=null && !sort.isEmpty()){
            sortBy = sort.getKeySet().iterator().next();
            direction = sort.get(sortBy);
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


            String key;
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
            else{
                key = name.toLowerCase();
            }

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


        JSONObject json = new JSONObject();
        json.set("dir", path);
        json.set("items", arr);
        json.set("count", files.size()+folders.size());
        json.set("size", totalSize);
        json.set("pathSeparator", Directory.PathSeparator);
        return new ServiceResponse(json);
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