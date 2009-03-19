/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.Menu"]){
dojo._hasResource["dijit.Menu"]=true;
dojo.provide("dijit.Menu");
dojo.require("dijit._Widget");
dojo.require("dijit._KeyNavContainer");
dojo.require("dijit._Templated");
dojo.declare("dijit._MenuBase",[dijit._Widget,dijit._Templated,dijit._KeyNavContainer],{parentMenu:null,popupDelay:500,startup:function(){
if(this._started){
return;
}
dojo.forEach(this.getChildren(),function(_1){
_1.startup();
});
this.startupKeyNavChildren();
this.inherited(arguments);
},onExecute:function(){
},onCancel:function(_2){
},_moveToPopup:function(_3){
if(this.focusedChild&&this.focusedChild.popup&&!this.focusedChild.disabled){
this.focusedChild._onClick(_3);
}else{
var _4=this._getTopMenu();
if(_4&&_4._isMenuBar){
_4.focusNext();
}
}
},onItemHover:function(_5){
if(this.isActive){
this.focusChild(_5);
if(this.focusedChild.popup&&!this.focusedChild.disabled&&!this.hover_timer){
this.hover_timer=setTimeout(dojo.hitch(this,"_openPopup"),this.popupDelay);
}
}
},_onChildBlur:function(_6){
_6._setSelected(false);
dijit.popup.close(_6.popup);
this._stopPopupTimer();
},onItemUnhover:function(_7){
if(this.isActive){
this._stopPopupTimer();
}
},_stopPopupTimer:function(){
if(this.hover_timer){
clearTimeout(this.hover_timer);
this.hover_timer=null;
}
},_getTopMenu:function(){
for(var _8=this;_8.parentMenu;_8=_8.parentMenu){
}
return _8;
},onItemClick:function(_9,_a){
if(_9.disabled){
return false;
}
this.focusChild(_9);
if(_9.popup){
if(!this.is_open){
this._openPopup();
}
}else{
this.onExecute();
_9.onClick(_a);
}
},_openPopup:function(){
this._stopPopupTimer();
var _b=this.focusedChild;
var _c=_b.popup;
if(_c.isShowingNow){
return;
}
_c.parentMenu=this;
var _d=this;
dijit.popup.open({parent:this,popup:_c,around:_b.domNode,orient:this._orient||(this.isLeftToRight()?{"TR":"TL","TL":"TR"}:{"TL":"TR","TR":"TL"}),onCancel:function(){
dijit.popup.close(_c);
_b.focus();
_d.currentPopup=null;
},onExecute:dojo.hitch(this,"_onDescendantExecute")});
this.currentPopup=_c;
if(_c.focus){
setTimeout(dojo.hitch(_c,"focus"),0);
}
},onOpen:function(e){
this.isShowingNow=true;
},onClose:function(){
this._stopPopupTimer();
this.parentMenu=null;
this.isShowingNow=false;
this.currentPopup=null;
if(this.focusedChild){
this._onChildBlur(this.focusedChild);
this.focusedChild=null;
}
},_onFocus:function(){
this.isActive=true;
dojo.addClass(this.domNode,"dijitMenuActive");
dojo.removeClass(this.domNode,"dijitMenuPassive");
this.inherited(arguments);
},_onBlur:function(){
this.isActive=false;
dojo.removeClass(this.domNode,"dijitMenuActive");
dojo.addClass(this.domNode,"dijitMenuPassive");
this.onClose();
this.inherited(arguments);
},_onDescendantExecute:function(){
this.onClose();
}});
dojo.declare("dijit.Menu",dijit._MenuBase,{constructor:function(){
this._bindings=[];
},templateString:"<table class=\"dijit dijitMenu dijitMenuPassive dijitReset dijitMenuTable\" waiRole=\"menu\" tabIndex=\"${tabIndex}\" dojoAttachEvent=\"onkeypress:_onKeyPress\">\n\t<tbody class=\"dijitReset\" dojoAttachPoint=\"containerNode\"></tbody>\n</table>\n",targetNodeIds:[],contextMenuForWindow:false,leftClickToOpen:false,_contextMenuWithMouse:false,postCreate:function(){
if(this.contextMenuForWindow){
this.bindDomNode(dojo.body());
}else{
dojo.forEach(this.targetNodeIds,this.bindDomNode,this);
}
var k=dojo.keys,l=this.isLeftToRight();
this._openSubMenuKey=l?k.RIGHT_ARROW:k.LEFT_ARROW;
this._closeSubMenuKey=l?k.LEFT_ARROW:k.RIGHT_ARROW;
this.connectKeyNavHandlers([k.UP_ARROW],[k.DOWN_ARROW]);
},_onKeyPress:function(evt){
if(evt.ctrlKey||evt.altKey){
return;
}
switch(evt.charOrCode){
case this._openSubMenuKey:
this._moveToPopup(evt);
dojo.stopEvent(evt);
break;
case this._closeSubMenuKey:
if(this.parentMenu){
if(this.parentMenu._isMenuBar){
this.parentMenu.focusPrev();
}else{
this.onCancel(false);
}
}else{
dojo.stopEvent(evt);
}
break;
}
},_iframeContentWindow:function(_12){
var win=dijit.getDocumentWindow(dijit.Menu._iframeContentDocument(_12))||dijit.Menu._iframeContentDocument(_12)["__parent__"]||(_12.name&&dojo.doc.frames[_12.name])||null;
return win;
},_iframeContentDocument:function(_14){
var doc=_14.contentDocument||(_14.contentWindow&&_14.contentWindow.document)||(_14.name&&dojo.doc.frames[_14.name]&&dojo.doc.frames[_14.name].document)||null;
return doc;
},bindDomNode:function(_16){
_16=dojo.byId(_16);
var win=dijit.getDocumentWindow(_16.ownerDocument);
if(_16.tagName.toLowerCase()=="iframe"){
win=this._iframeContentWindow(_16);
_16=dojo.withGlobal(win,dojo.body);
}
var cn=(_16==dojo.body()?dojo.doc:_16);
_16[this.id]=this._bindings.push([dojo.connect(cn,(this.leftClickToOpen)?"onclick":"oncontextmenu",this,"_openMyself"),dojo.connect(cn,"onkeydown",this,"_contextKey"),dojo.connect(cn,"onmousedown",this,"_contextMouse")]);
},unBindDomNode:function(_19){
var _1a=dojo.byId(_19);
if(_1a){
var bid=_1a[this.id]-1,b=this._bindings[bid];
dojo.forEach(b,dojo.disconnect);
delete this._bindings[bid];
}
},_contextKey:function(e){
this._contextMenuWithMouse=false;
if(e.keyCode==dojo.keys.F10){
dojo.stopEvent(e);
if(e.shiftKey&&e.type=="keydown"){
var _e={target:e.target,pageX:e.pageX,pageY:e.pageY};
_e.preventDefault=_e.stopPropagation=function(){
};
window.setTimeout(dojo.hitch(this,function(){
this._openMyself(_e);
}),1);
}
}
},_contextMouse:function(e){
this._contextMenuWithMouse=true;
},_openMyself:function(e){
if(this.leftClickToOpen&&e.button>0){
return;
}
dojo.stopEvent(e);
var x,y;
if(dojo.isSafari||this._contextMenuWithMouse){
x=e.pageX;
y=e.pageY;
}else{
var _23=dojo.coords(e.target,true);
x=_23.x+10;
y=_23.y+10;
}
var _24=this;
var _25=dijit.getFocus(this);
function _26(){
dijit.focus(_25);
dijit.popup.close(_24);
};
dijit.popup.open({popup:this,x:x,y:y,onExecute:_26,onCancel:_26,orient:this.isLeftToRight()?"L":"R"});
this.focus();
this._onBlur=function(){
this.inherited("_onBlur",arguments);
dijit.popup.close(this);
};
},uninitialize:function(){
dojo.forEach(this.targetNodeIds,this.unBindDomNode,this);
this.inherited(arguments);
}});
dojo.require("dijit.MenuItem");
dojo.require("dijit.PopupMenuItem");
dojo.require("dijit.CheckedMenuItem");
dojo.require("dijit.MenuSeparator");
}
