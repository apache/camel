/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sketch.Anchor"]){
dojo._hasResource["dojox.sketch.Anchor"]=true;
dojo.provide("dojox.sketch.Anchor");
dojo.require("dojox.gfx");
(function(){
var ta=dojox.sketch;
ta.Anchor=function(an,id,_4){
var _5=this;
var _6=4;
var _7=null;
this.type=function(){
return "Anchor";
};
this.annotation=an;
this.id=id;
this._key="anchor-"+ta.Anchor.count++;
this.shape=null;
this.isControl=(_4!=null)?_4:true;
this.beginEdit=function(){
this.annotation.beginEdit(ta.CommandTypes.Modify);
};
this.endEdit=function(){
this.annotation.endEdit();
};
this.zoom=function(_8){
if(this.shape){
var rs=Math.floor(_6/_8);
var _a=dojox.gfx.renderer=="vml"?1:1/_8;
this.shape.setShape({x:an[id].x-rs,y:an[id].y-rs,width:rs*2,height:rs*2}).setStroke({color:"black",width:_a});
}
};
this.setBinding=function(pt){
an[id]={x:an[id].x+pt.dx,y:an[id].y+pt.dy};
an.draw();
an.drawBBox();
};
this.setUndo=function(){
an.setUndo();
};
this.enable=function(){
if(!an.shape){
return;
}
an.figure._add(this);
_7={x:an[id].x-_6,y:an[id].y-_6,width:_6*2,height:_6*2};
this.shape=an.shape.createRect(_7).setFill([255,255,255,0.35]);
this.shape.getEventSource().setAttribute("id",_5._key);
this.shape.getEventSource().setAttribute("shape-rendering","crispEdges");
this.zoom(an.figure.zoomFactor);
};
this.disable=function(){
an.figure._remove(this);
if(an.shape){
an.shape.remove(this.shape);
}
this.shape=null;
_7=null;
};
};
ta.Anchor.count=0;
})();
}
