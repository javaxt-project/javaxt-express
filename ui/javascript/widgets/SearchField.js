if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  Search Field
//******************************************************************************
/**
 *   Form field used to initiate a search via web sockets
 *
 ******************************************************************************/

javaxt.express.SearchField = function(parent, config) {
    this.className = "javaxt.express.SearchField";

    var me = this;


  //Create default style
    var defaultStyle = {};
    if (javaxt.dhtml.style){
        if (javaxt.dhtml.style.default){
            var style = javaxt.dhtml.style.default.combobox;
            if (style) defaultStyle = style;
        }
    }


    var defaultConfig = {

      /** Relative path the to websocket endpoint (e.g. "/search"). You do not
       *  need to specify a full url with a "ws://" or "wss://" prefix.
       */
        url: '/search',




      /** Delay between the last user input and start of a new search.
       */
        searchDelay: 500,


      /** Style for individual elements within the component. Note that you can
       *  provide CSS class names instead of individual style definitions.
       */
        style: defaultStyle,


      /** Function to call when the user clicks on the search button.
       */
        onButtonClick: function(){

        },

        debugr: null
    };

    var input;
    var socket;
    var socketStatus = 0; //0 (Closed), 1 (Opening), 2 (Open), 3 (Ready)


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    var init = function(){

        if (typeof parent === "string"){
            parent = document.getElementById(parent);
        }
        if (!parent) return;


      //Clone the config so we don't modify the original config object
        var clone = {};
        merge(clone, config);


      //Merge clone with default config
        merge(clone, defaultConfig);
        config = clone;


      //Update event handlers
        for (var key in config) {
            if (config.hasOwnProperty(key)){
                if (typeof config[key] == "function") {
                    if (me[key] && typeof me[key] == "function"){
                        me[key] = config[key];
                    }
                }
            }
        }


      //Create ComboBox
        input = new javaxt.dhtml.ComboBox(parent, {
            maxVisibleRows: 6,
            showMenuOnFocus: false,
            style: config.style
        });
        input.filter = function(){}; //<--override native filter
        input.getButton().onclick = config.onButtonClick;


      //Watch for changes
        var timer;
        input.onChange = function(label, value){
            if (value) me.onSelect();
            if (timer) clearTimeout(timer);
            timer = setTimeout(function(){
                if (!value) search();
            }, config.searchDelay);
        };



      //Get reference to the input element
        var el = me.el = input.el;



      //Add event listener to the input
        el.addEventListener("focusout", function(){
            if (timer) clearTimeout(timer);
            if (socket) socket.close();
            socketStatus = 0;
        });
    };


  //**************************************************************************
  //** getValue
  //**************************************************************************
  /** Used to retrieve the value for the key/id associated with the selected
   *  search result
   */
    this.getValue = function(){
        return input.getValue();
    };


  //**************************************************************************
  //** setValue
  //**************************************************************************
    this.setValue = function(value, silent){
        input.setValue(value, silent);
    };


  //**************************************************************************
  //** onSelect
  //**************************************************************************
  /** Called whenever a user selects an item from the drop-down menu
   */
    this.onSelect = function(){};


  //**************************************************************************
  //** clear
  //**************************************************************************
    this.clear = function(){
        input.clear();
    };


  //**************************************************************************
  //** getInput
  //**************************************************************************
    this.getInput = function(){
        return input.getInput();
    };


  //**************************************************************************
  //** createFilter
  //**************************************************************************
    this.createFilter = function(text){
        return text;
    };


  //**************************************************************************
  //** parseResponse
  //**************************************************************************
  /** Default method used to parse responses from the server. Should return
   *  an array of key/value pairs to insert into the combobox. Example:
   *  [["Bob","12/30"],["Jim","10/28"]]
   */
    this.parseResponse = function(text){
        return [];
    };


  //**************************************************************************
  //** sendQuery
  //**************************************************************************
  /** Used to send a query to the remote server.
   */
    var sendQuery = function(){
        log("sendQuery");

        var text = input.getText().trim();
        if (text.length===0) return;

        var filter = me.createFilter(text);
        if (filter) socket.send(filter);
    };


  //**************************************************************************
  //** search
  //**************************************************************************
  /** Used to update the combo box and initiate a search.
   */
    var search = function(){
        log("search");

      //Clear menu options
        input.removeAll();


      //Create socket connection as needed
        if (!socket || socketStatus===0){
            openSocket();
        }
        else{
            if (socketStatus===3){
                sendQuery();
            }
        }
    };



  //**************************************************************************
  //** openSocket
  //**************************************************************************
  /** Used to create a socket connection to the server.
   */
    var openSocket = function(){
        socketStatus = 1;

      //Set URL
        var protocol = window.location.protocol.toLowerCase();
        if (protocol.indexOf("https")===0) protocol = "wss";
        else protocol = "ws";
        var url = protocol + '://' + window.location.host;
        if (config.url.indexOf("/")!=0) url += "/";
        url += config.url;



        log(url);
        socket = new WebSocket(url);
        socket.onopen = function(event){
            log("onopen");
            socketStatus = 2;
        };
        socket.onclose = function(event){
            log("onclose", event.code, event.reason);
            socketStatus = 0;
        };
        socket.onerror = function(error){
            log("onerror");
        };
        socket.onmessage = function(event) {
            var text = event.data;
            log(text);
            if (text==="Ready!"){
                socketStatus = 3;
                sendQuery();
            }
            else{
                var arr = config.parseResponse(text);

                try{
                    arr.forEach((entry)=>{
                        if (isArray(entry)){
                            var label = entry[0];
                            var value = entry[1];
                            input.add(label, value);
                        }
                    });

                    var options = input.getOptions();
                    log(options);

                    if (options.length>0){
                        input.showMenu();
                    }

                }
                catch(e){}


            }
        };

    };


  //**************************************************************************
  //** log
  //**************************************************************************
    var log = function(msg){
        var debugr = config.debugr;
        if (debugr && debugr.append){
            debugr.append(msg);
        }
        //console.log(msg);
    };


  //**************************************************************************
  //** Utils
  //**************************************************************************
    var merge = javaxt.dhtml.utils.merge;
    var isArray = javaxt.dhtml.utils.isArray;

    init();
};