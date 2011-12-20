/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.object"]){
dojo._hasResource["dojox.lang.functional.object"]=true;
dojo.provide("dojox.lang.functional.object");
dojo.require("dojox.lang.functional.lambda");
(function(){
var d=dojo,df=dojox.lang.functional,_3={};
d.mixin(df,{keys:function(_4){
var t=[];
for(var i in _4){
if(!(i in _3)){
t.push(i);
}
}
return t;
},values:function(_7){
var t=[];
for(var i in _7){
if(!(i in _3)){
t.push(_7[i]);
}
}
return t;
},filterIn:function(_a,f,o){
o=o||d.global;
f=df.lambda(f);
var t={},v,i;
for(i in _a){
if(!(i in _3)){
v=_a[i];
if(f.call(o,v,i,_a)){
t[i]=v;
}
}
}
return t;
},forIn:function(obj,f,o){
o=o||d.global;
f=df.lambda(f);
for(var i in obj){
if(!(i in _3)){
f.call(o,obj[i],i,obj);
}
}
return o;
},mapIn:function(obj,f,o){
o=o||d.global;
f=df.lambda(f);
var t={},i;
for(i in obj){
if(!(i in _3)){
t[i]=f.call(o,obj[i],i,obj);
}
}
return t;
}});
})();
}
