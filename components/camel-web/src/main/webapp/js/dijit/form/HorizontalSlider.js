/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.HorizontalSlider"]){
dojo._hasResource["dijit.form.HorizontalSlider"]=true;
dojo.provide("dijit.form.HorizontalSlider");
dojo.require("dijit.form._FormWidget");
dojo.require("dijit._Container");
dojo.require("dojo.dnd.move");
dojo.require("dijit.form.Button");
dojo.require("dojo.number");
dojo.require("dojo._base.fx");
dojo.declare("dijit.form.HorizontalSlider",[dijit.form._FormValueWidget,dijit._Container],{templateString:"<table class=\"dijit dijitReset dijitSlider\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" rules=\"none\" dojoAttachEvent=\"onkeypress:_onKeyPress\"\n\t><tr class=\"dijitReset\"\n\t\t><td class=\"dijitReset\" colspan=\"2\"></td\n\t\t><td dojoAttachPoint=\"containerNode,topDecoration\" class=\"dijitReset\" style=\"text-align:center;width:100%;\"></td\n\t\t><td class=\"dijitReset\" colspan=\"2\"></td\n\t></tr\n\t><tr class=\"dijitReset\"\n\t\t><td class=\"dijitReset dijitSliderButtonContainer dijitSliderButtonContainerH\"\n\t\t\t><div class=\"dijitSliderDecrementIconH\" tabIndex=\"-1\" style=\"display:none\" dojoAttachPoint=\"decrementButton\"><span class=\"dijitSliderButtonInner\">-</span></div\n\t\t></td\n\t\t><td class=\"dijitReset\"\n\t\t\t><div class=\"dijitSliderBar dijitSliderBumper dijitSliderBumperH dijitSliderLeftBumper dijitSliderLeftBumper\" dojoAttachEvent=\"onmousedown:_onClkDecBumper\"></div\n\t\t></td\n\t\t><td class=\"dijitReset\"\n\t\t\t><input dojoAttachPoint=\"valueNode\" type=\"hidden\" ${nameAttrSetting}\n\t\t\t/><div class=\"dijitReset dijitSliderBarContainerH\" waiRole=\"presentation\" dojoAttachPoint=\"sliderBarContainer\"\n\t\t\t\t><div waiRole=\"presentation\" dojoAttachPoint=\"progressBar\" class=\"dijitSliderBar dijitSliderBarH dijitSliderProgressBar dijitSliderProgressBarH\" dojoAttachEvent=\"onmousedown:_onBarClick\"\n\t\t\t\t\t><div class=\"dijitSliderMoveable dijitSliderMoveableH\" \n\t\t\t\t\t\t><div dojoAttachPoint=\"sliderHandle,focusNode\" class=\"dijitSliderImageHandle dijitSliderImageHandleH\" dojoAttachEvent=\"onmousedown:_onHandleClick\" waiRole=\"slider\" valuemin=\"${minimum}\" valuemax=\"${maximum}\"></div\n\t\t\t\t\t></div\n\t\t\t\t></div\n\t\t\t\t><div waiRole=\"presentation\" dojoAttachPoint=\"remainingBar\" class=\"dijitSliderBar dijitSliderBarH dijitSliderRemainingBar dijitSliderRemainingBarH\" dojoAttachEvent=\"onmousedown:_onBarClick\"></div\n\t\t\t></div\n\t\t></td\n\t\t><td class=\"dijitReset\"\n\t\t\t><div class=\"dijitSliderBar dijitSliderBumper dijitSliderBumperH dijitSliderRightBumper dijitSliderRightBumper\" dojoAttachEvent=\"onmousedown:_onClkIncBumper\"></div\n\t\t></td\n\t\t><td class=\"dijitReset dijitSliderButtonContainer dijitSliderButtonContainerH\" style=\"right:0px;\"\n\t\t\t><div class=\"dijitSliderIncrementIconH\" tabIndex=\"-1\" style=\"display:none\" dojoAttachPoint=\"incrementButton\"><span class=\"dijitSliderButtonInner\">+</span></div\n\t\t></td\n\t></tr\n\t><tr class=\"dijitReset\"\n\t\t><td class=\"dijitReset\" colspan=\"2\"></td\n\t\t><td dojoAttachPoint=\"containerNode,bottomDecoration\" class=\"dijitReset\" style=\"text-align:center;\"></td\n\t\t><td class=\"dijitReset\" colspan=\"2\"></td\n\t></tr\n></table>\n",value:0,showButtons:true,minimum:0,maximum:100,discreteValues:Infinity,pageIncrement:2,clickSelect:true,slideDuration:dijit.defaultDuration,widgetsInTemplate:true,attributeMap:dojo.delegate(dijit.form._FormWidget.prototype.attributeMap,{id:""}),baseClass:"dijitSlider",_mousePixelCoord:"pageX",_pixelCount:"w",_startingPixelCoord:"x",_startingPixelCount:"l",_handleOffsetCoord:"left",_progressPixelSize:"width",_onKeyPress:function(e){
if(this.disabled||this.readOnly||e.altKey||e.ctrlKey){
return;
}
switch(e.charOrCode){
case dojo.keys.HOME:
this._setValueAttr(this.minimum,true);
break;
case dojo.keys.END:
this._setValueAttr(this.maximum,true);
break;
case ((this._descending||this.isLeftToRight())?dojo.keys.RIGHT_ARROW:dojo.keys.LEFT_ARROW):
case (this._descending===false?dojo.keys.DOWN_ARROW:dojo.keys.UP_ARROW):
case (this._descending===false?dojo.keys.PAGE_DOWN:dojo.keys.PAGE_UP):
this.increment(e);
break;
case ((this._descending||this.isLeftToRight())?dojo.keys.LEFT_ARROW:dojo.keys.RIGHT_ARROW):
case (this._descending===false?dojo.keys.UP_ARROW:dojo.keys.DOWN_ARROW):
case (this._descending===false?dojo.keys.PAGE_UP:dojo.keys.PAGE_DOWN):
this.decrement(e);
break;
default:
return;
}
dojo.stopEvent(e);
},_onHandleClick:function(e){
if(this.disabled||this.readOnly){
return;
}
if(!dojo.isIE){
dijit.focus(this.sliderHandle);
}
dojo.stopEvent(e);
},_isReversed:function(){
return !this.isLeftToRight();
},_onBarClick:function(e){
if(this.disabled||this.readOnly||!this.clickSelect){
return;
}
dijit.focus(this.sliderHandle);
dojo.stopEvent(e);
var _4=dojo.coords(this.sliderBarContainer,true);
var _5=e[this._mousePixelCoord]-_4[this._startingPixelCoord];
this._setPixelValue(this._isReversed()?(_4[this._pixelCount]-_5):_5,_4[this._pixelCount],true);
this._movable.onMouseDown(e);
},_setPixelValue:function(_6,_7,_8){
if(this.disabled||this.readOnly){
return;
}
_6=_6<0?0:_7<_6?_7:_6;
var _9=this.discreteValues;
if(_9<=1||_9==Infinity){
_9=_7;
}
_9--;
var _a=_7/_9;
var _b=Math.round(_6/_a);
this._setValueAttr((this.maximum-this.minimum)*_b/_9+this.minimum,_8);
},_setValueAttr:function(_c,_d){
this.valueNode.value=this.value=_c;
dijit.setWaiState(this.focusNode,"valuenow",_c);
this.inherited(arguments);
var _e=(_c-this.minimum)/(this.maximum-this.minimum);
var _f=(this._descending===false)?this.remainingBar:this.progressBar;
var _10=(this._descending===false)?this.progressBar:this.remainingBar;
if(this._inProgressAnim&&this._inProgressAnim.status!="stopped"){
this._inProgressAnim.stop(true);
}
if(_d&&this.slideDuration>0&&_f.style[this._progressPixelSize]){
var _11=this;
var _12={};
var _13=parseFloat(_f.style[this._progressPixelSize]);
var _14=this.slideDuration*(_e-_13/100);
if(_14==0){
return;
}
if(_14<0){
_14=0-_14;
}
_12[this._progressPixelSize]={start:_13,end:_e*100,units:"%"};
this._inProgressAnim=dojo.animateProperty({node:_f,duration:_14,onAnimate:function(v){
_10.style[_11._progressPixelSize]=(100-parseFloat(v[_11._progressPixelSize]))+"%";
},onEnd:function(){
delete _11._inProgressAnim;
},properties:_12});
this._inProgressAnim.play();
}else{
_f.style[this._progressPixelSize]=(_e*100)+"%";
_10.style[this._progressPixelSize]=((1-_e)*100)+"%";
}
},_bumpValue:function(_16){
if(this.disabled||this.readOnly){
return;
}
var s=dojo.getComputedStyle(this.sliderBarContainer);
var c=dojo._getContentBox(this.sliderBarContainer,s);
var _19=this.discreteValues;
if(_19<=1||_19==Infinity){
_19=c[this._pixelCount];
}
_19--;
var _1a=(this.value-this.minimum)*_19/(this.maximum-this.minimum)+_16;
if(_1a<0){
_1a=0;
}
if(_1a>_19){
_1a=_19;
}
_1a=_1a*(this.maximum-this.minimum)/_19+this.minimum;
this._setValueAttr(_1a,true);
},_onClkBumper:function(val){
if(this.disabled||this.readOnly||!this.clickSelect){
return;
}
this._setValueAttr(val,true);
},_onClkIncBumper:function(){
this._onClkBumper(this._descending===false?this.minimum:this.maximum);
},_onClkDecBumper:function(){
this._onClkBumper(this._descending===false?this.maximum:this.minimum);
},decrement:function(e){
this._bumpValue(e.charOrCode==dojo.keys.PAGE_DOWN?-this.pageIncrement:-1);
},increment:function(e){
this._bumpValue(e.charOrCode==dojo.keys.PAGE_UP?this.pageIncrement:1);
},_mouseWheeled:function(evt){
dojo.stopEvent(evt);
var _1f=!dojo.isMozilla;
var _20=evt[(_1f?"wheelDelta":"detail")]*(_1f?1:-1);
this[(_20<0?"decrement":"increment")](evt);
},startup:function(){
dojo.forEach(this.getChildren(),function(_21){
if(this[_21.container]!=this.containerNode){
this[_21.container].appendChild(_21.domNode);
}
},this);
},_typematicCallback:function(_22,_23,e){
if(_22==-1){
return;
}
this[(_23==(this._descending?this.incrementButton:this.decrementButton))?"decrement":"increment"](e);
},postCreate:function(){
if(this.showButtons){
this.incrementButton.style.display="";
this.decrementButton.style.display="";
this._connects.push(dijit.typematic.addMouseListener(this.decrementButton,this,"_typematicCallback",25,500));
this._connects.push(dijit.typematic.addMouseListener(this.incrementButton,this,"_typematicCallback",25,500));
}
this.connect(this.domNode,!dojo.isMozilla?"onmousewheel":"DOMMouseScroll","_mouseWheeled");
var _25=this;
var _26=function(){
dijit.form._SliderMover.apply(this,arguments);
this.widget=_25;
};
dojo.extend(_26,dijit.form._SliderMover.prototype);
this._movable=new dojo.dnd.Moveable(this.sliderHandle,{mover:_26});
var _27=dojo.query("label[for=\""+this.id+"\"]");
if(_27.length){
_27[0].id=(this.id+"_label");
dijit.setWaiState(this.focusNode,"labelledby",_27[0].id);
}
dijit.setWaiState(this.focusNode,"valuemin",this.minimum);
dijit.setWaiState(this.focusNode,"valuemax",this.maximum);
this.inherited(arguments);
},destroy:function(){
this._movable.destroy();
if(this._inProgressAnim&&this._inProgressAnim.status!="stopped"){
this._inProgressAnim.stop(true);
}
this.inherited(arguments);
}});
dojo.declare("dijit.form._SliderMover",dojo.dnd.Mover,{onMouseMove:function(e){
var _29=this.widget;
var _2a=_29._abspos;
if(!_2a){
_2a=_29._abspos=dojo.coords(_29.sliderBarContainer,true);
_29._setPixelValue_=dojo.hitch(_29,"_setPixelValue");
_29._isReversed_=_29._isReversed();
}
var _2b=e[_29._mousePixelCoord]-_2a[_29._startingPixelCoord];
_29._setPixelValue_(_29._isReversed_?(_2a[_29._pixelCount]-_2b):_2b,_2a[_29._pixelCount],false);
},destroy:function(e){
dojo.dnd.Mover.prototype.destroy.apply(this,arguments);
var _2d=this.widget;
_2d._abspos=null;
_2d._setValueAttr(_2d.value,true);
}});
}
