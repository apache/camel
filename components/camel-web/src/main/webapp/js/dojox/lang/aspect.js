/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.aspect"]){
dojo._hasResource["dojox.lang.aspect"]=true;
dojo.provide("dojox.lang.aspect");
(function(){
var d=dojo,_2=dojox.lang.aspect,ap=Array.prototype,_4=[],_5;
var _6=function(){
this.next_before=this.prev_before=this.next_around=this.prev_around=this.next_afterReturning=this.prev_afterReturning=this.next_afterThrowing=this.prev_afterThrowing=this;
this.counter=0;
};
d.extend(_6,{add:function(_7){
var _8=d.isFunction(_7),_9={advice:_7,dynamic:_8};
this._add(_9,"before","",_8,_7);
this._add(_9,"around","",_8,_7);
this._add(_9,"after","Returning",_8,_7);
this._add(_9,"after","Throwing",_8,_7);
++this.counter;
return _9;
},_add:function(_a,_b,_c,_d,_e){
var _f=_b+_c;
if(_d||_e[_b]||(_c&&_e[_f])){
var _10="next_"+_f,_11="prev_"+_f;
(_a[_11]=this[_11])[_10]=_a;
(_a[_10]=this)[_11]=_a;
}
},remove:function(_12){
this._remove(_12,"before");
this._remove(_12,"around");
this._remove(_12,"afterReturning");
this._remove(_12,"afterThrowing");
--this.counter;
},_remove:function(_13,_14){
var _15="next_"+_14,_16="prev_"+_14;
if(_13[_15]){
_13[_15][_16]=_13[_16];
_13[_16][_15]=_13[_15];
}
},isEmpty:function(){
return !this.counter;
}});
var _17=function(){
return function(){
var _18=arguments.callee,_19=_18.advices,ret,i,a,e,t;
if(_5){
_4.push(_5);
}
_5={instance:this,joinPoint:_18,depth:_4.length,around:_19.prev_around,dynAdvices:[],dynIndex:0};
try{
for(i=_19.prev_before;i!=_19;i=i.prev_before){
if(i.dynamic){
_5.dynAdvices.push(a=new i.advice(_5));
if(t=a.before){
t.apply(a,arguments);
}
}else{
t=i.advice;
t.before.apply(t,arguments);
}
}
try{
ret=(_19.prev_around==_19?_18.target:_2.proceed).apply(this,arguments);
}
catch(e){
_5.dynIndex=_5.dynAdvices.length;
for(i=_19.next_afterThrowing;i!=_19;i=i.next_afterThrowing){
a=i.dynamic?_5.dynAdvices[--_5.dynIndex]:i.advice;
if(t=a.afterThrowing){
t.call(a,e);
}
if(t=a.after){
t.call(a);
}
}
throw e;
}
_5.dynIndex=_5.dynAdvices.length;
for(i=_19.next_afterReturning;i!=_19;i=i.next_afterReturning){
a=i.dynamic?_5.dynAdvices[--_5.dynIndex]:i.advice;
if(t=a.afterReturning){
t.call(a,ret);
}
if(t=a.after){
t.call(a);
}
}
var ls=_18._listeners;
for(i in ls){
if(!(i in ap)){
ls[i].apply(this,arguments);
}
}
}
finally{
for(i=0;i<_5.dynAdvices.length;++i){
a=_5.dynAdvices[i];
if(a.destroy){
a.destroy();
}
}
_5=_4.length?_4.pop():null;
}
return ret;
};
};
_2.advise=function(obj,_21,_22){
if(typeof obj!="object"){
obj=obj.prototype;
}
var _23=[];
if(!(_21 instanceof Array)){
_21=[_21];
}
for(var j=0;j<_21.length;++j){
var t=_21[j];
if(t instanceof RegExp){
for(var i in obj){
if(d.isFunction(obj[i])&&t.test(i)){
_23.push(i);
}
}
}else{
if(d.isFunction(obj[t])){
_23.push(t);
}
}
}
if(!d.isArray(_22)){
_22=[_22];
}
return _2.adviseRaw(obj,_23,_22);
};
_2.adviseRaw=function(obj,_28,_29){
if(!_28.length||!_29.length){
return null;
}
var m={},al=_29.length;
for(var i=_28.length-1;i>=0;--i){
var _2d=_28[i],o=obj[_2d],ao=new Array(al),t=o.advices;
if(!t){
var x=obj[_2d]=_17();
x.target=o.target||o;
x.targetName=_2d;
x._listeners=o._listeners||[];
x.advices=new _6;
t=x.advices;
}
for(var j=0;j<al;++j){
ao[j]=t.add(_29[j]);
}
m[_2d]=ao;
}
return [obj,m];
};
_2.unadvise=function(_33){
if(!_33){
return;
}
var obj=_33[0],_35=_33[1];
for(var _36 in _35){
var o=obj[_36],t=o.advices,ao=_35[_36];
for(var i=ao.length-1;i>=0;--i){
t.remove(ao[i]);
}
if(t.isEmpty()){
var _3b=true,ls=o._listeners;
if(ls.length){
for(i in ls){
if(!(i in ap)){
_3b=false;
break;
}
}
}
if(_3b){
obj[_36]=o.target;
}else{
var x=obj[_36]=d._listener.getDispatcher();
x.target=o.target;
x._listeners=ls;
}
}
}
};
_2.getContext=function(){
return _5;
};
_2.getContextStack=function(){
return _4;
};
_2.proceed=function(){
var _3e=_5.joinPoint,_3f=_3e.advices;
for(var c=_5.around;c!=_3f;c=_5.around){
_5.around=c.prev_around;
if(c.dynamic){
var a=_5.dynAdvices[_5.dynIndex++],t=a.around;
if(t){
return t.apply(a,arguments);
}
}else{
return c.advice.around.apply(c.advice,arguments);
}
}
return _3e.target.apply(_5.instance,arguments);
};
})();
}
