/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.EnterKeyHandling"]){
dojo._hasResource["dijit._editor.plugins.EnterKeyHandling"]=true;
dojo.provide("dijit._editor.plugins.EnterKeyHandling");
dojo.declare("dijit._editor.plugins.EnterKeyHandling",dijit._editor._Plugin,{blockNodeForEnter:"BR",constructor:function(_1){
if(_1){
dojo.mixin(this,_1);
}
},setEditor:function(_2){
this.editor=_2;
if(this.blockNodeForEnter=="BR"){
if(dojo.isIE){
_2.contentDomPreFilters.push(dojo.hitch(this,"regularPsToSingleLinePs"));
_2.contentDomPostFilters.push(dojo.hitch(this,"singleLinePsToRegularPs"));
_2.onLoadDeferred.addCallback(dojo.hitch(this,"_fixNewLineBehaviorForIE"));
}else{
_2.onLoadDeferred.addCallback(dojo.hitch(this,function(d){
try{
this.editor.document.execCommand("insertBrOnReturn",false,true);
}
catch(e){
}
return d;
}));
}
}else{
if(this.blockNodeForEnter){
dojo["require"]("dijit._editor.range");
var h=dojo.hitch(this,this.handleEnterKey);
_2.addKeyHandler(13,0,0,h);
_2.addKeyHandler(13,0,1,h);
this.connect(this.editor,"onKeyPressed","onKeyPressed");
}
}
},connect:function(o,f,tf){
if(!this._connects){
this._connects=[];
}
this._connects.push(dojo.connect(o,f,this,tf));
},destroy:function(){
dojo.forEach(this._connects,dojo.disconnect);
this._connects=[];
},onKeyPressed:function(e){
if(this._checkListLater){
if(dojo.withGlobal(this.editor.window,"isCollapsed",dijit)){
var _9=dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,["LI"]);
if(!_9){
dijit._editor.RichText.prototype.execCommand.call(this.editor,"formatblock",this.blockNodeForEnter);
var _a=dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,[this.blockNodeForEnter]);
if(_a){
_a.innerHTML=this.bogusHtmlContent;
if(dojo.isIE){
var r=this.editor.document.selection.createRange();
r.move("character",-1);
r.select();
}
}else{
alert("onKeyPressed: Can not find the new block node");
}
}else{
if(dojo.isMoz){
if(_9.parentNode.parentNode.nodeName=="LI"){
_9=_9.parentNode.parentNode;
}
}
var fc=_9.firstChild;
if(fc&&fc.nodeType==1&&(fc.nodeName=="UL"||fc.nodeName=="OL")){
_9.insertBefore(fc.ownerDocument.createTextNode(" "),fc);
var _d=dijit.range.create();
_d.setStart(_9.firstChild,0);
var _e=dijit.range.getSelection(this.editor.window,true);
_e.removeAllRanges();
_e.addRange(_d);
}
}
}
this._checkListLater=false;
}
if(this._pressedEnterInBlock){
if(this._pressedEnterInBlock.previousSibling){
this.removeTrailingBr(this._pressedEnterInBlock.previousSibling);
}
delete this._pressedEnterInBlock;
}
},bogusHtmlContent:"&nbsp;",blockNodes:/^(?:P|H1|H2|H3|H4|H5|H6|LI)$/,handleEnterKey:function(e){
if(!this.blockNodeForEnter){
return true;
}
var _10,_11,_12,doc=this.editor.document,br;
if(e.shiftKey||this.blockNodeForEnter=="BR"){
var _15=dojo.withGlobal(this.editor.window,"getParentElement",dijit._editor.selection);
var _16=dijit.range.getAncestor(_15,this.blockNodes);
if(_16){
if(!e.shiftKey&&_16.tagName=="LI"){
return true;
}
_10=dijit.range.getSelection(this.editor.window);
_11=_10.getRangeAt(0);
if(!_11.collapsed){
_11.deleteContents();
}
if(dijit.range.atBeginningOfContainer(_16,_11.startContainer,_11.startOffset)){
if(e.shiftKey){
br=doc.createElement("br");
_12=dijit.range.create();
_16.insertBefore(br,_16.firstChild);
_12.setStartBefore(br.nextSibling);
_10.removeAllRanges();
_10.addRange(_12);
}else{
dojo.place(br,_16,"before");
}
}else{
if(dijit.range.atEndOfContainer(_16,_11.startContainer,_11.startOffset)){
_12=dijit.range.create();
br=doc.createElement("br");
if(e.shiftKey){
_16.appendChild(br);
_16.appendChild(doc.createTextNode(" "));
_12.setStart(_16.lastChild,0);
}else{
dojo.place(br,_16,"after");
_12.setStartAfter(_16);
}
_10.removeAllRanges();
_10.addRange(_12);
}else{
return true;
}
}
}else{
dijit._editor.RichText.prototype.execCommand.call(this.editor,"inserthtml","<br>");
}
return false;
}
var _17=true;
_10=dijit.range.getSelection(this.editor.window);
_11=_10.getRangeAt(0);
if(!_11.collapsed){
_11.deleteContents();
}
var _18=dijit.range.getBlockAncestor(_11.endContainer,null,this.editor.editNode);
var _19=_18.blockNode;
if((this._checkListLater=(_19&&(_19.nodeName=="LI"||_19.parentNode.nodeName=="LI")))){
if(dojo.isMoz){
this._pressedEnterInBlock=_19;
}
if(/^(?:\s|&nbsp;)$/.test(_19.innerHTML)){
_19.innerHTML="";
}
return true;
}
if(!_18.blockNode||_18.blockNode===this.editor.editNode){
dijit._editor.RichText.prototype.execCommand.call(this.editor,"formatblock",this.blockNodeForEnter);
_18={blockNode:dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,[this.blockNodeForEnter]),blockContainer:this.editor.editNode};
if(_18.blockNode){
if(!(_18.blockNode.textContent||_18.blockNode.innerHTML).replace(/^\s+|\s+$/g,"").length){
this.removeTrailingBr(_18.blockNode);
return false;
}
}else{
_18.blockNode=this.editor.editNode;
}
_10=dijit.range.getSelection(this.editor.window);
_11=_10.getRangeAt(0);
}
var _1a=doc.createElement(this.blockNodeForEnter);
_1a.innerHTML=this.bogusHtmlContent;
this.removeTrailingBr(_18.blockNode);
if(dijit.range.atEndOfContainer(_18.blockNode,_11.endContainer,_11.endOffset)){
if(_18.blockNode===_18.blockContainer){
_18.blockNode.appendChild(_1a);
}else{
dojo.place(_1a,_18.blockNode,"after");
}
_17=false;
_12=dijit.range.create();
_12.setStart(_1a,0);
_10.removeAllRanges();
_10.addRange(_12);
if(this.editor.height){
_1a.scrollIntoView(false);
}
}else{
if(dijit.range.atBeginningOfContainer(_18.blockNode,_11.startContainer,_11.startOffset)){
dojo.place(_1a,_18.blockNode,_18.blockNode===_18.blockContainer?"first":"before");
if(_1a.nextSibling&&this.editor.height){
_1a.nextSibling.scrollIntoView(false);
}
_17=false;
}else{
if(dojo.isMoz){
this._pressedEnterInBlock=_18.blockNode;
}
}
}
return _17;
},removeTrailingBr:function(_1b){
var _1c=/P|DIV|LI/i.test(_1b.tagName)?_1b:dijit._editor.selection.getParentOfType(_1b,["P","DIV","LI"]);
if(!_1c){
return;
}
if(_1c.lastChild){
if((_1c.childNodes.length>1&&_1c.lastChild.nodeType==3&&/^[\s\xAD]*$/.test(_1c.lastChild.nodeValue))||(_1c.lastChild&&_1c.lastChild.tagName=="BR")){
dojo.destroy(_1c.lastChild);
}
}
if(!_1c.childNodes.length){
_1c.innerHTML=this.bogusHtmlContent;
}
},_fixNewLineBehaviorForIE:function(d){
if(this.editor.document.__INSERTED_EDITIOR_NEWLINE_CSS===undefined){
var _1e="p{margin:0 !important;}";
var _1f=function(_20,doc,URI){
if(!_20){
return null;
}
if(!doc){
doc=document;
}
var _23=doc.createElement("style");
_23.setAttribute("type","text/css");
var _24=doc.getElementsByTagName("head")[0];
if(!_24){

return null;
}else{
_24.appendChild(_23);
}
if(_23.styleSheet){
var _25=function(){
try{
_23.styleSheet.cssText=_20;
}
catch(e){

}
};
if(_23.styleSheet.disabled){
setTimeout(_25,10);
}else{
_25();
}
}else{
var _26=doc.createTextNode(_20);
_23.appendChild(_26);
}
return _23;
};
_1f(_1e,this.editor.document);
this.editor.document.__INSERTED_EDITIOR_NEWLINE_CSS=true;
return d;
}
return null;
},regularPsToSingleLinePs:function(_27,_28){
function _29(el){
function _2b(_2c){
var _2d=_2c[0].ownerDocument.createElement("p");
_2c[0].parentNode.insertBefore(_2d,_2c[0]);
dojo.forEach(_2c,function(_2e){
_2d.appendChild(_2e);
});
};
var _2f=0;
var _30=[];
var _31;
while(_2f<el.childNodes.length){
_31=el.childNodes[_2f];
if(_31.nodeType==3||(_31.nodeType==1&&_31.nodeName!="BR"&&dojo.style(_31,"display")!="block")){
_30.push(_31);
}else{
var _32=_31.nextSibling;
if(_30.length){
_2b(_30);
_2f=(_2f+1)-_30.length;
if(_31.nodeName=="BR"){
dojo.destroy(_31);
}
}
_30=[];
}
_2f++;
}
if(_30.length){
_2b(_30);
}
};
function _33(el){
var _35=null;
var _36=[];
var _37=el.childNodes.length-1;
for(var i=_37;i>=0;i--){
_35=el.childNodes[i];
if(_35.nodeName=="BR"){
var _39=_35.ownerDocument.createElement("p");
dojo.place(_39,el,"after");
if(_36.length==0&&i!=_37){
_39.innerHTML="&nbsp;";
}
dojo.forEach(_36,function(_3a){
_39.appendChild(_3a);
});
dojo.destroy(_35);
_36=[];
}else{
_36.unshift(_35);
}
}
};
var _3b=[];
var ps=_27.getElementsByTagName("p");
dojo.forEach(ps,function(p){
_3b.push(p);
});
dojo.forEach(_3b,function(p){
if((p.previousSibling)&&(p.previousSibling.nodeName=="P"||dojo.style(p.previousSibling,"display")!="block")){
var _3f=p.parentNode.insertBefore(this.document.createElement("p"),p);
_3f.innerHTML=_28?"":"&nbsp;";
}
_33(p);
},this.editor);
_29(_27);
return _27;
},singleLinePsToRegularPs:function(_40){
function _41(_42){
var ps=_42.getElementsByTagName("p");
var _44=[];
for(var i=0;i<ps.length;i++){
var p=ps[i];
var _47=false;
for(var k=0;k<_44.length;k++){
if(_44[k]===p.parentNode){
_47=true;
break;
}
}
if(!_47){
_44.push(p.parentNode);
}
}
return _44;
};
function _49(_4a){
if(_4a.nodeType!=1||_4a.tagName!="P"){
return dojo.style(_4a,"display")=="block";
}else{
if(!_4a.childNodes.length||_4a.innerHTML=="&nbsp;"){
return true;
}
}
return false;
};
var _4b=_41(_40);
for(var i=0;i<_4b.length;i++){
var _4d=_4b[i];
var _4e=null;
var _4f=_4d.firstChild;
var _50=null;
while(_4f){
if(_4f.nodeType!="1"||_4f.tagName!="P"){
_4e=null;
}else{
if(_49(_4f)){
_50=_4f;
_4e=null;
}else{
if(_4e==null){
_4e=_4f;
}else{
if((!_4e.lastChild||_4e.lastChild.nodeName!="BR")&&(_4f.firstChild)&&(_4f.firstChild.nodeName!="BR")){
_4e.appendChild(this.editor.document.createElement("br"));
}
while(_4f.firstChild){
_4e.appendChild(_4f.firstChild);
}
_50=_4f;
}
}
}
_4f=_4f.nextSibling;
if(_50){
dojo.destroy(_50);
_50=null;
}
}
}
return _40;
}});
}
