/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.secure.DOM"]){
dojo._hasResource["dojox.secure.DOM"]=true;
dojo.provide("dojox.secure.DOM");
dojo.require("dojox.lang.observable");
dojox.secure.DOM=function(_1){
function _2(_3){
if(!_3){
return _3;
}
var _4=_3;
do{
if(_4==_1){
return _5(_3);
}
}while((_4=_4.parentNode));
return null;
};
function _5(_6){
if(_6){
if(_6.nodeType){
var _7=_8(_6);
if(_6.nodeType==1&&typeof _7.style=="function"){
_7.style=_9(_6.style);
_7.ownerDocument=_a;
_7.childNodes={__get__:function(i){
return _5(_6.childNodes[i]);
},length:0};
}
return _7;
}
if(_6&&typeof _6=="object"){
if(_6.__observable){
return _6.__observable;
}
_7=_6 instanceof Array?[]:{};
_6.__observable=_7;
for(var i in _6){
if(i!="__observable"){
_7[i]=_5(_6[i]);
}
}
_7.data__=_6;
return _7;
}
if(typeof _6=="function"){
var _d=function(_e){
if(typeof _e=="function"){
return function(){
for(var i=0;i<arguments.length;i++){
arguments[i]=_5(arguments[i]);
}
return _d(_e.apply(_5(this),arguments));
};
}
return dojox.secure.unwrap(_e);
};
return function(){
if(_6.safetyCheck){
_6.safetyCheck.apply(_d(this),arguments);
}
for(var i=0;i<arguments.length;i++){
arguments[i]=_d(arguments[i]);
}
return _5(_6.apply(_d(this),arguments));
};
}
}
return _6;
};
unwrap=dojox.secure.unwrap;
function _11(css){
css+="";
if(css.match(/behavior:|content:|javascript:|binding|expression|\@import/)){
throw new Error("Illegal CSS");
}
var id=_1.id||(_1.id="safe"+(""+Math.random()).substring(2));
return css.replace(/(\}|^)\s*([^\{]*\{)/g,function(t,a,b){
return a+" #"+id+" "+b;
});
};
function _17(url){
if(url.match(/:/)&&!url.match(/^(http|ftp|mailto)/)){
throw new Error("Unsafe URL "+url);
}
};
function _19(el){
if(el&&el.nodeType==1){
if(el.tagName.match(/script/i)){
var src=el.src;
if(src&&src!=""){
el.parentNode.removeChild(el);
dojo.xhrGet({url:src,secure:true}).addCallback(function(_1c){
_a.evaluate(_1c);
});
}else{
var _1d=el.innerHTML;
el.parentNode.removeChild(el);
_5.evaluate(_1d);
}
}
if(el.tagName.match(/link/i)){
throw new Error("illegal tag");
}
if(el.tagName.match(/style/i)){
var _1e=function(_1f){
if(el.styleSheet){
el.styleSheet.cssText=_1f;
}else{
var _20=doc.createTextNode(_1f);
if(el.childNodes[0]){
el.replaceChild(_20,el.childNodes[0]);
}else{
el.appendChild(_20);
}
}
};
src=el.src;
if(src&&src!=""){
alert("src"+src);
el.src=null;
dojo.xhrGet({url:src,secure:true}).addCallback(function(_21){
_1e(_11(_21));
});
}
_1e(_11(el.innerHTML));
}
if(el.style){
_11(el.style.cssText);
}
if(el.href){
_17(el.href);
}
if(el.src){
_17(el.src);
}
var _22,i=0;
while((_22=el.attributes[i++])){
if(_22.name.substring(0,2)=="on"&&_22.value!="null"&&_22.value!=""){
throw new Error("event handlers not allowed in the HTML, they must be set with element.addEventListener");
}
}
var _24=el.childNodes;
for(var i=0,l=_24.length;i<l;i++){
_19(_24[i]);
}
}
};
function _26(_27){
var div=document.createElement("div");
if(_27.match(/<object/i)){
throw new Error("The object tag is not allowed");
}
div.innerHTML=_27;
_19(div);
return div;
};
var doc=_1.ownerDocument;
var _a={getElementById:function(id){
return _2(doc.getElementById(id));
},createElement:function(_2b){
return _5(doc.createElement(_2b));
},createTextNode:function(_2c){
return _5(doc.createTextNode(_2c));
},write:function(str){
var div=_26(str);
while(div.childNodes.length){
_1.appendChild(div.childNodes[0]);
}
}};
_a.open=_a.close=function(){
};
var _2f={innerHTML:function(_30,_31){

_30.innerHTML=_26(_31).innerHTML;
}};
_2f.outerHTML=function(_32,_33){
throw new Error("Can not set this property");
};
function _34(_35,_36){
return function(_37,_38){
_19(_38[_36]);
return _37[_35](_38[0]);
};
};
var _39={appendChild:_34("appendChild",0),insertBefore:_34("insertBefore",0),replaceChild:_34("replaceChild",1),cloneNode:function(_3a,_3b){
return _3a.cloneNode(_3b[0]);
},addEventListener:function(_3c,_3d){
dojo.connect(_3c,"on"+_3d[0],this,function(_3e){
_3e=_8(_3e||window.event);
_3d[1].call(this,_3e);
});
}};
_39.childNodes=_39.style=_39.ownerDocument=function(){
};
function _3f(_40){
return dojox.lang.makeObservable(function(_41,_42){
var _43;
return _41[_42];
},_40,function(_44,_45,_46,_47){
for(var i=0;i<_47.length;i++){
_47[i]=unwrap(_47[i]);
}
if(_39[_46]){
return _5(_39[_46].call(_44,_45,_47));
}
return _5(_45[_46].apply(_45,_47));
},_39);
};
var _8=_3f(function(_49,_4a,_4b){
if(_2f[_4a]){
_2f[_4a](_49,_4b);
}
_49[_4a]=_4b;
});
var _4c={behavior:1,MozBinding:1};
var _9=_3f(function(_4d,_4e,_4f){
if(!_4c[_4e]){
_4d[_4e]=_11(_4f);
}
});
_5.safeHTML=_26;
_5.safeCSS=_11;
return _5;
};
dojox.secure.unwrap=function unwrap(_50){
return (_50&&_50.data__)||_50;
};
}
