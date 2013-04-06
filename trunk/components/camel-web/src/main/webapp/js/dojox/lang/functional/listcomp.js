/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.listcomp"]){
dojo._hasResource["dojox.lang.functional.listcomp"]=true;
dojo.provide("dojox.lang.functional.listcomp");
(function(){
var _1=/\bfor\b|\bif\b/gm;
var _2=function(s){
var _4=s.split(_1),_5=s.match(_1),_6=["var r = [];"],_7=[],i=0;
l=_5.length;
while(i<l){
var a=_5[i],f=_4[++i];
if(a=="for"&&!/^\s*\(\s*(;|var)/.test(f)){
f=f.replace(/^\s*\(/,"(var ");
}
_6.push(a,f,"{");
_7.push("}");
}
return _6.join("")+"r.push("+_4[0]+");"+_7.join("")+"return r;";
};
dojo.mixin(dojox.lang.functional,{buildListcomp:function(s){
return "function(){"+_2(s)+"}";
},compileListcomp:function(s){
return new Function([],_2(s));
},listcomp:function(s){
return (new Function([],_2(s)))();
}});
})();
}
