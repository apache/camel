/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.storage.WhatWGStorageProvider"]){
dojo._hasResource["dojox.storage.WhatWGStorageProvider"]=true;
dojo.provide("dojox.storage.WhatWGStorageProvider");
dojo.require("dojox.storage.Provider");
dojo.require("dojox.storage.manager");
dojo.declare("dojox.storage.WhatWGStorageProvider",[dojox.storage.Provider],{initialized:false,_domain:null,_available:null,_statusHandler:null,_allNamespaces:null,_storageEventListener:null,initialize:function(){
if(dojo.config["disableWhatWGStorage"]==true){
return;
}
this._domain=this._getDomain();
this.initialized=true;
dojox.storage.manager.loaded();
},isAvailable:function(){
try{
var _1=globalStorage[this._getDomain()];
}
catch(e){
this._available=false;
return this._available;
}
this._available=true;
return this._available;
},put:function(_2,_3,_4,_5){
if(this.isValidKey(_2)==false){
throw new Error("Invalid key given: "+_2);
}
_5=_5||this.DEFAULT_NAMESPACE;
_2=this.getFullKey(_2,_5);
this._statusHandler=_4;
if(dojo.isString(_3)){
_3="string:"+_3;
}else{
_3=dojo.toJson(_3);
}
var _6=dojo.hitch(this,function(_7){
window.removeEventListener("storage",_6,false);
if(_4){
_4.call(null,this.SUCCESS,_2,null,_5);
}
});
window.addEventListener("storage",_6,false);
try{
var _8=globalStorage[this._domain];
_8.setItem(_2,_3);
}
catch(e){
this._statusHandler.call(null,this.FAILED,_2,e.toString(),_5);
}
},get:function(_9,_a){
if(this.isValidKey(_9)==false){
throw new Error("Invalid key given: "+_9);
}
_a=_a||this.DEFAULT_NAMESPACE;
_9=this.getFullKey(_9,_a);
var _b=globalStorage[this._domain];
var _c=_b.getItem(_9);
if(_c==null||_c==""){
return null;
}
_c=_c.value;
if(dojo.isString(_c)&&(/^string:/.test(_c))){
_c=_c.substring("string:".length);
}else{
_c=dojo.fromJson(_c);
}
return _c;
},getNamespaces:function(){
var _d=[this.DEFAULT_NAMESPACE];
var _e={};
var _f=globalStorage[this._domain];
var _10=/^__([^_]*)_/;
for(var i=0;i<_f.length;i++){
var _12=_f.key(i);
if(_10.test(_12)==true){
var _13=_12.match(_10)[1];
if(typeof _e[_13]=="undefined"){
_e[_13]=true;
_d.push(_13);
}
}
}
return _d;
},getKeys:function(_14){
_14=_14||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_14)==false){
throw new Error("Invalid namespace given: "+_14);
}
var _15;
if(_14==this.DEFAULT_NAMESPACE){
_15=new RegExp("^([^_]{2}.*)$");
}else{
_15=new RegExp("^__"+_14+"_(.*)$");
}
var _16=globalStorage[this._domain];
var _17=[];
for(var i=0;i<_16.length;i++){
var _19=_16.key(i);
if(_15.test(_19)==true){
_19=_19.match(_15)[1];
_17.push(_19);
}
}
return _17;
},clear:function(_1a){
_1a=_1a||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_1a)==false){
throw new Error("Invalid namespace given: "+_1a);
}
var _1b;
if(_1a==this.DEFAULT_NAMESPACE){
_1b=new RegExp("^[^_]{2}");
}else{
_1b=new RegExp("^__"+_1a+"_");
}
var _1c=globalStorage[this._domain];
var _1d=[];
for(var i=0;i<_1c.length;i++){
if(_1b.test(_1c.key(i))==true){
_1d[_1d.length]=_1c.key(i);
}
}
dojo.forEach(_1d,dojo.hitch(_1c,"removeItem"));
},remove:function(key,_20){
key=this.getFullKey(key,_20);
var _21=globalStorage[this._domain];
_21.removeItem(key);
},isPermanent:function(){
return true;
},getMaximumSize:function(){
return this.SIZE_NO_LIMIT;
},hasSettingsUI:function(){
return false;
},showSettingsUI:function(){
throw new Error(this.declaredClass+" does not support a storage settings user-interface");
},hideSettingsUI:function(){
throw new Error(this.declaredClass+" does not support a storage settings user-interface");
},getFullKey:function(key,_23){
_23=_23||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_23)==false){
throw new Error("Invalid namespace given: "+_23);
}
if(_23==this.DEFAULT_NAMESPACE){
return key;
}else{
return "__"+_23+"_"+key;
}
},_getDomain:function(){
return ((location.hostname=="localhost"&&dojo.isFF&&dojo.isFF<3)?"localhost.localdomain":location.hostname);
}});
dojox.storage.manager.register("dojox.storage.WhatWGStorageProvider",new dojox.storage.WhatWGStorageProvider());
}
