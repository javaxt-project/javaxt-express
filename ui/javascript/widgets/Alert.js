
  //**************************************************************************
  //** alert
  //**************************************************************************
  /** Overrides the native javascript alert() method by creating a custom
   *  Alert window.
   */
    var alert = function(msg){
        javaxt.dhtml.utils.alert(msg);
    };