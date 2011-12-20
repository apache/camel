/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.contrib.dijit"]){
dojo._hasResource["dojox.dtl.contrib.dijit"]=true;
dojo.provide("dojox.dtl.contrib.dijit");
dojo.require("dojox.dtl.dom");
dojo.require("dojo.parser");
(function(){
var dd=dojox.dtl;
var _2=dd.contrib.dijit;
_2.AttachNode=dojo.extend(function(_3,_4){
this._keys=_3;
this._object=_4;
},{render:function(_5,_6){
if(!this._rendered){
this._rendered=true;
for(var i=0,_8;_8=this._keys[i];i++){
_5.getThis()[_8]=this._object||_6.getParent();
}
}
return _6;
},unrender:function(_9,_a){
if(this._rendered){
this._rendered=false;
for(var i=0,_c;_c=this._keys[i];i++){
if(_9.getThis()[_c]===(this._object||_a.getParent())){
delete _9.getThis()[_c];
}
}
}
return _a;
},clone:function(_d){
return new this.constructor(this._keys,this._object);
}});
_2.EventNode=dojo.extend(function(_e,_f){
this._command=_e;
var _10,_11=_e.split(/\s*,\s*/);
var _12=dojo.trim;
var _13=[];
var fns=[];
while(_10=_11.pop()){
if(_10){
var fn=null;
if(_10.indexOf(":")!=-1){
var _16=_10.split(":");
_10=_12(_16[0]);
fn=_12(_16.slice(1).join(":"));
}else{
_10=_12(_10);
}
if(!fn){
fn=_10;
}
_13.push(_10);
fns.push(fn);
}
}
this._types=_13;
this._fns=fns;
this._object=_f;
this._rendered=[];
},{_clear:false,render:function(_17,_18){
for(var i=0,_1a;_1a=this._types[i];i++){
if(!this._clear&&!this._object){
_18.getParent()[_1a]=null;
}
var fn=this._fns[i];
var _1c;
if(fn.indexOf(" ")!=-1){
if(this._rendered[i]){
dojo.disconnect(this._rendered[i]);
this._rendered[i]=false;
}
_1c=dojo.map(fn.split(" ").slice(1),function(_1d){
return new dd._Filter(_1d).resolve(_17);
});
fn=fn.split(" ",2)[0];
}
if(!this._rendered[i]){
if(!this._object){
this._rendered[i]=_18.addEvent(_17,_1a,fn,_1c);
}else{
this._rendered[i]=dojo.connect(this._object,_1a,_17.getThis(),fn);
}
}
}
this._clear=true;
return _18;
},unrender:function(_1e,_1f){
while(this._rendered.length){
dojo.disconnect(this._rendered.pop());
}
return _1f;
},clone:function(){
return new this.constructor(this._command,this._object);
}});
function _20(n1){
var n2=n1.cloneNode(true);
if(dojo.isIE){
dojo.query("script",n2).forEach("item.text = this[index].text;",dojo.query("script",n1));
}
return n2;
};
_2.DojoTypeNode=dojo.extend(function(_23,_24){
this._node=_23;
this._parsed=_24;
var _25=_23.getAttribute("dojoAttachEvent");
if(_25){
this._events=new _2.EventNode(dojo.trim(_25));
}
var _26=_23.getAttribute("dojoAttachPoint");
if(_26){
this._attach=new _2.AttachNode(dojo.trim(_26).split(/\s*,\s*/));
}
if(!_24){
this._dijit=dojo.parser.instantiate([_20(_23)])[0];
}else{
_23=_20(_23);
var old=_2.widgetsInTemplate;
_2.widgetsInTemplate=false;
this._template=new dd.DomTemplate(_23);
_2.widgetsInTemplate=old;
}
},{render:function(_28,_29){
if(this._parsed){
var _2a=new dd.DomBuffer();
this._template.render(_28,_2a);
var _2b=_20(_2a.getRootNode());
var div=document.createElement("div");
div.appendChild(_2b);
var _2d=div.innerHTML;
div.removeChild(_2b);
if(_2d!=this._rendered){
this._rendered=_2d;
if(this._dijit){
this._dijit.destroyRecursive();
}
this._dijit=dojo.parser.instantiate([_2b])[0];
}
}
var _2e=this._dijit.domNode;
if(this._events){
this._events._object=this._dijit;
this._events.render(_28,_29);
}
if(this._attach){
this._attach._object=this._dijit;
this._attach.render(_28,_29);
}
return _29.concat(_2e);
},unrender:function(_2f,_30){
return _30.remove(this._dijit.domNode);
},clone:function(){
return new this.constructor(this._node,this._parsed);
}});
dojo.mixin(_2,{widgetsInTemplate:true,dojoAttachPoint:function(_31,_32){
return new _2.AttachNode(_32.contents.slice(16).split(/\s*,\s*/));
},dojoAttachEvent:function(_33,_34){
return new _2.EventNode(_34.contents.slice(16));
},dojoType:function(_35,_36){
if(_2.widgetsInTemplate){
var _37=_35.swallowNode();
var _38=false;
if(_36.contents.slice(-7)==" parsed"){
_38=true;
_37.setAttribute("dojoType",_36.contents.slice(0,-7));
}
return new _2.DojoTypeNode(_37,_38);
}
return dd._noOpNode;
},on:function(_39,_3a){
var _3b=_3a.contents.split();
return new _2.EventNode(_3b[0]+":"+_3b.slice(1).join(" "));
}});
dd.register.tags("dojox.dtl.contrib",{"dijit":["attr:dojoType","attr:dojoAttachPoint",["attr:attach","dojoAttachPoint"],"attr:dojoAttachEvent",[/(attr:)?on(click|key(up))/i,"on"]]});
})();
}
