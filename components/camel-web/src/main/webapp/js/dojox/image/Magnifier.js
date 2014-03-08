/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.Magnifier"]){
dojo._hasResource["dojox.image.Magnifier"]=true;
dojo.provide("dojox.image.Magnifier");
dojo.require("dojox.gfx");
dojo.require("dojox.image.MagnifierLite");
dojo.declare("dojox.image.Magnifier",[dojox.image.MagnifierLite],{_createGlass:function(){
this.glassNode=dojo.doc.createElement("div");
this.surfaceNode=this.glassNode.appendChild(dojo.doc.createElement("div"));
dojo.addClass(this.glassNode,"glassNode");
dojo.body().appendChild(this.glassNode);
with(this.glassNode.style){
height=this.glassSize+"px";
width=this.glassSize+"px";
}
this.surface=dojox.gfx.createSurface(this.surfaceNode,this.glassSize,this.glassSize);
this.img=this.surface.createImage({src:this.domNode.src,width:this._zoomSize.w,height:this._zoomSize.h});
},_placeGlass:function(e){
var x=e.pageX-2;
var y=e.pageY-2;
var _4=this.offset.x+this.offset.w+2;
var _5=this.offset.y+this.offset.h+2;
if(x<this.offset.x||y<this.offset.y||x>_4||y>_5){
this._hideGlass();
}else{
this.inherited(arguments);
}
},_setImage:function(e){
var _7=(e.pageX-this.offset.l)/this.offset.w;
var _8=(e.pageY-this.offset.t)/this.offset.h;
var x=(this._zoomSize.w*_7*-1)+(this.glassSize*_7);
var y=(this._zoomSize.h*_8*-1)+(this.glassSize*_8);
this.img.setShape({x:x,y:y});
}});
}
