/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.ml.util"]){
dojo._hasResource["dojox.wire.ml.util"]=true;
dojo.provide("dojox.wire.ml.util");
dojo.require("dojox.xml.parser");
dojo.require("dojox.wire.Wire");
dojox.wire.ml._getValue=function(_1,_2){
if(!_1){
return undefined;
}
var _3=undefined;
if(_2&&_1.length>=9&&_1.substring(0,9)=="arguments"){
_3=_1.substring(9);
return new dojox.wire.Wire({property:_3}).getValue(_2);
}
var i=_1.indexOf(".");
if(i>=0){
_3=_1.substring(i+1);
_1=_1.substring(0,i);
}
var _5=(dijit.byId(_1)||dojo.byId(_1)||dojo.getObject(_1));
if(!_5){
return undefined;
}
if(!_3){
return _5;
}else{
return new dojox.wire.Wire({object:_5,property:_3}).getValue();
}
};
dojox.wire.ml._setValue=function(_6,_7){
if(!_6){
return;
}
var i=_6.indexOf(".");
if(i<0){
return;
}
var _9=this._getValue(_6.substring(0,i));
if(!_9){
return;
}
var _a=_6.substring(i+1);
var _b=new dojox.wire.Wire({object:_9,property:_a}).setValue(_7);
};
dojo.declare("dojox.wire.ml.XmlElement",null,{constructor:function(_c){
if(dojo.isString(_c)){
_c=this._getDocument().createElement(_c);
}
this.element=_c;
},getPropertyValue:function(_d){
var _e=undefined;
if(!this.element){
return _e;
}
if(!_d){
return _e;
}
if(_d.charAt(0)=="@"){
var _f=_d.substring(1);
_e=this.element.getAttribute(_f);
}else{
if(_d=="text()"){
var _10=this.element.firstChild;
if(_10){
_e=_10.nodeValue;
}
}else{
var _11=[];
for(var i=0;i<this.element.childNodes.length;i++){
var _13=this.element.childNodes[i];
if(_13.nodeType===1&&_13.nodeName==_d){
_11.push(new dojox.wire.ml.XmlElement(_13));
}
}
if(_11.length>0){
if(_11.length===1){
_e=_11[0];
}else{
_e=_11;
}
}
}
}
return _e;
},setPropertyValue:function(_14,_15){
var i;
var _17;
if(!this.element){
return;
}
if(!_14){
return;
}
if(_14.charAt(0)=="@"){
var _18=_14.substring(1);
if(_15){
this.element.setAttribute(_18,_15);
}else{
this.element.removeAttribute(_18);
}
}else{
if(_14=="text()"){
while(this.element.firstChild){
this.element.removeChild(this.element.firstChild);
}
if(_15){
_17=this._getDocument().createTextNode(_15);
this.element.appendChild(_17);
}
}else{
var _19=null;
var _1a;
for(i=this.element.childNodes.length-1;i>=0;i--){
_1a=this.element.childNodes[i];
if(_1a.nodeType===1&&_1a.nodeName==_14){
if(!_19){
_19=_1a.nextSibling;
}
this.element.removeChild(_1a);
}
}
if(_15){
if(dojo.isArray(_15)){
for(i in _15){
var e=_15[i];
if(e.element){
this.element.insertBefore(e.element,_19);
}
}
}else{
if(_15 instanceof dojox.wire.ml.XmlElement){
if(_15.element){
this.element.insertBefore(_15.element,_19);
}
}else{
_1a=this._getDocument().createElement(_14);
_17=this._getDocument().createTextNode(_15);
_1a.appendChild(_17);
this.element.insertBefore(_1a,_19);
}
}
}
}
}
},toString:function(){
var s="";
if(this.element){
var _1d=this.element.firstChild;
if(_1d){
s=_1d.nodeValue;
}
}
return s;
},toObject:function(){
if(!this.element){
return null;
}
var _1e="";
var obj={};
var _20=0;
var i;
for(i=0;i<this.element.childNodes.length;i++){
var _22=this.element.childNodes[i];
if(_22.nodeType===1){
_20++;
var o=new dojox.wire.ml.XmlElement(_22).toObject();
var _24=_22.nodeName;
var p=obj[_24];
if(!p){
obj[_24]=o;
}else{
if(dojo.isArray(p)){
p.push(o);
}else{
obj[_24]=[p,o];
}
}
}else{
if(_22.nodeType===3||_22.nodeType===4){
_1e+=_22.nodeValue;
}
}
}
var _26=0;
if(this.element.nodeType===1){
_26=this.element.attributes.length;
for(i=0;i<_26;i++){
var _27=this.element.attributes[i];
obj["@"+_27.nodeName]=_27.nodeValue;
}
}
if(_20===0){
if(_26===0){
return _1e;
}
obj["text()"]=_1e;
}
return obj;
},_getDocument:function(){
if(this.element){
return (this.element.nodeType==9?this.element:this.element.ownerDocument);
}else{
return dojox.xml.parser.parse();
}
}});
}
