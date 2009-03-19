/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.focus"]){
dojo._hasResource["dijit._base.focus"]=true;
dojo.provide("dijit._base.focus");
dojo.mixin(dijit,{_curFocus:null,_prevFocus:null,isCollapsed:function(){
var _1=dojo.doc;
if(_1.selection){
var s=_1.selection;
if(s.type=="Text"){
return !s.createRange().htmlText.length;
}else{
return !s.createRange().length;
}
}else{
var _3=dojo.global;
var _4=_3.getSelection();
if(dojo.isString(_4)){
return !_4;
}else{
return _4.isCollapsed||!_4.toString();
}
}
},getBookmark:function(){
var _5,_6=dojo.doc.selection;
if(_6){
var _7=_6.createRange();
if(_6.type.toUpperCase()=="CONTROL"){
if(_7.length){
_5=[];
var i=0,_9=_7.length;
while(i<_9){
_5.push(_7.item(i++));
}
}else{
_5=null;
}
}else{
_5=_7.getBookmark();
}
}else{
if(window.getSelection){
_6=dojo.global.getSelection();
if(_6){
_7=_6.getRangeAt(0);
_5=_7.cloneRange();
}
}else{
console.warn("No idea how to store the current selection for this browser!");
}
}
return _5;
},moveToBookmark:function(_a){
var _b=dojo.doc;
if(_b.selection){
var _c;
if(dojo.isArray(_a)){
_c=_b.body.createControlRange();
dojo.forEach(_a,function(n){
_c.addElement(n);
});
}else{
_c=_b.selection.createRange();
_c.moveToBookmark(_a);
}
_c.select();
}else{
var _e=dojo.global.getSelection&&dojo.global.getSelection();
if(_e&&_e.removeAllRanges){
_e.removeAllRanges();
_e.addRange(_a);
}else{
console.warn("No idea how to restore selection for this browser!");
}
}
},getFocus:function(_f,_10){
return {node:_f&&dojo.isDescendant(dijit._curFocus,_f.domNode)?dijit._prevFocus:dijit._curFocus,bookmark:!dojo.withGlobal(_10||dojo.global,dijit.isCollapsed)?dojo.withGlobal(_10||dojo.global,dijit.getBookmark):null,openedForWindow:_10};
},focus:function(_11){
if(!_11){
return;
}
var _12="node" in _11?_11.node:_11,_13=_11.bookmark,_14=_11.openedForWindow;
if(_12){
var _15=(_12.tagName.toLowerCase()=="iframe")?_12.contentWindow:_12;
if(_15&&_15.focus){
try{
_15.focus();
}
catch(e){
}
}
dijit._onFocusNode(_12);
}
if(_13&&dojo.withGlobal(_14||dojo.global,dijit.isCollapsed)){
if(_14){
_14.focus();
}
try{
dojo.withGlobal(_14||dojo.global,dijit.moveToBookmark,null,[_13]);
}
catch(e){
}
}
},_activeStack:[],registerIframe:function(_16){
dijit.registerWin(_16.contentWindow,_16);
},registerWin:function(_17,_18){
dojo.connect(_17.document,"onmousedown",function(evt){
dijit._justMouseDowned=true;
setTimeout(function(){
dijit._justMouseDowned=false;
},0);
dijit._onTouchNode(_18||evt.target||evt.srcElement);
});
var doc=_17.document;
if(doc){
if(dojo.isIE){
doc.attachEvent("onactivate",function(evt){
if(evt.srcElement.tagName.toLowerCase()!="#document"){
dijit._onFocusNode(_18||evt.srcElement);
}
});
doc.attachEvent("ondeactivate",function(evt){
dijit._onBlurNode(_18||evt.srcElement);
});
}else{
doc.addEventListener("focus",function(evt){
dijit._onFocusNode(_18||evt.target);
},true);
doc.addEventListener("blur",function(evt){
dijit._onBlurNode(_18||evt.target);
},true);
}
}
doc=null;
},_onBlurNode:function(_1f){
dijit._prevFocus=dijit._curFocus;
dijit._curFocus=null;
if(dijit._justMouseDowned){
return;
}
if(dijit._clearActiveWidgetsTimer){
clearTimeout(dijit._clearActiveWidgetsTimer);
}
dijit._clearActiveWidgetsTimer=setTimeout(function(){
delete dijit._clearActiveWidgetsTimer;
dijit._setStack([]);
dijit._prevFocus=null;
},100);
},_onTouchNode:function(_20){
if(dijit._clearActiveWidgetsTimer){
clearTimeout(dijit._clearActiveWidgetsTimer);
delete dijit._clearActiveWidgetsTimer;
}
var _21=[];
try{
while(_20){
if(_20.dijitPopupParent){
_20=dijit.byId(_20.dijitPopupParent).domNode;
}else{
if(_20.tagName&&_20.tagName.toLowerCase()=="body"){
if(_20===dojo.body()){
break;
}
_20=dijit.getDocumentWindow(_20.ownerDocument).frameElement;
}else{
var id=_20.getAttribute&&_20.getAttribute("widgetId");
if(id){
_21.unshift(id);
}
_20=_20.parentNode;
}
}
}
}
catch(e){
}
dijit._setStack(_21);
},_onFocusNode:function(_23){
if(!_23){
return;
}
if(_23.nodeType==9){
return;
}
dijit._onTouchNode(_23);
if(_23==dijit._curFocus){
return;
}
if(dijit._curFocus){
dijit._prevFocus=dijit._curFocus;
}
dijit._curFocus=_23;
dojo.publish("focusNode",[_23]);
},_setStack:function(_24){
var _25=dijit._activeStack;
dijit._activeStack=_24;
for(var _26=0;_26<Math.min(_25.length,_24.length);_26++){
if(_25[_26]!=_24[_26]){
break;
}
}
for(var i=_25.length-1;i>=_26;i--){
var _28=dijit.byId(_25[i]);
if(_28){
_28._focused=false;
_28._hasBeenBlurred=true;
if(_28._onBlur){
_28._onBlur();
}
if(_28._setStateClass){
_28._setStateClass();
}
dojo.publish("widgetBlur",[_28]);
}
}
for(i=_26;i<_24.length;i++){
_28=dijit.byId(_24[i]);
if(_28){
_28._focused=true;
if(_28._onFocus){
_28._onFocus();
}
if(_28._setStateClass){
_28._setStateClass();
}
dojo.publish("widgetFocus",[_28]);
}
}
}});
dojo.addOnLoad(function(){
dijit.registerWin(window);
});
}
