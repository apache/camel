/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.ServiceStore"]){
dojo._hasResource["dojox.data.ServiceStore"]=true;
dojo.provide("dojox.data.ServiceStore");
dojo.declare("dojox.data.ServiceStore",dojox.data.ClientFilter,{constructor:function(_1){
this.byId=this.fetchItemByIdentity;
this._index={};
if(_1){
dojo.mixin(this,_1);
}
this.idAttribute=(_1&&_1.idAttribute)||(this.schema&&this.schema._idAttr);
this.labelAttribute=this.labelAttribute||"label";
},schema:null,idAttribute:"id",syncMode:false,estimateCountFactor:1,getSchema:function(){
return this.schema;
},loadLazyValues:true,getValue:function(_2,_3,_4){
var _5=_2[_3];
return _5||(_3 in _2?_5:_2._loadObject?(dojox.rpc._sync=true)&&arguments.callee.call(this,dojox.data.ServiceStore.prototype.loadItem({item:_2})||{},_3,_4):_4);
},getValues:function(_6,_7){
var _8=this.getValue(_6,_7);
return _8 instanceof Array?_8:_8===undefined?[]:[_8];
},getAttributes:function(_9){
var _a=[];
for(var i in _9){
if(_9.hasOwnProperty(i)&&!(i.charAt(0)=="_"&&i.charAt(1)=="_")){
_a.push(i);
}
}
return _a;
},hasAttribute:function(_c,_d){
return _d in _c;
},containsValue:function(_e,_f,_10){
return dojo.indexOf(this.getValues(_e,_f),_10)>-1;
},isItem:function(_11){
return (typeof _11=="object")&&_11&&!(_11 instanceof Date);
},isItemLoaded:function(_12){
return _12&&!_12._loadObject;
},loadItem:function(_13){
var _14;
if(_13.item._loadObject){
_13.item._loadObject(function(_15){
_14=_15;
delete _14._loadObject;
var _16=_15 instanceof Error?_13.onError:_13.onItem;
if(_16){
_16.call(_13.scope,_15);
}
});
}else{
if(_13.onItem){
_13.onItem.call(_13.scope,_13.item);
}
}
return _14;
},_currentId:0,_processResults:function(_17,_18){
if(_17&&typeof _17=="object"){
var id=_17.__id;
if(!id){
if(this.idAttribute){
id=_17[this.idAttribute];
}else{
id=this._currentId++;
}
if(id!==undefined){
var _1a=this._index[id];
if(_1a){
for(var j in _1a){
delete _1a[j];
}
_17=dojo.mixin(_1a,_17);
}
_17.__id=id;
this._index[id]=_17;
}
}
for(var i in _17){
_17[i]=this._processResults(_17[i],_18).items;
}
}
var _1d=_17.length;
return {totalCount:_18.request.count==_1d?(_18.request.start||0)+_1d*this.estimateCountFactor:_1d,items:_17};
},close:function(_1e){
return _1e&&_1e.abort&&_1e.abort();
},fetch:function(_1f){
_1f=_1f||{};
if("syncMode" in _1f?_1f.syncMode:this.syncMode){
dojox.rpc._sync=true;
}
var _20=this;
var _21=_1f.scope||_20;
var _22=this.cachingFetch?this.cachingFetch(_1f):this._doQuery(_1f);
_22.request=_1f;
_22.addCallback(function(_23){
if(_1f.clientFetch){
_23=_20.clientSideFetch({query:_1f.clientFetch,sort:_1f.sort,start:_1f.start,count:_1f.count},_23);
}
var _24=_20._processResults(_23,_22);
_23=_1f.results=_24.items;
if(_1f.onBegin){
_1f.onBegin.call(_21,_24.totalCount,_1f);
}
if(_1f.onItem){
for(var i=0;i<_23.length;i++){
_1f.onItem.call(_21,_23[i],_1f);
}
}
if(_1f.onComplete){
_1f.onComplete.call(_21,_1f.onItem?null:_23,_1f);
}
return _23;
});
_22.addErrback(_1f.onError&&dojo.hitch(_21,_1f.onError));
_1f.abort=function(){
_22.ioArgs.xhr.abort();
};
_1f.store=this;
return _1f;
},_doQuery:function(_26){
var _27=typeof _26.queryStr=="string"?_26.queryStr:_26.query;
return this.service(_27);
},getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Identity":true,"dojo.data.api.Schema":this.schema};
},getLabel:function(_28){
return this.getValue(_28,this.labelAttribute);
},getLabelAttributes:function(_29){
return [this.labelAttribute];
},getIdentity:function(_2a){
return _2a.__id;
},getIdentityAttributes:function(_2b){
return [this.idAttribute];
},fetchItemByIdentity:function(_2c){
var _2d=this._index[(_2c._prefix||"")+_2c.identity];
if(_2d&&_2c.onItem){
_2c.onItem.call(_2c.scope,_2d);
}else{
return this.fetch({query:_2c.identity,onComplete:_2c.onItem,onError:_2c.onError,scope:_2c.scope}).results;
}
return _2d;
}});
}
