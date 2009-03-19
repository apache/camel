/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.FontChoice"]){
dojo._hasResource["dijit._editor.plugins.FontChoice"]=true;
dojo.provide("dijit._editor.plugins.FontChoice");
dojo.require("dijit._editor._Plugin");
dojo.require("dijit.form.FilteringSelect");
dojo.require("dojo.data.ItemFileReadStore");
dojo.require("dojo.i18n");
dojo.requireLocalization("dijit._editor","FontChoice",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit._editor.plugins.FontChoice",dijit._editor._Plugin,{_uniqueId:0,buttonClass:dijit.form.FilteringSelect,useDefaultCommand:false,_initButton:function(){
var _1=this.command;
var _2=this.custom||{fontName:this.generic?["serif","sans-serif","monospace","cursive","fantasy"]:["Arial","Times New Roman","Comic Sans MS","Courier New"],fontSize:[1,2,3,4,5,6,7],formatBlock:["p","h1","h2","h3","pre"]}[_1];
this._availableValues=_2;
var _3=dojo.i18n.getLocalization("dijit._editor","FontChoice");
var _4=dojo.map(_2,function(_5){
var _6=_3[_5]||_5;
var _7=_6;
switch(_1){
case "fontName":
_7="<div style='font-family: "+_5+"'>"+_6+"</div>";
break;
case "fontSize":
_7="<font size="+_5+"'>"+_6+"</font>";
break;
case "formatBlock":
_7="<"+_5+">"+_6+"</"+_5+">";
}
return {label:_7,name:_6,value:_5};
});
this.inherited(arguments,[{required:false,labelType:"html",labelAttr:"label",searchAttr:"name",store:new dojo.data.ItemFileReadStore({data:{identifier:"value",items:_4}})}]);
this.button.attr("value","");
this.connect(this.button,"onChange",function(_8){
if(this.updating){
return;
}
if(dojo.isIE||!this._focusHandle){
this.editor.focus();
}else{
dijit.focus(this._focusHandle);
}
if(this.command=="fontName"&&_8.indexOf(" ")!=-1){
_8="'"+_8+"'";
}
this.editor.execCommand(this.editor._normalizeCommand(this.command),_8);
});
},updateState:function(){
this.inherited(arguments);
var _e=this.editor;
var _c=this.command;
if(!_e||!_e.isLoaded||!_c.length){
return;
}
if(this.button){
var _b;
try{
_b=_e.queryCommandValue(_c)||"";
}
catch(e){
_b="";
}
var _c=dojo.isString(_b)&&_b.match(/'([^']*)'/);
if(_c){
_b=_c[1];
}
if(this.generic&&_c=="fontName"){
var _d={"Arial":"sans-serif","Helvetica":"sans-serif","Myriad":"sans-serif","Times":"serif","Times New Roman":"serif","Comic Sans MS":"cursive","Apple Chancery":"cursive","Courier":"monospace","Courier New":"monospace","Papyrus":"fantasy"};
_b=_d[_b]||_b;
}else{
if(_c=="fontSize"&&_b.indexOf&&_b.indexOf("px")!=-1){
var _e=parseInt(_b);
_b={10:1,13:2,16:3,18:4,24:5,32:6,48:7}[_e]||_b;
}
}
this.updating=true;
this.button.attr("value",dojo.indexOf(this._availableValues,_b)<0?"":_b);
delete this.updating;
}
if(this.editor.iframe){
this._focusHandle=dijit.getFocus(this.editor.iframe);
}
},setToolbar:function(){
this.inherited(arguments);
var _f=this.button;
if(!_f.id){
_f.id=dijit._scopeName+"EditorButton-"+this.command+(this._uniqueId++);
}
var _10=dojo.doc.createElement("label");
dojo.addClass(_10,"dijit dijitReset dijitLeft dijitInline");
_10.setAttribute("for",_f.id);
var _11=dojo.i18n.getLocalization("dijit._editor","FontChoice");
_10.appendChild(dojo.doc.createTextNode(_11[this.command]));
dojo.place(_10,this.button.domNode,"before");
}});
dojo.subscribe(dijit._scopeName+".Editor.getPlugin",null,function(o){
if(o.plugin){
return;
}
switch(o.args.name){
case "fontName":
case "fontSize":
case "formatBlock":
o.plugin=new dijit._editor.plugins.FontChoice({command:o.args.name});
}
});
}
