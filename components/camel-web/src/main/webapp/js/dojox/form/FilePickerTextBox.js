/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.FilePickerTextBox"]){
dojo._hasResource["dojox.form.FilePickerTextBox"]=true;
dojo.provide("dojox.form.FilePickerTextBox");
dojo.require("dojox.widget.FilePicker");
dojo.require("dijit.form.ValidationTextBox");
dojo.require("dojox.form._HasDropDown");
dojo.declare("dojox.form.FilePickerTextBox",[dijit.form.ValidationTextBox,dojox.form._HasDropDown],{baseClass:"dojoxFilePickerTextBox",templateString:"<div class=\"dijit dijitReset dijitInlineTable dijitLeft\"\n\tid=\"widget_${id}\"\n\tdojoAttachEvent=\"onmouseenter:_onMouse,onmouseleave:_onMouse,onmousedown:_onMouse\" waiRole=\"combobox\" tabIndex=\"-1\"\n\t><div style=\"overflow:hidden;\"\n\t\t><div class='dijitReset dijitRight dijitButtonNode dijitArrowButton dijitDownArrowButton'\n\t\t\tdojoAttachPoint=\"downArrowNode,dropDownNode,popupStateNode\" waiRole=\"presentation\"\n\t\t\t><div class=\"dijitArrowButtonInner\">&thinsp;</div\n\t\t\t><div class=\"dijitArrowButtonChar\">&#9660;</div\n\t\t></div\n\t\t><div class=\"dijitReset dijitValidationIcon\"><br></div\n\t\t><div class=\"dijitReset dijitValidationIconText\">&Chi;</div\n\t\t><div class=\"dijitReset dijitInputField\"\n\t\t\t><input type=\"text\" autocomplete=\"off\" ${nameAttrSetting} class='dijitReset'\n\t\t\t\tdojoAttachEvent='onkeypress:_onKey' \n\t\t\t\tdojoAttachPoint='textbox,focusNode' waiRole=\"textbox\" waiState=\"haspopup-true,autocomplete-list\"\n\t\t/></div\n\t></div\n></div>\n",searchDelay:500,_stopClickEvents:false,valueItem:null,numPanes:2.25,postMixInProperties:function(){
this.inherited(arguments);
this.dropDown=new dojox.widget.FilePicker(this.constraints);
},postCreate:function(){
this.inherited(arguments);
this.connect(this.dropDown,"onChange",this._onWidgetChange);
this.connect(this.focusNode,"onblur","_focusBlur");
this.connect(this.focusNode,"onfocus","_focusFocus");
this.connect(this.focusNode,"ondblclick",function(){
dijit.selectInputText(this.focusNode);
});
},_setValueAttr:function(_1,_2,_3){
if(!this._searchInProgress){
this.inherited(arguments);
_1=_1||"";
var _4=this.dropDown.attr("pathValue")||"";
if(_1!==_4){
this._skip=true;
var fx=dojo.hitch(this,"_setBlurValue");
this.dropDown._setPathValueAttr(_1,!_3,this._settingBlurValue?fx:null);
}
}
},_onWidgetChange:function(_6){
if(!_6&&this.focusNode.value){
this._hasValidPath=false;
this.focusNode.value="";
}else{
this.valueItem=_6;
var _7=this.dropDown._getPathValueAttr(_6);
if(_7){
this._hasValidPath=true;
}
if(!this._skip){
this._setValueAttr(_7,undefined,true);
}
delete this._skip;
}
this.validate();
},startup:function(){
if(!this.dropDown._started){
this.dropDown.startup();
}
this.inherited(arguments);
},openDropDown:function(){
this.dropDown.domNode.style.width="0px";
if(!("minPaneWidth" in (this.constraints||{}))){
this.dropDown.attr("minPaneWidth",(this.domNode.offsetWidth/this.numPanes));
}
this.inherited(arguments);
},toggleDropDown:function(){
this.inherited(arguments);
if(this._opened){
this.dropDown.attr("pathValue",this.attr("value"));
}
},_focusBlur:function(e){
if(e.explicitOriginalTarget==this.focusNode&&!this._allowBlur){
window.setTimeout(dojo.hitch(this,function(){
if(!this._allowBlur){
this.focus();
}
}),1);
}else{
if(this._menuFocus){
this.dropDown._updateClass(this._menuFocus,"Item",{"Hover":false});
delete this._menuFocus;
}
}
},_focusFocus:function(e){
if(this._menuFocus){
this.dropDown._updateClass(this._menuFocus,"Item",{"Hover":false});
}
delete this._menuFocus;
var _a=dijit.getFocus(this);
if(_a&&_a.node){
_a=dijit.byNode(_a.node);
if(_a){
this._menuFocus=_a.domNode;
}
}
if(this._menuFocus){
this.dropDown._updateClass(this._menuFocus,"Item",{"Hover":true});
}
delete this._allowBlur;
},_onBlur:function(){
this._allowBlur=true;
delete this.dropDown._savedFocus;
this.inherited(arguments);
},_setBlurValue:function(){
if(this.dropDown&&!this._settingBlurValue){
this._settingBlurValue=true;
this.attr("value",this.focusNode.value);
}else{
delete this._settingBlurValue;
this.inherited(arguments);
}
},parse:function(_b,_c){
if(this._hasValidPath||this._hasSelection){
return _b;
}
var dd=this.dropDown,_e=dd.topDir,_f=dd.pathSeparator;
var _10=dd.attr("pathValue");
var _11=function(v){
if(_e.length&&v.indexOf(_e)===0){
v=v.substring(_e.length);
}
if(_f&&v[v.length-1]==_f){
v=v.substring(0,v.length-1);
}
return v;
};
_10=_11(_10);
var val=_11(_b);
if(val==_10){
return _b;
}
return undefined;
},_startSearchFromInput:function(){
var dd=this.dropDown,fn=this.focusNode;
var val=fn.value,_17=val,_18=dd.topDir;
if(this._hasSelection){
dijit.selectInputText(fn,_17.length);
}
this._hasSelection=false;
if(_18.length&&val.indexOf(_18)===0){
val=val.substring(_18.length);
}
var _19=val.split(dd.pathSeparator);
var _1a=dojo.hitch(this,function(idx){
var dir=_19[idx];
var _1d=dd.getChildren()[idx];
var _1e;
this._searchInProgress=true;
var _1f=dojo.hitch(this,function(){
delete this._searchInProgress;
});
if((dir||_1d)&&!this._opened){
this.toggleDropDown();
}
if(dir&&_1d){
var fx=dojo.hitch(this,function(){
if(_1e){
this.disconnect(_1e);
}
delete _1e;
var _21=_1d._menu.getChildren();
var _22=dojo.filter(_21,function(i){
return i.label==dir;
})[0];
var _24=dojo.filter(_21,function(i){
return (i.label.indexOf(dir)===0);
})[0];
if(_22&&((_19.length>idx+1&&_22.children)||(!_22.children))){
idx++;
_1d._menu.onItemClick(_22,{type:"internal",stopPropagation:function(){
},preventDefault:function(){
}});
if(_19[idx]){
_1a(idx);
}else{
_1f();
}
}else{
_1d._setSelected(null);
if(_24&&_19.length===idx+1){
dd._setInProgress=true;
dd._removeAfter(_1d);
delete dd._setInProgress;
var _26=_24.label;
if(_24.children){
_26+=dd.pathSeparator;
}
_26=_26.substring(dir.length);
window.setTimeout(function(){
dijit.scrollIntoView(_24.domNode);
},1);
fn.value=_17+_26;
dijit.selectInputText(fn,_17.length);
this._hasSelection=true;
try{
_24.focusNode.focus();
}
catch(e){
}
}else{
if(this._menuFocus){
this.dropDown._updateClass(this._menuFocus,"Item",{"Hover":false,"Focus":false});
}
delete this._menuFocus;
}
_1f();
}
});
if(!_1d.isLoaded){
_1e=this.connect(_1d,"onLoad",fx);
}else{
fx();
}
}else{
if(_1d){
_1d._setSelected(null);
dd._setInProgress=true;
dd._removeAfter(_1d);
delete dd._setInProgress;
}
_1f();
}
});
_1a(0);
},_onKey:function(e){
if(this.disabled||this.readOnly){
return;
}
var dk=dojo.keys;
var c=e.charOrCode;
if(c==dk.DOWN_ARROW){
this._allowBlur=true;
}
if(c==dk.ENTER&&this._opened){
this.dropDown.onExecute();
dijit.selectInputText(this.focusNode,this.focusNode.value.length);
this._hasSelection=false;
dojo.stopEvent(e);
return;
}
if((c==dk.RIGHT_ARROW||c==dk.LEFT_ARROW||c==dk.TAB)&&this._hasSelection){
this._startSearchFromInput();
dojo.stopEvent(e);
return;
}
this.inherited(arguments);
var _2a=false;
if((c==dk.BACKSPACE||c==dk.DELETE)&&this._hasSelection){
this._hasSelection=false;
}else{
if(c==dk.BACKSPACE||c==dk.DELETE||c==" "){
_2a=true;
}else{
_2a=e.keyChar!=="";
}
}
if(this._searchTimer){
window.clearTimeout(this._searchTimer);
}
delete this._searchTimer;
if(_2a){
this._hasValidPath=false;
this._hasSelection=false;
this._searchTimer=window.setTimeout(dojo.hitch(this,"_startSearchFromInput"),this.searchDelay+1);
}
}});
}
