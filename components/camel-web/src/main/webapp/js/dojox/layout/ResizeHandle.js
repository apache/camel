/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.layout.ResizeHandle"]){
dojo._hasResource["dojox.layout.ResizeHandle"]=true;
dojo.provide("dojox.layout.ResizeHandle");
dojo.experimental("dojox.layout.ResizeHandle");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojo.fx");
dojo.declare("dojox.layout.ResizeHandle",[dijit._Widget,dijit._Templated],{targetId:"",targetContainer:null,resizeAxis:"xy",activeResize:false,activeResizeClass:"dojoxResizeHandleClone",animateSizing:true,animateMethod:"chain",animateDuration:225,minHeight:100,minWidth:100,constrainMax:false,maxHeight:0,maxWidth:0,fixedAspect:false,intermediateChanges:false,templateString:"<div dojoAttachPoint=\"resizeHandle\" class=\"dojoxResizeHandle\"><div></div></div>",postCreate:function(){
this.connect(this.resizeHandle,"onmousedown","_beginSizing");
if(!this.activeResize){
this._resizeHelper=dijit.byId("dojoxGlobalResizeHelper");
if(!this._resizeHelper){
this._resizeHelper=new dojox.layout._ResizeHelper({id:"dojoxGlobalResizeHelper"}).placeAt(dojo.body());
dojo.addClass(this._resizeHelper.domNode,this.activeResizeClass);
}
}else{
this.animateSizing=false;
}
if(!this.minSize){
this.minSize={w:this.minWidth,h:this.minHeight};
}
if(this.constrainMax){
this.maxSize={w:this.maxWidth,h:this.maxHeight};
}
this._resizeX=this._resizeY=false;
var _1=dojo.partial(dojo.addClass,this.resizeHandle);
switch(this.resizeAxis.toLowerCase()){
case "xy":
this._resizeX=this._resizeY=true;
_1("dojoxResizeNW");
break;
case "x":
this._resizeX=true;
_1("dojoxResizeW");
break;
case "y":
this._resizeY=true;
_1("dojoxResizeN");
break;
}
},_beginSizing:function(e){
if(this._isSizing){
return false;
}
this.targetWidget=dijit.byId(this.targetId);
this.targetDomNode=this.targetWidget?this.targetWidget.domNode:dojo.byId(this.targetId);
if(this.targetContainer){
this.targetDomNode=this.targetContainer;
}
if(!this.targetDomNode){
return false;
}
if(!this.activeResize){
var c=dojo.coords(this.targetDomNode,true);
this._resizeHelper.resize({l:c.x,t:c.y,w:c.w,h:c.h});
this._resizeHelper.show();
}
this._isSizing=true;
this.startPoint={x:e.clientX,y:e.clientY};
var mb=this.targetWidget?dojo.marginBox(this.targetDomNode):dojo.contentBox(this.targetDomNode);
this.startSize={w:mb.w,h:mb.h};
if(this.fixedAspect){
var _5,_6;
if(mb.w>mb.h){
_5="w";
_6=mb.w/mb.h;
}else{
_5="h";
_6=mb.h/mb.w;
}
this._aspect={prop:_5};
this._aspect[_5]=_6;
}
this._pconnects=[];
this._pconnects.push(dojo.connect(dojo.doc,"onmousemove",this,"_updateSizing"));
this._pconnects.push(dojo.connect(dojo.doc,"onmouseup",this,"_endSizing"));
dojo.stopEvent(e);
},_updateSizing:function(e){
if(this.activeResize){
this._changeSizing(e);
}else{
var _8=this._getNewCoords(e);
if(_8===false){
return;
}
this._resizeHelper.resize(_8);
}
e.preventDefault();
},_getNewCoords:function(e){
try{
if(!e.clientX||!e.clientY){
return false;
}
}
catch(e){
return false;
}
this._activeResizeLastEvent=e;
var dx=this.startPoint.x-e.clientX,dy=this.startPoint.y-e.clientY,_c=this.startSize.w-(this._resizeX?dx:0),_d=this.startSize.h-(this._resizeY?dy:0);
return this._checkConstraints(_c,_d);
},_checkConstraints:function(_e,_f){
if(this.minSize){
var tm=this.minSize;
if(_e<tm.w){
_e=tm.w;
}
if(_f<tm.h){
_f=tm.h;
}
}
if(this.constrainMax&&this.maxSize){
var ms=this.maxSize;
if(_e>ms.w){
_e=ms.w;
}
if(_f>ms.h){
_f=ms.h;
}
}
if(this.fixedAspect){
var ta=this._aspect[this._aspect.prop];
if(_e<_f){
_f=_e*ta;
}else{
if(_f<_e){
_e=_f*ta;
}
}
}
return {w:_e,h:_f};
},_changeSizing:function(e){
var tmp=this._getNewCoords(e);
if(tmp===false){
return;
}
if(this.targetWidget&&dojo.isFunction(this.targetWidget.resize)){
this.targetWidget.resize(tmp);
}else{
if(this.animateSizing){
var _15=dojo.fx[this.animateMethod]([dojo.animateProperty({node:this.targetDomNode,properties:{width:{start:this.startSize.w,end:tmp.w,unit:"px"}},duration:this.animateDuration}),dojo.animateProperty({node:this.targetDomNode,properties:{height:{start:this.startSize.h,end:tmp.h,unit:"px"}},duration:this.animateDuration})]);
_15.play();
}else{
dojo.style(this.targetDomNode,{width:tmp.w+"px",height:tmp.h+"px"});
}
}
if(this.intermediateChanges){
this.onResize(e);
}
},_endSizing:function(e){
dojo.forEach(this._pconnects,dojo.disconnect);
if(!this.activeResize){
this._resizeHelper.hide();
this._changeSizing(e);
}
this._isSizing=false;
this.onResize(e);
},onResize:function(e){
}});
dojo.declare("dojox.layout._ResizeHelper",dijit._Widget,{show:function(){
dojo.fadeIn({node:this.domNode,duration:120,beforeBegin:dojo.partial(dojo.style,this.domNode,"display","")}).play();
},hide:function(){
dojo.fadeOut({node:this.domNode,duration:250,onEnd:dojo.partial(dojo.style,this.domNode,"display","none")}).play();
},resize:function(dim){
dojo.marginBox(this.domNode,dim);
}});
}
