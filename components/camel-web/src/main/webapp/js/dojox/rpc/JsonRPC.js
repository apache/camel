/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.rpc.JsonRPC"]){
dojo._hasResource["dojox.rpc.JsonRPC"]=true;
dojo.provide("dojox.rpc.JsonRPC");
dojo.require("dojox.rpc.Service");
(function(){
function _1(_2){
return {serialize:function(_3,_4,_5,_6){
var d={id:this._requestId++,method:_4.name,params:_5};
if(_2){
d.jsonrpc=_2;
}
return {data:dojo.toJson(d),handleAs:"json",contentType:"application/json",transport:"POST"};
},deserialize:function(_8){
if("Error"==_8.name){
_8=dojo.fromJson(_8.responseText);
}
if(_8.error){
var e=new Error(_8.error.message||_8.error);
e._rpcErrorObject=_8.error;
return e;
}
return _8.result;
}};
};
dojox.rpc.envelopeRegistry.register("JSON-RPC-1.0",function(_a){
return _a=="JSON-RPC-1.0";
},dojo.mixin({namedParams:false},_1()));
dojox.rpc.envelopeRegistry.register("JSON-RPC-2.0",function(_b){
return _b=="JSON-RPC-2.0";
},_1("2.0"));
})();
}
