/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.JsonRestStore"]){
dojo._hasResource["dojox.data.JsonRestStore"]=true;
dojo.provide("dojox.data.JsonRestStore");
dojo.require("dojox.data.ServiceStore");
dojo.require("dojox.rpc.JsonRest");
dojo.declare("dojox.data.JsonRestStore",dojox.data.ServiceStore,{constructor:function(_1){
dojo.connect(dojox.rpc.Rest._index,"onUpdate",this,function(_2,_3,_4,_5){
var _6=this.service.servicePath;
if(!_2.__id){

}else{
if(_2.__id.substring(0,_6.length)==_6){
this.onSet(_2,_3,_4,_5);
}
}
});
this.idAttribute=this.idAttribute||"id";
if(typeof _1.target=="string"&&!this.service){
this.service=dojox.rpc.Rest(this.target,true);
}
dojox.rpc.JsonRest.registerService(this.service,_1.target,this.schema);
this.schema=this.service._schema=this.schema||this.service._schema||{};
this.service._store=this;
this.schema._idAttr=this.idAttribute;
var _7=dojox.rpc.JsonRest.getConstructor(this.service);
var _8=this;
this._constructor=function(_9){
_7.call(this,_9);
_8.onNew(this);
};
this._constructor.prototype=_7.prototype;
this._index=dojox.rpc.Rest._index;
},referenceIntegrity:true,target:"",newItem:function(_a,_b){
_a=new this._constructor(_a);
if(_b){
var _c=this.getValue(_b.parent,_b.attribute,[]);
this.setValue(_b.parent,_b.attribute,_c.concat([_a]));
}
return _a;
},deleteItem:function(_d){
var _e=[];
var _f=dojox.data._getStoreForItem(_d)||this;
if(this.referenceIntegrity){
dojox.rpc.JsonRest._saveNotNeeded=true;
var _10=dojox.rpc.Rest._index;
var _11=function(_12){
var _13;
_e.push(_12);
_12.__checked=1;
for(var i in _12){
var _15=_12[i];
if(_15==_d){
if(_12!=_10){
if(_12 instanceof Array){
(_13=_13||[]).push(i);
}else{
(dojox.data._getStoreForItem(_12)||_f).unsetAttribute(_12,i);
}
}
}else{
if((typeof _15=="object")&&_15){
if(!_15.__checked){
_11(_15);
}
if(typeof _15.__checked=="object"&&_12!=_10){
(dojox.data._getStoreForItem(_12)||_f).setValue(_12,i,_15.__checked);
}
}
}
}
if(_13){
i=_13.length;
_12=_12.__checked=_12.concat();
while(i--){
_12.splice(_13[i],1);
}
return _12;
}
return null;
};
_11(_10);
dojox.rpc.JsonRest._saveNotNeeded=false;
var i=0;
while(_e[i]){
delete _e[i++].__checked;
}
}
dojox.rpc.JsonRest.deleteObject(_d);
_f.onDelete(_d);
},changing:function(_17,_18){
dojox.rpc.JsonRest.changing(_17,_18);
},setValue:function(_19,_1a,_1b){
var old=_19[_1a];
var _1d=_19.__id?dojox.data._getStoreForItem(_19):this;
if(dojox.json.schema&&_1d.schema&&_1d.schema.properties){
dojox.json.schema.mustBeValid(dojox.json.schema.checkPropertyChange(_1b,_1d.schema.properties[_1a]));
}
if(_1a==_1d.idAttribute){
throw new Error("Can not change the identity attribute for an item");
}
_1d.changing(_19);
_19[_1a]=_1b;
_1d.onSet(_19,_1a,old,_1b);
},setValues:function(_1e,_1f,_20){
if(!dojo.isArray(_20)){
throw new Error("setValues expects to be passed an Array object as its value");
}
this.setValue(_1e,_1f,_20);
},unsetAttribute:function(_21,_22){
this.changing(_21);
var old=_21[_22];
delete _21[_22];
this.onSet(_21,_22,old,undefined);
},save:function(_24){
if(!(_24&&_24.global)){
(_24=_24||{}).service=this.service;
}
var _25=dojox.rpc.JsonRest.commit(_24);
this.serverVersion=this._updates&&this._updates.length;
return _25;
},revert:function(_26){
var _27=dojox.rpc.JsonRest.getDirtyObjects().concat([]);
while(_27.length>0){
var d=_27.pop();
var _29=dojox.data._getStoreForItem(d.object||d.old);
if(!d.object){
_29.onNew(d.old);
}else{
if(!d.old){
_29.onDelete(d.object);
}else{
for(var i in d.object){
if(d.object[i]!=d.old[i]){
_29.onSet(d.object,i,d.object[i],d.old[i]);
}
}
}
}
}
dojox.rpc.JsonRest.revert(_26&&_26.global&&this.service);
},isDirty:function(_2b){
return dojox.rpc.JsonRest.isDirty(_2b);
},isItem:function(_2c,_2d){
return _2c&&_2c.__id&&(_2d||this.service==dojox.rpc.JsonRest.getServiceAndId(_2c.__id).service);
},_doQuery:function(_2e){
var _2f=typeof _2e.queryStr=="string"?_2e.queryStr:_2e.query;
return dojox.rpc.JsonRest.query(this.service,_2f,_2e);
},_processResults:function(_30,_31){
var _32=_30.length;
return {totalCount:_31.fullLength||(_31.request.count==_32?(_31.request.start||0)+_32*2:_32),items:_30};
},getConstructor:function(){
return this._constructor;
},getIdentity:function(_33){
var id=_33.__clientId||_33.__id;
if(!id){
return id;
}
var _35=this.service.servicePath;
return id.substring(0,_35.length)!=_35?id:id.substring(_35.length);
},fetchItemByIdentity:function(_36){
var id=_36.identity;
var _38=this;
if(id.match(/^(\w*:)?\//)){
var _39=dojox.rpc.JsonRest.getServiceAndId(id);
_38=_39.service._store;
_36.identity=_39.id;
}
_36._prefix=_38.service.servicePath;
return _38.inherited(arguments);
},onSet:function(){
},onNew:function(){
},onDelete:function(){
},getFeatures:function(){
var _3a=this.inherited(arguments);
_3a["dojo.data.api.Write"]=true;
_3a["dojo.data.api.Notification"]=true;
return _3a;
}});
dojox.data._getStoreForItem=function(_3b){
if(_3b.__id){
var _3c=_3b.__id.match(/.*\//)[0];
var _3d=dojox.rpc.JsonRest.services[_3c];
return _3d?_3d._store:new dojox.data.JsonRestStore({target:_3c});
}
return null;
};
dojox.json.ref._useRefs=true;
}
