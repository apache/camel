/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojo.parser"]){
dojo._hasResource["dojo.parser"]=true;
dojo.provide("dojo.parser");
dojo.require("dojo.date.stamp");
dojo.parser=new function(){
var d=dojo;
var _2=d._scopeName+"Type";
var _3="["+_2+"]";
var _4=0,_5={};
var _6=function(_7,_8){
var _9=_8||_5;
if(dojo.isIE){
var cn=_7["__dojoNameCache"];
if(cn&&_9[cn]===_7){
return cn;
}
}
var _b;
do{
_b="__"+_4++;
}while(_b in _9);
_9[_b]=_7;
return _b;
};
function _c(_d){
if(d.isString(_d)){
return "string";
}
if(typeof _d=="number"){
return "number";
}
if(typeof _d=="boolean"){
return "boolean";
}
if(d.isFunction(_d)){
return "function";
}
if(d.isArray(_d)){
return "array";
}
if(_d instanceof Date){
return "date";
}
if(_d instanceof d._Url){
return "url";
}
return "object";
};
function _e(_f,_10){
switch(_10){
case "string":
return _f;
case "number":
return _f.length?Number(_f):NaN;
case "boolean":
return typeof _f=="boolean"?_f:!(_f.toLowerCase()=="false");
case "function":
if(d.isFunction(_f)){
_f=_f.toString();
_f=d.trim(_f.substring(_f.indexOf("{")+1,_f.length-1));
}
try{
if(_f.search(/[^\w\.]+/i)!=-1){
_f=_6(new Function(_f),this);
}
return d.getObject(_f,false);
}
catch(e){
return new Function();
}
case "array":
return _f?_f.split(/\s*,\s*/):[];
case "date":
switch(_f){
case "":
return new Date("");
case "now":
return new Date();
default:
return d.date.stamp.fromISOString(_f);
}
case "url":
return d.baseUrl+_f;
default:
return d.fromJson(_f);
}
};
var _11={};
function _12(_13){
if(!_11[_13]){
var cls=d.getObject(_13);
if(!d.isFunction(cls)){
throw new Error("Could not load class '"+_13+"'. Did you spell the name correctly and use a full path, like 'dijit.form.Button'?");
}
var _15=cls.prototype;
var _16={};
for(var _17 in _15){
if(_17.charAt(0)=="_"){
continue;
}
var _18=_15[_17];
_16[_17]=_c(_18);
}
_11[_13]={cls:cls,params:_16};
}
return _11[_13];
};
this._functionFromScript=function(_19){
var _1a="";
var _1b="";
var _1c=_19.getAttribute("args");
if(_1c){
d.forEach(_1c.split(/\s*,\s*/),function(_1d,idx){
_1a+="var "+_1d+" = arguments["+idx+"]; ";
});
}
var _1f=_19.getAttribute("with");
if(_1f&&_1f.length){
d.forEach(_1f.split(/\s*,\s*/),function(_20){
_1a+="with("+_20+"){";
_1b+="}";
});
}
return new Function(_1a+_19.innerHTML+_1b);
};
this.instantiate=function(_21,_22){
var _23=[];
_22=_22||{};
d.forEach(_21,function(_24){
if(!_24){
return;
}
var _25=_2 in _22?_22[_2]:_24.getAttribute(_2);
if(!_25||!_25.length){
return;
}
var _26=_12(_25),_27=_26.cls,ps=_27._noScript||_27.prototype._noScript;
var _29={},_2a=_24.attributes;
for(var _2b in _26.params){
var _2c=_2b in _22?{value:_22[_2b],specified:true}:_2a.getNamedItem(_2b);
if(!_2c||(!_2c.specified&&(!dojo.isIE||_2b.toLowerCase()!="value"))){
continue;
}
var _2d=_2c.value;
switch(_2b){
case "class":
_2d="className" in _22?_22.className:_24.className;
break;
case "style":
_2d="style" in _22?_22.style:(_24.style&&_24.style.cssText);
}
var _2e=_26.params[_2b];
if(typeof _2d=="string"){
_29[_2b]=_e(_2d,_2e);
}else{
_29[_2b]=_2d;
}
}
if(!ps){
var _2f=[],_30=[];
d.query("> script[type^='dojo/']",_24).orphan().forEach(function(_31){
var _32=_31.getAttribute("event"),_25=_31.getAttribute("type"),nf=d.parser._functionFromScript(_31);
if(_32){
if(_25=="dojo/connect"){
_2f.push({event:_32,func:nf});
}else{
_29[_32]=nf;
}
}else{
_30.push(nf);
}
});
}
var _34=_27["markupFactory"];
if(!_34&&_27["prototype"]){
_34=_27.prototype["markupFactory"];
}
var _35=_34?_34(_29,_24,_27):new _27(_29,_24);
_23.push(_35);
var _36=_24.getAttribute("jsId");
if(_36){
d.setObject(_36,_35);
}
if(!ps){
d.forEach(_2f,function(_37){
d.connect(_35,_37.event,null,_37.func);
});
d.forEach(_30,function(_38){
_38.call(_35);
});
}
});
d.forEach(_23,function(_39){
if(_39&&_39.startup&&!_39._started&&(!_39.getParent||!_39.getParent())){
_39.startup();
}
});
return _23;
};
this.parse=function(_3a){
var _3b=d.query(_3,_3a);
var _3c=this.instantiate(_3b);
return _3c;
};
}();
(function(){
var _3d=function(){
if(dojo.config["parseOnLoad"]==true){
dojo.parser.parse();
}
};
if(dojo.exists("dijit.wai.onload")&&(dijit.wai.onload===dojo._loaders[0])){
dojo._loaders.splice(1,0,_3d);
}else{
dojo._loaders.unshift(_3d);
}
})();
}
