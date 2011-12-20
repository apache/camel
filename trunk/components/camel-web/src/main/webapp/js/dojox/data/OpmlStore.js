/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.OpmlStore"]){
dojo._hasResource["dojox.data.OpmlStore"]=true;
dojo.provide("dojox.data.OpmlStore");
dojo.require("dojo.data.util.filter");
dojo.require("dojo.data.util.simpleFetch");
dojo.declare("dojox.data.OpmlStore",null,{constructor:function(_1){
this._xmlData=null;
this._arrayOfTopLevelItems=[];
this._arrayOfAllItems=[];
this._metadataNodes=null;
this._loadFinished=false;
this.url=_1.url;
this._opmlData=_1.data;
if(_1.label){
this.label=_1.label;
}
this._loadInProgress=false;
this._queuedFetches=[];
this._identityMap={};
this._identCount=0;
this._idProp="_I";
},label:"text",url:"",_assertIsItem:function(_2){
if(!this.isItem(_2)){
throw new Error("dojo.data.OpmlStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_3){
if(!dojo.isString(_3)){
throw new Error("dojox.data.OpmlStore: a function was passed an attribute argument that was not an attribute object nor an attribute name string");
}
},_removeChildNodesThatAreNotElementNodes:function(_4,_5){
var _6=_4.childNodes;
if(_6.length===0){
return;
}
var _7=[];
var i,_9;
for(i=0;i<_6.length;++i){
_9=_6[i];
if(_9.nodeType!=1){
_7.push(_9);
}
}
for(i=0;i<_7.length;++i){
_9=_7[i];
_4.removeChild(_9);
}
if(_5){
for(i=0;i<_6.length;++i){
_9=_6[i];
this._removeChildNodesThatAreNotElementNodes(_9,_5);
}
}
},_processRawXmlTree:function(_a){
this._loadFinished=true;
this._xmlData=_a;
var _b=_a.getElementsByTagName("head");
var _c=_b[0];
if(_c){
this._removeChildNodesThatAreNotElementNodes(_c);
this._metadataNodes=_c.childNodes;
}
var _d=_a.getElementsByTagName("body");
var _e=_d[0];
if(_e){
this._removeChildNodesThatAreNotElementNodes(_e,true);
var _f=_d[0].childNodes;
for(var i=0;i<_f.length;++i){
var _11=_f[i];
if(_11.tagName=="outline"){
this._identityMap[this._identCount]=_11;
this._identCount++;
this._arrayOfTopLevelItems.push(_11);
this._arrayOfAllItems.push(_11);
this._checkChildNodes(_11);
}
}
}
},_checkChildNodes:function(_12){
if(_12.firstChild){
for(var i=0;i<_12.childNodes.length;i++){
var _14=_12.childNodes[i];
if(_14.tagName=="outline"){
this._identityMap[this._identCount]=_14;
this._identCount++;
this._arrayOfAllItems.push(_14);
this._checkChildNodes(_14);
}
}
}
},_getItemsArray:function(_15){
if(_15&&_15.deep){
return this._arrayOfAllItems;
}
return this._arrayOfTopLevelItems;
},getValue:function(_16,_17,_18){
this._assertIsItem(_16);
this._assertIsAttribute(_17);
if(_17=="children"){
return (_16.firstChild||_18);
}else{
var _19=_16.getAttribute(_17);
return (_19!==undefined)?_19:_18;
}
},getValues:function(_1a,_1b){
this._assertIsItem(_1a);
this._assertIsAttribute(_1b);
var _1c=[];
if(_1b=="children"){
for(var i=0;i<_1a.childNodes.length;++i){
_1c.push(_1a.childNodes[i]);
}
}else{
if(_1a.getAttribute(_1b)!==null){
_1c.push(_1a.getAttribute(_1b));
}
}
return _1c;
},getAttributes:function(_1e){
this._assertIsItem(_1e);
var _1f=[];
var _20=_1e;
var _21=_20.attributes;
for(var i=0;i<_21.length;++i){
var _23=_21.item(i);
_1f.push(_23.nodeName);
}
if(_20.childNodes.length>0){
_1f.push("children");
}
return _1f;
},hasAttribute:function(_24,_25){
return (this.getValues(_24,_25).length>0);
},containsValue:function(_26,_27,_28){
var _29=undefined;
if(typeof _28==="string"){
_29=dojo.data.util.filter.patternToRegExp(_28,false);
}
return this._containsValue(_26,_27,_28,_29);
},_containsValue:function(_2a,_2b,_2c,_2d){
var _2e=this.getValues(_2a,_2b);
for(var i=0;i<_2e.length;++i){
var _30=_2e[i];
if(typeof _30==="string"&&_2d){
return (_30.match(_2d)!==null);
}else{
if(_2c===_30){
return true;
}
}
}
return false;
},isItem:function(_31){
return (_31&&_31.nodeType==1&&_31.tagName=="outline"&&_31.ownerDocument===this._xmlData);
},isItemLoaded:function(_32){
return this.isItem(_32);
},loadItem:function(_33){
},getLabel:function(_34){
if(this.isItem(_34)){
return this.getValue(_34,this.label);
}
return undefined;
},getLabelAttributes:function(_35){
return [this.label];
},_fetchItems:function(_36,_37,_38){
var _39=this;
var _3a=function(_3b,_3c){
var _3d=null;
if(_3b.query){
_3d=[];
var _3e=_3b.queryOptions?_3b.queryOptions.ignoreCase:false;
var _3f={};
for(var key in _3b.query){
var _41=_3b.query[key];
if(typeof _41==="string"){
_3f[key]=dojo.data.util.filter.patternToRegExp(_41,_3e);
}
}
for(var i=0;i<_3c.length;++i){
var _43=true;
var _44=_3c[i];
for(var key in _3b.query){
var _41=_3b.query[key];
if(!_39._containsValue(_44,key,_41,_3f[key])){
_43=false;
}
}
if(_43){
_3d.push(_44);
}
}
}else{
if(_3c.length>0){
_3d=_3c.slice(0,_3c.length);
}
}
_37(_3d,_3b);
};
if(this._loadFinished){
_3a(_36,this._getItemsArray(_36.queryOptions));
}else{
if(this._loadInProgress){
this._queuedFetches.push({args:_36,filter:_3a});
}else{
if(this.url!==""){
this._loadInProgress=true;
var _45={url:_39.url,handleAs:"xml"};
var _46=dojo.xhrGet(_45);
_46.addCallback(function(_47){
_39._processRawXmlTree(_47);
_3a(_36,_39._getItemsArray(_36.queryOptions));
_39._handleQueuedFetches();
});
_46.addErrback(function(_48){
throw _48;
});
}else{
if(this._opmlData){
this._processRawXmlTree(this._opmlData);
this._opmlData=null;
_3a(_36,this._getItemsArray(_36.queryOptions));
}else{
throw new Error("dojox.data.OpmlStore: No OPML source data was provided as either URL or XML data input.");
}
}
}
}
},getFeatures:function(){
var _49={"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
return _49;
},getIdentity:function(_4a){
if(this.isItem(_4a)){
for(var i in this._identityMap){
if(this._identityMap[i]===_4a){
return i;
}
}
}
return null;
},fetchItemByIdentity:function(_4c){
if(!this._loadFinished){
var _4d=this;
if(this.url!==""){
if(this._loadInProgress){
this._queuedFetches.push({args:_4c});
}else{
this._loadInProgress=true;
var _4e={url:_4d.url,handleAs:"xml"};
var _4f=dojo.xhrGet(_4e);
_4f.addCallback(function(_50){
var _51=_4c.scope?_4c.scope:dojo.global;
try{
_4d._processRawXmlTree(_50);
var _52=_4d._identityMap[_4c.identity];
if(!_4d.isItem(_52)){
_52=null;
}
if(_4c.onItem){
_4c.onItem.call(_51,_52);
}
_4d._handleQueuedFetches();
}
catch(error){
if(_4c.onError){
_4c.onError.call(_51,error);
}
}
});
_4f.addErrback(function(_53){
this._loadInProgress=false;
if(_4c.onError){
var _54=_4c.scope?_4c.scope:dojo.global;
_4c.onError.call(_54,_53);
}
});
}
}else{
if(this._opmlData){
this._processRawXmlTree(this._opmlData);
this._opmlData=null;
var _55=this._identityMap[_4c.identity];
if(!_4d.isItem(_55)){
_55=null;
}
if(_4c.onItem){
var _56=_4c.scope?_4c.scope:dojo.global;
_4c.onItem.call(_56,_55);
}
}
}
}else{
var _55=this._identityMap[_4c.identity];
if(!this.isItem(_55)){
_55=null;
}
if(_4c.onItem){
var _56=_4c.scope?_4c.scope:dojo.global;
_4c.onItem.call(_56,_55);
}
}
},getIdentityAttributes:function(_57){
return null;
},_handleQueuedFetches:function(){
if(this._queuedFetches.length>0){
for(var i=0;i<this._queuedFetches.length;i++){
var _59=this._queuedFetches[i];
var _5a=_59.args;
var _5b=_59.filter;
if(_5b){
_5b(_5a,this._getItemsArray(_5a.queryOptions));
}else{
this.fetchItemByIdentity(_5a);
}
}
this._queuedFetches=[];
}
},close:function(_5c){
}});
dojo.extend(dojox.data.OpmlStore,dojo.data.util.simpleFetch);
}
