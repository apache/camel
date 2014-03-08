/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.S3Store"]){
dojo._hasResource["dojox.data.S3Store"]=true;
dojo.provide("dojox.data.S3Store");
dojo.require("dojox.rpc.ProxiedPath");
dojo.require("dojox.data.JsonRestStore");
dojo.declare("dojox.data.S3Store",dojox.data.JsonRestStore,{_processResults:function(_1){
var _2=_1.getElementsByTagName("Key");
var _3=[];
var _4=this;
for(var i=0;i<_2.length;i++){
var _6=_2[i];
var _7={_loadObject:(function(_8,_9){
return function(_a){
delete this._loadObject;
_4.service(_8).addCallback(_a);
};
})(_6.firstChild.nodeValue,_7)};
_3.push(_7);
}
return {totalCount:_3.length,items:_3};
}});
}
