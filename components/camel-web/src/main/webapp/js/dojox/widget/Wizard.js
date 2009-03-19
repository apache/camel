/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.Wizard"]){
dojo._hasResource["dojox.widget.Wizard"]=true;
dojo.provide("dojox.widget.Wizard");
dojo.require("dijit.layout.StackContainer");
dojo.require("dijit.layout.ContentPane");
dojo.require("dijit.form.Button");
dojo.require("dojo.i18n");
dojo.requireLocalization("dijit","common",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.requireLocalization("dojox.widget","Wizard",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dojox.widget.Wizard",[dijit.layout.StackContainer,dijit._Templated],{widgetsInTemplate:true,templateString:"<div class=\"dojoxWizard\" dojoAttachPoint=\"wizardNode\">\n    <div class=\"dojoxWizardContainer\" dojoAttachPoint=\"containerNode\"></div>\n    <div class=\"dojoxWizardButtons\" dojoAttachPoint=\"wizardNav\">\n        <button dojoType=\"dijit.form.Button\" dojoAttachPoint=\"previousButton\">${previousButtonLabel}</button>\n        <button dojoType=\"dijit.form.Button\" dojoAttachPoint=\"nextButton\">${nextButtonLabel}</button>\n        <button dojoType=\"dijit.form.Button\" dojoAttachPoint=\"doneButton\" style=\"display:none\">${doneButtonLabel}</button>\n        <button dojoType=\"dijit.form.Button\" dojoAttachPoint=\"cancelButton\">${cancelButtonLabel}</button>\n    </div>\n</div>\n",nextButtonLabel:"",previousButtonLabel:"",cancelButtonLabel:"",doneButtonLabel:"",cancelFunction:null,hideDisabled:false,postMixInProperties:function(){
this.inherited(arguments);
var _1=dojo.mixin({cancel:dojo.i18n.getLocalization("dijit","common",this.lang).buttonCancel},dojo.i18n.getLocalization("dojox.widget","Wizard",this.lang));
var _2;
for(_2 in _1){
if(!this[_2+"ButtonLabel"]){
this[_2+"ButtonLabel"]=_1[_2];
}
}
},startup:function(){
if(this._started){
return;
}
this.inherited(arguments);
this.connect(this.nextButton,"onClick","_forward");
this.connect(this.previousButton,"onClick","back");
if(this.cancelFunction){
if(dojo.isString(this.cancelFunction)){
this.cancelFunction=dojo.getObject(this.cancelFunction);
}
this.connect(this.cancelButton,"onClick",this.cancelFunction);
}else{
this.cancelButton.domNode.style.display="none";
}
this.connect(this.doneButton,"onClick","done");
this._subscription=dojo.subscribe(this.id+"-selectChild",dojo.hitch(this,"_checkButtons"));
this._checkButtons();
this._started=true;
},_checkButtons:function(){
var sw=this.selectedChildWidget;
var _4=sw.isLastChild;
this.nextButton.attr("disabled",_4);
this._setButtonClass(this.nextButton);
if(sw.doneFunction){
this.doneButton.domNode.style.display="";
if(_4){
this.nextButton.domNode.style.display="none";
}
}else{
this.doneButton.domNode.style.display="none";
}
this.previousButton.attr("disabled",!this.selectedChildWidget.canGoBack);
this._setButtonClass(this.previousButton);
},_setButtonClass:function(_5){
_5.domNode.style.display=(this.hideDisabled&&_5.disabled)?"none":"";
},_forward:function(){
if(this.selectedChildWidget._checkPass()){
this.forward();
}
},done:function(){
this.selectedChildWidget.done();
},destroy:function(){
dojo.unsubscribe(this._subscription);
this.inherited(arguments);
}});
dojo.declare("dojox.widget.WizardPane",dijit.layout.ContentPane,{canGoBack:true,passFunction:null,doneFunction:null,startup:function(){
this.inherited(arguments);
if(this.isFirstChild){
this.canGoBack=false;
}
if(dojo.isString(this.passFunction)){
this.passFunction=dojo.getObject(this.passFunction);
}
if(dojo.isString(this.doneFunction)&&this.doneFunction){
this.doneFunction=dojo.getObject(this.doneFunction);
}
},_onShow:function(){
if(this.isFirstChild){
this.canGoBack=false;
}
this.inherited(arguments);
},_checkPass:function(){
var r=true;
if(this.passFunction&&dojo.isFunction(this.passFunction)){
var _7=this.passFunction();
switch(typeof _7){
case "boolean":
r=_7;
break;
case "string":
alert(_7);
r=false;
break;
}
}
return r;
},done:function(){
if(this.doneFunction&&dojo.isFunction(this.doneFunction)){
this.doneFunction();
}
}});
}
