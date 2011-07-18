/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._data.fields"]){
dojo._hasResource["dojox.grid.compat._data.fields"]=true;
dojo.provide("dojox.grid.compat._data.fields");
dojo.declare("dojox.grid.data.Mixer",null,{constructor:function(){
this.defaultValue={};
this.values=[];
},count:function(){
return this.values.length;
},clear:function(){
this.values=[];
},build:function(_1){
var _2=dojo.mixin({owner:this},this.defaultValue);
_2.key=_1;
this.values[_1]=_2;
return _2;
},getDefault:function(){
return this.defaultValue;
},setDefault:function(_3){
for(var i=0,a;(a=arguments[i]);i++){
dojo.mixin(this.defaultValue,a);
}
},get:function(_6){
return this.values[_6]||this.build(_6);
},_set:function(_7,_8){
var v=this.get(_7);
for(var i=1;i<arguments.length;i++){
dojo.mixin(v,arguments[i]);
}
this.values[_7]=v;
},set:function(){
if(arguments.length<1){
return;
}
var a=arguments[0];
if(!dojo.isArray(a)){
this._set.apply(this,arguments);
}else{
if(a.length&&a[0]["default"]){
this.setDefault(a.shift());
}
for(var i=0,l=a.length;i<l;i++){
this._set(i,a[i]);
}
}
},insert:function(_e,_f){
if(_e>=this.values.length){
this.values[_e]=_f;
}else{
this.values.splice(_e,0,_f);
}
},remove:function(_10){
this.values.splice(_10,1);
},swap:function(_11,_12){
dojox.grid.arraySwap(this.values,_11,_12);
},move:function(_13,_14){
dojox.grid.arrayMove(this.values,_13,_14);
}});
dojox.grid.data.compare=function(a,b){
return (a>b?1:(a==b?0:-1));
};
dojo.declare("dojox.grid.data.Field",null,{constructor:function(_17){
this.name=_17;
this.compare=dojox.grid.data.compare;
},na:dojox.grid.na});
dojo.declare("dojox.grid.data.Fields",dojox.grid.data.Mixer,{constructor:function(_18){
var _19=_18?_18:dojox.grid.data.Field;
this.defaultValue=new _19();
},indexOf:function(_1a){
for(var i=0;i<this.values.length;i++){
var v=this.values[i];
if(v&&v.key==_1a){
return i;
}
}
return -1;
}});
}
