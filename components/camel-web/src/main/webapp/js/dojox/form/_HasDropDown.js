/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form._HasDropDown"]){
dojo._hasResource["dojox.form._HasDropDown"]=true;
dojo.provide("dojox.form._HasDropDown");
dojo.require("dijit._Widget");
dojo.declare("dojox.form._HasDropDown",null,{dropDownNode:null,popupStateNode:null,aroundNode:null,dropDown:null,autoWidth:true,_stopClickEvents:true,_onMenuMouseup:function(e){
},_onDropDownMouse:function(e){
if(e.type=="click"&&!this._seenKeydown){
return;
}
this._seenKeydown=false;
if(e.type=="mousedown"){
this._docHandler=this.connect(dojo.doc,"onmouseup","_onDropDownMouseup");
}
if(this.disabled||this.readOnly){
return;
}
if(this._stopClickEvents){
dojo.stopEvent(e);
}
this.toggleDropDown();
if(e.type=="click"||e.type=="keypress"){
this._onDropDownMouseup();
}
},_onDropDownMouseup:function(e){
if(e&&this._docHandler){
this.disconnect(this._docHandler);
}
var _4=this.dropDown,_5=false;
if(e&&this._opened){
var t=e.target;
while(t&&!_5){
if(dojo.hasClass(t,"dijitPopup")){
_5=true;
}else{
t=t.parentNode;
}
}
if(_5){
this._onMenuMouseup(e);
return;
}
}
if(this._opened&&_4.focus){
window.setTimeout(dojo.hitch(_4,"focus"),1);
}else{
dijit.focus(this.focusNode);
}
},_setupDropdown:function(){
this.dropDownNode=this.dropDownNode||this.focusNode||this.domNode;
this.popupStateNode=this.popupStateNode||this.focusNode||this.dropDownNode;
this.aroundNode=this.aroundNode||this.domNode;
this.connect(this.dropDownNode,"onmousedown","_onDropDownMouse");
this.connect(this.dropDownNode,"onclick","_onDropDownMouse");
this.connect(this.dropDownNode,"onkeydown","_onDropDownKeydown");
this.connect(this.dropDownNode,"onblur","_onDropDownBlur");
this.connect(this.dropDownNode,"onkeypress","_onKey");
if(this._setStateClass){
this.connect(this,"openDropDown","_setStateClass");
this.connect(this,"closeDropDown","_setStateClass");
}
},postCreate:function(){
this._setupDropdown();
this.inherited("postCreate",arguments);
},startup:function(){
dijit.popup.prepare(this.dropDown.domNode);
this.inherited("startup",arguments);
},destroyDescendants:function(){
if(this.dropDown){
this.dropDown.destroyRecursive();
delete this.dropDown;
}
this.inherited("destroyDescendants",arguments);
},_onDropDownKeydown:function(e){
this._seenKeydown=true;
},_onKeyPress:function(e){
if(this._opened&&e.charOrCode==dojo.keys.ESCAPE&&!e.shiftKey&&!e.ctrlKey&&!e.altKey){
this.toggleDropDown();
dojo.stopEvent(e);
return;
}
this.inherited(arguments);
},_onDropDownBlur:function(e){
this._seenKeydown=false;
},_onKey:function(e){
if(this.disabled||this.readOnly){
return;
}
var d=this.dropDown;
if(d&&this._opened&&d.handleKey){
if(d.handleKey(e)===false){
return;
}
}
if(d&&this._opened&&e.keyCode==dojo.keys.ESCAPE){
this.toggleDropDown();
return;
}
if(e.keyCode==dojo.keys.DOWN_ARROW){
this._onDropDownMouse(e);
}
},_onBlur:function(){
this.closeDropDown();
this.inherited("_onBlur",arguments);
},isLoaded:function(){
return true;
},loadDropDown:function(_c){
_c();
},toggleDropDown:function(){
if(this.disabled||this.readOnly){
return;
}
this.focus();
var _d=this.dropDown;
if(!_d){
return;
}
if(!this._opened){
if(!this.isLoaded()){
this.loadDropDown(dojo.hitch(this,"openDropDown"));
return;
}else{
this.openDropDown();
}
}else{
this.closeDropDown();
}
},openDropDown:function(){
var _e=this.dropDown;
var _f=_e.domNode.style.width;
var _10=this;
var _11=dijit.popup.open({parent:this,popup:_e,around:this.aroundNode,orient:this.isLeftToRight()?{"BL":"TL","BR":"TR","TL":"BL","TR":"BR"}:{"BR":"TR","BL":"TL","TR":"BR","TL":"BL"},onExecute:function(){
_10.closeDropDown(true);
},onCancel:function(){
_10.closeDropDown(true);
},onClose:function(){
_e.domNode.style.width=_f;
dojo.attr(_10.popupStateNode,"popupActive",false);
dojo.removeClass(_10.popupStateNode,"dojoxHasDropDownOpen");
_10._opened=false;
_10.state="";
}});
if(this.autoWidth&&this.domNode.offsetWidth>_e.domNode.offsetWidth){
var _12=null;
if(!this.isLeftToRight()){
_12=_e.domNode.parentNode;
var _13=_12.offsetLeft+_12.offsetWidth;
}
if(_e.resize){
_e.resize({w:this.domNode.offsetWidth});
}else{
dojo.marginBox(_e.domNode,{w:this.domNode.offsetWidth});
}
if(_12){
_12.style.left=_13-this.domNode.offsetWidth+"px";
}
}
dojo.attr(this.popupStateNode,"popupActive","true");
dojo.addClass(_10.popupStateNode,"dojoxHasDropDownOpen");
this._opened=true;
this.state="Opened";
return _11;
},closeDropDown:function(_14){
if(this._opened){
dijit.popup.close(this.dropDown);
if(_14){
this.focus();
}
this._opened=false;
this.state="";
}
}});
}
