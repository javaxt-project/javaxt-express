if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  Spinner
//******************************************************************************
/**
 *   Material design spinner. Credit:
 *   https://codepen.io/mrrocks/pen/EiplA
 *
 *   Note that this class requires an external css file.
 *
 ******************************************************************************/

javaxt.express.Spinner = function(parent, config) {

    var me = this;

    var defaultConfig = {
        style: "spinner",
        size: "25px",
        lineWidth: 6
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
        var clone = {};
        merge(clone, config);


      //Merge clone with default config
        merge(clone, defaultConfig);
        config = clone;


        config.size = parseInt(config.size)+"px";
        config.lineWidth = parseInt(config.lineWidth)+"";


        var div = document.createElement("div");
        div.className = config.style;
        div.style.width = config.size;
        div.style.height = config.size;
        div.style.display = "inline-block";



        var NS = "http://www.w3.org/2000/svg";
        var svg = document.createElementNS(NS, "svg");
        svg.setAttribute("width", config.size);
        svg.setAttribute("height", config.size);
        svg.setAttribute("viewBox", "0 0 66 66");
        div.appendChild(svg);

        var circle = document.createElementNS(NS, "circle");
        circle.setAttribute("fill", "none");
        circle.setAttribute("stroke-width", config.lineWidth);
        circle.setAttribute("stroke-linecap", "round");
        circle.setAttribute("cx", "33");
        circle.setAttribute("cy", "33");
        circle.setAttribute("r", "30");
        svg.appendChild(circle);



        parent.appendChild(div);
        me.el = div;
    };


  //**************************************************************************
  //** show
  //**************************************************************************
    this.show = function(){
        setTimeout(function(){
            me.el.style.display = '';
            me.el.style.opacity = 1;
            me.el.childNodes[0].style.opacity = 1;
        }, 50);
    };


  //**************************************************************************
  //** hide
  //**************************************************************************
    this.hide = function(){
        me.el.style.opacity = 0;
        me.el.childNodes[0].style.opacity = 0;
        setTimeout(function(){
            me.el.style.display = 'none';
        }, 500);
    };


    var merge = javaxt.dhtml.utils.merge;

    init();
};