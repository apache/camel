/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.rpc.OfflineRest"]){
dojo._hasResource["dojox.rpc.OfflineRest"]=true;
dojo.provide("dojox.rpc.OfflineRest");
dojo.require("dojox.data.ClientFilter");
dojo.require("dojox.rpc.Rest");
dojo.require("dojox.storage");
(function(){
var _1=dojox.rpc.Rest;
var _2="dojox_rpc_OfflineRest";
var _3;
var _4=_1._index;
dojox.storage.manager.addOnLoad(function(){
_3=dojox.storage.manager.available;
for(var i in _4){
_6(_4[i],i);
}
});
var _7;
function _8(_9){
return _9.replace(/[^0-9A-Za-z_]/g,"_");
};
function _6(_a,id){
if(_3&&!_7&&(id||(_a&&_a.__id))){
dojox.storage.put(_8(id||_a.__id),typeof _a=="object"?dojox.json.ref.toJson(_a):_a,function(){
},_2);
}
};
function _c(_d){
return _d instanceof Error&&(_d.status==503||_d.status>12000||!_d.status);
};
function _e(){
if(_3){
var _f=dojox.storage.get("dirty",_2);
if(_f){
for(var _10 in _f){
_11(_10,_f);
}
}
}
};
var _12;
function _13(){
_12.sendChanges();
_12.downloadChanges();
};
var _14=setInterval(_13,15000);
dojo.connect(document,"ononline",_13);
_12=dojox.rpc.OfflineRest={turnOffAutoSync:function(){
clearInterval(_14);
},sync:_13,sendChanges:_e,downloadChanges:function(){
},addStore:function(_15,_16){
_12.stores.push(_15);
_15.fetch({queryOptions:{cache:true},query:_16,onComplete:function(_17,_18){
_15._localBaseResults=_17;
_15._localBaseFetch=_18;
}});
}};
_12.stores=[];
var _19=_1._get;
_1._get=function(_1a,id){
try{
_e();
if(window.navigator&&navigator.onLine===false){
throw new Error();
}
var dfd=_19(_1a,id);
}
catch(e){
dfd=new dojo.Deferred();
dfd.errback(e);
}
var _1d=dojox.rpc._sync;
dfd.addCallback(function(_1e){
_6(_1e,_1a.servicePath+id);
return _1e;
});
dfd.addErrback(function(_1f){
if(_3){
if(_c(_1f)){
var _20={};
var _21=function(id,_23){
if(_20[id]){
return _23;
}
var _24=dojo.fromJson(dojox.storage.get(_8(id),_2))||_23;
_20[id]=_24;
for(var i in _24){
var val=_24[i];
if(val&&val.$ref){
_24[i]=_21(val.$ref,val);
}
}
if(_24 instanceof Array){
for(i=0;i<_24.length;i++){
if(_24[i]===undefined){
_24.splice(i--,1);
}
}
}
return _24;
};
_7=true;
var _27=_21(_1a.servicePath+id);
if(!_27){
return _1f;
}
_7=false;
return _27;
}else{
return _1f;
}
}else{
if(_1d){
return new Error("Storage manager not loaded, can not continue");
}
dfd=new dojo.Deferred();
dfd.addCallback(arguments.callee);
dojox.storage.manager.addOnLoad(function(){
dfd.callback();
});
return dfd;
}
});
return dfd;
};
var _28=_1._change;
_1._change=function(_29,_2a,id,_2c){
if(!_3){
return _28.apply(this,arguments);
}
var _2d=_2a.servicePath+id;
if(_29=="delete"){
dojox.storage.remove(_8(_2d),_2);
}else{
dojox.storage.put(_8(dojox.rpc.JsonRest._contentId),_2c,function(){
},_2);
}
var _2e=_2a._store;
if(_2e){
_2e.updateResultSet(_2e._localBaseResults,_2e._localBaseFetch);
dojox.storage.put(_8(_2a.servicePath+_2e._localBaseFetch.query),dojox.json.ref.toJson(_2e._localBaseResults),function(){
},_2);
}
var _2f=dojox.storage.get("dirty",_2)||{};
if(_29=="put"||_29=="delete"){
var _30=_2d;
}else{
_30=0;
for(var i in _2f){
if(!isNaN(parseInt(i))){
_30=i;
}
}
_30++;
}
_2f[_30]={method:_29,id:_2d,content:_2c};
return _11(_30,_2f);
};
function _11(_32,_33){
var _34=_33[_32];
var _35=dojox.rpc.JsonRest.getServiceAndId(_34.id);
var _36=_28(_34.method,_35.service,_35.id,_34.content);
_33[_32]=_34;
dojox.storage.put("dirty",_33,function(){
},_2);
_36.addBoth(function(_37){
if(_c(_37)){
return null;
}
var _38=dojox.storage.get("dirty",_2)||{};
delete _38[_32];
dojox.storage.put("dirty",_38,function(){
},_2);
return _37;
});
return _36;
};
dojo.connect(_4,"onLoad",_6);
dojo.connect(_4,"onUpdate",_6);
})();
}
