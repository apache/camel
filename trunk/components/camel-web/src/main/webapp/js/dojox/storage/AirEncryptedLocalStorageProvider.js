/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.storage.AirEncryptedLocalStorageProvider"]){
dojo._hasResource["dojox.storage.AirEncryptedLocalStorageProvider"]=true;
dojo.provide("dojox.storage.AirEncryptedLocalStorageProvider");
dojo.require("dojox.storage.manager");
dojo.require("dojox.storage.Provider");
if(dojo.isAIR){
(function(){
if(!_1){
var _1={};
}
_1.ByteArray=window.runtime.flash.utils.ByteArray;
_1.EncryptedLocalStore=window.runtime.flash.data.EncryptedLocalStore,dojo.declare("dojox.storage.AirEncryptedLocalStorageProvider",[dojox.storage.Provider],{initialize:function(){
dojox.storage.manager.loaded();
},isAvailable:function(){
return true;
},_getItem:function(_2){
var _3=_1.EncryptedLocalStore.getItem("__dojo_"+_2);
return _3?_3.readUTFBytes(_3.length):"";
},_setItem:function(_4,_5){
var _6=new _1.ByteArray();
_6.writeUTFBytes(_5);
_1.EncryptedLocalStore.setItem("__dojo_"+_4,_6);
},_removeItem:function(_7){
_1.EncryptedLocalStore.removeItem("__dojo_"+_7);
},put:function(_8,_9,_a,_b){
if(this.isValidKey(_8)==false){
throw new Error("Invalid key given: "+_8);
}
_b=_b||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_b)==false){
throw new Error("Invalid namespace given: "+_b);
}
try{
var _c=this._getItem("namespaces")||"|";
if(_c.indexOf("|"+_b+"|")==-1){
this._setItem("namespaces",_c+_b+"|");
}
var _d=this._getItem(_b+"_keys")||"|";
if(_d.indexOf("|"+_8+"|")==-1){
this._setItem(_b+"_keys",_d+_8+"|");
}
this._setItem("_"+_b+"_"+_8,_9);
}
catch(e){

_a(this.FAILED,_8,e.toString(),_b);
return;
}
if(_a){
_a(this.SUCCESS,_8,null,_b);
}
},get:function(_e,_f){
if(this.isValidKey(_e)==false){
throw new Error("Invalid key given: "+_e);
}
_f=_f||this.DEFAULT_NAMESPACE;
return this._getItem("_"+_f+"_"+_e);
},getNamespaces:function(){
var _10=[this.DEFAULT_NAMESPACE];
var _11=(this._getItem("namespaces")||"|").split("|");
for(var i=0;i<_11.length;i++){
if(_11[i].length&&_11[i]!=this.DEFAULT_NAMESPACE){
_10.push(_11[i]);
}
}
return _10;
},getKeys:function(_13){
_13=_13||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_13)==false){
throw new Error("Invalid namespace given: "+_13);
}
var _14=[];
var _15=(this._getItem(_13+"_keys")||"|").split("|");
for(var i=0;i<_15.length;i++){
if(_15[i].length){
_14.push(_15[i]);
}
}
return _14;
},clear:function(_17){
if(this.isValidKey(_17)==false){
throw new Error("Invalid namespace given: "+_17);
}
var _18=this._getItem("namespaces")||"|";
if(_18.indexOf("|"+_17+"|")!=-1){
this._setItem("namespaces",_18.replace("|"+_17+"|","|"));
}
var _19=(this._getItem(_17+"_keys")||"|").split("|");
for(var i=0;i<_19.length;i++){
if(_19[i].length){
this._removeItem(_17+"_"+_19[i]);
}
}
this._removeItem(_17+"_keys");
},remove:function(key,_1c){
_1c=_1c||this.DEFAULT_NAMESPACE;
var _1d=this._getItem(_1c+"_keys")||"|";
if(_1d.indexOf("|"+key+"|")!=-1){
this._setItem(_1c+"_keys",_1d.replace("|"+key+"|","|"));
}
this._removeItem("_"+_1c+"_"+key);
},putMultiple:function(_1e,_1f,_20,_21){
if(this.isValidKeyArray(_1e)===false||!_1f instanceof Array||_1e.length!=_1f.length){
throw new Error("Invalid arguments: keys = ["+_1e+"], values = ["+_1f+"]");
}
if(_21==null||typeof _21=="undefined"){
_21=this.DEFAULT_NAMESPACE;
}
if(this.isValidKey(_21)==false){
throw new Error("Invalid namespace given: "+_21);
}
this._statusHandler=_20;
try{
for(var i=0;i<_1e.length;i++){
this.put(_1e[i],_1f[i],null,_21);
}
}
catch(e){

if(_20){
_20(this.FAILED,_1e,e.toString(),_21);
}
return;
}
if(_20){
_20(this.SUCCESS,_1e,null);
}
},getMultiple:function(_23,_24){
if(this.isValidKeyArray(_23)===false){
throw new Error("Invalid key array given: "+_23);
}
if(_24==null||typeof _24=="undefined"){
_24=this.DEFAULT_NAMESPACE;
}
if(this.isValidKey(_24)==false){
throw new Error("Invalid namespace given: "+_24);
}
var _25=[];
for(var i=0;i<_23.length;i++){
_25[i]=this.get(_23[i],_24);
}
return _25;
},removeMultiple:function(_27,_28){
_28=_28||this.DEFAULT_NAMESPACE;
for(var i=0;i<_27.length;i++){
this.remove(_27[i],_28);
}
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
}});
dojox.storage.manager.register("dojox.storage.AirEncryptedLocalStorageProvider",new dojox.storage.AirEncryptedLocalStorageProvider());
dojox.storage.manager.initialize();
})();
}
}
