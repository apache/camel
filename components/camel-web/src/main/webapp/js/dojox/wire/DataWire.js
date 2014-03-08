/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.DataWire"]){
dojo._hasResource["dojox.wire.DataWire"]=true;
dojo.provide("dojox.wire.DataWire");
dojo.require("dojox.wire.Wire");
dojo.declare("dojox.wire.DataWire",dojox.wire.Wire,{_wireClass:"dojox.wire.DataWire",constructor:function(_1){
if(!this.dataStore&&this.parent){
this.dataStore=this.parent.dataStore;
}
},_getValue:function(_2){
if(!_2||!this.attribute||!this.dataStore){
return _2;
}
var _3=_2;
var _4=this.attribute.split(".");
for(var i in _4){
_3=this._getAttributeValue(_3,_4[i]);
if(!_3){
return undefined;
}
}
return _3;
},_setValue:function(_6,_7){
if(!_6||!this.attribute||!this.dataStore){
return _6;
}
var _8=_6;
var _9=this.attribute.split(".");
var _a=_9.length-1;
for(var i=0;i<_a;i++){
_8=this._getAttributeValue(_8,_9[i]);
if(!_8){
return undefined;
}
}
this._setAttributeValue(_8,_9[_a],_7);
return _6;
},_getAttributeValue:function(_c,_d){
var _e=undefined;
var i1=_d.indexOf("[");
if(i1>=0){
var i2=_d.indexOf("]");
var _11=_d.substring(i1+1,i2);
_d=_d.substring(0,i1);
var _12=this.dataStore.getValues(_c,_d);
if(_12){
if(!_11){
_e=_12;
}else{
_e=_12[_11];
}
}
}else{
_e=this.dataStore.getValue(_c,_d);
}
return _e;
},_setAttributeValue:function(_13,_14,_15){
var i1=_14.indexOf("[");
if(i1>=0){
var i2=_14.indexOf("]");
var _18=_14.substring(i1+1,i2);
_14=_14.substring(0,i1);
var _19=null;
if(!_18){
_19=_15;
}else{
_19=this.dataStore.getValues(_13,_14);
if(!_19){
_19=[];
}
_19[_18]=_15;
}
this.dataStore.setValues(_13,_14,_19);
}else{
this.dataStore.setValue(_13,_14,_15);
}
}});
}
