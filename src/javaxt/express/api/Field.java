package javaxt.express.api;

public class Field {
    private String col;
    private String table;
    private String alias;
    private boolean isFunction;
    
    public Field(String field){
        col = field;
        isFunction = false;
    }

    public void setAlias(String alias){
        this.alias = alias;
    }

    public boolean isFunction(){
        return isFunction;
    }
    
    public void isFunction(boolean isFunction){
        this.isFunction = isFunction;
    }

    public String toString(){
        String str = col;
        if (table!=null) str = table + "." + str;
        if (alias!=null) str += " as " + alias;
        return str;
    }

    public boolean equals(Object obj){
        if (obj instanceof String){
            String str = (String) obj;
            if (str.equalsIgnoreCase(col)) return true;
            if (str.equalsIgnoreCase(alias)) return true;
            if (str.equalsIgnoreCase(this.toString())) return true;
        }
        else if (obj instanceof Field){
            Field field = (Field) obj;
            return field.equals(this.toString());
        }
        return false;
    }
}