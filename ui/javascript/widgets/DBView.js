if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  DBView
//******************************************************************************
/**
 *   Panel used to execute queries and view results in a grid. This widget is
 *   designed to work with REST services provided by implementations of the
 *   javaxt.express.services.QueryService class.
 *
 ******************************************************************************/

javaxt.express.DBView = function(parent, config) {
    this.className = "javaxt.express.DBView";

    var me = this;
    var defaultConfig = {

      /** If the CodeMirror library is available, it will be used as the query
       *  editor (vs default textarea). You can specify a coding language to
       *  use with CodeMirror (e.g. sql, cypher, etc). Default is sql.
       */
        queryLanguage: "sql",


      /** Path to REST endpoint used to create, get, and delete query jobs.
       *  This path may be overridden via the createJob, getJob, and cancelJob
       *  config.
       */
        queryService: "sql/job/",


      /** Path to REST endpoint that returns a list of tables. Alternatively,
       *  you can provide a function with a callback.
       */
        getTables: "sql/tables/",


      /** Path to REST endpoint used to create a query job. Alternatively, you
       *  can provide a function that executes a "POST" request.
       */
        createJob: "sql/job/",


      /** Path to REST endpoint used to delete/cancel a query job. Note that
       *  the "{jobID}" will be replaced with an actual id. Alternatively, you
       *  can provide a function that executes a "DELETE" request. The function
       *  should return an XHR request.
       */
        cancelJob: "sql/job/{jobID}",


      /** Path to REST endpoint that returns results of a query job. Note that
       *  the "{jobID}" will be replaced with an actual id. Alternatively, you
       *  can provide a function that executes a "GET" request. The function
       *  should return an XHR request.
       */
        getJob: "sql/job/{jobID}",


      /** Used to specify the page size (i.e. the maximum number records to
       *  fetch from the server at a time)
       */
        pageSize: 50,


      /** Used to specify the response format for the query results. Options
       *  include "json", "csv", "tsv, and "jsv"
       */
        format: "json",


      /** Style for individual elements within the component. Note that you can
       *  provide CSS class names instead of individual style definitions.
       */
        style:{
            container: {
                width: "100%",
                height: "100%"
            },

            border: "1px solid #383b41",

            leftPanel: {
                backgroundColor: "#272a31",
                borderRight: "1px solid #383b41",
                color: "#bfc0c3"
            },

            bottomPanel: {
                backgroundColor: "#e4e4e4",
                borderTop: "1px solid #b5b5b5"
                /*
                fontFamily: '"Consolas", "Bitstream Vera Sans Mono", "Courier New", Courier, monospace';
                color: "#97989c";
                 */
            },

            toolbar: "panel-toolbar",
            toolbarButton: {}, //javaxt.dhtml.style.default.toolbarButton
            toolbarIcons: {
                run: "runIcon",
                cancel: "deleteIcon"
            },

            editor: {
                height: "240px",
                /*
                background: "inherit",
                border: "0px none",

                margin: 0,
                padding: "5px 10px",

                fontFamily: '"Consolas", "Bitstream Vera Sans Mono", "Courier New", Courier, monospace',
                color: "#97989c"
                */
            },

            table: {}, //javaxt.dhtml.style.default.table
            tree: {
                padding: "7px 0 7px 0px",
                backgroundColor: "",
                li: "tree-node"
            }
        },


      /** By default, when a user clicks on a node in the tree, the query editor
       *  is updated with a default query statement for the node. You can update
       *  this default behaviour by providing a custom function.
       */
        onTreeClick: function(item){
            var tableName = item.name;
            if (item.node){
                var schema = item.node.schema;
                if (schema && schema!=="public"){
                    tableName = schema + "." + tableName;
                }
            }
            editor.setValue("select * from " + tableName);
        }
    };


    var tree, toolbar, editor, grid, gridContainer, waitmask;
    var runButton, cancelButton;
    var jobID;
    var border;



  //**************************************************************************
  //** Constructor
  //**************************************************************************
    var init = function(){

        if (typeof parent === "string"){
            parent = document.getElementById(parent);
        }
        if (!parent) return;


      //Clone the config so we don't modify the original config object
        config = clone(config);


      //Merge clone with default config
        config = merge(config, defaultConfig);


      //Get waitmask
        waitmask = config.waitmask;
        border = config.border;


      //Create main div
        var div = createElement('div', parent, config.style.container);
        me.el = div;
        addShowHide(me);


      //Create table with 2 columns
        var table = createTable(div);
        var tr = table.addRow();


      //Left column
        var td = tr.addColumn(config.style.leftPanel);
        td.style.height = "100%";
        var target = createTableView(td);


      //Right column
        var td = tr.addColumn({
            height: "100%",
            width: "100%"
        });

        var hr = addHorizontalResizer(td, target);
        createQueryView(hr);



        onRender(me.el, function(){
            if (waitmask) waitmask.show(500);
            var getTables = config.getTables;
            if (typeof getTables === "string"){
                get(getTables, {
                    success: function(text){
                        if (waitmask) waitmask.hide();

                      //Parse response
                        var tables = JSON.parse(text).tables;


                      //Add nodes to the tree
                        tree.addNodes(tables);

                    },
                    failure: function(request){
                        if (waitmask) waitmask.hide();
                        alert(request);
                    }
                });
            }
            else if (typeof getTables === "function") {
                getTables.apply(me, [tree]);
            }

        });
    };


  //**************************************************************************
  //** setQuery
  //**************************************************************************
    this.setQuery = function(query){
        if (query){
            if (query===editor.getValue()){
                //Do nothing
            }
            else{
                editor.setValue(query);
                me.clearResults();
            }
        }
        else{
            me.clear();
        }
    };


  //**************************************************************************
  //** getQuery
  //**************************************************************************
    this.getQuery = function(){
        return editor.getValue();
    };


  //**************************************************************************
  //** executeQuery
  //**************************************************************************
    this.executeQuery = function(){

      //Cancel current query (if running)
        cancel();


      //Show waitmask
        if (waitmask) waitmask.show(500);


      //Clear current grid
        me.clearResults();
        grid = null;


      //Set parameters for the query service
        var payload = {
            query: editor.getValue(),
            limit: config.pageSize,
            format: config.format
        };


      //Execute query and render results
        getResponse(payload, function(request){
            cancelButton.disable();
            var data = parseResponse(request.responseText);
            render(data.records, data.columns);
            if (waitmask) waitmask.hide();
        });
    };


  //**************************************************************************
  //** clear
  //**************************************************************************
    this.clear = function(){
        editor.setValue("");
        me.clearResults();
    };


  //**************************************************************************
  //** clearResults
  //**************************************************************************
    this.clearResults = function(){
        if (grid){
            grid.clear();
            destroy(grid);
        }
        gridContainer.innerHTML = "";
    };


  //**************************************************************************
  //** getComponents
  //**************************************************************************
  /** Returns a handle to individual components of this view
   */
    this.getComponents = function(){
        return {
            tree: tree,
            grid: grid,
            editor: editor,
            toolbar: toolbar
        };
    };


  //**************************************************************************
  //** createTableView
  //**************************************************************************
  /** Creates a panel used to render a list of tables in the database. Tables
   *  are rendered in a tree view.
   */
    var createTableView = function(parent){

        var outerDiv = createElement("div", parent, {
            position: "relative",
            height: "100%",
            width: "210px",
            overflow: "auto"
        });

        var div = createElement("div", outerDiv, {
            position: "absolute",
            width: "100%"
        });


      //Create tree
        tree = new javaxt.dhtml.Tree(div, {
            style: config.style.tree
        });


        tree.onClick = function(item){
            config.onTreeClick.apply(me, [item]);
        };

        return outerDiv;
    };


  //**************************************************************************
  //** createQueryView
  //**************************************************************************
    var createQueryView = function(parent){
        var table = createTable(parent);


      //Create toolbar
        toolbar = table.addRow().addColumn(config.style.toolbar);
        addButtons(toolbar);


      //Create editor
        var td = table.addRow().addColumn(config.style.editor);
        td.style.borderBottom = border;

        var target = td;
        if (typeof CodeMirror !== 'undefined'){
            var outerDiv = createElement("div", td, {
                height: "100%",
                position: "relative"
            });

            var innerDiv = createElement("div", outerDiv, {
                width: "100%",
                height: "100%",
                position: "absolute",
                overflow: "hidden",
                overflowX: "auto"
            });



            editor = CodeMirror(innerDiv, {
                value: "",
                mode:  config.queryLanguage,
                lineNumbers: true,
                indentUnit: 4
            });
            td.childNodes[0].style.height = "100%";

            editor.setValue = function(str){
                var doc = this.getDoc();
                doc.setValue(str);
                doc.clearHistory();

                var cm = this;
                setTimeout(function() {
                    cm.refresh();
                },200);
            };
            editor.getValue = function(){
                return this.getDoc().getValue();
            };
        }
        else{
            editor = createElement('textarea', td, {
                width: "100%",
                height: "100%",
                resize: "none",
                border: "0 none",
                padding: "10px",
                boxSizing: "border-box" //this is critical if you want padding
            });
            editor.spellcheck = false;
            editor.getValue = function(){
                return this.value;
            };
            editor.setValue = function(str){
                this.value = str;
            };
        }



      //Create results table
        td = table.addRow().addColumn(config.style.bottomPanel);
        td.style.height = "100%";
        td.style.verticalAlign = "top";



      //Add vertical resizer
        addVerticalResizer(td, target);


      //Add div for the grid
        gridContainer = createElement("div", td, {
            height: "100%"
        });

    };


  //**************************************************************************
  //** addVerticalResizer
  //**************************************************************************
  /** Inserts a resize handle into the parent. Assumes target is above the
   *  parent.
   */
    var addVerticalResizer = function(parent, target){
        var div = createElement("div", parent, {
            position: "relative"
        });

        var resizeHandle = createElement("div", div, {
            position: "absolute",
            width: "100%",
            height: "10px",
            top: "-5px",
            cursor: "ns-resize",
            zIndex: 2
        });


        javaxt.dhtml.utils.initDrag(resizeHandle, {
            onDragStart: function(x,y){
                var div = this;
                div.yOffset = y;
                div.initialHeight = target.offsetHeight;
            },
            onDrag: function(x,y){
                var div = this;
                var top = (div.yOffset-y);
                var height = div.initialHeight-top;
                target.style.height = height + "px";
            },
            onDragEnd: function(){
            }
        });
    };


  //**************************************************************************
  //** addHorizontalResizer
  //**************************************************************************
  /** Inserts a resize handle into the parent. Assumes target is to the left
   *  of the parent parent.
   */
    var addHorizontalResizer = function(parent, target){

        var div = createElement("div", parent, {
            position: "relative",
            height: "100%"
        });

        var resizeHandle = createElement("div", div, {
            position: "absolute",
            height: "100%",
            width: "10px",
            left: "-5px",
            cursor: "ew-resize",
            zIndex: 2
        });


        javaxt.dhtml.utils.initDrag(resizeHandle, {
            onDragStart: function(x,y){
                var div = this;
                div.xOffset = x;
                div.initialWidth = target.offsetWidth;
            },
            onDrag: function(x,y){
                var div = this;
                var left = (div.xOffset-x);
                var width = div.initialWidth-left;
                target.style.width = width + "px";
            },
            onDragEnd: function(){
            }
        });

        return div;
    };


  //**************************************************************************
  //** addButtons
  //**************************************************************************
  /** Adds buttons to the toolbar
   */
    var addButtons = function(toolbar){


      //Add button
        runButton = createButton(toolbar, {
            label: "Run",
            //menu: true,
            icon: config.style.toolbarIcons.run,
            disabled: false
        });
        runButton.onClick = function(){
            me.executeQuery();
        };



      //Cancel button
        cancelButton = createButton(toolbar, {
            label: "Cancel",
            icon: config.style.toolbarIcons.cancel,
            disabled: true
        });
        cancelButton.onClick = function(){
            cancel(function(){
                if (waitmask) waitmask.hide();
            });
        };
    };


  //**************************************************************************
  //** cancel
  //**************************************************************************
  /** Used to cancel the current query
   */
    var cancel = function(callback){
        if (jobID){

            var request;
            var requestConfig = {
                success : function(){
                    cancelButton.disable();
                    if (callback) callback.apply(null,[]);
                },
                failure: function(request){
                    cancelButton.disable();
                    if (callback) callback.apply(null,[]);

                    if (request.status!==404){
                        showError(request);
                    }
                }
            };

            var cancelJob = config.cancelJob;
            if (!cancelJob) cancelJob = config.queryService + jobID;
            if (typeof cancelJob === "string"){
                var url = cancelJob.replace("{jobID}",jobID);
                request = javaxt.dhtml.utils.delete(url, requestConfig);
            }
            else if  (typeof cancelJob === "function") {
                request = cancelJob.apply(me, [jobID, requestConfig]);
            }

        }
        else{
            if (callback) callback.apply(null,[]);
        }
        jobID = null;
    };


  //**************************************************************************
  //** showError
  //**************************************************************************
  /** Used to render error messages returned from the server
   */
    var showError = function(msg){
        cancelButton.disable();

        if (msg.responseText){
            msg = (msg.responseText.length>0 ? msg.responseText : msg.statusText);
        }
        gridContainer.innerHTML = msg;
        if (waitmask) waitmask.hide();
    };


  //**************************************************************************
  //** getResponse
  //**************************************************************************
  /** Used to execute a sql api request and get a response. Note that queries
   *  are executed asynchronously. This method will pool the sql api until
   *  the query is complete.
   */
    var getResponse = function(payload, callback){


        var requestConfig = {
            success : function(text){

                jobID = JSON.parse(text).job_id;
                cancelButton.enable();


              //Periodically check job status
                var timer;
                var checkStatus = function(){
                    if (jobID){


                        var request;
                        var requestConfig = {
                            success : function(text){
                                if (text==="pending" || text==="running"){
                                    timer = setTimeout(checkStatus, 250);
                                }
                                else{
                                    clearTimeout(timer);
                                    callback.apply(me, [request]);
                                }
                            },
                            failure: function(response){
                                clearTimeout(timer);
                                showError(response);
                            }
                        };

                        var getJob = config.getJob;
                        if (!getJob) getJob = config.queryService + jobID;
                        if (typeof getJob === "string"){
                            var url = getJob.replace("{jobID}",jobID);
                            request = get(url, requestConfig);
                        }
                        else if  (typeof getJob === "function") {
                            request = getJob.apply(me, [jobID, requestConfig]);
                        }

                    }
                    else{
                        clearTimeout(timer);
                    }
                };
                timer = setTimeout(checkStatus, 250);

            },
            failure: function(response){
                //mainMask.hide();
                showError(response);
            }
        };



        var createJob = config.createJob;
        if (!createJob) createJob = config.queryService;
        if (typeof createJob === "string"){
            post(createJob, JSON.stringify(payload), requestConfig);
        }
        else if  (typeof createJob === "function") {
            createJob.apply(me, [payload, requestConfig]);
        }
    };


  //**************************************************************************
  //** parseResponse
  //**************************************************************************
  /** Used to parse the query response from the sql api
   */
    var parseResponse = function(text){

        var records = [];
        var columns = [];

        if (config.format==="json"){
            var json = JSON.parse(text);


          //Get rows
            var rows = json.rows;


          //Generate list of columns
            var record = rows[0];
            for (var key in record) {
                if (record.hasOwnProperty(key)) {
                    columns.push(key);
                }
            }


          //Generate records
            for (var i=0; i<rows.length; i++){
                var record = [];
                var row = rows[i];
                for (var j=0; j<columns.length; j++){
                    var key = columns[j];
                    var val = row[key];
                    record.push(val);
                }
                records.push(record);
            }


        }
        else if (config.format==="jsv"){
            var json = JSON.parse(text);
            records = json.rows;
            columns = json.cols;
        }
        else{
            //csv or tsv
        }

        return {
            columns: columns,
            records: records
        };
    };


  //**************************************************************************
  //** render
  //**************************************************************************
  /** Used to render query results in a grid
   */
    var render = function(records, columns){
        if (columns.length===0) return;

        //mainMask.hide();


      //Create divs
        var outerDiv = createElement("div", gridContainer, {
            position: "relative",
            height: "100%"
        });


        var innerDiv = createElement("div", outerDiv, {
            position: "absolute",
            width: "100%",
            height: "100%",
            overflow: "hidden",
            overflowX: "auto"
        });


        var overflowDiv = createElement("div", innerDiv, {
            position: "absolute",
            width: "100%",
            height: "100%"
        });



      //Create grid after the divs are rendered
        onRender(innerDiv, function(){
            var rect = javaxt.dhtml.utils.getRect(innerDiv);


          //Compute column widths
            var widths = [];
            var minWidths = [];
            var headerWidth = 0;
            var pixelsPerChar = 10;
            if (columns.length>1){

                var arr = [];
                arr.push(columns);
                arr.push(...records);

                var o = getSuggestedColumnWidths(arr, pixelsPerChar, rect.width);
                widths = o.suggestedWidths;
                headerWidth = o.headerWidth;

                for (var i=0; i<columns.length; i++){
                    var minWidth = columns[i].length*pixelsPerChar;
                    if (minWidth<150) minWidth = 150;
                    minWidths.push(minWidth);
                }
            }
            else{
                widths.push("100%");
                minWidths.push(null);
            }


          //Generate column definitions
            var arr = [];
            for (var i=0; i<columns.length; i++){
                arr.push({
                   header: columns[i],
                   width: widths[i],
                   minWidth: minWidths[i],
                   sortable: false
                });
            }



          //Adjust width of the overflowDiv as needed
            if (rect.width<headerWidth){
                overflowDiv.style.width = headerWidth + "px";
            }



          //Create grid
            grid = new javaxt.dhtml.DataGrid(overflowDiv, {
                columns: arr,
                style: config.style.table,
                url: config.queryService,
                limit: config.pageSize,
                getResponse: function(url, payload, callback){

                    payload = {
                        query: editor.getValue(),
                        page: grid.getCurrPage(),
                        limit: config.pageSize,
                        format: config.format
                    };

                    getResponse(payload, callback);
                },
                parseResponse: function(request){
                    return parseResponse(request.responseText).records;
                },
                update: function(row, record){
                    for (var i=0; i<record.length; i++){
                        var val = record[i];
                        if (val === null || val === undefined){
                            val = "";
                        }
                        else{
                            if (val.constructor === arrayConstructor ||
                                val.constructor === objectConstructor) {
                                val = JSON.stringify(val);
                            }
                        }
                        row.set(i, val);
                    }
                }
            });


            grid.beforeLoad = function(page){
                //mainMask.show();
                //cancelButton.disable();
            };

            grid.afterLoad = function(){
                //mainMask.hide();
                cancelButton.disable();
            };

            grid.onSelectionChange = function(){

            };

            grid.getColumns = function(){
                return columns;
            };

            grid.getConfig = function(){
                return {
                    columns: arr
                };
            };


            grid.load(records, 1);
        });
    };


  //**************************************************************************
  //** createButton
  //**************************************************************************
    var createButton = function(toolbar, btn){

        btn.style = JSON.parse(JSON.stringify(config.style.toolbarButton)); //<-- clone the style config!
        if (btn.icon){
            btn.style.icon = "toolbar-button-icon " + btn.icon;
            delete btn.icon;
        }


        if (btn.menu===true){
            btn.style.arrow = "toolbar-button-menu-icon";
            btn.style.menu = "menu-panel";
            btn.style.select = "panel-toolbar-menubutton-selected";
        }

        return new javaxt.dhtml.Button(toolbar, btn);
    };

var arrayConstructor = [].constructor;
var objectConstructor = ({}).constructor;


  //**************************************************************************
  //** Utilites
  //**************************************************************************
  /** Common functions found in Utils.js
   */
    var get = javaxt.dhtml.utils.get;
    var post = javaxt.dhtml.utils.post;
    var del = javaxt.dhtml.utils.delete;
    var clone = javaxt.dhtml.utils.clone;
    var merge = javaxt.dhtml.utils.merge;
    var destroy = javaxt.dhtml.utils.destroy;
    var onRender = javaxt.dhtml.utils.onRender;
    var addShowHide = javaxt.dhtml.utils.addShowHide;
    var createTable = javaxt.dhtml.utils.createTable;
    var createElement = javaxt.dhtml.utils.createElement;
    var getSuggestedColumnWidths = javaxt.dhtml.utils.getSuggestedColumnWidths;


    init();
};