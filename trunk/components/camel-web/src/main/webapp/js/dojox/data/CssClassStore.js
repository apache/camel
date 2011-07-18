/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.CssClassStore"]){
dojo._hasResource["dojox.data.CssClassStore"]=true;
dojo.provide("dojox.data.CssClassStore");
dojo.require("dojox.data.CssRuleStore");
dojo.declare("dojox.data.CssClassStore",dojox.data.CssRuleStore,{_labelAttribute:"class",_idAttribute:"class",_cName:"dojox.data.CssClassStore",getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
},getAttributes:function(_1){
this._assertIsItem(_1);
return ["class","classSans"];
},getValue:function(_2,_3,_4){
var _5=this.getValues(_2,_3);
if(_5&&_5.length>0){
return _5[0];
}
return _4;
},getValues:function(_6,_7){
this._assertIsItem(_6);
this._assertIsAttribute(_7);
var _8=[];
if(_7==="class"){
_8=[_6.className];
}else{
if(_7==="classSans"){
_8=[_6.className.replace(/\./g,"")];
}
}
return _8;
},_handleRule:function(_9,_a,_b){
var _c={};
var s=_9["selectorText"].split(" ");
for(var j=0;j<s.length;j++){
var _f=s[j];
var _10=_f.indexOf(".");
if(_f&&_f.length>0&&_10!==-1){
var _11=_f.indexOf(",")||_f.indexOf("[");
_f=_f.substring(_10,((_11!==-1&&_11>_10)?_11:_f.length));
_c[_f]=true;
}
}
for(var key in _c){
if(!this._allItems[key]){
var _13={};
_13.className=key;
_13[this._storeRef]=this;
this._allItems[key]=_13;
}
}
},_handleReturn:function(){
var _14=[];
var _15={};
for(var i in this._allItems){
_15[i]=this._allItems[i];
}
var _17;
while(this._pending.length){
_17=this._pending.pop();
_17.request._items=_15;
_14.push(_17);
}
while(_14.length){
_17=_14.pop();
if(_17.fetch){
this._handleFetchReturn(_17.request);
}else{
this._handleFetchByIdentityReturn(_17.request);
}
}
},_handleFetchByIdentityReturn:function(_18){
var _19=_18._items;
var _1a=_19[(dojo.isWebKit?_18.identity.toLowerCase():_18.identity)];
if(!this.isItem(_1a)){
_1a=null;
}
if(_18.onItem){
var _1b=_18.scope||dojo.global;
_18.onItem.call(_1b,_1a);
}
},getIdentity:function(_1c){
this._assertIsItem(_1c);
return this.getValue(_1c,this._idAttribute);
},getIdentityAttributes:function(_1d){
this._assertIsItem(_1d);
return [this._idAttribute];
},fetchItemByIdentity:function(_1e){
_1e=_1e||{};
if(!_1e.store){
_1e.store=this;
}
if(this._pending&&this._pending.length>0){
this._pending.push({request:_1e});
}else{
this._pending=[{request:_1e}];
this._fetch(_1e);
}
return _1e;
}});
}
