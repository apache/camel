/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.ValidationTextBox"]){
dojo._hasResource["dijit.form.ValidationTextBox"]=true;
dojo.provide("dijit.form.ValidationTextBox");
dojo.require("dojo.i18n");
dojo.require("dijit.form.TextBox");
dojo.require("dijit.Tooltip");
dojo.requireLocalization("dijit.form","validate",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.form.ValidationTextBox",dijit.form.TextBox,{templateString:"<div class=\"dijit dijitReset dijitInlineTable dijitLeft\"\n\tid=\"widget_${id}\"\n\tdojoAttachEvent=\"onmouseenter:_onMouse,onmouseleave:_onMouse,onmousedown:_onMouse\" waiRole=\"presentation\"\n\t><div style=\"overflow:hidden;\"\n\t\t><div class=\"dijitReset dijitValidationIcon\"><br></div\n\t\t><div class=\"dijitReset dijitValidationIconText\">&Chi;</div\n\t\t><div class=\"dijitReset dijitInputField\"\n\t\t\t><input class=\"dijitReset\" dojoAttachPoint='textbox,focusNode' autocomplete=\"off\"\n\t\t\t${nameAttrSetting} type='${type}'\n\t\t/></div\n\t></div\n></div>\n",baseClass:"dijitTextBox",required:false,promptMessage:"",invalidMessage:"$_unset_$",constraints:{},regExp:".*",regExpGen:function(_1){
return this.regExp;
},state:"",tooltipPosition:[],_setValueAttr:function(){
this.inherited(arguments);
this.validate(this._focused);
},validator:function(_2,_3){
return (new RegExp("^(?:"+this.regExpGen(_3)+")"+(this.required?"":"?")+"$")).test(_2)&&(!this.required||!this._isEmpty(_2))&&(this._isEmpty(_2)||this.parse(_2,_3)!==undefined);
},_isValidSubset:function(){
return this.textbox.value.search(this._partialre)==0;
},isValid:function(_4){
return this.validator(this.textbox.value,this.constraints);
},_isEmpty:function(_5){
return /^\s*$/.test(_5);
},getErrorMessage:function(_6){
return this.invalidMessage;
},getPromptMessage:function(_7){
return this.promptMessage;
},_maskValidSubsetError:true,validate:function(_8){
var _9="";
var _a=this.disabled||this.isValid(_8);
if(_a){
this._maskValidSubsetError=true;
}
var _b=!_a&&_8&&this._isValidSubset();
var _c=this._isEmpty(this.textbox.value);
this.state=(_a||(!this._hasBeenBlurred&&_c)||_b)?"":"Error";
if(this.state=="Error"){
this._maskValidSubsetError=false;
}
this._setStateClass();
dijit.setWaiState(this.focusNode,"invalid",_a?"false":"true");
if(_8){
if(_c){
_9=this.getPromptMessage(true);
}
if(!_9&&(this.state=="Error"||(_b&&!this._maskValidSubsetError))){
_9=this.getErrorMessage(true);
}
}
this.displayMessage(_9);
return _a;
},_message:"",displayMessage:function(_d){
if(this._message==_d){
return;
}
this._message=_d;
dijit.hideTooltip(this.domNode);
if(_d){
dijit.showTooltip(_d,this.domNode,this.tooltipPosition);
}
},_refreshState:function(){
this.validate(this._focused);
this.inherited(arguments);
},constructor:function(){
this.constraints={};
},postMixInProperties:function(){
this.inherited(arguments);
this.constraints.locale=this.lang;
this.messages=dojo.i18n.getLocalization("dijit.form","validate",this.lang);
if(this.invalidMessage=="$_unset_$"){
this.invalidMessage=this.messages.invalidMessage;
}
var p=this.regExpGen(this.constraints);
this.regExp=p;
var _f="";
if(p!=".*"){
this.regExp.replace(/\\.|\[\]|\[.*?[^\\]{1}\]|\{.*?\}|\(\?[=:!]|./g,function(re){
switch(re.charAt(0)){
case "{":
case "+":
case "?":
case "*":
case "^":
case "$":
case "|":
case "(":
_f+=re;
break;
case ")":
_f+="|$)";
break;
default:
_f+="(?:"+re+"|$)";
break;
}
});
}
try{
"".search(_f);
}
catch(e){
_f=this.regExp;
console.warn("RegExp error in "+this.declaredClass+": "+this.regExp);
}
this._partialre="^(?:"+_f+")$";
},_setDisabledAttr:function(_11){
this.inherited(arguments);
if(this.valueNode){
this.valueNode.disabled=_11;
}
this._refreshState();
},_setRequiredAttr:function(_12){
this.required=_12;
dijit.setWaiState(this.focusNode,"required",_12);
this._refreshState();
},postCreate:function(){
if(dojo.isIE){
var s=dojo.getComputedStyle(this.focusNode);
if(s){
var ff=s.fontFamily;
if(ff){
this.focusNode.style.fontFamily=ff;
}
}
}
this.inherited(arguments);
},reset:function(){
this._maskValidSubsetError=true;
this.inherited(arguments);
}});
dojo.declare("dijit.form.MappedTextBox",dijit.form.ValidationTextBox,{postMixInProperties:function(){
this.inherited(arguments);
this.nameAttrSetting="";
},serialize:function(val,_16){
return val.toString?val.toString():"";
},toString:function(){
var val=this.filter(this.attr("value"));
return val!=null?(typeof val=="string"?val:this.serialize(val,this.constraints)):"";
},validate:function(){
this.valueNode.value=this.toString();
return this.inherited(arguments);
},buildRendering:function(){
this.inherited(arguments);
this.valueNode=dojo.create("input",{style:{display:"none"},type:this.type,name:this.name},this.textbox,"after");
},_setDisabledAttr:function(_18){
this.inherited(arguments);
dojo.attr(this.valueNode,"disabled",_18);
},reset:function(){
this.valueNode.value="";
this.inherited(arguments);
}});
dojo.declare("dijit.form.RangeBoundTextBox",dijit.form.MappedTextBox,{rangeMessage:"",rangeCheck:function(_19,_1a){
var _1b="min" in _1a;
var _1c="max" in _1a;
if(_1b||_1c){
return (!_1b||this.compare(_19,_1a.min)>=0)&&(!_1c||this.compare(_19,_1a.max)<=0);
}
return true;
},isInRange:function(_1d){
return this.rangeCheck(this.attr("value"),this.constraints);
},_isDefinitelyOutOfRange:function(){
var val=this.attr("value");
var _1f=false;
var _20=false;
if("min" in this.constraints){
var min=this.constraints.min;
val=this.compare(val,((typeof min=="number")&&min>=0&&val!=0)?0:min);
_1f=(typeof val=="number")&&val<0;
}
if("max" in this.constraints){
var max=this.constraints.max;
val=this.compare(val,((typeof max!="number")||max>0)?max:0);
_20=(typeof val=="number")&&val>0;
}
return _1f||_20;
},_isValidSubset:function(){
return this.inherited(arguments)&&!this._isDefinitelyOutOfRange();
},isValid:function(_23){
return this.inherited(arguments)&&((this._isEmpty(this.textbox.value)&&!this.required)||this.isInRange(_23));
},getErrorMessage:function(_24){
if(dijit.form.RangeBoundTextBox.superclass.isValid.call(this,false)&&!this.isInRange(_24)){
return this.rangeMessage;
}
return this.inherited(arguments);
},postMixInProperties:function(){
this.inherited(arguments);
if(!this.rangeMessage){
this.messages=dojo.i18n.getLocalization("dijit.form","validate",this.lang);
this.rangeMessage=this.messages.rangeMessage;
}
},postCreate:function(){
this.inherited(arguments);
if(this.constraints.min!==undefined){
dijit.setWaiState(this.focusNode,"valuemin",this.constraints.min);
}
if(this.constraints.max!==undefined){
dijit.setWaiState(this.focusNode,"valuemax",this.constraints.max);
}
},_setValueAttr:function(_25,_26){
dijit.setWaiState(this.focusNode,"valuenow",_25);
this.inherited(arguments);
}});
}
