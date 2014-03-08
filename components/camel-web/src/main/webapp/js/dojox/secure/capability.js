/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.secure.capability"]){
dojo._hasResource["dojox.secure.capability"]=true;
dojo.provide("dojox.secure.capability");
dojox.secure.badProps=/^__|^(apply|call|callee|caller|constructor|eval|prototype|this|unwatch|valueOf|watch)$|__$/;
dojox.secure.capability={keywords:["break","case","catch","const","continue","debugger","default","delete","do","else","enum","false","finally","for","function","if","in","instanceof","new","null","yield","return","switch","throw","true","try","typeof","var","void","while"],validate:function(_1,_2,_3){
var _4=this.keywords;
for(var i=0;i<_4.length;i++){
_3[_4[i]]=true;
}
var _6="|this| keyword in object literal without a Class call";
var _7=[];
if(_1.match(/[\u200c-\u200f\u202a-\u202e\u206a-\u206f\uff00-\uffff]/)){
throw new Error("Illegal unicode characters detected");
}
if(_1.match(/\/\*@cc_on/)){
throw new Error("Conditional compilation token is not allowed");
}
_1=_1.replace(/\\["'\\\/bfnrtu]/g,"@").replace(/\/\/.*|\/\*[\w\W]*?\*\/|\/(\\[\/\\]|[^*\/])(\\.|[^\/\n\\])*\/[gim]*|("[^"]*")|('[^']*')/g,function(t){
return t.match(/^\/\/|^\/\*/)?" ":"0";
}).replace(/\.\s*([a-z\$_A-Z][\w\$_]*)|([;,{])\s*([a-z\$_A-Z][\w\$_]*\s*):/g,function(t,_a,_b,_c){
_a=_a||_c;
if(/^__|^(apply|call|callee|caller|constructor|eval|prototype|this|unwatch|valueOf|watch)$|__$/.test(_a)){
throw new Error("Illegal property name "+_a);
}
return (_b&&(_b+"0:"))||"~";
});
_1.replace(/([^\[][\]\}]\s*=)|((\Wreturn|\S)\s*\[\s*\+?)|([^=!][=!]=[^=])/g,function(_d){
if(!_d.match(/((\Wreturn|[=\&\|\:\?\,])\s*\[)|\[\s*\+$/)){
throw new Error("Illegal operator "+_d.substring(1));
}
});
_1=_1.replace(new RegExp("("+_2.join("|")+")[\\s~]*\\(","g"),function(_e){
return "new(";
});
function _f(_10,_11){
var _12={};
_10.replace(/#\d/g,function(b){
var _14=_7[b.substring(1)];
for(var i in _14){
if(i==_6){
throw i;
}
if(i=="this"&&_14[":method"]&&_14["this"]==1){
i=_6;
}
if(i!=":method"){
_12[i]=2;
}
}
});
_10.replace(/(\W|^)([a-z_\$A-Z][\w_\$]*)/g,function(t,a,_18){
if(_18.charAt(0)=="_"){
throw new Error("Names may not start with _");
}
_12[_18]=1;
});
return _12;
};
var _19,_1a;
function _1b(t,_1d,a,b,_20,_21){
_21.replace(/(^|,)0:\s*function#(\d)/g,function(t,a,b){
var _25=_7[b];
_25[":method"]=1;
});
_21=_21.replace(/(^|[^_\w\$])Class\s*\(\s*([_\w\$]+\s*,\s*)*#(\d)/g,function(t,p,a,b){
var _2a=_7[b];
delete _2a[_6];
return (p||"")+(a||"")+"#"+b;
});
_1a=_f(_21,_1d);
function _2b(t,a,b,_2f){
_2f.replace(/,?([a-z\$A-Z][_\w\$]*)/g,function(t,_31){
if(_31=="Class"){
throw new Error("Class is reserved");
}
delete _1a[_31];
});
};
if(_1d){
_2b(t,a,a,_20);
}
_21.replace(/(\W|^)(var) ([ \t,_\w\$]+)/g,_2b);
return (a||"")+(b||"")+"#"+(_7.push(_1a)-1);
};
do{
_19=_1.replace(/((function|catch)(\s+[_\w\$]+)?\s*\(([^\)]*)\)\s*)?{([^{}]*)}/g,_1b);
}while(_19!=_1&&(_1=_19));
_1b(0,0,0,0,0,_1);
for(i in _1a){
if(!(i in _3)){
throw new Error("Illegal reference to "+i);
}
}
}};
}
