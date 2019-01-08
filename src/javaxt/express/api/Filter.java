package javaxt.express.api;
import javaxt.json.JSONArray;
import javaxt.json.JSONObject;

public class Filter {
    private JSONArray filters;

    public Filter(JSONArray filters){
        this.filters = filters;
    }

    public Filter(JSONObject filter){
        filters = new JSONArray();
        java.util.Iterator<String> it = filter.keys();
        while (it.hasNext()){
            String key = it.next();
            String val = filter.get(key).toString();
            String op = "=";

            if (val.contains(",")){
                if (val.startsWith("!")){
                    val = "(" + val.substring(1).trim() + ")";
                    op = "NOT IN";
                }
                else{
                    val = "(" + val + ")";
                    op = "IN";
                }
            }
            else{
                String str = val.substring(0, 2);
                switch (str) {
                    case "<>":
                        op = str;
                        val = val.substring(2).trim();
                        break;
                    case "!=":
                        op = "<>";
                        val = val.substring(2).trim();
                        break;
                    case ">=":
                        op = str;
                        val = val.substring(2).trim();
                        break;
                    case "<=":
                        op = str;
                        val = val.substring(2).trim();
                        break;
                    default:


                        String s = val.substring(0, 1);
                        switch (s) {
                            case "=":
                                op = s;
                                val = val.substring(1).trim();
                                break;
                            case ">":
                                op = s;
                                val = val.substring(1).trim();
                                break;
                            case "!":
                                op = "<>";
                                val = val.substring(1).trim();
                                break;
                            case "<":
                                op = s;
                                val = val.substring(1).trim();
                                break;
                            default: 

                                break;
                        }


                        break;
                }
            }


            JSONObject f = new JSONObject();
            f.set("col", key);
            f.set("op", op);
            f.set("val", val);
            filters.add(f);
        }
    }

    public JSONArray toJson(){
        return filters;
    }
}