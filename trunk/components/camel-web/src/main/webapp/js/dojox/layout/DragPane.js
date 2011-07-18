/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.layout.DragPane"]){
dojo._hasResource["dojox.layout.DragPane"]=true;
dojo.provide("dojox.layout.DragPane");
dojo.require("dijit._Widget");
dojo.declare("dojox.layout.DragPane",dijit._Widget,{invert:true,postCreate:function(){
this.inherited(arguments);
this.connect(this.domNode,"onmousedown","_down");
this.connect(this.domNode,"onmouseup","_up");
},_down:function(e){
var t=this.domNode;
dojo.style(t,"cursor","move");
this._x=e.pageX;
this._y=e.pageY;
if((this._x<t.offsetLeft+t.clientWidth)&&(this._y<t.offsetTop+t.clientHeight)){
dojo.setSelectable(t,false);
this._mover=this.connect(t,"onmousemove","_move");
}
},_up:function(e){
dojo.setSelectable(this.domNode,true);
dojo.style(this.domNode,"cursor","pointer");
this.disconnect(this._mover);
},_move:function(e){
var _5=this.invert?1:-1;
this.domNode.scrollTop+=(this._y-e.pageY)*_5;
this.domNode.scrollLeft+=(this._x-e.pageX)*_5;
this._x=e.pageX;
this._y=e.pageY;
}});
}
