package javaxt.express.utils;

//******************************************************************************
//**  Markdown Parser
//******************************************************************************
/**
 *   Simple, standalone Markdown-to-HTML converter. Supports headers, lists,
 *   fenced code blocks, inline links, and paragraphs.
 *
 ******************************************************************************/

public class MDParser {

  //**************************************************************************
  //** toHTML
  //**************************************************************************
  /** Converts a Markdown string to an HTML snippet.
   */
    public static String toHTML(String markdown){
        if (markdown==null) return "";

        String[] lines = markdown.split("\n");
        StringBuilder html = new StringBuilder();

        boolean inCodeBlock = false;
        boolean inUl = false;
        boolean inOl = false;
        boolean inTable = false;
        boolean tableHeaderDone = false;
        boolean inParagraph = false;
        StringBuilder paragraph = new StringBuilder();

        for (int i=0; i<lines.length; i++){
            String line = lines[i];

          //Strip trailing carriage return
            if (line.endsWith("\r")) line = line.substring(0, line.length()-1);


          //Handle fenced code blocks
            if (line.trim().startsWith("```")){
                if (!inCodeBlock){

                  //Close any open block
                    closeParagraph(html, paragraph, inParagraph);
                    inParagraph = false;
                    if (inUl){ html.append("</ul>\n"); inUl = false; }
                    if (inOl){ html.append("</ol>\n"); inOl = false; }
                    if (inTable){ html.append("</table>\n"); inTable = false; tableHeaderDone = false; }

                  //Get optional language
                    String lang = line.trim().substring(3).trim();
                    if (lang.isEmpty()){
                        html.append("<pre>");
                    }
                    else{
                        html.append("<pre class=\"brush: ").append(lang).append(";\">");
                    }
                    inCodeBlock = true;
                }
                else{
                    html.append("</pre>\n");
                    inCodeBlock = false;
                }
                continue;
            }

            if (inCodeBlock){
                html.append(escapeHtml(line)).append("\n");
                continue;
            }


          //Handle headers
            if (line.startsWith("#")){

              //Close any open block
                closeParagraph(html, paragraph, inParagraph);
                inParagraph = false;
                if (inUl){ html.append("</ul>\n"); inUl = false; }
                if (inOl){ html.append("</ol>\n"); inOl = false; }
                if (inTable){ html.append("</table>\n"); inTable = false; tableHeaderDone = false; }

                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                if (level > 6) level = 6;
                String text = line.substring(level).trim();
                text = processInline(text);
                html.append("<h").append(level).append(">")
                    .append(text)
                    .append("</h").append(level).append(">\n");
                continue;
            }


          //Handle unordered list items
            if (line.matches("^\\s*[\\-\\*]\\s+.*")){

              //Close paragraph or ordered list if open
                closeParagraph(html, paragraph, inParagraph);
                inParagraph = false;
                if (inOl){ html.append("</ol>\n"); inOl = false; }

                if (!inUl){
                    html.append("<ul>\n");
                    inUl = true;
                }
                String text = line.replaceFirst("^\\s*[\\-\\*]\\s+", "");
                html.append("<li>").append(processInline(text)).append("</li>\n");
                continue;
            }


          //Handle ordered list items
            if (line.matches("^\\s*\\d+\\.\\s+.*")){

              //Close paragraph or unordered list if open
                closeParagraph(html, paragraph, inParagraph);
                inParagraph = false;
                if (inUl){ html.append("</ul>\n"); inUl = false; }

                if (!inOl){
                    html.append("<ol>\n");
                    inOl = true;
                }
                String text = line.replaceFirst("^\\s*\\d+\\.\\s+", "");
                html.append("<li>").append(processInline(text)).append("</li>\n");
                continue;
            }


          //Handle list continuation lines (indented text following a list item)
            if ((inUl || inOl) && line.length()>0 && (line.charAt(0)==' ' || line.charAt(0)=='\t') && !line.trim().isEmpty()){
                // Remove trailing </li>\n from previous item and append continuation
                String ending = "</li>\n";
                int idx = html.length() - ending.length();
                if (idx >= 0 && html.substring(idx).equals(ending)){
                    html.setLength(idx);
                    html.append(" ").append(processInline(line.trim())).append("</li>\n");
                }
                continue;
            }


          //Handle table rows (lines starting and ending with |)
            if (line.trim().startsWith("|") && line.trim().endsWith("|")){

              //Close other open blocks
                closeParagraph(html, paragraph, inParagraph);
                inParagraph = false;
                if (inUl){ html.append("</ul>\n"); inUl = false; }
                if (inOl){ html.append("</ol>\n"); inOl = false; }

              //Skip separator rows (e.g. |---|---|)
                if (line.trim().matches("^\\|[\\s\\-:|]+\\|$")){
                    tableHeaderDone = true;
                    continue;
                }

                if (!inTable){
                    html.append("<table>\n");
                    inTable = true;
                    tableHeaderDone = false;
                }

              //Parse cells
                String trimmed = line.trim();
                trimmed = trimmed.substring(1, trimmed.length()-1); // strip outer pipes
                String[] cells = trimmed.split("\\|");
                String cellTag = !tableHeaderDone ? "th" : "td";
                html.append("<tr>");
                for (String cell : cells){
                    html.append("<").append(cellTag).append(">")
                        .append(processInline(cell.trim()))
                        .append("</").append(cellTag).append(">");
                }
                html.append("</tr>\n");
                continue;
            }

          //Close table if we're in one and hit a non-table line
            if (inTable){
                html.append("</table>\n");
                inTable = false;
                tableHeaderDone = false;
            }


          //Handle blank lines
            if (line.trim().isEmpty()){
                closeParagraph(html, paragraph, inParagraph);
                inParagraph = false;
                if (inUl){ html.append("</ul>\n"); inUl = false; }
                if (inOl){ html.append("</ol>\n"); inOl = false; }
                continue;
            }


          //Paragraph text
            if (inUl){ html.append("</ul>\n"); inUl = false; }
            if (inOl){ html.append("</ol>\n"); inOl = false; }

            if (!inParagraph){
                inParagraph = true;
                paragraph.setLength(0);
            }
            else{
                paragraph.append(" ");
            }
            paragraph.append(line.trim());
        }


      //Close any remaining open blocks
        closeParagraph(html, paragraph, inParagraph);
        if (inUl) html.append("</ul>\n");
        if (inOl) html.append("</ol>\n");
        if (inTable) html.append("</table>\n");
        if (inCodeBlock) html.append("</pre>\n");

        return html.toString().trim();
    }


  //**************************************************************************
  //** closeParagraph
  //**************************************************************************
    private static void closeParagraph(StringBuilder html, StringBuilder paragraph, boolean inParagraph){
        if (inParagraph && paragraph.length()>0){
            html.append("<p>").append(processInline(paragraph.toString())).append("</p>\n");
            paragraph.setLength(0);
        }
    }


  //**************************************************************************
  //** processInline
  //**************************************************************************
  /** Processes inline markdown elements (code, bold, italic, links,
   *  em-dashes) within a line of text.
   */
    private static String processInline(String text){

      //Convert inline code: `text` -> <code>text</code>
      //Must come first so content inside backticks is not processed further
        StringBuilder code = new StringBuilder();
        int cpos = 0;
        while (cpos < text.length()){
            int start = text.indexOf('`', cpos);
            if (start == -1){
                code.append(text.substring(cpos));
                break;
            }
            int end = text.indexOf('`', start+1);
            if (end == -1){
                code.append(text.substring(cpos));
                break;
            }
            code.append(text, cpos, start);
            String inner = text.substring(start+1, end);
            code.append("<code>").append(escapeHtml(inner)).append("</code>");
            cpos = end + 1;
        }
        text = code.toString();

      //Convert bold: **text** -> <b>text</b>
        StringBuilder bold = new StringBuilder();
        int bpos = 0;
        while (bpos < text.length()){
            int start = text.indexOf("**", bpos);
            if (start == -1){
                bold.append(text.substring(bpos));
                break;
            }
            int end = text.indexOf("**", start+2);
            if (end == -1){
                bold.append(text.substring(bpos));
                break;
            }
            bold.append(text, bpos, start);
            bold.append("<b>").append(text, start+2, end).append("</b>");
            bpos = end + 2;
        }
        text = bold.toString();

      //Convert italic: *text* -> <i>text</i>
      //Must come after bold so ** is consumed first
        StringBuilder italic = new StringBuilder();
        int ipos = 0;
        while (ipos < text.length()){
            int start = text.indexOf('*', ipos);
            if (start == -1){
                italic.append(text.substring(ipos));
                break;
            }
            int end = text.indexOf('*', start+1);
            if (end == -1){
                italic.append(text.substring(ipos));
                break;
            }
            italic.append(text, ipos, start);
            italic.append("<i>").append(text, start+1, end).append("</i>");
            ipos = end + 1;
        }
        text = italic.toString();

      //Convert inline links: [text](url) -> <a href="url">text</a>
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (pos < text.length()){
            int linkStart = text.indexOf('[', pos);
            if (linkStart == -1){
                sb.append(text.substring(pos));
                break;
            }
            int linkTextEnd = text.indexOf(']', linkStart);
            if (linkTextEnd == -1){
                sb.append(text.substring(pos));
                break;
            }
            if (linkTextEnd+1 < text.length() && text.charAt(linkTextEnd+1) == '('){
                int urlEnd = text.indexOf(')', linkTextEnd+2);
                if (urlEnd == -1){
                    sb.append(text.substring(pos));
                    break;
                }
                sb.append(text, pos, linkStart);
                String linkText = text.substring(linkStart+1, linkTextEnd);
                String url = text.substring(linkTextEnd+2, urlEnd);
                sb.append("<a href=\"").append(url).append("\">").append(linkText).append("</a>");
                pos = urlEnd + 1;
            }
            else{
                sb.append(text, pos, linkTextEnd+1);
                pos = linkTextEnd + 1;
            }
        }
        text = sb.toString();

      //Convert em-dashes: space--space -> &mdash;
      //Also handles -- at start/end of text adjacent to a space
        text = text.replace(" -- ", " &mdash; ");

        return text;
    }


  //**************************************************************************
  //** escapeHtml
  //**************************************************************************
  /** Escapes HTML special characters in code blocks.
   */
    private static String escapeHtml(String text){
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}
