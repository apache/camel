/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.themes.PlotKit.red"]){
dojo._hasResource["dojox.charting.themes.PlotKit.red"]=true;
dojo.provide("dojox.charting.themes.PlotKit.red");
dojo.require("dojox.charting.Theme");
(function(){
var _1=dojox.charting;
_1.themes.PlotKit.red=new _1.Theme({chart:{stroke:null,fill:"white"},plotarea:{stroke:null,fill:"#f5e6e6"},axis:{stroke:{color:"#fff",width:2},line:{color:"#fff",width:1},majorTick:{color:"#fff",width:2,length:12},minorTick:{color:"#fff",width:1,length:8},font:"normal normal normal 8pt Tahoma",fontColor:"#999"},series:{outline:{width:1,color:"#fff"},stroke:{width:2,color:"#666"},fill:new dojo.Color([102,102,102,0.8]),font:"normal normal normal 7pt Tahoma",fontColor:"#000"},marker:{stroke:{width:2},fill:"#333",font:"normal normal normal 7pt Tahoma",fontColor:"#000"},colors:[]});
_1.themes.PlotKit.red.defineColors({hue:1,saturation:60,low:40,high:88});
})();
}
