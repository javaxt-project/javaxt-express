if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  WebSite
//******************************************************************************
/**
 *   Used to wrap the content in a carousel control.
 *
 ******************************************************************************/

javaxt.express.WebSite = function (content, config) {
    this.className = "javaxt.express.WebSite";

    var me = this;
    var tabs;
    var carousel;
    var currPage, nextPage;
    var slideIndex;

    var defaultConfig = {

        tabs: [],
        navbar: null,
        sidebar: null,


      /** Minimul height of a panel
       */
        minContentHeight: 600,


      /** Padding between panels in the carousel. This should not exceed the
       *  content padding defined in the CSS.
       */
        padding: 20,


      /** Time to transition between panels, in milliseconds. Only applicable
       *  when "animate" is set to true.
       */
        animationSteps: 600,


      /** An instance of a javaxt.dhtml.Effects class used to animate
       *  transitions. Only used when "animate" is set to true.
       */
        fx: null,

        style: {
            panel : { //Style for individual panels in the carousel
                background: "#fff",
                height: ""
            }
        }
    };


    var pageLoader = new javaxt.dhtml.PageLoader();
    var scrollUp = false;
    var timer;
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


        if (!config.fx) config.fx = new javaxt.dhtml.Effects();


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


      //Update nav components
        var path = getPath(window.location.pathname);
        me.updateTabs(path);
        me.updateNav(path);
        me.updateSidebar(path);


      //Update hyperlinks
        var links = document.getElementsByTagName("a");
        me.updateLinks(links);





      //Highlight code blocks. Do this before computing the content height.
        if (typeof CodeMirror !== 'undefined'){
            highlight(content);
        }
        else if (typeof SyntaxHighlighter !== 'undefined'){
            SyntaxHighlighter.defaults['class-name'] = 'code-block';
            SyntaxHighlighter.defaults['toolbar'] = false;
            SyntaxHighlighter.defaults['gutter'] = false;
            highlight(content);
        }



      //Explicitly set the content height. Do this before removing
      //elements or inserting the carousel control.
        var contentHeight = content.offsetHeight;
        var minContentHeight = parseFloat(config.minContentHeight);
        if (!isNaN(minContentHeight)){
            contentHeight = Math.max(contentHeight, minContentHeight);
        }
        content.style.height = contentHeight + "px";



      //Create panel for the carousel using elements elements from the content node
        currPage = document.createElement("div");
        currPage.style.height = "100%";
        var contentWrapper = document.createElement("div");
        setStyle(contentWrapper, config.style.panel);
        currPage.appendChild(contentWrapper);

        while (content.childNodes.length>0){
            var node = content.childNodes[0];
            content.removeChild(node);
            contentWrapper.appendChild(node);
        }


      //Create a second empty panel for the carousel
        nextPage = document.createElement("div");
        nextPage.style.height = "100%";



      //Insert carousel
        carousel = new javaxt.dhtml.Carousel(content, {
            items: [currPage, nextPage],
            drag: false,
            loop: true,
            padding: config.padding,
            animationSteps: config.animationSteps,
            transitionEffect: "easeInOutCubic",
            fx: config.fx,
            onRender: function(){
                //console.log("ready!");
            }
        });





      //Watch for changes to the carousel
        {
            carousel.beforeChange = function(currPanel, nextPanel){


              //Hide content
                currPanel.style.opacity = 0;
                nextPanel.style.opacity = 0;


                if (timer) clearInterval(timer);


              //Update content of the nextPanel. This should be done in the load
              //function but there are issues getting the correct DOM element.
              //I think it has something to do with the cloning process. In any
              //event, this event listener provides a workaround.
                updateContent(nextPanel);


              //Update nav components
                var path = getPath(window.location.pathname);
                me.updateTabs(path);
                me.updateNav(path);
                me.updateSidebar(path);



                //console.log(getScrollPosition());



                dispatchEvent('beforeunload');
            };



            carousel.onChange = function(currPanel, prevPanel){

              //Update class variables
                currPage = currPanel;
                nextPage = prevPanel;


              //Update opacity
                if (config.fx){
                    config.fx.fadeIn(currPanel, 'easeInCubic', 300);
                }
                else{
                    currPanel.style.opacity = 1;
                }


                var resizeSteps = 0;
                if (config.animationSteps>0) resizeSteps = 100;



              //Scroll up after the carousel changes as needed
                if (scrollUp){
                    scrollUp = false;
                    if (getScrollPosition()>50 && resizeSteps>0){
                        var animationSteps = 1000;
                        scrollTop(new Date().getTime(), animationSteps, animationSteps, function(){
                            resize(currPanel, resizeSteps);
                        });
                    }
                    else{
                        setScrollPosition(0);

                    }
                }



              //Create timer to update content height
                timer = setInterval(function(){
                    resize(currPanel, resizeSteps);
                }, 250);



              //Remove content from previous panel. Content will be loaded
              //dynamically on 'popstate' events
                prevPanel.innerHTML = "";


              //Trigger window.onload event
                dispatchEvent('load');

            };



            carousel.onResize = function(){
                dispatchEvent('resize');
            };
        }



      //Resize height of the content panel whenever the window is resized
        window.addEventListener('resize', function(e) {
            resize(currPage);
//            var panels = carousel.getPanels();
//            for (var i=0; i<panels.length; i++){
//                if (panels[i].isVisible){
//                    resize(panels[i].div);
//                    return;
//                }
//            }
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


//      //Find panel
//        var nextPage;
//        var panels = carousel.getPanels();
//        for (var i=0; i<panels.length; i++){
//            if (!panels[i].isVisible){
//                nextPage = panels[i].div;
//                break;
//            }
//        }


        if (!nextPage){
            console.error("nextPage is null in popstateListener");
            return;
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
  //** beforeResize
  //**************************************************************************
    this.beforeResize = function(){};


  //**************************************************************************
  //** onResize
  //**************************************************************************
    this.onResize = function(){};


  //**************************************************************************
  //** resize
  //**************************************************************************
  /** Used to update the content height whenever the carousel changes.
   */
    var resize = function(panel, animationSteps){

        var r1 = _getRect(panel.parentNode);
        var firstChild = getFirstChild(panel);
        if (firstChild) panel = firstChild;
        var r2 = _getRect(panel);
        var h = r2.height + (r2.top-r1.top);

        var contentHeight = parseFloat(content.style.height);
        var minContentHeight = parseFloat(config.minContentHeight);
        if (!isNaN(minContentHeight)){
            h = Math.max(h, minContentHeight);
        }



        if (h===contentHeight){
            return;
        }
        else{
            me.beforeResize();
            if (!animationSteps) animationSteps = 0;
            if (animationSteps>0){
                _resize(contentHeight, h, new Date().getTime(), animationSteps, me.onResize);
            }
            else{
                content.style.height = h + "px";
                me.onResize();
            }
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
            setScrollPosition(0);
            if (callback) callback.apply(me, []);
            return;
        }



        timeLeft -= elapsedTicks;

        var start = getScrollPosition();
        var d = start;
        var percentComplete = 1-(timeLeft/animationSteps);
        var offset = Math.round(percentComplete * d);
        setScrollPosition(start-offset);

        setTimeout(function(){
            scrollTop(curTick, timeLeft, animationSteps, callback);
        }, 33);
    };


  //**************************************************************************
  //** getScrollPosition
  //**************************************************************************
    var getScrollPosition = function(){
        return window.scrollY; //vs return body.scrollTop;
    };


  //**************************************************************************
  //** setScrollPosition
  //**************************************************************************
  /** body.scrollTop has been deprecated */
    var setScrollPosition = function(y){
        window.scrollTo(0, y); //vs body.scrollTop = y;
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

      //Get <pre> nodes that are not inside a <pre> node
        var nodes = [];
        var pres = el.getElementsByTagName("pre");
        for (var i=0; i<pres.length; i++){
            var pre = pres[i];

            var addNode = true;
            var parentNode = pre.parentNode;
            while (parentNode!=null){
                if (parentNode.nodeName.toLowerCase() === "pre"){
                    addNode = false;
                    break;
                }
                parentNode = parentNode.parentNode;
            }

            if (addNode) nodes.push(pre);
        }


        if (typeof CodeMirror !== 'undefined'){

            for (var i=0; i<nodes.length; i++){
                var pre = nodes[i];
                var src = pre.innerText.replace(/\s+$/, '');
                var lang = pre.getAttribute("language");
                if (!lang && pre.className) {
                    var idx = pre.className.indexOf("brush:");
                    if (idx>-1){
                        lang = pre.className.substring(idx+"brush:".length);
                        idx = lang.indexOf(";");
                        if (idx>-1){
                            lang = lang.substring(0, idx);
                        }
                        lang = lang.replace(/^\s*/, "").replace(/\s*$/, "");
                    }
                }
                if (!lang) lang = "java"; //"javascript"
                if (lang=="js") lang = "javascript";
                if (lang=="java") lang = "text/x-java";
                var lineNumbers = pre.getAttribute("lineNumbers");
                if (lineNumbers==="true") lineNumbers = true;
                else lineNumbers = false;
                var numLines = src.split("\n").length;

                var initCodeMirror = function(pre, src, lang, lineNumbers, callback){

                    var cm = CodeMirror(pre, {
                        mode: lang,
                        lineNumbers: lineNumbers,
                        readOnly: true,
                        value: src
                    });
                    cm.setSize("100%", "100%");

                    if (callback) callback.apply(this, [pre, cm]);
                };

                if (numLines<250){
                    pre.innerHTML = "";
                    initCodeMirror(pre, src, lang, lineNumbers);
                }
                else{

                    var h = parseFloat(pre.offsetHeight);
                    var lineHeight = h/numLines;
                    h += lineHeight;

                    pre.style.opacity = 0;
                    pre.innerHTML = "";
                    pre.style.height = h +"px";

                    setTimeout(initCodeMirror, 200, pre, src, lang, lineNumbers, function(pre){
                        config.fx.fadeIn(pre, 'easeInCubic', 300);
                    });
                }
            }
        }
        else if (typeof SyntaxHighlighter !== 'undefined'){
            var highlight = false;
            for (var i=0; i<nodes.length; i++){
                var pre = nodes[i];
                if (pre.className==""){ pre.className = "brush: java;"; }
                highlight = true;
            }
            if (highlight) SyntaxHighlighter.highlight();
        }
    };



  //**************************************************************************
  //** updateLinks
  //**************************************************************************
    this.updateLinks = function(links){

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
        if (!tab) return;
        var currPath = getPath(window.location.pathname);
        var currDir = getDir(currPath);
        var currTab = me.getTab(currDir);



//      //Find panels in the carousel
//        var currPage, nextPage;
//        var panels = carousel.getPanels();
//        for (var i=0; i<panels.length; i++){
//            var panel = panels[i];
//            var el = panel.div;
//            if (panel.isVisible){
//                currPage = el;
//            }
//            else{
//                nextPage = el;
//            }
//        }



        if (!nextPage){
            console.error("nextPage is null in load");
            return;
        }


        if (!currPage){
            console.error("currPage is null in load");
            return;
        }



      //Update title
        document.title = title;


      //Update URL
        history.pushState(null, title, url);



      //Update scroll variable
        scrollUp = true;


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
  //** updateTabs
  //**************************************************************************
    this.updateTabs = function(path){
        if (!path) path = getPath(window.location.pathname);

        var currDir = getDir(path);
        var currTab = me.getTab(currDir);

        for (var i=0; i<tabs.length; i++){
            tabs[i].div.className = "";
        }
        if (currTab) currTab.div.className = "active";
    };


  //**************************************************************************
  //** updateNav
  //**************************************************************************
  /** Used to update the navbar (aka breadcrumbs)
   */
    this.updateNav = function(path){
        if (!config.navbar) return;
        config.navbar.innerHTML = "";

        if (!path) path = getPath(window.location.pathname);
        if (path.lastIndexOf("/")===path.length-1) path = path.substring(0, path.length-1);
        var arr = path.split("/");
        if (arr.length===0 || arr[0]==='') config.navbar.style.height = "0px";
        else config.navbar.style.height = "";

        for (var i=0; i<arr.length; i++){
            var div = document.createElement("div");
            div.innerText = arr[i].split("_").join(" ");
            if (i<arr.length-1){
                div.path = arr.slice(0, i+1).join("/");
                div.onclick = function(e){

                    var path = this.path;
                    var url = window.location.hostname + path;
                    var dir = getDir(path);

                    var url = window.location.origin;
                    if (!url){
                        url = window.location.protocol;
                        if (url.indexOf(":")===-1) url += ":";
                        var host = window.location.host;
                        url += "//" + host;
                        var port = parseInt(window.location.port);
                        if (!isNaN(port)){
                            if (host.indexOf(":")===-1) url += ":" +port;
                        }
                    }
                    url += "/" + path;


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
            }
            config.navbar.appendChild(div);
        }
    };


  //**************************************************************************
  //** updateSidebar
  //**************************************************************************
    this.updateSidebar = function(path){};


  //**************************************************************************
  //** updateContent
  //**************************************************************************
    var updateContent = function(el){
      //Update pre/code blocks
        highlight(el);

      //Update links
        var links = el.getElementsByTagName("a");
        me.updateLinks(links);
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
            var contentWrapper = document.createElement("div");
            setStyle(contentWrapper, config.style.panel);
            contentWrapper.innerHTML = html;
            html = contentWrapper.outerHTML;

          //Get title as needed
            if (title==null || title.length==0){
                var h1 = contentWrapper.getElementsByTagName("h1");
                if (h1.length>0) title = h1[0].innerHTML;
            }

          //Delete div
            contentWrapper = null;

          //Callback
            if (callback) callback.apply(me, [html, title, inlineScripts]);

        });
    };


    var getFirstChild = function(panel){
        for (var i=0; i<panel.childNodes.length; i++){
            var node = panel.childNodes[i];
            if (node.nodeType===1){
                return node;
            }
        }
        return null;
    };


  //**************************************************************************
  //** log
  //**************************************************************************
    var log = function(str){
        if (debug) console.log(str);
    };


  //**************************************************************************
  //** Utils
  //**************************************************************************
    var _getRect = javaxt.dhtml.utils.getRect;
    var merge = javaxt.dhtml.utils.merge;
    var setStyle = javaxt.dhtml.utils.setStyle;

    init();
};