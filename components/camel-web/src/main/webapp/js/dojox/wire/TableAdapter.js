/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.TableAdapter"]){
dojo._hasResource["dojox.wire.TableAdapter"]=true;
dojo.provide("dojox.wire.TableAdapter");
dojo.require("dojox.wire.CompositeWire");
dojo.declare("dojox.wire.TableAdapter",dojox.wire.CompositeWire,{_wireClass:"dojox.wire.TableAdapter",constructor:function(_1){
this._initializeChildren(this.columns);
},_getValue:function(_2){
if(!_2||!this.columns){
return _2;
}
var _3=_2;
if(!dojo.isArray(_3)){
_3=[_3];
}
var _4=[];
for(var i in _3){
var _6=this._getRow(_3[i]);
_4.push(_6);
}
return _4;
},_setValue:function(_7,_8){
throw new Error("Unsupported API: "+this._wireClass+"._setValue");
},_getRow:function(_9){
var _a=(dojo.isArray(this.columns)?[]:{});
for(var c in this.columns){
_a[c]=this.columns[c].getValue(_9);
}
return _a;
}});
}
