if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};
javaxt.express.Alert = null;

  //**************************************************************************
  //** alert
  //**************************************************************************
  /** Overrides the native javascript alert() method by creating a
   *  javaxt.express.Alert window.
   */
    var alert = function(msg){

        if (msg==null) msg = "";


      //Special case for ajax request
        if (!(typeof(msg) === 'string' || msg instanceof String)){
            if (typeof msg.responseText !== 'undefined'){
                msg = (msg.responseText.length>0 ? msg.responseText : msg.statusText);
                if (!msg) msg = "Unknown Server Error";
            }
        }

        var win = javaxt.express.Alert;

        if (!win){

            var body = document.getElementsByTagName("body")[0];


            var outerDiv = document.createElement('div');
            outerDiv.style.width = "100%";
            outerDiv.style.height = "100%";
            outerDiv.style.position = "relative";
            outerDiv.style.cursor = "inherit";
            var innerDiv = document.createElement('div');
            innerDiv.style.width = "100%";
            innerDiv.style.height = "100%";
            innerDiv.style.position = "absolute";
            innerDiv.style.overflowX = 'hidden';
            innerDiv.style.cursor = "inherit";
            outerDiv.appendChild(innerDiv);


            win = javaxt.express.Alert = new javaxt.dhtml.Window(body, {
                width: 450,
                height: 200,
                valign: "top",
                modal: true,
                title: "Alert",
                body: outerDiv,
                style: {
                    panel: "window",
                    header: "window-header alert-header",
                    title: "window-title",
                    buttonBar: "window-header-button-bar",
                    button: "window-header-button",
                    body: "window-body alert-body"
                }
            });
            win.div = innerDiv;
        }


        win.div.innerHTML = msg;
        win.show();

    };