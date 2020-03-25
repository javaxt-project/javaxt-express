package javaxt.express.models;
import javaxt.json.*;
import java.sql.SQLException;


//******************************************************************************
//**  Name Class
//******************************************************************************
/**
 *   Used to represent a Name
 *
 ******************************************************************************/

public class Name extends javaxt.sql.Model {

    private String name;
    private Boolean preferred;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Name(){
        super("name", new java.util.HashMap<String, String>() {{

            put("name", "name");
            put("preferred", "preferred");

        }});

    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a record ID in the database.
   */
    public Name(long id) throws SQLException {
        this();
        init(id);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a JSON representation of a
   *  Name.
   */
    public Name(JSONObject json){
        this();
        update(json);
    }


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to update attributes using a record in the database.
   */
    protected void update(Object rs) throws SQLException {

        try{
            this.id = getValue(rs, "id").toLong();
            this.name = getValue(rs, "name").toString();
            this.preferred = getValue(rs, "preferred").toBoolean();


        }
        catch(Exception e){
            if (e instanceof SQLException) throw (SQLException) e;
            else throw new SQLException(e.getMessage());
        }
    }


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to update attributes with attributes from another Name.
   */
    public void update(JSONObject json){

        Long id = json.get("id").toLong();
        if (id!=null && id>0) this.id = id;
        this.name = json.get("name").toString();
        this.preferred = json.get("preferred").toBoolean();
    }


    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public Boolean getPreferred(){
        return preferred;
    }

    public void setPreferred(Boolean preferred){
        this.preferred = preferred;
    }




  //**************************************************************************
  //** get
  //**************************************************************************
  /** Used to find a Name using a given set of constraints. Example:
   *  Name obj = Name.get("name=", name);
   */
    public static Name get(Object...args) throws SQLException {
        Object obj = _get(Name.class, args);
        return obj==null ? null : (Name) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find Names using a given set of constraints.
   */
    public static Name[] find(Object...args) throws SQLException {
        Object[] obj = _find(Name.class, args);
        Name[] arr = new Name[obj.length];
        for (int i=0; i<arr.length; i++){
            arr[i] = (Name) obj[i];
        }
        return arr;
    }
}