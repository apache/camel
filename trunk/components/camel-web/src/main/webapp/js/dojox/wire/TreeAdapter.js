/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.TreeAdapter"]){
dojo._hasResource["dojox.wire.TreeAdapter"]=true;
dojo.provide("dojox.wire.TreeAdapter");
dojo.require("dojox.wire.CompositeWire");
dojo.declare("dojox.wire.TreeAdapter",dojox.wire.CompositeWire,{_wireClass:"dojox.wire.TreeAdapter",constructor:function(_1){
this._initializeChildren(this.nodes);
},_getValue:function(_2){
if(!_2||!this.nodes){
return _2;
}
var _3=_2;
if(!dojo.isArray(_3)){
_3=[_3];
}
var _4=[];
for(var i in _3){
for(var i2 in this.nodes){
_4=_4.concat(this._getNodes(_3[i],this.nodes[i2]));
}
}
return _4;
},_setValue:function(_7,_8){
throw new Error("Unsupported API: "+this._wireClass+"._setValue");
},_initializeChildren:function(_9){
if(!_9){
return;
}
for(var i in _9){
var _b=_9[i];
if(_b.node){
_b.node.parent=this;
if(!dojox.wire.isWire(_b.node)){
_b.node=dojox.wire.create(_b.node);
}
}
if(_b.title){
_b.title.parent=this;
if(!dojox.wire.isWire(_b.title)){
_b.title=dojox.wire.create(_b.title);
}
}
if(_b.children){
this._initializeChildren(_b.children);
}
}
},_getNodes:function(_c,_d){
var _e=null;
if(_d.node){
_e=_d.node.getValue(_c);
if(!_e){
return [];
}
if(!dojo.isArray(_e)){
_e=[_e];
}
}else{
_e=[_c];
}
var _f=[];
for(var i in _e){
_c=_e[i];
var _11={};
if(_d.title){
_11.title=_d.title.getValue(_c);
}else{
_11.title=_c;
}
if(_d.children){
var _12=[];
for(var i2 in _d.children){
_12=_12.concat(this._getNodes(_c,_d.children[i2]));
}
if(_12.length>0){
_11.children=_12;
}
}
_f.push(_11);
}
return _f;
}});
}
