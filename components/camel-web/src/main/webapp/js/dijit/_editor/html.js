/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.html"]){
dojo._hasResource["dijit._editor.html"]=true;
dojo.provide("dijit._editor.html");
dijit._editor.escapeXml=function(_1,_2){
_1=_1.replace(/&/gm,"&amp;").replace(/</gm,"&lt;").replace(/>/gm,"&gt;").replace(/"/gm,"&quot;");
if(!_2){
_1=_1.replace(/'/gm,"&#39;");
}
return _1;
};
dijit._editor.getNodeHtml=function(_3){
var _4;
switch(_3.nodeType){
case 1:
_4="<"+_3.nodeName.toLowerCase();
var _5=[];
if(dojo.isIE&&_3.outerHTML){
var s=_3.outerHTML;
s=s.substr(0,s.indexOf(">")).replace(/(['"])[^"']*\1/g,"");
var _7=/([^\s=]+)=/g;
var m,_9;
while((m=_7.exec(s))){
_9=m[1];
if(_9.substr(0,3)!="_dj"){
if(_9=="src"||_9=="href"){
if(_3.getAttribute("_djrealurl")){
_5.push([_9,_3.getAttribute("_djrealurl")]);
continue;
}
}
var _a;
switch(_9){
case "style":
_a=_3.style.cssText.toLowerCase();
break;
case "class":
_a=_3.className;
break;
default:
_a=_3.getAttribute(_9);
}
_5.push([_9,_a.toString()]);
}
}
}else{
var _b,i=0;
while((_b=_3.attributes[i++])){
var n=_b.name;
if(n.substr(0,3)!="_dj"){
var v=_b.value;
if(n=="src"||n=="href"){
if(_3.getAttribute("_djrealurl")){
v=_3.getAttribute("_djrealurl");
}
}
_5.push([n,v]);
}
}
}
_5.sort(function(a,b){
return a[0]<b[0]?-1:(a[0]==b[0]?0:1);
});
var j=0;
while((_b=_5[j++])){
_4+=" "+_b[0]+"=\""+(dojo.isString(_b[1])?dijit._editor.escapeXml(_b[1],true):_b[1])+"\"";
}
if(_3.childNodes.length){
_4+=">"+dijit._editor.getChildrenHtml(_3)+"</"+_3.nodeName.toLowerCase()+">";
}else{
_4+=" />";
}
break;
case 3:
_4=dijit._editor.escapeXml(_3.nodeValue,true);
break;
case 8:
_4="<!--"+dijit._editor.escapeXml(_3.nodeValue,true)+"-->";
break;
default:
_4="<!-- Element not recognized - Type: "+_3.nodeType+" Name: "+_3.nodeName+"-->";
}
return _4;
};
dijit._editor.getChildrenHtml=function(dom){
var out="";
if(!dom){
return out;
}
var _14=dom["childNodes"]||dom;
var _15=!dojo.isIE||_14!==dom;
var _16,i=0;
while((_16=_14[i++])){
if(!_15||_16.parentNode==dom){
out+=dijit._editor.getNodeHtml(_16);
}
}
return out;
};
}
