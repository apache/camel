/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._TimePicker"]){
dojo._hasResource["dijit._TimePicker"]=true;
dojo.provide("dijit._TimePicker");
dojo.require("dijit.form._FormWidget");
dojo.require("dojo.date.locale");
dojo.declare("dijit._TimePicker",[dijit._Widget,dijit._Templated],{templateString:"<div id=\"widget_${id}\" class=\"dijitMenu ${baseClass}\"\n    ><div dojoAttachPoint=\"upArrow\" class=\"dijitButtonNode dijitUpArrowButton\" dojoAttachEvent=\"onmouseenter:_buttonMouse,onmouseleave:_buttonMouse\"\n\t\t><div class=\"dijitReset dijitInline dijitArrowButtonInner\" wairole=\"presentation\" role=\"presentation\">&nbsp;</div\n\t\t><div class=\"dijitArrowButtonChar\">&#9650;</div></div\n    ><div dojoAttachPoint=\"timeMenu,focusNode\" dojoAttachEvent=\"onclick:_onOptionSelected,onmouseover,onmouseout\"></div\n    ><div dojoAttachPoint=\"downArrow\" class=\"dijitButtonNode dijitDownArrowButton\" dojoAttachEvent=\"onmouseenter:_buttonMouse,onmouseleave:_buttonMouse\"\n\t\t><div class=\"dijitReset dijitInline dijitArrowButtonInner\" wairole=\"presentation\" role=\"presentation\">&nbsp;</div\n\t\t><div class=\"dijitArrowButtonChar\">&#9660;</div></div\n></div>\n",baseClass:"dijitTimePicker",clickableIncrement:"T00:15:00",visibleIncrement:"T01:00:00",visibleRange:"T05:00:00",value:new Date(),_visibleIncrement:2,_clickableIncrement:1,_totalIncrements:10,constraints:{},serialize:dojo.date.stamp.toISOString,_filterString:"",setValue:function(_1){
dojo.deprecated("dijit._TimePicker:setValue() is deprecated.  Use attr('value') instead.","","2.0");
this.attr("value",_1);
},_setValueAttr:function(_2){
this.value=_2;
this._showText();
},onOpen:function(_3){
if(this._beenOpened&&this.domNode.parentNode){
var p=dijit.byId(this.domNode.parentNode.dijitPopupParent);
if(p){
var _5=p.getDisplayedValue();
if(_5&&!p.parse(_5,p.constraints)){
this._filterString=_5;
}else{
this._filterString="";
}
this._showText();
}
}
this._beenOpened=true;
},isDisabledDate:function(_6,_7){
return false;
},_getFilteredNodes:function(_8,_9,_a){
var _b=[],n,i=_8,_e=this._maxIncrement+Math.abs(i),_f=_a?-1:1,dec=_a?1:0,inc=_a?0:1;
do{
i=i-dec;
n=this._createOption(i);
if(n){
_b.push(n);
}
i=i+inc;
}while(_b.length<_9&&(i*_f)<_e);
if(_a){
_b.reverse();
}
return _b;
},_showText:function(){
this.timeMenu.innerHTML="";
var _12=dojo.date.stamp.fromISOString;
this._clickableIncrementDate=_12(this.clickableIncrement);
this._visibleIncrementDate=_12(this.visibleIncrement);
this._visibleRangeDate=_12(this.visibleRange);
var _13=function(_14){
return _14.getHours()*60*60+_14.getMinutes()*60+_14.getSeconds();
};
var _15=_13(this._clickableIncrementDate);
var _16=_13(this._visibleIncrementDate);
var _17=_13(this._visibleRangeDate);
var _18=this.value.getTime();
this._refDate=new Date(_18-_18%(_16*1000));
this._refDate.setFullYear(1970,0,1);
this._clickableIncrement=1;
this._totalIncrements=_17/_15;
this._visibleIncrement=_16/_15;
this._maxIncrement=(60*60*24)/_15;
var _19=this._getFilteredNodes(0,this._totalIncrements>>1,true);
var _1a=this._getFilteredNodes(0,this._totalIncrements>>1,false);
if(_19.length<this._totalIncrements>>1){
_19=_19.slice(_19.length/2);
_1a=_1a.slice(0,_1a.length/2);
}
dojo.forEach(_19.concat(_1a),function(n){
this.timeMenu.appendChild(n);
},this);
},postCreate:function(){
if(this.constraints===dijit._TimePicker.prototype.constraints){
this.constraints={};
}
dojo.mixin(this,this.constraints);
if(!this.constraints.locale){
this.constraints.locale=this.lang;
}
this.connect(this.timeMenu,dojo.isIE?"onmousewheel":"DOMMouseScroll","_mouseWheeled");
var _1c=this;
var _1d=function(){
_1c._connects.push(dijit.typematic.addMouseListener.apply(null,arguments));
};
_1d(this.upArrow,this,this._onArrowUp,1,50);
_1d(this.downArrow,this,this._onArrowDown,1,50);
var _1e=function(cb){
return function(cnt){
if(cnt>0){
cb.call(this,arguments);
}
};
};
var _21=function(_22,cb){
return function(e){
dojo.stopEvent(e);
dijit.typematic.trigger(e,this,_22,_1e(cb),_22,1,50);
};
};
this.connect(this.upArrow,"onmouseover",_21(this.upArrow,this._onArrowUp));
this.connect(this.downArrow,"onmouseover",_21(this.downArrow,this._onArrowDown));
this.inherited(arguments);
},_buttonMouse:function(e){
dojo.toggleClass(e.currentTarget,"dijitButtonNodeHover",e.type=="mouseover");
},_createOption:function(_26){
var _27=new Date(this._refDate);
var _28=this._clickableIncrementDate;
_27.setHours(_27.getHours()+_28.getHours()*_26,_27.getMinutes()+_28.getMinutes()*_26,_27.getSeconds()+_28.getSeconds()*_26);
var _29=dojo.date.locale.format(_27,this.constraints);
if(this._filterString&&_29.toLowerCase().indexOf(this._filterString)!==0){
return null;
}
var div=dojo.create("div",{"class":this.baseClass+"Item"});
div.date=_27;
div.index=_26;
dojo.create("div",{"class":this.baseClass+"ItemInner",innerHTML:_29},div);
if(_26%this._visibleIncrement<1&&_26%this._visibleIncrement>-1){
dojo.addClass(div,this.baseClass+"Marker");
}else{
if(!(_26%this._clickableIncrement)){
dojo.addClass(div,this.baseClass+"Tick");
}
}
if(this.isDisabledDate(_27)){
dojo.addClass(div,this.baseClass+"ItemDisabled");
}
if(!dojo.date.compare(this.value,_27,this.constraints.selector)){
div.selected=true;
dojo.addClass(div,this.baseClass+"ItemSelected");
if(dojo.hasClass(div,this.baseClass+"Marker")){
dojo.addClass(div,this.baseClass+"MarkerSelected");
}else{
dojo.addClass(div,this.baseClass+"TickSelected");
}
}
return div;
},_onOptionSelected:function(tgt){
var _2c=tgt.target.date||tgt.target.parentNode.date;
if(!_2c||this.isDisabledDate(_2c)){
return;
}
this._highlighted_option=null;
this.attr("value",_2c);
this.onValueSelected(_2c);
},onValueSelected:function(_2d){
},_highlightOption:function(_2e,_2f){
if(!_2e){
return;
}
if(_2f){
if(this._highlighted_option){
this._highlightOption(this._highlighted_option,false);
}
this._highlighted_option=_2e;
}else{
if(this._highlighted_option!==_2e){
return;
}else{
this._highlighted_option=null;
}
}
dojo.toggleClass(_2e,this.baseClass+"ItemHover",_2f);
if(dojo.hasClass(_2e,this.baseClass+"Marker")){
dojo.toggleClass(_2e,this.baseClass+"MarkerHover",_2f);
}else{
dojo.toggleClass(_2e,this.baseClass+"TickHover",_2f);
}
},onmouseover:function(e){
var tgr=(e.target.parentNode===this.timeMenu)?e.target:e.target.parentNode;
if(!dojo.hasClass(tgr,this.baseClass+"Item")){
return;
}
this._highlightOption(tgr,true);
},onmouseout:function(e){
var tgr=(e.target.parentNode===this.timeMenu)?e.target:e.target.parentNode;
this._highlightOption(tgr,false);
},_mouseWheeled:function(e){
dojo.stopEvent(e);
var _35=(dojo.isIE?e.wheelDelta:-e.detail);
this[(_35>0?"_onArrowUp":"_onArrowDown")]();
},_onArrowUp:function(_36){
if(typeof _36=="number"&&_36==-1){
return;
}
if(!this.timeMenu.childNodes.length){
return;
}
var _37=this.timeMenu.childNodes[0].index;
var _38=this._getFilteredNodes(_37,1,true);
if(_38.length){
this.timeMenu.removeChild(this.timeMenu.childNodes[this.timeMenu.childNodes.length-1]);
this.timeMenu.insertBefore(_38[0],this.timeMenu.childNodes[0]);
}
},_onArrowDown:function(_39){
if(typeof _39=="number"&&_39==-1){
return;
}
if(!this.timeMenu.childNodes.length){
return;
}
var _3a=this.timeMenu.childNodes[this.timeMenu.childNodes.length-1].index+1;
var _3b=this._getFilteredNodes(_3a,1,false);
if(_3b.length){
this.timeMenu.removeChild(this.timeMenu.childNodes[0]);
this.timeMenu.appendChild(_3b[0]);
}
},handleKey:function(e){
var dk=dojo.keys;
if(e.keyChar||e.charOrCode===dk.BACKSPACE||e.charOrCode==dk.DELETE){
setTimeout(dojo.hitch(this,function(){
this._filterString=e.target.value.toLowerCase();
this._showText();
}),1);
}else{
if(e.charOrCode==dk.DOWN_ARROW||e.charOrCode==dk.UP_ARROW){
dojo.stopEvent(e);
if(this._highlighted_option&&!this._highlighted_option.parentNode){
this._highlighted_option=null;
}
var _3e=this.timeMenu,tgt=this._highlighted_option||dojo.query("."+this.baseClass+"ItemSelected",_3e)[0];
if(!tgt){
tgt=_3e.childNodes[0];
}else{
if(_3e.childNodes.length){
if(e.charOrCode==dk.DOWN_ARROW&&!tgt.nextSibling){
this._onArrowDown();
}else{
if(e.charOrCode==dk.UP_ARROW&&!tgt.previousSibling){
this._onArrowUp();
}
}
if(e.charOrCode==dk.DOWN_ARROW){
tgt=tgt.nextSibling;
}else{
tgt=tgt.previousSibling;
}
}
}
this._highlightOption(tgt,true);
}else{
if(this._highlighted_option&&(e.charOrCode==dk.ENTER||e.charOrCode===dk.TAB)){
if(e.charOrCode==dk.ENTER){
dojo.stopEvent(e);
}
setTimeout(dojo.hitch(this,function(){
this._onOptionSelected({target:this._highlighted_option});
}),1);
}
}
}
}});
}
