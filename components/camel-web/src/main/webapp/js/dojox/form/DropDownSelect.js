/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.DropDownSelect"]){
dojo._hasResource["dojox.form.DropDownSelect"]=true;
dojo.provide("dojox.form.DropDownSelect");
dojo.require("dojox.form._FormSelectWidget");
dojo.require("dojox.form._HasDropDown");
dojo.require("dijit.Menu");
dojo.requireLocalization("dijit.form","validate",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dojox.form.DropDownSelect",[dojox.form._FormSelectWidget,dojox.form._HasDropDown],{attributeMap:dojo.mixin(dojo.clone(dojox.form._FormSelectWidget.prototype.attributeMap),{value:"valueNode",name:"valueNode"}),baseClass:"dojoxDropDownSelect",templateString:"<table class='dijit dijitReset dijitInline dijitLeft'\n\tdojoAttachPoint=\"dropDownNode,tableNode\" cellspacing='0' cellpadding='0' waiRole=\"presentation\"\n\tdojoAttachEvent=\"onmouseenter:_onMouse,onmouseleave:_onMouse,onmousedown:_onMouse\"\n\t><tbody waiRole=\"presentation\"><tr waiRole=\"presentation\"\n\t\t><td class=\"dijitReset dijitStretch dijitButtonContents dijitButtonNode\" \n\t\t\t><span class=\"dijitReset dijitInline dijitButtonText\"  dojoAttachPoint=\"containerNode,popupStateNode\" id=\"${id}_label\"></span\n\t\t\t><input type=\"hidden\" ${nameAttrSetting} dojoAttachPoint=\"valueNode\" value=\"${value}\" />\n\t\t</td><td class=\"dijitReset dijitRight dijitButtonNode dijitArrowButton dijitDownArrowButton\" \n\t\t\t\tdojoAttachPoint=\"focusNode,titleNode\" waiRole=\"button\" waiState=\"haspopup-true,labelledby-${id}_label\"\n\t\t\t><div class=\"dijitReset dijitArrowButtonInner\">&thinsp;</div\n\t\t\t><div class=\"dijitReset dijitArrowButtonChar\" waiRole=\"presentation\">&#9660;</div\n\t\t></td\n\t></tr></tbody\n></table>\n",attributeMap:dojo.mixin(dojo.clone(dojox.form._FormSelectWidget.prototype.attributeMap),{style:"tableNode"}),required:false,state:"",tooltipPosition:[],emptyLabel:"",_isLoaded:false,_childrenLoaded:false,_fillContent:function(){
this.inherited(arguments);
if(this.options.length&&!this.value){
var si=this.srcNodeRef.selectedIndex;
this.value=this.options[si!=-1?si:0].value;
}
this.dropDown=new dijit.Menu();
dojo.addClass(this.dropDown.domNode,this.baseClass+"Menu");
},_getMenuItemForOption:function(_2){
if(!_2.value){
return new dijit.MenuSeparator();
}else{
var _3=dojo.hitch(this,"_setValueAttr",_2);
return new dijit.MenuItem({option:_2,label:_2.label,onClick:_3,disabled:_2.disabled||false});
}
},_addOptionItem:function(_4){
this.dropDown.addChild(this._getMenuItemForOption(_4));
},_getChildren:function(){
return this.dropDown.getChildren();
},_loadChildren:function(){
this.inherited(arguments);
var _5=this.options.length;
this._isLoaded=false;
this._childrenLoaded=true;
if(!this._iReadOnly){
this.attr("readOnly",(_5===1));
delete this._iReadOnly;
}
if(!this._iDisabled){
this.attr("disabled",(_5===0));
delete this._iDisabled;
}
this._setValueAttr(this.value);
},_setValueAttr:function(_6){
this.inherited(arguments);
dojo.attr(this.valueNode,"value",this.attr("value"));
},_setDisplay:function(_7){
this.containerNode.innerHTML="<span class=\" "+this.baseClass+"Label\">"+(_7||this.emptyLabel||"&nbsp;")+"</span>";
this._layoutHack();
},validate:function(_8){
var _9=this.isValid(_8);
this.state=_9?"":"Error";
this._setStateClass();
dijit.setWaiState(this.focusNode,"invalid",_9?"false":"true");
var _a=_9?"":this._missingMsg;
if(this._message!==_a){
this._message=_a;
dijit.hideTooltip(this.domNode);
if(_a){
dijit.showTooltip(_a,this.domNode,this.tooltipPosition);
}
}
return _9;
},isValid:function(_b){
return (!this.required||!(/^\s*$/.test(this.value)));
},reset:function(){
this.inherited(arguments);
dijit.hideTooltip(this.domNode);
this.state="";
this._setStateClass();
delete this._message;
},postMixInProperties:function(){
this.inherited(arguments);
this._missingMsg=dojo.i18n.getLocalization("dijit.form","validate",this.lang).missingMessage;
},postCreate:function(){
this.inherited(arguments);
if(dojo.attr(this.srcNodeRef,"disabled")){
this.attr("disabled",true);
}
if(this.tableNode.style.width){
dojo.addClass(this.domNode,this.baseClass+"FixedWidth");
}
},startup:function(){
if(this._started){
return;
}
if(!this.dropDown){
var _c=dojo.query("[widgetId]",this.dropDownContainer)[0];
this.dropDown=dijit.byNode(_c);
delete this.dropDownContainer;
}
this.inherited(arguments);
},_onMenuMouseup:function(e){
var _e=this.dropDown,t=e.target;
if(_e.onItemClick){
var _10;
while(t&&!(_10=dijit.byNode(t))){
t=t.parentNode;
}
if(_10&&_10.onClick&&_10.getParent){
_10.getParent().onItemClick(_10,e);
}
}
},isLoaded:function(){
return this._isLoaded;
},loadDropDown:function(_11){
this._loadChildren();
this._isLoaded=true;
_11();
},_setReadOnlyAttr:function(_12){
this._iReadOnly=_12;
if(!_12&&this._childrenLoaded&&this.options.length===1){
return;
}
this.readOnly=_12;
},_setDisabledAttr:function(_13){
this._iDisabled=_13;
if(!_13&&this._childrenLoaded&&this.options.length===0){
return;
}
this.inherited(arguments);
},uninitialize:function(_14){
if(this.dropDown){
this.dropDown.destroyRecursive(_14);
delete this.dropDown;
}
this.inherited(arguments);
}});
}
