/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form._FormWidget"]){
dojo._hasResource["dijit.form._FormWidget"]=true;
dojo.provide("dijit.form._FormWidget");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dijit.form._FormWidget",[dijit._Widget,dijit._Templated],{baseClass:"",name:"",alt:"",value:"",type:"text",tabIndex:"0",disabled:false,readOnly:false,intermediateChanges:false,scrollOnFocus:true,attributeMap:dojo.delegate(dijit._Widget.prototype.attributeMap,{value:"focusNode",disabled:"focusNode",readOnly:"focusNode",id:"focusNode",tabIndex:"focusNode",alt:"focusNode"}),postMixInProperties:function(){
this.nameAttrSetting=this.name?("name='"+this.name+"'"):"";
this.inherited(arguments);
},_setDisabledAttr:function(_1){
this.disabled=_1;
dojo.attr(this.focusNode,"disabled",_1);
dijit.setWaiState(this.focusNode,"disabled",_1);
if(_1){
this._hovering=false;
this._active=false;
this.focusNode.removeAttribute("tabIndex");
}else{
this.focusNode.setAttribute("tabIndex",this.tabIndex);
}
this._setStateClass();
},setDisabled:function(_2){
dojo.deprecated("setDisabled("+_2+") is deprecated. Use attr('disabled',"+_2+") instead.","","2.0");
this.attr("disabled",_2);
},_onFocus:function(e){
if(this.scrollOnFocus){
dijit.scrollIntoView(this.domNode);
}
this.inherited(arguments);
},_onMouse:function(_4){
var _5=_4.currentTarget;
if(_5&&_5.getAttribute){
this.stateModifier=_5.getAttribute("stateModifier")||"";
}
if(!this.disabled){
switch(_4.type){
case "mouseenter":
case "mouseover":
this._hovering=true;
this._active=this._mouseDown;
break;
case "mouseout":
case "mouseleave":
this._hovering=false;
this._active=false;
break;
case "mousedown":
this._active=true;
this._mouseDown=true;
var _6=this.connect(dojo.body(),"onmouseup",function(){
if(this._mouseDown&&this.isFocusable()){
this.focus();
}
this._active=false;
this._mouseDown=false;
this._setStateClass();
this.disconnect(_6);
});
break;
}
this._setStateClass();
}
},isFocusable:function(){
return !this.disabled&&!this.readOnly&&this.focusNode&&(dojo.style(this.domNode,"display")!="none");
},focus:function(){
dijit.focus(this.focusNode);
},_setStateClass:function(){
var _7=this.baseClass.split(" ");
function _8(_9){
_7=_7.concat(dojo.map(_7,function(c){
return c+_9;
}),"dijit"+_9);
};
if(this.checked){
_8("Checked");
}
if(this.state){
_8(this.state);
}
if(this.selected){
_8("Selected");
}
if(this.disabled){
_8("Disabled");
}else{
if(this.readOnly){
_8("ReadOnly");
}else{
if(this._active){
_8(this.stateModifier+"Active");
}else{
if(this._focused){
_8("Focused");
}
if(this._hovering){
_8(this.stateModifier+"Hover");
}
}
}
}
var tn=this.stateNode||this.domNode,_c={};
dojo.forEach(tn.className.split(" "),function(c){
_c[c]=true;
});
if("_stateClasses" in this){
dojo.forEach(this._stateClasses,function(c){
delete _c[c];
});
}
dojo.forEach(_7,function(c){
_c[c]=true;
});
var _10=[];
for(var c in _c){
_10.push(c);
}
tn.className=_10.join(" ");
this._stateClasses=_7;
},compare:function(_12,_13){
if((typeof _12=="number")&&(typeof _13=="number")){
return (isNaN(_12)&&isNaN(_13))?0:(_12-_13);
}else{
if(_12>_13){
return 1;
}else{
if(_12<_13){
return -1;
}else{
return 0;
}
}
}
},onChange:function(_14){
},_onChangeActive:false,_handleOnChange:function(_15,_16){
this._lastValue=_15;
if(this._lastValueReported==undefined&&(_16===null||!this._onChangeActive)){
this._resetValue=this._lastValueReported=_15;
}
if((this.intermediateChanges||_16||_16===undefined)&&((typeof _15!=typeof this._lastValueReported)||this.compare(_15,this._lastValueReported)!=0)){
this._lastValueReported=_15;
if(this._onChangeActive){
this.onChange(_15);
}
}
},create:function(){
this.inherited(arguments);
this._onChangeActive=true;
this._setStateClass();
},destroy:function(){
if(this._layoutHackHandle){
clearTimeout(this._layoutHackHandle);
}
this.inherited(arguments);
},setValue:function(_17){
dojo.deprecated("dijit.form._FormWidget:setValue("+_17+") is deprecated.  Use attr('value',"+_17+") instead.","","2.0");
this.attr("value",_17);
},getValue:function(){
dojo.deprecated(this.declaredClass+"::getValue() is deprecated. Use attr('value') instead.","","2.0");
return this.attr("value");
},_layoutHack:function(){
if(dojo.isFF==2&&!this._layoutHackHandle){
var _18=this.domNode;
var old=_18.style.opacity;
_18.style.opacity="0.999";
this._layoutHackHandle=setTimeout(dojo.hitch(this,function(){
this._layoutHackHandle=null;
_18.style.opacity=old;
}),0);
}
}});
dojo.declare("dijit.form._FormValueWidget",dijit.form._FormWidget,{attributeMap:dojo.delegate(dijit.form._FormWidget.prototype.attributeMap,{value:""}),postCreate:function(){
if(dojo.isIE||dojo.isWebKit){
this.connect(this.focusNode||this.domNode,"onkeydown",this._onKeyDown);
}
if(this._resetValue===undefined){
this._resetValue=this.value;
}
},_setValueAttr:function(_1a,_1b){
this.value=_1a;
this._handleOnChange(_1a,_1b);
},_getValueAttr:function(_1c){
return this._lastValue;
},undo:function(){
this._setValueAttr(this._lastValueReported,false);
},reset:function(){
this._hasBeenBlurred=false;
this._setValueAttr(this._resetValue,true);
},_onKeyDown:function(e){
if(e.keyCode==dojo.keys.ESCAPE&&!e.ctrlKey&&!e.altKey){
var te;
if(dojo.isIE){
e.preventDefault();
te=document.createEventObject();
te.keyCode=dojo.keys.ESCAPE;
te.shiftKey=e.shiftKey;
e.srcElement.fireEvent("onkeypress",te);
}else{
if(dojo.isWebKit){
te=document.createEvent("Events");
te.initEvent("keypress",true,true);
te.keyCode=dojo.keys.ESCAPE;
te.shiftKey=e.shiftKey;
e.target.dispatchEvent(te);
}
}
}
}});
}
