/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.contrib.data"]){
dojo._hasResource["dojox.dtl.contrib.data"]=true;
dojo.provide("dojox.dtl.contrib.data");
dojo.require("dojox.dtl._base");
(function(){
var dd=dojox.dtl;
var _2=dd.contrib.data;
var _3=true;
_2._BoundItem=dojo.extend(function(_4,_5){
this.item=_4;
this.store=_5;
},{get:function(_6){
var _7=this.store;
var _8=this.item;
if(_6=="getLabel"){
return _7.getLabel(_8);
}else{
if(_6=="getAttributes"){
return _7.getAttributes(_8);
}else{
if(_6=="getIdentity"){
if(_7.getIdentity){
return _7.getIdentity(_8);
}
return "Store has no identity API";
}else{
if(!_7.hasAttribute(_8,_6)){
if(_6.slice(-1)=="s"){
if(_3){
_3=false;
dojo.deprecated("You no longer need an extra s to call getValues, it can be figured out automatically");
}
_6=_6.slice(0,-1);
}
if(!_7.hasAttribute(_8,_6)){
return;
}
}
var _9=_7.getValues(_8,_6);
if(!_9){
return;
}
if(!dojo.isArray(_9)){
return new _2._BoundItem(_9,_7);
}
_9=dojo.map(_9,function(_a){
if(dojo.isObject(_a)&&_7.isItem(_a)){
return new _2._BoundItem(_a,_7);
}
return _a;
});
_9.get=_2._get;
return _9;
}
}
}
}});
_2._BoundItem.prototype.get.safe=true;
_2.BindDataNode=dojo.extend(function(_b,_c,_d,_e){
this.items=_b&&new dd._Filter(_b);
this.query=_c&&new dd._Filter(_c);
this.store=new dd._Filter(_d);
this.alias=_e;
},{render:function(_f,_10){
var _11=this.items&&this.items.resolve(_f);
var _12=this.query&&this.query.resolve(_f);
var _13=this.store.resolve(_f);
if(!_13||!_13.getFeatures){
throw new Error("data_bind didn't receive a store");
}
if(_12){
var _14=false;
_13.fetch({query:_12,sync:true,scope:this,onComplete:function(it){
_14=true;
_11=it;
}});
if(!_14){
throw new Error("The bind_data tag only works with a query if the store executed synchronously");
}
}
var _16=[];
if(_11){
for(var i=0,_18;_18=_11[i];i++){
_16.push(new _2._BoundItem(_18,_13));
}
}
_f[this.alias]=_16;
return _10;
},unrender:function(_19,_1a){
return _1a;
},clone:function(){
return this;
}});
dojo.mixin(_2,{_get:function(key){
if(this.length){
return (this[0] instanceof _2._BoundItem)?this[0].get(key):this[0][key];
}
},bind_data:function(_1c,_1d){
var _1e=_1d.contents.split();
if(_1e[2]!="to"||_1e[4]!="as"||!_1e[5]){
throw new Error("data_bind expects the format: 'data_bind items to store as varName'");
}
return new _2.BindDataNode(_1e[1],null,_1e[3],_1e[5]);
},bind_query:function(_1f,_20){
var _21=_20.contents.split();
if(_21[2]!="to"||_21[4]!="as"||!_21[5]){
throw new Error("data_bind expects the format: 'bind_query query to store as varName'");
}
return new _2.BindDataNode(null,_21[1],_21[3],_21[5]);
}});
_2._get.safe=true;
dd.register.tags("dojox.dtl.contrib",{"data":["bind_data","bind_query"]});
})();
}
