/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.themes.ET.greys"]){
dojo._hasResource["dojox.charting.themes.ET.greys"]=true;
dojo.provide("dojox.charting.themes.ET.greys");
dojo.require("dojox.charting.Theme");
dojo.deprecated("dojox.charting.themes.ET.greys","1.3");
(function(){
var _1=dojox.charting;
_1.themes.ET.greys=new _1.Theme({antiAlias:false,chart:{stroke:null,fill:"inherit"},plotarea:{stroke:null,fill:"transparent"},axis:{stroke:{width:0},line:{width:0},majorTick:{color:"#666666",width:1,length:5},minorTick:{color:"black",width:0.5,length:2},font:"normal normal normal 8pt Tahoma",fontColor:"#999999"},series:{outline:{width:0,color:"black"},stroke:{width:1,color:"black"},fill:dojo.colorFromHex("#3b444b"),font:"normal normal normal 7pt Tahoma",fontColor:"#717171"},marker:{stroke:{width:1},fill:"#333",font:"normal normal normal 7pt Tahoma",fontColor:"#000"},colors:[dojo.colorFromHex("#8a8c8f"),dojo.colorFromHex("#4b4b4b"),dojo.colorFromHex("#3b444b"),dojo.colorFromHex("#2e2d30"),dojo.colorFromHex("#000000")]});
})();
}
