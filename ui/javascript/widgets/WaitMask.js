if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  WaitMask
//******************************************************************************
/**
 *   Semi transparent div with a material design spinner
 *
 ******************************************************************************/

javaxt.express.WaitMask = function(parent, config) {

    var me = this;
    var waitmask, spinner;
    var timer, timer2;
    var isVisible = false;
    var defaultConfig = {
        style: {
            mask: "waitmask",
            spinner: {
                size: "50px",
                lineWidth: 3
            }
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
        var clone = {};
        merge(clone, config);


      //Merge clone with default config
        merge(clone, defaultConfig);
        config = clone;


      //Create waitmask
        waitmask = document.createElement('div');
        waitmask.className = config.style.mask;
        waitmask.style.opacity = 0;

        addShowHide(waitmask);
        waitmask.hide();

        parent.appendChild(waitmask);
        me.el = waitmask;
    };


  //**************************************************************************
  //** show
  //**************************************************************************
    this.show = function(delay){
        if (isVisible) return;
        delay = parseInt(delay);
        if (timer) clearTimeout(timer);
        if (!isNaN(delay) && delay>0){
            timer = setTimeout(show, delay);
        }
        else{
            show();
        }
    };


    var show = function(){
        waitmask.style.zIndex = getNextHighestZindex();
        waitmask.show();
        waitmask.style.opacity = "";
        waitmask.innerHTML = "";
        spinner = new javaxt.express.Spinner(waitmask, config.style.spinner);
        spinner.show();
        isVisible = true;
    };


  //**************************************************************************
  //** hide
  //**************************************************************************
    this.hide = function(){
        if (timer) clearTimeout(timer);
        waitmask.style.zIndex = "";
        waitmask.style.opacity = 0;
        timer2 = setTimeout(function(){
            waitmask.hide();
            waitmask.innerHTML = "";
            isVisible = false;
        },1500);
    };


  //**************************************************************************
  //** Utils
  //**************************************************************************
    var merge = javaxt.dhtml.utils.merge;
    var addShowHide = javaxt.dhtml.utils.addShowHide;
    var getNextHighestZindex = javaxt.dhtml.utils.getNextHighestZindex;

    init();
};