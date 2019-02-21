if(!javaxt) var javaxt={};
if(!javaxt.dhtml) javaxt.dhtml={};

//******************************************************************************
//**  WebSite
//******************************************************************************
/**
 *   Used to wrap the content in a carousel control. 
 *
 ******************************************************************************/

javaxt.dhtml.WebSite = function (content, config) {
    this.className = "javaxt.dhtml.WebSite";
    
    var me = this;
    var tabs;
    var carousel;
    var slideIndex;
    
    var defaultConfig = {
        
        padding: 20, //Padding between panels in the carousel. This should 
                     //not exceed the content padding defined in the CSS.
                     
        background: "#ffffff", //Background for individual panels in the 
                               //carousel. This should match the content 
                               //background defined in the CSS.
                               
        animationSteps: 600
    };


    var fx = new javaxt.dhtml.Effects();
    var pageLoader = new javaxt.dhtml.PageLoader();
    var body = document.getElementsByTagName('body')[0];
    var debug = false;
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
    var init = function(){


        
      //Clone the config so we don't modify the original config object
        var clone = {};
        merge(clone, config);


      //Merge clone with default config
        merge(clone, defaultConfig);
        config = clone;
        
        


      //Parse tabs
        tabs = config.tabs;
        var arr = [];
        for (var i=0; i<tabs.length; i++){
            var path = getPath(tabs[i].pathname);
            var dir = getDir(path);
            var div = tabs[i].getElementsByTagName("div")[0];
            arr.push({
                dir: dir,
                div: div
            });
        }
        tabs = arr;
        
        
      //Update hyperlinks
        var links = document.getElementsByTagName("a");
        parseLinks(links);
        

      //Initialize SyntaxHighlighter and highlight code. Do this  
      //before computing the content height.
        if (typeof SyntaxHighlighter !== 'undefined'){
            SyntaxHighlighter.defaults['class-name'] = 'code-block';
            SyntaxHighlighter.defaults['toolbar'] = false;
            SyntaxHighlighter.defaults['gutter'] = false;
            highlight(content);
        }


        
      //Explicitly set the content height. Do this before removing 
      //elements or inserting the carousel control.
        content.style.height = content.offsetHeight + "px";
        
        
      //Create panel for the carousel using elements elements from the content node
        var currPage = document.createElement("div");
        currPage.style.height = "100%";
        while (content.childNodes.length>0){
            var node = content.childNodes[0];
            content.removeChild(node);
            currPage.appendChild(node);
        }


      //Create a second empty panel for the carousel
        var nextPage = document.createElement("div");
        nextPage.style.height = "100%";



      //Insert carousel
        carousel = new javaxt.dhtml.Carousel(content, {
            items: [currPage, nextPage],
            drag: false,
            loop: true,
            padding: config.padding,
            animationSteps: config.animationSteps,
            transitionEffect: "easeInOutCubic",
            fx: fx
        });



      //Watch for changes to the carousel
        {
            carousel.beforeChange = function(currPanel, nextPanel){

              //Update content of the nextPanel. This should be done in the load
              //function but there are issues getting the correct DOM element.
              //I think it has something to do with the cloning process. In any 
              //event, this event listener provides a workaround.
                updateContent(nextPanel);
                
                
              //Update tabs
                var currDir = getDir(getPath(window.location.pathname));
                var currTab = me.getTab(currDir);
                
                for (var i=0; i<tabs.length; i++){
                    tabs[i].div.className = "";
                }
                currTab.div.className = "active";

                dispatchEvent('beforeunload');
            };


          
            carousel.onChange = function(currPanel, prevPanel){
                
                
              //Update content height and scroll after the carousel changes
                var resizeSteps = 0;
                if (config.animationSteps>0) resizeSteps = 100;
                if (body.scrollTop>50){
                    setTimeout(function(){
                        var animationSteps = 1000;
                        scrollTop(new Date().getTime(), animationSteps, animationSteps, function(){
                            resize(currPanel, resizeSteps);
                        });
                    }, 250);
                }
                else{
                    body.scrollTop = 0;
                    resize(currPanel, resizeSteps);
                }
                

                
                
              //Remove content from previous panel. Content will be loaded 
              //dynamically on 'popstate' events
                prevPanel.innerHTML = "";


              //Trigger window.onload event
                dispatchEvent('load');

            };
        }



      //Resize height of the content panel whenever the window is resized
        window.addEventListener('resize', function(e) {
            var panels = carousel.getPanels();
            for (var i=0; i<panels.length; i++){
                if (panels[i].isVisible){
                    resize(panels[i].div);
                    return;
                }
            }
        });


      //Call the resize listener to update the height of the content div. 
      //This is important for IE and Chrome.
        dispatchEvent('resize');


      //Watch for forward and back events via a 'popstate' listener
        window.addEventListener('popstate', popstateListener);

        


      //Set initial history. This is critical for the popstate listener
        log(window.history.state);
        if (window.history.state==null){
            slideIndex = 0;
            var panelInfo = {
                index: slideIndex, 
                title: document.title
            };
            history.replaceState(panelInfo, null, '');
        }
        else{
            slideIndex = window.history.state.index;
        }
        
        log("slideIndex: " + slideIndex);
    };


  //**************************************************************************
  //** popstateListener
  //**************************************************************************
  /** Used to processes forward and back events from the browser
   */
    var popstateListener = function(e) {

        var panel = e.state;
        log(panel);
        document.title = panel.title;


      //Find panel
        var nextPage;
        var panels = carousel.getPanels();
        for (var i=0; i<panels.length; i++){
            if (!panels[i].isVisible){
                nextPage = panels[i].div;
                break;
            }
        }




        var url = window.location;
        getHTML(url, function(html, title, inlineScripts){
            nextPage.innerHTML = html;
            loadInlineScripts(inlineScripts);

            if (slideIndex>=panel.index){
                carousel.back();
                slideIndex = panel.index;
            }
            else if (slideIndex<panel.index){
                carousel.next();
                slideIndex = panel.index;
            }
            else{
                log(slideIndex + " vs " + panel.index);
            }


        });

    };
    
    
  //**************************************************************************
  //** enablePopstateListener
  //**************************************************************************
    this.enablePopstateListener = function(){
        me.disablePopstateListener(); //Just in case...
        window.addEventListener('popstate', popstateListener);
    };


  //**************************************************************************
  //** disablePopstateListener
  //**************************************************************************
    this.disablePopstateListener = function(){
        window.removeEventListener('popstate', popstateListener);
    };
    
    
  //**************************************************************************
  //** resize
  //**************************************************************************
  /** Used to update the content height whenever the carousel changes.
   */
    var resize = function(panel, animationSteps){
        var r1 = _getRect(panel.parentNode);
        var r2 = _getRect(panel);
        var h = r2.height + (r2.top-r1.top);
        if (h<100) return; //Ignore invalid heights (e.g. JavaXT javadocs)
        

        if (animationSteps){
            _resize(parseInt(content.style.height), h, new Date().getTime(), animationSteps, animationSteps);
        }
        else{
            content.style.height = h + "px";
        }
    };

    var _resize = function(start, end, lastTick, timeLeft, animationSteps, callback){

        var curTick = new Date().getTime();
        var elapsedTicks = curTick - lastTick;


      //If the animation is complete, ensure that the content is target height
        if (timeLeft <= elapsedTicks){
            content.style.height = end+"px";
            if (callback) callback.apply(me, []);
            return;
        }



        timeLeft -= elapsedTicks;
        
        
        var d = start-end;
        var percentComplete = 1-(timeLeft/animationSteps);
        var offset = Math.round(percentComplete * d);
        content.style.height = start-offset + "px";

        setTimeout(function(){
            _resize(start, end, curTick, timeLeft, animationSteps, callback);
        }, 33);
    };
    
    
  //**************************************************************************
  //** scrollTop
  //**************************************************************************
  /** Animated scroll to the top of the page. 
   */
    var scrollTop = function(lastTick, timeLeft, animationSteps, callback){

        var curTick = new Date().getTime();
        var elapsedTicks = curTick - lastTick;


      //If the animation is complete, ensure that the content is target height
        if (timeLeft <= elapsedTicks){
            body.scrollTop = 0;
            if (callback) callback.apply(me, []);
            return;
        }



        timeLeft -= elapsedTicks;
        
        var start = body.scrollTop;
        var d = start;
        var percentComplete = 1-(timeLeft/animationSteps);
        var offset = Math.round(percentComplete * d);
        body.scrollTop = start-offset;

        setTimeout(function(){
            scrollTop(curTick, timeLeft, animationSteps, callback);
        }, 33);
    };
    
    
  //**************************************************************************
  //** setContentHeight
  //**************************************************************************
    this.setContentHeight = function(h){
        content.style.height = h;
    };
    
    
  //**************************************************************************
  //** highlight
  //**************************************************************************
  /** Used to update SyntaxHighlighter
   */
    var highlight = function(el){
        if (typeof SyntaxHighlighter !== 'undefined'){
            var highlight = false;
            var nodes = el.getElementsByTagName("pre");
            for (var i=0; i<nodes.length; i++){
                var pre = nodes[i];
                if (pre.className==""){ pre.className = "brush: java;"; }
                highlight = true;
            }
            if (highlight) SyntaxHighlighter.highlight();
        }
    };

    
    
  //**************************************************************************
  //** parseLinks
  //**************************************************************************
    var parseLinks = function(links){

        for (var i=0; i<links.length; i++){
            
          //Add event listener if the link refers to a resource on this site
            if (links[i].hostname==window.location.hostname){
                var onclick = function(e){

                    
                  //Ignore the link if the url matches the current url 
                    var url = this.href;
                    if (window.location==url){
                        e.preventDefault();
                        return;
                    }


                  //Ignore the link if it contains a hash/anchor
                    var tmp = url.substring(url.indexOf(this.pathname) + this.pathname.length);
                    if (tmp.indexOf("#")==0){
                        e.preventDefault();
                        return;
                    }
                    


                  //Parse path
                    var path = getPath(this.pathname);
                    var dir = getDir(path);
                    
                    

                  //Update the link
                    if (me.updateLink(url, path, dir)){

                      //Prevent 
                        e.preventDefault();

                      //Load    
                        getHTML(url, function(html, title, inlineScripts){
                            load(html, title, url, dir, inlineScripts);
                        });

                    }
                };
                
                //links[i].ontouchstart = onclick;
                links[i].onclick = onclick;
            }
        }
    };

    
    
  //**************************************************************************
  //** load
  //**************************************************************************
    var load = function(html, title, url, dir, inlineScripts){


      //Get current path and tab variables. Do this before updating the URL.
        var tab = me.getTab(dir);
        var currPath = getPath(window.location.pathname);
        var currDir = getDir(currPath);
        var currTab = me.getTab(currDir);


      //Update title
        document.title = title;



      //Update URL
        history.pushState(null, title, url);
        


        
      //Find panels in the carousel
        var currPage, nextPage;
        var panels = carousel.getPanels();
        for (var i=0; i<panels.length; i++){
            var panel = panels[i];
            var el = panel.div;
            if (panel.isVisible){
                currPage = el;
            }
            else{
                nextPage = el;
            }
        }

        

      //
        var slide = tab.dir!=currTab.dir;
        slide = true;




      //Update content
        if (!slide){
            currPage.innerHTML = html;
            updateContent(currPage);
        }
        else{ 
            nextPage.innerHTML = html;
            //updateContent(currPage); <-- See carousel.onChange event listener


            
          //Determine which direction to slide the carousel
            var a, b;
            for (var i=0; i<tabs.length; i++){
                var path = tabs[i].dir;
                if (path==currTab.dir) a = i;
                if (path==tab.dir) b = i;
            }
            
            

            log(a + " " + b);
            
            if (a==b){
                var path = getPath(window.location.pathname);
                if (path.indexOf(currPath)==0){
                    b=1;
                    a=0;
                }
                else{
                    log(currPath + " vs " + path);
                }
            }
            
            
          //Slide carousel to view content
            if (b>a){
                slideIndex++;
                carousel.next();
            }
            else{
                slideIndex--;
                carousel.back();
            }
            
        }
        
        
      //Execute inline scripts
        loadInlineScripts(inlineScripts);



      //Update browser history
        var panelInfo = {
            index: slideIndex,
            title: title
        };
        history.replaceState(panelInfo, title, url);
    };
    

  //**************************************************************************
  //** loadInlineScripts
  //**************************************************************************
    var loadInlineScripts = function(inlineScripts){

      //Execute inline scripts
        for (var i=0; i<inlineScripts.length; i++){
            var script = inlineScripts[i].firstChild.nodeValue;
            eval(script);
        }
    };

    
    
  //**************************************************************************
  //** dispatchEvent
  //**************************************************************************
  /** Fires a given window event (e.g. "load")
   */
    var dispatchEvent = function(name){
        var evt;
        try{
            evt = new Event(name);
        }
        catch(e){ //e.g. IE
            evt = document.createEvent('Event');  
            evt.initEvent(name, false, false);  
        }
        
        if (config.animationSteps>0){
            window.dispatchEvent(evt);
        }
        else{ 
            
          //Found a bug where the documentation tab wouldn't load correctly
          //if the animation was disabled. I wasn't able to track down the 
          //root cause but adding a slight delay seems to do the trick
            setTimeout(function(){
                window.dispatchEvent(evt);
            },50);
        }
    };
    

  //**************************************************************************
  //** getTabs
  //**************************************************************************
  /** Public method allows users to access the tabs variable. Used in 
   *  conjuntion with the getTab() method.
   */
    me.getTabs = function(){
        return tabs;
    };


  //**************************************************************************
  //** getTab
  //**************************************************************************
  /** Returns a tab for a given directory. 
   */
    me.getTab = function(dir){
        for (var i=0; i<tabs.length; i++){
            if (tabs[i].dir==dir) return tabs[i];
        }
        return null;
    };
    
    
  //**************************************************************************
  //** updateLink
  //**************************************************************************
  /** This method is called while parsing links in an HTML document. Returns
   *  true if the given URL should be loaded dynamically as content (default).
   *  Otherwise the link will be left as is. 
   */
    this.updateLink = function(url, path, dir){
        
        if (dir=="downloads" || dir=="images"){

            var idx = path.lastIndexOf(".");
            if ((path.length-idx)<5){
                return false;
            }
        }
        
        return true;
    };
    


  //**************************************************************************
  //** updateContent
  //**************************************************************************
    var updateContent = function(el){
      //Update pre/code blocks
        highlight(el);

      //Update links
        var links = el.getElementsByTagName("a");
        parseLinks(links);          
    };
    
    
  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Updates a given path by removing the leading "/" character.
   */
    var getPath = function(path){
        if (path==null) path = "";
        else if (path.indexOf("/")==0){
            if (path.length>1) path = path.substring(1);
            else path = "";
        }
        return path;
    };
    
    
  //**************************************************************************
  //** getDir
  //**************************************************************************
  /** Assumes the leading "/" character has been removed from the path 
   */
    var getDir = function(path){
        var dir = path.toLowerCase();
        var idx = dir.indexOf("/");
        if (idx>0) dir = dir.substring(0, idx);
        return dir;
    };


  //**************************************************************************
  //** getHTML
  //**************************************************************************
    var getHTML = function(url, callback){
        
      //Append "template=false" to the query string
        url += ""; //In case the url is a Location object
        if (url.indexOf("?")==-1) url += "?";
        else url += "&";
        url += "template=false";
        
        
      //Load page and call callback
        pageLoader.load(url, function(html, title, inlineScripts){
            
          //Create div
            var div = document.createElement("div");
            //div.style.height = "100%";
            div.style.background = config.background;
            div.innerHTML = html;
            html = div.outerHTML;

          //Get title as needed
            if (title==null || title.length==0){
                var h1 = div.getElementsByTagName("h1");
                if (h1.length>0) title = h1[0].innerHTML;
            }
            
          //Delete div
            div = null;
            
          //Callback
            if (callback) callback.apply(me, [html, title, inlineScripts]);

        });
    };


  //**************************************************************************
  //** log
  //**************************************************************************
    var log = function(str){
        if (debug) console.log(str);
    };
    
    
  //**************************************************************************
  //** getRect
  //**************************************************************************
  /** Returns the geometry of a given element.
   */
    var _getRect = function(el){

        if (el.getBoundingClientRect){
            return el.getBoundingClientRect();
        }
        else{
            var x = 0;
            var y = 0;
            var w = el.offsetWidth;
            var h = el.offsetHeight;

            function isNumber(n){
               return n === parseFloat(n);
            }

            var org = el;

            do{
                x += el.offsetLeft - el.scrollLeft;
                y += el.offsetTop - el.scrollTop;
            } while ( el = el.offsetParent );


            el = org;
            do{
                if (isNumber(el.scrollLeft)) x -= el.scrollLeft;
                if (isNumber(el.scrollTop)) y -= el.scrollTop;
            } while ( el = el.parentNode );


            return{
                left: x,
                right: x+w,
                top: y,
                bottom: y+h,
                width: w,
                height: h
            };
        }
    };
    
    
  //**************************************************************************
  //** merge
  //**************************************************************************
  /** Used to merge properties from one json object into another. Credit:
   *  https://github.com/stevenleadbeater/JSONT/blob/master/JSONT.js
   */
    var merge = function(settings, defaults) {
        for (var p in defaults) {
            if ( defaults.hasOwnProperty(p) && typeof settings[p] !== "undefined" ) {
                if (p!=0) //<--Added this as a bug fix
                merge(settings[p], defaults[p]);
            }
            else {
                settings[p] = defaults[p];
            }
        }
    };
    
    
    init();
};