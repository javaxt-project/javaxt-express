if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};
if(!javaxt.express.app) javaxt.express.app={};

//******************************************************************************
//**  Horizon App
//******************************************************************************
/**
 *   User interface with a fixed header and horizontal tabs. The user interface
 *   is initialized via the update() method. Websockets are used to relay
 *   events between the client and the server.
 *
 ******************************************************************************/

javaxt.express.app.Horizon = function(parent, config) {
    this.className = "javaxt.express.app.Horizon";

    var me = this;
    var defaultConfig = {

      /** Name of the application. By default, the name will be used as the
       *  document title. As a user switches tabs, the tab name will be
       *  appended to the title.
       */
        name: "Express",

      /** Style for individual elements within the component. In addition,
       *  there is a general "javaxt" config for javaxt-components. This is a
       *  complex, nested config. See "default.js" in the javaxt-webcontrols.
       *  Note that you can provide CSS class names or an inline set of css
       *  style definitions for each components and javaxt subcomponents.
       */
        style: {
            javaxt: javaxt.dhtml.style.default,
            header: {
                div: "app-header",
                icon: "app-header-icon noselect",
                profileButton: "app-header-profile noselect",
                menuButton: "app-header-menu noselect",
                menuPopup: "app-menu",
                menuItem: "app-menu-item noselect"
            },
            navbar: {
                div: "app-nav-bar",
                tabs: "app-tab-container"
            },
            body: {
                div: "app-body"
            },
            footer: {
                div: "app-footer"
            }
        },


      /** Map of URLs to REST end points
       */
        url: {

          /** URL to the login service
           */
            login: "login",

          /** URL to the logoff service
           */
            logoff: "logoff",

          /** URL to the web socket endpoint that is sending CRUD notifications
           */
            websocket: "/ws"
        },


      /** Used to define the maximum idle time for a user before calling
       *  logoff(). Units are in milliseconds. Default is false (i.e. no
       *  auto-logoff).
       */
        autoLogoff: false,


      /** A shared array of javaxt.dhtml.Window components. All the windows in
       *  the array are automatically closed when a user logs off or when the
       *  logoff() method is called. You are encouraged to create your own
       *  array and pass it to the constructor via this config setting and
       *  update the array whenever you create a new window.
       */
        windows: [],


        renderers: {
            profileButton: function(user, profileButton){}
        },

        messages: {
            connectionLost: "The connection to the server has been lost. " +
            "The internet might be down or there might be a problem with the server. " +
            "Some features might not work as expected while the server is offline. " +
            "Please do not refresh your browser. We will try to reconnect in a few moments.",

            connectionTimeout: "We have lost contact with the server. " +
            "It has been unavailable for over 5 minutes. Please check your " +
            "internet connection or contact the system administrator for assistance."
        }
    };

    var waitmask;
    var auth;
    var currUser;

  //Web socket stuff
    var ws; //web socket listener
    var connected = false;
    var communicationError;


  //Header components
    var profileButton, menuButton; //header buttons
    var mainMenu, profileMenu;
    var callout;

  //Other components
    var tabbar, body, footer;
    var tabs = {};
    var panels = {};
    var timers = {};

    var userInteractions = ["mousemove","click","keydown","touchmove","wheel"];



  //**************************************************************************
  //** Constructor
  //**************************************************************************
    var init = function(){

        if (!config) config = {};
        config = merge(config, defaultConfig);


        auth = new javaxt.dhtml.Authentication(config.url.login, config.url.logoff);


      //Set global configuration variables
        if (!config.fx) config.fx = new javaxt.dhtml.Effects();

        if (!config.waitmask) config.waitmask = new javaxt.express.WaitMask(document.body);
        waitmask = config.waitmask;


      //Prevent native browser shortcuts (ctrl+a,h,o,p,s,...)
        document.addEventListener("keydown", function(e){
            if ((e.keyCode == 65 || e.keyCode == 72 || e.keyCode == 79 || e.keyCode == 80 || e.keyCode == 83) &&
            (navigator.platform.match("Mac") ? e.metaKey : e.ctrlKey)) {
                e.preventDefault();
                e.stopPropagation();
            }
        });


      //Create main table
        var table = createTable(parent);


      //Create header
        createHeader(table.addRow().addColumn(config.style.header.div));


      //Create tabs
        var td = table.addRow().addColumn(config.style.navbar.div);
        tabbar = createElement("div", td, config.style.navbar.tabs);


      //Create body
        body = table.addRow().addColumn(config.style.body.div);
        body.style.height = "100%";


      //Create footer
        footer = table.addRow().addColumn(config.style.footer.div);


        me.el = table;
    };


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to initialize the app with a new user and a set of tabs
   *  @param user Simple json object with an id. Additional attributes such
   *  as name, contact info, etc may be present and used by the renderers
   *  defined in the config (e.g. profileButton)
   *  @param tabs Either an array or json object with tabs. Each entry should
   *  have a name and a fully-qualified class (e.g. com.javaxt.Test). The
   *  class will be instantiated at runtime. Note if the class has a public
   *  update() method, it will be called after the class is instantiated.
   */
    this.update = function(user, tabs){

      //Update title
        document.title = config.name;


      //Add tabs
        if (tabs){
            if (isArray(tabs)){
                tabs.forEach((tab)=>{
                    addTab(tab.name, tab.cls);
                });
            }
            else{
                for (var key in tabs) {
                    if (tabs.hasOwnProperty(key)){
                        addTab(key, tabs[key]);
                    }
                }
            }
        }


      //Update user
        var prevUserID = currUser ? currUser.id : null;
        updateUser(user);
        if (user.id===prevUserID) return;


      //Watch for forward and back events via a 'popstate' listener
        enablePopstateListener();


      //Watch for user events
        enableEventListeners();


      //Create auto-logoff timer
        if (config.autoLogoff && config.autoLogoff>0){
            timers.logoff = setTimeout(me.logoff, config.autoLogoff);
        }


      //Create web socket listener. Note that the listener is destroyed on logoff()
        if (!ws) ws = new javaxt.dhtml.WebSocket({
            url: config.url.websocket,
            onMessage: function(msg){

                try { me.onMessage(msg); }
                catch(e) {}

                var arr = msg.split(",");
                var op = arr[0];
                var model = arr[1];
                var id = arr[2];
                var userID = arr[3];


              //Parse id as needed
                if (id.indexOf("_")===-1 && id.indexOf("-")===-1){
                    try {
                        var i = parseInt(id);
                        if (!isNaN(i)) id = i;
                    }
                    catch(e) {}
                }


              //Parse userID
                try { userID = parseInt(userID); } catch(e) {}


              //Process event
                processEvent(op, model, id, userID);


            },
            onConnect: function(){
                if (!connected){
                    connected = true;
                    processEvent("connect", "WebSocket", -1, -1);
                }
            },
            onDisconnect: function(){
                if (connected){
                    connected = false;
                    processEvent("disconnect", "WebSocket", -1, -1);
                }
            },
            onTimeout: function(){
                if (communicationError) communicationError.hide();
                alert(config.messages.connectionTimeout);
            }
        });
    };


  //**************************************************************************
  //** sendMessage
  //**************************************************************************
  /** Used to send a message to the server via websockets.
   */
    this.sendMessage = function(msg){
        if (ws) ws.send(msg);
    };


  //**************************************************************************
  //** onMessage
  //**************************************************************************
  /** Called whenever a message is recieved from the server via websockets.
   *  Used the onModelChangeEvent() event listener to receive CRUD events
   *  specifically.
   */
    this.onMessage = function(msg){};


  //**************************************************************************
  //** onModelChangeEvent
  //**************************************************************************
  /** Called whenever a Model created, updated, or deleted.
   *  @param op Operation name. Options include "create", "update", or "delete"
   *  @param model The name of the model that was changed (e.g. "User").
   *  @param id The unique identifier associated with the model (e.g. 12345)
   *  @param userID The unique identifier associated with the user that's
   *  responsible for the change.
   */
    this.onModelChangeEvent = function(op, model, id, userID){};


  //**************************************************************************
  //** onLogOff
  //**************************************************************************
  /** Called after the logoff() method is complete.
   */
    this.onLogOff = function(){};


  //**************************************************************************
  //** onUserInteration
  //**************************************************************************
  /** Called whenever a user interacts with the app (mouse click, mouse move,
   *  keypress, or touch event).
   */
    this.onUserInteration = function(e){};


    var onUserInteration = function(e){
        me.onUserInteration(e);


        if (timers.logoff){
            clearTimeout(timers.logoff);
            timers.logoff = setTimeout(me.logoff, config.autoLogoff);
        };
    };


    var enableEventListeners = function(){
        userInteractions.forEach((interaction)=>{
            document.body.addEventListener(interaction, onUserInteration);
        });
    };


    var disableEventListeners = function(){
        userInteractions.forEach((interaction)=>{
            document.body.removeEventListener(interaction, onUserInteration);
        });
    };


  //**************************************************************************
  //** updateUser
  //**************************************************************************
    var updateUser = function(user){
        currUser = user;


      //Update the profile button
        if (config.renderers.profileButton){
            config.renderers.profileButton(user, profileButton);
        }



      //Get active and requested tab
        var currTab, requestedTab;
        var t = getParameter("tab").toLowerCase();
        for (var key in tabs) {
            if (tabs.hasOwnProperty(key)){
                var tab = tabs[key];
                if (tab.isVisible()){
                    if (tab.className==="active"){
                        currTab = tab;
                    }
                    if (key.toLowerCase()===t){
                        requestedTab = tab;
                    }
                }
            }
        }



      //Get user preferences
        user.preferences = new javaxt.express.UserPreferences(()=>{


          //Click on a tab
            if (requestedTab){

              //Click on the requested tab
                requestedTab.click();


              //Remove tab parameter from the url
                var state = window.history.state;
                if (!state) state = {};
                var url = window.location.href;
                url = url.replace("tab="+getParameter("tab"),"");
                if (url.lastIndexOf("&")===url.length-1) url = url.substring(0, url.length-1);
                if (url.lastIndexOf("?")===url.length-1) url = url.substring(0, url.length-1);
                history.replaceState(state, document.title, url);

            }
            else{

              //Click on user's last tab
                if (!currTab) currTab = user.preferences.get("Tab");
                if (currTab && tabs[currTab]){
                    tabs[currTab].click();
                }
                else{
                    Object.values(tabs)[0].click();
                }
            }

        });
    };


  //**************************************************************************
  //** processEvent
  //**************************************************************************
  /** Used to process web socket events and dispatch them to other panels as
   *  needed
   */
    var processEvent = function(op, model, id, userID){


      //Process event
        if (model==="WebSocket"){
            if (currUser){
                if (op==="connect"){
                    if (communicationError) communicationError.hide();
                }
                else{
                    if (!communicationError) createErrorMessage();
                    communicationError.show();
                }
            }
            else{
                //logout initiated
            }
        }
        else if (model==="WebFile"){
            if (currUser && currUser.preferences){
                var autoReload = currUser.preferences.get("AutoReload");
                if (autoReload===true || autoReload==="true"){
                    console.log("reload!");
                    location.reload();
                }
                else{
                    console.log("prompt to reload!");
                    location.reload();
                }
            }
        }
        else{
            me.onModelChangeEvent(op, model, id, userID);
        }



      //Dispatch event to other panels
        for (var key in panels) {
            if (panels.hasOwnProperty(key)){
                var panel = panels[key];
                if (panel.notify) panel.notify(op, model, id, userID);
            }
        }

    };


  //**************************************************************************
  //** createHeader
  //**************************************************************************
    var createHeader = function(parent){

        var tr = createTable(parent).addRow();
        var td;


        td = tr.addColumn();
        createElement("div", td, config.style.header.icon);


        td = tr.addColumn();
        td.style.width = "100%";


      //Create profile button
        td = tr.addColumn();
        profileButton = createElement("div", td, config.style.header.profileButton);
        profileButton.onclick = function(e){
            if (currUser) showMenu(getProfileMenu(), this);
        };
        addShowHide(profileButton);


      //Create menu button
        td = tr.addColumn();
        menuButton = createElement("div", td, config.style.header.menuButton);
        createElement("i", menuButton, "fas fa-ellipsis-v");
        menuButton.onclick = function(e){
            if (currUser) showMenu(getMainMenu(), this);
        };
        addShowHide(menuButton);
    };


  //**************************************************************************
  //** addTab
  //**************************************************************************
    var addTab = function(label, className){
        var tab = createElement("div", tabbar);
        tab.innerText = label;

        var fn = function(){
            var panel = panels[label];

            Object.values(panels).forEach((p)=>{
                if (p===panel) return;
                p.hide();
            });


            if (panel){
                panel.show();
            }
            else{


              //Create custom config for the panel
                var cfg = {
                    style: config.style.javaxt,
                    fx: config.fx,
                    waitmask: config.waitmask
                };

              //Update config with non-standard config options
                for (var key in config) {
                    if (config.hasOwnProperty(key)){
                        if (defaultConfig[key]) continue;
                        cfg[key] = config[key];
                    }
                }


              //Instantiate panel
                var cls = eval(className);
                panel = new cls(body, cfg);
                addShowHide(panel);
                panels[label] = panel;
                if (panel.update) panel.update();
            }
        };


        tab.raise = function(){
            if (this.className==="active") return;
            hideWindows();
            for (var i=0; i<tabbar.childNodes.length; i++){
                tabbar.childNodes[i].className = "";
            }
            this.className = "active";
            fn.apply(me, []);
            document.title = config.name + " - " + label;
            if (currUser) currUser.preferences.set("Tab", label);
        };


        tab.onclick = function(){
            if (this.className==="active") return;

          //Update history. Do this BEFORE raising the tab so that whatever
          //history the tab panel has happens AFTER the tab change event.
            var state = window.history.state;
            if (state==null) state = {};
            state[me.className] = {
                tab: label,
                lastUpdate: {
                    date: new Date().getTime(),
                    event: "pushState"
                }
            };
            var url = "";
            history.pushState(state, document.title, url);


          //Raise the tab
            this.raise();
        };

        tabs[label] = tab;
        addShowHide(tab);
    };


  //**************************************************************************
  //** enablePopstateListener
  //**************************************************************************
    var enablePopstateListener = function(){
        disablePopstateListener();
        window.addEventListener('popstate', popstateListener);

      //Set initial history. This is critical for the popstate listener
        history.replaceState({}, null, '');
    };


  //**************************************************************************
  //** disablePopstateListener
  //**************************************************************************
    var disablePopstateListener = function(){
        window.removeEventListener('popstate', popstateListener);
    };


  //**************************************************************************
  //** popstateListener
  //**************************************************************************
  /** Used to processes forward and back events from the browser
   */
    var popstateListener = function(e) {

        if (e.state[me.className]){
            var label = e.state[me.className].tab;
            var tab = tabs[label];
            if (tab) tab.raise();
        }
        else{
            history.back();
        }
    };


  //**************************************************************************
  //** createBody
  //**************************************************************************
    var createBody = function(parent){

    };


  //**************************************************************************
  //** createFooter
  //**************************************************************************
    var createFooter = function(parent){

    };


  //**************************************************************************
  //** showMenu
  //**************************************************************************
    var showMenu = function(menu, target){

        var numVisibleItems = 0;
        for (var i=0; i<menu.childNodes.length; i++){
            var menuItem = menu.childNodes[i];
            if (menuItem.isVisible()) numVisibleItems++;
        }
        if (numVisibleItems===0){
            return;
        }

        var callout = getCallout();
        var innerDiv = callout.getInnerDiv();
        while (innerDiv.firstChild) {
            innerDiv.removeChild(innerDiv.lastChild);
        }
        innerDiv.appendChild(menu);

        var rect = javaxt.dhtml.utils.getRect(target);
        var x = rect.x + (rect.width/2);
        var y = rect.y + rect.height + 3;
        callout.showAt(x, y, "below", "right");
    };


  //**************************************************************************
  //** getProfileMenu
  //**************************************************************************
    var getProfileMenu = function(){
        if (!profileMenu){
            var div = createElement("div", config.style.header.menuPopup);
            div.appendChild(createMenuOption("Account Settings", "edit", function(){
                console.log("Show Account");
            }));
            div.appendChild(createMenuOption("Sign Out", "times", function(){
                me.logoff();
            }));
            profileMenu = div;
        }
        return profileMenu;
    };


  //**************************************************************************
  //** createMenuOption
  //**************************************************************************
    var createMenuOption = function(label, icon, onClick){
        var div = createElement("div", config.style.header.menuItem);
        if (icon && icon.length>0){
            div.innerHTML = '<i class="fas fa-' + icon + '"></i>' + label;
        }
        else{
            div.innerHTML = label;
        }
        div.label = label;
        div.onclick = function(){
            callout.hide();
            onClick.apply(this, [label]);
        };
        addShowHide(div);
        return div;
    };


  //**************************************************************************
  //** createErrorMessage
  //**************************************************************************
  /** Used to create a communications error message
   */
    var createErrorMessage = function(){

      //Create main div
        var div = createElement("div", parent, {
            position: "absolute",
            top: "10px",
            width: "100%",
            height: "0px",
            display: "none"
        });


      //Create show/hide functions
        var fx = config.fx;
        var transitionEffect = "ease";
        var duration = 1000;
        var isVisible = false;

        div.show = function(){
            if (isVisible) return;
            isVisible = true;
            fx.fadeIn(div, transitionEffect, duration, function(){
                menuButton.className += " warning";
            });
        };
        div.hide = function(){
            if (!isVisible) return;
            isVisible = false;
            fx.fadeOut(div, transitionEffect, duration/2, function(){
                menuButton.className = menuButton.className.replaceAll("warning","").trim();
            });
        };
        div.isVisible = function(){
            return isVisible;
        };


      //Add content
        var error = createElement("div", div, "communication-error center");
        createElement("div", error, "icon");
        createElement("div", error, "title").innerText = "Connection Lost";
        createElement("div", error, "message").innerText = config.messages.connectionLost;


      //Add main div to windows array so it closes automatically on logoff
        config.windows.push(div);
        communicationError = div;
    };


  //**************************************************************************
  //** hideWindows
  //**************************************************************************
    var hideWindows = function(){
        config.windows.forEach((window)=>{
            window.hide();
        });
    };


  //**************************************************************************
  //** logoff
  //**************************************************************************
    this.logoff = function(){
        waitmask.show();
        currUser = null;

      //Disable event listeners
        disableEventListeners();
        disablePopstateListener();

      //Stop websocket listener
        if (ws){
            ws.stop();
            ws = null;
        }


      //Stop timers
        for (var key in timers) {
            if (timers.hasOwnProperty(key)){
                var timer = timers[key];
                clearTimeout(timer);
            }
        }
        timers = {};


      //Hide all popup windows
        hideWindows();


      //Remove all tabs
        tabbar.innerHTML = "";
        tabs = {};


      //Destroy panels
        for (var key in panels) {
            if (panels.hasOwnProperty(key)){
                var panel = panels[key];
                if (panel.clear) panel.clear();
                destroy(panel);
            }
        }
        panels = {};


      //Remove menus
        if (mainMenu){
            var parent = mainMenu.parentNode;
            if (parent) parent.removeChild(mainMenu);
            mainMenu = null;
        }
        if (profileMenu){
            var parent = profileMenu.parentNode;
            if (parent) parent.removeChild(profileMenu);
            profileMenu = null;
        }



      //Logoff
        auth.logoff(function(){
            me.onLogOff();
            var pageLoader = new javaxt.dhtml.PageLoader();
            pageLoader.loadPage("index.html", function(){
                waitmask.hide();
            });
        });
    };


  //**************************************************************************
  //** getCallout
  //**************************************************************************
    var getCallout = function(){
        if (callout){
            var parent = callout.el.parentNode;
            if (!parent){
                callout.el.innerHTML = "";
                callout = null;
            }
        }
        if (!callout) callout = new javaxt.dhtml.Callout(document.body,{
            style: config.style.callout
        });
        return callout;
    };


  //**************************************************************************
  //** Utils
  //**************************************************************************
    var createElement = javaxt.dhtml.utils.createElement;
    var getParameter = javaxt.dhtml.utils.getParameter;
    var createTable = javaxt.dhtml.utils.createTable;
    var addShowHide = javaxt.dhtml.utils.addShowHide;
    var isArray = javaxt.dhtml.utils.isArray;
    var destroy = javaxt.dhtml.utils.destroy;
    var merge = javaxt.dhtml.utils.merge;


    init();
};