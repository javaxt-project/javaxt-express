if(!javaxt) var javaxt={};
if(!javaxt.express) javaxt.express={};

//******************************************************************************
//**  Activity Meter
//******************************************************************************
/**
 *   Panel used to render user activity using real-time charts. Requires D3.
 *
 ******************************************************************************/

javaxt.express.ActivityMeter = function(parent, config) {

    var me = this;
    var defaultConfig = {
        maxIdleTime: 5*60*1000, //5 minutes
        lineGraph: {
            refeshRate: 500
        },
        userChart: {

        }
    };

    var lineChart, pieChart; //d3 svg
    var requestsPerMinute = {};
    var activeUsers = {};


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    var init = function(){

      //Parse config
        config = merge(config, defaultConfig);
        if (!config.style) config.style = javaxt.dhtml.style.default;

        var div = createElement("div", parent, "user-stats");


      //Create main table
        var table = createTable(div);
        var tr = table.addRow();


      //Create line chart
        createLineChart(tr.addColumn({
            width: "100%",
            height: "100%",
            padding: "20px"
        }));


      //Create donut chart
        createDonutChart(tr.addColumn({
            padding: "20px",
            height: "100%"
        }));


        me.el = div;
        addShowHide(me);
    };


  //**************************************************************************
  //** notify
  //**************************************************************************
    this.notify = function(op, model, id, userID){
        var currTime = getCurrentTime();
        var numRequests = requestsPerMinute[currTime];
        requestsPerMinute[currTime] = !numRequests ? 1 : numRequests+1;
    };


  //**************************************************************************
  //** clear
  //**************************************************************************
    this.clear = function(){
        if (lineChart){
            lineChart.stop();
            lineChart.clear();
        }
        if (pieChart) {
            pieChart.stop();
            pieChart.clear();
        }
    };


  //**************************************************************************
  //** update
  //**************************************************************************
    this.update = function(_activeUsers){
        activeUsers = _activeUsers;
        if (lineChart) lineChart.start();
        if (pieChart) pieChart.start();
    };


  //**************************************************************************
  //** createLineChart
  //**************************************************************************
    var createLineChart = function(parent){

        var div = createElement("div", parent, {
            width: "100%",
            height: "100%"
        });




        onRender(div, function(){

            var _chart = d3.select(div);
            const bb = _chart.node().getBoundingClientRect();


            const xScale = d3.scaleLinear()
              .domain([0, 100])
              .range([0, bb.width]);

            const yScale = d3.scaleLinear()
              .domain([0, 10])
              .range([bb.height, 0]);


            var data = new Array(100).fill(0);

            lineChart = _chart
              .append('svg')
              .attr('width', '100%')
              .attr('height', '100%');


            const line = d3.line()
              .curve(d3.curveBasis)
              .x((_, i) => xScale(i))
              .y((d) => yScale(d));


            lineChart.append('path')
                .attr('d', line(data))
                .style("stroke", "#777")
                .style("stroke-width", 1)
                .style("fill", "none");



            lineChart.start = function(){
                lineChart.stop();
                lineChart.interval = setInterval(function(){

                    var currTime = getCurrentTime();
                    var numRequests = requestsPerMinute[currTime];
                    if (!numRequests) numRequests = 0;
                    delete requestsPerMinute[currTime];

                    data.pop();
                    data.unshift(numRequests);

                    var lineColor = "#777";
                    for (var i in data){
                        if (data[i]>0){
                            lineColor = "#6699CC";
                            break;
                        }
                    }

                    lineChart.selectAll('path')
                      .data([data])
                      .attr('d', line)
                      .style("stroke", lineColor);

                }, config.lineGraph.refeshRate);

            };

            lineChart.stop = function(){
                clearInterval(lineChart.interval);
            };

            lineChart.clear = function(){
                data = new Array(100).fill(0);
                lineChart.selectAll('path')
                  .data([data])
                  .attr('d', line);
            };

            lineChart.start();
        });
    };


  //**************************************************************************
  //** createDonutChart
  //**************************************************************************
    var createDonutChart = function(parent){

        var div = createElement("div", parent,{
            width: "350px",
            height: "100%"
        });

        //title: "Active Users",


        onRender(div, function(){


            var width = div.offsetWidth;
            var height = div.offsetHeight;
            var radius = Math.min(width, height) / 2;
            var cutout = 0.65; //percent
            var innerRadius = radius*cutout;
            var arc = d3.arc()
                .innerRadius(innerRadius)
                .outerRadius(radius);


          //Create pie chart
            pieChart = d3.select(div).append("svg")
              .attr("width", "100%")
              .attr("height", "100%")
              .append("g")
              .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");


          //Creat pie function to parse data
            var pie = d3.pie().value(function(d) {return d.value; });


          //Create label in the center of the pie chart
            var label = pieChart.append("text")
              .attr("text-anchor", "middle")
		 .attr('font-size', '4em')
		 .attr('y', 20);

          //Create function to animate changes in the pie chart
            var arcTween = function(a) {
                var i = d3.interpolate(this._current, a);
                this._current = i(0);
                return function(t) {
                    return arc(i(t));
                };
            };


          //Create function to generate data for the pie chart
            var getData = function(){
                var data = [];
                for (var key in activeUsers) {
                    var lastUpdate = new Date(activeUsers[key]).getTime();
                    var currTime = new Date().getTime();
                    var elapsedTime = currTime-lastUpdate;
                    if (true) { //if (elapsedTime<config.maxIdleTime){
                        data.push({
                            key: key,
                            value: config.maxIdleTime-elapsedTime
                        });
                    }
                }
                return data;
            };


          //Create function to colorize categorical data
            var getColor = d3.scaleOrdinal(d3.schemeCategory10);


          //Create render function used to update the chart
            var render = function(data){

                var numActiveUsers = Object.keys(data).length;
                label.text(numActiveUsers+"");

                if (numActiveUsers===0){
                    data.push({
                        key: "-1",
                        value: 1
                    });
                }




                var pieData = pie(data);


                // add transition to new path
                pieChart.selectAll("path").data(pieData).transition().duration(100).attrTween("d", arcTween);



                // add any new paths
                pieChart.selectAll("path")
                .data(pieData)
                .enter().append("path")
                  .attr("d", arc)
                  .attr("fill", function(d,i){
                      if (d.data.key === "-1") return "#dcdcdc";
                      return getColor(d.data.key);
                  })
                  .style("opacity", 0.8)
                  .each(function(d){ this._current = d; });

                // remove data not being used
                pieChart.selectAll("path")
                  .data(pieData).exit().remove();

            };





            pieChart.start = function(){
                pieChart.stop();
                pieChart.interval = setInterval(function(){

                    var inactiveUsers = [];
                    for (var key in activeUsers) {
                        var lastUpdate = activeUsers[key];
                        var currTime = new Date().getTime();
                        var elapsedTime = currTime-lastUpdate;
                        if (elapsedTime>config.maxIdleTime){
                            inactiveUsers.push(key);
                        }
                    }


                  //Prune inactive users
                    for (var i in inactiveUsers){
                        var userID = inactiveUsers[i];
                        delete activeUsers[userID];
                    }

                    render(getData());

                }, 1000);
            };

            pieChart.stop = function(){
                clearInterval(pieChart.interval);
            };

            pieChart.clear = function(){
                render([]);
                //pieChart.selectAll("*").remove();
            };

            pieChart.start();

        });
    };


  //**************************************************************************
  //** getCurrentTime
  //**************************************************************************
    var getCurrentTime = function(){
        var d = new Date();
        return d.getUTCFullYear() +
        pad(d.getUTCMonth()+1) +
        pad(d.getUTCDate()) +
        pad(d.getUTCHours()) +
        pad(d.getUTCMinutes());
    };


  //**************************************************************************
  //** pad
  //**************************************************************************
    var pad = function(i){
        return (i<9 ? "0"+i : i+"");
    };


  //**************************************************************************
  //** Utils
  //**************************************************************************
    var createElement = javaxt.dhtml.utils.createElement;
    var createTable = javaxt.dhtml.utils.createTable;
    var addShowHide = javaxt.dhtml.utils.addShowHide;
    var onRender = javaxt.dhtml.utils.onRender;
    var merge = javaxt.dhtml.utils.merge;


    init();
};