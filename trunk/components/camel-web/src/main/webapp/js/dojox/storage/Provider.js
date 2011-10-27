/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.storage.Provider"]){
dojo._hasResource["dojox.storage.Provider"]=true;
dojo.provide("dojox.storage.Provider");
dojo.declare("dojox.storage.Provider",null,{constructor:function(){
},SUCCESS:"success",FAILED:"failed",PENDING:"pending",SIZE_NOT_AVAILABLE:"Size not available",SIZE_NO_LIMIT:"No size limit",DEFAULT_NAMESPACE:"default",onHideSettingsUI:null,initialize:function(){
console.warn("dojox.storage.initialize not implemented");
},isAvailable:function(){
console.warn("dojox.storage.isAvailable not implemented");
},put:function(_1,_2,_3,_4){
console.warn("dojox.storage.put not implemented");
},get:function(_5,_6){
console.warn("dojox.storage.get not implemented");
},hasKey:function(_7,_8){
return !!this.get(_7,_8);
},getKeys:function(_9){
console.warn("dojox.storage.getKeys not implemented");
},clear:function(_a){
console.warn("dojox.storage.clear not implemented");
},remove:function(_b,_c){
console.warn("dojox.storage.remove not implemented");
},getNamespaces:function(){
console.warn("dojox.storage.getNamespaces not implemented");
},isPermanent:function(){
console.warn("dojox.storage.isPermanent not implemented");
},getMaximumSize:function(){
console.warn("dojox.storage.getMaximumSize not implemented");
},putMultiple:function(_d,_e,_f,_10){
for(var i=0;i<_d.length;i++){
dojox.storage.put(_d[i],_e[i],_f,_10);
}
},getMultiple:function(_12,_13){
var _14=[];
for(var i=0;i<_12.length;i++){
_14.push(dojox.storage.get(_12[i],_13));
}
return _14;
},removeMultiple:function(_16,_17){
for(var i=0;i<_16.length;i++){
dojox.storage.remove(_16[i],_17);
}
},isValidKeyArray:function(_19){
if(_19===null||_19===undefined||!dojo.isArray(_19)){
return false;
}
return !dojo.some(_19,function(key){
return !this.isValidKey(key);
},this);
},hasSettingsUI:function(){
return false;
},showSettingsUI:function(){
console.warn("dojox.storage.showSettingsUI not implemented");
},hideSettingsUI:function(){
console.warn("dojox.storage.hideSettingsUI not implemented");
},isValidKey:function(_1b){
if(_1b===null||_1b===undefined){
return false;
}
return /^[0-9A-Za-z_]*$/.test(_1b);
},getResourceList:function(){
return [];
}});
}
