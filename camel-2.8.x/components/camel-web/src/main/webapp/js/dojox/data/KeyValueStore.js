/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.KeyValueStore"]){
dojo._hasResource["dojox.data.KeyValueStore"]=true;
dojo.provide("dojox.data.KeyValueStore");
dojo.require("dojo.data.util.filter");
dojo.require("dojo.data.util.simpleFetch");
dojo.declare("dojox.data.KeyValueStore",null,{constructor:function(_1){
if(_1.url){
this.url=_1.url;
}
this._keyValueString=_1.data;
this._keyValueVar=_1.dataVar;
this._keyAttribute="key";
this._valueAttribute="value";
this._storeProp="_keyValueStore";
this._features={"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
this._loadInProgress=false;
this._queuedFetches=[];
},url:"",data:"",_assertIsItem:function(_2){
if(!this.isItem(_2)){
throw new Error("dojox.data.KeyValueStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_3,_4){
if(!dojo.isString(_4)){
throw new Error("dojox.data.KeyValueStore: a function was passed an attribute argument that was not an attribute object nor an attribute name string");
}
},getValue:function(_5,_6,_7){
this._assertIsItem(_5);
this._assertIsAttribute(_5,_6);
var _8;
if(_6==this._keyAttribute){
_8=_5[this._keyAttribute];
}else{
_8=_5[this._valueAttribute];
}
if(_8===undefined){
_8=_7;
}
return _8;
},getValues:function(_9,_a){
var _b=this.getValue(_9,_a);
return (_b?[_b]:[]);
},getAttributes:function(_c){
return [this._keyAttribute,this._valueAttribute,_c[this._keyAttribute]];
},hasAttribute:function(_d,_e){
this._assertIsItem(_d);
this._assertIsAttribute(_d,_e);
return (_e==this._keyAttribute||_e==this._valueAttribute||_e==_d[this._keyAttribute]);
},containsValue:function(_f,_10,_11){
var _12=undefined;
if(typeof _11==="string"){
_12=dojo.data.util.filter.patternToRegExp(_11,false);
}
return this._containsValue(_f,_10,_11,_12);
},_containsValue:function(_13,_14,_15,_16){
var _17=this.getValues(_13,_14);
for(var i=0;i<_17.length;++i){
var _19=_17[i];
if(typeof _19==="string"&&_16){
return (_19.match(_16)!==null);
}else{
if(_15===_19){
return true;
}
}
}
return false;
},isItem:function(_1a){
if(_1a&&_1a[this._storeProp]===this){
return true;
}
return false;
},isItemLoaded:function(_1b){
return this.isItem(_1b);
},loadItem:function(_1c){
},getFeatures:function(){
return this._features;
},close:function(_1d){
},getLabel:function(_1e){
return _1e[this._keyAttribute];
},getLabelAttributes:function(_1f){
return [this._keyAttribute];
},_fetchItems:function(_20,_21,_22){
var _23=this;
var _24=function(_25,_26){
var _27=null;
if(_25.query){
_27=[];
var _28=_25.queryOptions?_25.queryOptions.ignoreCase:false;
var _29={};
for(var key in _25.query){
var _2b=_25.query[key];
if(typeof _2b==="string"){
_29[key]=dojo.data.util.filter.patternToRegExp(_2b,_28);
}
}
for(var i=0;i<_26.length;++i){
var _2d=true;
var _2e=_26[i];
for(var key in _25.query){
var _2b=_25.query[key];
if(!_23._containsValue(_2e,key,_2b,_29[key])){
_2d=false;
}
}
if(_2d){
_27.push(_2e);
}
}
}else{
if(_25.identity){
_27=[];
var _2f;
for(var key in _26){
_2f=_26[key];
if(_2f[_23._keyAttribute]==_25.identity){
_27.push(_2f);
break;
}
}
}else{
if(_26.length>0){
_27=_26.slice(0,_26.length);
}
}
}
_21(_27,_25);
};
if(this._loadFinished){
_24(_20,this._arrayOfAllItems);
}else{
if(this.url!==""){
if(this._loadInProgress){
this._queuedFetches.push({args:_20,filter:_24});
}else{
this._loadInProgress=true;
var _30={url:_23.url,handleAs:"json-comment-filtered"};
var _31=dojo.xhrGet(_30);
_31.addCallback(function(_32){
_23._processData(_32);
_24(_20,_23._arrayOfAllItems);
_23._handleQueuedFetches();
});
_31.addErrback(function(_33){
_23._loadInProgress=false;
throw _33;
});
}
}else{
if(this._keyValueString){
this._processData(eval(this._keyValueString));
this._keyValueString=null;
_24(_20,this._arrayOfAllItems);
}else{
if(this._keyValueVar){
this._processData(this._keyValueVar);
this._keyValueVar=null;
_24(_20,this._arrayOfAllItems);
}else{
throw new Error("dojox.data.KeyValueStore: No source data was provided as either URL, String, or Javascript variable data input.");
}
}
}
}
},_handleQueuedFetches:function(){
if(this._queuedFetches.length>0){
for(var i=0;i<this._queuedFetches.length;i++){
var _35=this._queuedFetches[i];
var _36=_35.filter;
var _37=_35.args;
if(_36){
_36(_37,this._arrayOfAllItems);
}else{
this.fetchItemByIdentity(_35.args);
}
}
this._queuedFetches=[];
}
},_processData:function(_38){
this._arrayOfAllItems=[];
for(var i=0;i<_38.length;i++){
this._arrayOfAllItems.push(this._createItem(_38[i]));
}
this._loadFinished=true;
this._loadInProgress=false;
},_createItem:function(_3a){
var _3b={};
_3b[this._storeProp]=this;
for(var i in _3a){
_3b[this._keyAttribute]=i;
_3b[this._valueAttribute]=_3a[i];
break;
}
return _3b;
},getIdentity:function(_3d){
if(this.isItem(_3d)){
return _3d[this._keyAttribute];
}
return null;
},getIdentityAttributes:function(_3e){
return [this._keyAttribute];
},fetchItemByIdentity:function(_3f){
_3f.oldOnItem=_3f.onItem;
_3f.onItem=null;
_3f.onComplete=this._finishFetchItemByIdentity;
this.fetch(_3f);
},_finishFetchItemByIdentity:function(_40,_41){
var _42=_41.scope||dojo.global;
if(_40.length){
_41.oldOnItem.call(_42,_40[0]);
}else{
_41.oldOnItem.call(_42,null);
}
}});
dojo.extend(dojox.data.KeyValueStore,dojo.data.util.simpleFetch);
}
