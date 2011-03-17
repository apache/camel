/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.QueryReadStore"]){
dojo._hasResource["dojox.data.QueryReadStore"]=true;
dojo.provide("dojox.data.QueryReadStore");
dojo.require("dojo.string");
dojo.require("dojo.data.util.sorter");
dojo.declare("dojox.data.QueryReadStore",null,{url:"",requestMethod:"get",_className:"dojox.data.QueryReadStore",_items:[],_lastServerQuery:null,_numRows:-1,lastRequestHash:null,doClientPaging:false,doClientSorting:false,_itemsByIdentity:null,_identifier:null,_features:{"dojo.data.api.Read":true,"dojo.data.api.Identity":true},_labelAttr:"label",constructor:function(_1){
dojo.mixin(this,_1);
},getValue:function(_2,_3,_4){
this._assertIsItem(_2);
if(!dojo.isString(_3)){
throw new Error(this._className+".getValue(): Invalid attribute, string expected!");
}
if(!this.hasAttribute(_2,_3)){
if(_4){
return _4;
}

}
return _2.i[_3];
},getValues:function(_5,_6){
this._assertIsItem(_5);
var _7=[];
if(this.hasAttribute(_5,_6)){
_7.push(_5.i[_6]);
}
return _7;
},getAttributes:function(_8){
this._assertIsItem(_8);
var _9=[];
for(var i in _8.i){
_9.push(i);
}
return _9;
},hasAttribute:function(_b,_c){
return this.isItem(_b)&&typeof _b.i[_c]!="undefined";
},containsValue:function(_d,_e,_f){
var _10=this.getValues(_d,_e);
var len=_10.length;
for(var i=0;i<len;i++){
if(_10[i]==_f){
return true;
}
}
return false;
},isItem:function(_13){
if(_13){
return typeof _13.r!="undefined"&&_13.r==this;
}
return false;
},isItemLoaded:function(_14){
return this.isItem(_14);
},loadItem:function(_15){
if(this.isItemLoaded(_15.item)){
return;
}
},fetch:function(_16){
_16=_16||{};
if(!_16.store){
_16.store=this;
}
var _17=this;
var _18=function(_19,_1a){
if(_1a.onError){
var _1b=_1a.scope||dojo.global;
_1a.onError.call(_1b,_19,_1a);
}
};
var _1c=function(_1d,_1e,_1f){
var _20=_1e.abort||null;
var _21=false;
var _22=_1e.start?_1e.start:0;
if(_17.doClientPaging==false){
_22=0;
}
var _23=_1e.count?(_22+_1e.count):_1d.length;
_1e.abort=function(){
_21=true;
if(_20){
_20.call(_1e);
}
};
var _24=_1e.scope||dojo.global;
if(!_1e.store){
_1e.store=_17;
}
if(_1e.onBegin){
_1e.onBegin.call(_24,_1f,_1e);
}
if(_1e.sort&&_17.doClientSorting){
_1d.sort(dojo.data.util.sorter.createSortFunction(_1e.sort,_17));
}
if(_1e.onItem){
for(var i=_22;(i<_1d.length)&&(i<_23);++i){
var _26=_1d[i];
if(!_21){
_1e.onItem.call(_24,_26,_1e);
}
}
}
if(_1e.onComplete&&!_21){
var _27=null;
if(!_1e.onItem){
_27=_1d.slice(_22,_23);
}
_1e.onComplete.call(_24,_27,_1e);
}
};
this._fetchItems(_16,_1c,_18);
return _16;
},getFeatures:function(){
return this._features;
},close:function(_28){
},getLabel:function(_29){
if(this._labelAttr&&this.isItem(_29)){
return this.getValue(_29,this._labelAttr);
}
return undefined;
},getLabelAttributes:function(_2a){
if(this._labelAttr){
return [this._labelAttr];
}
return null;
},_xhrFetchHandler:function(_2b,_2c,_2d,_2e){
_2b=this._filterResponse(_2b);
if(_2b.label){
this._labelAttr=_2b.label;
}
var _2f=_2b.numRows||-1;
this._items=[];
dojo.forEach(_2b.items,function(e){
this._items.push({i:e,r:this});
},this);
var _31=_2b.identifier;
this._itemsByIdentity={};
if(_31){
this._identifier=_31;
var i;
for(i=0;i<this._items.length;++i){
var _33=this._items[i].i;
var _34=_33[_31];
if(!this._itemsByIdentity[_34]){
this._itemsByIdentity[_34]=_33;
}else{
throw new Error(this._className+":  The json data as specified by: ["+this.url+"] is malformed.  Items within the list have identifier: ["+_31+"].  Value collided: ["+_34+"]");
}
}
}else{
this._identifier=Number;
for(i=0;i<this._items.length;++i){
this._items[i].n=i;
}
}
_2f=this._numRows=(_2f===-1)?this._items.length:_2f;
_2d(this._items,_2c,_2f);
this._numRows=_2f;
},_fetchItems:function(_35,_36,_37){
var _38=_35.serverQuery||_35.query||{};
if(!this.doClientPaging){
_38.start=_35.start||0;
if(_35.count){
_38.count=_35.count;
}
}
if(!this.doClientSorting){
if(_35.sort){
var _39=_35.sort[0];
if(_39&&_39.attribute){
var _3a=_39.attribute;
if(_39.descending){
_3a="-"+_3a;
}
_38.sort=_3a;
}
}
}
if(this.doClientPaging&&this._lastServerQuery!==null&&dojo.toJson(_38)==dojo.toJson(this._lastServerQuery)){
this._numRows=(this._numRows===-1)?this._items.length:this._numRows;
_36(this._items,_35,this._numRows);
}else{
var _3b=this.requestMethod.toLowerCase()=="post"?dojo.xhrPost:dojo.xhrGet;
var _3c=_3b({url:this.url,handleAs:"json-comment-optional",content:_38});
_3c.addCallback(dojo.hitch(this,function(_3d){
this._xhrFetchHandler(_3d,_35,_36,_37);
}));
_3c.addErrback(function(_3e){
_37(_3e,_35);
});
this.lastRequestHash=new Date().getTime()+"-"+String(Math.random()).substring(2);
this._lastServerQuery=dojo.mixin({},_38);
}
},_filterResponse:function(_3f){
return _3f;
},_assertIsItem:function(_40){
if(!this.isItem(_40)){
throw new Error(this._className+": Invalid item argument.");
}
},_assertIsAttribute:function(_41){
if(typeof _41!=="string"){
throw new Error(this._className+": Invalid attribute argument ('"+_41+"').");
}
},fetchItemByIdentity:function(_42){
if(this._itemsByIdentity){
var _43=this._itemsByIdentity[_42.identity];
if(!(_43===undefined)){
if(_42.onItem){
var _44=_42.scope?_42.scope:dojo.global;
_42.onItem.call(_44,{i:_43,r:this});
}
return;
}
}
var _45=function(_46,_47){
var _48=_42.scope?_42.scope:dojo.global;
if(_42.onError){
_42.onError.call(_48,_46);
}
};
var _49=function(_4a,_4b){
var _4c=_42.scope?_42.scope:dojo.global;
try{
var _4d=null;
if(_4a&&_4a.length==1){
_4d=_4a[0];
}
if(_42.onItem){
_42.onItem.call(_4c,_4d);
}
}
catch(error){
if(_42.onError){
_42.onError.call(_4c,error);
}
}
};
var _4e={serverQuery:{id:_42.identity}};
this._fetchItems(_4e,_49,_45);
},getIdentity:function(_4f){
var _50=null;
if(this._identifier===Number){
_50=_4f.n;
}else{
_50=_4f.i[this._identifier];
}
return _50;
},getIdentityAttributes:function(_51){
return [this._identifier];
}});
}
