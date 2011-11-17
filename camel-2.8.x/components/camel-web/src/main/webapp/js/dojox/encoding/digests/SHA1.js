/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.encoding.digests.SHA1"]){
dojo._hasResource["dojox.encoding.digests.SHA1"]=true;
dojo.provide("dojox.encoding.digests.SHA1");
dojo.require("dojox.encoding.digests._base");
(function(){
var _1=dojox.encoding.digests;
var _2=8,_3=(1<<_2)-1;
function R(n,c){
return (n<<c)|(n>>>(32-c));
};
function FT(t,b,c,d){
if(t<20){
return (b&c)|((~b)&d);
}
if(t<40){
return b^c^d;
}
if(t<60){
return (b&c)|(b&d)|(c&d);
}
return b^c^d;
};
function KT(t){
return (t<20)?1518500249:(t<40)?1859775393:(t<60)?-1894007588:-899497514;
};
function _e(x,len){
x[len>>5]|=128<<(24-len%32);
x[((len+64>>9)<<4)+15]=len;
var w=new Array(80),a=1732584193,b=-271733879,c=-1732584194,d=271733878,e=-1009589776;
for(var i=0;i<x.length;i+=16){
var _18=a,_19=b,_1a=c,_1b=d,_1c=e;
for(var j=0;j<80;j++){
if(j<16){
w[j]=x[i+j];
}else{
w[j]=R(w[j-3]^w[j-8]^w[j-14]^w[j-16],1);
}
var t=_1.addWords(_1.addWords(R(a,5),FT(j,b,c,d)),_1.addWords(_1.addWords(e,w[j]),KT(j)));
e=d;
d=c;
c=R(b,30);
b=a;
a=t;
}
a=_1.addWords(a,_18);
b=_1.addWords(b,_19);
c=_1.addWords(c,_1a);
d=_1.addWords(d,_1b);
e=_1.addWords(e,_1c);
}
return [a,b,c,d,e];
};
function _1f(_20,key){
var wa=_23(key);
if(wa.length>16){
wa=_e(wa,key.length*_2);
}
var _24=new Array(16),_25=new Array(16);
for(var i=0;i<16;i++){
_24[i]=wa[i]^909522486;
_25[i]=wa[i]^1549556828;
}
var _27=_e(_24.concat(_23(_20)),512+_20.length*_2);
return _e(_25.concat(_27),512+160);
};
function _23(s){
var wa=[];
for(var i=0,l=s.length*_2;i<l;i+=_2){
wa[i>>5]|=(s.charCodeAt(i/_2)&_3)<<(32-_2-i%32);
}
return wa;
};
function _2c(wa){
var h="0123456789abcdef",s=[];
for(var i=0,l=wa.length*4;i<l;i++){
s.push(h.charAt((wa[i>>2]>>((3-i%4)*8+4))&15),h.charAt((wa[i>>2]>>((3-i%4)*8))&15));
}
return s.join("");
};
function _32(wa){
var s=[];
for(var i=0,l=wa.length*32;i<l;i+=_2){
s.push(String.fromCharCode((wa[i>>5]>>>(32-_2-i%32))&_3));
}
return s.join("");
};
function _37(wa){
var p="=",tab="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/",s=[];
for(var i=0,l=wa.length*4;i<l;i+=3){
var t=(((wa[i>>2]>>8*(3-i%4))&255)<<16)|(((wa[i+1>>2]>>8*(3-(i+1)%4))&255)<<8)|((wa[i+2>>2]>>8*(3-(i+2)%4))&255);
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
_1.SHA1=function(_40,_41){
var out=_41||_1.outputTypes.Base64;
var wa=_e(_23(_40),_40.length*_2);
switch(out){
case _1.outputTypes.Raw:
return wa;
case _1.outputTypes.Hex:
return _2c(wa);
case _1.outputTypes.String:
return _32(wa);
default:
return _37(wa);
}
};
_1.SHA1._hmac=function(_44,key,_46){
var out=_46||_1.outputTypes.Base64;
var wa=_1f(_44,key);
switch(out){
case _1.outputTypes.Raw:
return wa;
case _1.outputTypes.Hex:
return _2c(wa);
case _1.outputTypes.String:
return _32(wa);
default:
return _37(wa);
}
};
})();
}
