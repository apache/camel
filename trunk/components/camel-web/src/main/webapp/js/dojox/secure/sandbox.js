/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.secure.sandbox"]){
dojo._hasResource["dojox.secure.sandbox"]=true;
dojo.provide("dojox.secure.sandbox");
dojo.require("dojox.secure.DOM");
dojo.require("dojox.secure.capability");
dojo.require("dojo.NodeList-fx");
(function(){
var _1=setTimeout;
var _2=setInterval;
if({}.__proto__){
var _3=function(_4){
var _5=Array.prototype[_4];
if(_5&&!_5.fixed){
(Array.prototype[_4]=function(){
if(this==window){
throw new TypeError("Called with wrong this");
}
return _5.apply(this,arguments);
}).fixed=true;
}
};
_3("concat");
_3("reverse");
_3("sort");
_3("slice");
_3("forEach");
_3("filter");
_3("reduce");
_3("reduceRight");
_3("every");
_3("map");
_3("some");
}
var _6=function(){
return dojo.xhrGet.apply(dojo,arguments);
};
dojox.secure.sandbox=function(_7){
var _8=dojox.secure.DOM(_7);
_7=_8(_7);
var _9=_7.ownerDocument;
var _a,_b=dojox.secure._safeDojoFunctions(_7,_8);
var _c=[];
var _d=["isNaN","isFinite","parseInt","parseFloat","escape","unescape","encodeURI","encodeURIComponent","decodeURI","decodeURIComponent","alert","confirm","prompt","Error","EvalError","RangeError","ReferenceError","SyntaxError","TypeError","Date","RegExp","Number","Object","Array","String","Math","setTimeout","setInterval","clearTimeout","clearInterval","dojo","get","set","forEach","load","evaluate"];
for(var i in _b){
_d.push(i);
_c.push("var "+i+"=dojo."+i);
}
eval(_c.join(";"));
function _f(obj,_11){
_11=""+_11;
if(dojox.secure.badProps.test(_11)){
throw new Error("bad property access");
}
if(obj.__get__){
return obj.__get__(_11);
}
return obj[_11];
};
function set(obj,_14,_15){
_14=""+_14;
_f(obj,_14);
if(obj.__set){
return obj.__set(_14);
}
obj[_14]=_15;
return _15;
};
function _16(obj,fun){
if(typeof fun!="function"){
throw new TypeError();
}
if("length" in obj){
if(obj.__get__){
var len=obj.__get__("length");
for(var i=0;i<len;i++){
if(i in obj){
fun.call(obj,obj.__get__(i),i,obj);
}
}
}else{
len=obj.length;
for(i=0;i<len;i++){
if(i in obj){
fun.call(obj,obj[i],i,obj);
}
}
}
}else{
for(i in obj){
fun.call(obj,_f(obj,i),i,obj);
}
}
};
function _1b(_1c,_1d,_1e){
var _1f,_20,_21;
var arg;
for(var i=0,l=arguments.length;typeof (arg=arguments[i])=="function"&&i<l;i++){
if(_1f){
_a(_1f,arg.prototype);
}else{
_20=arg;
var F=function(){
};
F.prototype=arg.prototype;
_1f=new F;
}
}
if(arg){
for(var j in arg){
var _27=arg[j];
if(typeof _27=="function"){
arg[j]=function(){
if(this instanceof _1b){
return arguments.callee.__rawMethod__.apply(this,arguments);
}
throw new Error("Method called on wrong object");
};
arg[j].__rawMethod__=_27;
}
}
if(arg.hasOwnProperty("constructor")){
_21=arg.constructor;
}
}
_1f=_1f?_a(_1f,arg):arg;
function _1b(){
if(_20){
_20.apply(this,arguments);
}
if(_21){
_21.apply(this,arguments);
}
};
_a(_1b,arguments[i]);
_1f.constructor=_1b;
_1b.prototype=_1f;
return _1b;
};
function _28(_29){
if(typeof _29!="function"){
throw new Error("String is not allowed in setTimeout/setInterval");
}
};
function _2a(_2b,_2c){
_28(_2b);
return _1(_2b,_2c);
};
function _2d(_2e,_2f){
_28(_2e);
return _2(_2e,_2f);
};
function _30(_31){
return _8.evaluate(_31);
};
var _32=_8.load=function(url){
if(url.match(/^[\w\s]*:/)){
throw new Error("Access denied to cross-site requests");
}
return _6({url:(new _b._Url(_8.rootUrl,url))+"",secure:true});
};
_8.evaluate=function(_34){
dojox.secure.capability.validate(_34,_d,{document:1,element:1});
if(_34.match(/^\s*[\[\{]/)){
var _35=eval("("+_34+")");
}else{
eval(_34);
}
};
return {loadJS:function(url){
_8.rootUrl=url;
return _6({url:url,secure:true}).addCallback(function(_37){
_30(_37,_7);
});
},loadHTML:function(url){
_8.rootUrl=url;
return _6({url:url,secure:true}).addCallback(function(_39){
_7.innerHTML=_39;
});
},evaluate:function(_3a){
return _8.evaluate(_3a);
}};
};
})();
dojox.secure._safeDojoFunctions=function(_3b,_3c){
var _3d=["mixin","require","isString","isArray","isFunction","isObject","isArrayLike","isAlien","hitch","delegate","partial","trim","disconnect","subscribe","unsubscribe","Deferred","toJson","style","attr"];
var doc=_3b.ownerDocument;
var _3f=dojox.secure.unwrap;
dojo.NodeList.prototype.addContent.safetyCheck=function(_40){
_3c.safeHTML(_40);
};
dojo.NodeList.prototype.style.safetyCheck=function(_41,_42){
if(_41=="behavior"){
throw new Error("Can not set behavior");
}
_3c.safeCSS(_42);
};
dojo.NodeList.prototype.attr.safetyCheck=function(_43,_44){
if(_44&&(_43=="src"||_43=="href"||_43=="style")){
throw new Error("Illegal to set "+_43);
}
};
var _45={query:function(_46,_47){
return _3c(dojo.query(_46,_3f(_47||_3b)));
},connect:function(el,_49){
var obj=el;
arguments[0]=_3f(el);
if(obj!=arguments[0]&&_49.substring(0,2)!="on"){
throw new Error("Invalid event name for element");
}
return dojo.connect.apply(dojo,arguments);
},body:function(){
return _3b;
},byId:function(id){
return _3b.ownerDocument.getElementById(id);
},fromJson:function(str){
dojox.secure.capability.validate(str,[],{});
return dojo.fromJson(str);
}};
for(var i=0;i<_3d.length;i++){
_45[_3d[i]]=dojo[_3d[i]];
}
return _45;
};
}
