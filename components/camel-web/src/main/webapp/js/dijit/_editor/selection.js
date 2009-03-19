/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.selection"]){
dojo._hasResource["dijit._editor.selection"]=true;
dojo.provide("dijit._editor.selection");
dojo.mixin(dijit._editor.selection,{getType:function(){
if(dojo.doc.selection){
return dojo.doc.selection.type.toLowerCase();
}else{
var _1="text";
var _2;
try{
_2=dojo.global.getSelection();
}
catch(e){
}
if(_2&&_2.rangeCount==1){
var _3=_2.getRangeAt(0);
if((_3.startContainer==_3.endContainer)&&((_3.endOffset-_3.startOffset)==1)&&(_3.startContainer.nodeType!=3)){
_1="control";
}
}
return _1;
}
},getSelectedText:function(){
if(dojo.doc.selection){
if(dijit._editor.selection.getType()=="control"){
return null;
}
return dojo.doc.selection.createRange().text;
}else{
var _4=dojo.global.getSelection();
if(_4){
return _4.toString();
}
}
return "";
},getSelectedHtml:function(){
if(dojo.doc.selection){
if(dijit._editor.selection.getType()=="control"){
return null;
}
return dojo.doc.selection.createRange().htmlText;
}else{
var _5=dojo.global.getSelection();
if(_5&&_5.rangeCount){
var _6=_5.getRangeAt(0).cloneContents();
var _7=dojo.doc.createElement("div");
_7.appendChild(_6);
return _7.innerHTML;
}
return null;
}
},getSelectedElement:function(){
if(dijit._editor.selection.getType()=="control"){
if(dojo.doc.selection){
var _8=dojo.doc.selection.createRange();
if(_8&&_8.item){
return dojo.doc.selection.createRange().item(0);
}
}else{
var _9=dojo.global.getSelection();
return _9.anchorNode.childNodes[_9.anchorOffset];
}
}
return null;
},getParentElement:function(){
if(dijit._editor.selection.getType()=="control"){
var p=this.getSelectedElement();
if(p){
return p.parentNode;
}
}else{
if(dojo.doc.selection){
var r=dojo.doc.selection.createRange();
r.collapse(true);
return r.parentElement();
}else{
var _c=dojo.global.getSelection();
if(_c){
var _d=_c.anchorNode;
while(_d&&(_d.nodeType!=1)){
_d=_d.parentNode;
}
return _d;
}
}
}
return null;
},hasAncestorElement:function(_e){
return this.getAncestorElement.apply(this,arguments)!=null;
},getAncestorElement:function(_f){
var _10=this.getSelectedElement()||this.getParentElement();
return this.getParentOfType(_10,arguments);
},isTag:function(_11,_12){
if(_11&&_11.tagName){
var _13=_11.tagName.toLowerCase();
for(var i=0;i<_12.length;i++){
var _15=String(_12[i]).toLowerCase();
if(_13==_15){
return _15;
}
}
}
return "";
},getParentOfType:function(_16,_17){
while(_16){
if(this.isTag(_16,_17).length){
return _16;
}
_16=_16.parentNode;
}
return null;
},collapse:function(_18){
if(window["getSelection"]){
var _19=dojo.global.getSelection();
if(_19.removeAllRanges){
if(_18){
_19.collapseToStart();
}else{
_19.collapseToEnd();
}
}else{
_19.collapse(_18);
}
}else{
if(dojo.doc.selection){
var _1a=dojo.doc.selection.createRange();
_1a.collapse(_18);
_1a.select();
}
}
},remove:function(){
var _s=dojo.doc.selection;
if(_s){
if(_s.type.toLowerCase()!="none"){
_s.clear();
}
return _s;
}else{
_s=dojo.global.getSelection();
_s.deleteFromDocument();
return _s;
}
},selectElementChildren:function(_1c,_1d){
var _1e=dojo.global;
var _1f=dojo.doc;
_1c=dojo.byId(_1c);
if(_1f.selection&&dojo.body().createTextRange){
var _20=_1c.ownerDocument.body.createTextRange();
_20.moveToElementText(_1c);
if(!_1d){
try{
_20.select();
}
catch(e){
}
}
}else{
if(_1e.getSelection){
var _21=_1e.getSelection();
if(_21.setBaseAndExtent){
_21.setBaseAndExtent(_1c,0,_1c,_1c.innerText.length-1);
}else{
if(_21.selectAllChildren){
_21.selectAllChildren(_1c);
}
}
}
}
},selectElement:function(_22,_23){
var _24,_25=dojo.doc;
_22=dojo.byId(_22);
if(_25.selection&&dojo.body().createTextRange){
try{
_24=dojo.body().createControlRange();
_24.addElement(_22);
if(!_23){
_24.select();
}
}
catch(e){
this.selectElementChildren(_22,_23);
}
}else{
if(dojo.global.getSelection){
var _26=dojo.global.getSelection();
if(_26.removeAllRanges){
_24=_25.createRange();
_24.selectNode(_22);
_26.removeAllRanges();
_26.addRange(_24);
}
}
}
}});
}
