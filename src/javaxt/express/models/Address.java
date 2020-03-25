package javaxt.express.models;
import javaxt.json.*;
import java.sql.SQLException;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;

//******************************************************************************
//**  Address Class
//******************************************************************************
/**
 *   Used to represent a Address
 *
 ******************************************************************************/

public class Address extends javaxt.sql.Model {

    private String type;
    private String street;
    private String city;
    private String state;
    private String postalCode;
    private Geometry coordinates;
    private Boolean preferred;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Address(){
        super("address", new java.util.HashMap<String, String>() {{

            put("type", "type");
            put("street", "street");
            put("city", "city");
            put("state", "state");
            put("postalCode", "postal_code");
            put("coordinates", "coordinates");
            put("preferred", "preferred");

        }});

    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a record ID in the database.
   */
    public Address(long id) throws SQLException {
        this();
        init(id);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a JSON representation of a
   *  Address.
   */
    public Address(JSONObject json){
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
            this.street = getValue(rs, "street").toString();
            this.city = getValue(rs, "city").toString();
            this.state = getValue(rs, "state").toString();
            this.postalCode = getValue(rs, "postal_code").toString();
            this.coordinates = new WKTReader().read(getValue(rs, "coordinates").toString());
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
  /** Used to update attributes with attributes from another Address.
   */
    public void update(JSONObject json){

        Long id = json.get("id").toLong();
        if (id!=null && id>0) this.id = id;
        this.type = json.get("type").toString();
        this.street = json.get("street").toString();
        this.city = json.get("city").toString();
        this.state = json.get("state").toString();
        this.postalCode = json.get("postalCode").toString();
        try {
            this.coordinates = new WKTReader().read(json.get("coordinates").toString());
        }
        catch(Exception e) {}
        this.preferred = json.get("preferred").toBoolean();
    }


    public String getType(){
        return type;
    }

    public void setType(String type){
        this.type = type;
    }

    public String getStreet(){
        return street;
    }

    public void setStreet(String street){
        this.street = street;
    }

    public String getCity(){
        return city;
    }

    public void setCity(String city){
        this.city = city;
    }

    public String getState(){
        return state;
    }

    public void setState(String state){
        this.state = state;
    }

    public String getPostalCode(){
        return postalCode;
    }

    public void setPostalCode(String postalCode){
        this.postalCode = postalCode;
    }

    public Geometry getCoordinates(){
        return coordinates;
    }

    public void setCoordinates(Geometry coordinates){
        this.coordinates = coordinates;
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
  /** Used to find a Address using a given set of constraints. Example:
   *  Address obj = Address.get("type=", type);
   */
    public static Address get(Object...args) throws SQLException {
        Object obj = _get(Address.class, args);
        return obj==null ? null : (Address) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find Addresss using a given set of constraints.
   */
    public static Address[] find(Object...args) throws SQLException {
        Object[] obj = _find(Address.class, args);
        Address[] arr = new Address[obj.length];
        for (int i=0; i<arr.length; i++){
            arr[i] = (Address) obj[i];
        }
        return arr;
    }
}