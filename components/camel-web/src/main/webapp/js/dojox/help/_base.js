/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.help._base"]){
dojo._hasResource["dojox.help._base"]=true;
dojo.provide("dojox.help._base");
dojo.require("dojox.rpc.Service");
dojo.require("dojo.io.script");
dojo.experimental("dojox.help");
console.warn("Script causes side effects (on numbers, strings, and booleans). Call dojox.help.noConflict() if you plan on executing code.");
dojox.help={locate:function(_1,_2,_3){
_3=_3||20;
var _4=[];
var _5={};
var _6;
if(_2){
if(!dojo.isArray(_2)){
_2=[_2];
}
for(var i=0,_8;_8=_2[i];i++){
_6=_8;
if(dojo.isString(_8)){
_8=dojo.getObject(_8);
if(!_8){
continue;
}
}else{
if(dojo.isObject(_8)){
_6=_8.__name__;
}else{
continue;
}
}
_4.push(_8);
if(_6){
_6=_6.split(".")[0];
if(!_5[_6]&&dojo.indexOf(dojox.help._namespaces,_6)==-1){
dojox.help.refresh(_6);
}
_5[_6]=true;
}
}
}
if(!_4.length){
_4.push({__name__:"window"});
dojo.forEach(dojox.help._namespaces,function(_9){
_5[_9]=true;
});
}
var _a=_1.toLowerCase();
var _b=[];
out:
for(var i=0,_8;_8=_4[i];i++){
var _c=_8.__name__||"";
var _d=dojo.some(_4,function(_e){
_e=_e.__name__||"";
return (_c.indexOf(_e+".")==0);
});
if(_c&&!_d){
_6=_c.split(".")[0];
var _f=[];
if(_c=="window"){
for(_6 in dojox.help._names){
if(dojo.isArray(dojox.help._names[_6])){
_f=_f.concat(dojox.help._names[_6]);
}
}
}else{
_f=dojox.help._names[_6];
}
for(var j=0,_11;_11=_f[j];j++){
if((_c=="window"||_11.indexOf(_c+".")==0)&&_11.toLowerCase().indexOf(_a)!=-1){
if(_11.slice(-10)==".prototype"){
continue;
}
var obj=dojo.getObject(_11);
if(obj){
_b.push([_11,obj]);
if(_b.length==_3){
break out;
}
}
}
}
}
}
dojox.help._displayLocated(_b);
if(!+dojo.isFF){
return "";
}
},refresh:function(_13,_14){
if(arguments.length<2){
_14=true;
}
dojox.help._recurse(_13,_14);
},noConflict:function(_15){
if(arguments.length){
return dojox.help._noConflict(_15);
}else{
while(dojox.help._overrides.length){
var _16=dojox.help._overrides.pop();
var _17=_16[0];
var key=_16[1];
var _19=_17[key];
_17[key]=dojox.help._noConflict(_19);
}
}
},init:function(_1a,_1b){
if(_1a){
dojox.help._namespaces.concat(_1a);
}
dojo.addOnLoad(function(){
dojo.require=(function(_1c){
return function(){
dojox.help.noConflict();
_1c.apply(dojo,arguments);
if(dojox.help._timer){
clearTimeout(dojox.help._timer);
}
dojox.help._timer=setTimeout(function(){
dojo.addOnLoad(function(){
dojox.help.refresh();
dojox.help._timer=false;
});
},500);
};
})(dojo.require);
dojox.help._recurse();
});
},_noConflict:function(_1d){
if(_1d instanceof String){
return _1d.toString();
}else{
if(_1d instanceof Number){
return +_1d;
}else{
if(_1d instanceof Boolean){
return (_1d==true);
}else{
if(dojo.isObject(_1d)){
delete _1d.__name__;
delete _1d.help;
}
}
}
}
return _1d;
},_namespaces:["dojo","dojox","dijit","djConfig"],_rpc:new dojox.rpc.Service(dojo.moduleUrl("dojox.rpc.SMDLibrary","dojo-api.smd")),_attributes:["summary","type","returns","parameters"],_clean:function(_1e){
var obj={};
for(var i=0,_21;_21=dojox.help._attributes[i];i++){
var _22=_1e["__"+_21+"__"];
if(_22){
obj[_21]=_22;
}
}
return obj;
},_displayLocated:function(_23){
throw new Error("_displayLocated should be overridden in one of the dojox.help packages");
},_displayHelp:function(_24,obj){
throw new Error("_displayHelp should be overridden in one of the dojox.help packages");
},_addVersion:function(obj){
if(obj.name){
obj.version=[dojo.version.major,dojo.version.minor,dojo.version.patch].join(".");
var _27=obj.name.split(".");
if(_27[0]=="dojo"||_27[0]=="dijit"||_27[0]=="dojox"){
obj.project=_27[0];
}
}
return obj;
},_stripPrototype:function(_28){
var _29=_28.replace(/\.prototype(\.|$)/g,".");
var _2a=_29;
if(_29.slice(-1)=="."){
_2a=_29=_29.slice(0,-1);
}else{
_29=_28;
}
return [_2a,_29];
},_help:function(){
var _2b=this.__name__;
var _2c=dojox.help._stripPrototype(_2b)[0];
var _2d=[];
for(var i=0,_2f;_2f=dojox.help._attributes[i];i++){
if(!this["__"+_2f+"__"]){
_2d.push(_2f);
}
}
dojox.help._displayHelp(true,{name:this.__name__});
if(!_2d.length||this.__searched__){
dojox.help._displayHelp(false,dojox.help._clean(this));
}else{
this.__searched__=true;
dojox.help._rpc.get(dojox.help._addVersion({name:_2c,exact:true,attributes:_2d})).addCallback(this,function(_30){
if(this.toString===dojox.help._toString){
this.toString(_30);
}
if(_30&&_30.length){
_30=_30[0];
for(var i=0,_2f;_2f=dojox.help._attributes[i];i++){
if(_30[_2f]){
this["__"+_2f+"__"]=_30[_2f];
}
}
dojox.help._displayHelp(false,dojox.help._clean(this));
}else{
dojox.help._displayHelp(false,false);
}
});
}
if(!+dojo.isFF){
return "";
}
},_parse:function(_32){
delete this.__searching__;
if(_32&&_32.length){
var _33=_32[0].parameters;
if(_33){
var _34=["function ",this.__name__,"("];
this.__parameters__=_33;
for(var i=0,_36;_36=_33[i];i++){
if(i){
_34.push(", ");
}
_34.push(_36.name);
if(_36.types){
var _37=[];
for(var j=0,_39;_39=_36.types[j];j++){
_37.push(_39.title);
}
if(_37.length){
_34.push(": ");
_34.push(_37.join("|"));
}
}
if(_36.repeating){
_34.push("...");
}
if(_36.optional){
_34.push("?");
}
}
_34.push(")");
this.__source__=this.__source__.replace(/function[^\(]*\([^\)]*\)/,_34.join(""));
}
if(this.__output__){
delete this.__output__;

}
}else{
dojox.help._displayHelp(false,false);
}
},_toStrings:{},_toString:function(_3a){
if(!this.__source__){
return this.__name__;
}
var _3b=(!this.__parameters__);
this.__parameters__=[];
if(_3a){
dojox.help._parse.call(this,_3a);
}else{
if(_3b){
this.__searching__=true;
dojox.help._toStrings[dojox.help._stripPrototype(this.__name__)[0]]=this;
if(dojox.help._toStringTimer){
clearTimeout(dojox.help._toStringTimer);
}
dojox.help._toStringTimer=setTimeout(function(){
dojox.help.__toString();
},50);
}
}
if(!_3b||!this.__searching__){
return this.__source__;
}
var _3c="function Loading info for "+this.__name__+"... (watch console for result) {}";
if(!+dojo.isFF){
this.__output__=true;
return _3c;
}
return {toString:dojo.hitch(this,function(){
this.__output__=true;
return _3c;
})};
},__toString:function(){
if(dojox.help._toStringTimer){
clearTimeout(dojox.help._toStringTimer);
}
var _3d=[];
dojox.help.noConflict(dojox.help._toStrings);
for(var _3e in dojox.help._toStrings){
_3d.push(_3e);
}
while(_3d.length){
dojox.help._rpc.batch(dojox.help._addVersion({names:_3d.splice(-50,50),exact:true,attributes:["parameters"]})).addCallback(this,function(_3f){
for(var i=0,_41;_41=_3f[i];i++){
var fn=dojox.help._toStrings[_41.name];
if(fn){
dojox.help._parse.call(fn,[_41]);
delete dojox.help._toStrings[_41.name];
}
}
});
}
},_overrides:[],_recursions:[],_names:{},_recurse:function(_43,_44){
if(arguments.length<2){
_44=true;
}
var _45=[];
if(_43&&dojo.isString(_43)){
dojox.help.__recurse(dojo.getObject(_43),_43,_43,_45,_44);
}else{
for(var i=0,ns;ns=dojox.help._namespaces[i];i++){
if(window[ns]){
dojox.help._recursions.push([window[ns],ns,ns]);
window[ns].__name__=ns;
if(!window[ns].help){
window[ns].help=dojox.help._help;
}
}
}
}
while(dojox.help._recursions.length){
var _48=dojox.help._recursions.shift();
dojox.help.__recurse(_48[0],_48[1],_48[2],_45,_44);
}
for(var i=0,_49;_49=_45[i];i++){
delete _49.__seen__;
}
},__recurse:function(_4a,_4b,_4c,_4d,_4e){
for(var key in _4a){
if(key.match(/([^\w_.$]|__[\w_.$]+__)/)){
continue;
}
var _50=_4a[key];
if(typeof _50=="undefined"||_50===document||_50===window||_50===dojox.help._toString||_50===dojox.help._help||_50===null||(+dojo.isIE&&_50.tagName)||_50.__seen__){
continue;
}
var _51=dojo.isFunction(_50);
var _52=dojo.isObject(_50)&&!dojo.isArray(_50)&&!_50.nodeType;
var _53=(_4c)?(_4c+"."+key):key;
if(_53=="dojo._blockAsync"){
continue;
}
if(!_50.__name__){
var _54=null;
if(dojo.isString(_50)){
_54=String;
}else{
if(typeof _50=="number"){
_54=Number;
}else{
if(typeof _50=="boolean"){
_54=Boolean;
}
}
}
if(_54){
_50=_4a[key]=new _54(_50);
}
}
_50.__seen__=true;
_50.__name__=_53;
(dojox.help._names[_4b]=dojox.help._names[_4b]||[]).push(_53);
_4d.push(_50);
if(!_51){
dojox.help._overrides.push([_4a,key]);
}
if((_51||_52)&&_4e){
dojox.help._recursions.push([_50,_4b,_53]);
}
if(_51){
if(!_50.__source__){
_50.__source__=_50.toString().replace(/^function\b ?/,"function "+_53);
}
if(_50.toString===Function.prototype.toString){
_50.toString=dojox.help._toString;
}
}
if(!_50.help){
_50.help=dojox.help._help;
}
}
}};
}
