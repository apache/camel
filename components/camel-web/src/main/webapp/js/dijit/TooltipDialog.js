/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.TooltipDialog"]){
dojo._hasResource["dijit.TooltipDialog"]=true;
dojo.provide("dijit.TooltipDialog");
dojo.require("dijit.layout.ContentPane");
dojo.require("dijit._Templated");
dojo.require("dijit.form._FormMixin");
dojo.require("dijit._DialogMixin");
dojo.declare("dijit.TooltipDialog",[dijit.layout.ContentPane,dijit._Templated,dijit.form._FormMixin,dijit._DialogMixin],{title:"",doLayout:false,autofocus:true,baseClass:"dijitTooltipDialog",_firstFocusItem:null,_lastFocusItem:null,templateString:null,templateString:"<div waiRole=\"presentation\">\n\t<div class=\"dijitTooltipContainer\" waiRole=\"presentation\">\n\t\t<div class =\"dijitTooltipContents dijitTooltipFocusNode\" dojoAttachPoint=\"containerNode\" tabindex=\"-1\" waiRole=\"dialog\"></div>\n\t</div>\n\t<div class=\"dijitTooltipConnector\" waiRole=\"presentation\"></div>\n</div>\n",postCreate:function(){
this.inherited(arguments);
this.connect(this.containerNode,"onkeypress","_onKey");
this.containerNode.title=this.title;
},orient:function(_1,_2,_3){
var c=this._currentOrientClass;
if(c){
dojo.removeClass(this.domNode,c);
}
c="dijitTooltipAB"+(_3.charAt(1)=="L"?"Left":"Right")+" dijitTooltip"+(_3.charAt(0)=="T"?"Below":"Above");
dojo.addClass(this.domNode,c);
this._currentOrientClass=c;
},onOpen:function(_5){
this.orient(this.domNode,_5.aroundCorner,_5.corner);
this._onShow();
if(this.autofocus){
this._getFocusItems(this.containerNode);
dijit.focus(this._firstFocusItem);
}
},_onKey:function(_6){
var _7=_6.target;
var dk=dojo.keys;
if(_6.charOrCode===dk.TAB){
this._getFocusItems(this.containerNode);
}
var _9=(this._firstFocusItem==this._lastFocusItem);
if(_6.charOrCode==dk.ESCAPE){
this.onCancel();
dojo.stopEvent(_6);
}else{
if(_7==this._firstFocusItem&&_6.shiftKey&&_6.charOrCode===dk.TAB){
if(!_9){
dijit.focus(this._lastFocusItem);
}
dojo.stopEvent(_6);
}else{
if(_7==this._lastFocusItem&&_6.charOrCode===dk.TAB&&!_6.shiftKey){
if(!_9){
dijit.focus(this._firstFocusItem);
}
dojo.stopEvent(_6);
}else{
if(_6.charOrCode===dk.TAB){
_6.stopPropagation();
}
}
}
}
}});
}
