if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  FileUploader
//******************************************************************************
/**
 *   Window used to upload files to a server
 *
 ******************************************************************************/

javaxt.express.FileUploader = function(parent, config) {
    this.className = "javaxt.express.FileUploader";

    var me = this;
    var win;


    var defaultConfig = {

      //Service config
        uploadService: "upload",
        maxUploads: 1,


      //Window config
        title: "File Upload",
        width: 600,
        height: 450,
        modal: true,
        closable: true,
        footer: null,



      //Style config
        style:{

          //Window style
            panel: "window",
            header: "window-header",
            button: "window-header-button",
            title: "window-title",
            body: "window-body",


            uploadArea: {
                border: "2px dashed #bbb",
                borderRadius: "5px",
                padding: "25px",
                margin: "25px",
                textAlign: "center",
                fontSize: "20pt",
                fontWeight: "bold",
                color: "#bbb",
                backgroundColor: "#f8f8f8",
                height: "200px"
            },

          //Center panel style
            centerPanel: {
                width: "100%",
                height: "100%",
                background: "#fff" //f8f8f8
            },
            table: {}, //javaxt.dhtml.style.default.table
            info: {
                height: "20px"
            }
        },

        renderers: {

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
        var tbody = table.firstChild;
        var tr, td;

        tr = document.createElement("tr");
        tbody.appendChild(tr);
        td = document.createElement("td");
        tr.appendChild(td);
        createUploadArea(td);


        tr = document.createElement("tr");
        tbody.appendChild(tr);
        td = document.createElement("td");
        tr.appendChild(td);
        //createBody(td);



      //Create window
        config.body = table;
        win = new javaxt.dhtml.Window(parent, config);


      //Copy window methods to this class
        for (var m in win) {
            if (typeof win[m] == "function") {
                if (me[m]==null) me[m] = win[m];
            }
        }

        me.el = win.el;




      //Watch for drag and drop events
        me.el.addEventListener('dragover', onDragOver, false);
        me.el.addEventListener('drop', function(e) {
            e.stopPropagation();
            e.preventDefault();
            var files = e.dataTransfer.files;
            me.upload(files);
        }, false);

    };


  //**************************************************************************
  //** accept
  //**************************************************************************
  /** Example:
   <pre>
    var uploader = new javaxt.express.FileUploader(...);
    uploader.accept = function(file){
        if (file.type.match('image.*')) return true;
        return false;
    }
   </pre>
   */
    this.accept = function(file){
        return true;
    };


  //**************************************************************************
  //** upload
  //**************************************************************************
    this.upload = function(files){
        var arr = [];
        for (var i=0; i<files.length; i++) {
            var file = files[i];
            if (me.accept(file)===true){
                arr.push(file);
            }
        }


        var x = 0;
        var numUploads = arr.length;
        var upload = function(){
            if (arr.length==0) return;
            //if (numUploads==config.maxUploads) return;
            var file = arr.shift();
            var formData = new FormData();
            formData.append(file.name, file);
            post(config.uploadService, formData, {
                success: function(text){
                    x++;
                    upload();
                },
                failure: function(request){
                    x++;
                    upload();
                }
            });
        };
        upload();
    };


  //**************************************************************************
  //** createUploadArea
  //**************************************************************************
    var createUploadArea = function(parent){

        var div = document.createElement("div");
        setStyle(div, config.style.uploadArea);
        parent.appendChild(div);

        var input = document.createElement("input");
        input.type = "file";
        input.style.display = "none";
        input.onchange = function(){
            me.upload(this.files);
        };
        div.appendChild(input);

        var text = document.createElement("span");
        text.innerHTML = "Drag and drop files or ";
        div.appendChild(text);

        var link = document.createElement("a");
        link.href="#";
        link.innerHTML = "browse";
        link.onclick = function(e){
            e.preventDefault();
            input.click();
        };
        div.appendChild(link);
    };


  //**************************************************************************
  //** onDragOver
  //**************************************************************************
  /** Called when the client drags a file over the parent.
   */
    var onDragOver = function(e) {
        e.stopPropagation();
        e.preventDefault();
        e.dataTransfer.dropEffect = 'copy'; // Explicitly show this is a copy.
    };


  //**************************************************************************
  //** createBody
  //**************************************************************************
    var createBody = function(parent){

        var table = createTable();
        var tbody = table.firstChild;
        var tr, td;

        tr = document.createElement("tr");
        tbody.appendChild(tr);

        td = document.createElement("td");
        tr.appendChild(td);
        createNav(td);

        td = document.createElement("td");
        td.style.width = "100%";
        tr.appendChild(td);
        createGrid(td);

        parent.appendChild(table);
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
  //** Utilites
  //**************************************************************************
  /** Common functions found in Utils.js
   */
    var post = javaxt.dhtml.utils.post;
    var clone = javaxt.dhtml.utils.clone;
    var merge = javaxt.dhtml.utils.merge;
    var setStyle = javaxt.dhtml.utils.setStyle;
    var createTable = javaxt.dhtml.utils.createTable;

    init();
};