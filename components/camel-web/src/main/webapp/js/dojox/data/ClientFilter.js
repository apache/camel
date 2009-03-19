/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.ClientFilter"]){
dojo._hasResource["dojox.data.ClientFilter"]=true;
dojo.provide("dojox.data.ClientFilter");
dojo.require("dojo.data.util.filter");
(function(){
var cf;
var _2=function(_3,_4,_5){
return function(_6){
_3._updates.push({create:_4&&_6,remove:_5&&_6});
cf.onUpdate();
};
};
cf=dojo.declare("dojox.data.ClientFilter",null,{constructor:function(){
this.onSet=_2(this,true,true);
this.onNew=_2(this,true,false);
this.onDelete=_2(this,false,true);
this._updates=[];
this._fetchCache=[];
},updateResultSet:function(_7,_8){
if(this.isUpdateable(_8)){
for(var i=_8._version||0;i<this._updates.length;i++){
var _a=this._updates[i].create;
var _b=this._updates[i].remove;
if(_b){
for(var j=0;j<_7.length;j++){
if(_7[j]==_b){
_7.splice(j--,1);
var _d=true;
}
}
}
if(_a&&this.matchesQuery(_a,_8)&&dojo.indexOf(_7,_a)==-1){
_7.push(_a);
_d=true;
}
}
if(_8.sort&&_d){
_7.sort(this.makeComparator(_8.sort.concat()));
}
if(_8.count&&_d){
_7.splice(_8.count,_7.length);
}
_8._version=this._updates.length;
return _d?2:1;
}
return 0;
},querySuperSet:function(_e,_f){
if(_e.query==_f.query){
return {};
}
if(!(_f.query instanceof Object&&(!_e.query||typeof _e.query=="object"))){
return false;
}
var _10=dojo.mixin({},_f.query);
for(var i in _e.query){
if(_10[i]==_e.query[i]){
delete _10[i];
}else{
if(!(typeof _e.query[i]=="string"&&dojo.data.util.filter.patternToRegExp(_e.query[i]).test(_10[i]))){
return false;
}
}
}
return _10;
},serverVersion:0,cachingFetch:function(_12){
var _13=this;
for(var i=0;i<this._fetchCache.length;i++){
var _15=this._fetchCache[i];
var _16=this.querySuperSet(_15,_12);
if(_16!==false){
var _17=_15._loading;
if(!_17){
_17=new dojo.Deferred();
_17.callback(_15.cacheResults);
}
_17.addCallback(function(_18){
_18=_13.clientSideFetch({query:_16,sort:_12.sort,start:_12.start,count:_12.count},_18);
_17.fullLength=_18._fullLength;
return _18;
});
}
}
if(!_17){
var _19=dojo.mixin({},_12);
var _1a=(_12.queryOptions||0).cache;
if(_1a===undefined?this.cacheByDefault:_1a){
if(_12.start||_12.count){
delete _19.start;
delete _19.count;
_12.clientQuery=dojo.mixin(_12.clientQuery||{},{start:_12.start,count:_12.count});
}
_12=_19;
this._fetchCache.push(_12);
}
_17=_12._loading=this._doQuery(_12);
}
var _1b=this.serverVersion;
_17.addCallback(function(_1c){
delete _12._loading;
if(_1c){
_12._version=_1b;
_13.updateResultSet(_1c,_12);
_12.cacheResults=_1c;
}
return _1c;
});
return _17;
},isUpdateable:function(_1d){
return typeof _1d.query=="object";
},clientSideFetch:function(_1e,_1f){
if(_1e.query){
var _20=[];
for(var i=0;i<_1f.length;i++){
var _22=_1f[i];
if(_22&&this.matchesQuery(_22,_1e)){
_20.push(_1f[i]);
}
}
}else{
_20=_1e.sort?_1f.concat():_1f;
}
if(_1e.sort){
_20.sort(this.makeComparator(_1e.sort.concat()));
}
return this.clientSidePaging(_1e,_20);
},clientSidePaging:function(_23,_24){
var _25=_23.start||0;
var _26=(_25||_23.count)?_24.slice(_25,_25+(_23.count||_24.length)):_24;
_26._fullLength=_24.length;
return _26;
},matchesQuery:function(_27,_28){
var _29=_28.query;
var _2a=_28.queryOptions&&_28.queryOptions.ignoreCase;
for(var i in _29){
var _2c=_29[i];
var _2d=this.getValue(_27,i);
if((typeof _2c=="string"&&(_2c.match(/[\*\.]/)||_2a))?!dojo.data.util.filter.patternToRegExp(_2c,_2a).test(_2d):_2d!=_2c){
return false;
}
}
return true;
},makeComparator:function(_2e){
var _2f=_2e.shift();
if(!_2f){
return function(){
};
}
var _30=_2f.attribute;
var _31=!!_2f.descending;
var _32=this.makeComparator(_2e);
var _33=this;
return function(a,b){
var av=_33.getValue(a,_30);
var bv=_33.getValue(b,_30);
if(av!=bv){
return av<bv==_31?1:-1;
}
return _32(a,b);
};
}});
cf.onUpdate=function(){
};
})();
}
