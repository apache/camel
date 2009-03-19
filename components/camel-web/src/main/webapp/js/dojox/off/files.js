/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.off.files"]){
dojo._hasResource["dojox.off.files"]=true;
dojo.provide("dojox.off.files");
dojox.off.files={versionURL:"version.js",listOfURLs:[],refreshing:false,_cancelID:null,_error:false,_errorMessages:[],_currentFileIndex:0,_store:null,_doSlurp:false,slurp:function(){
this._doSlurp=true;
},cache:function(_1){
if(dojo.isString(_1)){
var _2=this._trimAnchor(_1+"");
if(!this.isAvailable(_2)){
this.listOfURLs.push(_2);
}
}else{
if(_1 instanceof dojo._Url){
var _2=this._trimAnchor(_1.uri);
if(!this.isAvailable(_2)){
this.listOfURLs.push(_2);
}
}else{
dojo.forEach(_1,function(_3){
_3=this._trimAnchor(_3);
if(!this.isAvailable(_3)){
this.listOfURLs.push(_3);
}
},this);
}
}
},printURLs:function(){

dojo.forEach(this.listOfURLs,function(i){

});
},remove:function(_5){
for(var i=0;i<this.listOfURLs.length;i++){
if(this.listOfURLs[i]==_5){
this.listOfURLs=this.listOfURLs.splice(i,1);
break;
}
}
},isAvailable:function(_7){
for(var i=0;i<this.listOfURLs.length;i++){
if(this.listOfURLs[i]==_7){
return true;
}
}
return false;
},refresh:function(_9){
try{
if(dojo.config.isDebug){
this.printURLs();
}
this.refreshing=true;
if(this.versionURL){
this._getVersionInfo(function(_a,_b,_c){
if(dojo.config.isDebug||!_b||_c||!_a||_a!=_b){
console.warn("Refreshing offline file list");
this._doRefresh(_9,_b);
}else{
console.warn("No need to refresh offline file list");
_9(false,[]);
}
});
}else{
console.warn("Refreshing offline file list");
this._doRefresh(_9);
}
}
catch(e){
this.refreshing=false;
dojox.off.coreOpFailed=true;
dojox.off.enabled=false;
dojox.off.onFrameworkEvent("coreOperationFailed");
}
},abortRefresh:function(){
if(!this.refreshing){
return;
}
this._store.abortCapture(this._cancelID);
this.refreshing=false;
},_slurp:function(){
if(!this._doSlurp){
return;
}
var _d=dojo.hitch(this,function(_e){
if(this._sameLocation(_e)){
this.cache(_e);
}
});
_d(window.location.href);
dojo.query("script").forEach(function(i){
try{
_d(i.getAttribute("src"));
}
catch(exp){
}
});
dojo.query("link").forEach(function(i){
try{
if(!i.getAttribute("rel")||i.getAttribute("rel").toLowerCase()!="stylesheet"){
return;
}
_d(i.getAttribute("href"));
}
catch(exp){
}
});
dojo.query("img").forEach(function(i){
try{
_d(i.getAttribute("src"));
}
catch(exp){
}
});
dojo.query("a").forEach(function(i){
try{
_d(i.getAttribute("href"));
}
catch(exp){
}
});
dojo.forEach(document.styleSheets,function(_13){
try{
if(_13.cssRules){
dojo.forEach(_13.cssRules,function(_14){
var _15=_14.cssText;
if(_15){
var _16=_15.match(/url\(\s*([^\) ]*)\s*\)/i);
if(!_16){
return;
}
for(var i=1;i<_16.length;i++){
_d(_16[i]);
}
}
});
}else{
if(_13.cssText){
var _18;
var _19=_13.cssText.toString();
var _1a=_19.split(/\f|\r|\n/);
for(var i=0;i<_1a.length;i++){
_18=_1a[i].match(/url\(\s*([^\) ]*)\s*\)/i);
if(_18&&_18.length){
_d(_18[1]);
}
}
}
}
}
catch(exp){
}
});
},_sameLocation:function(url){
if(!url){
return false;
}
if(url.length&&url.charAt(0)=="#"){
return false;
}
url=new dojo._Url(url);
if(!url.scheme&&!url.port&&!url.host){
return true;
}
if(!url.scheme&&url.host&&url.port&&window.location.hostname==url.host&&window.location.port==url.port){
return true;
}
if(!url.scheme&&url.host&&!url.port&&window.location.hostname==url.host&&window.location.port==80){
return true;
}
return window.location.protocol==(url.scheme+":")&&window.location.hostname==url.host&&(window.location.port==url.port||!window.location.port&&!url.port);
},_trimAnchor:function(url){
return url.replace(/\#.*$/,"");
},_doRefresh:function(_1e,_1f){
var _20;
try{
_20=google.gears.factory.create("beta.localserver","1.0");
}
catch(exp){
dojo.setObject("google.gears.denied",true);
dojox.off.onFrameworkEvent("coreOperationFailed");
throw "Google Gears must be allowed to run";
}
var _21="dot_store_"+window.location.href.replace(/[^0-9A-Za-z_]/g,"_");
if(_21.length>=64){
_21=_21.substring(0,63);
}
_20.removeStore(_21);
_20.openStore(_21);
var _22=_20.createStore(_21);
this._store=_22;
var _23=this;
this._currentFileIndex=0;
this._cancelID=_22.capture(this.listOfURLs,function(url,_25,_26){
if(!_25&&_23.refreshing){
_23._cancelID=null;
_23.refreshing=false;
var _27=[];
_27.push("Unable to capture: "+url);
_1e(true,_27);
return;
}else{
if(_25){
_23._currentFileIndex++;
}
}
if(_25&&_23._currentFileIndex>=_23.listOfURLs.length){
_23._cancelID=null;
_23.refreshing=false;
if(_1f){
dojox.storage.put("oldVersion",_1f,null,dojox.off.STORAGE_NAMESPACE);
}
dojox.storage.put("justDebugged",dojo.config.isDebug,null,dojox.off.STORAGE_NAMESPACE);
_1e(false,[]);
}
});
},_getVersionInfo:function(_28){
var _29=dojox.storage.get("justDebugged",dojox.off.STORAGE_NAMESPACE);
var _2a=dojox.storage.get("oldVersion",dojox.off.STORAGE_NAMESPACE);
var _2b=null;
_28=dojo.hitch(this,_28);
dojo.xhrGet({url:this.versionURL+"?browserbust="+new Date().getTime(),timeout:5*1000,handleAs:"javascript",error:function(err){
dojox.storage.remove("oldVersion",dojox.off.STORAGE_NAMESPACE);
dojox.storage.remove("justDebugged",dojox.off.STORAGE_NAMESPACE);
_28(_2a,_2b,_29);
},load:function(_2d){
if(_2d){
_2b=_2d;
}
_28(_2a,_2b,_29);
}});
}};
}
