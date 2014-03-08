/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.storage.FlashStorageProvider"]){
dojo._hasResource["dojox.storage.FlashStorageProvider"]=true;
dojo.provide("dojox.storage.FlashStorageProvider");
dojo.require("dojox.flash");
dojo.require("dojox.storage.manager");
dojo.require("dojox.storage.Provider");
dojo.declare("dojox.storage.FlashStorageProvider",dojox.storage.Provider,{initialized:false,_available:null,_statusHandler:null,_flashReady:false,_pageReady:false,initialize:function(){
if(dojo.config["disableFlashStorage"]==true){
return;
}
dojox.flash.addLoadedListener(dojo.hitch(this,function(){
this._flashReady=true;
if(this._flashReady&&this._pageReady){
this._loaded();
}
}));
var _1=dojo.moduleUrl("dojox","storage/Storage.swf").toString();
dojox.flash.setSwf(_1,false);
dojo.connect(dojo,"loaded",this,function(){
this._pageReady=true;
if(this._flashReady&&this._pageReady){
this._loaded();
}
});
},setFlushDelay:function(_2){
if(_2===null||typeof _2==="undefined"||isNaN(_2)){
throw new Error("Invalid argunment: "+_2);
}
dojox.flash.comm.setFlushDelay(String(_2));
},getFlushDelay:function(){
return Number(dojox.flash.comm.getFlushDelay());
},flush:function(_3){
if(_3==null||typeof _3=="undefined"){
_3=dojox.storage.DEFAULT_NAMESPACE;
}
dojox.flash.comm.flush(_3);
},isAvailable:function(){
return (this._available=!dojo.config["disableFlashStorage"]);
},put:function(_4,_5,_6,_7){
if(!this.isValidKey(_4)){
throw new Error("Invalid key given: "+_4);
}
if(!_7){
_7=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_7)){
throw new Error("Invalid namespace given: "+_7);
}
this._statusHandler=_6;
if(dojo.isString(_5)){
_5="string:"+_5;
}else{
_5=dojo.toJson(_5);
}
dojox.flash.comm.put(_4,_5,_7);
},putMultiple:function(_8,_9,_a,_b){
if(!this.isValidKeyArray(_8)||!_9 instanceof Array||_8.length!=_9.length){
throw new Error("Invalid arguments: keys = ["+_8+"], values = ["+_9+"]");
}
if(!_b){
_b=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_b)){
throw new Error("Invalid namespace given: "+_b);
}
this._statusHandler=_a;
var _c=_8.join(",");
var _d=[];
for(var i=0;i<_9.length;i++){
if(dojo.isString(_9[i])){
_9[i]="string:"+_9[i];
}else{
_9[i]=dojo.toJson(_9[i]);
}
_d[i]=_9[i].length;
}
var _f=_9.join("");
var _10=_d.join(",");
dojox.flash.comm.putMultiple(_c,_f,_10,_b);
},get:function(key,_12){
if(!this.isValidKey(key)){
throw new Error("Invalid key given: "+key);
}
if(!_12){
_12=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_12)){
throw new Error("Invalid namespace given: "+_12);
}
var _13=dojox.flash.comm.get(key,_12);
if(_13==""){
return null;
}
return this._destringify(_13);
},getMultiple:function(_14,_15){
if(!this.isValidKeyArray(_14)){
throw new ("Invalid key array given: "+_14);
}
if(!_15){
_15=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_15)){
throw new Error("Invalid namespace given: "+_15);
}
var _16=_14.join(",");
var _17=dojox.flash.comm.getMultiple(_16,_15);
var _18=eval("("+_17+")");
for(var i=0;i<_18.length;i++){
_18[i]=(_18[i]=="")?null:this._destringify(_18[i]);
}
return _18;
},_destringify:function(_1a){
if(dojo.isString(_1a)&&(/^string:/.test(_1a))){
_1a=_1a.substring("string:".length);
}else{
_1a=dojo.fromJson(_1a);
}
return _1a;
},getKeys:function(_1b){
if(!_1b){
_1b=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_1b)){
throw new Error("Invalid namespace given: "+_1b);
}
var _1c=dojox.flash.comm.getKeys(_1b);
if(_1c==null||_1c=="null"){
_1c="";
}
_1c=_1c.split(",");
_1c.sort();
return _1c;
},getNamespaces:function(){
var _1d=dojox.flash.comm.getNamespaces();
if(_1d==null||_1d=="null"){
_1d=dojox.storage.DEFAULT_NAMESPACE;
}
_1d=_1d.split(",");
_1d.sort();
return _1d;
},clear:function(_1e){
if(!_1e){
_1e=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_1e)){
throw new Error("Invalid namespace given: "+_1e);
}
dojox.flash.comm.clear(_1e);
},remove:function(key,_20){
if(!_20){
_20=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_20)){
throw new Error("Invalid namespace given: "+_20);
}
dojox.flash.comm.remove(key,_20);
},removeMultiple:function(_21,_22){
if(!this.isValidKeyArray(_21)){
dojo.raise("Invalid key array given: "+_21);
}
if(!_22){
_22=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_22)){
throw new Error("Invalid namespace given: "+_22);
}
var _23=_21.join(",");
dojox.flash.comm.removeMultiple(_23,_22);
},isPermanent:function(){
return true;
},getMaximumSize:function(){
return dojox.storage.SIZE_NO_LIMIT;
},hasSettingsUI:function(){
return true;
},showSettingsUI:function(){
dojox.flash.comm.showSettings();
dojox.flash.obj.setVisible(true);
dojox.flash.obj.center();
},hideSettingsUI:function(){
dojox.flash.obj.setVisible(false);
if(dojo.isFunction(dojox.storage.onHideSettingsUI)){
dojox.storage.onHideSettingsUI.call(null);
}
},getResourceList:function(){
return [];
},_loaded:function(){
this._allNamespaces=this.getNamespaces();
this.initialized=true;
dojox.storage.manager.loaded();
},_onStatus:function(_24,key,_26){
var ds=dojox.storage;
var dfo=dojox.flash.obj;
if(_24==ds.PENDING){
dfo.center();
dfo.setVisible(true);
}else{
dfo.setVisible(false);
}
if(ds._statusHandler){
ds._statusHandler.call(null,_24,key,null,_26);
}
}});
dojox.storage.manager.register("dojox.storage.FlashStorageProvider",new dojox.storage.FlashStorageProvider());
}
