/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.string.sprintf"]){
dojo._hasResource["dojox.string.sprintf"]=true;
dojo.provide("dojox.string.sprintf");
dojo.require("dojox.string.tokenize");
dojox.string.sprintf=function(_1,_2){
for(var _3=[],i=1;i<arguments.length;i++){
_3.push(arguments[i]);
}
var _5=new dojox.string.sprintf.Formatter(_1);
return _5.format.apply(_5,_3);
};
dojox.string.sprintf.Formatter=function(_6){
var _7=[];
this._mapped=false;
this._format=_6;
this._tokens=dojox.string.tokenize(_6,this._re,this._parseDelim,this);
};
dojo.extend(dojox.string.sprintf.Formatter,{_re:/\%(?:\(([\w_]+)\)|([1-9]\d*)\$)?([0 +\-\#]*)(\*|\d+)?(\.)?(\*|\d+)?[hlL]?([\%scdeEfFgGiouxX])/g,_parseDelim:function(_8,_9,_a,_b,_c,_d,_e){
if(_8){
this._mapped=true;
}
return {mapping:_8,intmapping:_9,flags:_a,_minWidth:_b,period:_c,_precision:_d,specifier:_e};
},_specifiers:{b:{base:2,isInt:true},o:{base:8,isInt:true},x:{base:16,isInt:true},X:{extend:["x"],toUpper:true},d:{base:10,isInt:true},i:{extend:["d"]},u:{extend:["d"],isUnsigned:true},c:{setArg:function(_f){
if(!isNaN(_f.arg)){
var num=parseInt(_f.arg);
if(num<0||num>127){
throw new Error("invalid character code passed to %c in sprintf");
}
_f.arg=isNaN(num)?""+num:String.fromCharCode(num);
}
}},s:{setMaxWidth:function(_11){
_11.maxWidth=(_11.period==".")?_11.precision:-1;
}},e:{isDouble:true,doubleNotation:"e"},E:{extend:["e"],toUpper:true},f:{isDouble:true,doubleNotation:"f"},F:{extend:["f"]},g:{isDouble:true,doubleNotation:"g"},G:{extend:["g"],toUpper:true}},format:function(_12){
if(this._mapped&&typeof _12!="object"){
throw new Error("format requires a mapping");
}
var str="";
var _14=0;
for(var i=0,_16;i<this._tokens.length;i++){
_16=this._tokens[i];
if(typeof _16=="string"){
str+=_16;
}else{
if(this._mapped){
if(typeof _12[_16.mapping]=="undefined"){
throw new Error("missing key "+_16.mapping);
}
_16.arg=_12[_16.mapping];
}else{
if(_16.intmapping){
var _14=parseInt(_16.intmapping)-1;
}
if(_14>=arguments.length){
throw new Error("got "+arguments.length+" printf arguments, insufficient for '"+this._format+"'");
}
_16.arg=arguments[_14++];
}
if(!_16.compiled){
_16.compiled=true;
_16.sign="";
_16.zeroPad=false;
_16.rightJustify=false;
_16.alternative=false;
var _17={};
for(var fi=_16.flags.length;fi--;){
var _19=_16.flags.charAt(fi);
_17[_19]=true;
switch(_19){
case " ":
_16.sign=" ";
break;
case "+":
_16.sign="+";
break;
case "0":
_16.zeroPad=(_17["-"])?false:true;
break;
case "-":
_16.rightJustify=true;
_16.zeroPad=false;
break;
case "#":
_16.alternative=true;
break;
default:
throw Error("bad formatting flag '"+_16.flags.charAt(fi)+"'");
}
}
_16.minWidth=(_16._minWidth)?parseInt(_16._minWidth):0;
_16.maxWidth=-1;
_16.toUpper=false;
_16.isUnsigned=false;
_16.isInt=false;
_16.isDouble=false;
_16.precision=1;
if(_16.period=="."){
if(_16._precision){
_16.precision=parseInt(_16._precision);
}else{
_16.precision=0;
}
}
var _1a=this._specifiers[_16.specifier];
if(typeof _1a=="undefined"){
throw new Error("unexpected specifier '"+_16.specifier+"'");
}
if(_1a.extend){
dojo.mixin(_1a,this._specifiers[_1a.extend]);
delete _1a.extend;
}
dojo.mixin(_16,_1a);
}
if(typeof _16.setArg=="function"){
_16.setArg(_16);
}
if(typeof _16.setMaxWidth=="function"){
_16.setMaxWidth(_16);
}
if(_16._minWidth=="*"){
if(this._mapped){
throw new Error("* width not supported in mapped formats");
}
_16.minWidth=parseInt(arguments[_14++]);
if(isNaN(_16.minWidth)){
throw new Error("the argument for * width at position "+_14+" is not a number in "+this._format);
}
if(_16.minWidth<0){
_16.rightJustify=true;
_16.minWidth=-_16.minWidth;
}
}
if(_16._precision=="*"&&_16.period=="."){
if(this._mapped){
throw new Error("* precision not supported in mapped formats");
}
_16.precision=parseInt(arguments[_14++]);
if(isNaN(_16.precision)){
throw Error("the argument for * precision at position "+_14+" is not a number in "+this._format);
}
if(_16.precision<0){
_16.precision=1;
_16.period="";
}
}
if(_16.isInt){
if(_16.period=="."){
_16.zeroPad=false;
}
this.formatInt(_16);
}else{
if(_16.isDouble){
if(_16.period!="."){
_16.precision=6;
}
this.formatDouble(_16);
}
}
this.fitField(_16);
str+=""+_16.arg;
}
}
return str;
},_zeros10:"0000000000",_spaces10:"          ",formatInt:function(_1b){
var i=parseInt(_1b.arg);
if(!isFinite(i)){
if(typeof _1b.arg!="number"){
throw new Error("format argument '"+_1b.arg+"' not an integer; parseInt returned "+i);
}
i=0;
}
if(i<0&&(_1b.isUnsigned||_1b.base!=10)){
i=4294967295+i+1;
}
if(i<0){
_1b.arg=(-i).toString(_1b.base);
this.zeroPad(_1b);
_1b.arg="-"+_1b.arg;
}else{
_1b.arg=i.toString(_1b.base);
if(!i&&!_1b.precision){
_1b.arg="";
}else{
this.zeroPad(_1b);
}
if(_1b.sign){
_1b.arg=_1b.sign+_1b.arg;
}
}
if(_1b.base==16){
if(_1b.alternative){
_1b.arg="0x"+_1b.arg;
}
_1b.arg=_1b.toUpper?_1b.arg.toUpperCase():_1b.arg.toLowerCase();
}
if(_1b.base==8){
if(_1b.alternative&&_1b.arg.charAt(0)!="0"){
_1b.arg="0"+_1b.arg;
}
}
},formatDouble:function(_1d){
var f=parseFloat(_1d.arg);
if(!isFinite(f)){
if(typeof _1d.arg!="number"){
throw new Error("format argument '"+_1d.arg+"' not a float; parseFloat returned "+f);
}
f=0;
}
switch(_1d.doubleNotation){
case "e":
_1d.arg=f.toExponential(_1d.precision);
break;
case "f":
_1d.arg=f.toFixed(_1d.precision);
break;
case "g":
if(Math.abs(f)<0.0001){
_1d.arg=f.toExponential(_1d.precision>0?_1d.precision-1:_1d.precision);
}else{
_1d.arg=f.toPrecision(_1d.precision);
}
if(!_1d.alternative){
_1d.arg=_1d.arg.replace(/(\..*[^0])0*/,"$1");
_1d.arg=_1d.arg.replace(/\.0*e/,"e").replace(/\.0$/,"");
}
break;
default:
throw new Error("unexpected double notation '"+_1d.doubleNotation+"'");
}
_1d.arg=_1d.arg.replace(/e\+(\d)$/,"e+0$1").replace(/e\-(\d)$/,"e-0$1");
if(dojo.isOpera){
_1d.arg=_1d.arg.replace(/^\./,"0.");
}
if(_1d.alternative){
_1d.arg=_1d.arg.replace(/^(\d+)$/,"$1.");
_1d.arg=_1d.arg.replace(/^(\d+)e/,"$1.e");
}
if(f>=0&&_1d.sign){
_1d.arg=_1d.sign+_1d.arg;
}
_1d.arg=_1d.toUpper?_1d.arg.toUpperCase():_1d.arg.toLowerCase();
},zeroPad:function(_1f,_20){
_20=(arguments.length==2)?_20:_1f.precision;
if(typeof _1f.arg!="string"){
_1f.arg=""+_1f.arg;
}
var _21=_20-10;
while(_1f.arg.length<_21){
_1f.arg=(_1f.rightJustify)?_1f.arg+this._zeros10:this._zeros10+_1f.arg;
}
var pad=_20-_1f.arg.length;
_1f.arg=(_1f.rightJustify)?_1f.arg+this._zeros10.substring(0,pad):this._zeros10.substring(0,pad)+_1f.arg;
},fitField:function(_23){
if(_23.maxWidth>=0&&_23.arg.length>_23.maxWidth){
return _23.arg.substring(0,_23.maxWidth);
}
if(_23.zeroPad){
this.zeroPad(_23,_23.minWidth);
return;
}
this.spacePad(_23);
},spacePad:function(_24,_25){
_25=(arguments.length==2)?_25:_24.minWidth;
if(typeof _24.arg!="string"){
_24.arg=""+_24.arg;
}
var _26=_25-10;
while(_24.arg.length<_26){
_24.arg=(_24.rightJustify)?_24.arg+this._spaces10:this._spaces10+_24.arg;
}
var pad=_25-_24.arg.length;
_24.arg=(_24.rightJustify)?_24.arg+this._spaces10.substring(0,pad):this._spaces10.substring(0,pad)+_24.arg;
}});
}
