/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.mix"]){
dojo._hasResource["dojox.lang.mix"]=true;
dojo.provide("dojox.lang.mix");
(function(){
var _1={},_2=dojox.lang.mix;
_2.processProps=function(_3,_4,_5){
if(_3){
var t,i,j,l;
if(_5){
if(dojo.isArray(_5)){
for(j=0,l=_5.length;j<l;++j){
delete _3[_5[j]];
}
}else{
for(i in _5){
if(_5.hasOwnProperty(i)){
delete _3[i];
}
}
}
}
if(_4){
for(i in _4){
if(_4.hasOwnProperty(i)&&_3.hasOwnProperty(i)){
t=_3[i];
delete _3[i];
_3[_4[i]]=t;
}
}
}
}
return _3;
};
var _a=function(_b,_c,_d){
this.value=_b;
this.rename=_c||_1;
if(_d&&dojo.isArray(_d)){
var p={};
for(var j=0,l=_d.length;j<l;++j){
p[_d[j]]=1;
}
this.skip=p;
}else{
this.skip=_d||_1;
}
};
dojo.extend(_a,{filter:function(_11){
if(this.skip.hasOwnProperty(_11)){
return "";
}
return this.rename.hasOwnProperty(_11)?this.rename[_11]:_11;
}});
var _12=function(_13){
this.value=_13;
};
dojo.extend(_12,{process:function(_14,_15){
if(this.value instanceof _12){
this.value.process(_14,_15);
}else{
_14[_15]=this.value;
}
}});
_2.mixer=function(_16,_17){
var dcr=null,flt=null,i,l=arguments.length,_1c,_1d,_1e;
for(i=1,l;i<l;++i){
_17=arguments[i];
if(_17 instanceof _12){
dcr=_17;
_17=dcr.value;
}
if(_17 instanceof _a){
flt=_17;
_17=flt.value;
}
for(_1c in _17){
if(_17.hasOwnProperty(_1c)){
_1d=_17[_1c];
_1e=flt?flt.filter(_1c):_1c;
if(!_1e){
continue;
}
if(_1d instanceof _12){
_1d.process(_16,_1e);
}else{
if(dcr){
dcr.value=_1d;
dcr.process(_16,_1e);
}else{
_16[_1e]=_1d;
}
}
}
}
if(flt){
_17=flt;
flt=null;
}
if(dcr){
dcr.value=_17;
dcr=null;
}
}
return _16;
};
_2.makeFilter=function(_1f){
dojo.declare("dojox.__temp__",_a,_1f||_1);
var t=dojox.__temp__;
delete dojox.__temp__;
return t;
};
_2.createFilter=function(_21){
var _22=_2.makeFilter(_21&&{filter:_21}||_1);
return function(_23){
return new _22(_23);
};
};
_2.makeDecorator=function(_24){
dojo.declare("dojox.__temp__",_12,_24||_1);
var t=dojox.__temp__;
delete dojox.__temp__;
return t;
};
_2.createDecorator=function(_26){
var _27=_2.makeDecorator(_26&&{process:_26}||_1);
return function(_28){
return new _27(_28);
};
};
var _29=_2.makeDecorator({constructor:function(_2a,_2b){
this.value=_2b;
this.context=_2a;
},process:function(_2c,_2d){
var old=_2c[_2d],_2f=this.value,_30=this.context;
_2c[_2d]=function(){
return _2f.call(_30,this,arguments,_2d,old);
};
}});
dojo.mixin(_2,{filter:_2.createFilter(),augment:_2.createDecorator(function(_31,_32){
if(!(_32 in _31)){
_31[_32]=this.value;
}
}),override:_2.createDecorator(function(_33,_34){
if(_34 in _33){
_33[_34]=this.value;
}
}),replaceContext:function(_35,_36){
return new _29(_35,_36);
},shuffle:_2.createDecorator(function(_37,_38){
if(_38 in _37){
var old=_37[_38],_3a=this.value;
_37[_38]=function(){
return old.apply(this,_3a.apply(this,arguments));
};
}
}),chainBefore:_2.createDecorator(function(_3b,_3c){
if(_3c in _3b){
var old=_3b[_3c],_3e=this.value;
_3b[_3c]=function(){
_3e.apply(this,arguments);
return old.apply(this,arguments);
};
}else{
_3b[_3c]=this.value;
}
}),chainAfter:_2.createDecorator(function(_3f,_40){
if(_40 in _3f){
var old=_3f[_40],_42=this.value;
_3f[_40]=function(){
old.apply(this,arguments);
return _42.apply(this,arguments);
};
}else{
_3f[_40]=this.value;
}
}),before:_2.createDecorator(function(_43,_44){
var old=_43[_44],_46=this.value;
_43[_44]=old?function(){
_46.apply(this,arguments);
return old.apply(this,arguments);
}:function(){
_46.apply(this,arguments);
};
}),around:_2.createDecorator(function(_47,_48){
var old=_47[_48],_4a=this.value;
_47[_48]=old?function(){
return _4a.call(this,old,arguments);
}:function(){
return _4a.call(this,null,arguments);
};
}),afterReturning:_2.createDecorator(function(_4b,_4c){
var old=_4b[_4c],_4e=this.value;
_4b[_4c]=old?function(){
var ret=old.apply(this,arguments);
_4e.call(this,ret);
return ret;
}:function(){
_4e.call(this);
};
}),afterThrowing:_2.createDecorator(function(_50,_51){
var old=_50[_51],_53=this.value;
if(old){
_50[_51]=function(){
var ret;
try{
ret=old.apply(this,arguments);
}
catch(e){
_53.call(this,e);
throw e;
}
return ret;
};
}
}),after:_2.createDecorator(function(_55,_56){
var old=_55[_56],_58=this.value;
_55[_56]=old?function(){
var ret;
try{
ret=old.apply(this,arguments);
}
finally{
_58.call(this);
}
return ret;
}:function(){
_58.call(this);
};
})});
})();
}
