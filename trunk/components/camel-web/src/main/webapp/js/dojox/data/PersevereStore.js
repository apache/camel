/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.PersevereStore"]){
dojo._hasResource["dojox.data.PersevereStore"]=true;
dojo.provide("dojox.data.PersevereStore");
dojo.require("dojox.data.JsonQueryRestStore");
dojo.require("dojox.rpc.Client");
dojox.json.ref.serializeFunctions=true;
dojo.declare("dojox.data.PersevereStore",dojox.data.JsonQueryRestStore,{useFullIdInQueries:true,jsonQueryPagination:false});
dojox.data.PersevereStore.getStores=function(_1,_2){
_1=(_1&&(_1.match(/\/$/)?_1:(_1+"/")))||"/";
if(_1.match(/^\w*:\/\//)){
dojo.require("dojox.io.xhrScriptPlugin");
dojox.io.xhrScriptPlugin(_1,"callback",dojox.io.xhrPlugins.fullHttpAdapter);
}
var _3=dojo.xhr;
dojo.xhr=function(_4,_5){
(_5.headers=_5.headers||{})["Server-Methods"]=false;
return _3.apply(dojo,arguments);
};
var _6=dojox.rpc.Rest(_1,true);
dojox.rpc._sync=_2;
var _7=_6("Class/");
var _8;
var _9={};
var _a=0;
_7.addCallback(function(_b){
dojox.json.ref.resolveJson(_b,{index:dojox.rpc.Rest._index,idPrefix:"/Class/",assignAbsoluteIds:true});
function _c(_d){
if(_d["extends"]&&_d["extends"].prototype){
if(!_d.prototype||!_d.prototype.isPrototypeOf(_d["extends"].prototype)){
_c(_d["extends"]);
dojox.rpc.Rest._index[_d.prototype.__id]=_d.prototype=dojo.mixin(dojo.delegate(_d["extends"].prototype),_d.prototype);
}
}
};
function _e(_f,_10){
if(_f&&_10){
for(var j in _f){
var _12=_f[j];
if(_12.runAt=="server"&&!_10[j]){
_10[j]=(function(_13){
return function(){
var _14=dojo.rawXhrPost({url:this.__id,postData:dojo.toJson({method:_13,id:_a++,params:dojo._toArray(arguments)}),handleAs:"json"});
_14.addCallback(function(_15){
return _15.error?new Error(_15.error):_15.result;
});
return _14;
};
})(j);
}
}
}
};
for(var i in _b){
if(typeof _b[i]=="object"){
var _17=_b[i];
_c(_17);
_e(_17.methods,_17.prototype=_17.prototype||{});
_e(_17.staticMethods,_17);
_9[_b[i].id]=new dojox.data.PersevereStore({target:new dojo._Url(_1,_b[i].id)+"",schema:_17});
}
}
return (_8=_9);
});
dojo.xhr=_3;
return _2?_8:_7;
};
dojox.data.PersevereStore.addProxy=function(){
dojo.require("dojox.io.xhrPlugins");
dojox.io.xhrPlugins.addProxy("/proxy/");
};
}
