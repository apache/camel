/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.CompositeWire"]){
dojo._hasResource["dojox.wire.CompositeWire"]=true;
dojo.provide("dojox.wire.CompositeWire");
dojo.require("dojox.wire._base");
dojo.require("dojox.wire.Wire");
dojo.declare("dojox.wire.CompositeWire",dojox.wire.Wire,{_wireClass:"dojox.wire.CompositeWire",constructor:function(_1){
this._initializeChildren(this.children);
},_getValue:function(_2){
if(!_2||!this.children){
return _2;
}
var _3=(dojo.isArray(this.children)?[]:{});
for(var c in this.children){
_3[c]=this.children[c].getValue(_2);
}
return _3;
},_setValue:function(_5,_6){
if(!_5||!this.children){
return _5;
}
for(var c in this.children){
this.children[c].setValue(_6[c],_5);
}
return _5;
},_initializeChildren:function(_8){
if(!_8){
return;
}
for(var c in _8){
var _a=_8[c];
_a.parent=this;
if(!dojox.wire.isWire(_a)){
_8[c]=dojox.wire.create(_a);
}
}
}});
}
