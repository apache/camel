/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.XmlWire"]){
dojo._hasResource["dojox.wire.XmlWire"]=true;
dojo.provide("dojox.wire.XmlWire");
dojo.require("dojox.xml.parser");
dojo.require("dojox.wire.Wire");
dojo.declare("dojox.wire.XmlWire",dojox.wire.Wire,{_wireClass:"dojox.wire.XmlWire",constructor:function(_1){
},_getValue:function(_2){
if(!_2||!this.path){
return _2;
}
var _3=_2;
var _4=this.path;
var i;
if(_4.charAt(0)=="/"){
i=_4.indexOf("/",1);
_4=_4.substring(i+1);
}
var _6=_4.split("/");
var _7=_6.length-1;
for(i=0;i<_7;i++){
_3=this._getChildNode(_3,_6[i]);
if(!_3){
return undefined;
}
}
var _8=this._getNodeValue(_3,_6[_7]);
return _8;
},_setValue:function(_9,_a){
if(!this.path){
return _9;
}
var _b=_9;
var _c=this._getDocument(_b);
var _d=this.path;
var i;
if(_d.charAt(0)=="/"){
i=_d.indexOf("/",1);
if(!_b){
var _f=_d.substring(1,i);
_b=_c.createElement(_f);
_9=_b;
}
_d=_d.substring(i+1);
}else{
if(!_b){
return undefined;
}
}
var _10=_d.split("/");
var _11=_10.length-1;
for(i=0;i<_11;i++){
var _12=this._getChildNode(_b,_10[i]);
if(!_12){
_12=_c.createElement(_10[i]);
_b.appendChild(_12);
}
_b=_12;
}
this._setNodeValue(_b,_10[_11],_a);
return _9;
},_getNodeValue:function(_13,exp){
var _15=undefined;
if(exp.charAt(0)=="@"){
var _16=exp.substring(1);
_15=_13.getAttribute(_16);
}else{
if(exp=="text()"){
var _17=_13.firstChild;
if(_17){
_15=_17.nodeValue;
}
}else{
_15=[];
for(var i=0;i<_13.childNodes.length;i++){
var _19=_13.childNodes[i];
if(_19.nodeType===1&&_19.nodeName==exp){
_15.push(_19);
}
}
}
}
return _15;
},_setNodeValue:function(_1a,exp,_1c){
if(exp.charAt(0)=="@"){
var _1d=exp.substring(1);
if(_1c){
_1a.setAttribute(_1d,_1c);
}else{
_1a.removeAttribute(_1d);
}
}else{
if(exp=="text()"){
while(_1a.firstChild){
_1a.removeChild(_1a.firstChild);
}
if(_1c){
var _1e=this._getDocument(_1a).createTextNode(_1c);
_1a.appendChild(_1e);
}
}
}
},_getChildNode:function(_1f,_20){
var _21=1;
var i1=_20.indexOf("[");
if(i1>=0){
var i2=_20.indexOf("]");
_21=_20.substring(i1+1,i2);
_20=_20.substring(0,i1);
}
var _24=1;
for(var i=0;i<_1f.childNodes.length;i++){
var _26=_1f.childNodes[i];
if(_26.nodeType===1&&_26.nodeName==_20){
if(_24==_21){
return _26;
}
_24++;
}
}
return null;
},_getDocument:function(_27){
if(_27){
return (_27.nodeType==9?_27:_27.ownerDocument);
}else{
return dojox.xml.parser.parse();
}
}});
}
