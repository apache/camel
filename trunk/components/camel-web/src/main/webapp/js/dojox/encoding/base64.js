/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.encoding.base64"]){
dojo._hasResource["dojox.encoding.base64"]=true;
dojo.provide("dojox.encoding.base64");
(function(){
var p="=";
var _2="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
var _3=dojox.encoding;
_3.base64.encode=function(ba){
var s=[],l=ba.length;
var rm=l%3;
var x=l-rm;
for(var i=0;i<x;){
var t=ba[i++]<<16|ba[i++]<<8|ba[i++];
s.push(_2.charAt((t>>>18)&63));
s.push(_2.charAt((t>>>12)&63));
s.push(_2.charAt((t>>>6)&63));
s.push(_2.charAt(t&63));
}
switch(rm){
case 2:
var t=ba[i++]<<16|ba[i++]<<8;
s.push(_2.charAt((t>>>18)&63));
s.push(_2.charAt((t>>>12)&63));
s.push(_2.charAt((t>>>6)&63));
s.push(p);
break;
case 1:
var t=ba[i++]<<16;
s.push(_2.charAt((t>>>18)&63));
s.push(_2.charAt((t>>>12)&63));
s.push(p);
s.push(p);
break;
}
return s.join("");
};
_3.base64.decode=function(_b){
var s=_b.split(""),_d=[];
var l=s.length;
while(s[--l]==p){
}
for(var i=0;i<l;){
var t=_2.indexOf(s[i++])<<18;
if(i<=l){
t|=_2.indexOf(s[i++])<<12;
}
if(i<=l){
t|=_2.indexOf(s[i++])<<6;
}
if(i<=l){
t|=_2.indexOf(s[i++]);
}
_d.push((t>>>16)&255);
_d.push((t>>>8)&255);
_d.push(t&255);
}
while(_d[_d.length-1]==0){
_d.pop();
}
return _d;
};
})();
}
