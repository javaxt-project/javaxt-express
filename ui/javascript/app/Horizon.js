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
    this.className = "javaxt.express.app.Horizon"; //used by popstateListener

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

              /** Style for the header that appears at the top of the app.
               */
                div: "app-header",


              /** Style for the app icon/logo that appears on the left side of
               *  the header.
               */
                icon: "app-header-icon noselect",


              /** Style for the user profile button that appears on the right
               *  side of the header.
               */
                profileButton: "app-header-profile noselect",


              /** Style for the menu button that appears on the right side of
               *  the header. The menu button consists of an icon and label.
               *  The CSS class should include definitions for "icon" and
               *  "label".
               */
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
            },

          /** Style for the communication error popup that is rendered when the
           *  connection to the server is lost. The popup consists of an icon,
           *  title, message, and a close button. Note that the nested style
           *  properties can be replaced with a string representing a CSS class,
           *  provided that the class includes definitions for "icon", "title",
           *  "message", and "close".
           */
            communicationError: {
                div: "communication-error center",
                icon: "communication-error-icon",
                title: "title",
                message: "message",
                closeButton: "close"
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


      /** Map of keywords
       */
        keywords: {

          /** URL parameter used to indicate a preferred tab to raise
           */
            requestedTab: "tab"
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
    var timeoutWarning;


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

        if (!config.waitmask || !config.waitmask.el.parentNode)
        config.waitmask = new javaxt.express.WaitMask(document.body);
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
   *  have a name and a class will be instantiated at runtime. The class
   *  constructor should accept two arguments:
   *  <ul>
   *  <li>parent: DOM object</li>
   *  <li>config: JSON object with optional config settings</li>
   *  </ul>
   *  Here's an example of an array of tabs:
   <pre>
    tabs = [
        {name: "Home", class: com.acme.webapp.Home, config: { ... }},
        {name: "Admin", class: com.acme.webapp.Admin, config: { ... }}
    ];
   </pre>
   *  Here's an example of a json object with tabs:
   <pre>
    tabs = {
        Home: com.acme.webapp.Home,
        Admin: com.acme.webapp.Admin
    };
   </pre>
   *  The most significant difference between the two options is that the
   *  array includes an optional config key which will be used to instantiate
   *  the class. Note if the class has a public update() method, it will be
   *  called after the class is instantiated.
   */
    this.update = function(user, tabs){

      //Update title
        document.title = config.name;


      //Update tabs
        updateTabs(tabs);


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
                var n = id+"";
                var isNumber = !isNaN(parseFloat(n)) && !isNaN(n - 0);
                if (isNumber){
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
                connected = false;
                if (communicationError) communicationError.hide(true);
                if (!timeoutWarning) createTimeoutWarning();
                timeoutWarning.show();
            }
        });
    };


  //**************************************************************************
  //** getTabs
  //**************************************************************************
  /** Returns an array of tabs. Each entry includes:
   *  <ul>
   *  <li>name: Name/label of the tab (String)</li>
   *  <li>tab: Tab in the tab bar (DOM Object)</li>
   *  <li>panel: The panel that is rendered in the body (Object). Note that
   *  the panel might be null/undefined if it has never been raised. This is
   *  because panels are only instantiated if a user clicks on a tab.
   *  </li>
   *  </ul>
   */
    this.getTabs = function(){
        var arr = [];
        for (var key in tabs) {
            if (tabs.hasOwnProperty(key)){
                arr.push({
                    name: key,
                    tab: tabs[key],
                    panel: panels[key]
                });
            }
        }
        return arr;
    };


  //**************************************************************************
  //** getTab
  //**************************************************************************
  /** Returns an individual tab for a given label.
   */
    this.getTab = function(name){
        var tab = tabs[name];
        if (!tab) return null;
        return {
            name: name,
            tab: tab,
            panel: panels[name]
        };
    };


  //**************************************************************************
  //** beforeTabChange
  //**************************************************************************
  /** Called immediately before a tab is raised in the tab bar.
   *  @param currTab Object representing the current tab. Example:
   *  <ul>
   *  <li>name: Name/label of the tab (String)</li>
   *  <li>tab: Tab in the tab bar (DOM Object)</li>
   *  <li>panel: The panel that is rendered in the body (Object)</li>
   *  </ul>
   *  @param nextTab Object representing the tab that will be raised.
   */
    this.beforeTabChange = function(currTab, nextTab){};


  //**************************************************************************
  //** onTabChange
  //**************************************************************************
  /** Called whenever a tab is raised in the tab bar.
   *  @param currTab Object with key/value pairs including:
   *  <ul>
   *  <li>name: Name/label of the tab (String)</li>
   *  <li>tab: Tab in the tab bar (DOM Object)</li>
   *  <li>panel: The panel that is rendered in the body (Object)</li>
   *  </ul>
   */
    this.onTabChange = function(currTab){};


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
        if (user) profileButton.show();
        if (config.renderers.profileButton){
            config.renderers.profileButton(user, profileButton);
        }



      //Get active and requested tab
        var currTab, requestedTab;
        var t = getParameter(config.keywords.requestedTab).toLowerCase();
        for (var key in tabs) {
            if (tabs.hasOwnProperty(key)){
                var tab = tabs[key];
                if (tab.isVisible()){
                    if (tab.className==="active"){
                        currTab = key;
                    }
                    if (key.toLowerCase()===t){
                        requestedTab = key;
                    }
                }
            }
        }



      //Get user preferences
        user.preferences = new javaxt.express.UserPreferences(()=>{


          //Raise tab
            if (requestedTab){

              //Remove tab parameter from the url
                var url = window.location.href;
                url = url.replace("tab="+getParameter(config.keywords.requestedTab),"");
                if (url.lastIndexOf("&")===url.length-1) url = url.substring(0, url.length-1);
                if (url.lastIndexOf("?")===url.length-1) url = url.substring(0, url.length-1);


              //Update history
                me.updateHistory({
                    title: config.name + " - " + requestedTab,
                    tab: requestedTab,
                    url: url
                });


              //Raise the tab
                tabs[requestedTab].raise();

            }
            else{

              //Click on user's last tab
                if (!currTab) currTab = user.preferences.get("Tab");
                if (currTab && tabs[currTab]){

                    me.updateHistory({
                        title: config.name + " - " + currTab,
                        tab: currTab
                    });

                    tabs[currTab].raise();
                }
                else{

                    for (var tabLabel in tabs) {
                        if (tabs.hasOwnProperty(tabLabel)){

                            me.updateHistory({
                                title: config.name + " - " + tabLabel,
                                tab: tabLabel
                            });

                            tabs[tabLabel].raise();

                            break;
                        }
                    }

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
                    if (timeoutWarning) timeoutWarning.close();
                    menuButton.hideMessage();
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
                    location.reload();
                }
                else{
                    confirm({
                        width: 515,
                        title: "Update Available",
                        text: "An update is available for this application. " +
                        "Would you like to update now?",
                        leftButton: {
                            label: "Yes",
                            value: true
                        },
                        rightButton: {
                            label: "No",
                            value: false
                        },
                        callback: function(answer){
                            if (answer===true) location.reload();
                            else{
                                menuButton.showMessage("Update Available");
                            }
                        }
                    });
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

      //Render app icon/logo
        createElement("div", tr.addColumn(), config.style.header.icon);


      //Add spacer
        tr.addColumn().style.width = "100%";


      //Create buttons
        createProfileButton(tr.addColumn());
        createMenuButton(tr.addColumn());
    };


  //**************************************************************************
  //** updateTabs
  //**************************************************************************
    var updateTabs = function(obj){

      //Generate a list of tabs to render in the tabbar
        var newTabs = {};
        if (obj){
            if (isArray(obj)){
                obj.forEach((tab)=>{
                    if (tab.cls && !tab.class) tab.class = tab.cls;
                    if (typeof tab.class === 'function') {
                        newTabs[tab.name] = tab;
                    }
                });
            }
            else{
                for (var key in obj) {
                    if (obj.hasOwnProperty(key)){
                        var cls = obj[key];
                        if (typeof cls === 'function') {
                            newTabs[key] = {
                                class: cls
                            };
                        }
                    }
                }
            }
        }


      //Remove any existing tabs from the tabbar
        var activeTab;
        for (var key in tabs) {
            if (tabs.hasOwnProperty(key)){
                var tab = tabs[key];
                if (tab.parentNode) tabbar.removeChild(tab);
                if (tab.className==="active"){
                    activeTab = key;
                }
            }
        }


      //Update tabbar
        for (var key in newTabs) {
            if (newTabs.hasOwnProperty(key)){
                if (tabs[key]){
                    var tab = tabs[key];
                    tabbar.appendChild(tab);
                    tab.show();
                }
                else{
                    createTab(key, newTabs[key]);
                }
            }
        }



      //Raise previously active tab
        if (activeTab){
            if (newTabs[activeTab]){
                tabs[activeTab].className="active";
                var panel = panels[activeTab];
                if (panel) panel.show();
            }
            else{
                tabs[activeTab].className="";
                var panel = panels[activeTab];
                if (panel) panel.hide();
            }
        }
    };


  //**************************************************************************
  //** createTab
  //**************************************************************************
    var createTab = function(label, obj){
        if (tabs[label]) return;

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

              //Get or create config
                var cfg;
                if (obj.config){
                    cfg = obj.config;
                }
                else{


                  //Create custom config for the panel
                    cfg = {
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
                }


              //Instantiate panel
                var fn = eval(obj.class);
                panel = new fn(body, cfg);
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
            if (currUser) currUser.preferences.set("Tab", label);


            me.onTabChange({
                name: label,
                tab: this,
                panel: panels[label]
            });
        };


        tab.onclick = function(){
            if (this.className==="active") return;


            var currTab;
            for (var i=0; i<tabbar.childNodes.length; i++){
                var t = tabbar.childNodes[i];
                if (t.className==="active"){

                    var l = t.innerText;
                    currTab = {
                        name: l,
                        tab: t,
                        panel: panels[l]
                    };
                    break;
                }
            }

            me.beforeTabChange(currTab, {
                name: label,
                tab: this,
                panel: panels[label]
            });



          //Update history. Do this BEFORE raising the tab so that whatever
          //history the tab panel wants to modify happens AFTER the tab change.
            me.addHistory({
                title: config.name + " - " + label,
                tab: label
            });


          //Raise the tab
            this.raise();
        };

        tabs[label] = tab;
        addShowHide(tab);
    };


  //**************************************************************************
  //** addHistory
  //**************************************************************************
  /** Used to add a "page" to the browser history
   *  @param params JSON object with the following:
   *  <ul>
   *  <li>title - text to display in the browser's title</li>
   *  <li>tab - label associated with a tab</li>
   *  <li>url - custom url</li>
   *  </ul>
   */
    this.addHistory = function(params){
        updateState(params, false);
    };


  //**************************************************************************
  //** updateHistory
  //**************************************************************************
  /** Used to update browser history for the current "page"
   *  @param params JSON object with the following:
   *  <ul>
   *  <li>title - text to display in the browser's title</li>
   *  <li>tab - label associated with a tab</li>
   *  <li>url - custom url</li>
   *  </ul>
   */
    this.updateHistory = function(params){
        updateState(params, true);
    };


  //**************************************************************************
  //** updateState
  //**************************************************************************
    var updateState = function(params, replace){

        var title = params.title;
        if (!title) title = document.title;

        var url = "";
        if (params.url){
            url = params.url;
            delete params.url;
        }

        var state = window.history.state;
        if (!state) state = {};

        state[me.className] = params;
        state[me.className].lastUpdate = {
            date: new Date().getTime(),
            event: replace ? "replaceState" : "pushState"
        };

        if (replace){
            document.title = title;
            history.replaceState(state, title, url);
        }
        else{
            history.pushState(state, title, url);
            document.title = title;
        }
    };


  //**************************************************************************
  //** enablePopstateListener
  //**************************************************************************
    var enablePopstateListener = function(){
        disablePopstateListener();
        window.addEventListener('popstate', popstateListener);

      //Set initial history. This is critical for the popstate listener
        var state = window.history.state;
        if (!state) state = {};
        if (!state[me.className]) history.replaceState({}, null, '');
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

        if (e.state[me.className]){ //event emanated from this class

          //Get tab name/label
            var label = e.state[me.className].tab;

          //Check if the label matches the requested tab in the url
            var t = getParameter(config.keywords.requestedTab).toLowerCase();
            if (t && t.length>0){
                if (t!==label.toLowerCase()){
                    for (var key in tabs) {
                        if (tabs.hasOwnProperty(key)){
                            var tab = tabs[key];
                            if (tab.isVisible()){
                                if (key.toLowerCase()===t){
                                    label = key;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            var tab = tabs[label];
            if (tab) tab.raise();
        }
        else{ //user clicked browser back button but there's no state?

            //Don't call history.back(); It may cause unintended side effects
            //in panels with thier own popstate listeners...
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
  //** createMenuButton
  //**************************************************************************
    var createMenuButton = function(parent){
        menuButton = createElement("div", parent, config.style.header.menuButton);
        menuButton.label = createElement("div", menuButton, "label");
        menuButton.icon = createElement("div", menuButton, "icon");
        menuButton.label.style.opacity = 0;
        menuButton.showMessage = function(msg){
            if (!msg) msg = "Warning";
            menuButton.classList.add("warning");
            menuButton.label.innerText = msg;
            menuButton.label.style.opacity = 1;
        };
        menuButton.hideMessage = function(){
            menuButton.label.style.opacity = 0;
            menuButton.label.style.display = "none";
            menuButton.classList.remove("warning");

            setTimeout(()=>{
                menuButton.label.style.display = "";
                menuButton.label.style.opacity = 0;
            }, 500);
        };
        menuButton.onclick = function(e){
            if (currUser) showMenu(getMainMenu(), this);
        };
        addShowHide(menuButton);
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
  //** createProfileButton
  //**************************************************************************
    var createProfileButton = function(parent){
        profileButton = createElement("div", parent, config.style.header.profileButton);
        profileButton.onclick = function(e){
            if (currUser) showMenu(getProfileMenu(), this);
        };
        addShowHide(profileButton);
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
        //if (communicationError) return;

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
        var transitionEffect = "easeInBack";
        var duration = 1000;
        var isVisible = false;

        div.show = function(){
            if (isVisible) return;
            isVisible = true;
            fx.fadeIn(div, transitionEffect, duration);
        };
        div.hide = function(nodelay){
            if (!isVisible) return;
            isVisible = false;
            if (nodelay===true) div.style.display = "none";
            fx.fadeOut(div, transitionEffect, duration/2);
        };
        div.isVisible = function(){
            return isVisible;
        };


      //Add content
        var style = config.style.communicationError;
        var useClass = isString(style);
        var error = createElement("div", div, useClass ? style : style.div);
        createElement("div", error, useClass ? "icon" : style.icon);
        createElement("div", error, useClass ? "title" : style.title).innerText = "Connection Lost";
        createElement("div", error, useClass ? "message" : style.message).innerText = config.messages.connectionLost;
        var closeButton = createElement("div", error, useClass ? "close" : style.closeButton);
        closeButton.onclick = function(){
            div.hide(true);
            setTimeout(()=>{
                menuButton.showMessage("Offline");
            }, 500);
        };


      //Add main div to windows array so it closes automatically on logoff
        config.windows.push(div);
        communicationError = div;
    };


  //**************************************************************************
  //** createTimeoutWarning
  //**************************************************************************
    var createTimeoutWarning = function(){
        //if (timeoutWarning) return;

      //Create window
        timeoutWarning = new javaxt.dhtml.Window(document.body, {
            width: 470,
            valign: "top",
            modal: true,
            title: "Warning",
            style: config.style.javaxt.window,
            buttons: [
                {
                    name: "OK",
                    onclick: function(){
                        timeoutWarning.close();
                    }
                }
            ]
        });


      //Add window to the windows array so it closes automatically on logoff
        config.windows.push(timeoutWarning);


      //Populate the body
        var table = createTable(timeoutWarning.getBody());
        table.style.height = "";
        var tr = table.addRow();
        var d = createElement("div", tr.addColumn({verticalAlign: "top"}), {
            padding: "3px 10px"
        });
        createElement("div", d, config.style.communicationError.icon);
        tr.addColumn({ width: "100%" }).innerText =
        config.messages.connectionTimeout;


      //Watch for open/close events
        timeoutWarning.onClose = function(){
            menuButton.showMessage("Offline");
        };
        timeoutWarning.onOpen = function(){
            if (communicationError) communicationError.hide();
        };
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


      //Update URL
        var state = window.history.state;
        if (!state) state = {};
        var url = window.location.href;
        var idx = url.indexOf("?");
        if (idx>-1) url = url.substring(0, idx);
        document.title = config.name;
        history.replaceState(state, config.name, url);


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


      //Update menu
        menuButton.hideMessage();
        if (mainMenu){
            var parent = mainMenu.parentNode;
            if (parent) parent.removeChild(mainMenu);
            mainMenu = null;
        }


      //Update profile
        profileButton.hide();
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
    var isString = javaxt.dhtml.utils.isString;
    var isArray = javaxt.dhtml.utils.isArray;
    var destroy = javaxt.dhtml.utils.destroy;
    var merge = javaxt.dhtml.utils.merge;


    init();
};