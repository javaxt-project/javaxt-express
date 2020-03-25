package javaxt.express.models;
import javaxt.json.*;
import java.sql.SQLException;
import java.util.ArrayList;

//******************************************************************************
//**  Person Class
//******************************************************************************
/**
 *   Used to represent a Person
 *
 ******************************************************************************/

public class Person extends javaxt.sql.Model {

    private String gender;
    private Integer birthday;
    private JSONObject info;
    private ArrayList<Name> names;
    private ArrayList<Phone> phoneNumbers;
    private ArrayList<Email> emails;
    private ArrayList<Address> addresses;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Person(){
        super("person", new java.util.HashMap<String, String>() {{

            put("gender", "gender");
            put("birthday", "birthday");
            put("info", "info");
            put("names", "names");
            put("phoneNumbers", "phone_numbers");
            put("emails", "emails");
            put("addresses", "addresses");

        }});
        names = new ArrayList<Name>();
        phoneNumbers = new ArrayList<Phone>();
        emails = new ArrayList<Email>();
        addresses = new ArrayList<Address>();
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a record ID in the database.
   */
    public Person(long id) throws SQLException {
        this();
        init(id);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a JSON representation of a
   *  Person.
   */
    public Person(JSONObject json){
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
            this.gender = getValue(rs, "gender").toString();
            this.birthday = getValue(rs, "birthday").toInteger();
            this.info = new JSONObject(getValue(rs, "info").toString());


            javaxt.sql.Connection conn = null;
            try{
                conn = getConnection(this.getClass());


              //Set names
                ArrayList<Long> nameIDs = new ArrayList<Long>();
                for (javaxt.sql.Recordset row : conn.getRecordset(
                    "select name_id from photos.person_name where person_id="+id)){
                    nameIDs.add(row.getValue(0).toLong());
                }
                for (long nameID : nameIDs){
                    names.add(new Name(nameID));
                }



              //Set phoneNumbers
                ArrayList<Long> phoneIDs = new ArrayList<Long>();
                for (javaxt.sql.Recordset row : conn.getRecordset(
                    "select phone_id from photos.person_phone where person_id="+id)){
                    phoneIDs.add(row.getValue(0).toLong());
                }
                for (long phoneID : phoneIDs){
                    phoneNumbers.add(new Phone(phoneID));
                }



              //Set emails
                ArrayList<Long> emailIDs = new ArrayList<Long>();
                for (javaxt.sql.Recordset row : conn.getRecordset(
                    "select email_id from photos.person_email where person_id="+id)){
                    emailIDs.add(row.getValue(0).toLong());
                }
                for (long emailID : emailIDs){
                    emails.add(new Email(emailID));
                }



              //Set addresses
                ArrayList<Long> addressIDs = new ArrayList<Long>();
                for (javaxt.sql.Recordset row : conn.getRecordset(
                    "select address_id from photos.person_address where person_id="+id)){
                    addressIDs.add(row.getValue(0).toLong());
                }
                for (long addressID : addressIDs){
                    addresses.add(new Address(addressID));
                }

                conn.close();
            }
            catch(SQLException e){
                if (conn!=null) conn.close();
                throw e;
            }


        }
        catch(Exception e){
            if (e instanceof SQLException) throw (SQLException) e;
            else throw new SQLException(e.getMessage());
        }
    }


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to update attributes with attributes from another Person.
   */
    public void update(JSONObject json){

        Long id = json.get("id").toLong();
        if (id!=null && id>0) this.id = id;
        this.gender = json.get("gender").toString();
        this.birthday = json.get("birthday").toInteger();
        this.info = json.get("info").toJSONObject();

      //Set names
        if (json.has("names")){
            JSONArray _names = json.get("names").toJSONArray();
            for (int i=0; i<_names.length(); i++){
                names.add(new Name(_names.get(i).toJSONObject()));
            }
        }


      //Set phoneNumbers
        if (json.has("phoneNumbers")){
            JSONArray _phoneNumbers = json.get("phoneNumbers").toJSONArray();
            for (int i=0; i<_phoneNumbers.length(); i++){
                phoneNumbers.add(new Phone(_phoneNumbers.get(i).toJSONObject()));
            }
        }


      //Set emails
        if (json.has("emails")){
            JSONArray _emails = json.get("emails").toJSONArray();
            for (int i=0; i<_emails.length(); i++){
                emails.add(new Email(_emails.get(i).toJSONObject()));
            }
        }


      //Set addresses
        if (json.has("addresses")){
            JSONArray _addresses = json.get("addresses").toJSONArray();
            for (int i=0; i<_addresses.length(); i++){
                addresses.add(new Address(_addresses.get(i).toJSONObject()));
            }
        }
    }


    public String getGender(){
        return gender;
    }

    public void setGender(String gender){
        this.gender = gender;
    }

    public Integer getBirthday(){
        return birthday;
    }

    public void setBirthday(Integer birthday){
        this.birthday = birthday;
    }

    public JSONObject getInfo(){
        return info;
    }

    public void setInfo(JSONObject info){
        this.info = info;
    }

    public Name[] getNames(){
        return names.toArray(new Name[names.size()]);
    }

    public void setNames(Name[] arr){
        names = new ArrayList<Name>();
        for (int i=0; i<arr.length; i++){
            names.add(arr[i]);
        }
    }

    public void addName(Name name){
        this.names.add(name);
    }

    public Phone[] getPhoneNumbers(){
        return phoneNumbers.toArray(new Phone[phoneNumbers.size()]);
    }

    public void setPhoneNumbers(Phone[] arr){
        phoneNumbers = new ArrayList<Phone>();
        for (int i=0; i<arr.length; i++){
            phoneNumbers.add(arr[i]);
        }
    }

    public void addPhone(Phone phone){
        this.phoneNumbers.add(phone);
    }

    public Email[] getEmails(){
        return emails.toArray(new Email[emails.size()]);
    }

    public void setEmails(Email[] arr){
        emails = new ArrayList<Email>();
        for (int i=0; i<arr.length; i++){
            emails.add(arr[i]);
        }
    }

    public void addEmail(Email email){
        this.emails.add(email);
    }

    public Address[] getAddresses(){
        return addresses.toArray(new Address[addresses.size()]);
    }

    public void setAddresses(Address[] arr){
        addresses = new ArrayList<Address>();
        for (int i=0; i<arr.length; i++){
            addresses.add(arr[i]);
        }
    }

    public void addAddress(Address address){
        this.addresses.add(address);
    }

  //**************************************************************************
  //** save
  //**************************************************************************
  /** Used to save a Person in the database.
   */
    public void save() throws SQLException {
        super.save();
        javaxt.sql.Connection conn = null;
        try{
            conn = getConnection(this.getClass());
            javaxt.sql.Recordset rs = new javaxt.sql.Recordset();

          //Save names
            ArrayList<Long> nameIDs = new ArrayList<Long>();
            for (Name obj : names){
                obj.save();
                nameIDs.add(obj.getID());
            }
            for (long nameID : nameIDs){
                rs.open("select * from photos.person_name where person_id=" + id +
                " and name_id=" + nameID, conn, false);
                if (rs.EOF){
                    rs.addNew();
                    rs.setValue("person_id", id);
                    rs.setValue("name_id", nameID);
                    rs.update();
                }
                rs.close();
            }


          //Save phoneNumbers
            ArrayList<Long> phoneIDs = new ArrayList<Long>();
            for (Phone obj : phoneNumbers){
                obj.save();
                phoneIDs.add(obj.getID());
            }
            for (long phoneID : phoneIDs){
                rs.open("select * from photos.person_phone where person_id=" + id +
                " and phone_id=" + phoneID, conn, false);
                if (rs.EOF){
                    rs.addNew();
                    rs.setValue("person_id", id);
                    rs.setValue("phone_id", phoneID);
                    rs.update();
                }
                rs.close();
            }


          //Save emails
            ArrayList<Long> emailIDs = new ArrayList<Long>();
            for (Email obj : emails){
                obj.save();
                emailIDs.add(obj.getID());
            }
            for (long emailID : emailIDs){
                rs.open("select * from photos.person_email where person_id=" + id +
                " and email_id=" + emailID, conn, false);
                if (rs.EOF){
                    rs.addNew();
                    rs.setValue("person_id", id);
                    rs.setValue("email_id", emailID);
                    rs.update();
                }
                rs.close();
            }


          //Save addresses
            ArrayList<Long> addressIDs = new ArrayList<Long>();
            for (Address obj : addresses){
                obj.save();
                addressIDs.add(obj.getID());
            }
            for (long addressID : addressIDs){
                rs.open("select * from photos.person_address where person_id=" + id +
                " and address_id=" + addressID, conn, false);
                if (rs.EOF){
                    rs.addNew();
                    rs.setValue("person_id", id);
                    rs.setValue("address_id", addressID);
                    rs.update();
                }
                rs.close();
            }


            conn.close();
        }
        catch(SQLException e){
            if (conn!=null) conn.close();
            throw e;
        }
    }




  //**************************************************************************
  //** get
  //**************************************************************************
  /** Used to find a Person using a given set of constraints. Example:
   *  Person obj = Person.get("gender=", gender);
   */
    public static Person get(Object...args) throws SQLException {
        Object obj = _get(Person.class, args);
        return obj==null ? null : (Person) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find Persons using a given set of constraints.
   */
    public static Person[] find(Object...args) throws SQLException {
        Object[] obj = _find(Person.class, args);
        Person[] arr = new Person[obj.length];
        for (int i=0; i<arr.length; i++){
            arr[i] = (Person) obj[i];
        }
        return arr;
    }
}