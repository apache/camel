/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.util.JsonQuery"]){
dojo._hasResource["dojox.data.util.JsonQuery"]=true;
dojo.provide("dojox.data.util.JsonQuery");
dojo.declare("dojox.data.util.JsonQuery",null,{useFullIdInQueries:false,_toJsonQuery:function(_1,_2){
var _3=true;
var _4=this;
function _5(_6,_7){
if(_7.__id){
var _8={};
_8[_4.idAttribute]=_4.useFullIdInQueries?_7.__id:_7[_4.idAttribute];
_7=_8;
}
for(var i in _7){
var _a=_7[i];
var _b=_6+(/^[a-zA-Z_][\w_]*$/.test(i)?"."+i:"["+dojo._escapeString(i)+"]");
if(_a&&typeof _a=="object"){
_5(_b,_a);
}else{
if(_a!="*"){
_c+=(_3?"":"&")+_b+((_1.queryOptions&&_1.queryOptions.ignoreCase)?"~":"=")+dojo.toJson(_a);
_3=false;
}
}
}
};
if(_1.query&&typeof _1.query=="object"){
var _c="[?(";
_5("@",_1.query);
if(!_3){
_c+=")]";
}else{
_c="";
}
_1.queryStr=_c.replace(/\\"|"/g,function(t){
return t=="\""?"'":t;
});
}else{
if(!_1.query||_1.query=="*"){
_1.query="";
}
}
var _e=_1.sort;
if(_e){
_1.queryStr=_1.queryStr||(typeof _1.query=="string"?_1.query:"");
_3=true;
for(i=0;i<_e.length;i++){
_1.queryStr+=(_3?"[":",")+(_e[i].descending?"\\":"/")+"@["+dojo._escapeString(_e[i].attribute)+"]";
_3=false;
}
if(!_3){
_1.queryStr+="]";
}
}
if(_2&&(_1.start||_1.count)){
_1.queryStr=(_1.queryStr||(typeof _1.query=="string"?_1.query:""))+"["+(_1.start||"")+":"+(_1.count?(_1.start||0)+_1.count:"")+"]";
}
if(typeof _1.queryStr=="string"){
_1.queryStr=_1.queryStr.replace(/\\"|"/g,function(t){
return t=="\""?"'":t;
});
return _1.queryStr;
}
return _1.query;
},jsonQueryPagination:true,fetch:function(_10){
this._toJsonQuery(_10,this.jsonQueryPagination);
return this.inherited(arguments);
},isUpdateable:function(){
return true;
},matchesQuery:function(_11,_12){
_12._jsonQuery=_12._jsonQuery||dojox.json.query(this._toJsonQuery(_12));
return _12._jsonQuery([_11]).length;
},clientSideFetch:function(_13,_14){
_13._jsonQuery=_13._jsonQuery||dojox.json.query(this._toJsonQuery(_13));
return this.clientSidePaging(_13,_13._jsonQuery(_14));
},querySuperSet:function(_15,_16){
if(!_15.query){
return _16.query;
}
return this.inherited(arguments);
}});
}
