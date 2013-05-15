dojo.require("dojox.charting.Theme");
dojo.require("dojox.charting.scaler.linear");
dojo.require("dojox.charting.Chart2D");
dojo.require("dojox.lang.functional");
dojo.require("dojox.charting.themes.PlotKit.orange");

var chart;
var threadPoolSeries = [];

var xhrArgs = {
  url: "/threadpools/" + threadPoolName + "/poolSize",
  handleAs: "text",
  load: function(data){
	if(threadPoolSeries.length == 100) {
		threadPoolSeries.splice(0, 1);
	}
    threadPoolSeries.push(parseInt(data));
    
    chart.updateSeries("ThreadPoolSize", threadPoolSeries);
    chart.render();
    setTimeout("update()", 1000);
  }
}
  
var update = function() {
	dojo.xhrGet(xhrArgs);
};

var createChart = function() {
  chart = new dojox.charting.Chart2D("threadPoolSize");
  chart.setTheme(dojox.charting.themes.PlotKit.orange);
  chart.addAxis("x", {fixLower: "minor", natural: true, min: 1, max: 100});
  chart.addAxis("y", {vertical: true, min: 0, max: limit, majorTickStep: 5, minorTickStep: 1});
  chart.addPlot("default", {type: "Areas"});
  chart.addSeries("ThreadPoolSize", threadPoolSeries);
  chart.addPlot("grid", {type: "Grid", hMinorLines: true});
  chart.render();
  setTimeout("update()", 1000);  
};

dojo.addOnLoad(createChart);