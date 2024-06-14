package javaxt.express.utils;

import java.util.*;
import javaxt.json.JSONObject;
//import static javaxt.utils.Console.console;


//******************************************************************************
//**  Python
//******************************************************************************
/**
 *   Used to execute python commands and parse the output.
 *
 ******************************************************************************/

public class Python {

    private String python;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Python(String pythonCommand){
        try{
            test(pythonCommand);
            python = pythonCommand;
        }
        catch(Exception e){
            throw new IllegalArgumentException("Invalid");
        }
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Python(){

      //Check which python is installed on the system
        for (String pythonCommand : new String[]{"python3", "python"}){
            try{
                test(pythonCommand);
                python = pythonCommand;
                break;
            }
            catch(Exception e){
                //e.printStackTrace();
            }
        }

        if (python==null) throw new IllegalArgumentException("Invalid");
    }


  //**************************************************************************
  //** getScriptVersion
  //**************************************************************************
  /** Returns a version number for a given script. Under the hood, this method
   *  simply passes "--version" as a command-line argument to the script and
   *  returns the response.
   */
    public String getScriptVersion(javaxt.io.File script) throws Exception {
        ArrayList<String> params = new ArrayList<>();
        params.add("--version");
        List<String> output = execute(script, params);
        return output.get(0);
    }


  //**************************************************************************
  //** executeScript
  //**************************************************************************
  /** Used to run a given python script. Assumes the script is implemented as
   *  a command line application and that the application generates some sort
   *  of JSON formatted response.
   */
    public JSONObject executeScript(javaxt.io.File script, ArrayList<String> params)
    throws Exception {
        return parseOutput(execute(script, params));
    }


  //**************************************************************************
  //** execute
  //**************************************************************************
    private List<String> execute(javaxt.io.File script, ArrayList<String> params)
    throws Exception {

        String[] cmdarray = new String[params.size()+2];
        cmdarray[0] = python;
        cmdarray[1] = script.toString();
        int i = 2;
        for (String param : params){
            cmdarray[i] = param;
            i++;
        }

        javaxt.io.Shell cmd = new javaxt.io.Shell(cmdarray);
        cmd.run();
        List<String> output = cmd.getOutput();
        List<String> errors = cmd.getErrors();


      //Check if there are any errors
        parseErrors(errors);


      //Return output
        return output;
    }


  //**************************************************************************
  //** parseOutput
  //**************************************************************************
  /** Used to parse the standard output stream and return a JSONObject. Throws
   *  an exception if the output can't be parsed.
   */
    private static JSONObject parseOutput(List<String> output) throws Exception {
        try{

            StringBuilder str = new StringBuilder();
            Iterator<String> i2 = output.iterator();
            while (i2.hasNext()){
                String out = i2.next();
                if (out!=null) str.append(out);
            }

            return new JSONObject(str.toString());
        }
        catch(Exception e){
            StringBuilder err = new StringBuilder();
            err.append("Error parsing script output");
            StringBuilder result = new StringBuilder();
            Iterator<String> i2 = output.iterator();
            while (i2.hasNext()){
                String out = i2.next();
                if (out!=null) result.append(out + "\r\n");
            }
            err.append(":\r\n" + result);
            throw new Exception(err.toString());
        }
    }


  //**************************************************************************
  //** parseErrors
  //**************************************************************************
  /** Used to parse the error output stream. Throws an exception if there are
   *  any errors.
   */
    private static void parseErrors(List<String> errors) throws Exception{
        if (errors.size()>0){
            StringBuilder err = new StringBuilder();
            Iterator<String> i2 = errors.iterator();
            while (i2.hasNext()){
                String error = i2.next();
                if (error!=null) err.append(error + "\r\n");
            }
            if (err.length()>0){
                throw new Exception(err.toString());
            }
        }
    }


  //**************************************************************************
  //** test
  //**************************************************************************
  /** Used to test a given string to see if it is a valid "python" command.
   */
    private static void test(String python) throws Exception {
        String[] cmdarray = new String[]{python, "--version"};
        javaxt.io.Shell cmd = new javaxt.io.Shell(cmdarray);
        cmd.run();
        parseErrors(cmd.getErrors());

        //TODO: check output
        //console.log("Found python " + cmd.getOutput().get(0));
    }
}