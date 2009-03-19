/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.InlineEditBox"]){
dojo._hasResource["dijit.InlineEditBox"]=true;
dojo.provide("dijit.InlineEditBox");
dojo.require("dojo.i18n");
dojo.require("dijit._Widget");
dojo.require("dijit._Container");
dojo.require("dijit.form.Button");
dojo.require("dijit.form.TextBox");
dojo.requireLocalization("dijit","common",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.InlineEditBox",dijit._Widget,{editing:false,autoSave:true,buttonSave:"",buttonCancel:"",renderAsHtml:false,editor:"dijit.form.TextBox",editorParams:{},onChange:function(_1){
},onCancel:function(){
},width:"100%",value:"",noValueIndicator:"<span style='font-family: wingdings; text-decoration: underline;'>&nbsp;&nbsp;&nbsp;&nbsp;&#x270d;&nbsp;&nbsp;&nbsp;&nbsp;</span>",constructor:function(){
this.editorParams={};
},postMixInProperties:function(){
this.inherited(arguments);
this.displayNode=this.srcNodeRef;
var _2={ondijitclick:"_onClick",onmouseover:"_onMouseOver",onmouseout:"_onMouseOut",onfocus:"_onMouseOver",onblur:"_onMouseOut"};
for(var _3 in _2){
this.connect(this.displayNode,_3,_2[_3]);
}
dijit.setWaiRole(this.displayNode,"button");
if(!this.displayNode.getAttribute("tabIndex")){
this.displayNode.setAttribute("tabIndex",0);
}
this.attr("value",this.value||this.displayNode.innerHTML);
},setDisabled:function(_4){
dojo.deprecated("dijit.InlineEditBox.setDisabled() is deprecated.  Use attr('disabled', bool) instead.","","2.0");
this.attr("disabled",_4);
},_setDisabledAttr:function(_5){
this.disabled=_5;
dijit.setWaiState(this.domNode,"disabled",_5);
},_onMouseOver:function(){
dojo.addClass(this.displayNode,this.disabled?"dijitDisabledClickableRegion":"dijitClickableRegion");
},_onMouseOut:function(){
dojo.removeClass(this.displayNode,this.disabled?"dijitDisabledClickableRegion":"dijitClickableRegion");
},_onClick:function(e){
if(this.disabled){
return;
}
if(e){
dojo.stopEvent(e);
}
this._onMouseOut();
setTimeout(dojo.hitch(this,"edit"),0);
},edit:function(){
if(this.disabled||this.editing){
return;
}
this.editing=true;
var _7=(this.renderAsHtml?this.value:this.value.replace(/\s*\r?\n\s*/g,"").replace(/<br\/?>/gi,"\n").replace(/&gt;/g,">").replace(/&lt;/g,"<").replace(/&amp;/g,"&").replace(/&quot;/g,"\""));
var _8=dojo.create("span",null,this.domNode,"before");
var ew=this.editWidget=new dijit._InlineEditor({value:dojo.trim(_7),autoSave:this.autoSave,buttonSave:this.buttonSave,buttonCancel:this.buttonCancel,renderAsHtml:this.renderAsHtml,editor:this.editor,editorParams:this.editorParams,sourceStyle:dojo.getComputedStyle(this.displayNode),save:dojo.hitch(this,"save"),cancel:dojo.hitch(this,"cancel"),width:this.width},_8);
var _a=ew.domNode.style;
this.displayNode.style.display="none";
_a.position="static";
_a.visibility="visible";
this.domNode=ew.domNode;
setTimeout(function(){
ew.focus();
ew._resetValue=ew.getValue();
},100);
},_showText:function(_b){
this.displayNode.style.display="";
var ew=this.editWidget;
var _d=ew.domNode.style;
_d.position="absolute";
_d.visibility="hidden";
this.domNode=this.displayNode;
if(_b){
dijit.focus(this.displayNode);
}
_d.display="none";
setTimeout(function(){
ew.destroy();
delete ew;
if(dojo.isIE){
dijit.focus(dijit.getFocus());
}
},1000);
},save:function(_e){
if(this.disabled||!this.editing){
return;
}
this.editing=false;
var _f=this.editWidget.getValue()+"";
this.attr("value",this.renderAsHtml?_f:_f.replace(/&/gm,"&amp;").replace(/</gm,"&lt;").replace(/>/gm,"&gt;").replace(/"/gm,"&quot;").replace(/\n/g,"<br>"));
this.onChange(_f);
this._showText(_e);
},setValue:function(val){
dojo.deprecated("dijit.InlineEditBox.setValue() is deprecated.  Use attr('value', ...) instead.","","2.0");
return this.attr("value",val);
},_setValueAttr:function(val){
this.value=val;
this.displayNode.innerHTML=dojo.trim(val)||this.noValueIndicator;
},getValue:function(){
dojo.deprecated("dijit.InlineEditBox.getValue() is deprecated.  Use attr('value') instead.","","2.0");
return this.attr("value");
},cancel:function(_12){
this.editing=false;
this.onCancel();
this._showText(_12);
}});
dojo.declare("dijit._InlineEditor",[dijit._Widget,dijit._Templated],{templateString:"<span dojoAttachPoint=\"editNode\" waiRole=\"presentation\" style=\"position: absolute; visibility:hidden\" class=\"dijitReset dijitInline\"\n\tdojoAttachEvent=\"onkeypress: _onKeyPress\" \n\t><span dojoAttachPoint=\"editorPlaceholder\"></span\n\t><span dojoAttachPoint=\"buttonContainer\"\n\t\t><button class='saveButton' dojoAttachPoint=\"saveButton\" dojoType=\"dijit.form.Button\" dojoAttachEvent=\"onClick:save\" disabled=\"true\" label=\"${buttonSave}\"></button\n\t\t><button class='cancelButton' dojoAttachPoint=\"cancelButton\" dojoType=\"dijit.form.Button\" dojoAttachEvent=\"onClick:cancel\" label=\"${buttonCancel}\"></button\n\t></span\n></span>\n",widgetsInTemplate:true,postMixInProperties:function(){
this.inherited(arguments);
this.messages=dojo.i18n.getLocalization("dijit","common",this.lang);
dojo.forEach(["buttonSave","buttonCancel"],function(_13){
if(!this[_13]){
this[_13]=this.messages[_13];
}
},this);
},postCreate:function(){
var cls=dojo.getObject(this.editor);
var _15=this.sourceStyle;
var _16="line-height:"+_15.lineHeight+";";
dojo.forEach(["Weight","Family","Size","Style"],function(_17){
_16+="font-"+_17+":"+_15["font"+_17]+";";
},this);
dojo.forEach(["marginTop","marginBottom","marginLeft","marginRight"],function(_18){
this.domNode.style[_18]=_15[_18];
},this);
if(this.width=="100%"){
_16+="width:100%;";
this.domNode.style.display="block";
}else{
_16+="width:"+(this.width+(Number(this.width)==this.width?"px":""))+";";
}
this.editorParams.style=_16;
this.editorParams["displayedValue" in cls.prototype?"displayedValue":"value"]=this.value;
var ew=this.editWidget=new cls(this.editorParams,this.editorPlaceholder);
this.connect(ew,"onChange","_onChange");
this.connect(ew,"onKeyPress","_onKeyPress");
this.connect(ew,"onKeyUp","_onKeyPress");
if(this.autoSave){
this.buttonContainer.style.display="none";
}
},destroy:function(){
this.editWidget.destroy();
this.inherited(arguments);
},getValue:function(){
var ew=this.editWidget;
return ew.attr("displayedValue" in ew?"displayedValue":"value");
},_onKeyPress:function(e){
if(this._exitInProgress){
return;
}
if(this.autoSave){
if(e.altKey||e.ctrlKey){
return;
}
if(e.charOrCode==dojo.keys.ESCAPE){
dojo.stopEvent(e);
this._exitInProgress=true;
this.cancel(true);
}else{
if(e.charOrCode==dojo.keys.ENTER&&this.editWidget.focusNode.tagName=="INPUT"){
dojo.stopEvent(e);
this._exitInProgress=true;
this.save(true);
}else{
if(e.charOrCode===dojo.keys.TAB){
this._exitInProgress=true;
setTimeout(dojo.hitch(this,"save",false),0);
}
}
}
}else{
var _1c=this;
setTimeout(function(){
_1c._onChange();
},100);
}
},_onBlur:function(){
this.inherited(arguments);
if(this._exitInProgress){
return;
}
if(this.autoSave){
this._exitInProgress=true;
if(this.getValue()==this._resetValue){
this.cancel(false);
}else{
this.save(false);
}
}
},_onChange:function(){
if(this._exitInProgress){
return;
}
if(this.autoSave){
this._exitInProgress=true;
this.save(true);
}else{
this.saveButton.attr("disabled",(this.getValue()==this._resetValue)||!this.enableSave());
}
},enableSave:function(){
return this.editWidget.isValid?this.editWidget.isValid():true;
},focus:function(){
this.editWidget.focus();
dijit.selectInputText(this.editWidget.focusNode);
}});
}
