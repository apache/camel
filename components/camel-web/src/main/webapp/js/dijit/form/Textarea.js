/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.Textarea"]){
dojo._hasResource["dijit.form.Textarea"]=true;
dojo.provide("dijit.form.Textarea");
dojo.require("dijit.form.SimpleTextarea");
dojo.declare("dijit.form.Textarea",dijit.form.SimpleTextarea,{cols:"",_previousNewlines:0,_strictMode:(dojo.doc.compatMode!="BackCompat"),_getHeight:function(_1){
var _2=_1.scrollHeight;
if(dojo.isIE){
_2+=_1.offsetHeight-_1.clientHeight-((dojo.isIE<8&&this._strictMode)?dojo._getPadBorderExtents(_1).h:0);
}else{
if(dojo.isMoz){
_2+=_1.offsetHeight-_1.clientHeight;
}else{
_2+=dojo._getPadBorderExtents(_1).h;
}
}
return _2;
},_onInput:function(){
this.inherited(arguments);
if(this._busyResizing){
return;
}
this._busyResizing=true;
var _3=this.domNode;
_3.scrollTop=0;
var _4=parseFloat(dojo.getComputedStyle(_3).height);
var _5=this._getHeight(_3);
if(_5>0&&_3.style.height!=_5){
_3.style.maxHeight=_3.style.height=_5+"px";
}
this._busyResizing=false;
if(dojo.isMoz||dojo.isWebKit){
var _6=(_3.value.match(/\n/g)||[]).length;
if(_6<this._previousNewlines){
this._shrink();
}
this._previousNewlines=_6;
}
},_busyResizing:false,_shrink:function(){
if((dojo.isMoz||dojo.isSafari)&&!this._busyResizing){
this._busyResizing=true;
var _7=this.domNode;
var _8=false;
if(_7.value==""){
_7.value=" ";
_8=true;
}
var _9=this._getHeight(_7);
if(_9>0){
var _a=_7.scrollHeight;
var _b=-1;
var _c=dojo.getComputedStyle(_7).paddingBottom;
var _d=dojo._getPadExtents(_7);
var _e=_d.h-_d.t;
_7.style.maxHeight=_9+"px";
while(_b!=_a){
_b=_a;
_e+=16;
_7.style.paddingBottom=_e+"px";
_7.scrollTop=0;
_a=_7.scrollHeight;
_9-=_b-_a;
}
_7.style.paddingBottom=_c;
_7.style.maxHeight=_7.style.height=_9+"px";
}
if(_8){
_7.value="";
}
this._busyResizing=false;
}
},resize:function(){
this._onInput();
this._shrink();
},_setValueAttr:function(){
this.inherited(arguments);
this.resize();
},postCreate:function(){
this.inherited(arguments);
dojo.style(this.domNode,{overflowY:"hidden",overflowX:"auto",boxSizing:"border-box",MsBoxSizing:"border-box",WebkitBoxSizing:"border-box",MozBoxSizing:"border-box"});
this.connect(this.domNode,"onscroll",this._onInput);
this.connect(this.domNode,"onresize",this._onInput);
setTimeout(dojo.hitch(this,"resize"),0);
}});
}
