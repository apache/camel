/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.WikipediaStore"]){
dojo._hasResource["dojox.data.WikipediaStore"]=true;
dojo.provide("dojox.data.WikipediaStore");
dojo.require("dojo.io.script");
dojo.require("dojox.rpc.Service");
dojo.require("dojox.data.ServiceStore");
dojo.experimental("dojox.data.WikipediaStore");
dojo.declare("dojox.data.WikipediaStore",dojox.data.ServiceStore,{constructor:function(_1){
if(_1&&_1.service){
this.service=_1.service;
}else{
var _2=new dojox.rpc.Service(dojo.moduleUrl("dojox.rpc.SMDLibrary","wikipedia.smd"));
this.service=_2.query;
}
this.idAttribute=this.labelAttribute="title";
},fetch:function(_3){
var rq=dojo.mixin({},_3.query);
if(rq&&(!rq.action||rq.action==="parse")){
rq.action="parse";
rq.page=rq.title;
delete rq.title;
}else{
if(rq.action==="query"){
rq.list="search";
rq.srwhat="text";
rq.srsearch=rq.text;
if(_3.start){
rq.sroffset=_3.start-1;
}
if(_3.count){
rq.srlimit=_3.count>=500?500:_3.count;
}
delete rq.text;
}
}
_3.query=rq;
return this.inherited(arguments);
},_processResults:function(_5,_6){
if(_5.parse){
_5.parse.title=dojo.queryToObject(_6.ioArgs.url.split("?")[1]).page;
_5=[_5.parse];
}else{
if(_5.query&&_5.query.search){
_5=_5.query.search;
var _7=this;
for(var i in _5){
_5[i]._loadObject=function(_9){
_7.fetch({query:{action:"parse",title:this.title},onItem:_9});
delete this._loadObject;
};
}
}
}
return this.inherited(arguments);
}});
}
