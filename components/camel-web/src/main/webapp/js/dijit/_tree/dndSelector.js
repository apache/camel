/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._tree.dndSelector"]){
dojo._hasResource["dijit._tree.dndSelector"]=true;
dojo.provide("dijit._tree.dndSelector");
dojo.require("dojo.dnd.common");
dojo.require("dijit._tree.dndContainer");
dojo.declare("dijit._tree.dndSelector",dijit._tree.dndContainer,{constructor:function(_1,_2){
this.selection={};
this.anchor=null;
this.simpleSelection=false;
this.events.push(dojo.connect(this.tree.domNode,"onmousedown",this,"onMouseDown"),dojo.connect(this.tree.domNode,"onmouseup",this,"onMouseUp"));
},singular:false,getSelectedItems:function(){
var _3=[];
for(var i in this.selection){
_3.push(dijit.getEnclosingWidget(this.selection[i]).item);
}
return _3;
},getSelectedNodes:function(){
return this.selection;
},selectNone:function(){
return this._removeSelection()._removeAnchor();
},insertItems:function(_5,_6){
},destroy:function(){
dijit._tree.dndSelector.superclass.destroy.call(this);
this.selection=this.anchor=null;
},onMouseDown:function(e){
if(!this.current){
return;
}
if(e.button==2){
return;
}
var _8=dijit.getEnclosingWidget(this.current).item;
var id=this.tree.model.getIdentity(_8);
if(!this.current.id){
this.current.id=id;
}
if(!this.current.type){
this.current.type="data";
}
if(!this.singular&&!dojo.dnd.getCopyKeyState(e)&&!e.shiftKey&&(this.current.id in this.selection)){
this.simpleSelection=true;
dojo.stopEvent(e);
return;
}
if(this.singular){
if(this.anchor==this.current){
if(dojo.dnd.getCopyKeyState(e)){
this.selectNone();
}
}else{
this.selectNone();
this.anchor=this.current;
this._addItemClass(this.anchor,"Anchor");
this.selection[this.current.id]=this.current;
}
}else{
if(!this.singular&&e.shiftKey){
if(dojo.dnd.getCopyKeyState(e)){
}else{
}
}else{
if(dojo.dnd.getCopyKeyState(e)){
if(this.anchor==this.current){
delete this.selection[this.anchor.id];
this._removeAnchor();
}else{
if(this.current.id in this.selection){
this._removeItemClass(this.current,"Selected");
delete this.selection[this.current.id];
}else{
if(this.anchor){
this._removeItemClass(this.anchor,"Anchor");
this._addItemClass(this.anchor,"Selected");
}
this.anchor=this.current;
this._addItemClass(this.current,"Anchor");
this.selection[this.current.id]=this.current;
}
}
}else{
var _8=dijit.getEnclosingWidget(this.current).item;
var id=this.tree.model.getIdentity(_8);
if(!(id in this.selection)){
this.selectNone();
this.anchor=this.current;
this._addItemClass(this.current,"Anchor");
this.selection[id]=this.current;
}
}
}
}
dojo.stopEvent(e);
},onMouseUp:function(e){
if(!this.simpleSelection){
return;
}
this.simpleSelection=false;
this.selectNone();
if(this.current){
this.anchor=this.current;
this._addItemClass(this.anchor,"Anchor");
this.selection[this.current.id]=this.current;
}
},_removeSelection:function(){
var e=dojo.dnd._empty;
for(var i in this.selection){
if(i in e){
continue;
}
var _d=dojo.byId(i);
if(_d){
this._removeItemClass(_d,"Selected");
}
}
this.selection={};
return this;
},_removeAnchor:function(){
if(this.anchor){
this._removeItemClass(this.anchor,"Anchor");
this.anchor=null;
}
return this;
}});
}
