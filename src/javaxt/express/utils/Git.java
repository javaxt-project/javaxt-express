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
  //** getRelativePath
  //**************************************************************************
    private String getRelativePath(javaxt.io.File file){
        String filePath = file.toString().replace("\\", "/");
        String basePath = contentDir.toString().replace("\\", "/");
        String relativePath = filePath.replace(basePath, "");
        if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
        return relativePath;
    }


  //**************************************************************************
  //** commit
  //**************************************************************************
  /** Stages the given file and creates a commit.
   */
    public void commit(javaxt.io.File file, String author, String message){
        if (git==null) return;
        try {
            String relativePath = getRelativePath(file);

          //git add
            Method addMethod = git.getClass().getMethod("add");
            Object addCommand = addMethod.invoke(git);
            Method addFilepattern = addCommand.getClass().getMethod("addFilepattern", String.class);
            addFilepattern.invoke(addCommand, relativePath);
            Method addCall = addCommand.getClass().getMethod("call");
            addCall.invoke(addCommand);

          //git commit
            doCommit(author, message);
        }
        catch(Throwable e){
            //Silent failure -- versioning is best-effort
        }
    }


  //**************************************************************************
  //** rename
  //**************************************************************************
  /** Stages the removal of the old file, stages the new file, and creates
   *  a single commit. Git will detect this as a rename.
   */
    public void rename(javaxt.io.File oldFile, javaxt.io.File newFile, String author, String message){
        if (git==null) return;
        try {
            String oldPath = getRelativePath(oldFile);
            String newPath = getRelativePath(newFile);

          //git rm old file
            Method rmMethod = git.getClass().getMethod("rm");
            Object rmCommand = rmMethod.invoke(git);
            Method rmFilepattern = rmCommand.getClass().getMethod("addFilepattern", String.class);
            rmFilepattern.invoke(rmCommand, oldPath);
            Method rmCall = rmCommand.getClass().getMethod("call");
            rmCall.invoke(rmCommand);

          //git add new file
            Method addMethod = git.getClass().getMethod("add");
            Object addCommand = addMethod.invoke(git);
            Method addFilepattern = addCommand.getClass().getMethod("addFilepattern", String.class);
            addFilepattern.invoke(addCommand, newPath);
            Method addCall = addCommand.getClass().getMethod("call");
            addCall.invoke(addCommand);

          //git commit (single commit captures both changes)
            doCommit(author, message);
        }
        catch(Throwable e){
            //Silent failure -- versioning is best-effort
        }
    }


  //**************************************************************************
  //** delete
  //**************************************************************************
  /** Stages the removal of the given file and creates a commit.
   */
    public void delete(javaxt.io.File file, String author, String message){
        if (git==null) return;
        try {
            String relativePath = getRelativePath(file);

          //git rm
            Method rmMethod = git.getClass().getMethod("rm");
            Object rmCommand = rmMethod.invoke(git);
            Method addFilepattern = rmCommand.getClass().getMethod("addFilepattern", String.class);
            addFilepattern.invoke(rmCommand, relativePath);
            Method rmCall = rmCommand.getClass().getMethod("call");
            rmCall.invoke(rmCommand);

          //git commit
            doCommit(author, message);
        }
        catch(Throwable e){
            //Silent failure -- versioning is best-effort
        }
    }


  //**************************************************************************
  //** doCommit
  //**************************************************************************
    private void doCommit(String author, String message) throws Exception {
        Method commitMethod = git.getClass().getMethod("commit");
        Object commitCommand = commitMethod.invoke(git);

        Method setMessage = commitCommand.getClass().getMethod("setMessage", String.class);
        setMessage.invoke(commitCommand, message);

        Method setAuthor = commitCommand.getClass().getMethod("setAuthor", String.class, String.class);
        setAuthor.invoke(commitCommand, author, author);

        Method commitCall = commitCommand.getClass().getMethod("call");
        commitCall.invoke(commitCommand);
    }


  //**************************************************************************
  //** getHistory
  //**************************************************************************
  /** Returns version history for the given file as a JSON array. Each entry
   *  contains hash, author, date, and message. Returns an empty array if
   *  JGit is not available.
   */
    public javaxt.json.JSONArray getHistory(javaxt.io.File file){
        return getHistory(file, false);
    }


  //**************************************************************************
  //** getHistory
  //**************************************************************************
  /** Returns version history for the given file as a JSON array. If follow
   *  is true, tracks renames so that history from before a rename is included
   *  (equivalent to git log --follow).
   */
    public javaxt.json.JSONArray getHistory(javaxt.io.File file, boolean follow){
        javaxt.json.JSONArray arr = new javaxt.json.JSONArray();
        if (git==null) return arr;
        try {
            String relativePath = getRelativePath(file);

            Iterable<?> commits;

            if (follow){
                commits = getHistoryWithFollow(relativePath);
            }
            else{
              //git log -- <path>
                Method logMethod = git.getClass().getMethod("log");
                Object logCommand = logMethod.invoke(git);
                Method addPath = logCommand.getClass().getMethod("addPath", String.class);
                addPath.invoke(logCommand, relativePath);
                Method logCall = logCommand.getClass().getMethod("call");
                commits = (Iterable<?>) logCall.invoke(logCommand);
            }

            if (commits==null) return arr;

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
  //** getHistoryWithFollow
  //**************************************************************************
  /** Uses RevWalk with FollowFilter to traverse commit history across renames.
   *  Returns null if FollowFilter is not available, allowing the caller to
   *  fall back to the simple LogCommand approach.
   */
    private Iterable<?> getHistoryWithFollow(String relativePath) throws Exception {

      //Get repository
        Method getRepository = git.getClass().getMethod("getRepository");
        Object repo = getRepository.invoke(git);

      //Get DiffConfig for rename detection
        Method getConfig = repo.getClass().getMethod("getConfig");
        Object config = getConfig.invoke(repo);

        Class<?> diffConfigClass = Class.forName("org.eclipse.jgit.diff.DiffConfig");
        Field keyField = diffConfigClass.getField("KEY");
        Object diffConfigKey = keyField.get(null);

        Class<?> sectionParserClass = Class.forName("org.eclipse.jgit.lib.Config$SectionParser");
        Method configGet = config.getClass().getMethod("get", sectionParserClass);
        Object diffConfig = configGet.invoke(config, diffConfigKey);

      //Create FollowFilter
        Class<?> followFilterClass = Class.forName("org.eclipse.jgit.revwalk.FollowFilter");
        Method createFollow = followFilterClass.getMethod("create", String.class, diffConfigClass);
        Object followFilter = createFollow.invoke(null, relativePath, diffConfig);

      //Resolve HEAD
        Class<?> repoClass = Class.forName("org.eclipse.jgit.lib.Repository");
        Method resolve = repo.getClass().getMethod("resolve", String.class);
        Object headId = resolve.invoke(repo, "HEAD");
        if (headId==null) return null;

      //Create RevWalk
        Class<?> revWalkClass = Class.forName("org.eclipse.jgit.revwalk.RevWalk");
        Object revWalk = revWalkClass.getConstructor(repoClass).newInstance(repo);

      //Set FollowFilter as the tree filter
        Class<?> treeFilterClass = Class.forName("org.eclipse.jgit.treewalk.filter.TreeFilter");
        Method setTreeFilter = revWalkClass.getMethod("setTreeFilter", treeFilterClass);
        setTreeFilter.invoke(revWalk, followFilter);

      //Mark HEAD as the start commit
        Class<?> anyObjectIdClass = Class.forName("org.eclipse.jgit.lib.AnyObjectId");
        Method parseCommit = revWalkClass.getMethod("parseCommit", anyObjectIdClass);
        Object headCommit = parseCommit.invoke(revWalk, headId);
        Method markStart = revWalkClass.getMethod("markStart", Class.forName("org.eclipse.jgit.revwalk.RevCommit"));
        markStart.invoke(revWalk, headCommit);

      //Collect commits into a list (RevWalk is Iterable but we need to
      //close it after iteration, so collect upfront)
        java.util.List<Object> result = new java.util.ArrayList<>();
        Method next = revWalkClass.getMethod("next");
        Object commit;
        while ((commit = next.invoke(revWalk)) != null){
            result.add(commit);
        }

      //Clean up
        Method rwClose = revWalkClass.getMethod("close");
        rwClose.invoke(revWalk);

        return result;
    }


  //**************************************************************************
  //** getVersion
  //**************************************************************************
  /** Returns the content of a file at a specific commit as a String. Returns
   *  null if JGit is not available or the file/commit cannot be resolved.
   */
    public String getVersion(javaxt.io.File file, String commitHash){
        if (git==null) return null;
        try {
            String relativePath = getRelativePath(file);

          //Get repository
            Method getRepository = git.getClass().getMethod("getRepository");
            Object repo = getRepository.invoke(git);

          //Resolve commit hash to ObjectId
            Method resolve = repo.getClass().getMethod("resolve", String.class);
            Object commitId = resolve.invoke(repo, commitHash);
            if (commitId==null) return null;

          //Create RevWalk and parse the commit
            Class<?> revWalkClass = Class.forName("org.eclipse.jgit.revwalk.RevWalk");
            Class<?> repoClass = Class.forName("org.eclipse.jgit.lib.Repository");
            Object revWalk = revWalkClass.getConstructor(repoClass).newInstance(repo);

            Class<?> objectIdClass = Class.forName("org.eclipse.jgit.lib.AnyObjectId");
            Method parseCommit = revWalkClass.getMethod("parseCommit", objectIdClass);
            Object commit = parseCommit.invoke(revWalk, commitId);

          //Get the commit's tree
            Method getTree = commit.getClass().getMethod("getTree");
            Object tree = getTree.invoke(commit);

          //Create TreeWalk to find the file in the tree
            Class<?> treeWalkClass = Class.forName("org.eclipse.jgit.treewalk.TreeWalk");
            Object treeWalk = treeWalkClass.getConstructor(repoClass).newInstance(repo);

            Class<?> objectIdClass2 = Class.forName("org.eclipse.jgit.lib.AnyObjectId");
            Method addTree = treeWalkClass.getMethod("addTree", objectIdClass2);
            Method getTreeId = tree.getClass().getMethod("getId");
            Object treeId = getTreeId.invoke(tree);
            addTree.invoke(treeWalk, treeId);

            Method setRecursive = treeWalkClass.getMethod("setRecursive", boolean.class);
            setRecursive.invoke(treeWalk, true);

          //Set path filter
            Class<?> pathFilterClass = Class.forName("org.eclipse.jgit.treewalk.filter.PathFilter");
            Method createFilter = pathFilterClass.getMethod("create", String.class);
            Object pathFilter = createFilter.invoke(null, relativePath);

            Class<?> treeFilterClass = Class.forName("org.eclipse.jgit.treewalk.filter.TreeFilter");
            Method setFilter = treeWalkClass.getMethod("setFilter", treeFilterClass);
            setFilter.invoke(treeWalk, pathFilter);

          //Walk to find the file
            Method next = treeWalkClass.getMethod("next");
            boolean found = (Boolean) next.invoke(treeWalk);
            if (!found){
                Method twClose = treeWalkClass.getMethod("close");
                twClose.invoke(treeWalk);
                Method rwClose = revWalkClass.getMethod("close");
                rwClose.invoke(revWalk);
                return null;
            }

          //Get the object and read its content
            Method getObjectId = treeWalkClass.getMethod("getObjectId", int.class);
            Object blobId = getObjectId.invoke(treeWalk, 0);

            Method openObject = repo.getClass().getMethod("open", objectIdClass2);
            Object objectLoader = openObject.invoke(repo, blobId);

            Method getBytes = objectLoader.getClass().getMethod("getBytes");
            byte[] bytes = (byte[]) getBytes.invoke(objectLoader);

          //Clean up
            Method twClose = treeWalkClass.getMethod("close");
            twClose.invoke(treeWalk);
            Method rwClose = revWalkClass.getMethod("close");
            rwClose.invoke(revWalk);

            return new String(bytes, "UTF-8");
        }
        catch(Throwable e){
            return null;
        }
    }


  //**************************************************************************
  //** getDiff
  //**************************************************************************
  /** Returns a unified diff for the given file at a specific commit, compared
   *  to its parent. Returns an empty string for the initial commit or if JGit
   *  is not available.
   */
    public String getDiff(javaxt.io.File file, String commitHash){
        if (git==null) return "";
        try {
            String relativePath = getRelativePath(file);

          //Get repository
            Method getRepository = git.getClass().getMethod("getRepository");
            Object repo = getRepository.invoke(git);

          //Resolve commit hash to ObjectId
            Method resolve = repo.getClass().getMethod("resolve", String.class);
            Object commitId = resolve.invoke(repo, commitHash);
            if (commitId==null) return "";

          //Create RevWalk and parse the commit
            Class<?> revWalkClass = Class.forName("org.eclipse.jgit.revwalk.RevWalk");
            Class<?> repoClass = Class.forName("org.eclipse.jgit.lib.Repository");
            Object revWalk = revWalkClass.getConstructor(repoClass).newInstance(repo);

            Class<?> anyObjectIdClass = Class.forName("org.eclipse.jgit.lib.AnyObjectId");
            Method parseCommit = revWalkClass.getMethod("parseCommit", anyObjectIdClass);
            Object commit = parseCommit.invoke(revWalk, commitId);

          //Get the commit's tree
            Method getTree = commit.getClass().getMethod("getTree");
            Object newTree = getTree.invoke(commit);

          //Get the parent's tree (or null for initial commit)
            Object oldTree = null;
            Method getParentCount = commit.getClass().getMethod("getParentCount");
            int parentCount = (Integer) getParentCount.invoke(commit);
            if (parentCount > 0){
                Method getParent = commit.getClass().getMethod("getParent", int.class);
                Object parentRef = getParent.invoke(commit, 0);
                Object parent = parseCommit.invoke(revWalk, parentRef);
                oldTree = getTree.invoke(parent);
            }

          //Create DiffFormatter
            Class<?> dfClass = Class.forName("org.eclipse.jgit.diff.DiffFormatter");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            Object diffFormatter = dfClass.getConstructor(java.io.OutputStream.class).newInstance(out);

          //Set repository
            Method setRepo = dfClass.getMethod("setRepository", repoClass);
            setRepo.invoke(diffFormatter, repo);

          //Enable rename detection
            Method setDetectRenames = dfClass.getMethod("setDetectRenames", boolean.class);
            setDetectRenames.invoke(diffFormatter, true);

          //Set path filter
            Class<?> pathFilterClass = Class.forName("org.eclipse.jgit.treewalk.filter.PathFilter");
            Method createFilter = pathFilterClass.getMethod("create", String.class);
            Object pathFilter = createFilter.invoke(null, relativePath);
            Class<?> treeFilterClass = Class.forName("org.eclipse.jgit.treewalk.filter.TreeFilter");
            Method setPathFilter = dfClass.getMethod("setPathFilter", treeFilterClass);
            setPathFilter.invoke(diffFormatter, pathFilter);

          //Scan for diff entries between parent and commit trees
            Class<?> abstractTreeIterClass = Class.forName("org.eclipse.jgit.treewalk.AbstractTreeIterator");
            Method scan = dfClass.getMethod("scan", abstractTreeIterClass, abstractTreeIterClass);

            Object oldTreeIter;
            if (oldTree!=null){
                oldTreeIter = createTreeIterator(repo, oldTree, revWalkClass, repoClass);
            }
            else{
                Class<?> emptyTreeClass = Class.forName("org.eclipse.jgit.treewalk.EmptyTreeIterator");
                oldTreeIter = emptyTreeClass.getConstructor().newInstance();
            }
            Object newTreeIter = createTreeIterator(repo, newTree, revWalkClass, repoClass);

            java.util.List<?> diffs = (java.util.List<?>) scan.invoke(diffFormatter, oldTreeIter, newTreeIter);

          //Format each diff entry
            if (diffs!=null && !diffs.isEmpty()){
                Class<?> diffEntryClass = Class.forName("org.eclipse.jgit.diff.DiffEntry");
                Method format = dfClass.getMethod("format", diffEntryClass);
                for (Object entry : diffs){
                    format.invoke(diffFormatter, entry);
                }
            }

          //Flush and get output
            Method flush = dfClass.getMethod("flush");
            flush.invoke(diffFormatter);

          //Clean up
            Method dfClose = dfClass.getMethod("close");
            dfClose.invoke(diffFormatter);
            Method rwClose = revWalkClass.getMethod("close");
            rwClose.invoke(revWalk);

            return out.toString("UTF-8");
        }
        catch(Throwable e){
            return "";
        }
    }


  //**************************************************************************
  //** createTreeIterator
  //**************************************************************************
  /** Creates a CanonicalTreeParser for the given tree object.
   */
    private Object createTreeIterator(Object repo, Object tree,
        Class<?> revWalkClass, Class<?> repoClass) throws Exception {

        Class<?> parserClass = Class.forName("org.eclipse.jgit.treewalk.CanonicalTreeParser");
        Object parser = parserClass.getConstructor().newInstance();

        Class<?> objectReaderClass = Class.forName("org.eclipse.jgit.lib.ObjectReader");
        Method newReader = repoClass.getMethod("newObjectReader");
        Object reader = newReader.invoke(repo);

        Class<?> anyObjectIdClass = Class.forName("org.eclipse.jgit.lib.AnyObjectId");
        Method getId = tree.getClass().getMethod("getId");
        Object treeId = getId.invoke(tree);

        Method reset = parserClass.getMethod("reset", objectReaderClass, anyObjectIdClass);
        reset.invoke(parser, reader, treeId);

        return parser;
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