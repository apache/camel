/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.curry"]){
dojo._hasResource["dojox.lang.functional.curry"]=true;
dojo.provide("dojox.lang.functional.curry");
dojo.require("dojox.lang.functional.lambda");
(function(){
var df=dojox.lang.functional,ap=Array.prototype;
var _3=function(_4){
return function(){
var _5=_4.args.concat(ap.slice.call(arguments,0));
if(arguments.length+_4.args.length<_4.arity){
return _3({func:_4.func,arity:_4.arity,args:_5});
}
return _4.func.apply(this,_5);
};
};
dojo.mixin(df,{curry:function(f,_7){
f=df.lambda(f);
_7=typeof _7=="number"?_7:f.length;
return _3({func:f,arity:_7,args:[]});
},arg:{},partial:function(f){
var a=arguments,l=a.length,_b=new Array(l-1),p=[],i=1,t;
f=df.lambda(f);
for(;i<l;++i){
t=a[i];
_b[i-1]=t;
if(t===df.arg){
p.push(i-1);
}
}
return function(){
var t=ap.slice.call(_b,0),i=0,l=p.length;
for(;i<l;++i){
t[p[i]]=arguments[i];
}
return f.apply(this,t);
};
},mixer:function(f,mix){
f=df.lambda(f);
return function(){
var t=new Array(mix.length),i=0,l=mix.length;
for(;i<l;++i){
t[i]=arguments[mix[i]];
}
return f.apply(this,t);
};
},flip:function(f){
f=df.lambda(f);
return function(){
var a=arguments,l=a.length-1,t=new Array(l+1),i=0;
for(;i<=l;++i){
t[l-i]=a[i];
}
return f.apply(this,t);
};
}});
})();
}
