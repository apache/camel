/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.DataSelection"]){
dojo._hasResource["dojox.grid.DataSelection"]=true;
dojo.provide("dojox.grid.DataSelection");
dojo.require("dojox.grid.Selection");
dojo.declare("dojox.grid.DataSelection",dojox.grid.Selection,{getFirstSelected:function(){
var _1=dojox.grid.Selection.prototype.getFirstSelected.call(this);
if(_1==-1){
return null;
}
return this.grid.getItem(_1);
},getNextSelected:function(_2){
var _3=this.grid.getItemIndex(_2);
var _4=dojox.grid.Selection.prototype.getNextSelected.call(this,_3);
if(_4==-1){
return null;
}
return this.grid.getItem(_4);
},getSelected:function(){
var _5=[];
for(var i=0,l=this.selected.length;i<l;i++){
if(this.selected[i]){
_5.push(this.grid.getItem(i));
}
}
return _5;
},addToSelection:function(_8){
if(this.mode=="none"){
return;
}
var _9=null;
if(typeof _8=="number"||typeof _8=="string"){
_9=_8;
}else{
_9=this.grid.getItemIndex(_8);
}
dojox.grid.Selection.prototype.addToSelection.call(this,_9);
},deselect:function(_a){
if(this.mode=="none"){
return;
}
var _b=null;
if(typeof _a=="number"||typeof _a=="string"){
_b=_a;
}else{
_b=this.grid.getItemIndex(_a);
}
dojox.grid.Selection.prototype.deselect.call(this,_b);
},deselectAll:function(_c){
var _d=null;
if(_c||typeof _c=="number"){
if(typeof _c=="number"||typeof _c=="string"){
_d=_c;
}else{
_d=this.grid.getItemIndex(_c);
}
dojox.grid.Selection.prototype.deselectAll.call(this,_d);
}else{
this.inherited(arguments);
}
}});
}
