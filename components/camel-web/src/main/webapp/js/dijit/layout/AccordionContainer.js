/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.layout.AccordionContainer"]){
dojo._hasResource["dijit.layout.AccordionContainer"]=true;
dojo.provide("dijit.layout.AccordionContainer");
dojo.require("dojo.fx");
dojo.require("dijit._Container");
dojo.require("dijit._Templated");
dojo.require("dijit.layout.StackContainer");
dojo.require("dijit.layout.ContentPane");
dojo.require("dijit.layout.AccordionPane");
dojo.declare("dijit.layout.AccordionContainer",dijit.layout.StackContainer,{duration:dijit.defaultDuration,_verticalSpace:0,baseClass:"dijitAccordionContainer",postCreate:function(){
this.domNode.style.overflow="hidden";
this.inherited(arguments);
dijit.setWaiRole(this.domNode,"tablist");
},startup:function(){
if(this._started){
return;
}
this.inherited(arguments);
if(this.selectedChildWidget){
var _1=this.selectedChildWidget.containerNode.style;
_1.display="";
_1.overflow="auto";
this.selectedChildWidget._buttonWidget._setSelectedState(true);
}
},_getTargetHeight:function(_2){
var cs=dojo.getComputedStyle(_2);
return Math.max(this._verticalSpace-dojo._getPadBorderExtents(_2,cs).h,0);
},layout:function(){
var _4=this.selectedChildWidget;
var _5=0;
dojo.forEach(this.getChildren(),function(_6){
_5+=_6._buttonWidget.getTitleHeight();
});
var _7=this._contentBox;
this._verticalSpace=_7.h-_5;
this._containerContentBox={h:this._verticalSpace,w:_7.w};
if(_4){
_4.resize(this._containerContentBox);
}
},_setupChild:function(_8){
_8._buttonWidget=new dijit.layout._AccordionButton({contentWidget:_8,title:_8.title,id:_8.id+"_button",parent:this});
dojo.place(_8._buttonWidget.domNode,_8.domNode,"before");
this.inherited(arguments);
},removeChild:function(_9){
_9._buttonWidget.destroy();
this.inherited(arguments);
},getChildren:function(){
return dojo.filter(this.inherited(arguments),function(_a){
return _a.declaredClass!="dijit.layout._AccordionButton";
});
},destroy:function(){
dojo.forEach(this.getChildren(),function(_b){
_b._buttonWidget.destroy();
});
this.inherited(arguments);
},_transition:function(_c,_d){
if(this._inTransition){
return;
}
this._inTransition=true;
var _e=[];
var _f=this._verticalSpace;
if(_c){
_c._buttonWidget.setSelected(true);
this._showChild(_c);
if(this.doLayout&&_c.resize){
_c.resize(this._containerContentBox);
}
var _10=_c.domNode;
dojo.addClass(_10,"dijitVisible");
dojo.removeClass(_10,"dijitHidden");
var _11=_10.style.overflow;
_10.style.overflow="hidden";
_e.push(dojo.animateProperty({node:_10,duration:this.duration,properties:{height:{start:1,end:this._getTargetHeight(_10)}},onEnd:function(){
_10.style.overflow=_11;
}}));
}
if(_d){
_d._buttonWidget.setSelected(false);
var _12=_d.domNode;
var _13=_12.style.overflow;
_12.style.overflow="hidden";
_e.push(dojo.animateProperty({node:_12,duration:this.duration,properties:{height:{start:this._getTargetHeight(_12),end:1}},onEnd:function(){
dojo.addClass(_12,"dijitHidden");
dojo.removeClass(_12,"dijitVisible");
_12.style.overflow=_13;
}}));
}
this._inTransition=false;
dojo.fx.combine(_e).play();
},_onKeyPress:function(e,_15){
if(this.disabled||e.altKey||!(_15||e.ctrlKey)){
return;
}
var k=dojo.keys,c=e.charOrCode;
if((_15&&(c==k.LEFT_ARROW||c==k.UP_ARROW))||(e.ctrlKey&&c==k.PAGE_UP)){
this._adjacent(false)._buttonWidget._onTitleClick();
dojo.stopEvent(e);
}else{
if((_15&&(c==k.RIGHT_ARROW||c==k.DOWN_ARROW))||(e.ctrlKey&&(c==k.PAGE_DOWN||c==k.TAB))){
this._adjacent(true)._buttonWidget._onTitleClick();
dojo.stopEvent(e);
}
}
}});
dojo.declare("dijit.layout._AccordionButton",[dijit._Widget,dijit._Templated],{templateString:"<div dojoAttachPoint='titleNode,focusNode' dojoAttachEvent='ondijitclick:_onTitleClick,onkeypress:_onTitleKeyPress,onfocus:_handleFocus,onblur:_handleFocus,onmouseenter:_onTitleEnter,onmouseleave:_onTitleLeave'\n\t\tclass='dijitAccordionTitle' wairole=\"tab\" waiState=\"expanded-false\"\n\t\t><span class='dijitInline dijitAccordionArrow' waiRole=\"presentation\"></span\n\t\t><span class='arrowTextUp' waiRole=\"presentation\">+</span\n\t\t><span class='arrowTextDown' waiRole=\"presentation\">-</span\n\t\t><span waiRole=\"presentation\" dojoAttachPoint='titleTextNode' class='dijitAccordionText'></span>\n</div>\n",attributeMap:dojo.mixin(dojo.clone(dijit.layout.ContentPane.prototype.attributeMap),{title:{node:"titleTextNode",type:"innerHTML"}}),baseClass:"dijitAccordionTitle",getParent:function(){
return this.parent;
},postCreate:function(){
this.inherited(arguments);
dojo.setSelectable(this.domNode,false);
this.setSelected(this.selected);
var _18=dojo.attr(this.domNode,"id").replace(" ","_");
dojo.attr(this.titleTextNode,"id",_18+"_title");
dijit.setWaiState(this.focusNode,"labelledby",dojo.attr(this.titleTextNode,"id"));
},getTitleHeight:function(){
return dojo.marginBox(this.titleNode).h;
},_onTitleClick:function(){
var _19=this.getParent();
if(!_19._inTransition){
_19.selectChild(this.contentWidget);
dijit.focus(this.focusNode);
}
},_onTitleEnter:function(){
dojo.addClass(this.focusNode,"dijitAccordionTitle-hover");
},_onTitleLeave:function(){
dojo.removeClass(this.focusNode,"dijitAccordionTitle-hover");
},_onTitleKeyPress:function(evt){
return this.getParent()._onKeyPress(evt,this.contentWidget);
},_setSelectedState:function(_1b){
this.selected=_1b;
dojo[(_1b?"addClass":"removeClass")](this.titleNode,"dijitAccordionTitle-selected");
dijit.setWaiState(this.focusNode,"expanded",_1b);
dijit.setWaiState(this.focusNode,"selected",_1b);
this.focusNode.setAttribute("tabIndex",_1b?"0":"-1");
},_handleFocus:function(e){
dojo[(e.type=="focus"?"addClass":"removeClass")](this.focusNode,"dijitAccordionFocused");
},setSelected:function(_1d){
this._setSelectedState(_1d);
if(_1d){
var cw=this.contentWidget;
if(cw.onSelected){
cw.onSelected();
}
}
}});
}
