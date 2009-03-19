/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.LinkDialog"]){
dojo._hasResource["dijit._editor.plugins.LinkDialog"]=true;
dojo.provide("dijit._editor.plugins.LinkDialog");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._editor._Plugin");
dojo.require("dijit.TooltipDialog");
dojo.require("dijit.form.Button");
dojo.require("dijit.form.ValidationTextBox");
dojo.require("dojo.i18n");
dojo.require("dojo.string");
dojo.requireLocalization("dijit._editor","LinkDialog",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit._editor.plugins.LinkDialog",dijit._editor._Plugin,{buttonClass:dijit.form.DropDownButton,useDefaultCommand:false,urlRegExp:"((https?|ftps?)\\://|)(((?:(?:[\\da-zA-Z](?:[-\\da-zA-Z]{0,61}[\\da-zA-Z])?)\\.)*(?:[a-zA-Z](?:[-\\da-zA-Z]{0,6}[\\da-zA-Z])?)\\.?)|(((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])|(0[xX]0*[\\da-fA-F]?[\\da-fA-F]\\.){3}0[xX]0*[\\da-fA-F]?[\\da-fA-F]|(0+[0-3][0-7][0-7]\\.){3}0+[0-3][0-7][0-7]|(0|[1-9]\\d{0,8}|[1-3]\\d{9}|4[01]\\d{8}|42[0-8]\\d{7}|429[0-3]\\d{6}|4294[0-8]\\d{5}|42949[0-5]\\d{4}|429496[0-6]\\d{3}|4294967[01]\\d{2}|42949672[0-8]\\d|429496729[0-5])|0[xX]0*[\\da-fA-F]{1,8}|([\\da-fA-F]{1,4}\\:){7}[\\da-fA-F]{1,4}|([\\da-fA-F]{1,4}\\:){6}((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])))(\\:\\d+)?(/(?:[^?#\\s/]+/)*(?:[^?#\\s/]+(?:\\?[^?#\\s/]*)?(?:#[A-Za-z][\\w.:-]*)?)?)?",linkDialogTemplate:["<table><tr><td>","<label for='${id}_urlInput'>${url}</label>","</td><td>","<input dojoType='dijit.form.ValidationTextBox' regExp='${urlRegExp}' required='true' id='${id}_urlInput' name='urlInput'>","</td></tr><tr><td>","<label for='${id}_textInput'>${text}</label>","</td><td>","<input dojoType='dijit.form.ValidationTextBox' required='true' id='${id}_textInput' name='textInput'>","</td></tr><tr><td colspan='2'>","<button dojoType='dijit.form.Button' type='submit'>${set}</button>","</td></tr></table>"].join(""),_initButton:function(){
var _1=this;
this.tag=this.command=="insertImage"?"img":"a";
var _2=dojo.i18n.getLocalization("dijit._editor","LinkDialog",this.lang);
var _3=(this.dropDown=new dijit.TooltipDialog({title:_2[this.command+"Title"],execute:dojo.hitch(this,"setValue"),onOpen:function(){
_1._onOpenDialog();
dijit.TooltipDialog.prototype.onOpen.apply(this,arguments);
},onCancel:function(){
setTimeout(dojo.hitch(_1,"_onCloseDialog"),0);
},onClose:dojo.hitch(this,"_onCloseDialog")}));
_2.urlRegExp=this.urlRegExp;
_2.id=dijit.getUniqueId(this.editor.id);
this._setContent(_3.title+"<div style='border-bottom: 1px black solid;padding-bottom:2pt;margin-bottom:4pt'></div>"+dojo.string.substitute(this.linkDialogTemplate,_2));
_3.startup();
this.inherited(arguments);
},_setContent:function(_4){
this.dropDown.attr("content",_4);
},setValue:function(_5){
this._onCloseDialog();
if(dojo.isIE){
var a=dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,[this.tag]);
if(a){
dojo.withGlobal(this.editor.window,"selectElement",dijit._editor.selection,[a]);
}
}
_5.tag=this.tag;
_5.refAttr=this.tag=="img"?"src":"href";
var _7="<${tag} ${refAttr}='${urlInput}' _djrealurl='${urlInput}'"+(_5.tag=="img"?" alt='${textInput}'>":">${textInput}")+"</${tag}>";
this.editor.execCommand("inserthtml",dojo.string.substitute(_7,_5));
},_onCloseDialog:function(){
this.editor.focus();
},_onOpenDialog:function(){
var a=dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,[this.tag]);
var _9,_a;
if(a){
_9=a.getAttribute("_djrealurl");
_a=this.tag=="img"?a.getAttribute("alt"):a.textContent||a.innerText;
dojo.withGlobal(this.editor.window,"selectElement",dijit._editor.selection,[a,true]);
}else{
_a=dojo.withGlobal(this.editor.window,dijit._editor.selection.getSelectedText);
}
this.dropDown.reset();
this.dropDown.setValues({urlInput:_9||"",textInput:_a||""});
}});
dojo.subscribe(dijit._scopeName+".Editor.getPlugin",null,function(o){
if(o.plugin){
return;
}
switch(o.args.name){
case "createLink":
case "insertImage":
o.plugin=new dijit._editor.plugins.LinkDialog({command:o.args.name});
}
});
}
