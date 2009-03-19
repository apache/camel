/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.editor.refactor.RichText"]){
dojo._hasResource["dojox.editor.refactor.RichText"]=true;
dojo.provide("dojox.editor.refactor.RichText");
dojo.require("dojo.AdapterRegistry");
dojo.require("dijit._Widget");
dojo.require("dijit._editor.selection");
dojo.require("dijit._editor.range");
dojo.require("dijit._editor.html");
dojo.require("dojo.i18n");
dojo.requireLocalization("dijit.form","Textarea",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
if(!dojo.config["useXDomain"]||dojo.config["allowXdRichTextSave"]){
if(dojo._postLoad){
(function(){
var _1=dojo.doc.createElement("textarea");
_1.id=dijit._scopeName+"._editor.RichText.savedContent";
dojo.style(_1,{display:"none",position:"absolute",top:"-100px",left:"-100px",height:"3px",width:"3px"});
dojo.body().appendChild(_1);
})();
}else{
try{
dojo.doc.write("<textarea id=\""+dijit._scopeName+"._editor.RichText.savedContent\" "+"style=\"display:none;position:absolute;top:-100px;left:-100px;height:3px;width:3px;overflow:hidden;\"></textarea>");
}
catch(e){
}
}
}
dojox.editor.refactor.RichTextIframeMixin={_writeOpen:function(_2){
if(dojo.isIE||dojo.isSafari||dojo.isOpera){
if(dojo.config["useXDomain"]&&!dojo.config["dojoBlankHtmlUrl"]){
console.warn("dojox.editor.newRT.RichText: When using cross-domain Dojo builds,"+" please save dojo/resources/blank.html to your domain and set djConfig.dojoBlankHtmlUrl"+" to the path on your domain to blank.html");
}
var _3=dojo.config["dojoBlankHtmlUrl"]||(dojo.moduleUrl("dojo","resources/blank.html")+"");
var _4=this.editorObject=this.iframe=dojo.doc.createElement("iframe");
_4.id=this.id+"_iframe";
_4.src=_3;
_4.style.border="none";
_4.style.width="100%";
_4.frameBorder=0;
this.editingArea.appendChild(_4);
var h=null;
var _6=dojo.hitch(this,function(){
if(h){
dojo.disconnect(h);
h=null;
}
this.window=_4.contentWindow;
var d=this.document=this.window.document;
d.open();
d.write(this._getIframeDocTxt(_2));
d.close();
if(dojo.isIE>=7){
if(this.height){
_4.style.height=this.height;
}
if(this.minHeight){
_4.style.minHeight=this.minHeight;
}
}else{
_4.style.height=this.height?this.height:this.minHeight;
}
if(dojo.isIE){
this._localizeEditorCommands();
}
this.onLoad();
this.savedContent=this.getValue(true);
});
if(dojo.isIE<7){
var t=setInterval(function(){
if(_4.contentWindow.isLoaded){
clearInterval(t);
_6();
}
},100);
}else{
h=dojo.connect(((dojo.isIE)?_4.contentWindow:_4),"onload",_6);
}
}else{
this._drawIframe(_2);
this.savedContent=this.getValue(true);
}
},_getIframeDocTxt:function(_9){
var _a=dojo.getComputedStyle(this.domNode);
if(!this.height&&!dojo.isMoz){
_9="<div>"+_9+"</div>";
}
var _b=[_a.fontWeight,_a.fontSize,_a.fontFamily].join(" ");
var _c=_a.lineHeight;
if(_c.indexOf("px")>=0){
_c=parseFloat(_c)/parseFloat(_a.fontSize);
}else{
if(_c.indexOf("em")>=0){
_c=parseFloat(_c);
}else{
_c="1.0";
}
}
return [this.isLeftToRight()?"<html><head>":"<html dir='rtl'><head>",(dojo.isMoz?"<title>"+this._localizedIframeTitles.iframeEditTitle+"</title>":""),"<style>","body,html {","\tbackground:transparent;","\tpadding: 0;","\tmargin: 0;","}","body{","\ttop:0px; left:0px; right:0px;",((this.height||dojo.isOpera)?"":"position: fixed;"),"\tfont:",_b,";","\tmin-height:",this.minHeight,";","\tline-height:",_c,"}","p{ margin: 1em 0 !important; }",(this.height?"":"body,html{overflow-y:hidden;/*for IE*/} body > div {overflow-x:auto;/*for FF to show vertical scrollbar*/}"),"li > ul:-moz-first-node, li > ol:-moz-first-node{ padding-top: 1.2em; } ","li{ min-height:1.2em; }","</style>",this._applyEditingAreaStyleSheets(),"</head><body>"+_9+"</body></html>"].join("");
},_drawIframe:function(_d){
if(!this.iframe){
var _e=this.iframe=dojo.doc.createElement("iframe");
_e.id=this.id+"_iframe";
var _f=_e.style;
_f.border="none";
_f.lineHeight="0";
_f.verticalAlign="bottom";
this.editorObject=this.iframe;
this._localizedIframeTitles=dojo.i18n.getLocalization("dijit.form","Textarea");
var _10=dojo.query("label[for=\""+this.id+"\"]");
if(_10.length){
this._localizedIframeTitles.iframeEditTitle=_10[0].innerHTML+" "+this._localizedIframeTitles.iframeEditTitle;
}
}
this.iframe.style.width=this.inheritWidth?this._oldWidth:"100%";
if(this.height){
this.iframe.style.height=this.height;
}else{
this.iframe.height=this._oldHeight;
}
var _11;
if(this.textarea){
_11=this.srcNodeRef;
}else{
_11=dojo.doc.createElement("div");
_11.style.display="none";
_11.innerHTML=_d;
this.editingArea.appendChild(_11);
}
this.editingArea.appendChild(this.iframe);
var _12=dojo.hitch(this,function(){
if(!this.editNode){
if(!this.document){
try{
if(this.iframe.contentWindow){
this.window=this.iframe.contentWindow;
this.document=this.iframe.contentWindow.document;
}else{
if(this.iframe.contentDocument){
this.window=this.iframe.contentDocument.window;
this.document=this.iframe.contentDocument;
}
}
}
catch(e){
setTimeout(_12,50);
return;
}
if(!this.document){
setTimeout(_12,50);
return;
}
var _13=this.document;
_13.open();
_13.write(this._getIframeDocTxt(_d));
_13.close();
dojo.destroy(_11);
}
if(!this.document.body){
setTimeout(_12,50);
return;
}
this.onLoad();
}else{
dojo.destroy(_11);
this.editNode.innerHTML=_d;
this.onDisplayChanged();
}
this._preDomFilterContent(this.editNode);
});
_12();
},onLoad:function(e){
this.focusNode=this.editNode=(this.height||dojo.isMoz)?this.document.body:this.document.body.firstChild;
dojox.editor.refactor.RichText.prototype.onLoad.call(this,e);
},_applyEditingAreaStyleSheets:function(){
var _15=[];
if(this.styleSheets){
_15=this.styleSheets.split(";");
this.styleSheets="";
}
_15=_15.concat(this.editingAreaStyleSheets);
this.editingAreaStyleSheets=[];
var _16="",i=0,url;
while((url=_15[i++])){
var _19=(new dojo._Url(dojo.global.location,url)).toString();
this.editingAreaStyleSheets.push(_19);
_16+="<link rel=\"stylesheet\" type=\"text/css\" href=\""+_19+"\">";
}
return _16;
},addStyleSheet:function(uri){
var url=uri.toString();
if(url.charAt(0)=="."||(url.charAt(0)!="/"&&!uri.host)){
url=(new dojo._Url(dojo.global.location,url)).toString();
}
if(dojo.indexOf(this.editingAreaStyleSheets,url)>-1){
return;
}
this.editingAreaStyleSheets.push(url);
if(this.document.createStyleSheet){
this.document.createStyleSheet(url);
}else{
var _1c=this.document.getElementsByTagName("head")[0];
var _1d=this.document.createElement("link");
with(_1d){
rel="stylesheet";
type="text/css";
href=url;
}
_1c.appendChild(_1d);
}
},removeStyleSheet:function(uri){
var url=uri.toString();
if(url.charAt(0)=="."||(url.charAt(0)!="/"&&!uri.host)){
url=(new dojo._Url(dojo.global.location,url)).toString();
}
var _20=dojo.indexOf(this.editingAreaStyleSheets,url);
if(_20==-1){
return;
}
delete this.editingAreaStyleSheets[_20];
dojo.withGlobal(this.window,"query",dojo,["link:[href=\""+url+"\"]"]).orphan();
},_setDisabledAttr:function(_21){
_21=Boolean(_21);
if(dojo.isMoz&&this.iframe){
this.document.designMode=_21?"off":"on";
}
dojox.editor.refactor.RichText.prototype._setDisabledAttr.call(this,_21);
},blur:function(){
this.window.blur();
}};
dojo.declare("dojox.editor.refactor.RichText",dijit._Widget,{constructor:function(_22){
this.contentPreFilters=[];
this.contentPostFilters=[];
this.contentDomPreFilters=[];
this.contentDomPostFilters=[];
this.editingAreaStyleSheets=[];
this._keyHandlers={};
this.contentPreFilters.push(dojo.hitch(this,"_preFixUrlAttributes"));
if(dojo.isMoz){
this.contentPreFilters.push(this._fixContentForMoz);
this.contentPostFilters.push(this._removeMozBogus);
}
if(dojo.isSafari){
this.contentPostFilters.push(this._removeSafariBogus);
}
this.onLoadDeferred=new dojo.Deferred();
this.useIframe=(dojo.isFF&&(dojo.isFF<3))||_22["useIframe"]||_22["styleSheets"];
if(this.useIframe){
dojo.mixin(this,dojox.editor.refactor.RichTextIframeMixin);
}
this.onLoadDeferred.addCallback(this,function(_23){
this.connect(this.editNode,"onblur","_customOnBlur");
this.connect(this.editNode,"onfocus","_customOnFocus");
return _23;
});
},inheritWidth:false,focusOnLoad:false,name:"",styleSheets:"",useIframe:false,_content:"",height:"300px",minHeight:"1em",isClosed:true,isLoaded:false,_SEPARATOR:"@@**%%__RICHTEXTBOUNDRY__%%**@@",onLoadDeferred:null,isTabIndent:false,postCreate:function(){
if("textarea"==this.domNode.tagName.toLowerCase()){
console.warn("RichText should not be used with the TEXTAREA tag.  See dojox.editor.refactor.RichText docs.");
}
dojo.publish(dijit._scopeName+"._editor.RichText::init",[this]);
this.open();
this.setupDefaultShortcuts();
},setupDefaultShortcuts:function(){
var _24=dojo.hitch(this,function(cmd,arg){
return function(){
return !this.execCommand(cmd,arg);
};
});
var _27={b:_24("bold"),i:_24("italic"),u:_24("underline"),a:_24("selectall"),s:function(){
this.save(true);
},m:function(){
this.isTabIndent=!this.isTabIndent;
},"1":_24("formatblock","h1"),"2":_24("formatblock","h2"),"3":_24("formatblock","h3"),"4":_24("formatblock","h4"),"\\":_24("insertunorderedlist")};
if(!dojo.isIE){
_27.Z=_24("redo");
}
for(var key in _27){
this.addKeyHandler(key,true,false,_27[key]);
}
},events:["onKeyPress","onKeyDown","onKeyUp","onClick"],captureEvents:[],_editorCommandsLocalized:false,_localizeEditorCommands:function(){
if(this._editorCommandsLocalized){
return;
}
this._editorCommandsLocalized=true;
var _29=["div","p","pre","h1","h2","h3","h4","h5","h6","ol","ul","address"];
var _2a="",_2b,i=0;
while((_2b=_29[i++])){
if(_2b.charAt(1)!="l"){
_2a+="<"+_2b+"><span>content</span></"+_2b+"><br/>";
}else{
_2a+="<"+_2b+"><li>content</li></"+_2b+"><br/>";
}
}
var div=dojo.doc.createElement("div");
dojo.style(div,{position:"absolute",left:"-2000px",top:"-2000px"});
dojo.doc.body.appendChild(div);
div.innerHTML=_2a;
var _2e=div.firstChild;
try{
while(_2e){
dijit._editor.selection.selectElement(_2e.firstChild);
this.s_call("selectElement",[_2e.firstChild]);
var _2f=_2e.tagName.toLowerCase();
this._local2NativeFormatNames[_2f]=document.queryCommandValue("formatblock");
this._native2LocalFormatNames[this._local2NativeFormatNames[_2f]]=_2f;
_2e=_2e.nextSibling.nextSibling;
}
}
catch(e){

}
dojo.body().removeChild(div);
},open:function(_30){
if((!this.onLoadDeferred)||(this.onLoadDeferred.fired>=0)){
this.onLoadDeferred=new dojo.Deferred();
}
if(!this.isClosed){
this.close();
}
dojo.publish(dijit._scopeName+"._editor.RichText::open",[this]);
this._content="";
if((arguments.length==1)&&(_30["nodeName"])){
this.domNode=_30;
}
var dn=this.domNode;
var _32;
if((dn["nodeName"])&&(dn.nodeName.toLowerCase()=="textarea")){
var ta=this.textarea=dn;
this.name=ta.name;
_32=this._preFilterContent(ta.value);
dn=this.domNode=dojo.doc.createElement("div");
dn.setAttribute("widgetId",this.id);
ta.removeAttribute("widgetId");
dn.cssText=ta.cssText;
dn.className+=" "+ta.className;
dojo.place(dn,ta,"before");
ta.onfocus=function(e){

dojo.stopEvent(e);
return false;
};
var _35=dojo.hitch(this,function(){
with(ta.style){
display="block";
position="absolute";
left=top="-1000px";
if(dojo.isIE){
this.__overflow=overflow;
overflow="hidden";
}
}
});
if(dojo.isIE){
setTimeout(_35,10);
}else{
_35();
}
if(ta.form){
dojo.connect(ta.form,"onsubmit",this,function(){
ta.value=this.getValue();
});
}
}else{
_32=this._preFilterContent(dijit._editor.getChildrenHtml(dn));
dn.innerHTML="";
}
if(_32==""){
_32="&nbsp;";
}
var _36=dojo.contentBox(dn);
this._oldHeight=_36.h;
this._oldWidth=_36.w;
this.savedContent=_32;
if((dn["nodeName"])&&(dn.nodeName=="LI")){
dn.innerHTML=" <br>";
}
this.editingArea=dn.ownerDocument.createElement("div");
dn.appendChild(this.editingArea);
if(this.name!=""&&(!dojo.config["useXDomain"]||dojo.config["allowXdRichTextSave"])){
var _37=dojo.byId(dijit._scopeName+"._editor.RichText.savedContent");
if(_37.value!=""){
var _38=_37.value.split(this._SEPARATOR),i=0,dat;
while((dat=_38[i++])){
var _3b=dat.split(":");
if(_3b[0]==this.name){
_32=_3b[1];
_38.splice(i,1);
break;
}
}
}
this.connect(window,"onbeforeunload","_saveContent");
}
this.isClosed=false;
this._writeOpen(_32);
if(dn.nodeName=="LI"){
dn.lastChild.style.marginTop="-1.2em";
}
dojo.addClass(dn,"RichTextEditable");
return this.onLoadDeferred;
},_writeOpen:function(_3c){
var en=this.focusNode=this.editNode=this.editingArea;
en.id=this.id;
en.className="dijitEditorArea";
en.innerHTML=_3c;
en.contentEditable=true;
if(this.height){
en.style.height=this.height;
}
if(this.height){
en.style.overflowY="auto";
}
this.window=dojo.global;
this.document=dojo.doc;
if(dojo.isIE){
this._localizeEditorCommands();
}
this.onLoad();
},_local2NativeFormatNames:{},_native2LocalFormatNames:{},_localizedIframeTitles:null,disabled:true,_mozSettingProps:{"styleWithCSS":false},_setDisabledAttr:function(_3e){
_3e=Boolean(_3e);
if(!this.editNode){
return;
}
this.editNode.contentEditable=!_3e;
this.disabled=_3e;
if(!_3e&&this._mozSettingProps){
var ps=this._mozSettingProps;
for(var n in ps){
if(ps.hasOwnProperty(n)){
try{
this.document.execCommand(n,false,ps[n]);
}
catch(e){
}
}
}
}
},setDisabled:function(_41){
dojo.deprecated("dijit.Editor::setDisabled is deprecated","use dijit.Editor::attr(\"disabled\",boolean) instead",2);
this.attr("disabled",_41);
},_isResized:function(){
return false;
},onLoad:function(e){
this.isLoaded=true;
if(!this.window.__registeredWindow){
this.window.__registeredWindow=true;
dijit.registerWin(this.window);
}
try{
this.attr("disabled",true);
this.attr("disabled",false);
}
catch(e){
var _43=dojo.connect(this,"onClick",this,function(){
this.attr("disabled",false);
dojo.disconnect(_43);
});
}
this._preDomFilterContent(this.editNode);
var _44=this.events.concat(this.captureEvents);
var ap=(this.iframe)?this.document:this.editNode;
dojo.forEach(_44,function(_46){
this.connect(ap,_46.toLowerCase(),_46);
},this);
if(dojo.isIE){
this.editNode.style.zoom=1;
}
if(this.focusOnLoad){
setTimeout(dojo.hitch(this,"focus"),0);
}
this.onDisplayChanged(e);
if(this.onLoadDeferred){
this.onLoadDeferred.callback(true);
}
},onKeyDown:function(e){
if(dojo.isIE){
if(e.keyCode===dojo.keys.BACKSPACE&&this.document.selection.type==="Control"){
dojo.stopEvent(e);
this.execCommand("delete");
}
}
return true;
},_lastPressStopped:false,onKeyUp:function(e){
if(this._lastPressStopped){
this._lastPressStopped=false;
dojo.stopEvent(e);
return false;
}
return true;
},onKeyPress:function(e){
var c=e.keyChar.toLowerCase()||e.keyCode;
var _4b=this._keyHandlers[c];
var _4c=arguments;
if(_4b){
dojo.forEach(_4b,function(h){
if((!!h.shift==!!e.shiftKey)&&(!!h.ctrl==!!e.ctrlKey)){
if(!h.handler.apply(this,_4c)){
dojo.stopEvent(e);
this._lastPressStopped=true;
}
}
},this);
}
if(!this._onKeyHitch){
this._onKeyHitch=dojo.hitch(this,"onKeyPressed");
}
setTimeout(this._onKeyHitch,1);
return true;
},addKeyHandler:function(key,_4f,_50,_51){
if(!dojo.isArray(this._keyHandlers[key])){
this._keyHandlers[key]=[];
}
this._keyHandlers[key].push({shift:_50||false,ctrl:_4f||false,handler:_51});
},onKeyPressed:function(){
this.onDisplayChanged();
},onClick:function(e){
this.onDisplayChanged(e);
},_onMouseDown:function(e){
if(!this._focused&&!this.disabled){
this.focus();
}
},_savedSelection:null,_saveSelection:function(){
var r=dijit.range.getSelection(this.window).getRangeAt(0);
var _55=this._getRangeNodes(r);
this._savedSelection={range:((dojo.isIE)?r.duplicate():r.cloneRange()),nodes:_55,bookmark:this._getBookmark(_55)};

},_onBlur:function(e){
},_customOnBlur:function(e){
this._saveSelection();
var _c=this.getValue(true);
if(_c!=this.savedContent){
this.onChange(_c);
this.savedContent=_c;
}
if(dojo.isMoz&&this.iframe){
this.iframe.contentDocument.title=this._localizedIframeTitles.iframeEditTitle;
}
e.stopPropagation();
},_initialFocus:true,_customOnFocus:function(e){
setTimeout(dojo.hitch(this,"_doOnFocus"),10);
},_doOnFocus:function(){
if(this._initialFocus){
this._initialFocus=false;
if(dojo.isMoz){
if(this.editNode.innerHTML.replace(/^\s+|\s+$/g,"")=="&nbsp;"){
this.placeCursorAtStart();
}
}
}
if(dojo.isSafari){
this.placeCursorAtStart();
}

if(this._savedSelection){
this._moveToBookmark(this._savedSelection.bookmark);
}
this._savedSelection=null;
},blur:function(){
this.editNode.blur();
},focus:function(){

if(!this.iframe&&dojo.isSafari){
return;
}
if(this.iframe&&!dojo.isIE){
dijit.focus(this.iframe);
}else{
if(this.editNode&&this.editNode.focus){
this.editNode.focus();
}
}
},updateInterval:200,_updateTimer:null,onDisplayChanged:function(e){
if(this._updateTimer){
clearTimeout(this._updateTimer);
}
if(!this._updateHandler){
this._updateHandler=dojo.hitch(this,"onNormalizedDisplayChanged");
}
this._updateTimer=setTimeout(this._updateHandler,this.updateInterval);
},onNormalizedDisplayChanged:function(){
delete this._updateTimer;
},onChange:function(_5b){
},_normalizeCommand:function(cmd){
var _5d=cmd.toLowerCase();
if(_5d=="formatblock"){
if(dojo.isSafari){
_5d="heading";
}
}else{
if(_5d=="hilitecolor"&&!dojo.isMoz){
_5d="backcolor";
}
}
return _5d;
},_qcaCache:{},queryCommandAvailable:function(_5e){
var ca=this._qcaCache[_5e];
if(ca!=undefined){
return ca;
}
return this._qcaCache[_5e]=this._queryCommandAvailable(_5e);
},_queryCommandAvailable:function(_60){
var ie=1;
var _62=1<<1;
var _63=1<<2;
var _64=1<<3;
var _65=1<<4;
var _66=dojo.isSafari;
function _67(_68){
return {ie:Boolean(_68&ie),mozilla:Boolean(_68&_62),safari:Boolean(_68&_63),safari420:Boolean(_68&_65),opera:Boolean(_68&_64)};
};
var _69=null;
switch(_60.toLowerCase()){
case "bold":
case "italic":
case "underline":
case "subscript":
case "superscript":
case "fontname":
case "fontsize":
case "forecolor":
case "hilitecolor":
case "justifycenter":
case "justifyfull":
case "justifyleft":
case "justifyright":
case "delete":
case "selectall":
case "toggledir":
_69=_67(_62|ie|_63|_64);
break;
case "createlink":
case "unlink":
case "removeformat":
case "inserthorizontalrule":
case "insertimage":
case "insertorderedlist":
case "insertunorderedlist":
case "indent":
case "outdent":
case "formatblock":
case "inserthtml":
case "undo":
case "redo":
case "strikethrough":
case "tabindent":
_69=_67(_62|ie|_64|_65);
break;
case "blockdirltr":
case "blockdirrtl":
case "dirltr":
case "dirrtl":
case "inlinedirltr":
case "inlinedirrtl":
_69=_67(ie);
break;
case "cut":
case "copy":
case "paste":
_69=_67(ie|_62|_65);
break;
case "inserttable":
_69=_67(_62|ie);
break;
case "insertcell":
case "insertcol":
case "insertrow":
case "deletecells":
case "deletecols":
case "deleterows":
case "mergecells":
case "splitcell":
_69=_67(ie|_62);
break;
default:
return false;
}
return (dojo.isIE&&_69.ie)||(dojo.isMoz&&_69.mozilla)||(dojo.isSafari&&_69.safari)||(_66&&_69.safari420)||(dojo.isOpera&&_69.opera);
},execCommand:function(_6a,_6b){
var _6c;
this.focus();
var c=dojox.editor.refactor.RichText._commands;
var _6e;
if(_6e=c.match(_6a.toLowerCase())){
return _6e.applyCommand(this,_6b);
}
_6a=this._normalizeCommand(_6a);
if(_6b!=undefined){
if(_6a=="heading"){
throw new Error("unimplemented");
}else{
if((_6a=="formatblock")&&dojo.isIE){
_6b="<"+_6b+">";
}
}
}
if(_6a=="inserthtml"){
_6b=this._preFilterContent(_6b);
_6c=true;
if(dojo.isIE){
var _6f=this.document.selection.createRange();
_6f.pasteHTML(_6b);
_6f.select();
}else{
if(dojo.isMoz&&!_6b.length){
this._sCall("remove");
_6c=true;
}else{
_6c=this.document.execCommand(_6a,false,_6b);
}
}
}else{
if((_6a=="unlink")&&(this.queryCommandEnabled("unlink"))&&(dojo.isMoz||dojo.isSafari)){
var a=this._sCall("getAncestorElement",["a"]);
this._sCall("selectElement",[a]);
_6c=this.document.execCommand("unlink",false,null);
}else{
if((_6a=="hilitecolor")&&(dojo.isMoz)){
this.document.execCommand("styleWithCSS",false,true);
_6c=this.document.execCommand(_6a,false,_6b);
this.document.execCommand("styleWithCSS",false,false);
}else{
if((dojo.isIE)&&((_6a=="backcolor")||(_6a=="forecolor"))){
_6b=arguments.length>1?_6b:null;
_6c=this.document.execCommand(_6a,false,_6b);
}else{
_6b=arguments.length>1?_6b:null;
if(_6b||_6a!="createlink"){
_6c=this.document.execCommand(_6a,false,_6b);
}
}
}
}
}
this.onDisplayChanged();
return _6c;
},queryCommandEnabled:function(_71){
if(this.disabled){
return false;
}
_71=this._normalizeCommand(_71);
if(dojo.isMoz||dojo.isSafari){
if(_71=="unlink"){
this._sCall("hasAncestorElement",["a"]);
}else{
if(_71=="inserttable"){
return true;
}
}
}
if(dojo.isSafari){
if(_71=="copy"){
_71="cut";
}else{
if(_71=="paste"){
return true;
}
}
}
if(_71=="indent"){
var li=this._sCall("getAncestorElement",["li"]);
var n=li&&li.previousSibling;
while(n){
if(n.nodeType==1){
return true;
}
n=n.previousSibling;
}
return false;
}else{
if(_71=="outdent"){
return this._sCall("hasAncestorElement",["li"]);
}
}
var _74=dojo.isIE?this.document.selection.createRange():this.document;
return _74.queryCommandEnabled(_71);
},queryCommandState:function(_75){
if(this.disabled){
return false;
}
var c=dojox.editor.refactor.RichText._commands;
var _77;
if(_77=c.match(_75)){
return _77.queryState(this);
}
_75=this._normalizeCommand(_75);
this.editNode.contentEditable=true;
return this.document.queryCommandState(_75);
},queryCommandValue:function(_78){
if(this.disabled){
return false;
}
var r;
_78=this._normalizeCommand(_78);
if(dojo.isIE&&_78=="formatblock"){
r=this._native2LocalFormatNames[this.document.queryCommandValue(_78)];
}else{
r=this.document.queryCommandValue(_78);
}
return r;
},_sCall:function(_7a,_7b){
dojo.withGlobal(this.window,_7a,dijit._editor.selection,_7b);
},placeCursorAtStart:function(){
this.focus();
var _7c=false;
if(dojo.isMoz){
var _7d=this.editNode.firstChild;
while(_7d){
if(_7d.nodeType==3){
if(_7d.nodeValue.replace(/^\s+|\s+$/g,"").length>0){
_7c=true;
this._sCall("selectElement",[_7d]);
break;
}
}else{
if(_7d.nodeType==1){
_7c=true;
this._sCall("selectElementChildren",[_7d]);
break;
}
}
_7d=_7d.nextSibling;
}
}else{
_7c=true;
this._sCall("selectElementChildren",[this.editNode]);
}
if(_7c){
this._sCall("collapse",[true]);
}
},placeCursorAtEnd:function(){
this.focus();
var _7e=false;
if(dojo.isMoz){
var _7f=this.editNode.lastChild;
while(_7f){
if(_7f.nodeType==3){
if(_7f.nodeValue.replace(/^\s+|\s+$/g,"").length>0){
_7e=true;
this._sCall("selectElement",[_7f]);
break;
}
}else{
if(_7f.nodeType==1){
_7e=true;
if(_7f.lastChild){
this._sCall("selectElement",[_7f.lastChild]);
}else{
this._sCall("selectElement",[_7f]);
}
break;
}
}
_7f=_7f.previousSibling;
}
}else{
_7e=true;
this._sCall("selectElementChildren",[this.editNode]);
}
if(_7e){
this._sCall("collapse",[false]);
}
},getValue:function(_80){
if(this.textarea){
if(this.isClosed||!this.isLoaded){
return this.textarea.value;
}
}
return this._postFilterContent(null,_80);
},setValue:function(_81){
if(this.textarea&&(this.isClosed||!this.isLoaded)){
this.textarea.value=_81;
}else{
_81=this._preFilterContent(_81);
var _82=this.isClosed?this.domNode:this.editNode;
_82.innerHTML=_81;
this._preDomFilterContent(_82);
}
this.onDisplayChanged();
},replaceValue:function(_83){
if(this.isClosed){
this.setValue(_83);
}else{
if(this.window&&this.window.getSelection&&!dojo.isMoz){
this.setValue(_83);
}else{
if(this.window&&this.window.getSelection){
_83=this._preFilterContent(_83);
this.execCommand("selectall");
if(dojo.isMoz&&!_83){
_83="&nbsp;";
}
this.execCommand("inserthtml",_83);
this._preDomFilterContent(this.editNode);
}else{
if(this.document&&this.document.selection){
this.setValue(_83);
}
}
}
}
},_preFilterContent:function(_84){
var ec=_84;
dojo.forEach(this.contentPreFilters,function(ef){
if(ef){
ec=ef(ec);
}
});
return ec;
},_preDomFilterContent:function(dom){
dom=dom||this.editNode;
dojo.forEach(this.contentDomPreFilters,function(ef){
if(ef&&dojo.isFunction(ef)){
ef(dom);
}
},this);
},_postFilterContent:function(dom,_8a){
var ec;
if(!dojo.isString(dom)){
dom=dom||this.editNode;
if(this.contentDomPostFilters.length){
if(_8a){
dom=dojo.clone(dom);
}
dojo.forEach(this.contentDomPostFilters,function(ef){
dom=ef(dom);
});
}
ec=dijit._editor.getChildrenHtml(dom);
}else{
ec=dom;
}
if(!dojo.trim(ec.replace(/^\xA0\xA0*/,"").replace(/\xA0\xA0*$/,"")).length){
ec="";
}
dojo.forEach(this.contentPostFilters,function(ef){
ec=ef(ec);
});
return ec;
},_saveContent:function(e){
var _8f=dojo.byId(dijit._scopeName+"._editor.RichText.savedContent");
_8f.value+=this._SEPARATOR+this.name+":"+this.getValue();
},escapeXml:function(str,_91){
str=str.replace(/&/gm,"&amp;").replace(/</gm,"&lt;").replace(/>/gm,"&gt;").replace(/"/gm,"&quot;");
if(!_91){
str=str.replace(/'/gm,"&#39;");
}
return str;
},getNodeHtml:function(_92){
dojo.deprecated("dijit.Editor::getNodeHtml is deprecated","use dijit._editor.getNodeHtml instead",2);
return dijit._editor.getNodeHtml(_92);
},getNodeChildrenHtml:function(dom){
dojo.deprecated("dijit.Editor::getNodeChildrenHtml is deprecated","use dijit._editor.getChildrenHtml instead",2);
return dijit._editor.getChildrenHtml(dom);
},close:function(_94,_95){
if(this.isClosed){
return false;
}
if(!arguments.length){
_94=true;
}
this._content=this.getValue();
var _96=(this.savedContent!=this._content);
if(this.interval){
clearInterval(this.interval);
}
if(this.textarea){
with(this.textarea.style){
position="";
left=top="";
if(dojo.isIE){
overflow=this.__overflow;
this.__overflow=null;
}
}
this.textarea.value=_94?this._content:this.savedContent;
dojo.destroy(this.domNode);
this.domNode=this.textarea;
}else{
this.domNode.innerHTML=_94?this._content:this.savedContent;
}
dojo.removeClass(this.domNode,"RichTextEditable");
this.isClosed=true;
this.isLoaded=false;
delete this.editNode;
if(this.window&&this.window._frameElement){
this.window._frameElement=null;
}
this.window=null;
this.document=null;
this.editingArea=null;
this.editorObject=null;
return _96;
},destroyRendering:function(){
},destroy:function(){
this.destroyRendering();
if(!this.isClosed){
this.close(false);
}
this.inherited("destroy",arguments);
},_removeMozBogus:function(_97){
return _97.replace(/\stype="_moz"/gi,"").replace(/\s_moz_dirty=""/gi,"");
},_removeSafariBogus:function(_98){
return _98.replace(/\sclass="webkit-block-placeholder"/gi,"");
},_fixContentForMoz:function(_99){
return _99.replace(/<(\/)?strong([ \>])/gi,"<$1b$2").replace(/<(\/)?em([ \>])/gi,"<$1i$2");
},_preFixUrlAttributes:function(_9a){
return _9a.replace(/(?:(<a(?=\s).*?\shref=)("|')(.*?)\2)|(?:(<a\s.*?href=)([^"'][^ >]+))/gi,"$1$4$2$3$5$2 _djrealurl=$2$3$5$2").replace(/(?:(<img(?=\s).*?\ssrc=)("|')(.*?)\2)|(?:(<img\s.*?src=)([^"'][^ >]+))/gi,"$1$4$2$3$5$2 _djrealurl=$2$3$5$2");
},_bookmarkId:0,_getBookmark:function(_9b){
var _9c,_9d;
var id=this._bookmarkId++;
if(_9b.length){
_9c=this.document.createElement("span");
dojo.attr(_9c,{isBookmark:"true",bookmarkId:id,style:{width:"1px",height:"1px",overflow:"hidden",border:"3px solid blue"}});
_9d=_9c.cloneNode(false);
var sid="_richText_startMarker_"+this.id+"_"+id;
var eid="_richText_endMarker_"+this.id+"_"+id;
_9c.id=sid;
_9d.id=eid;
if(dojo.isIE){
_9c.innerHTML=sid;
_9d.innerHTML=eid;
}
dojo.place(_9c,_9b[0],"before");
dojo.place(_9d,_9b.last(),"after");
}
return [_9c,_9d];
},_getRangeNodes:function(s){
if(!s){
s=dijit.range.getSelection(this.window);
}
if(!s.startContainer&&s.focusNode){
s=s.getRangeAt(0);
}
var r=[];
r.last=function(){
return this[this.length-1];
};
var _a3=s.commonAncestorContainer;
var sc=s.startContainer;
var so=s.startOffset;
var ec=s.endContainer;
var eo=s.endOffset;
var od=sc.ownerDocument;
var tmp=od.createTextNode("");
if(sc===ec){
var _aa=(dojo.isIE)?!s.text.length:s.collapsed;
if(_aa){
var _ab=tmp.cloneNode(true);
if(sc.nodeType==1){
dojo.place(_ab,this.domNode,"first");
}else{
var _ac=sc.splitText(so);
sc.parentNode.insertBefore(_ab,_ac);
}
r.push(_ab);
}else{
if(1==sc.nodeType){
var tmp=so;
do{
r.push(sc.childNodes.item(tmp));
tmp++;
}while(tmp<eo);
}else{
if(3==sc.nodeType){
sc.splitText(eo);
r.push(sc.splitText(so));
}
}
}
return r;
}
if(3==sc.nodeType){
var l=String(sc.value).length;
if(0==so){
if((sc.parentNode!=_a3)&&!sc.previousSibling){
r.push(sc.parentNode);
}else{
r.push(sc);
}
}else{
if(l==so){
var ns=sc.nextSibling;
if(ns){
if(!dojo.isDescendant(ec,ns)){
r.push(ns);
}else{
var _ab=tmp.cloneNode();
dojo.place(_ab,sc,"after");
r.push(_ab);
}
}
}else{
r.push(sc.splitText(so));
ec.splitText(eo);
}
}
}else{
if(1==sc.nodeType){
var cn=dojo._toArray(sc.childNodes);
if(sc===_a3){
var end=false;
dojo.forEach(cn,function(_b1,idx,arr){
if(end){
return;
}
if((_b1===ec)||dojo.isDescendant(ec,_b1)){
end=true;
return;
}
if(idx>=so){
r.push(_b1);
}
});
}else{
if(sc.parentNode===_a3){
dojo.forEach(cn,function(_b4,idx,arr){
if(idx>=so){
r.push(_b4);
}
});
var _ac=sc.nextSibling;
while(_ac&&((_ac!==ec)&&!dojo.isDescendant(ec,_ac))){
r.push(_ac);
_ac=_ac.nextSibling;
}
}else{
if(so){
r.push.apply(r,cn.slice(so));
}else{
r.push(sc);
}
}
}
}
}
this._collectNodes(r,_a3,ec,eo);
return r;
},_collectNodes:function(arr,_b8,end,_ba){
var _bb=arr.last();
if(!_bb||(_bb===_b8)){
return;
}
do{
var n=_bb.nextSibling;
while(n){
if(dojo.isDescendant(end,n)){
break;
}
arr.push(n);
n=n.nextSibling;
}
_bb=_bb.parentNode;
}while(_bb&&_bb!=_b8);
_bb=arr.last();
if(1==end.nodeType){
end=end.childNodes[_ba-1];
}
arr.push(end);
var al=arr.length-1;
var _be=end;
do{
var n=_be.previousSibling;
while(n){
if(n==_bb){
return;
}
arr.splice(al,0,n);
n=n.previousSibling;
}
_be=_be.parentNode;
}while(_be&&_be!=_b8);
},_moveToBookmark:function(_bf){
var _c0=_bf[0];
var end=_bf[1];
if(!_c0||!end){
console.error("_moveToBookmark must be passed start/end caps to be removed!");
return;
}
if(dojo.isIE){
var r=this.document.createTextRange();
var r2=r.duplicate();
var _c4=r.findText(_c0.innerHTML,100000000);
var _c5=r2.findText(end.innerHTML,100000000);
var r3=r.duplicate();
r3.move("character",_c0.innerHTML.length);
r3.setEndPoint("EndToStart",r2);
r3.select();
}
var _c7=_c0.nextSibling;
var _c8=end.previousSibling;
var _c9=end.nextSibling;
if(dojo.isIE){
end.innerHTML=_c0.innerHTML="";
}
_c0.parentNode.removeChild(_c0);
end.parentNode.removeChild(end);
if(!dojo.isIE){
var s=this.window.getSelection();
if(s.rangeCount>0){
s.removeAllRanges();
}
var r=this.document.createRange();
r.setStartBefore(_c7);
var ep=_c8||_c9.previousSibling||_c9.parentNode.previousSibling||_c9;
r.setEndAfter(ep);
s.addRange(r);
}
}});
dojo.declare("dojox.editor.refactor.Command",null,{name:"",constructor:function(_cc){
dojo.mixin(this,_cc);
this.register();
this.init();
},register:function(_cd){
var r=this.registry=_cd||this.registry;
if(r&&this.name.length){
r.register(this.name,dojo.hitch(this,"registryCheck"),this);
}
},init:function(){
},registryCheck:function(_cf,_d0){
return (_cf==this.name);
},queryEnabled:function(rt){
return false;
},queryValue:function(rt){
return null;
},queryState:function(rt){
return false;
},applyCommand:function(rt,_d5){
return true;
}});
dojo.declare("dojox.editor.refactor.TagWrapCommand",dojox.editor.refactor.Command,{init:function(){
if(this.tag){
this._upperTag=this._upperTag||this.tag.toUpperCase();
}
if(this.name){
this._nameAttr=this.name+"_command";
}
},tag:"",attrs:{},applicationHelper:function(arg,_d7){
},removalHelper:function(arg,_d9){
},isntAppliedHelper:function(_da){
return false;
},reParentOnRemoval:false,applyCommand:function(rt,arg){
var _dd=rt._getRangeNodes();
var _de=rt._getBookmark(_dd);
var _df=dojo.filter(_dd,this._isntAppliedToNode,this);
if(!_df.length){

dojo.forEach(_dd,this._removeFromNode,this);
dojo.forEach(_dd,dojo.hitch(this,"removalHelper",arg));
}else{

dojo.forEach(_df,this._applyToNode,this);
dojo.forEach(_df,dojo.hitch(this,"applicationHelper",arg));
}
rt._moveToBookmark(_de);
return true;
},queryState:function(rt){
return this._isApplied(rt._getRangeNodes());
},_isApplied:function(rn){
var r=(0==dojo.filter(rn,this._isntAppliedToNode,this));
return r;
},_isntAppliedToNode:function(_e3){
var ta=this.attrs;
var nt=_e3.nodeType;
if(1==nt){
if(this.tag.length){
if(!this._isTagMatch(_e3)){
return true;
}
}
for(var x in ta){
if(x=="style"){
continue;
}
if(dojo.attr(_e3,x)!=ta[x]){
return true;
}
}
if(ta.style){
for(var x in ta.style){
if(dojo.style(_e3,x)!=ta.style[x]){
return true;
}
}
}
return this.isntAppliedHelper(_e3);
}else{
if(3==nt){
if(!_e3.nodeValue.length){
return false;
}
return this._isntAppliedToNode(_e3.parentNode);
}
}
},_isTagMatch:function(_e7){
return (_e7.tagName==this._upperTag);
},_applyToNode:function(_e8){
var nt=_e8.nodeType;
var el=_e8;
if((3==nt)||!this._isTagMatch(_e8)){
el=_e8.ownerDocument.createElement(this.tag||"span");
_e8.parentNode.replaceChild(el,_e8);
el.appendChild(_e8);
}
dojo.attr(el,this.attrs);
dojo.attr(el,this._nameAttr,"applied");
return el;
},_removeFromSingleNode:function(_eb){
try{
dojo.removeAttr(_eb,this._nameAttr);
}
catch(e){
}
for(var x in this.attrs){
if(x!="style"){
dojo.attr(_eb,x,"");
}
}
for(var x in this.attrs.style){
dojo.style(_eb,x,"");
}
if(this.reParentOnRemoval){
while(_eb.firstChild){
_eb.parentNode.insertBefore(_eb.firstChild,_eb);
}
_eb.parentNode.removeChild(_eb);
}
},_removeFromNode:function(_ed,idx,arr){
if(3==_ed.nodeType){
var pn=_ed.parentNode;
if(pn.lastChild==_ed){
dojo.place(_ed,pn,"after");
}else{
if(pn.firstChild==_ed){
dojo.place(_ed,pn,"before");
}else{
return this._removeFromNode(pn,idx,arr);
}
}
return;
}
if(dojo.attr(_ed,this._nameAttr)){
this._removeFromSingleNode(_ed);
}
dojo.query("["+this._nameAttr+"]",_ed).forEach(this._removeFromSingleNode,this);
}});
(function(){
var de=dojox.editor.refactor;
var c=de.RichText._commands=new dojo.AdapterRegistry(true);
var TRC=de.TagWrapCommand;
new TRC({name:"bold",tag:"span",attrs:{style:{fontWeight:"bold"}},registry:c});
new TRC({name:"italic",tag:"span",attrs:{style:{fontStyle:"italic"}},registry:c});
new TRC({name:"strikethrough",tag:"span",attrs:{style:{textDecoration:"line-through"}},registry:c});
new TRC({name:"underline",tag:"span",attrs:{style:{textDecoration:"underline"}},registry:c});
new TRC({name:"subscript",tag:"span",attrs:{style:{verticalAlign:"sub"}},registry:c});
new TRC({name:"superscript",tag:"span",attrs:{style:{verticalAlign:"super"}},registry:c});
new TRC({name:"fontname",tag:"span",applicationHelper:function(arg,_f5){
dojo.style(_f5,"fontFamily",arg);
},removalHelper:function(arg,_f7){
dojo.style(_f7,"fontFamily","");
},isntAppliedHelper:function(_f8){

return !dojo.style(_f8,"fontFamily");
},registry:c});
})();
}
