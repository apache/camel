/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._tree.dndContainer"]){
dojo._hasResource["dijit._tree.dndContainer"]=true;
dojo.provide("dijit._tree.dndContainer");
dojo.require("dojo.dnd.common");
dojo.require("dojo.dnd.Container");
dojo.declare("dijit._tree.dndContainer",null,{constructor:function(_1,_2){
this.tree=_1;
this.node=_1.domNode;
dojo.mixin(this,_2);
this.map={};
this.current=null;
this.containerState="";
dojo.addClass(this.node,"dojoDndContainer");
if(!(_2&&_2._skipStartup)){
this.startup();
}
this.events=[dojo.connect(this.node,"onmouseenter",this,"onOverEvent"),dojo.connect(this.node,"onmouseleave",this,"onOutEvent"),dojo.connect(this.tree,"_onNodeMouseEnter",this,"onMouseOver"),dojo.connect(this.tree,"_onNodeMouseLeave",this,"onMouseOut"),dojo.connect(this.node,"ondragstart",dojo,"stopEvent"),dojo.connect(this.node,"onselectstart",dojo,"stopEvent")];
},getItem:function(_3){
return this.selection[_3];
},destroy:function(){
dojo.forEach(this.events,dojo.disconnect);
this.node=this.parent=null;
},onMouseOver:function(_4,_5){
this.current=_4.rowNode;
this.currentWidget=_4;
},onMouseOut:function(_6,_7){
this.current=null;
this.currentWidget=null;
},_changeState:function(_8,_9){
var _a="dojoDnd"+_8;
var _b=_8.toLowerCase()+"State";
dojo.removeClass(this.node,_a+this[_b]);
dojo.addClass(this.node,_a+_9);
this[_b]=_9;
},_addItemClass:function(_c,_d){
dojo.addClass(_c,"dojoDndItem"+_d);
},_removeItemClass:function(_e,_f){
dojo.removeClass(_e,"dojoDndItem"+_f);
},onOverEvent:function(){
this._changeState("Container","Over");
},onOutEvent:function(){
this._changeState("Container","");
}});
}
