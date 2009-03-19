/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._tree.dndSource"]){
dojo._hasResource["dijit._tree.dndSource"]=true;
dojo.provide("dijit._tree.dndSource");
dojo.require("dijit._tree.dndSelector");
dojo.require("dojo.dnd.Manager");
dojo.declare("dijit._tree.dndSource",dijit._tree.dndSelector,{isSource:true,accept:["text"],copyOnly:false,dragThreshold:0,betweenThreshold:0,skipForm:false,constructor:function(_1,_2){
if(!_2){
_2={};
}
dojo.mixin(this,_2);
this.isSource=typeof _2.isSource=="undefined"?true:_2.isSource;
var _3=_2.accept instanceof Array?_2.accept:["text"];
this.accept=null;
if(_3.length){
this.accept={};
for(var i=0;i<_3.length;++i){
this.accept[_3[i]]=1;
}
}
this.isDragging=false;
this.mouseDown=false;
this.targetAnchor=null;
this.targetBox=null;
this.dropPosition="";
this._lastX=0;
this._lastY=0;
this.sourceState="";
if(this.isSource){
dojo.addClass(this.node,"dojoDndSource");
}
this.targetState="";
if(this.accept){
dojo.addClass(this.node,"dojoDndTarget");
}
this.topics=[dojo.subscribe("/dnd/source/over",this,"onDndSourceOver"),dojo.subscribe("/dnd/start",this,"onDndStart"),dojo.subscribe("/dnd/drop",this,"onDndDrop"),dojo.subscribe("/dnd/cancel",this,"onDndCancel")];
this.events.push(dojo.connect(this.node,"onmousemove",this,"onMouseMove"));
},startup:function(){
},checkAcceptance:function(_5,_6){
return true;
},copyState:function(_7){
return this.copyOnly||_7;
},destroy:function(){
this.inherited("destroy",arguments);
dojo.forEach(this.topics,dojo.unsubscribe);
this.targetAnchor=null;
},_onDragMouse:function(e){
var m=dojo.dnd.manager(),_a=this.targetAnchor,_b=this.current,_c=this.currentWidget,_d=this.dropPosition;
var _e="Over";
if(_b&&this.betweenThreshold>0){
if(!this.targetBox||_a!=_b){
this.targetBox={xy:dojo.coords(_b,true),w:_b.offsetWidth,h:_b.offsetHeight};
}
if((e.pageY-this.targetBox.xy.y)<=this.betweenThreshold){
_e="Before";
}else{
if((e.pageY-this.targetBox.xy.y)>=(this.targetBox.h-this.betweenThreshold)){
_e="After";
}
}
}
if(_b!=_a||_e!=_d){
if(_a){
this._removeItemClass(_a,_d);
}
if(_b){
this._addItemClass(_b,_e);
}
if(!_b){
m.canDrop(false);
}else{
if(_c==this.tree.rootNode&&_e!="Over"){
m.canDrop(false);
}else{
if(m.source==this&&(_b.id in this.selection)){
m.canDrop(false);
}else{
if(this.checkItemAcceptance(_b,m.source,_e.toLowerCase())){
m.canDrop(true);
}else{
m.canDrop(false);
}
}
}
}
this.targetAnchor=_b;
this.dropPosition=_e;
}
},onMouseMove:function(e){
if(this.isDragging&&this.targetState=="Disabled"){
return;
}
var m=dojo.dnd.manager();
if(this.isDragging){
if(this.betweenThreshold>0){
this._onDragMouse(e);
}
}else{
if(this.mouseDown&&this.isSource&&(Math.abs(e.pageX-this._lastX)>=this.dragThreshold||Math.abs(e.pageY-this._lastY)>=this.dragThreshold)){
var n=this.getSelectedNodes();
var _12=[];
for(var i in n){
_12.push(n[i]);
}
if(_12.length){
m.startDrag(this,_12,this.copyState(dojo.dnd.getCopyKeyState(e)));
}
}
}
},onMouseDown:function(e){
this.mouseDown=true;
this.mouseButton=e.button;
this._lastX=e.pageX;
this._lastY=e.pageY;
this.inherited("onMouseDown",arguments);
},onMouseUp:function(e){
if(this.mouseDown){
this.mouseDown=false;
this.inherited("onMouseUp",arguments);
}
},onMouseOver:function(_16,e){
this.inherited(arguments);
if(this.isDragging){
this._onDragMouse(e);
}
},onMouseOut:function(){
this.inherited(arguments);
this._unmarkTargetAnchor();
},checkItemAcceptance:function(_18,_19,_1a){
return true;
},onDndSourceOver:function(_1b){
if(this!=_1b){
this.mouseDown=false;
this._unmarkTargetAnchor();
}else{
if(this.isDragging){
var m=dojo.dnd.manager();
m.canDrop(false);
}
}
},onDndStart:function(_1d,_1e,_1f){
if(this.isSource){
this._changeState("Source",this==_1d?(_1f?"Copied":"Moved"):"");
}
var _20=this.checkAcceptance(_1d,_1e);
this._changeState("Target",_20?"":"Disabled");
if(_20){
dojo.dnd.manager().overSource(this);
}
this.isDragging=true;
},itemCreator:function(_21){
return dojo.map(_21,function(_22){
return {"id":_22.id,"name":_22.textContent||_22.innerText||""};
});
},onDndDrop:function(_23,_24,_25){
if(this.containerState=="Over"){
var _26=this.tree,_27=_26.model,_28=this.targetAnchor,_29=false;
this.isDragging=false;
var _2a=dijit.getEnclosingWidget(_28);
var _2b;
var _2c;
_2b=(_2a&&_2a.item)||_26.item;
if(this.dropPosition=="Before"||this.dropPosition=="After"){
_2b=(_2a.getParent()&&_2a.getParent().item)||_26.item;
_2c=_2a.getIndexInParent();
if(this.dropPosition=="After"){
_2c=_2a.getIndexInParent()+1;
}
}else{
_2b=(_2a&&_2a.item)||_26.item;
}
var _2d;
if(_23!=this){
_2d=this.itemCreator(_24,_28);
}
dojo.forEach(_24,function(_2e,idx){
if(_23==this){
var _30=dijit.getEnclosingWidget(_2e),_31=_30.item,_32=_30.getParent().item;
if(typeof _2c=="number"){
if(_2b==_32&&_30.getIndexInParent()<_2c){
_2c-=1;
}
}
_27.pasteItem(_31,_32,_2b,_25,_2c);
}else{
_27.newItem(_2d[idx],_2b);
}
},this);
this.tree._expandNode(_2a);
}
this.onDndCancel();
},onDndCancel:function(){
this._unmarkTargetAnchor();
this.isDragging=false;
this.mouseDown=false;
delete this.mouseButton;
this._changeState("Source","");
this._changeState("Target","");
},onOverEvent:function(){
this.inherited(arguments);
dojo.dnd.manager().overSource(this);
},onOutEvent:function(){
this._unmarkTargetAnchor();
var m=dojo.dnd.manager();
if(this.isDragging){
m.canDrop(false);
}
m.outSource(this);
this.inherited(arguments);
},_unmarkTargetAnchor:function(){
if(!this.targetAnchor){
return;
}
this._removeItemClass(this.targetAnchor,this.dropPosition);
this.targetAnchor=null;
this.targetBox=null;
this.dropPosition=null;
},_markDndStatus:function(_34){
this._changeState("Source",_34?"Copied":"Moved");
}});
dojo.declare("dijit._tree.dndTarget",dijit._tree.dndSource,{constructor:function(_35,_36){
this.isSource=false;
dojo.removeClass(this.node,"dojoDndSource");
}});
}
