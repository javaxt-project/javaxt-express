if(!javaxt) var javaxt={};
if(!javaxt.dhtml) javaxt.dhtml={};

//******************************************************************************
//**  Thumbnail Editor
//*****************************************************************************/
/**
 *   Used to resize and crop an image
 *
 ******************************************************************************/

javaxt.dhtml.ThumbnailEditor = function(parent, config) {

    var me = this;
    var defaultConfig = {

      /** Width of the editor
       */
        thumbnailWidth: 400,

      /** Height of the editor
       */
        thumbnailHeight: 400,

      /** Used to render a mask over the image
       */
        mask: 'circle',

      /** If true, will render a slider used to resize the image. Otherwise,
       *  the slider is hidden from view. Default is true.
       */
        sliders: true,

      /** If true, will prevent users from resizing or moving the image.
       *  Default is false.
       */
        readOnly: false,

      /** Style for individual elements within the component. Note that you can
       *  provide CSS class names instead of individual style definitions.
       */
        style: {

            backgroundColor: "#000",

            uploadArea: {

            },


          /** Style for the toolbar area that appears below the main panel and
           *  contains the slider controls.
           */
            toolbar: {
                padding: "10px 7px 0px"
            },


          /** Style for individual sliders.
           */
            slider: {
                groove: "sliderGrove",
                handle: "sliderHandle"
            }
        }
    };

    var img;
    var maxWidth = 0;
    var maxHeight = 0;
    var minWidth = 0;
    var minHeight = 0;
    var canvas, ctx;
    var mask;
    var slider;


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


        config.thumbnailWidth = parseInt(config.thumbnailWidth);
        if (isNaN(config.thumbnailWidth)) config.thumbnailWidth = defaultConfig.thumbnailWidth;

        config.thumbnailHeight = parseInt(config.thumbnailHeight);
        if (isNaN(config.thumbnailHeight)) config.thumbnailHeight = defaultConfig.thumbnailHeight;


        config.readOnly = (config.readOnly===true);


        var div = createElement("div", parent, {
            display: "inline-block"
        });
        me.el = div;


        var table = createTable(div);
        var td = table.addRow().addColumn({
            height: "100%",
            backgroundColor: config.style.backgroundColor
        });
        createMainPanel(td);

        if (config.sliders!==false) createSliders(table.addRow().addColumn());
    };


  //**************************************************************************
  //** onChange
  //**************************************************************************
  /** Called whenever the a new image is added or adjusted
   */
    this.onChange = function(){};


  //**************************************************************************
  //** setImage
  //**************************************************************************
    this.setImage = function(src){
        if (!src) return;

        img.style.width = "";
        img.style.height = "";
        if (canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
        img.src = src;

        img.parentNode.className = "";
    };


  //**************************************************************************
  //** getImage
  //**************************************************************************
  /** Returns image data, cropped and scaled to match the thumbnail preview.
   *  Image data is returned as a Blob. The data can be rendered in a "img"
   *  element by converting the Blob to a Base64 encoded string. Example:
   *  <pre>img.src = URL.createObjectURL(data);</pre>
   *  @param format Image format (e.g. jpg, png, webp, etc). Supported formats
   *  vary from browser to browser.
   *  @param returnBase64 If true, returns a Base64 encoded string representing
   *  the image. Otherwise, returns a Blob. Default is false.
   */
    this.getImage = function(format, returnBase64){
        if (!format) format = "png";
        if (format==="jpg") format = "jpeg";


        var scaleWidth = maxWidth/parseFloat(img.style.width);
        var scaleHeight = maxHeight/parseFloat(img.style.height);

        var left = -parseFloat(img.style.left)*scaleWidth;
        var top = -parseFloat(img.style.top)*scaleHeight;
        var width = config.thumbnailWidth*scaleWidth;
        var height = config.thumbnailHeight*scaleHeight;

        try{
            var imageData = ctx.getImageData(left, top, width, height);


            var output = createElement('canvas');
            output.width = width;
            output.height = height;
            output.getContext('2d').putImageData(imageData, 0, 0);

            resizeCanvas(output, config.thumbnailWidth, config.thumbnailHeight, true);

            var type = 'image/' + format;
            var data = output.toDataURL(type);
            if (returnBase64===true) return data;
            data = data.substring(("data:" + type + ";base64,").length);
            return base64ToBlob(data, type);
        }
        catch(e){
            return null;
        }
    };


  //**************************************************************************
  //** createMainPanel
  //**************************************************************************
    var createMainPanel = function(parent){

      //Create main div
        var div = createElement("div", parent, config.style.uploadArea);
        div.style.width = config.thumbnailWidth + "px";
        div.style.height = config.thumbnailHeight + "px";
        div.style.position = "relative";
        div.style.overflow = "hidden";

        div.addEventListener('dragover', function(e) {
            e.stopPropagation();
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy'; // Explicitly show this is a copy.
            div.style.opacity = "0.9";
        }, false);
        div.addEventListener('drop', function(e) {
            e.stopPropagation();
            e.preventDefault();
            div.style.opacity = "";

            img.style.width = "";
            img.style.height = "";
            if (canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
            me.onChange();

            var files = e.dataTransfer.files;
            var file = files[0];

            if (file.type.match('image.*')){

                var reader = new FileReader();
                reader.onload = (function(f) {
                    return function(e) {
                        var src = e.target.result;
                        img.src = src;
                        //img.file = f;
                        div.className = "";
                    };
                })(file);

                reader.readAsDataURL(file);
            }

        }, false);


      //Create image
        img = createElement('img', div);
        img.style.position = "absolute";
        img.style.top = 0;
        img.style.left = 0;
        addShowHide(img);
        img.hide();
        img.onload = function(){

            img.style.top = 0;
            img.style.left = 0;


            maxWidth = img.width;
            maxHeight = img.height;
            minWidth = img.width;
            minHeight = img.height;

            resizeImage();


            mask.show();
            if (!config.readOnly){
                mask.style.cursor = "grab";
            }


          //Create canvas as needed
            if (!canvas){
                canvas = createElement('canvas');
                ctx = canvas.getContext('2d');
            }


          //Update canvas size
            canvas.width = maxWidth;
            canvas.height = maxHeight;


          //Copy original image into the canvas
            ctx.drawImage(img, 0, 0);


            img.show();

            if (slider){
                slider.setValue(0, true);
                slider.enable();
            }

            me.onChange();
        };



      //Create mask
        mask = createElement('canvas', div);
        mask.style.position = "absolute";
        mask.style.top = 0;
        mask.style.left = 0;
        mask.width = config.thumbnailWidth;
        mask.height = config.thumbnailHeight;
        addShowHide(mask);
        mask.hide();

        if (config.mask==='circle'){

            var context = mask.getContext('2d');
            context.fillStyle = "rgba(0, 0, 0, 0.3)";
            context.fillRect(0, 0, config.thumbnailWidth, config.thumbnailHeight);

            var cx = config.thumbnailWidth/2;
            var cy = config.thumbnailHeight/2;
            var radius = Math.min(config.thumbnailWidth,config.thumbnailHeight)/2;
            context.beginPath();
            context.fillStyle = "rgba(0, 0, 0, 1)";
            context.globalCompositeOperation = 'destination-out';
            context.arc(cx, cy, radius, 0, Math.PI*2);
            context.fill();
            context.globalCompositeOperation = 'source-over';

        }

        var xInitial = 0;
        var yInitial = 0;
        var xOffset = 0;
        var yOffset = 0;
        initDrag(mask, {
            onDragStart: function(x, y){
                if (config.readOnly) return;
                mask.style.cursor = "grabbing";
                xOffset = parseInt(img.style.left);
                yOffset = parseInt(img.style.top);
                xInitial = x;
                yInitial = y;
            },
            onDrag: function(x, y){
                if (config.readOnly) return;
                img.style.left = xOffset + (x-xInitial) + "px";
                img.style.top = yOffset + (y-yInitial) + "px";
            },
            onDragEnd: function(){
                if (config.readOnly) return;
                mask.style.cursor = "grab";
                me.onChange();
            }
        });

    };


  //**************************************************************************
  //** createSliders
  //**************************************************************************
    var createSliders = function(parent){
        var div = createElement("div", parent);
        setStyle(div, config.style.toolbar);

        slider = new javaxt.dhtml.Slider(div,{
            value: 0,
            disabled: true,
            style: config.style.slider
        });

        var timer;

        slider.onChange = function(){
            if (timer) cancelTimeout(timer);

            var p = slider.getValue(true);

            var width = minWidth + ((maxWidth-minWidth)*p);
            var height = minHeight + ((maxHeight-minHeight)*p);

            width = Math.min(Math.round(width), maxWidth);
            height = Math.min(Math.round(height), maxHeight);

            //console.log(p, Math.round(p*100) + "%", width, minWidth, maxWidth);

            img.style.width = width + "px";
            img.style.height = height + "px";

            setTimeout(function(){
                me.onChange();
            }, 200);
        };
    };


  //**************************************************************************
  //** resizeImage
  //**************************************************************************
  /** Used to resize the image so that the entire image is visible inside the
   *  thumbnail container
   */
    var resizeImage = function(){

        var setWidth = function(){
            var ratio = config.thumbnailWidth/minWidth;
            minWidth = minWidth*ratio;
            minHeight = minHeight*ratio;
        };

        var setHeight = function(){
            var ratio = config.thumbnailHeight/minHeight;
            minWidth = minWidth*ratio;
            minHeight = minHeight*ratio;
        };

        if (config.thumbnailHeight<config.thumbnailWidth){

            setHeight();
            if (minWidth>config.thumbnailWidth) setWidth();
        }
        else{
            setWidth();
            if (minHeight>config.thumbnailHeight) setHeight();
        }


        img.style.width = minWidth + "px";
        img.style.height = minHeight + "px";
    };


  //**************************************************************************
  //** resizeCanvas
  //**************************************************************************
  /** Fast image resize/resample algorithm using Hermite filter. Credit:
   *  https://stackoverflow.com/a/18320662/
   */
    var resizeCanvas = function(canvas, width, height, resize_canvas) {
        var width_source = canvas.width;
        var height_source = canvas.height;
        width = Math.round(width);
        height = Math.round(height);

        var ratio_w = width_source / width;
        var ratio_h = height_source / height;
        var ratio_w_half = Math.ceil(ratio_w / 2);
        var ratio_h_half = Math.ceil(ratio_h / 2);

        var ctx = canvas.getContext("2d");
        var img = ctx.getImageData(0, 0, width_source, height_source);
        var img2 = ctx.createImageData(width, height);
        var data = img.data;
        var data2 = img2.data;

        for (var j = 0; j < height; j++) {
            for (var i = 0; i < width; i++) {
                var x2 = (i + j * width) * 4;
                var weight = 0;
                var weights = 0;
                var weights_alpha = 0;
                var gx_r = 0;
                var gx_g = 0;
                var gx_b = 0;
                var gx_a = 0;
                var center_y = (j + 0.5) * ratio_h;
                var yy_start = Math.floor(j * ratio_h);
                var yy_stop = Math.ceil((j + 1) * ratio_h);
                for (var yy = yy_start; yy < yy_stop; yy++) {
                    var dy = Math.abs(center_y - (yy + 0.5)) / ratio_h_half;
                    var center_x = (i + 0.5) * ratio_w;
                    var w0 = dy * dy; //pre-calc part of w
                    var xx_start = Math.floor(i * ratio_w);
                    var xx_stop = Math.ceil((i + 1) * ratio_w);
                    for (var xx = xx_start; xx < xx_stop; xx++) {
                        var dx = Math.abs(center_x - (xx + 0.5)) / ratio_w_half;
                        var w = Math.sqrt(w0 + dx * dx);
                        if (w >= 1) {
                            //pixel too far
                            continue;
                        }
                        //hermite filter
                        weight = 2 * w * w * w - 3 * w * w + 1;
                        var pos_x = 4 * (xx + yy * width_source);
                        //alpha
                        gx_a += weight * data[pos_x + 3];
                        weights_alpha += weight;
                        //colors
                        if (data[pos_x + 3] < 255)
                            weight = weight * data[pos_x + 3] / 250;
                        gx_r += weight * data[pos_x];
                        gx_g += weight * data[pos_x + 1];
                        gx_b += weight * data[pos_x + 2];
                        weights += weight;
                    }
                }
                data2[x2] = gx_r / weights;
                data2[x2 + 1] = gx_g / weights;
                data2[x2 + 2] = gx_b / weights;
                data2[x2 + 3] = gx_a / weights_alpha;
            }
        }
        //clear and resize canvas
        if (resize_canvas === true) {
            canvas.width = width;
            canvas.height = height;
        } else {
            ctx.clearRect(0, 0, width_source, height_source);
        }

        //draw
        ctx.putImageData(img2, 0, 0);
    };


  //**************************************************************************
  //** base64ToBlob
  //**************************************************************************
    var base64ToBlob = function(base64, mime) {

        mime = mime || '';
        var sliceSize = 1024;
        var byteChars = window.atob(base64);
        var byteArrays = [];

        for (var offset = 0, len = byteChars.length; offset < len; offset += sliceSize) {
            var slice = byteChars.slice(offset, offset + sliceSize);

            var byteNumbers = new Array(slice.length);
            for (var i = 0; i < slice.length; i++) {
                byteNumbers[i] = slice.charCodeAt(i);
            }

            var byteArray = new Uint8Array(byteNumbers);

            byteArrays.push(byteArray);
        }

        return new Blob(byteArrays, {type: mime});
    };


  //**************************************************************************
  //** Utils
  //**************************************************************************
    var merge = javaxt.dhtml.utils.merge;
    var setStyle = javaxt.dhtml.utils.setStyle;
    var createElement = javaxt.dhtml.utils.createElement;
    var createTable = javaxt.dhtml.utils.createTable;
    var addShowHide = javaxt.dhtml.utils.addShowHide;
    var isNumber = javaxt.dhtml.utils.isNumber;
    var initDrag = javaxt.dhtml.utils.initDrag;
    var getRect = javaxt.dhtml.utils.getRect;

    init();
};