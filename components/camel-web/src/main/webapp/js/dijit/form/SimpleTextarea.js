/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.SimpleTextarea"]){
dojo._hasResource["dijit.form.SimpleTextarea"]=true;
dojo.provide("dijit.form.SimpleTextarea");
dojo.require("dijit.form.TextBox");
dojo.declare("dijit.form.SimpleTextarea",dijit.form.TextBox,{baseClass:"dijitTextArea",attributeMap:dojo.delegate(dijit.form._FormValueWidget.prototype.attributeMap,{rows:"textbox",cols:"textbox"}),rows:"3",cols:"20",templatePath:null,templateString:"<textarea ${nameAttrSetting} dojoAttachPoint='focusNode,containerNode,textbox' autocomplete='off'></textarea>",postMixInProperties:function(){
if(!this.value&&this.srcNodeRef){
this.value=this.srcNodeRef.value;
}
this.inherited(arguments);
},filter:function(_1){
if(_1){
_1=_1.replace(/\r/g,"");
}
return this.inherited(arguments);
},postCreate:function(){
this.inherited(arguments);
if(dojo.isIE&&this.cols){
dojo.addClass(this.domNode,"dijitTextAreaCols");
}
},_previousValue:"",_onInput:function(e){
if(this.maxLength){
var _3=parseInt(this.maxLength);
var _4=this.textbox.value.replace(/\r/g,"");
var _5=_4.length-_3;
if(_5>0){
dojo.stopEvent(e);
var _6=this.textbox;
if(_6.selectionStart){
var _7=_6.selectionStart;
var cr=0;
if(dojo.isOpera){
cr=(this.textbox.value.substring(0,_7).match(/\r/g)||[]).length;
}
this.textbox.value=_4.substring(0,_7-_5-cr)+_4.substring(_7-cr);
_6.setSelectionRange(_7-_5,_7-_5);
}else{
if(dojo.doc.selection){
_6.focus();
var _9=dojo.doc.selection.createRange();
_9.moveStart("character",-_5);
_9.text="";
_9.select();
}
}
}
this._previousValue=this.textbox.value;
}
this.inherited(arguments);
}});
}
