/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.ml.Data"]){
dojo._hasResource["dojox.wire.ml.Data"]=true;
dojo.provide("dojox.wire.ml.Data");
dojo.provide("dojox.wire.ml.DataProperty");
dojo.require("dijit._Widget");
dojo.require("dijit._Container");
dojo.require("dojox.wire.ml.util");
dojo.declare("dojox.wire.ml.Data",[dijit._Widget,dijit._Container],{startup:function(){
this._initializeProperties();
},_initializeProperties:function(_1){
if(!this._properties||_1){
this._properties={};
}
var _2=this.getChildren();
for(var i in _2){
var _4=_2[i];
if((_4 instanceof dojox.wire.ml.DataProperty)&&_4.name){
this.setPropertyValue(_4.name,_4.getValue());
}
}
},getPropertyValue:function(_5){
return this._properties[_5];
},setPropertyValue:function(_6,_7){
this._properties[_6]=_7;
}});
dojo.declare("dojox.wire.ml.DataProperty",[dijit._Widget,dijit._Container],{name:"",type:"",value:"",_getValueAttr:function(){
return this.getValue();
},getValue:function(){
var _8=this.value;
if(this.type){
if(this.type=="number"){
_8=parseInt(_8);
}else{
if(this.type=="boolean"){
_8=(_8=="true");
}else{
if(this.type=="array"){
_8=[];
var _9=this.getChildren();
for(var i in _9){
var _b=_9[i];
if(_b instanceof dojox.wire.ml.DataProperty){
_8.push(_b.getValue());
}
}
}else{
if(this.type=="object"){
_8={};
var _9=this.getChildren();
for(var i in _9){
var _b=_9[i];
if((_b instanceof dojox.wire.ml.DataProperty)&&_b.name){
_8[_b.name]=_b.getValue();
}
}
}else{
if(this.type=="element"){
_8=new dojox.wire.ml.XmlElement(_8);
var _9=this.getChildren();
for(var i in _9){
var _b=_9[i];
if((_b instanceof dojox.wire.ml.DataProperty)&&_b.name){
_8.setPropertyValue(_b.name,_b.getValue());
}
}
}
}
}
}
}
}
return _8;
}});
}
