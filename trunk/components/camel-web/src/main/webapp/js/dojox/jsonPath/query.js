/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.jsonPath.query"]){
dojo._hasResource["dojox.jsonPath.query"]=true;
dojo.provide("dojox.jsonPath.query");
dojox.jsonPath.query=function(_1,_2,_3){
var re=dojox.jsonPath._regularExpressions;
if(!_3){
_3={};
}
var _5=[];
function _6(i){
return _5[i];
};
var _8;
if(_3.resultType=="PATH"&&_3.evalType=="RESULT"){
throw Error("RESULT based evaluation not supported with PATH based results");
}
var P={resultType:_3.resultType||"VALUE",normalize:function(_a){
var _b=[];
_a=_a.replace(/'([^']|'')*'/g,function(t){
return "_str("+(_5.push(eval(t))-1)+")";
});
var ll=-1;
while(ll!=_b.length){
ll=_b.length;
_a=_a.replace(/(\??\([^\(\)]*\))/g,function($0){
return "#"+(_b.push($0)-1);
});
}
_a=_a.replace(/[\['](#[0-9]+)[\]']/g,"[$1]").replace(/'?\.'?|\['?/g,";").replace(/;;;|;;/g,";..;").replace(/;$|'?\]|'$/g,"");
ll=-1;
while(ll!=_a){
ll=_a;
_a=_a.replace(/#([0-9]+)/g,function($0,$1){
return _b[$1];
});
}
return _a.split(";");
},asPaths:function(_11){
for(var j=0;j<_11.length;j++){
var p="$";
var x=_11[j];
for(var i=1,n=x.length;i<n;i++){
p+=/^[0-9*]+$/.test(x[i])?("["+x[i]+"]"):("['"+x[i]+"']");
}
_11[j]=p;
}
return _11;
},exec:function(_17,val,rb){
var _1a=["$"];
var _1b=rb?val:[val];
var _1c=[_1a];
function add(v,p,def){
if(v&&v.hasOwnProperty(p)&&P.resultType!="VALUE"){
_1c.push(_1a.concat([p]));
}
if(def){
_1b=v[p];
}else{
if(v&&v.hasOwnProperty(p)){
_1b.push(v[p]);
}
}
};
function _21(v){
_1b.push(v);
_1c.push(_1a);
P.walk(v,function(i){
if(typeof v[i]==="object"){
var _24=_1a;
_1a=_1a.concat(i);
_21(v[i]);
_1a=_24;
}
});
};
function _25(loc,val){
if(val instanceof Array){
var len=val.length,_29=0,end=len,_2b=1;
loc.replace(/^(-?[0-9]*):(-?[0-9]*):?(-?[0-9]*)$/g,function($0,$1,$2,$3){
_29=parseInt($1||_29);
end=parseInt($2||end);
_2b=parseInt($3||_2b);
});
_29=(_29<0)?Math.max(0,_29+len):Math.min(len,_29);
end=(end<0)?Math.max(0,end+len):Math.min(len,end);
for(var i=_29;i<end;i+=_2b){
add(val,i);
}
}
};
function _31(str){
var i=loc.match(/^_str\(([0-9]+)\)$/);
return i?_5[i[1]]:str;
};
function _35(val){
if(/^\(.*?\)$/.test(loc)){
add(val,P.eval(loc,val),rb);
}else{
if(loc==="*"){
P.walk(val,rb&&val instanceof Array?function(i){
P.walk(val[i],function(j){
add(val[i],j);
});
}:function(i){
add(val,i);
});
}else{
if(loc===".."){
_21(val);
}else{
if(/,/.test(loc)){
for(var s=loc.split(/'?,'?/),i=0,n=s.length;i<n;i++){
add(val,_31(s[i]));
}
}else{
if(/^\?\(.*?\)$/.test(loc)){
P.walk(val,function(i){
if(P.eval(loc.replace(/^\?\((.*?)\)$/,"$1"),val[i])){
add(val,i);
}
});
}else{
if(/^(-?[0-9]*):(-?[0-9]*):?([0-9]*)$/.test(loc)){
_25(loc,val);
}else{
loc=_31(loc);
if(rb&&val instanceof Array&&!/^[0-9*]+$/.test(loc)){
P.walk(val,function(i){
add(val[i],loc);
});
}else{
add(val,loc,rb);
}
}
}
}
}
}
}
};
while(_17.length){
var loc=_17.shift();
if((val=_1b)===null||val===undefined){
return val;
}
_1b=[];
var _3f=_1c;
_1c=[];
if(rb){
_35(val);
}else{
P.walk(val,function(i){
_1a=_3f[i]||_1a;
_35(val[i]);
});
}
}
if(P.resultType=="BOTH"){
_1c=P.asPaths(_1c);
var _41=[];
for(var i=0;i<_1c.length;i++){
_41.push({path:_1c[i],value:_1b[i]});
}
return _41;
}
return P.resultType=="PATH"?P.asPaths(_1c):_1b;
},walk:function(val,f){
if(val instanceof Array){
for(var i=0,n=val.length;i<n;i++){
if(i in val){
f(i);
}
}
}else{
if(typeof val==="object"){
for(var m in val){
if(val.hasOwnProperty(m)){
f(m);
}
}
}
}
},eval:function(x,_v){
try{
return $&&_v&&eval(x.replace(/@/g,"_v"));
}
catch(e){
throw new SyntaxError("jsonPath: "+e.message+": "+x.replace(/@/g,"_v").replace(/\^/g,"_a"));
}
}};
var $=_1;
if(_2&&_1){
return P.exec(P.normalize(_2).slice(1),_1,_3.evalType=="RESULT");
}
return false;
};
}
