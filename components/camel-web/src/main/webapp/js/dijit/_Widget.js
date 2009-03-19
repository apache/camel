/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._Widget"]){
dojo._hasResource["dijit._Widget"]=true;
dojo.provide("dijit._Widget");
dojo.require("dijit._base");
dojo.connect(dojo,"connect",function(_1,_2){
if(_1&&dojo.isFunction(_1._onConnect)){
_1._onConnect(_2);
}
});
dijit._connectOnUseEventHandler=function(_3){
};
(function(){
var _4={};
var _5=function(dc){
if(!_4[dc]){
var r=[];
var _8;
var _9=dojo.getObject(dc).prototype;
for(var _a in _9){
if(dojo.isFunction(_9[_a])&&(_8=_a.match(/^_set([a-zA-Z]*)Attr$/))&&_8[1]){
r.push(_8[1].charAt(0).toLowerCase()+_8[1].substr(1));
}
}
_4[dc]=r;
}
return _4[dc]||[];
};
dojo.declare("dijit._Widget",null,{id:"",lang:"",dir:"","class":"",style:"",title:"",srcNodeRef:null,domNode:null,containerNode:null,attributeMap:{id:"",dir:"",lang:"","class":"",style:"",title:""},_deferredConnects:{onClick:"",onDblClick:"",onKeyDown:"",onKeyPress:"",onKeyUp:"",onMouseMove:"",onMouseDown:"",onMouseOut:"",onMouseOver:"",onMouseLeave:"",onMouseEnter:"",onMouseUp:""},onClick:dijit._connectOnUseEventHandler,onDblClick:dijit._connectOnUseEventHandler,onKeyDown:dijit._connectOnUseEventHandler,onKeyPress:dijit._connectOnUseEventHandler,onKeyUp:dijit._connectOnUseEventHandler,onMouseDown:dijit._connectOnUseEventHandler,onMouseMove:dijit._connectOnUseEventHandler,onMouseOut:dijit._connectOnUseEventHandler,onMouseOver:dijit._connectOnUseEventHandler,onMouseLeave:dijit._connectOnUseEventHandler,onMouseEnter:dijit._connectOnUseEventHandler,onMouseUp:dijit._connectOnUseEventHandler,_blankGif:(dojo.config.blankGif||dojo.moduleUrl("dojo","resources/blank.gif")),postscript:function(_b,_c){
this.create(_b,_c);
},create:function(_d,_e){
this.srcNodeRef=dojo.byId(_e);
this._connects=[];
this._deferredConnects=dojo.clone(this._deferredConnects);
for(var _f in this.attributeMap){
delete this._deferredConnects[_f];
}
for(_f in this._deferredConnects){
if(this[_f]!==dijit._connectOnUseEventHandler){
delete this._deferredConnects[_f];
}
}
if(this.srcNodeRef&&(typeof this.srcNodeRef.id=="string")){
this.id=this.srcNodeRef.id;
}
if(_d){
this.params=_d;
dojo.mixin(this,_d);
}
this.postMixInProperties();
if(!this.id){
this.id=dijit.getUniqueId(this.declaredClass.replace(/\./g,"_"));
}
dijit.registry.add(this);
this.buildRendering();
if(this.domNode){
this._applyAttributes();
var _10=this.srcNodeRef;
if(_10&&_10.parentNode){
_10.parentNode.replaceChild(this.domNode,_10);
}
for(_f in this.params){
this._onConnect(_f);
}
}
if(this.domNode){
this.domNode.setAttribute("widgetId",this.id);
}
this.postCreate();
if(this.srcNodeRef&&!this.srcNodeRef.parentNode){
delete this.srcNodeRef;
}
this._created=true;
},_applyAttributes:function(){
var _11=function(_12,_13){
if((_13.params&&_12 in _13.params)||_13[_12]){
_13.attr(_12,_13[_12]);
}
};
for(var _14 in this.attributeMap){
_11(_14,this);
}
dojo.forEach(_5(this.declaredClass),function(a){
if(!(a in this.attributeMap)){
_11(a,this);
}
},this);
},postMixInProperties:function(){
},buildRendering:function(){
this.domNode=this.srcNodeRef||dojo.create("div");
},postCreate:function(){
},startup:function(){
this._started=true;
},destroyRecursive:function(_16){
this.destroyDescendants(_16);
this.destroy(_16);
},destroy:function(_17){
this.uninitialize();
dojo.forEach(this._connects,function(_18){
dojo.forEach(_18,dojo.disconnect);
});
dojo.forEach(this._supportingWidgets||[],function(w){
if(w.destroy){
w.destroy();
}
});
this.destroyRendering(_17);
dijit.registry.remove(this.id);
},destroyRendering:function(_1a){
if(this.bgIframe){
this.bgIframe.destroy(_1a);
delete this.bgIframe;
}
if(this.domNode){
if(!_1a){
dojo.destroy(this.domNode);
}
delete this.domNode;
}
if(this.srcNodeRef){
if(!_1a){
dojo.destroy(this.srcNodeRef);
}
delete this.srcNodeRef;
}
},destroyDescendants:function(_1b){
dojo.forEach(this.getDescendants(true),function(_1c){
if(_1c.destroyRecursive){
_1c.destroyRecursive(_1b);
}
});
},uninitialize:function(){
return false;
},onFocus:function(){
},onBlur:function(){
},_onFocus:function(e){
this.onFocus();
},_onBlur:function(){
this.onBlur();
},_onConnect:function(_1e){
if(_1e in this._deferredConnects){
var _1f=this[this._deferredConnects[_1e]||"domNode"];
this.connect(_1f,_1e.toLowerCase(),this[_1e]);
delete this._deferredConnects[_1e];
}
},_setClassAttr:function(_20){
var _21=this[this.attributeMap["class"]||"domNode"];
dojo.removeClass(_21,this["class"]);
this["class"]=_20;
dojo.addClass(_21,_20);
},_setStyleAttr:function(_22){
var _23=this[this.attributeMap["style"]||"domNode"];
if(dojo.isObject(_22)){
dojo.style(_23,_22);
}else{
if(_23.style.cssText){
_23.style.cssText+="; "+_22;
}else{
_23.style.cssText=_22;
}
}
this["style"]=_22;
},setAttribute:function(_24,_25){
dojo.deprecated(this.declaredClass+"::setAttribute() is deprecated. Use attr() instead.","","2.0");
this.attr(_24,_25);
},_attrToDom:function(_26,_27){
var _28=this.attributeMap[_26];
dojo.forEach(dojo.isArray(_28)?_28:[_28],function(_29){
var _2a=this[_29.node||_29||"domNode"];
var _2b=_29.type||"attribute";
switch(_2b){
case "attribute":
if(dojo.isFunction(_27)){
_27=dojo.hitch(this,_27);
}
if(/^on[A-Z][a-zA-Z]*$/.test(_26)){
_26=_26.toLowerCase();
}
dojo.attr(_2a,_26,_27);
break;
case "innerHTML":
_2a.innerHTML=_27;
break;
case "class":
dojo.removeClass(_2a,this[_26]);
dojo.addClass(_2a,_27);
break;
}
},this);
this[_26]=_27;
},attr:function(_2c,_2d){
var _2e=arguments.length;
if(_2e==1&&!dojo.isString(_2c)){
for(var x in _2c){
this.attr(x,_2c[x]);
}
return this;
}
var _30=this._getAttrNames(_2c);
if(_2e==2){
if(this[_30.s]){
return this[_30.s](_2d)||this;
}else{
if(_2c in this.attributeMap){
this._attrToDom(_2c,_2d);
}
this[_2c]=_2d;
}
return this;
}else{
if(this[_30.g]){
return this[_30.g]();
}else{
return this[_2c];
}
}
},_attrPairNames:{},_getAttrNames:function(_31){
var apn=this._attrPairNames;
if(apn[_31]){
return apn[_31];
}
var uc=_31.charAt(0).toUpperCase()+_31.substr(1);
return apn[_31]={n:_31+"Node",s:"_set"+uc+"Attr",g:"_get"+uc+"Attr"};
},toString:function(){
return "[Widget "+this.declaredClass+", "+(this.id||"NO ID")+"]";
},getDescendants:function(_34,_35){
_35=_35||[];
if(this.containerNode){
this._getDescendantsHelper(_34,_35,this.containerNode);
}
return _35;
},_getDescendantsHelper:function(_36,_37,_38){
var _39=dojo.isIE?_38.children:_38.childNodes,i=0,_3b;
while(_3b=_39[i++]){
if(_3b.nodeType!=1){
continue;
}
var _3c=_3b.getAttribute("widgetId");
if(_3c){
var _3d=dijit.byId(_3c);
_37.push(_3d);
if(!_36){
_3d.getDescendants(_36,_37);
}
}else{
this._getDescendantsHelper(_36,_37,_3b);
}
}
},nodesWithKeyClick:["input","button"],connect:function(obj,_3f,_40){
var d=dojo;
var dco=d.hitch(d,"connect",obj);
var _43=[];
if(_3f=="ondijitclick"){
if(!this.nodesWithKeyClick[obj.nodeName]){
var m=d.hitch(this,_40);
_43.push(dco("onkeydown",this,function(e){
if(!d.isFF&&e.keyCode==d.keys.ENTER&&!e.ctrlKey&&!e.shiftKey&&!e.altKey&&!e.metaKey){
return m(e);
}else{
if(e.keyCode==d.keys.SPACE){
d.stopEvent(e);
}
}
}),dco("onkeyup",this,function(e){
if(e.keyCode==d.keys.SPACE&&!e.ctrlKey&&!e.shiftKey&&!e.altKey&&!e.metaKey){
return m(e);
}
}));
if(d.isFF){
_43.push(dco("onkeypress",this,function(e){
if(e.keyCode==d.keys.ENTER&&!e.ctrlKey&&!e.shiftKey&&!e.altKey&&!e.metaKey){
return m(e);
}
}));
}
}
_3f="onclick";
}
_43.push(dco(_3f,this,_40));
this._connects.push(_43);
return _43;
},disconnect:function(_48){
for(var i=0;i<this._connects.length;i++){
if(this._connects[i]==_48){
dojo.forEach(_48,dojo.disconnect);
this._connects.splice(i,1);
return;
}
}
},isLeftToRight:function(){
return dojo._isBodyLtr();
},isFocusable:function(){
return this.focus&&(dojo.style(this.domNode,"display")!="none");
},placeAt:function(_4a,_4b){
if(_4a["declaredClass"]&&_4a["addChild"]){
_4a.addChild(this,_4b);
}else{
dojo.place(this.domNode,_4a,_4b);
}
return this;
}});
})();
}
