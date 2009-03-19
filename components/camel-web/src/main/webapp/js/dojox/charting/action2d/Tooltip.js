/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.action2d.Tooltip"]){
dojo._hasResource["dojox.charting.action2d.Tooltip"]=true;
dojo.provide("dojox.charting.action2d.Tooltip");
dojo.require("dojox.charting.action2d.Base");
dojo.require("dijit.Tooltip");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.scan");
dojo.require("dojox.lang.functional.fold");
(function(){
var _1=function(o){
var t=o.run&&o.run.data&&o.run.data[o.index];
if(t&&typeof t=="object"&&t.tooltip){
return t.tooltip;
}
return o.element=="bar"?o.x:o.y;
};
var df=dojox.lang.functional,_5=Math.PI/4,_6=Math.PI/2;
dojo.declare("dojox.charting.action2d.Tooltip",dojox.charting.action2d.Base,{defaultParams:{text:_1},optionalParams:{},constructor:function(_7,_8,_9){
this.text=_9&&_9.text?_9.text:_1;
this.connect();
},process:function(o){
if(o.type==="onplotreset"||o.type==="onmouseout"){
dijit.hideTooltip(this.aroundRect);
this.aroundRect=null;
return;
}
if(!o.shape||o.type!=="onmouseover"){
return;
}
var _b={type:"rect"},_c=["after","before"];
switch(o.element){
case "marker":
_b.x=o.cx;
_b.y=o.cy;
_b.width=_b.height=1;
break;
case "circle":
_b.x=o.cx-o.cr;
_b.y=o.cy-o.cr;
_b.width=_b.height=2*o.cr;
break;
case "column":
_c=["above","below"];
case "bar":
_b=dojo.clone(o.shape.getShape());
break;
default:
if(!this.angles){
if(typeof o.run.data[0]=="number"){
this.angles=df.map(df.scanl(o.run.data,"+",0),"* 2 * Math.PI / this",df.foldl(o.run.data,"+",0));
}else{
this.angles=df.map(df.scanl(o.run.data,"a + b.y",0),"* 2 * Math.PI / this",df.foldl(o.run.data,"a + b.y",0));
}
}
var _d=(this.angles[o.index]+this.angles[o.index+1])/2;
_b.x=o.cx+o.cr*Math.cos(_d);
_b.y=o.cy+o.cr*Math.sin(_d);
_b.width=_b.height=1;
if(_d<_5){
}else{
if(_d<_6+_5){
_c=["below","above"];
}else{
if(_d<Math.PI+_5){
_c=["before","after"];
}else{
if(_d<2*Math.PI-_5){
_c=["above","below"];
}
}
}
}
break;
}
var lt=dojo.coords(this.chart.node,true);
_b.x+=lt.x;
_b.y+=lt.y;
_b.x=Math.round(_b.x);
_b.y=Math.round(_b.y);
_b.width=Math.ceil(_b.width);
_b.height=Math.ceil(_b.height);
this.aroundRect=_b;
dijit.showTooltip(this.text(o),this.aroundRect,_c);
}});
})();
}
