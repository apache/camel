/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.RailsStore"]){
dojo._hasResource["dojox.data.RailsStore"]=true;
dojo.provide("dojox.data.RailsStore");
dojo.require("dojox.data.JsonRestStore");
dojo.declare("dojox.data.RailsStore",dojox.data.JsonRestStore,{constructor:function(){
},preamble:function(_1){
if(typeof _1.target=="string"&&!_1.service){
var _2=_1.target.replace(/\/$/g,"");
var _3=function(id,_5){
_5=_5||{};
var _6=_2;
var _7;
var _8;
if(dojo.isObject(id)){
_8="";
_7="?"+dojo.objectToQuery(id);
}else{
if(_5.queryStr&&_5.queryStr.indexOf("?")!=-1){
_8=_5.queryStr.replace(/\?.*/,"");
_7=_5.queryStr.replace(/[^?]*\?/g,"?");
}else{
if(dojo.isString(_5.query)&&_5.query.indexOf("?")!=-1){
_8=_5.query.replace(/\?.*/,"");
_7=_5.query.replace(/[^?]*\?/g,"?");
}else{
_8=id?id.toString():"";
_7="";
}
}
}
if(_8.indexOf("=")!=-1){
_7=_8;
_8="";
}
if(_8){
_6=_6+"/"+_8+".json"+_7;
}else{
_6=_6+".json"+_7;
}
var _9=dojox.rpc._sync;
dojox.rpc._sync=false;
return {url:_6,handleAs:"json",contentType:"application/json",sync:_9,headers:{Accept:"application/json,application/javascript",Range:_5&&(_5.start>=0||_5.count>=0)?"items="+(_5.start||"0")+"-"+((_5.count&&(_5.count+(_5.start||0)-1))||""):undefined}};
};
_1.service=dojox.rpc.Rest(this.target,true,null,_3);
}
},fetch:function(_a){
_a=_a||{};
function _b(_c){
function _d(){
if(_a.queryStr==null){
_a.queryStr="";
}
if(dojo.isObject(_a.query)){
_a.queryStr="?"+dojo.objectToQuery(_a.query);
}else{
if(dojo.isString(_a.query)){
_a.queryStr=_a.query;
}
}
};
function _e(){
if(_a.queryStr.indexOf("?")==-1){
return "?";
}else{
return "&";
}
};
if(_a.queryStr==null){
_d();
}
_a.queryStr=_a.queryStr+_e()+dojo.objectToQuery(_c);
};
if(_a.start||_a.count){
if((_a.start||0)%_a.count){
throw new Error("The start parameter must be a multiple of the count parameter");
}
_b({page:((_a.start||0)/_a.count)+1,per_page:_a.count});
}
if(_a.sort){
var _f={sortBy:[],sortDir:[]};
dojo.forEach(_a.sort,function(_10){
_f.sortBy.push(_10.attribute);
_f.sortDir.push(!!_10.descending?"DESC":"ASC");
});
_b(_f);
delete _a.sort;
}
return this.inherited(arguments);
}});
}
