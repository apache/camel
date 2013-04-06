/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.storage.AirFileStorageProvider"]){
dojo._hasResource["dojox.storage.AirFileStorageProvider"]=true;
dojo.provide("dojox.storage.AirFileStorageProvider");
dojo.require("dojox.storage.manager");
dojo.require("dojox.storage.Provider");
if(dojo.isAIR){
(function(){
if(!_1){
var _1={};
}
_1.File=window.runtime.flash.filesystem.File;
_1.FileStream=window.runtime.flash.filesystem.FileStream;
_1.FileMode=window.runtime.flash.filesystem.FileMode;
dojo.declare("dojox.storage.AirFileStorageProvider",[dojox.storage.Provider],{initialized:false,_storagePath:"__DOJO_STORAGE/",initialize:function(){
this.initialized=false;
try{
var _2=_1.File.applicationStorageDirectory.resolvePath(this._storagePath);
if(!_2.exists){
_2.createDirectory();
}
this.initialized=true;
}
catch(e){

}
dojox.storage.manager.loaded();
},isAvailable:function(){
return true;
},put:function(_3,_4,_5,_6){
if(this.isValidKey(_3)==false){
throw new Error("Invalid key given: "+_3);
}
_6=_6||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_6)==false){
throw new Error("Invalid namespace given: "+_6);
}
try{
this.remove(_3,_6);
var _7=_1.File.applicationStorageDirectory.resolvePath(this._storagePath+_6);
if(!_7.exists){
_7.createDirectory();
}
var _8=_7.resolvePath(_3);
var _9=new _1.FileStream();
_9.open(_8,_1.FileMode.WRITE);
_9.writeObject(_4);
_9.close();
}
catch(e){

_5(this.FAILED,_3,e.toString(),_6);
return;
}
if(_5){
_5(this.SUCCESS,_3,null,_6);
}
},get:function(_a,_b){
if(this.isValidKey(_a)==false){
throw new Error("Invalid key given: "+_a);
}
_b=_b||this.DEFAULT_NAMESPACE;
var _c=null;
var _d=_1.File.applicationStorageDirectory.resolvePath(this._storagePath+_b+"/"+_a);
if(_d.exists&&!_d.isDirectory){
var _e=new _1.FileStream();
_e.open(_d,_1.FileMode.READ);
_c=_e.readObject();
_e.close();
}
return _c;
},getNamespaces:function(){
var _f=[this.DEFAULT_NAMESPACE];
var dir=_1.File.applicationStorageDirectory.resolvePath(this._storagePath);
var _11=dir.getDirectoryListing(),i;
for(i=0;i<_11.length;i++){
if(_11[i].isDirectory&&_11[i].name!=this.DEFAULT_NAMESPACE){
_f.push(_11[i].name);
}
}
return _f;
},getKeys:function(_13){
_13=_13||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_13)==false){
throw new Error("Invalid namespace given: "+_13);
}
var _14=[];
var dir=_1.File.applicationStorageDirectory.resolvePath(this._storagePath+_13);
if(dir.exists&&dir.isDirectory){
var _16=dir.getDirectoryListing(),i;
for(i=0;i<_16.length;i++){
_14.push(_16[i].name);
}
}
return _14;
},clear:function(_18){
if(this.isValidKey(_18)==false){
throw new Error("Invalid namespace given: "+_18);
}
var dir=_1.File.applicationStorageDirectory.resolvePath(this._storagePath+_18);
if(dir.exists&&dir.isDirectory){
dir.deleteDirectory(true);
}
},remove:function(key,_1b){
_1b=_1b||this.DEFAULT_NAMESPACE;
var _1c=_1.File.applicationStorageDirectory.resolvePath(this._storagePath+_1b+"/"+key);
if(_1c.exists&&!_1c.isDirectory){
_1c.deleteFile();
}
},putMultiple:function(_1d,_1e,_1f,_20){
if(this.isValidKeyArray(_1d)===false||!_1e instanceof Array||_1d.length!=_1e.length){
throw new Error("Invalid arguments: keys = ["+_1d+"], values = ["+_1e+"]");
}
if(_20==null||typeof _20=="undefined"){
_20=this.DEFAULT_NAMESPACE;
}
if(this.isValidKey(_20)==false){
throw new Error("Invalid namespace given: "+_20);
}
this._statusHandler=_1f;
try{
for(var i=0;i<_1d.length;i++){
this.put(_1d[i],_1e[i],null,_20);
}
}
catch(e){

if(_1f){
_1f(this.FAILED,_1d,e.toString(),_20);
}
return;
}
if(_1f){
_1f(this.SUCCESS,_1d,null,_20);
}
},getMultiple:function(_22,_23){
if(this.isValidKeyArray(_22)===false){
throw new Error("Invalid key array given: "+_22);
}
if(_23==null||typeof _23=="undefined"){
_23=this.DEFAULT_NAMESPACE;
}
if(this.isValidKey(_23)==false){
throw new Error("Invalid namespace given: "+_23);
}
var _24=[];
for(var i=0;i<_22.length;i++){
_24[i]=this.get(_22[i],_23);
}
return _24;
},removeMultiple:function(_26,_27){
_27=_27||this.DEFAULT_NAMESPACE;
for(var i=0;i<_26.length;i++){
this.remove(_26[i],_27);
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
dojox.storage.manager.register("dojox.storage.AirFileStorageProvider",new dojox.storage.AirFileStorageProvider());
dojox.storage.manager.initialize();
})();
}
}
