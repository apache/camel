/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.AlwaysShowToolbar"]){
dojo._hasResource["dijit._editor.plugins.AlwaysShowToolbar"]=true;
dojo.provide("dijit._editor.plugins.AlwaysShowToolbar");
dojo.declare("dijit._editor.plugins.AlwaysShowToolbar",dijit._editor._Plugin,{_handleScroll:true,setEditor:function(e){
if(!e.iframe){

return;
}
this.editor=e;
e.onLoadDeferred.addCallback(dojo.hitch(this,this.enable));
},enable:function(d){
this._updateHeight();
this.connect(window,"onscroll","globalOnScrollHandler");
this.connect(this.editor,"onNormalizedDisplayChanged","_updateHeight");
return d;
},_updateHeight:function(){
var e=this.editor;
if(!e.isLoaded){
return;
}
if(e.height){
return;
}
var _4=dojo.marginBox(e.editNode).h;
if(dojo.isOpera){
_4=e.editNode.scrollHeight;
}
if(!_4){
_4=dojo.marginBox(e.document.body).h;
}
if(_4==0){

return;
}
if(dojo.isIE<=7&&this.editor.minHeight){
var _5=parseInt(this.editor.minHeight);
if(_4<_5){
_4=_5;
}
}
if(_4!=this._lastHeight){
this._lastHeight=_4;
dojo.marginBox(e.iframe,{h:this._lastHeight});
}
},_lastHeight:0,globalOnScrollHandler:function(){
var _6=dojo.isIE<7;
if(!this._handleScroll){
return;
}
var _7=this.editor.toolbar.domNode;
var db=dojo.body;
if(!this._scrollSetUp){
this._scrollSetUp=true;
this._scrollThreshold=dojo._abs(_7,true).y;
}
var _9=dojo._docScroll().y;
var s=_7.style;
if(_9>this._scrollThreshold&&_9<this._scrollThreshold+this._lastHeight){
if(!this._fixEnabled){
var _b=dojo.marginBox(_7);
this.editor.iframe.style.marginTop=_b.h+"px";
if(_6){
s.left=dojo._abs(_7).x;
if(_7.previousSibling){
this._IEOriginalPos=["after",_7.previousSibling];
}else{
if(_7.nextSibling){
this._IEOriginalPos=["before",_7.nextSibling];
}else{
this._IEOriginalPos=["last",_7.parentNode];
}
}
dojo.body().appendChild(_7);
dojo.addClass(_7,"dijitIEFixedToolbar");
}else{
s.position="fixed";
s.top="0px";
}
dojo.marginBox(_7,{w:_b.w});
s.zIndex=2000;
this._fixEnabled=true;
}
var _c=(this.height)?parseInt(this.editor.height):this.editor._lastHeight;
s.display=(_9>this._scrollThreshold+_c)?"none":"";
}else{
if(this._fixEnabled){
this.editor.iframe.style.marginTop="";
s.position="";
s.top="";
s.zIndex="";
s.display="";
if(_6){
s.left="";
dojo.removeClass(_7,"dijitIEFixedToolbar");
if(this._IEOriginalPos){
dojo.place(_7,this._IEOriginalPos[1],this._IEOriginalPos[0]);
this._IEOriginalPos=null;
}else{
dojo.place(_7,this.editor.iframe,"before");
}
}
s.width="";
this._fixEnabled=false;
}
}
},destroy:function(){
this._IEOriginalPos=null;
this._handleScroll=false;
dojo.forEach(this._connects,dojo.disconnect);
if(dojo.isIE<7){
dojo.removeClass(this.editor.toolbar.domNode,"dijitIEFixedToolbar");
}
}});
}
