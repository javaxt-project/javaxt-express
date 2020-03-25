package javaxt.express.models;
import javaxt.json.*;
import java.sql.SQLException;


//******************************************************************************
//**  Phone Class
//******************************************************************************
/**
 *   Used to represent a Phone
 *
 ******************************************************************************/

public class Phone extends javaxt.sql.Model {

    private String number;
    private String type;
    private Boolean preferred;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Phone(){
        super("phone", new java.util.HashMap<String, String>() {{

            put("number", "number");
            put("type", "type");
            put("preferred", "preferred");

        }});

    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a record ID in the database.
   */
    public Phone(long id) throws SQLException {
        this();
        init(id);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a JSON representation of a
   *  Phone.
   */
    public Phone(JSONObject json){
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
            this.number = getValue(rs, "number").toString();
            this.type = getValue(rs, "type").toString();
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
  /** Used to update attributes with attributes from another Phone.
   */
    public void update(JSONObject json){

        Long id = json.get("id").toLong();
        if (id!=null && id>0) this.id = id;
        this.number = json.get("number").toString();
        this.type = json.get("type").toString();
        this.preferred = json.get("preferred").toBoolean();
    }


    public String getNumber(){
        return number;
    }

    public void setNumber(String number){
        this.number = number;
    }

    public String getType(){
        return type;
    }

    public void setType(String type){
        this.type = type;
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
  /** Used to find a Phone using a given set of constraints. Example:
   *  Phone obj = Phone.get("number=", number);
   */
    public static Phone get(Object...args) throws SQLException {
        Object obj = _get(Phone.class, args);
        return obj==null ? null : (Phone) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find Phones using a given set of constraints.
   */
    public static Phone[] find(Object...args) throws SQLException {
        Object[] obj = _find(Phone.class, args);
        Phone[] arr = new Phone[obj.length];
        for (int i=0; i<arr.length; i++){
            arr[i] = (Phone) obj[i];
        }
        return arr;
    }
}