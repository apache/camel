/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.FileStore"]){
dojo._hasResource["dojox.data.FileStore"]=true;
dojo.provide("dojox.data.FileStore");
dojo.declare("dojox.data.FileStore",null,{constructor:function(_1){
if(_1&&_1.label){
this.label=_1.label;
}
if(_1&&_1.url){
this.url=_1.url;
}
if(_1&&_1.options){
if(dojo.isArray(_1.options)){
this.options=_1.options;
}else{
if(dojo.isString(_1.options)){
this.options=_1.options.split(",");
}
}
}
if(_1&&_1.pathAsQueryParam){
this.pathAsQueryParam=true;
}
},url:"",_storeRef:"_S",label:"name",_identifier:"path",_attributes:["children","directory","name","path","modified","size","parentDir"],pathSeparator:"/",options:[],_assertIsItem:function(_2){
if(!this.isItem(_2)){
throw new Error("dojox.data.FileStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_3){
if(typeof _3!=="string"){
throw new Error("dojox.data.FileStore: a function was passed an attribute argument that was not an attribute name string");
}
},pathAsQueryParam:false,getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
},getValue:function(_4,_5,_6){
var _7=this.getValues(_4,_5);
var _8=_6;
if(_7&&_7.length>0){
_8=_7[0];
}
return _8;
},getAttributes:function(_9){
return this._attributes;
},hasAttribute:function(_a,_b){
if(this.getValue(_a,_b)){
return true;
}
return false;
},getIdentity:function(_c){
return this.getValue(_c,this._identifier);
},getIdentityAttributes:function(_d){
return [this._identifier];
},isItemLoaded:function(_e){
var _f=this.isItem(_e);
if(_f&&typeof _e._loaded=="boolean"&&!_e._loaded){
_f=false;
}
return _f;
},loadItem:function(_10){
var _11=_10.item;
var _12=this;
var _13=_10.scope||dojo.global;
var _14={};
if(this.options.length>0){
_14.options=dojo.toJson(this.options);
}
if(this.pathAsQueryParam){
_14.path=_11.parentPath+this.pathSeparator+_11.name;
}
var _15={url:this.pathAsQueryParam?this.url:this.url+"/"+_11.parentPath+"/"+_11.name,handleAs:"json-comment-optional",content:_14,preventCache:true};
var _16=dojo.xhrGet(_15);
_16.addErrback(function(_17){
if(_10.onError){
_10.onError.call(_13,_17);
}
});
_16.addCallback(function(_18){
delete _11.parentPath;
delete _11._loaded;
dojo.mixin(_11,_18);
_12._processItem(_11);
if(_10.onItem){
_10.onItem.call(_13,_11);
}
});
},getLabel:function(_19){
return this.getValue(_19,this.label);
},getLabelAttributes:function(_1a){
return [this.label];
},containsValue:function(_1b,_1c,_1d){
var _1e=this.getValues(_1b,_1c);
for(var i=0;i<_1e.length;i++){
if(_1e[i]==_1d){
return true;
}
}
return false;
},getValues:function(_20,_21){
this._assertIsItem(_20);
this._assertIsAttribute(_21);
var _22=_20[_21];
if(typeof _22!=="undefined"&&!dojo.isArray(_22)){
_22=[_22];
}else{
if(typeof _22==="undefined"){
_22=[];
}
}
return _22;
},isItem:function(_23){
if(_23&&_23[this._storeRef]===this){
return true;
}
return false;
},close:function(_24){
},fetch:function(_25){
_25=_25||{};
if(!_25.store){
_25.store=this;
}
var _26=this;
var _27=_25.scope||dojo.global;
var _28={};
if(_25.query){
_28.query=dojo.toJson(_25.query);
}
if(_25.sort){
_28.sort=dojo.toJson(_25.sort);
}
if(_25.queryOptions){
_28.queryOptions=dojo.toJson(_25.queryOptions);
}
if(typeof _25.start=="number"){
_28.start=""+_25.start;
}
if(typeof _25.count=="number"){
_28.count=""+_25.count;
}
if(this.options.length>0){
_28.options=dojo.toJson(this.options);
}
var _29={url:this.url,preventCache:true,handleAs:"json-comment-optional",content:_28};
var _2a=dojo.xhrGet(_29);
_2a.addCallback(function(_2b){
_26._processResult(_2b,_25);
});
_2a.addErrback(function(_2c){
if(_25.onError){
_25.onError.call(_27,_2c,_25);
}
});
},fetchItemByIdentity:function(_2d){
var _2e=_2d.identity;
var _2f=this;
var _30=_2d.scope||dojo.global;
var _31={};
if(this.options.length>0){
_31.options=dojo.toJson(this.options);
}
if(this.pathAsQueryParam){
_31.path=_2e;
}
var _32={url:this.pathAsQueryParam?this.url:this.url+"/"+_2e,handleAs:"json-comment-optional",content:_31,preventCache:true};
var _33=dojo.xhrGet(_32);
_33.addErrback(function(_34){
if(_2d.onError){
_2d.onError.call(_30,_34);
}
});
_33.addCallback(function(_35){
var _36=_2f._processItem(_35);
if(_2d.onItem){
_2d.onItem.call(_30,_36);
}
});
},_processResult:function(_37,_38){
var _39=_38.scope||dojo.global;
try{
if(_37.pathSeparator){
this.pathSeparator=_37.pathSeparator;
}
if(_38.onBegin){
_38.onBegin.call(_39,_37.total,_38);
}
var _3a=this._processItemArray(_37.items);
if(_38.onItem){
var i;
for(i=0;i<_3a.length;i++){
_38.onItem.call(_39,_3a[i],_38);
}
_3a=null;
}
if(_38.onComplete){
_38.onComplete.call(_39,_3a,_38);
}
}
catch(e){
if(_38.onError){
_38.onError.call(_39,e,_38);
}else{

}
}
},_processItemArray:function(_3c){
var i;
for(i=0;i<_3c.length;i++){
this._processItem(_3c[i]);
}
return _3c;
},_processItem:function(_3e){
if(!_3e){
return null;
}
_3e[this._storeRef]=this;
if(_3e.children&&_3e.directory){
if(dojo.isArray(_3e.children)){
var _3f=_3e.children;
var i;
for(i=0;i<_3f.length;i++){
var _41=_3f[i];
if(dojo.isObject(_41)){
_3f[i]=this._processItem(_41);
}else{
_3f[i]={name:_41,_loaded:false,parentPath:_3e.path};
_3f[i][this._storeRef]=this;
}
}
}else{
delete _3e.children;
}
}
return _3e;
}});
}
