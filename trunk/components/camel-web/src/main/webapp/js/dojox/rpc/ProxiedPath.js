/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.rpc.ProxiedPath"]){
dojo._hasResource["dojox.rpc.ProxiedPath"]=true;
dojo.provide("dojox.rpc.ProxiedPath");
dojo.require("dojox.rpc.Service");
dojox.rpc.envelopeRegistry.register("PROXIED-PATH",function(_1){
return _1=="PROXIED-PATH";
},{serialize:function(_2,_3,_4){
var i;
var _6=dojox.rpc.getTarget(_2,_3);
if(dojo.isArray(_4)){
for(i=0;i<_4.length;i++){
_6+="/"+(_4[i]==null?"":_4[i]);
}
}else{
for(i in _4){
_6+="/"+i+"/"+_4[i];
}
}
return {data:"",target:(_3.proxyUrl||_2.proxyUrl)+"?url="+encodeURIComponent(_6)};
},deserialize:function(_7){
return _7;
}});
}
