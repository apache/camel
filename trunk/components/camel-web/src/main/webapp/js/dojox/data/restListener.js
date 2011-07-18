/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.restListener"]){
dojo._hasResource["dojox.data.restListener"]=true;
dojo.provide("dojox.data.restListener");
dojox.data.restListener=function(_1){
var _2=_1.channel;
var jr=dojox.rpc.JsonRest;
var _4=jr.getServiceAndId(_2).service;
var _5=dojox.json.ref.resolveJson(_1.result,{defaultId:_1.event=="put"&&_2,index:dojox.rpc.Rest._index,idPrefix:_4.servicePath,idAttribute:jr.getIdAttribute(_4),schemas:jr.schemas,loader:jr._loader,assignAbsoluteIds:true});
var _6=dojox.rpc.Rest._index&&dojox.rpc.Rest._index[_2];
var _7="on"+_1.event.toLowerCase();
var _8=_4&&_4._store;
if(_6){
if(_6[_7]){
_6[_7](_5);
return;
}
}
if(_8){
switch(_7){
case "onpost":
_8.onNew(_5);
break;
case "ondelete":
_8.onDelete(_6);
break;
}
}
};
}
