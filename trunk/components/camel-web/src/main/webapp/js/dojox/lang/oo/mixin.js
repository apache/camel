/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.oo.mixin"]){
dojo._hasResource["dojox.lang.oo.mixin"]=true;
dojo.provide("dojox.lang.oo.mixin");
dojo.experimental("dojox.lang.oo.mixin");
dojo.require("dojox.lang.oo.Filter");
dojo.require("dojox.lang.oo.Decorator");
(function(){
var oo=dojox.lang.oo,_2=oo.Filter,_3=oo.Decorator,_4={},_5=function(_6){
return _6;
},_7=function(_8,_9,_a){
return _9;
},_b=function(_c,_d,_e,_f){
_c[_d]=_e;
},_10={},_11=oo.applyDecorator=function(_12,_13,_14,_15){
if(_14 instanceof _3){
var d=_14.decorator;
_14=_11(_12,_13,_14.value,_15);
return d(_13,_14,_15);
}
return _12(_13,_14,_15);
};
oo.__mixin=function(_17,_18,_19,_1a,_1b){
var _1c,_1d,_1e,_1f,_20;
for(_1c in _18){
if(!(_1c in _4)){
_1e=_18[_1c];
_1d=_1a(_1c,_17,_18,_1e);
if(_1d){
_20=_17[_1d];
_1f=_11(_19,_1d,_1e,_20);
if(_20!==_1f){
_1b(_17,_1d,_1f,_20);
}
}
}
}
return _17;
};
oo.mixin=function(_21,_22){
var _23,_24,i=1,l=arguments.length;
for(;i<l;++i){
_22=arguments[i];
if(_22 instanceof _2){
_24=_22.filter;
_22=_22.bag;
}else{
_24=_5;
}
if(_22 instanceof _3){
_23=_22.decorator;
_22=_22.value;
}else{
_23=_7;
}
oo.__mixin(_21,_22,_23,_24,_b);
}
return _21;
};
})();
}
