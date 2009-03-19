/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xml.DomParser"]){
dojo._hasResource["dojox.xml.DomParser"]=true;
dojo.provide("dojox.xml.DomParser");
dojox.xml.DomParser=new (function(){
var _1={ELEMENT:1,ATTRIBUTE:2,TEXT:3,CDATA_SECTION:4,PROCESSING_INSTRUCTION:7,COMMENT:8,DOCUMENT:9};
var _2=/<([^>\/\s+]*)([^>]*)>([^<]*)/g;
var _3=/([^=]*)=(("([^"]*)")|('([^']*)'))/g;
var _4=/<!ENTITY\s+([^"]*)\s+"([^"]*)">/g;
var _5=/<!\[CDATA\[([\u0001-\uFFFF]*?)\]\]>/g;
var _6=/<!--([\u0001-\uFFFF]*?)-->/g;
var _7=/^\s+|\s+$/g;
var _8=/\s+/g;
var _9=/\&gt;/g;
var _a=/\&lt;/g;
var _b=/\&quot;/g;
var _c=/\&apos;/g;
var _d=/\&amp;/g;
var _e="_def_";
function _f(){
return new (function(){
var all={};
this.nodeType=_1.DOCUMENT;
this.nodeName="#document";
this.namespaces={};
this._nsPaths={};
this.childNodes=[];
this.documentElement=null;
this._add=function(obj){
if(typeof (obj.id)!="undefined"){
all[obj.id]=obj;
}
};
this._remove=function(id){
if(all[id]){
delete all[id];
}
};
this.byId=this.getElementById=function(id){
return all[id];
};
this.byName=this.getElementsByTagName=_14;
this.byNameNS=this.getElementsByTagNameNS=_15;
this.childrenByName=_16;
this.childrenByNameNS=_17;
})();
};
function _14(_18){
function __(_1a,_1b,arr){
dojo.forEach(_1a.childNodes,function(c){
if(c.nodeType==_1.ELEMENT){
if(_1b=="*"){
arr.push(c);
}else{
if(c.nodeName==_1b){
arr.push(c);
}
}
__(c,_1b,arr);
}
});
};
var a=[];
__(this,_18,a);
return a;
};
function _15(_1f,ns){
function __(_22,_23,ns,arr){
dojo.forEach(_22.childNodes,function(c){
if(c.nodeType==_1.ELEMENT){
if(_23=="*"&&c.ownerDocument._nsPaths[ns]==c.namespace){
arr.push(c);
}else{
if(c.localName==_23&&c.ownerDocument._nsPaths[ns]==c.namespace){
arr.push(c);
}
}
__(c,_23,ns,arr);
}
});
};
if(!ns){
ns=_e;
}
var a=[];
__(this,_1f,ns,a);
return a;
};
function _16(_28){
var a=[];
dojo.forEach(this.childNodes,function(c){
if(c.nodeType==_1.ELEMENT){
if(_28=="*"){
a.push(c);
}else{
if(c.nodeName==_28){
a.push(c);
}
}
}
});
return a;
};
function _17(_2b,ns){
var a=[];
dojo.forEach(this.childNodes,function(c){
if(c.nodeType==_1.ELEMENT){
if(_2b=="*"&&c.ownerDocument._nsPaths[ns]==c.namespace){
a.push(c);
}else{
if(c.localName==_2b&&c.ownerDocument._nsPaths[ns]==c.namespace){
a.push(c);
}
}
}
});
return a;
};
function _2f(v){
return {nodeType:_1.TEXT,nodeName:"#text",nodeValue:v.replace(_8," ").replace(_9,">").replace(_a,"<").replace(_c,"'").replace(_b,"\"").replace(_d,"&")};
};
function _31(_32){
for(var i=0;i<this.attributes.length;i++){
if(this.attributes[i].nodeName==_32){
return this.attributes[i].nodeValue;
}
}
return null;
};
function _34(_35,ns){
for(var i=0;i<this.attributes.length;i++){
if(this.ownerDocument._nsPaths[ns]==this.attributes[i].namespace&&this.attributes[i].localName==_35){
return this.attributes[i].nodeValue;
}
}
return null;
};
function _38(_39,val){
var old=null;
for(var i=0;i<this.attributes.length;i++){
if(this.attributes[i].nodeName==_39){
old=this.attributes[i].nodeValue;
this.attributes[i].nodeValue=val;
break;
}
}
if(_39=="id"){
if(old!=null){
this.ownerDocument._remove(old);
}
this.ownerDocument._add(this);
}
};
function _3d(_3e,val,ns){
for(var i=0;i<this.attributes.length;i++){
if(this.ownerDocument._nsPaths[ns]==this.attributes[i].namespace&&this.attributes[i].localName==_3e){
this.attributes[i].nodeValue=val;
return;
}
}
};
function _42(){
var p=this.parentNode;
if(p){
for(var i=0;i<p.childNodes.length;i++){
if(p.childNodes[i]==this&&i>0){
return p.childNodes[i-1];
}
}
}
return null;
};
function _45(){
var p=this.parentNode;
if(p){
for(var i=0;i<p.childNodes.length;i++){
if(p.childNodes[i]==this&&(i+1)<p.childNodes.length){
return p.childNodes[i+1];
}
}
}
return null;
};
this.parse=function(str){
var _49=_f();
if(str==null){
return _49;
}
if(str.length==0){
return _49;
}
if(str.indexOf("<!ENTITY")>0){
var _4a,eRe=[];
if(_4.test(str)){
_4.lastIndex=0;
while((_4a=_4.exec(str))!=null){
eRe.push({entity:"&"+_4a[1].replace(_7,"")+";",expression:_4a[2]});
}
for(var i=0;i<eRe.length;i++){
str=str.replace(new RegExp(eRe[i].entity,"g"),eRe[i].expression);
}
}
}
var _4d=[],_4e;
while((_4e=_5.exec(str))!=null){
_4d.push(_4e[1]);
}
for(var i=0;i<_4d.length;i++){
str=str.replace(_4d[i],i);
}
var _4f=[],_50;
while((_50=_6.exec(str))!=null){
_4f.push(_50[1]);
}
for(i=0;i<_4f.length;i++){
str=str.replace(_4f[i],i);
}
var res,obj=_49;
while((res=_2.exec(str))!=null){
if(res[2].charAt(0)=="/"&&res[2].replace(_7,"").length>1){
if(obj.parentNode){
obj=obj.parentNode;
}
var _53=(res[3]||"").replace(_7,"");
if(_53.length>0){
obj.childNodes.push(_2f(_53));
}
}else{
if(res[1].length>0){
if(res[1].charAt(0)=="?"){
var _54=res[1].substr(1);
var _55=res[2].substr(0,res[2].length-2);
obj.childNodes.push({nodeType:_1.PROCESSING_INSTRUCTION,nodeName:_54,nodeValue:_55});
}else{
if(res[1].charAt(0)=="!"){
if(res[1].indexOf("![CDATA[")==0){
var val=parseInt(res[1].replace("![CDATA[","").replace("]]",""));
obj.childNodes.push({nodeType:_1.CDATA_SECTION,nodeName:"#cdata-section",nodeValue:_4d[val]});
}else{
if(res[1].substr(0,3)=="!--"){
var val=parseInt(res[1].replace("!--","").replace("--",""));
obj.childNodes.push({nodeType:_1.COMMENT,nodeName:"#comment",nodeValue:_4f[val]});
}
}
}else{
var _54=res[1].replace(_7,"");
var o={nodeType:_1.ELEMENT,nodeName:_54,localName:_54,namespace:_e,ownerDocument:_49,attributes:[],parentNode:null,childNodes:[]};
if(_54.indexOf(":")>-1){
var t=_54.split(":");
o.namespace=t[0];
o.localName=t[1];
}
o.byName=o.getElementsByTagName=_14;
o.byNameNS=o.getElementsByTagNameNS=_15;
o.childrenByName=_16;
o.childrenByNameNS=_17;
o.getAttribute=_31;
o.getAttributeNS=_34;
o.setAttribute=_38;
o.setAttributeNS=_3d;
o.previous=o.previousSibling=_42;
o.next=o.nextSibling=_45;
var _59;
while((_59=_3.exec(res[2]))!=null){
if(_59.length>0){
var _54=_59[1].replace(_7,"");
var val=(_59[4]||_59[6]||"").replace(_8," ").replace(_9,">").replace(_a,"<").replace(_c,"'").replace(_b,"\"").replace(_d,"&");
if(_54.indexOf("xmlns")==0){
if(_54.indexOf(":")>0){
var ns=_54.split(":");
_49.namespaces[ns[1]]=val;
_49._nsPaths[val]=ns[1];
}else{
_49.namespaces[_e]=val;
_49._nsPaths[val]=_e;
}
}else{
var ln=_54;
var ns=_e;
if(_54.indexOf(":")>0){
var t=_54.split(":");
ln=t[1];
ns=t[0];
}
o.attributes.push({nodeType:_1.ATTRIBUTE,nodeName:_54,localName:ln,namespace:ns,nodeValue:val});
if(ln=="id"){
o.id=val;
}
}
}
}
_49._add(o);
if(obj){
obj.childNodes.push(o);
o.parentNode=obj;
if(res[2].charAt(res[2].length-1)!="/"){
obj=o;
}
}
var _53=res[3];
if(_53.length>0){
obj.childNodes.push(_2f(_53));
}
}
}
}
}
}
for(var i=0;i<_49.childNodes.length;i++){
var e=_49.childNodes[i];
if(e.nodeType==_1.ELEMENT){
_49.documentElement=e;
break;
}
}
return _49;
};
})();
}
