/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.utils"]){
dojo._hasResource["dojox.lang.utils"]=true;
dojo.provide("dojox.lang.utils");
(function(){
var _1={},du=dojox.lang.utils;
var _3=function(o){
if(dojo.isArray(o)){
return dojo._toArray(o);
}
if(!dojo.isObject(o)||dojo.isFunction(o)){
return o;
}
return dojo.delegate(o);
};
dojo.mixin(du,{coerceType:function(_5,_6){
switch(typeof _5){
case "number":
return Number(eval("("+_6+")"));
case "string":
return String(_6);
case "boolean":
return Boolean(eval("("+_6+")"));
}
return eval("("+_6+")");
},updateWithObject:function(_7,_8,_9){
if(!_8){
return _7;
}
for(var x in _7){
if(x in _8&&!(x in _1)){
var t=_7[x];
if(t&&typeof t=="object"){
du.updateWithObject(t,_8[x],_9);
}else{
_7[x]=_9?du.coerceType(t,_8[x]):_3(_8[x]);
}
}
}
return _7;
},updateWithPattern:function(_c,_d,_e,_f){
if(!_d||!_e){
return _c;
}
for(var x in _e){
if(x in _d&&!(x in _1)){
_c[x]=_f?du.coerceType(_e[x],_d[x]):_3(_d[x]);
}
}
return _c;
}});
})();
}
