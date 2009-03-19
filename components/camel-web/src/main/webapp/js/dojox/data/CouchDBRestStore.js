/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.CouchDBRestStore"]){
dojo._hasResource["dojox.data.CouchDBRestStore"]=true;
dojo.provide("dojox.data.CouchDBRestStore");
dojo.require("dojox.data.JsonRestStore");
dojo.declare("dojox.data.CouchDBRestStore",dojox.data.JsonRestStore,{save:function(_1){
var _2=this.inherited(arguments);
var _3=this.service.servicePath;
for(var i=0;i<_2.length;i++){
(function(_5,_6){
_6.addCallback(function(_7){
if(_7){
_5.__id=_3+_7.id;
_5._rev=_7.rev;
}
return _7;
});
})(_2[i].content,_2[i].deferred);
}
},fetch:function(_8){
_8.query=_8.query||"_all_docs?";
if(_8.start){
_8.query=(_8.query?(_8.query+"&"):"")+"startkey="+_8.start;
delete _8.start;
}
if(_8.count){
_8.query=(_8.query?(_8.query+"&"):"")+"count="+_8.count;
delete _8.count;
}
return this.inherited(arguments);
},_processResults:function(_9){
var _a=_9.rows;
if(_a){
var _b=this.service.servicePath;
var _c=this;
for(var i=0;i<_a.length;i++){
_a[i]={__id:_b+_a[i].id,_id:_a[i].id,_loadObject:function(_e){
_c.fetchItemByIdentity({identity:this._id,onItem:_e});
delete this._loadObject;
}};
}
return {totalCount:_9.total_rows,items:_9.rows};
}else{
return {items:_9};
}
}});
dojox.data.CouchDBRestStore.getStores=function(_f){
var dfd=dojo.xhrGet({url:_f+"_all_dbs",handleAs:"json",sync:true});
var _11={};
dfd.addBoth(function(dbs){
for(var i=0;i<dbs.length;i++){
_11[dbs[i]]=new dojox.data.CouchDBRestStore({target:_f+dbs[i],idAttribute:"_id"});
}
return _11;
});
return _11;
};
}
