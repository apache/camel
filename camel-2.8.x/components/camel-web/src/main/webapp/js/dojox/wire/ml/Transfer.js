/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.ml.Transfer"]){
dojo._hasResource["dojox.wire.ml.Transfer"]=true;
dojo.provide("dojox.wire.ml.Transfer");
dojo.provide("dojox.wire.ml.ChildWire");
dojo.provide("dojox.wire.ml.ColumnWire");
dojo.provide("dojox.wire.ml.NodeWire");
dojo.provide("dojox.wire.ml.SegmentWire");
dojo.require("dijit._Widget");
dojo.require("dijit._Container");
dojo.require("dojox.wire._base");
dojo.require("dojox.wire.ml.Action");
dojo.declare("dojox.wire.ml.Transfer",dojox.wire.ml.Action,{source:"",sourceStore:"",sourceAttribute:"",sourcePath:"",type:"",converter:"",delimiter:"",target:"",targetStore:"",targetAttribute:"",targetPath:"",_run:function(){
var _1=this._getWire("source");
var _2=this._getWire("target");
dojox.wire.transfer(_1,_2,arguments);
},_getWire:function(_3){
var _4=undefined;
if(_3=="source"){
_4={object:this.source,dataStore:this.sourceStore,attribute:this.sourceAttribute,path:this.sourcePath,type:this.type,converter:this.converter};
}else{
_4={object:this.target,dataStore:this.targetStore,attribute:this.targetAttribute,path:this.targetPath};
}
if(_4.object){
if(_4.object.length>=9&&_4.object.substring(0,9)=="arguments"){
_4.property=_4.object.substring(9);
_4.object=null;
}else{
var i=_4.object.indexOf(".");
if(i<0){
_4.object=dojox.wire.ml._getValue(_4.object);
}else{
_4.property=_4.object.substring(i+1);
_4.object=dojox.wire.ml._getValue(_4.object.substring(0,i));
}
}
}
if(_4.dataStore){
_4.dataStore=dojox.wire.ml._getValue(_4.dataStore);
}
var _6=undefined;
var _7=this.getChildren();
for(var i in _7){
var _8=_7[i];
if(_8 instanceof dojox.wire.ml.ChildWire&&_8.which==_3){
if(!_6){
_6={};
}
_8._addWire(this,_6);
}
}
if(_6){
_6.object=dojox.wire.create(_4);
_6.dataStore=_4.dataStore;
_4=_6;
}
return _4;
}});
dojo.declare("dojox.wire.ml.ChildWire",dijit._Widget,{which:"source",object:"",property:"",type:"",converter:"",attribute:"",path:"",name:"",_addWire:function(_9,_a){
if(this.name){
if(!_a.children){
_a.children={};
}
_a.children[this.name]=this._getWire(_9);
}else{
if(!_a.children){
_a.children=[];
}
_a.children.push(this._getWire(_9));
}
},_getWire:function(_b){
return {object:(this.object?dojox.wire.ml._getValue(this.object):undefined),property:this.property,type:this.type,converter:this.converter,attribute:this.attribute,path:this.path};
}});
dojo.declare("dojox.wire.ml.ColumnWire",dojox.wire.ml.ChildWire,{column:"",_addWire:function(_c,_d){
if(this.column){
if(!_d.columns){
_d.columns={};
}
_d.columns[this.column]=this._getWire(_c);
}else{
if(!_d.columns){
_d.columns=[];
}
_d.columns.push(this._getWire(_c));
}
}});
dojo.declare("dojox.wire.ml.NodeWire",[dojox.wire.ml.ChildWire,dijit._Container],{titleProperty:"",titleAttribute:"",titlePath:"",_addWire:function(_e,_f){
if(!_f.nodes){
_f.nodes=[];
}
_f.nodes.push(this._getWires(_e));
},_getWires:function(_10){
var _11={node:this._getWire(_10),title:{type:"string",property:this.titleProperty,attribute:this.titleAttribute,path:this.titlePath}};
var _12=[];
var _13=this.getChildren();
for(var i in _13){
var _15=_13[i];
if(_15 instanceof dojox.wire.ml.NodeWire){
_12.push(_15._getWires(_10));
}
}
if(_12.length>0){
_11.children=_12;
}
return _11;
}});
dojo.declare("dojox.wire.ml.SegmentWire",dojox.wire.ml.ChildWire,{_addWire:function(_16,_17){
if(!_17.segments){
_17.segments=[];
}
_17.segments.push(this._getWire(_16));
if(_16.delimiter&&!_17.delimiter){
_17.delimiter=_16.delimiter;
}
}});
}
