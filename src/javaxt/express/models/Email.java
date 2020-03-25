package javaxt.express.models;
import javaxt.json.*;
import java.sql.SQLException;


//******************************************************************************
//**  Email Class
//******************************************************************************
/**
 *   Used to represent a Email
 *
 ******************************************************************************/

public class Email extends javaxt.sql.Model {

    private String type;
    private String address;
    private Boolean preferred;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Email(){
        super("email", new java.util.HashMap<String, String>() {{

            put("type", "type");
            put("address", "address");
            put("preferred", "preferred");

        }});

    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a record ID in the database.
   */
    public Email(long id) throws SQLException {
        this();
        init(id);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a JSON representation of a
   *  Email.
   */
    public Email(JSONObject json){
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
            this.type = getValue(rs, "type").toString();
            this.address = getValue(rs, "address").toString();
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
  /** Used to update attributes with attributes from another Email.
   */
    public void update(JSONObject json){

        Long id = json.get("id").toLong();
        if (id!=null && id>0) this.id = id;
        this.type = json.get("type").toString();
        this.address = json.get("address").toString();
        this.preferred = json.get("preferred").toBoolean();
    }


    public String getType(){
        return type;
    }

    public void setType(String type){
        this.type = type;
    }

    public String getAddress(){
        return address;
    }

    public void setAddress(String address){
        this.address = address;
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
  /** Used to find a Email using a given set of constraints. Example:
   *  Email obj = Email.get("type=", type);
   */
    public static Email get(Object...args) throws SQLException {
        Object obj = _get(Email.class, args);
        return obj==null ? null : (Email) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find Emails using a given set of constraints.
   */
    public static Email[] find(Object...args) throws SQLException {
        Object[] obj = _find(Email.class, args);
        Email[] arr = new Email[obj.length];
        for (int i=0; i<arr.length; i++){
            arr[i] = (Email) obj[i];
        }
        return arr;
    }
}