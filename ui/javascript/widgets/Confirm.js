
  //**************************************************************************
  //** confirm
  //**************************************************************************
  /** Overrides the native javascript confirm() method by creating a
   *  javaxt.express.Confirm window.
   */
    var confirm = function(msg, config){
        javaxt.dhtml.utils.confirm(msg, config);
        return false;
    };