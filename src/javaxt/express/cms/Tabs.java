package javaxt.express.cms;

//******************************************************************************
//**  TabFile
//******************************************************************************
/**
 *   Used to parse a plain text file with tab specs.
 *
 ******************************************************************************/

public class Tabs {

    private javaxt.io.File file;
    private long lastModified;
    private java.util.LinkedHashMap<String, String> items;
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Tabs(javaxt.io.File file) {
        this.file = file;
        this.lastModified = 0;
        this.items = getItems();
    }

    
  //**************************************************************************
  //** getLastModified
  //**************************************************************************
    public long getLastModified(){
        return lastModified;
    }


  //**************************************************************************
  //** getItems
  //**************************************************************************
    public java.util.LinkedHashMap<String, String> getItems(){
        
      //Parse tab file as needed
        if (file.exists()){
            long lastModified = file.getDate().getTime();
            if (lastModified>this.lastModified){
                items = new java.util.LinkedHashMap<String, String>();

                for (String row : file.getText().split("\n")){
                    row = row.trim();
                    if (row.length()>0 && row.contains("\t") &&
                       !(row.startsWith("<!--") || row.startsWith("#") || row.startsWith("//")))
                    {
                        int idx = row.indexOf("\t");

                        String text = row.substring(0, idx).trim();
                        String link = row.substring(idx).trim();

                        items.put(text, link);
                    }
                }


                this.lastModified = lastModified;
            }
        }
        else{
            lastModified = 0;
            items = new java.util.LinkedHashMap<String, String>();
        }
        
      //Return tab entries
        return items;
    }

}