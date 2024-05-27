if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};


//******************************************************************************
//**  User Preferences
//*****************************************************************************/
/**
 *   Used to set/get user preferences
 *
 ******************************************************************************/

javaxt.express.UserPreferences = function(callback, scope) {

    var me = this;
    var preferences = {};


  //**************************************************************************
  //** refresh
  //**************************************************************************
  /** Used to retrieve user preferences from the server.
   */
    this.refresh = function(callback, scope){
        get("UserPreferences?fields=key,value&format=json", {
            success: function(text){
                JSON.parse(text).forEach((r)=>{
                    preferences[r.key] = r.value;
                });

                if (!scope) scope = me;
                callback.apply(scope, [preferences]);
            },
            failure: function(){

            }
        });
    };


  //**************************************************************************
  //** getPreference
  //**************************************************************************
  /** Used to retrieve a specific preference.
   */
    this.get = function(key){
        var val = preferences[key.toLowerCase()];
        if (typeof val === 'undefined') return null;
        return val;
    };


  //**************************************************************************
  //** setPreference
  //**************************************************************************
  /** Used to set/update a user preference.
   *  @param silent If true, will update local preferences but will not save
   *  anything to the server
   */
    this.set = function(key, value, silent){

        key = key.toLowerCase().trim();
        if (preferences[key]===value) return;


        if (silent===true){

            preferences[key] = value;

        }
        else{

            var preference = {
                key: key,
                value: value
            };


            post("UserPreference", JSON.stringify(preference), {
                success: function(){
                    preferences[key] = value;
                },
                failure: function(){

                }
            });
        }

    };


    var post = javaxt.dhtml.utils.post;
    var get = javaxt.dhtml.utils.get;

    this.refresh(callback, scope);
};