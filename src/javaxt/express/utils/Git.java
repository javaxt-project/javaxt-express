package javaxt.express.utils;
import java.lang.reflect.*;

//******************************************************************************
//**  Git
//******************************************************************************
/**
 *   Provides Git-based version control utils via JGit
 *
 ******************************************************************************/

public class Git {

    private Object git;
    private javaxt.io.Directory contentDir;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class. Attempts to open or initialize a
   *  Git repository in the given directory.
   */
    public Git(javaxt.io.Directory contentDir){
        this.contentDir = contentDir;
        try {
            Class<?> gitClass = Class.forName("org.eclipse.jgit.api.Git");
            java.io.File dir = new java.io.File(contentDir.toString());
            java.io.File gitDir = new java.io.File(dir, ".git");

            if (gitDir.exists()){
                Method open = gitClass.getMethod("open", java.io.File.class);
                git = open.invoke(null, dir);
            }
            else{
                Method init = gitClass.getMethod("init");
                Object initCommand = init.invoke(null);
                Method setDir = initCommand.getClass().getMethod("setDirectory", java.io.File.class);
                setDir.invoke(initCommand, dir);
                Method call = initCommand.getClass().getMethod("call");
                git = call.invoke(initCommand);
            }

        }
        catch(Throwable e){
            git = null;
        }
    }


  //**************************************************************************
  //** isAvailable
  //**************************************************************************
    public boolean isAvailable(){
        return git!=null;
    }


  //**************************************************************************
  //** commit
  //**************************************************************************
  /** Stages the given file and creates a commit.
   */
    public void commit(javaxt.io.File file, String author, String message){
        if (git==null) return;
        try {

          //Compute relative path within the repo
            String filePath = file.toString().replace("\\", "/");
            String basePath = contentDir.toString().replace("\\", "/");
            String relativePath = filePath.replace(basePath, "");
            if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);


          //git add
            Method addMethod = git.getClass().getMethod("add");
            Object addCommand = addMethod.invoke(git);
            Method addFilepattern = addCommand.getClass().getMethod("addFilepattern", String.class);
            addFilepattern.invoke(addCommand, relativePath);
            Method addCall = addCommand.getClass().getMethod("call");
            addCall.invoke(addCommand);


          //git commit
            Method commitMethod = git.getClass().getMethod("commit");
            Object commitCommand = commitMethod.invoke(git);

            Method setMessage = commitCommand.getClass().getMethod("setMessage", String.class);
            setMessage.invoke(commitCommand, message);

            Method setAuthor = commitCommand.getClass().getMethod("setAuthor", String.class, String.class);
            setAuthor.invoke(commitCommand, author, author);

            Method commitCall = commitCommand.getClass().getMethod("call");
            commitCall.invoke(commitCommand);

        }
        catch(Throwable e){
            //Silent failure -- versioning is best-effort
        }
    }


  //**************************************************************************
  //** getHistory
  //**************************************************************************
  /** Returns version history for the given file as a JSON array. Each entry
   *  contains hash, author, date, and message. Returns an empty array if
   *  JGit is not available.
   */
    public javaxt.json.JSONArray getHistory(javaxt.io.File file){
        javaxt.json.JSONArray arr = new javaxt.json.JSONArray();
        if (git==null) return arr;
        try {

          //Compute relative path
            String filePath = file.toString().replace("\\", "/");
            String basePath = contentDir.toString().replace("\\", "/");
            String relativePath = filePath.replace(basePath, "");
            if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);


          //git log -- <path>
            Method logMethod = git.getClass().getMethod("log");
            Object logCommand = logMethod.invoke(git);

            Method addPath = logCommand.getClass().getMethod("addPath", String.class);
            addPath.invoke(logCommand, relativePath);

            Method logCall = logCommand.getClass().getMethod("call");
            Iterable<?> commits = (Iterable<?>) logCall.invoke(logCommand);


          //Extract commit info via reflection
            for (Object commit : commits){
                Class<?> commitClass = commit.getClass();

                Method getName = commitClass.getMethod("getName");
                String hash = (String) getName.invoke(commit);

                Method getShortMessage = commitClass.getMethod("getShortMessage");
                String msg = (String) getShortMessage.invoke(commit);

                Method getAuthorIdent = commitClass.getMethod("getAuthorIdent");
                Object ident = getAuthorIdent.invoke(commit);

                Method identGetName = ident.getClass().getMethod("getName");
                String authorName = (String) identGetName.invoke(ident);

                Method getWhen = ident.getClass().getMethod("getWhen");
                java.util.Date when = (java.util.Date) getWhen.invoke(ident);

                javaxt.json.JSONObject entry = new javaxt.json.JSONObject();
                entry.set("hash", hash);
                entry.set("author", authorName);
                entry.set("date", when.getTime());
                entry.set("message", msg);
                arr.add(entry);
            }
        }
        catch(Throwable e){
        }
        return arr;
    }


  //**************************************************************************
  //** close
  //**************************************************************************
    public void close(){
        if (git!=null){
            try {
                Method closeMethod = git.getClass().getMethod("close");
                closeMethod.invoke(git);
            }
            catch(Throwable e){}
        }
    }
}