/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.encoding.digests._base"]){
dojo._hasResource["dojox.encoding.digests._base"]=true;
dojo.provide("dojox.encoding.digests._base");
(function(){
var d=dojox.encoding.digests;
d.outputTypes={Base64:0,Hex:1,String:2,Raw:3};
d.addWords=function(a,b){
var l=(a&65535)+(b&65535);
var m=(a>>16)+(b>>16)+(l>>16);
return (m<<16)|(l&65535);
};
var _6=8;
var _7=(1<<_6)-1;
d.stringToWord=function(s){
var wa=[];
for(var i=0,l=s.length*_6;i<l;i+=_6){
wa[i>>5]|=(s.charCodeAt(i/_6)&_7)<<(i%32);
}
return wa;
};
d.wordToString=function(wa){
var s=[];
for(var i=0,l=wa.length*32;i<l;i+=_6){
s.push(String.fromCharCode((wa[i>>5]>>>(i%32))&_7));
}
return s.join("");
};
d.wordToHex=function(wa){
var h="0123456789abcdef",s=[];
for(var i=0,l=wa.length*4;i<l;i++){
s.push(h.charAt((wa[i>>2]>>((i%4)*8+4))&15)+h.charAt((wa[i>>2]>>((i%4)*8))&15));
}
return s.join("");
};
d.wordToBase64=function(wa){
var p="=",tab="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/",s=[];
for(var i=0,l=wa.length*4;i<l;i+=3){
var t=(((wa[i>>2]>>8*(i%4))&255)<<16)|(((wa[i+1>>2]>>8*((i+1)%4))&255)<<8)|((wa[i+2>>2]>>8*((i+2)%4))&255);
for(var j=0;j<4;j++){
if(i*8+j*6>wa.length*32){
s.push(p);
}else{
s.push(tab.charAt((t>>6*(3-j))&63));
}
}
}
return s.join("");
};
})();
}
