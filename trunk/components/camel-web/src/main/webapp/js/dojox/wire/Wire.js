/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.Wire"]){
dojo._hasResource["dojox.wire.Wire"]=true;
dojo.provide("dojox.wire.Wire");
dojo.require("dojox.wire._base");
dojo.declare("dojox.wire.Wire",null,{_wireClass:"dojox.wire.Wire",constructor:function(_1){
dojo.mixin(this,_1);
if(this.converter){
if(dojo.isString(this.converter)){
var _2=dojo.getObject(this.converter);
if(dojo.isFunction(_2)){
try{
var _3=new _2();
if(_3&&!dojo.isFunction(_3["convert"])){
this.converter={convert:_2};
}else{
this.converter=_3;
}
}
catch(e){
}
}else{
if(dojo.isObject(_2)){
if(dojo.isFunction(_2["convert"])){
this.converter=_2;
}
}
}
if(dojo.isString(this.converter)){
var _4=dojox.wire._getClass(this.converter);
if(_4){
this.converter=new _4();
}else{
this.converter=undefined;
}
}
}else{
if(dojo.isFunction(this.converter)){
this.converter={convert:this.converter};
}
}
}
},getValue:function(_5){
var _6=undefined;
if(dojox.wire.isWire(this.object)){
_6=this.object.getValue(_5);
}else{
_6=(this.object||_5);
}
if(this.property){
var _7=this.property.split(".");
for(var i in _7){
if(!_6){
return _6;
}
_6=this._getPropertyValue(_6,_7[i]);
}
}
var _9=undefined;
if(this._getValue){
_9=this._getValue(_6);
}else{
_9=_6;
}
if(_9){
if(this.type){
if(this.type=="string"){
_9=_9.toString();
}else{
if(this.type=="number"){
_9=parseInt(_9,10);
}else{
if(this.type=="boolean"){
_9=(_9!="false");
}else{
if(this.type=="array"){
if(!dojo.isArray(_9)){
_9=[_9];
}
}
}
}
}
}
if(this.converter&&this.converter.convert){
_9=this.converter.convert(_9,this);
}
}
return _9;
},setValue:function(_a,_b){
var _c=undefined;
if(dojox.wire.isWire(this.object)){
_c=this.object.getValue(_b);
}else{
_c=(this.object||_b);
}
var _d=undefined;
var o;
if(this.property){
if(!_c){
if(dojox.wire.isWire(this.object)){
_c={};
this.object.setValue(_c,_b);
}else{
throw new Error(this._wireClass+".setValue(): invalid object");
}
}
var _f=this.property.split(".");
var _10=_f.length-1;
for(var i=0;i<_10;i++){
var p=_f[i];
o=this._getPropertyValue(_c,p);
if(!o){
o={};
this._setPropertyValue(_c,p,o);
}
_c=o;
}
_d=_f[_10];
}
if(this._setValue){
if(_d){
o=this._getPropertyValue(_c,_d);
if(!o){
o={};
this._setPropertyValue(_c,_d,o);
}
_c=o;
}
var _13=this._setValue(_c,_a);
if(!_c&&_13){
if(dojox.wire.isWire(this.object)){
this.object.setValue(_13,_b);
}else{
throw new Error(this._wireClass+".setValue(): invalid object");
}
}
}else{
if(_d){
this._setPropertyValue(_c,_d,_a);
}else{
if(dojox.wire.isWire(this.object)){
this.object.setValue(_a,_b);
}else{
throw new Error(this._wireClass+".setValue(): invalid property");
}
}
}
},_getPropertyValue:function(_14,_15){
var _16=undefined;
var i1=_15.indexOf("[");
if(i1>=0){
var i2=_15.indexOf("]");
var _19=_15.substring(i1+1,i2);
var _1a=null;
if(i1===0){
_1a=_14;
}else{
_15=_15.substring(0,i1);
_1a=this._getPropertyValue(_14,_15);
if(_1a&&!dojo.isArray(_1a)){
_1a=[_1a];
}
}
if(_1a){
_16=_1a[_19];
}
}else{
if(_14.getPropertyValue){
_16=_14.getPropertyValue(_15);
}else{
var _1b="get"+_15.charAt(0).toUpperCase()+_15.substring(1);
if(this._useAttr(_14)){
_16=_14.attr(_15);
}else{
if(_14[_1b]){
_16=_14[_1b]();
}else{
_16=_14[_15];
}
}
}
}
return _16;
},_setPropertyValue:function(_1c,_1d,_1e){
var i1=_1d.indexOf("[");
if(i1>=0){
var i2=_1d.indexOf("]");
var _21=_1d.substring(i1+1,i2);
var _22=null;
if(i1===0){
_22=_1c;
}else{
_1d=_1d.substring(0,i1);
_22=this._getPropertyValue(_1c,_1d);
if(!_22){
_22=[];
this._setPropertyValue(_1c,_1d,_22);
}
}
_22[_21]=_1e;
}else{
if(_1c.setPropertyValue){
_1c.setPropertyValue(_1d,_1e);
}else{
var _23="set"+_1d.charAt(0).toUpperCase()+_1d.substring(1);
if(this._useAttr(_1c)){
_1c.attr(_1d,_1e);
}else{
if(_1c[_23]){
_1c[_23](_1e);
}else{
_1c[_1d]=_1e;
}
}
}
}
},_useAttr:function(_24){
var _25=false;
if(dojo.isFunction(_24.attr)){
_25=true;
}
return _25;
}});
}
