/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.lambda"]){
dojo._hasResource["dojox.lang.functional.lambda"]=true;
dojo.provide("dojox.lang.functional.lambda");
(function(){
var df=dojox.lang.functional,_2={};
var _3="ab".split(/a*/).length>1?String.prototype.split:function(_4){
var r=this.split.call(this,_4),m=_4.exec(this);
if(m&&m.index==0){
r.unshift("");
}
return r;
};
var _7=function(s){
var _9=[],_a=_3.call(s,/\s*->\s*/m);
if(_a.length>1){
while(_a.length){
s=_a.pop();
_9=_a.pop().split(/\s*,\s*|\s+/m);
if(_a.length){
_a.push("(function("+_9+"){return ("+s+")})");
}
}
}else{
if(s.match(/\b_\b/)){
_9=["_"];
}else{
var l=s.match(/^\s*(?:[+*\/%&|\^\.=<>]|!=)/m),r=s.match(/[+\-*\/%&|\^\.=<>!]\s*$/m);
if(l||r){
if(l){
_9.push("$1");
s="$1"+s;
}
if(r){
_9.push("$2");
s=s+"$2";
}
}else{
var _d=s.replace(/(?:\b[A-Z]|\.[a-zA-Z_$])[a-zA-Z_$\d]*|[a-zA-Z_$][a-zA-Z_$\d]*:|this|true|false|null|undefined|typeof|instanceof|in|delete|new|void|arguments|decodeURI|decodeURIComponent|encodeURI|encodeURIComponent|escape|eval|isFinite|isNaN|parseFloat|parseInt|unescape|dojo|dijit|dojox|window|document|'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"/g,"").match(/([a-z_$][a-z_$\d]*)/gi)||[],t={};
dojo.forEach(_d,function(v){
if(!(v in t)){
_9.push(v);
t[v]=1;
}
});
}
}
}
return {args:_9,body:s};
};
var _10=function(a){
return a.length?function(){
var i=a.length-1,x=df.lambda(a[i]).apply(this,arguments);
for(--i;i>=0;--i){
x=df.lambda(a[i]).call(this,x);
}
return x;
}:function(x){
return x;
};
};
dojo.mixin(df,{rawLambda:function(s){
return _7(s);
},buildLambda:function(s){
s=_7(s);
return "function("+s.args.join(",")+"){return ("+s.body+");}";
},lambda:function(s){
if(typeof s=="function"){
return s;
}
if(s instanceof Array){
return _10(s);
}
if(s in _2){
return _2[s];
}
s=_7(s);
return _2[s]=new Function(s.args,"return ("+s.body+");");
},clearLambdaCache:function(){
_2={};
}});
})();
}
