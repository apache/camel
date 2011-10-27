/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.aspect.memoizer"]){
dojo._hasResource["dojox.lang.aspect.memoizer"]=true;
dojo.provide("dojox.lang.aspect.memoizer");
(function(){
var _1=dojox.lang.aspect;
var _2={around:function(_3){
var _4=_1.getContext(),_5=_4.joinPoint,_6=_4.instance,t,u,_9;
if((t=_6.__memoizerCache)&&(t=t[_5.targetName])&&(_3 in t)){
return t[_3];
}
var _9=_1.proceed.apply(null,arguments);
if(!(t=_6.__memoizerCache)){
t=_6.__memoizerCache={};
}
if(!(u=t[_5.targetName])){
u=t[_5.targetName]={};
}
return u[_3]=_9;
}};
var _a=function(_b){
return {around:function(){
var _c=_1.getContext(),_d=_c.joinPoint,_e=_c.instance,t,u,ret,key=_b.apply(_e,arguments);
if((t=_e.__memoizerCache)&&(t=t[_d.targetName])&&(key in t)){
return t[key];
}
var ret=_1.proceed.apply(null,arguments);
if(!(t=_e.__memoizerCache)){
t=_e.__memoizerCache={};
}
if(!(u=t[_d.targetName])){
u=t[_d.targetName]={};
}
return u[key]=ret;
}};
};
_1.memoizer=function(_13){
return arguments.length==0?_2:_a(_13);
};
})();
}
