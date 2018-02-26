if(!javaxt) var javaxt={};
if(!javaxt.dhtml) javaxt.dhtml={};

//******************************************************************************
//**  WebSite
//******************************************************************************
/**
 *   Used to wrap the content in a carousel control. 
 *
 ******************************************************************************/

javaxt.dhtml.WebSite = function (content, tabs, navbar) {
    this.className = "javaxt.dhtml.WebSite";
    
    var me = this;
    var carousel;
    var slideIndex;
    
    var panelPadding=20; //Padding between panels in the carousel. This should 
                         //not exceed the content padding defined in the CSS.
                          
    var panelBackground="#ffffff"; //Background for individual panels in the 
                                   //carousel. This should match the content 
                                   //background defined in the CSS.


    var includes;
    
    var head = document.getElementsByTagName('head')[0];
    var body = document.getElementsByTagName('body')[0];
    var debug = false;
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
    var init = function(){


      //Parse tabs
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


        
      //Explicitely set the content height. Do this before removing 
      //elements or inserting the carousel control.
        content.style.height = content.offsetHeight + "px";
        
        
      //Remove elements from the content div
        var currPage = document.createElement("div");
        while (content.childNodes.length>0){
            var node = content.childNodes[0];
            content.removeChild(node);
            currPage.appendChild(node);
        }
        content.appendChild(currPage);

        

      //Insert carousel
        var nextPage = document.createElement("div");
        carousel = new javaxt.dhtml.Carousel(content, {
            items: [currPage, nextPage],
            animationSteps: 500,
            drag: false,
            loop: true,
            padding: panelPadding
        });
        
        
      //Delete contents of the hidden panel. This is critical for some 
      //assumptions made in the 'popstate' event listener.
        nextPage.parentNode.innerHTML = "";
        
        

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
                if (body.scrollTop>50){
                    setTimeout(function(){
                        var animationSteps = 1000;
                        scrollTop(new Date().getTime(), animationSteps, animationSteps, function(){
                            resize(currPanel, 100);
                        });
                    }, 250);
                }
                else{
                    body.scrollTop = 0;
                    resize(currPanel, 100);
                }
                

                
                
              //Remove conent from previous panel. Content will be loaded 
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
        var r1 = _getRect(panel);
        var r2 = _getRect(panel.getElementsByTagName("div")[0]);
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
        window.dispatchEvent(evt);
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
  //** removeNodes
  //**************************************************************************
    var removeNodes = function(nodes){
        while (nodes.length>0){
            var node = nodes[0];
            var parent = node.parentNode;
            parent.removeChild(node);
        }
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
  /** Assumes the leading "/" character has been removed from the path */
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
        
        
      //Download html fragment and parse response
        get(url, function(html){
            
          //Create div
            var div = document.createElement("div");
            //div.style.height = "100%";
            div.style.background = panelBackground;
            div.innerHTML = html;


          //Get title
            var title;
            var titles = div.getElementsByTagName("title");
            if (titles.length>0){
                title = titles[0].innerHTML;
            }
            else{
                var h1 = div.getElementsByTagName("h1");
                if (h1.length>0) title = h1[0].innerHTML;
            }


          //Get scripts
            var inlineScripts = [];
            var externalScripts = []; //urls
            var scripts = div.getElementsByTagName("script");
            for (var i=0; i<scripts.length; i++){
                if (scripts[i].src.length>0){
                    externalScripts.push(scripts[i].src);
                }
                else{
                    inlineScripts.push(scripts[i]);
                }
            }
            
            
          //Get external style sheets
            var css = []; //urls
            var cssNodes = [];
            var links = div.getElementsByTagName("link");
            for (var i=0; i<links.length; i++){
                if (links[i].rel=="stylesheet"){
                    if (links[i].href.length>0){
                        css.push(links[i].href);
                        cssNodes.push(links[i]);
                    }
                }
            }
            
            
          //Remove unused/unwanted nodes
            removeNodes(titles);
            removeNodes(div.getElementsByTagName("description"));
            removeNodes(div.getElementsByTagName("keywords")); 
            removeNodes(scripts);
            removeNodes(cssNodes);
            

          //Get html and delete div
            html = div.outerHTML;
            div = null;


          //Load includes and call the callback
            loadIncludes(css, externalScripts, function(){
                if (callback) callback.apply(me, [html, title, inlineScripts]);
            });
            
        });
    };


  //**************************************************************************
  //** get
  //**************************************************************************
    var get = function(url, success){
        
        var request = null;
        if (window.XMLHttpRequest) {
            request = new XMLHttpRequest();
        }
        else {
            request = new ActiveXObject("Microsoft.XMLHTTP");
        }

        request.open("GET", url, true);
        request.onreadystatechange = function(){
            if (request.readyState === 4) {
                if (request.status===200){
                    
                    if (success) success.apply(me, [request.responseText]);   
                }
            }
        };
        
        request.send(); 
    };
    
    
  //**************************************************************************
  //** loadIncludes
  //**************************************************************************
  /** Dynamically loads javascript and stylesheets.
   */
    var loadIncludes = function(css, scripts, callback){

        parseIncludes();

        var addInclude = function(category, key){
            var obj = includes[category];
            for (var k in obj){
                if (obj.hasOwnProperty(k)){
                    if (k==key) return false;
                }
            }
            return true;
        };

        var arr = [];
        
        
      //Generate list of stylesheets to include
        for (var i=0; i<css.length; i++){
            if (addInclude("css", css[i])){
                var link = document.createElement("link");
                link.setAttribute("rel", "stylesheet");
                link.setAttribute("type", "text/css");
                link.setAttribute("href", css[i]);
                arr.push(link);                
                
                includes.css[css[i]] = true;
            }
            else{
                log("Skipping " + css[i] + "...");
            }
        }
        
        
      //Generate list of javascripts to include
        for (var i=0; i<scripts.length; i++){
            if (addInclude("scripts", scripts[i])){
                var script = document.createElement("script");
                script.setAttribute("type", "text/javascript");
                script.setAttribute("src", scripts[i]);
                arr.push(script);
                                
                includes.scripts[scripts[i]] = true;
            }
            else{
                log("Skipping " + scripts[i] + "...");
            }
        }


      //Load includes
        if (arr.length>0){

            var t = arr.length;
            var loadResource = function(obj){
                obj.onload = function() {
                    log( 
                        Math.round((1-(arr.length/t))*100) + "%"
                    );
                    
                    if (arr.length>0) loadResource(arr.shift());
                    else {
                        if (callback!=null) callback.apply(me, []);
                    }
                };
                head.appendChild(obj);
            };
            
            loadResource(arr.shift());
        }
        else{
            if (callback!=null) callback.apply(me, []);
        }
        
    };
    
    
  //**************************************************************************
  //** parseIncludes
  //**************************************************************************
    var parseIncludes = function(){
        if (includes) return;
        
        includes = {
            css: {},
            scripts: {}
        };
        
        
        var scripts = document.getElementsByTagName("script");
        for (var i=0; i<scripts.length; i++){
            if (scripts[i].src.length>0){
                includes.scripts[scripts[i].src] = true;
            }
        }
        
        
        var css = document.getElementsByTagName("link");
        for (var i=0; i<css.length; i++){
            if (css[i].rel=="stylesheet"){
                if (css[i].href.length>0){
                    includes.css[css[i].href] = true;
                }
            }
        }
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
    
    
    
    
    init();
};