/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.MagnifierLite"]){
dojo._hasResource["dojox.image.MagnifierLite"]=true;
dojo.provide("dojox.image.MagnifierLite");
dojo.experimental("dojox.image.MagnifierLite");
dojo.require("dijit._Widget");
dojo.declare("dojox.image.MagnifierLite",dijit._Widget,{glassSize:125,scale:6,postCreate:function(){
this.inherited(arguments);
this._adjustScale();
this._createGlass();
this.connect(this.domNode,"onmouseenter","_showGlass");
this.connect(this.glassNode,"onmousemove","_placeGlass");
this.connect(this.img,"onmouseout","_hideGlass");
this.connect(window,"onresize","_adjustScale");
},_createGlass:function(){
var _1=this.glassNode=dojo.doc.createElement("div");
this.surfaceNode=_1.appendChild(dojo.doc.createElement("div"));
dojo.addClass(_1,"glassNode");
dojo.body().appendChild(_1);
dojo.style(_1,{height:this.glassSize+"px",width:this.glassSize+"px"});
this.img=dojo.clone(this.domNode);
_1.appendChild(this.img);
dojo.style(this.img,{position:"relative",top:0,left:0,width:this._zoomSize.w+"px",height:this._zoomSize.h+"px"});
},_adjustScale:function(){
this.offset=dojo.coords(this.domNode,true);
this._imageSize={w:this.offset.w,h:this.offset.h};
this._zoomSize={w:this._imageSize.w*this.scale,h:this._imageSize.h*this.scale};
},_showGlass:function(e){
this._placeGlass(e);
dojo.style(this.glassNode,{visibility:"visible",display:""});
},_hideGlass:function(e){
dojo.style(this.glassNode,{visibility:"hidden",display:"none"});
},_placeGlass:function(e){
this._setImage(e);
var _5=Math.floor(this.glassSize/2);
dojo.style(this.glassNode,{top:Math.floor(e.pageY-_5)+"px",left:Math.floor(e.pageX-_5)+"px"});
},_setImage:function(e){
var _7=(e.pageX-this.offset.l)/this.offset.w;
var _8=(e.pageY-this.offset.t)/this.offset.h;
var x=(this._zoomSize.w*_7*-1)+(this.glassSize*_7);
var y=(this._zoomSize.h*_8*-1)+(this.glassSize*_8);
dojo.style(this.img,{top:y+"px",left:x+"px"});
},destroy:function(_b){
dojo.destroy(this.glassNode);
this.inherited(arguments);
}});
}
