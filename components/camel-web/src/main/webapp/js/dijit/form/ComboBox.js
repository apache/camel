/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.ComboBox"]){
dojo._hasResource["dijit.form.ComboBox"]=true;
dojo.provide("dijit.form.ComboBox");
dojo.require("dijit.form.ValidationTextBox");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.data.util.filter");
dojo.require("dojo.regexp");
dojo.requireLocalization("dijit.form","ComboBox",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.form.ComboBoxMixin",null,{item:null,pageSize:Infinity,store:null,fetchProperties:{},query:{},autoComplete:true,highlightMatch:"first",searchDelay:100,searchAttr:"name",labelAttr:"",labelType:"text",queryExpr:"${0}*",ignoreCase:true,hasDownArrow:true,templateString:"<div class=\"dijit dijitReset dijitInlineTable dijitLeft\"\n\tid=\"widget_${id}\"\n\tdojoAttachEvent=\"onmouseenter:_onMouse,onmouseleave:_onMouse,onmousedown:_onMouse\" dojoAttachPoint=\"comboNode\" waiRole=\"combobox\" tabIndex=\"-1\"\n\t><div style=\"overflow:hidden;\"\n\t\t><div class='dijitReset dijitRight dijitButtonNode dijitArrowButton dijitDownArrowButton'\n\t\t\tdojoAttachPoint=\"downArrowNode\" waiRole=\"presentation\"\n\t\t\tdojoAttachEvent=\"onmousedown:_onArrowMouseDown,onmouseup:_onMouse,onmouseenter:_onMouse,onmouseleave:_onMouse\"\n\t\t\t><div class=\"dijitArrowButtonInner\">&thinsp;</div\n\t\t\t><div class=\"dijitArrowButtonChar\">&#9660;</div\n\t\t></div\n\t\t><div class=\"dijitReset dijitValidationIcon\"><br></div\n\t\t><div class=\"dijitReset dijitValidationIconText\">&Chi;</div\n\t\t><div class=\"dijitReset dijitInputField\"\n\t\t\t><input ${nameAttrSetting} type=\"text\" autocomplete=\"off\" class='dijitReset'\n\t\t\tdojoAttachEvent=\"onkeypress:_onKeyPress,compositionend\"\n\t\t\tdojoAttachPoint=\"textbox,focusNode\" waiRole=\"textbox\" waiState=\"haspopup-true,autocomplete-list\"\n\t\t/></div\n\t></div\n></div>\n",baseClass:"dijitComboBox",_getCaretPos:function(_1){
var _2=0;
if(typeof (_1.selectionStart)=="number"){
_2=_1.selectionStart;
}else{
if(dojo.isIE){
var tr=dojo.doc.selection.createRange().duplicate();
var _4=_1.createTextRange();
tr.move("character",0);
_4.move("character",0);
try{
_4.setEndPoint("EndToEnd",tr);
_2=String(_4.text).replace(/\r/g,"").length;
}
catch(e){
}
}
}
return _2;
},_setCaretPos:function(_5,_6){
_6=parseInt(_6);
dijit.selectInputText(_5,_6,_6);
},_setDisabledAttr:function(_7){
this.inherited(arguments);
dijit.setWaiState(this.comboNode,"disabled",_7);
},_onKeyPress:function(_8){
var _9=_8.charOrCode;
if(_8.altKey||(_8.ctrlKey&&(_9!="x"&&_9!="v"))||_8.key==dojo.keys.SHIFT){
return;
}
var _a=false;
var pw=this._popupWidget;
var dk=dojo.keys;
var _d=null;
if(this._isShowingNow){
pw.handleKey(_9);
_d=pw.getHighlightedOption();
}
switch(_9){
case dk.PAGE_DOWN:
case dk.DOWN_ARROW:
if(!this._isShowingNow||this._prev_key_esc){
this._arrowPressed();
_a=true;
}else{
if(_d){
this._announceOption(_d);
}
}
dojo.stopEvent(_8);
this._prev_key_backspace=false;
this._prev_key_esc=false;
break;
case dk.PAGE_UP:
case dk.UP_ARROW:
if(this._isShowingNow){
this._announceOption(_d);
}
dojo.stopEvent(_8);
this._prev_key_backspace=false;
this._prev_key_esc=false;
break;
case dk.ENTER:
if(_d){
if(_d==pw.nextButton){
this._nextSearch(1);
dojo.stopEvent(_8);
break;
}else{
if(_d==pw.previousButton){
this._nextSearch(-1);
dojo.stopEvent(_8);
break;
}
}
}else{
this._setDisplayedValueAttr(this.attr("displayedValue"),true);
}
_8.preventDefault();
case dk.TAB:
var _e=this.attr("displayedValue");
if(pw&&(_e==pw._messages["previousMessage"]||_e==pw._messages["nextMessage"])){
break;
}
if(this._isShowingNow){
this._prev_key_backspace=false;
this._prev_key_esc=false;
if(_d){
pw.attr("value",{target:_d});
}
this._lastQuery=null;
this._hideResultList();
}
break;
case " ":
this._prev_key_backspace=false;
this._prev_key_esc=false;
if(_d){
dojo.stopEvent(_8);
this._selectOption();
this._hideResultList();
}else{
_a=true;
}
break;
case dk.ESCAPE:
this._prev_key_backspace=false;
this._prev_key_esc=true;
if(this._isShowingNow){
dojo.stopEvent(_8);
this._hideResultList();
}
break;
case dk.DELETE:
case dk.BACKSPACE:
this._prev_key_esc=false;
this._prev_key_backspace=true;
_a=true;
break;
case dk.RIGHT_ARROW:
case dk.LEFT_ARROW:
this._prev_key_backspace=false;
this._prev_key_esc=false;
break;
default:
this._prev_key_backspace=false;
this._prev_key_esc=false;
_a=typeof _9=="string";
}
if(this.searchTimer){
clearTimeout(this.searchTimer);
}
if(_a){
setTimeout(dojo.hitch(this,"_startSearchFromInput"),1);
}
},_autoCompleteText:function(_f){
var fn=this.focusNode;
dijit.selectInputText(fn,fn.value.length);
var _11=this.ignoreCase?"toLowerCase":"substr";
if(_f[_11](0).indexOf(this.focusNode.value[_11](0))==0){
var _12=this._getCaretPos(fn);
if((_12+1)>fn.value.length){
fn.value=_f;
dijit.selectInputText(fn,_12);
}
}else{
fn.value=_f;
dijit.selectInputText(fn);
}
},_openResultList:function(_13,_14){
if(this.disabled||this.readOnly||(_14.query[this.searchAttr]!=this._lastQuery)){
return;
}
this._popupWidget.clearResultList();
if(!_13.length){
this._hideResultList();
return;
}
this.item=null;
var _15=new String(this.store.getValue(_13[0],this.searchAttr));
if(_15&&this.autoComplete&&!this._prev_key_backspace&&(_14.query[this.searchAttr]!="*")){
this.item=_13[0];
this._autoCompleteText(_15);
}
_14._maxOptions=this._maxOptions;
this._popupWidget.createOptions(_13,_14,dojo.hitch(this,"_getMenuLabelFromItem"));
this._showResultList();
if(_14.direction){
if(1==_14.direction){
this._popupWidget.highlightFirstOption();
}else{
if(-1==_14.direction){
this._popupWidget.highlightLastOption();
}
}
this._announceOption(this._popupWidget.getHighlightedOption());
}
},_showResultList:function(){
this._hideResultList();
var _16=this._popupWidget.getItems(),_17=Math.min(_16.length,this.maxListLength);
this._arrowPressed();
this.displayMessage("");
dojo.style(this._popupWidget.domNode,{width:"",height:""});
var _18=this.open();
var _19=dojo.marginBox(this._popupWidget.domNode);
this._popupWidget.domNode.style.overflow=((_18.h==_19.h)&&(_18.w==_19.w))?"hidden":"auto";
var _1a=_18.w;
if(_18.h<this._popupWidget.domNode.scrollHeight){
_1a+=16;
}
dojo.marginBox(this._popupWidget.domNode,{h:_18.h,w:Math.max(_1a,this.domNode.offsetWidth)});
dijit.setWaiState(this.comboNode,"expanded","true");
},_hideResultList:function(){
if(this._isShowingNow){
dijit.popup.close(this._popupWidget);
this._arrowIdle();
this._isShowingNow=false;
dijit.setWaiState(this.comboNode,"expanded","false");
dijit.removeWaiState(this.focusNode,"activedescendant");
}
},_setBlurValue:function(){
var _1b=this.attr("displayedValue");
var pw=this._popupWidget;
if(pw&&(_1b==pw._messages["previousMessage"]||_1b==pw._messages["nextMessage"])){
this._setValueAttr(this._lastValueReported,true);
}else{
this.attr("displayedValue",_1b);
}
},_onBlur:function(){
this._hideResultList();
this._arrowIdle();
this.inherited(arguments);
},_announceOption:function(_1d){
if(_1d==null){
return;
}
var _1e;
if(_1d==this._popupWidget.nextButton||_1d==this._popupWidget.previousButton){
_1e=_1d.innerHTML;
}else{
_1e=this.store.getValue(_1d.item,this.searchAttr);
}
this.focusNode.value=this.focusNode.value.substring(0,this._getCaretPos(this.focusNode));
dijit.setWaiState(this.focusNode,"activedescendant",dojo.attr(_1d,"id"));
this._autoCompleteText(_1e);
},_selectOption:function(evt){
var tgt=null;
if(!evt){
evt={target:this._popupWidget.getHighlightedOption()};
}
if(!evt.target){
this.attr("displayedValue",this.attr("displayedValue"));
return;
}else{
tgt=evt.target;
}
if(!evt.noHide){
this._hideResultList();
this._setCaretPos(this.focusNode,this.store.getValue(tgt.item,this.searchAttr).length);
}
this._doSelect(tgt);
},_doSelect:function(tgt){
this.item=tgt.item;
this.attr("value",this.store.getValue(tgt.item,this.searchAttr));
},_onArrowMouseDown:function(evt){
if(this.disabled||this.readOnly){
return;
}
dojo.stopEvent(evt);
this.focus();
if(this._isShowingNow){
this._hideResultList();
}else{
this._startSearch("");
}
},_startSearchFromInput:function(){
this._startSearch(this.focusNode.value.replace(/([\\\*\?])/g,"\\$1"));
},_getQueryString:function(_23){
return dojo.string.substitute(this.queryExpr,[_23]);
},_startSearch:function(key){
if(!this._popupWidget){
var _25=this.id+"_popup";
this._popupWidget=new dijit.form._ComboBoxMenu({onChange:dojo.hitch(this,this._selectOption),id:_25});
dijit.removeWaiState(this.focusNode,"activedescendant");
dijit.setWaiState(this.textbox,"owns",_25);
}
this.item=null;
var _26=dojo.clone(this.query);
this._lastInput=key;
this._lastQuery=_26[this.searchAttr]=this._getQueryString(key);
this.searchTimer=setTimeout(dojo.hitch(this,function(_27,_28){
var _29={queryOptions:{ignoreCase:this.ignoreCase,deep:true},query:_27,onBegin:dojo.hitch(this,"_setMaxOptions"),onComplete:dojo.hitch(this,"_openResultList"),onError:function(_2a){
console.error("dijit.form.ComboBox: "+_2a);
dojo.hitch(_28,"_hideResultList")();
},start:0,count:this.pageSize};
dojo.mixin(_29,_28.fetchProperties);
var _2b=_28.store.fetch(_29);
var _2c=function(_2d,_2e){
_2d.start+=_2d.count*_2e;
_2d.direction=_2e;
this.store.fetch(_2d);
};
this._nextSearch=this._popupWidget.onPage=dojo.hitch(this,_2c,_2b);
},_26,this),this.searchDelay);
},_setMaxOptions:function(_2f,_30){
this._maxOptions=_2f;
},_getValueField:function(){
return this.searchAttr;
},_arrowPressed:function(){
if(!this.disabled&&!this.readOnly&&this.hasDownArrow){
dojo.addClass(this.downArrowNode,"dijitArrowButtonActive");
}
},_arrowIdle:function(){
if(!this.disabled&&!this.readOnly&&this.hasDownArrow){
dojo.removeClass(this.downArrowNode,"dojoArrowButtonPushed");
}
},compositionend:function(evt){
this._onKeyPress({charCode:-1});
},constructor:function(){
this.query={};
this.fetchProperties={};
},postMixInProperties:function(){
if(!this.hasDownArrow){
this.baseClass="dijitTextBox";
}
if(!this.store){
var _32=this.srcNodeRef;
this.store=new dijit.form._ComboBoxDataStore(_32);
if(!this.value||((typeof _32.selectedIndex=="number")&&_32.selectedIndex.toString()===this.value)){
var _33=this.store.fetchSelectedItem();
if(_33){
this.value=this.store.getValue(_33,this._getValueField());
}
}
}
this.inherited(arguments);
},postCreate:function(){
var _34=dojo.query("label[for=\""+this.id+"\"]");
if(_34.length){
_34[0].id=(this.id+"_label");
var cn=this.comboNode;
dijit.setWaiState(cn,"labelledby",_34[0].id);
}
this.inherited(arguments);
},uninitialize:function(){
if(this._popupWidget){
this._hideResultList();
this._popupWidget.destroy();
}
},_getMenuLabelFromItem:function(_36){
var _37=this.store.getValue(_36,this.labelAttr||this.searchAttr);
var _38=this.labelType;
if(this.highlightMatch!="none"&&this.labelType=="text"&&this._lastInput){
_37=this.doHighlight(_37,this._escapeHtml(this._lastInput));
_38="html";
}
return {html:_38=="html",label:_37};
},doHighlight:function(_39,_3a){
var _3b="i"+(this.highlightMatch=="all"?"g":"");
var _3c=this._escapeHtml(_39);
_3a=dojo.regexp.escapeString(_3a);
var ret=_3c.replace(new RegExp("(^|\\s)("+_3a+")",_3b),"$1<span class=\"dijitComboBoxHighlightMatch\">$2</span>");
return ret;
},_escapeHtml:function(str){
str=String(str).replace(/&/gm,"&amp;").replace(/</gm,"&lt;").replace(/>/gm,"&gt;").replace(/"/gm,"&quot;");
return str;
},open:function(){
this._isShowingNow=true;
return dijit.popup.open({popup:this._popupWidget,around:this.domNode,parent:this});
},reset:function(){
this.item=null;
this.inherited(arguments);
}});
dojo.declare("dijit.form._ComboBoxMenu",[dijit._Widget,dijit._Templated],{templateString:"<ul class='dijitReset dijitMenu' dojoAttachEvent='onmousedown:_onMouseDown,onmouseup:_onMouseUp,onmouseover:_onMouseOver,onmouseout:_onMouseOut' tabIndex='-1' style='overflow: \"auto\"; overflow-x: \"hidden\";'>"+"<li class='dijitMenuItem dijitMenuPreviousButton' dojoAttachPoint='previousButton' waiRole='option'></li>"+"<li class='dijitMenuItem dijitMenuNextButton' dojoAttachPoint='nextButton' waiRole='option'></li>"+"</ul>",_messages:null,postMixInProperties:function(){
this._messages=dojo.i18n.getLocalization("dijit.form","ComboBox",this.lang);
this.inherited(arguments);
},_setValueAttr:function(_3f){
this.value=_3f;
this.onChange(_3f);
},onChange:function(_40){
},onPage:function(_41){
},postCreate:function(){
this.previousButton.innerHTML=this._messages["previousMessage"];
this.nextButton.innerHTML=this._messages["nextMessage"];
this.inherited(arguments);
},onClose:function(){
this._blurOptionNode();
},_createOption:function(_42,_43){
var _44=_43(_42);
var _45=dojo.doc.createElement("li");
dijit.setWaiRole(_45,"option");
if(_44.html){
_45.innerHTML=_44.label;
}else{
_45.appendChild(dojo.doc.createTextNode(_44.label));
}
if(_45.innerHTML==""){
_45.innerHTML="&nbsp;";
}
_45.item=_42;
return _45;
},createOptions:function(_46,_47,_48){
this.previousButton.style.display=(_47.start==0)?"none":"";
dojo.attr(this.previousButton,"id",this.id+"_prev");
dojo.forEach(_46,function(_49,i){
var _4b=this._createOption(_49,_48);
_4b.className="dijitReset dijitMenuItem";
dojo.attr(_4b,"id",this.id+i);
this.domNode.insertBefore(_4b,this.nextButton);
},this);
var _4c=false;
if(_47._maxOptions&&_47._maxOptions!=-1){
if((_47.start+_47.count)<_47._maxOptions){
_4c=true;
}else{
if((_47.start+_47.count)>(_47._maxOptions-1)){
if(_47.count==_46.length){
_4c=true;
}
}
}
}else{
if(_47.count==_46.length){
_4c=true;
}
}
this.nextButton.style.display=_4c?"":"none";
dojo.attr(this.nextButton,"id",this.id+"_next");
},clearResultList:function(){
while(this.domNode.childNodes.length>2){
this.domNode.removeChild(this.domNode.childNodes[this.domNode.childNodes.length-2]);
}
},getItems:function(){
return this.domNode.childNodes;
},getListLength:function(){
return this.domNode.childNodes.length-2;
},_onMouseDown:function(evt){
dojo.stopEvent(evt);
},_onMouseUp:function(evt){
if(evt.target===this.domNode){
return;
}else{
if(evt.target==this.previousButton){
this.onPage(-1);
}else{
if(evt.target==this.nextButton){
this.onPage(1);
}else{
var tgt=evt.target;
while(!tgt.item){
tgt=tgt.parentNode;
}
this._setValueAttr({target:tgt},true);
}
}
}
},_onMouseOver:function(evt){
if(evt.target===this.domNode){
return;
}
var tgt=evt.target;
if(!(tgt==this.previousButton||tgt==this.nextButton)){
while(!tgt.item){
tgt=tgt.parentNode;
}
}
this._focusOptionNode(tgt);
},_onMouseOut:function(evt){
if(evt.target===this.domNode){
return;
}
this._blurOptionNode();
},_focusOptionNode:function(_53){
if(this._highlighted_option!=_53){
this._blurOptionNode();
this._highlighted_option=_53;
dojo.addClass(this._highlighted_option,"dijitMenuItemSelected");
}
},_blurOptionNode:function(){
if(this._highlighted_option){
dojo.removeClass(this._highlighted_option,"dijitMenuItemSelected");
this._highlighted_option=null;
}
},_highlightNextOption:function(){
var fc=this.domNode.firstChild;
if(!this.getHighlightedOption()){
this._focusOptionNode(fc.style.display=="none"?fc.nextSibling:fc);
}else{
var ns=this._highlighted_option.nextSibling;
if(ns&&ns.style.display!="none"){
this._focusOptionNode(ns);
}
}
dijit.scrollIntoView(this._highlighted_option);
},highlightFirstOption:function(){
this._focusOptionNode(this.domNode.firstChild.nextSibling);
dijit.scrollIntoView(this._highlighted_option);
},highlightLastOption:function(){
this._focusOptionNode(this.domNode.lastChild.previousSibling);
dijit.scrollIntoView(this._highlighted_option);
},_highlightPrevOption:function(){
var lc=this.domNode.lastChild;
if(!this.getHighlightedOption()){
this._focusOptionNode(lc.style.display=="none"?lc.previousSibling:lc);
}else{
var ps=this._highlighted_option.previousSibling;
if(ps&&ps.style.display!="none"){
this._focusOptionNode(ps);
}
}
dijit.scrollIntoView(this._highlighted_option);
},_page:function(up){
var _59=0;
var _5a=this.domNode.scrollTop;
var _5b=dojo.style(this.domNode,"height");
if(!this.getHighlightedOption()){
this._highlightNextOption();
}
while(_59<_5b){
if(up){
if(!this.getHighlightedOption().previousSibling||this._highlighted_option.previousSibling.style.display=="none"){
break;
}
this._highlightPrevOption();
}else{
if(!this.getHighlightedOption().nextSibling||this._highlighted_option.nextSibling.style.display=="none"){
break;
}
this._highlightNextOption();
}
var _5c=this.domNode.scrollTop;
_59+=(_5c-_5a)*(up?-1:1);
_5a=_5c;
}
},pageUp:function(){
this._page(true);
},pageDown:function(){
this._page(false);
},getHighlightedOption:function(){
var ho=this._highlighted_option;
return (ho&&ho.parentNode)?ho:null;
},handleKey:function(key){
switch(key){
case dojo.keys.DOWN_ARROW:
this._highlightNextOption();
break;
case dojo.keys.PAGE_DOWN:
this.pageDown();
break;
case dojo.keys.UP_ARROW:
this._highlightPrevOption();
break;
case dojo.keys.PAGE_UP:
this.pageUp();
break;
}
}});
dojo.declare("dijit.form.ComboBox",[dijit.form.ValidationTextBox,dijit.form.ComboBoxMixin],{_setValueAttr:function(_5f,_60){
if(!_5f){
_5f="";
}
dijit.form.ValidationTextBox.prototype._setValueAttr.call(this,_5f,_60);
}});
dojo.declare("dijit.form._ComboBoxDataStore",null,{constructor:function(_61){
this.root=_61;
dojo.query("> option",_61).forEach(function(_62){
_62.innerHTML=dojo.trim(_62.innerHTML);
});
},getValue:function(_63,_64,_65){
return (_64=="value")?_63.value:(_63.innerText||_63.textContent||"");
},isItemLoaded:function(_66){
return true;
},getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
},_fetchItems:function(_67,_68,_69){
if(!_67.query){
_67.query={};
}
if(!_67.query.name){
_67.query.name="";
}
if(!_67.queryOptions){
_67.queryOptions={};
}
var _6a=dojo.data.util.filter.patternToRegExp(_67.query.name,_67.queryOptions.ignoreCase),_6b=dojo.query("> option",this.root).filter(function(_6c){
return (_6c.innerText||_6c.textContent||"").match(_6a);
});
if(_67.sort){
_6b.sort(dojo.data.util.sorter.createSortFunction(_67.sort,this));
}
_68(_6b,_67);
},close:function(_6d){
return;
},getLabel:function(_6e){
return _6e.innerHTML;
},getIdentity:function(_6f){
return dojo.attr(_6f,"value");
},fetchItemByIdentity:function(_70){
var _71=dojo.query("option[value='"+_70.identity+"']",this.root)[0];
_70.onItem(_71);
},fetchSelectedItem:function(){
var _72=this.root,si=_72.selectedIndex;
return dojo.query("> option:nth-child("+(si!=-1?si+1:1)+")",_72)[0];
}});
dojo.extend(dijit.form._ComboBoxDataStore,dojo.data.util.simpleFetch);
}
