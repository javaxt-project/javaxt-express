if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};
javaxt.express.Confirm = null;

  //**************************************************************************
  //** confirm
  //**************************************************************************
  /** Overrides the native javascript confirm() method by creating a
   *  javaxt.express.Confirm window.
   */
    var confirm = function(msg, config){

        if (!(typeof(msg) === 'string' || msg instanceof String)){
            config = msg;
        }


        javaxt.dhtml.utils.merge(config, {
            title: "Confirm",
            text: msg
        });


        var win = javaxt.express.Confirm;
        if (!win){
            var body = document.getElementsByTagName("body")[0];

            var buttonDiv = document.createElement("div");
            buttonDiv.className = "button-div";

            var createButton = function(label, result){
                var input = document.createElement("input");
                input.type = "button";
                input.className = "form-button";
                input.onclick = function(){
                    win.result = this.result;
                    win.close();
                };
                input.setLabel = function(label){
                    if (label) this.name = this.value = label;
                };
                input.setValue = function(b){
                    if (b===true || b===false) this.result = b;
                };
                input.update = function(config){
                    if (config){
                        this.setLabel(config.label);
                        this.setValue(config.value);
                    }
                };
                input.setLabel(label);
                input.setValue(result);
                buttonDiv.appendChild(input);
                return input;
            };


            win = javaxt.express.Confirm = new javaxt.dhtml.Window(body, {
                width: 450,
                height: 150,
                valign: "top",
                modal: true,
                footer: buttonDiv,
                style: {
                    panel: "window",
                    header: "window-header",
                    title: "window-title",
                    buttonBar: "window-header-button-bar",
                    button: "window-header-button",
                    body: "window-body confirm-body"
                }
            });


            win.leftButton = createButton("OK", true);
            win.rightButton = createButton("Cancel", false);
        }


        win.setTitle(config.title);
        win.setContent(config.text.replace("\n","<p></p>"));
        win.leftButton.update(config.leftButton);
        win.rightButton.update(config.rightButton);
        win.result = false;
        win.onClose = function(){
            var callback = config.callback;
            if (callback) callback.apply(win, [win.result]);
        };
        win.show();
        return false;
    };