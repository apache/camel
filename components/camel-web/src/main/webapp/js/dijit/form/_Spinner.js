/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form._Spinner"]){
dojo._hasResource["dijit.form._Spinner"]=true;
dojo.provide("dijit.form._Spinner");
dojo.require("dijit.form.ValidationTextBox");
dojo.declare("dijit.form._Spinner",dijit.form.RangeBoundTextBox,{defaultTimeout:500,timeoutChangeRate:0.9,smallDelta:1,largeDelta:10,templateString:"<div class=\"dijit dijitReset dijitInlineTable dijitLeft\"\n\tid=\"widget_${id}\"\n\tdojoAttachEvent=\"onmouseenter:_onMouse,onmouseleave:_onMouse,onmousedown:_onMouse\" waiRole=\"presentation\"\n\t><div class=\"dijitInputLayoutContainer\"\n\t\t><div class=\"dijitReset dijitSpinnerButtonContainer\"\n\t\t\t>&nbsp;<div class=\"dijitReset dijitLeft dijitButtonNode dijitArrowButton dijitUpArrowButton\"\n\t\t\t\tdojoAttachPoint=\"upArrowNode\"\n\t\t\t\tdojoAttachEvent=\"onmouseenter:_onMouse,onmouseleave:_onMouse\"\n\t\t\t\tstateModifier=\"UpArrow\"\n\t\t\t\t><div class=\"dijitArrowButtonInner\">&thinsp;</div\n\t\t\t\t><div class=\"dijitArrowButtonChar\">&#9650;</div\n\t\t\t></div\n\t\t\t><div class=\"dijitReset dijitLeft dijitButtonNode dijitArrowButton dijitDownArrowButton\"\n\t\t\t\tdojoAttachPoint=\"downArrowNode\"\n\t\t\t\tdojoAttachEvent=\"onmouseenter:_onMouse,onmouseleave:_onMouse\"\n\t\t\t\tstateModifier=\"DownArrow\"\n\t\t\t\t><div class=\"dijitArrowButtonInner\">&thinsp;</div\n\t\t\t\t><div class=\"dijitArrowButtonChar\">&#9660;</div\n\t\t\t></div\n\t\t></div\n\t\t><div class=\"dijitReset dijitValidationIcon\"><br></div\n\t\t><div class=\"dijitReset dijitValidationIconText\">&Chi;</div\n\t\t><div class=\"dijitReset dijitInputField\"\n\t\t\t><input class='dijitReset' dojoAttachPoint=\"textbox,focusNode\" type=\"${type}\" dojoAttachEvent=\"onkeypress:_onKeyPress\"\n\t\t\t\twaiRole=\"spinbutton\" autocomplete=\"off\" ${nameAttrSetting}\n\t\t/></div\n\t></div\n></div>\n",baseClass:"dijitSpinner",adjust:function(_1,_2){
return _1;
},_arrowState:function(_3,_4){
this._active=_4;
this.stateModifier=_3.getAttribute("stateModifier")||"";
this._setStateClass();
},_arrowPressed:function(_5,_6,_7){
if(this.disabled||this.readOnly){
return;
}
this._arrowState(_5,true);
this._setValueAttr(this.adjust(this.attr("value"),_6*_7),false);
dijit.selectInputText(this.textbox,this.textbox.value.length);
},_arrowReleased:function(_8){
this._wheelTimer=null;
if(this.disabled||this.readOnly){
return;
}
this._arrowState(_8,false);
},_typematicCallback:function(_9,_a,_b){
var _c=this.smallDelta;
if(_a==this.textbox){
var k=dojo.keys;
var _e=_b.charOrCode;
_c=(_e==k.PAGE_UP||_e==k.PAGE_DOWN)?this.largeDelta:this.smallDelta;
_a=(_e==k.UP_ARROW||_e==k.PAGE_UP)?this.upArrowNode:this.downArrowNode;
}
if(_9==-1){
this._arrowReleased(_a);
}else{
this._arrowPressed(_a,(_a==this.upArrowNode)?1:-1,_c);
}
},_wheelTimer:null,_mouseWheeled:function(_f){
dojo.stopEvent(_f);
var _10=_f.detail?(_f.detail*-1):(_f.wheelDelta/120);
if(_10!==0){
var _11=this[(_10>0?"upArrowNode":"downArrowNode")];
this._arrowPressed(_11,_10,this.smallDelta);
if(!this._wheelTimer){
clearTimeout(this._wheelTimer);
}
this._wheelTimer=setTimeout(dojo.hitch(this,"_arrowReleased",_11),50);
}
},postCreate:function(){
this.inherited(arguments);
this.connect(this.domNode,!dojo.isMozilla?"onmousewheel":"DOMMouseScroll","_mouseWheeled");
this._connects.push(dijit.typematic.addListener(this.upArrowNode,this.textbox,{charOrCode:dojo.keys.UP_ARROW,ctrlKey:false,altKey:false,shiftKey:false},this,"_typematicCallback",this.timeoutChangeRate,this.defaultTimeout));
this._connects.push(dijit.typematic.addListener(this.downArrowNode,this.textbox,{charOrCode:dojo.keys.DOWN_ARROW,ctrlKey:false,altKey:false,shiftKey:false},this,"_typematicCallback",this.timeoutChangeRate,this.defaultTimeout));
this._connects.push(dijit.typematic.addListener(this.upArrowNode,this.textbox,{charOrCode:dojo.keys.PAGE_UP,ctrlKey:false,altKey:false,shiftKey:false},this,"_typematicCallback",this.timeoutChangeRate,this.defaultTimeout));
this._connects.push(dijit.typematic.addListener(this.downArrowNode,this.textbox,{charOrCode:dojo.keys.PAGE_DOWN,ctrlKey:false,altKey:false,shiftKey:false},this,"_typematicCallback",this.timeoutChangeRate,this.defaultTimeout));
if(dojo.isIE){
var _12=this;
this.connect(this.domNode,"onresize",function(){
setTimeout(dojo.hitch(_12,function(){
var sz=this.upArrowNode.parentNode.offsetHeight;
if(sz){
this.upArrowNode.style.height=sz>>1;
this.downArrowNode.style.height=sz-(sz>>1);
this.focusNode.parentNode.style.height=sz;
}
this._setStateClass();
}),0);
});
}
}});
}
