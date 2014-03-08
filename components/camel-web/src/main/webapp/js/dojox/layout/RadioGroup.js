/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.layout.RadioGroup"]){
dojo._hasResource["dojox.layout.RadioGroup"]=true;
dojo.provide("dojox.layout.RadioGroup");
dojo.experimental("dojox.layout.RadioGroup");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._Contained");
dojo.require("dijit.layout.StackContainer");
dojo.require("dojo.fx.easing");
dojo.declare("dojox.layout.RadioGroup",[dijit.layout.StackContainer,dijit._Templated],{duration:750,hasButtons:false,buttonClass:"dojox.layout._RadioButton",templateString:"<div class=\"dojoxRadioGroup\">"+" \t<div dojoAttachPoint=\"buttonHolder\" style=\"display:none;\">"+"\t\t<table class=\"dojoxRadioButtons\"><tbody><tr class=\"dojoxRadioButtonRow\" dojoAttachPoint=\"buttonNode\"></tr></tbody></table>"+"\t</div>"+"\t<div class=\"dojoxRadioView\" dojoAttachPoint=\"containerNode\"></div>"+"</div>",startup:function(){
this.inherited(arguments);
this._children=this.getChildren();
this._buttons=this._children.length;
this._size=dojo.coords(this.containerNode);
if(this.hasButtons){
dojo.style(this.buttonHolder,"display","block");
}
},_setupChild:function(_1){
if(this.hasButtons){
dojo.style(_1.domNode,"position","absolute");
var _2=this.buttonNode.appendChild(dojo.create("td"));
var n=dojo.create("div",null,_2),_4=dojo.getObject(this.buttonClass),_5=new _4({label:_1.title,page:_1},n);
dojo.mixin(_1,{_radioButton:_5});
_5.startup();
}
_1.domNode.style.display="none";
},removeChild:function(_6){
if(this.hasButtons&&_6._radioButton){
_6._radioButton.destroy();
delete _6._radioButton;
}
this.inherited(arguments);
},_transition:function(_7,_8){
this._showChild(_7);
if(_8){
this._hideChild(_8);
}
if(this.doLayout&&_7.resize){
_7.resize(this._containerContentBox||this._contentBox);
}
},_showChild:function(_9){
var _a=this.getChildren();
_9.isFirstChild=(_9==_a[0]);
_9.isLastChild=(_9==_a[_a.length-1]);
_9.selected=true;
_9.domNode.style.display="";
if(_9._onShow){
_9._onShow();
}else{
if(_9.onShow){
_9.onShow();
}
}
},_hideChild:function(_b){
_b.selected=false;
_b.domNode.style.display="none";
if(_b.onHide){
_b.onHide();
}
}});
dojo.declare("dojox.layout.RadioGroupFade",dojox.layout.RadioGroup,{_hideChild:function(_c){
dojo.fadeOut({node:_c.domNode,duration:this.duration,onEnd:dojo.hitch(this,"inherited",arguments)}).play();
},_showChild:function(_d){
this.inherited(arguments);
dojo.style(_d.domNode,"opacity",0);
dojo.fadeIn({node:_d.domNode,duration:this.duration}).play();
}});
dojo.declare("dojox.layout.RadioGroupSlide",dojox.layout.RadioGroup,{easing:"dojo.fx.easing.backOut",zTop:99,constructor:function(){
if(dojo.isString(this.easing)){
this.easing=dojo.getObject(this.easing);
}
},_positionChild:function(_e){
if(!this._size){
return;
}
var rA=true,rB=true;
switch(_e.slideFrom){
case "bottom":
rB=!rB;
break;
case "right":
rA=!rA;
rB=!rB;
break;
case "top":
break;
case "left":
rA=!rA;
break;
default:
rA=Math.round(Math.random());
rB=Math.round(Math.random());
break;
}
var _11=rA?"top":"left",val=(rB?"-":"")+(this._size[rA?"h":"w"]+20)+"px";
dojo.style(_e.domNode,_11,val);
},_showChild:function(_13){
var _14=this.getChildren();
_13.isFirstChild=(_13==_14[0]);
_13.isLastChild=(_13==_14[_14.length-1]);
_13.selected=true;
dojo.style(_13.domNode,{zIndex:this.zTop,display:""});
if(this._anim&&this._anim.status()=="playing"){
this._anim.gotoPercent(100,true);
}
this._anim=dojo.animateProperty({node:_13.domNode,properties:{left:0,top:0},duration:this.duration,easing:this.easing,onEnd:dojo.hitch(_13,function(){
if(this.onShow){
this.onShow();
}
if(this._onShow){
this._onShow();
}
}),beforeBegin:dojo.hitch(this,"_positionChild",_13)});
this._anim.play();
},_hideChild:function(_15){
_15.selected=false;
_15.domNode.style.zIndex=this.zTop-1;
if(_15.onHide){
_15.onHide();
}
}});
dojo.declare("dojox.layout._RadioButton",[dijit._Widget,dijit._Templated,dijit._Contained],{label:"",page:null,templateString:"<div dojoAttachPoint=\"focusNode\" class=\"dojoxRadioButton\"><span dojoAttachPoint=\"titleNode\" class=\"dojoxRadioButtonLabel\">${label}</span></div>",startup:function(){
this.connect(this.domNode,"onmouseenter","_onMouse");
},_onMouse:function(e){
this.getParent().selectChild(this.page);
this._clearSelected();
dojo.addClass(this.domNode,"dojoxRadioButtonSelected");
},_clearSelected:function(){
dojo.query(".dojoxRadioButtonSelected",this.domNode.parentNode.parentNode).removeClass("dojoxRadioButtonSelected");
}});
dojo.extend(dijit._Widget,{slideFrom:"random"});
}
