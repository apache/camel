/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.widget.Sparkline"]){
dojo._hasResource["dojox.charting.widget.Sparkline"]=true;
dojo.provide("dojox.charting.widget.Sparkline");
dojo.require("dojox.charting.widget.Chart2D");
dojo.require("dojox.charting.themes.ET.greys");
(function(){
var d=dojo;
dojo.declare("dojox.charting.widget.Sparkline",dojox.charting.widget.Chart2D,{theme:dojox.charting.themes.ET.greys,margins:{l:0,r:0,t:0,b:0},type:"Lines",valueFn:"Number(x)",store:"",field:"",query:"",queryOptions:"",start:"0",count:"Infinity",sort:"",data:"",name:"default",buildRendering:function(){
var n=this.srcNodeRef;
if(!n.childNodes.length||!d.query("> .axis, > .plot, > .action, > .series",n).length){
var _3=document.createElement("div");
d.attr(_3,{"class":"plot","name":"default","type":this.type});
n.appendChild(_3);
var _4=document.createElement("div");
d.attr(_4,{"class":"series",plot:"default",name:this.name,start:this.start,count:this.count,valueFn:this.valueFn});
d.forEach(["store","field","query","queryOptions","sort","data"],function(i){
if(this[i].length){
d.attr(_4,i,this[i]);
}
},this);
n.appendChild(_4);
}
this.inherited(arguments);
}});
})();
}
