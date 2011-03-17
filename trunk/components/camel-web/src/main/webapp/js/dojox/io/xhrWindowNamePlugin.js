/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.io.xhrWindowNamePlugin"]){
dojo._hasResource["dojox.io.xhrWindowNamePlugin"]=true;
dojo.provide("dojox.io.xhrWindowNamePlugin");
dojo.require("dojox.io.xhrPlugins");
dojo.require("dojox.io.windowName");
dojo.require("dojox.io.httpParse");
dojo.require("dojox.secure.capability");
dojox.io.xhrWindowNamePlugin=function(_1,_2,_3){
dojox.io.xhrPlugins.register("windowName",function(_4,_5){
return _5.sync!==true&&(_4=="GET"||_4=="POST"||_2)&&(_5.url.substring(0,_1.length)==_1);
},function(_6,_7,_8){
var _9=dojox.io.windowName.send;
var _a=(_2?_2(_9,true):_9)(_6,_7,_8);
_a.addCallback(function(_b){
var _c=_a.ioArgs;
_c.xhr={getResponseHeader:function(_d){
return dojo.queryToObject(_c.hash.match(/[^#]*$/)[0])[_d];
}};
if(_c.handleAs=="json"){
if(!_3){
dojox.secure.capability.validate(_b,["Date"],{});
}
return dojo.fromJson(_b);
}
return dojo._contentHandlers[_c.handleAs||"text"]({responseText:_b});
});
return _a;
});
};
}
