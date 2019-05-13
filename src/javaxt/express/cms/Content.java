package javaxt.express.cms;

//******************************************************************************
//**  HTML Content
//******************************************************************************
/**
 *   Represents a block of HTML that will be inserted into a template via the
 *   <%=content%> tag.
 *
 ******************************************************************************/

public class Content {

    private java.util.Date date;
    private String html;

    public Content(String html, java.util.Date date){
        this.html = html;
        this.date = date;
    }

    public java.util.Date getDate(){
        return date;
    }

    public String getHTML(){
        return html;
    }
}