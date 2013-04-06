/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.RangeSlider"]){
dojo._hasResource["dojox.form.RangeSlider"]=true;
dojo.provide("dojox.form.RangeSlider");
dojo.require("dijit.form.HorizontalSlider");
dojo.require("dijit.form.VerticalSlider");
dojo.require("dojox.fx");
dojo.declare("dojox.form._RangeSliderMixin",null,{value:[0,100],postCreate:function(){
this.inherited(arguments);
if(this._isReversed()){
this.value.sort(function(a,b){
return b-a;
});
}else{
this.value.sort(function(a,b){
return a-b;
});
}
var _5=this;
var _6=function(){
dijit.form._SliderMoverMax.apply(this,arguments);
this.widget=_5;
};
dojo.extend(_6,dijit.form._SliderMoverMax.prototype);
this._movableMax=new dojo.dnd.Moveable(this.sliderHandleMax,{mover:_6});
dijit.setWaiState(this.focusNodeMax,"valuemin",this.minimum);
dijit.setWaiState(this.focusNodeMax,"valuemax",this.maximum);
var _7=function(){
dijit.form._SliderBarMover.apply(this,arguments);
this.widget=_5;
};
dojo.extend(_7,dijit.form._SliderBarMover.prototype);
this._movableBar=new dojo.dnd.Moveable(this.progressBar,{mover:_7});
},destroy:function(){
this.inherited(arguments);
this._movableMax.destroy();
this._movableBar.destroy();
},_onKeyPress:function(e){
if(this.disabled||this.readOnly||e.altKey||e.ctrlKey){
return;
}
var _9=e.currentTarget;
var _a=false;
var _b=false;
var _c;
if(_9==this.sliderHandle){
_a=true;
}else{
if(_9==this.progressBar){
_b=true;
_a=true;
}else{
if(_9==this.sliderHandleMax){
_b=true;
}
}
}
switch(e.keyCode){
case dojo.keys.HOME:
this._setValueAttr(this.minimum,true,_b);
break;
case dojo.keys.END:
this._setValueAttr(this.maximum,true,_b);
break;
case ((this._descending||this.isLeftToRight())?dojo.keys.RIGHT_ARROW:dojo.keys.LEFT_ARROW):
case (this._descending===false?dojo.keys.DOWN_ARROW:dojo.keys.UP_ARROW):
case (this._descending===false?dojo.keys.PAGE_DOWN:dojo.keys.PAGE_UP):
if(_a&&_b){
_c=Array();
_c[0]={"change":e.keyCode==dojo.keys.PAGE_UP?this.pageIncrement:1,"useMaxValue":true};
_c[1]={"change":e.keyCode==dojo.keys.PAGE_UP?this.pageIncrement:1,"useMaxValue":false};
this._bumpValue(_c);
}else{
if(_a){
this._bumpValue(e.keyCode==dojo.keys.PAGE_UP?this.pageIncrement:1,true);
}else{
if(_b){
this._bumpValue(e.keyCode==dojo.keys.PAGE_UP?this.pageIncrement:1);
}
}
}
break;
case ((this._descending||this.isLeftToRight())?dojo.keys.LEFT_ARROW:dojo.keys.RIGHT_ARROW):
case (this._descending===false?dojo.keys.UP_ARROW:dojo.keys.DOWN_ARROW):
case (this._descending===false?dojo.keys.PAGE_UP:dojo.keys.PAGE_DOWN):
if(_a&&_b){
_c=Array();
_c[0]={"change":e.keyCode==dojo.keys.PAGE_DOWN?-this.pageIncrement:-1,"useMaxValue":false};
_c[1]={"change":e.keyCode==dojo.keys.PAGE_DOWN?-this.pageIncrement:-1,"useMaxValue":true};
this._bumpValue(_c);
}else{
if(_a){
this._bumpValue(e.keyCode==dojo.keys.PAGE_DOWN?-this.pageIncrement:-1);
}else{
if(_b){
this._bumpValue(e.keyCode==dojo.keys.PAGE_DOWN?-this.pageIncrement:-1,true);
}
}
}
break;
default:
dijit.form._FormValueWidget.prototype._onKeyPress.apply(this,arguments);
this.inherited(arguments);
return;
}
dojo.stopEvent(e);
},_onHandleClickMax:function(e){
if(this.disabled||this.readOnly){
return;
}
if(!dojo.isIE){
dijit.focus(this.sliderHandleMax);
}
dojo.stopEvent(e);
},_onClkIncBumper:function(){
this._setValueAttr(this._descending===false?this.minimum:this.maximum,true,true);
},_bumpValue:function(_e,_f){
var _10;
if(!dojo.isArray(_e)){
_10=this._getBumpValue(_e,_f);
}else{
_10=Array();
_10[0]=this._getBumpValue(_e[0]["change"],_e[0]["useMaxValue"]);
_10[1]=this._getBumpValue(_e[1]["change"],_e[1]["useMaxValue"]);
}
this._setValueAttr(_10,true,!dojo.isArray(_e)&&((_e>0&&!_f)||(_f&&_e<0)));
},_getBumpValue:function(_11,_12){
var s=dojo.getComputedStyle(this.sliderBarContainer);
var c=dojo._getContentBox(this.sliderBarContainer,s);
var _15=this.discreteValues;
if(_15<=1||_15==Infinity){
_15=c[this._pixelCount];
}
_15--;
var _16=!_12?this.value[0]:this.value[1];
if((this._isReversed()&&_11<0)||(_11>0&&!this._isReversed())){
_16=!_12?this.value[1]:this.value[0];
}
var _17=(_16-this.minimum)*_15/(this.maximum-this.minimum)+_11;
if(_17<0){
_17=0;
}
if(_17>_15){
_17=_15;
}
return _17*(this.maximum-this.minimum)/_15+this.minimum;
},_onBarClick:function(e){
if(this.disabled||this.readOnly){
return;
}
if(!dojo.isIE){
dijit.focus(this.progressBar);
}
dojo.stopEvent(e);
},_onRemainingBarClick:function(e){
if(this.disabled||this.readOnly){
return;
}
if(!dojo.isIE){
dijit.focus(this.progressBar);
}
var _1a=dojo.coords(this.sliderBarContainer,true);
var bar=dojo.coords(this.progressBar,true);
var _1c=e[this._mousePixelCoord]-_1a[this._startingPixelCoord];
var _1d=bar[this._startingPixelCount];
var _1e=bar[this._startingPixelCount]+bar[this._pixelCount];
var _1f=this._isReversed()?_1c<=_1d:_1c>=_1e;
this._setPixelValue(this._isReversed()?(_1a[this._pixelCount]-_1c):_1c,_1a[this._pixelCount],true,_1f);
dojo.stopEvent(e);
},_setPixelValue:function(_20,_21,_22,_23){
if(this.disabled||this.readOnly){
return;
}
var _24=this._getValueByPixelValue(_20,_21);
this._setValueAttr(_24,_22,_23);
},_getValueByPixelValue:function(_25,_26){
_25=_25<0?0:_26<_25?_26:_25;
var _27=this.discreteValues;
if(_27<=1||_27==Infinity){
_27=_26;
}
_27--;
var _28=_26/_27;
var _29=Math.round(_25/_28);
return (this.maximum-this.minimum)*_29/_27+this.minimum;
},_setValueAttr:function(_2a,_2b,_2c){
var _2d=this.value;
if(!dojo.isArray(_2a)){
if(_2c){
if(this._isReversed()){
_2d[0]=_2a;
}else{
_2d[1]=_2a;
}
}else{
if(this._isReversed()){
_2d[1]=_2a;
}else{
_2d[0]=_2a;
}
}
}else{
_2d=_2a;
}
this._lastValueReported="";
this.valueNode.value=this.value=_2a=_2d;
dijit.setWaiState(this.focusNode,"valuenow",_2d[0]);
dijit.setWaiState(this.focusNodeMax,"valuenow",_2d[1]);
if(this._isReversed()){
this.value.sort(function(a,b){
return b-a;
});
}else{
this.value.sort(function(a,b){
return a-b;
});
}
dijit.form._FormValueWidget.prototype._setValueAttr.apply(this,arguments);
this._printSliderBar(_2b,_2c);
},_printSliderBar:function(_32,_33){
var _34=(this.value[0]-this.minimum)/(this.maximum-this.minimum);
var _35=(this.value[1]-this.minimum)/(this.maximum-this.minimum);
var _36=_34;
if(_34>_35){
_34=_35;
_35=_36;
}
var _37=this._isReversed()?((1-_34)*100):(_34*100);
var _38=this._isReversed()?((1-_35)*100):(_35*100);
var _39=this._isReversed()?((1-_35)*100):(_34*100);
if(_32&&this.slideDuration>0&&this.progressBar.style[this._progressPixelSize]){
var _3a=_33?_35:_34;
var _3b=this;
var _3c={};
var _3d=parseFloat(this.progressBar.style[this._handleOffsetCoord]);
var _3e=this.slideDuration/10;
if(_3e===0){
return;
}
if(_3e<0){
_3e=0-_3e;
}
var _3f={};
var _40={};
var _41={};
_3f[this._handleOffsetCoord]={start:this.sliderHandle.style[this._handleOffsetCoord],end:_37,units:"%"};
_40[this._handleOffsetCoord]={start:this.sliderHandleMax.style[this._handleOffsetCoord],end:_38,units:"%"};
_41[this._handleOffsetCoord]={start:this.progressBar.style[this._handleOffsetCoord],end:_39,units:"%"};
_41[this._progressPixelSize]={start:this.progressBar.style[this._progressPixelSize],end:(_35-_34)*100,units:"%"};
var _42=dojo.animateProperty({node:this.sliderHandle,duration:_3e,properties:_3f});
var _43=dojo.animateProperty({node:this.sliderHandleMax,duration:_3e,properties:_40});
var _44=dojo.animateProperty({node:this.progressBar,duration:_3e,properties:_41});
var _45=dojo.fx.combine([_42,_43,_44]);
_45.play();
}else{
this.sliderHandle.style[this._handleOffsetCoord]=_37+"%";
this.sliderHandleMax.style[this._handleOffsetCoord]=_38+"%";
this.progressBar.style[this._handleOffsetCoord]=_39+"%";
this.progressBar.style[this._progressPixelSize]=((_35-_34)*100)+"%";
}
}});
dojo.declare("dijit.form._SliderMoverMax",dijit.form._SliderMover,{onMouseMove:function(e){
var _47=this.widget;
var _48=_47._abspos;
if(!_48){
_48=_47._abspos=dojo.coords(_47.sliderBarContainer,true);
_47._setPixelValue_=dojo.hitch(_47,"_setPixelValue");
_47._isReversed_=_47._isReversed();
}
var _49=e[_47._mousePixelCoord]-_48[_47._startingPixelCoord];
_47._setPixelValue_(_47._isReversed_?(_48[_47._pixelCount]-_49):_49,_48[_47._pixelCount],false,true);
},destroy:function(e){
dojo.dnd.Mover.prototype.destroy.apply(this,arguments);
var _4b=this.widget;
_4b._abspos=null;
_4b._setValueAttr(_4b.value,true);
}});
dojo.declare("dijit.form._SliderBarMover",dojo.dnd.Mover,{onMouseMove:function(e){
var _4d=this.widget;
if(_4d.disabled||_4d.readOnly){
return;
}
var _4e=_4d._abspos;
var bar=_4d._bar;
var _50=_4d._mouseOffset;
if(!_4e){
_4e=_4d._abspos=dojo.coords(_4d.sliderBarContainer,true);
_4d._setPixelValue_=dojo.hitch(_4d,"_setPixelValue");
_4d._getValueByPixelValue_=dojo.hitch(_4d,"_getValueByPixelValue");
_4d._isReversed_=_4d._isReversed();
}
if(!bar){
bar=_4d._bar=dojo.coords(_4d.progressBar,true);
}
if(!_50){
_50=_4d._mouseOffset=e[_4d._mousePixelCoord]-_4e[_4d._startingPixelCoord]-bar[_4d._startingPixelCount];
}
var _51=e[_4d._mousePixelCoord]-_4e[_4d._startingPixelCoord]-_50;
var _52=e[_4d._mousePixelCoord]-_4e[_4d._startingPixelCoord]-_50+bar[_4d._pixelCount];
var _53=[_51,_52];
_53.sort(function(a,b){
return a-b;
});
if(_53[0]<=0){
_53[0]=0;
_53[1]=bar[_4d._pixelCount];
}
if(_53[1]>=_4e[_4d._pixelCount]){
_53[1]=_4e[_4d._pixelCount];
_53[0]=_4e[_4d._pixelCount]-bar[_4d._pixelCount];
}
var _56=[_4d._getValueByPixelValue(_4d._isReversed_?(_4e[_4d._pixelCount]-_53[0]):_53[0],_4e[_4d._pixelCount]),_4d._getValueByPixelValue(_4d._isReversed_?(_4e[_4d._pixelCount]-_53[1]):_53[1],_4e[_4d._pixelCount])];
_4d._setValueAttr(_56,false,false);
},destroy:function(e){
dojo.dnd.Mover.prototype.destroy.apply(this,arguments);
var _58=this.widget;
_58._abspos=null;
_58._bar=null;
_58._mouseOffset=null;
_58._setValueAttr(_58.value,true);
}});
dojo.declare("dojox.form.HorizontalRangeSlider",[dijit.form.HorizontalSlider,dojox.form._RangeSliderMixin],{templateString:"<table class=\"dijit dijitReset dijitSlider dojoxRangeSlider\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" rules=\"none\"\n    ><tr class=\"dijitReset\"\n        ><td class=\"dijitReset\" colspan=\"2\"></td\n        ><td dojoAttachPoint=\"containerNode,topDecoration\" class=\"dijitReset\" style=\"text-align:center;width:100%;\"></td\n        ><td class=\"dijitReset\" colspan=\"2\"></td\n    ></tr\n    ><tr class=\"dijitReset\"\n        ><td class=\"dijitReset dijitSliderButtonContainer dijitSliderButtonContainerH\"\n            ><div class=\"dijitSliderDecrementIconH\" tabIndex=\"-1\" style=\"display:none\" dojoAttachPoint=\"decrementButton\" dojoAttachEvent=\"onclick: decrement\"><span class=\"dijitSliderButtonInner\">-</span></div\n        ></td\n        ><td class=\"dijitReset\"\n            ><div class=\"dijitSliderBar dijitSliderBumper dijitSliderBumperH dijitSliderLeftBumper dijitSliderLeftBumperH\" dojoAttachEvent=\"onclick:_onClkDecBumper\"></div\n        ></td\n        ><td class=\"dijitReset\"\n            ><input dojoAttachPoint=\"valueNode\" type=\"hidden\" name=\"${name}\"\n            /><div waiRole=\"presentation\" class=\"dojoxRangeSliderBarContainer\" dojoAttachPoint=\"sliderBarContainer\"\n                ><div dojoAttachPoint=\"sliderHandle\" tabIndex=\"${tabIndex}\" class=\"dijitSliderMoveable\" dojoAttachEvent=\"onkeypress:_onKeyPress,onmousedown:_onHandleClick\" waiRole=\"slider\" valuemin=\"${minimum}\" valuemax=\"${maximum}\"\n                    ><div class=\"dijitSliderImageHandle dijitSliderImageHandleH\"></div\n                ></div\n                ><div waiRole=\"presentation\" dojoAttachPoint=\"progressBar,focusNode\" class=\"dijitSliderBar dijitSliderBarH dijitSliderProgressBar dijitSliderProgressBarH\" dojoAttachEvent=\"onkeypress:_onKeyPress,onmousedown:_onBarClick\"></div\n                ><div dojoAttachPoint=\"sliderHandleMax,focusNodeMax\" tabIndex=\"${tabIndex}\" class=\"dijitSliderMoveable\" dojoAttachEvent=\"onkeypress:_onKeyPress,onmousedown:_onHandleClickMax\" waiRole=\"sliderMax\" valuemin=\"${minimum}\" valuemax=\"${maximum}\"\n                    ><div class=\"dijitSliderImageHandle dijitSliderImageHandleH\"></div\n                ></div\n                ><div waiRole=\"presentation\" dojoAttachPoint=\"remainingBar\" class=\"dijitSliderBar dijitSliderBarH dijitSliderRemainingBar dijitSliderRemainingBarH\" dojoAttachEvent=\"onmousedown:_onRemainingBarClick\"></div\n            ></div\n        ></td\n        ><td class=\"dijitReset\"\n            ><div class=\"dijitSliderBar dijitSliderBumper dijitSliderBumperH dijitSliderRightBumper dijitSliderRightBumperH\" dojoAttachEvent=\"onclick:_onClkIncBumper\"></div\n        ></td\n        ><td class=\"dijitReset dijitSliderButtonContainer dijitSliderButtonContainerH\"\n            ><div class=\"dijitSliderIncrementIconH\" tabIndex=\"-1\" style=\"display:none\" dojoAttachPoint=\"incrementButton\" dojoAttachEvent=\"onclick: increment\"><span class=\"dijitSliderButtonInner\">+</span></div\n        ></td\n    ></tr\n    ><tr class=\"dijitReset\"\n        ><td class=\"dijitReset\" colspan=\"2\"></td\n        ><td dojoAttachPoint=\"containerNode,bottomDecoration\" class=\"dijitReset\" style=\"text-align:center;\"></td\n        ><td class=\"dijitReset\" colspan=\"2\"></td\n    ></tr\n></table>\n"});
dojo.declare("dojox.form.VerticalRangeSlider",[dijit.form.VerticalSlider,dojox.form._RangeSliderMixin],{templateString:"<table class=\"dijitReset dijitSlider dojoxRangeSlider\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" rules=\"none\"\n><tbody class=\"dijitReset\"\n    ><tr class=\"dijitReset\"\n        ><td class=\"dijitReset\"></td\n        ><td class=\"dijitReset dijitSliderButtonContainer dijitSliderButtonContainerV\"\n            ><div class=\"dijitSliderIncrementIconV\" tabIndex=\"-1\" style=\"display:none\" dojoAttachPoint=\"incrementButton\" dojoAttachEvent=\"onclick: increment\"><span class=\"dijitSliderButtonInner\">+</span></div\n        ></td\n        ><td class=\"dijitReset\"></td\n    ></tr\n    ><tr class=\"dijitReset\"\n        ><td class=\"dijitReset\"></td\n        ><td class=\"dijitReset\"\n            ><center><div class=\"dijitSliderBar dijitSliderBumper dijitSliderBumperV dijitSliderTopBumper dijitSliderTopBumperV\" dojoAttachEvent=\"onclick:_onClkIncBumper\"></div></center\n        ></td\n        ><td class=\"dijitReset\"></td\n    ></tr\n    ><tr class=\"dijitReset\"\n        ><td dojoAttachPoint=\"leftDecoration\" class=\"dijitReset\" style=\"text-align:center;height:100%;\"></td\n        ><td class=\"dijitReset\" style=\"height:100%;\"\n            ><input dojoAttachPoint=\"valueNode\" type=\"hidden\" name=\"${name}\"\n            /><center waiRole=\"presentation\" style=\"position:relative;height:100%;\" dojoAttachPoint=\"sliderBarContainer\"\n                ><div waiRole=\"presentation\" dojoAttachPoint=\"remainingBar\" class=\"dijitSliderBar dijitSliderBarV dijitSliderRemainingBar dijitSliderRemainingBarV\" dojoAttachEvent=\"onmousedown:_onRemainingBarClick\"\n                    ><div dojoAttachPoint=\"sliderHandle\" tabIndex=\"${tabIndex}\" class=\"dijitSliderMoveable\" dojoAttachEvent=\"onkeypress:_onKeyPress,onmousedown:_onHandleClick\" style=\"vertical-align:top;\" waiRole=\"slider\" valuemin=\"${minimum}\" valuemax=\"${maximum}\"\n                        ><div class=\"dijitSliderImageHandle dijitSliderImageHandleV\"></div\n                    ></div\n                    ><div waiRole=\"presentation\" dojoAttachPoint=\"progressBar,focusNode\" tabIndex=\"${tabIndex}\" class=\"dijitSliderBar dijitSliderBarV dijitSliderProgressBar dijitSliderProgressBarV\" dojoAttachEvent=\"onkeypress:_onKeyPress,onmousedown:_onBarClick\"\n                    ></div\n                    ><div dojoAttachPoint=\"sliderHandleMax,focusNodeMax\" tabIndex=\"${tabIndex}\" class=\"dijitSliderMoveable\" dojoAttachEvent=\"onkeypress:_onKeyPress,onmousedown:_onHandleClickMax\" style=\"vertical-align:top;\" waiRole=\"slider\" valuemin=\"${minimum}\" valuemax=\"${maximum}\"\n                        ><div class=\"dijitSliderImageHandle dijitSliderImageHandleV\"></div\n                    ></div\n                ></div\n            ></center\n        ></td\n        ><td dojoAttachPoint=\"containerNode,rightDecoration\" class=\"dijitReset\" style=\"text-align:center;height:100%;\"></td\n    ></tr\n    ><tr class=\"dijitReset\"\n        ><td class=\"dijitReset\"></td\n        ><td class=\"dijitReset\"\n            ><center><div class=\"dijitSliderBar dijitSliderBumper dijitSliderBumperV dijitSliderBottomBumper dijitSliderBottomBumperV\" dojoAttachEvent=\"onclick:_onClkDecBumper\"></div></center\n        ></td\n        ><td class=\"dijitReset\"></td\n    ></tr\n    ><tr class=\"dijitReset\"\n        ><td class=\"dijitReset\"></td\n        ><td class=\"dijitReset dijitSliderButtonContainer dijitSliderButtonContainerV\"\n            ><div class=\"dijitSliderDecrementIconV\" tabIndex=\"-1\" style=\"display:none\" dojoAttachPoint=\"decrementButton\" dojoAttachEvent=\"onclick: decrement\"><span class=\"dijitSliderButtonInner\">-</span></div\n        ></td\n        ><td class=\"dijitReset\"></td\n    ></tr\n></tbody></table>\n"});
}
