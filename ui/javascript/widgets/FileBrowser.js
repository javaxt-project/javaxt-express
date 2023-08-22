if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  FileBrowser
//******************************************************************************
/**
 *   Window used to browse files and directories on a server
 *
 ******************************************************************************/

javaxt.express.FileBrowser = function(parent, config) {
    this.className = "javaxt.express.FileBrowser";

    var me = this;
    var win, grid, addressBar;
    var button = {};

    var params = {};
    var history = [];

    var currDir;
    var pathSeparator = "\\"; //updated dynamically using response from fileService
    var totalCount = 0;
    var totalSize = 0;


    var defaultConfig = {

      //Service config
        fileService: "files/",
        pageSize: 300,
        foldersOnly: false,


      //Window config
        title: null,
        width: 600,
        height: 450,
        modal: true,
        closable: true,
        footer: null,


      //Column config
        columns: [
            { header: "Name", field: "name", width: "100%", sortable: true },
            { header: "Date", field: "date", width: "135px", align: "left", sortable: true },
            { header: "Type", field: "type", width: "100px", sortable: true },
            { header: "Size", field: "size", width: "80px", align: "right", sortable: true }
        ],


      //Style config
        style:{


          /** Style for the window control used to hold this panel. This config
           *  is only valid when parent parent = document.body. See style
           *  config in the javaxt.dhtml.Window class for options.
           */
            window: {
                panel: "window",
                header: "window-header",
                button: "window-header-button",
                title: "window-title",
                body: {
                    padding: "0px",
                    verticalAlign: "top"
                }
            },


          /** Style for the toolbar.
           */
            toolbar: {


                panel: "panel-toolbar",


              /** Style for individual buttons in the toolbar. See style config
               *  in the javaxt.dhtml.Button class for options.
               */
                button: {}, //javaxt.dhtml.style.default.toolbarButton


              /** Icons styles used for individual buttons
               */
                icons: {
                    back: "backIcon",
                    forward: "nextIcon",
                    up: "upIcon",
                    refresh: "refreshIcon"
                },


              /** Style of the location/address input in the toolbar
               */
                path: "form-input",

                pathItem: ""
            },




            addressBarItem:{

            },


          /** Style for the table/grid control used to render files and folders.
           *  See style config in the javaxt.dhtml.DataGrid class for options.
           */
            table: {}, //javaxt.dhtml.style.default.table


          /** Style for the folder metadata that appears below the table.
           */
            info: {
                height: "20px"
            },


          //Left panel style
            leftPanel: {
                background: "#272a31",
                borderRight: "1px solid #383b41"
            },
            tree: {
                padding: "7px 0 7px 0px",
                backgroundColor: "",
                li: "tree-node"
            },


          //Center panel style
            centerPanel: {
                width: "100%",
                height: "100%"
            }

        },

        renderers: {
            iconRenderer: null,
            nameRenderer: null,
            dateRenderer: null,
            typeRenderer: null,
            sizeRenderer: null
        }

    };


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



      //Create main table
        var table = createTable();
        var td;

        td = table.addRow().addColumn();
        createNavbar(td);

        td = table.addRow().addColumn();
        td.style.height = "100%";
        createGrid(td);


        if (parent===document.body){

          //Create window
            win = new javaxt.dhtml.Window(parent, {
                title: config.title,
                width: config.width,
                height: config.height,
                modal: config.modal,
                closable: config.closable,
                footer: config.footer,
                style: config.style.window,
                body: table
            });


          //Copy window methods to this class
            for (var m in win) {
                if (typeof win[m] == "function") {
                    if (me[m]==null) me[m] = win[m];
                }
            }
        }
        else{
            parent.appendChild(table);
        }
    };



  //**************************************************************************
  //** setSort
  //**************************************************************************
    this.setSort = function(col,dir){
        var filter = grid.getFilter();
        if (!col) delete filter.orderby;
        else{
            if (!dir) dir = "";
            filter.orderby = col + " " + dir;
        }
        grid.setFilter(filter);
    };


  //**************************************************************************
  //** setDirectory
  //**************************************************************************
  /** Used to set the current directory and update the view
   */
    this.setDirectory = function(path){
        grid.clear();

        if (path==null) path = "";
        else path = path.trim();

        if (path.length==0) {
            button.up.disable();
        }
        else{
            button.up.enable();
        }

        params.path = path;
        params.hidden = false;
        params.foldersOnly = config.foldersOnly;
        grid.load();
        history.push(path);
        addressBar.update(path);
    };


  //**************************************************************************
  //** getDirectory
  //**************************************************************************
    this.getDirectory = function(){
        var path = params.path;
        if (path && path.length>0){
            var s = "/";
            if (path.indexOf("\\")>-1) s = "\\";
            if (path.substring(path.length-1)!=s) path+=s;
        }
        return path;
    };


  //**************************************************************************
  //** onDirectoryChange
  //**************************************************************************
    this.onDirectoryChange = function(dir){};


  //**************************************************************************
  //** onClick
  //**************************************************************************
    this.onClick = function(item, path, row, e){};


  //**************************************************************************
  //** onDoubleClick
  //**************************************************************************
    this.onDoubleClick = function(item, path, row, e){};


  //**************************************************************************
  //** onSelectionChange
  //**************************************************************************
    this.onSelectionChange = function(){};


  //**************************************************************************
  //** getSelectedItems
  //**************************************************************************
    this.getSelectedItems = function(){
        var arr = grid.getSelectedRecords();
        for (var i=0; i<arr.length; i++){
            arr[i] = parseRecord(arr[i]);
        }
        return arr;
    };


  //**************************************************************************
  //** createNavbar
  //**************************************************************************
    var createNavbar = function(parent){
        setStyle(parent, config.style.toolbar.panel);
        var tr = createTable(parent).addRow();
        createToolbar(tr.addColumn());
        createAddressBar(tr.addColumn({
            width: "100%"
        }));
    };


  //**************************************************************************
  //** createToolbar
  //**************************************************************************
    var createToolbar = function(parent){

        var tr = createTable(parent).addRow();
        var td;


      //Back button
        td = tr.addColumn();
        var backButton = createButton(td, {
            label: "",
            icon: config.style.toolbar.icons.back,
            disabled: true
        });
        backButton.onClick = function(){

        };


      //Forward button
        td = tr.addColumn();
        var forwardButton = createButton(td, {
            label: "",
            icon: config.style.toolbar.icons.forward,
            disabled: true
        });
        forwardButton.onClick = function(){

        };



      //Up button
        td = tr.addColumn();
        var upButton = createButton(td, {
            label: "",
            icon: config.style.toolbar.icons.up,
            disabled: true
        });
        upButton.onClick = function(){
            if (!currDir) return;
            if (currDir.length===0) return;

            var str = getPath(currDir, true);
            var idx = str.lastIndexOf("/");
            var path = currDir.substring(0, idx+1);


            if (getPath(path).indexOf("/")>-1){
                me.setDirectory(path);
            }
            else{
                me.setDirectory("");
            }
        };

        button.up = upButton;
    };


  //**************************************************************************
  //** createAddressBar
  //**************************************************************************
    var createAddressBar = function(parent){

        var tr = createTable(parent).addRow();
        var td = tr.addColumn();
        td.style.width = "100%";


        var div = createElement("div", td);
        setStyle(div, config.style.toolbar.path);
        div.style.position = "relative";
        div.style.width = "100%";


        var outerDiv = createElement("div", div);
        outerDiv.style.position = "absolute";
        outerDiv.style.width = "100%";
        outerDiv.style.height = "100%";
        outerDiv.style.overflow = "hidden";
        outerDiv.style.whiteSpace = "nowrap";


        var innerDiv = createElement("div", outerDiv);
        innerDiv.style.position = "absolute";
        innerDiv.style.height = "100%";
        innerDiv.style.overflow = "hidden";
        innerDiv.style.whiteSpace = "nowrap";



        addressBar = {
            clear: function(){
                innerDiv.innerHTML = "";
            },
            update: function(path){
                innerDiv.innerHTML = "";
                path = getPath(path, true);
                if (path.length==0) return;
                var arr = path.split("/");
                for (var i=0; i<arr.length; i++){
                    var div = createElement("div", innerDiv);
                    setStyle(div, config.style.addressBarItem);
                    div.style.display = "inline-block";
                    div.innerHTML = arr[i] + pathSeparator;
                    outerDiv.scrollLeft = innerDiv.clientWidth;

                    div.onclick = function(){
                        var path = "";
                        for (var i=0; i<innerDiv.childNodes.length; i++){
                            var div = innerDiv.childNodes[i];
                            path+=div.innerHTML;
                            if (div==this) break;
                        }

                        if (path!=getPath(currDir, true)){
                            me.setDirectory(path);
                        }
                    };
                }

            }
        };


      //Refresh button
        var refreshButton = createButton(tr.addColumn(), {
            label: "",
            icon: config.style.toolbar.icons.refresh,
            disabled: false
        });
        refreshButton.onClick = function(){
            grid.refresh();
        };

    };


  //**************************************************************************
  //** createGrid
  //**************************************************************************
    var createGrid = function(parent){
        var table = createTable(parent);
        var td;


      //Create grid
        td = table.addRow().addColumn();
        setStyle(td, config.style.centerPanel);

        grid = new javaxt.dhtml.DataGrid(td, {
            columns: config.columns,
            style: config.style.table,
            url: config.fileService,
            limit: config.pageSize,
            getResponse: function(url, payload, callback){
                post(url, JSON.stringify(params), {
                    success: function(txt,xml,url,req){
                        callback.apply(me, [req]);
                    },
                    failure: function(req){
                        alert(req);
                    }
                });
            },
            parseResponse: function(request){
                var data = JSON.parse(request.responseText);
                currDir = data.dir;
                me.onDirectoryChange(me.getDirectory());
                pathSeparator = data.pathSeparator;
                totalCount = data.count;
                totalSize = data.size;
                return data.items;
            },
            update: function(row, record){
                var item = parseRecord(record);
                row.set("Name", getItem(item));
                row.set("Date", getDate(item));
                row.set("Type", getType(item));
                row.set("Size", getSize(item));
            }
        });


      //Create footer
        td = table.addRow().addColumn();
        setStyle(td, config.style.info);

        var info = createElement("div", td, {
            position: "absolute"
        });

        var countInfo = createElement("div", info);
        countInfo.style.display = "inline-block";

        var sizeInfo = createElement("div", info);
        sizeInfo.style.display = "inline-block";
        sizeInfo.style.marginLeft = "7px";

        var waitmask;
        grid.beforeLoad = function(page){
            //me.onSelectionChange();
            if (!waitmask) waitmask = new javaxt.express.WaitMask(table);
            waitmask.show(500);
            countInfo.innerHTML = "";
            sizeInfo.innerHTML = "";
        };

        grid.onLoad = function(){
            waitmask.hide();
            countInfo.innerHTML = totalCount + " items";
            if (totalSize>0) sizeInfo.innerHTML = formatSize(totalSize);
        };

        grid.onSelectionChange = function(){
            me.onSelectionChange();
        };

        grid.onRowClick = function(row, e){
            var item = parseRecord(row.record);
            var path;
            if (currDir) path = currDir + item.name;
            else path = item.name;

            if (e.detail===2){ //double click event
                me.onDoubleClick(item, path, row, e);
            }
            else{
                me.onClick(item, path, row, e);
            }
        };
    };


  //**************************************************************************
  //** parseRecord
  //**************************************************************************
    var parseRecord = function(record){
        return {
            name : record[0],
            type : record[1],
            date : new Date(record[2]),
            size : record[3]
        };
    };


  //**************************************************************************
  //** getName
  //**************************************************************************
    var getName = function(item){
        return render(item, 'name', function(){
            return item.name;
        });
    };


  //**************************************************************************
  //** getIcon
  //**************************************************************************
    var getIcon = function(item){
        return render(item, 'icon', function(){
            return null;
        });
    };


  //**************************************************************************
  //** getItem
  //**************************************************************************
    var getItem = function(item){
        var name = getName(item);
        var icon = getIcon(item);
        if (icon){

            var outerDiv = createElement("div");

            var div = createElement("div", outerDiv);
            div.style.display = "inline-block";
            setContent(icon, div);

            var div = createElement("div", outerDiv);
            div.style.display = "inline-block";
            setContent(name, div);

            return outerDiv;
        }
        else{
            return name;
        }
    };


  //**************************************************************************
  //** setContent
  //**************************************************************************
    var setContent = function(content, el){
        if (content==null || typeof content === "undefined"){
            el.innerHTML = "";
        }
        else{
            if (typeof content === "string" || !isNaN(parseFloat(content))){
                el.innerHTML = content;
            }
            else{
                el.innerHTML = "";
                try{
                    el.appendChild(content);
                }
                catch(e){
                }
            }
        }
    };


  //**************************************************************************
  //** getDate
  //**************************************************************************
    var getDate = function(item){
        return render(item, 'date', function(){
            var date = item.date;
            if (item.type=="Drive"){
                return "";
            }
            else{
                return formatDate(date);
            }
        });
    };


  //**************************************************************************
  //** getItemSize
  //**************************************************************************
    var getSize = function(item){
        return render(item, 'size', function(){
            var size = item.size;
            if (item.type=="Folder" || item.type=="Drive"){
                return "";
            }
            else{
                return formatSize(size);
            }
        });
    };


  //**************************************************************************
  //** getItemType
  //**************************************************************************
    var getType = function(item){
        return render(item, 'type', function(){
            return item.type;
        });
    };


  //**************************************************************************
  //** formatSize
  //**************************************************************************
    var formatSize = function(size){
        if (size>0){
            size = size/1024;
            if (size<=1) size = "1 KB";
            else{
                size = size/1024;
                if (size<=1) size = "1 MB";
                else{
                    size = Math.round(size) + " MB";
                }
            }
        }
        return size;
    };


  //**************************************************************************
  //** formatDate
  //**************************************************************************
    var formatDate = function(date){
        var hour = date.getHours();
        if (hour==0) hour = 12;
        else{
            if (hour>12) hour=hour-12;
        }
        var m = (date.getHours()<12) ? "AM" : "PM";
        var minutes = date.getMinutes();
        if (minutes<10) minutes = "0"+minutes;

        return (date.getMonth()+1) + "/" + date.getDate() + "/" + date.getFullYear() +
            " " + hour + ":" + minutes + " " + m;
    };


  //**************************************************************************
  //** createThumbnails
  //**************************************************************************
    var createThumbnails = function(parent){

    };


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Used to normalize a file path, replacing backslashes with forward
   *  slashes
   */
    var getPath = function(str, trim){
        str = str.replace(/\\/g, '/');
        if (trim===true){
            if (str.substring(str.length-1)==="/"){
                str = str.substring(0, str.length-1);
            }
        }
        return str;
    };


  //**************************************************************************
  //** render
  //**************************************************************************
    var render = function(item, renderer, fn){
        var key = renderer + "Renderer";
        if (config.renderers[key]) return config.renderers[key](item);
        else return fn.call();
    };


  //**************************************************************************
  //** createButton
  //**************************************************************************
    var createButton = function(toolbar, btn){
        btn.style = JSON.parse(JSON.stringify(config.style.toolbar.button));
        if (btn.icon){
            btn.style.icon = "toolbar-button-icon " + btn.icon;
            delete btn.icon;
        }
        return new javaxt.dhtml.Button(toolbar, btn);
    };


  //**************************************************************************
  //** Utilites
  //**************************************************************************
  /** Common functions found in Utils.js
   */
    var post = javaxt.dhtml.utils.post;
    var del = javaxt.dhtml.utils.delete;
    var clone = javaxt.dhtml.utils.clone;
    var merge = javaxt.dhtml.utils.merge;
    var setStyle = javaxt.dhtml.utils.setStyle;
    var createElement = javaxt.dhtml.utils.createElement;
    var createTable = javaxt.dhtml.utils.createTable;

    init();
};