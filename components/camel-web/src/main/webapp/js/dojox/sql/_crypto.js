/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sql._crypto"]){
dojo._hasResource["dojox.sql._crypto"]=true;
dojo.provide("dojox.sql._crypto");
dojo.mixin(dojox.sql._crypto,{_POOL_SIZE:100,encrypt:function(_1,_2,_3){
this._initWorkerPool();
var _4={plaintext:_1,password:_2};
_4=dojo.toJson(_4);
_4="encr:"+String(_4);
this._assignWork(_4,_3);
},decrypt:function(_5,_6,_7){
this._initWorkerPool();
var _8={ciphertext:_5,password:_6};
_8=dojo.toJson(_8);
_8="decr:"+String(_8);
this._assignWork(_8,_7);
},_initWorkerPool:function(){
if(!this._manager){
try{
this._manager=google.gears.factory.create("beta.workerpool","1.0");
this._unemployed=[];
this._employed={};
this._handleMessage=[];
var _9=this;
this._manager.onmessage=function(_a,_b){
var _c=_9._employed["_"+_b];
_9._employed["_"+_b]=undefined;
_9._unemployed.push("_"+_b);
if(_9._handleMessage.length){
var _d=_9._handleMessage.shift();
_9._assignWork(_d.msg,_d.callback);
}
_c(_a);
};
var _e="function _workerInit(){"+"gearsWorkerPool.onmessage = "+String(this._workerHandler)+";"+"}";
var _f=_e+" _workerInit();";
for(var i=0;i<this._POOL_SIZE;i++){
this._unemployed.push("_"+this._manager.createWorker(_f));
}
}
catch(exp){
throw exp.message||exp;
}
}
},_assignWork:function(msg,_12){
if(!this._handleMessage.length&&this._unemployed.length){
var _13=this._unemployed.shift().substring(1);
this._employed["_"+_13]=_12;
this._manager.sendMessage(msg,parseInt(_13,10));
}else{
this._handleMessage={msg:msg,callback:_12};
}
},_workerHandler:function(msg,_15){
var _16=[99,124,119,123,242,107,111,197,48,1,103,43,254,215,171,118,202,130,201,125,250,89,71,240,173,212,162,175,156,164,114,192,183,253,147,38,54,63,247,204,52,165,229,241,113,216,49,21,4,199,35,195,24,150,5,154,7,18,128,226,235,39,178,117,9,131,44,26,27,110,90,160,82,59,214,179,41,227,47,132,83,209,0,237,32,252,177,91,106,203,190,57,74,76,88,207,208,239,170,251,67,77,51,133,69,249,2,127,80,60,159,168,81,163,64,143,146,157,56,245,188,182,218,33,16,255,243,210,205,12,19,236,95,151,68,23,196,167,126,61,100,93,25,115,96,129,79,220,34,42,144,136,70,238,184,20,222,94,11,219,224,50,58,10,73,6,36,92,194,211,172,98,145,149,228,121,231,200,55,109,141,213,78,169,108,86,244,234,101,122,174,8,186,120,37,46,28,166,180,198,232,221,116,31,75,189,139,138,112,62,181,102,72,3,246,14,97,53,87,185,134,193,29,158,225,248,152,17,105,217,142,148,155,30,135,233,206,85,40,223,140,161,137,13,191,230,66,104,65,153,45,15,176,84,187,22];
var _17=[[0,0,0,0],[1,0,0,0],[2,0,0,0],[4,0,0,0],[8,0,0,0],[16,0,0,0],[32,0,0,0],[64,0,0,0],[128,0,0,0],[27,0,0,0],[54,0,0,0]];
function _18(_19,w){
var Nb=4;
var Nr=w.length/Nb-1;
var _1d=[[],[],[],[]];
for(var i=0;i<4*Nb;i++){
_1d[i%4][Math.floor(i/4)]=_19[i];
}
_1d=_1f(_1d,w,0,Nb);
for(var _20=1;_20<Nr;_20++){
_1d=_21(_1d,Nb);
_1d=_22(_1d,Nb);
_1d=_23(_1d,Nb);
_1d=_1f(_1d,w,_20,Nb);
}
_1d=_21(_1d,Nb);
_1d=_22(_1d,Nb);
_1d=_1f(_1d,w,Nr,Nb);
var _24=new Array(4*Nb);
for(var i=0;i<4*Nb;i++){
_24[i]=_1d[i%4][Math.floor(i/4)];
}
return _24;
};
function _21(s,Nb){
for(var r=0;r<4;r++){
for(var c=0;c<Nb;c++){
s[r][c]=_16[s[r][c]];
}
}
return s;
};
function _22(s,Nb){
var t=new Array(4);
for(var r=1;r<4;r++){
for(var c=0;c<4;c++){
t[c]=s[r][(c+r)%Nb];
}
for(var c=0;c<4;c++){
s[r][c]=t[c];
}
}
return s;
};
function _23(s,Nb){
for(var c=0;c<4;c++){
var a=new Array(4);
var b=new Array(4);
for(var i=0;i<4;i++){
a[i]=s[i][c];
b[i]=s[i][c]&128?s[i][c]<<1^283:s[i][c]<<1;
}
s[0][c]=b[0]^a[1]^b[1]^a[2]^a[3];
s[1][c]=a[0]^b[1]^a[2]^b[2]^a[3];
s[2][c]=a[0]^a[1]^b[2]^a[3]^b[3];
s[3][c]=a[0]^b[0]^a[1]^a[2]^b[3];
}
return s;
};
function _1f(_34,w,rnd,Nb){
for(var r=0;r<4;r++){
for(var c=0;c<Nb;c++){
_34[r][c]^=w[rnd*4+c][r];
}
}
return _34;
};
function _3a(key){
var Nb=4;
var Nk=key.length/4;
var Nr=Nk+6;
var w=new Array(Nb*(Nr+1));
var _40=new Array(4);
for(var i=0;i<Nk;i++){
var r=[key[4*i],key[4*i+1],key[4*i+2],key[4*i+3]];
w[i]=r;
}
for(var i=Nk;i<(Nb*(Nr+1));i++){
w[i]=new Array(4);
for(var t=0;t<4;t++){
_40[t]=w[i-1][t];
}
if(i%Nk==0){
_40=_44(_45(_40));
for(var t=0;t<4;t++){
_40[t]^=_17[i/Nk][t];
}
}else{
if(Nk>6&&i%Nk==4){
_40=_44(_40);
}
}
for(var t=0;t<4;t++){
w[i][t]=w[i-Nk][t]^_40[t];
}
}
return w;
};
function _44(w){
for(var i=0;i<4;i++){
w[i]=_16[w[i]];
}
return w;
};
function _45(w){
w[4]=w[0];
for(var i=0;i<4;i++){
w[i]=w[i+1];
}
return w;
};
function _4a(_4b,_4c,_4d){
if(!(_4d==128||_4d==192||_4d==256)){
return "";
}
var _4e=_4d/8;
var _4f=new Array(_4e);
for(var i=0;i<_4e;i++){
_4f[i]=_4c.charCodeAt(i)&255;
}
var key=_18(_4f,_3a(_4f));
key=key.concat(key.slice(0,_4e-16));
var _52=16;
var _53=new Array(_52);
var _54=(new Date()).getTime();
for(var i=0;i<4;i++){
_53[i]=(_54>>>i*8)&255;
}
for(var i=0;i<4;i++){
_53[i+4]=(_54/4294967296>>>i*8)&255;
}
var _55=_3a(key);
var _56=Math.ceil(_4b.length/_52);
var _57=new Array(_56);
for(var b=0;b<_56;b++){
for(var c=0;c<4;c++){
_53[15-c]=(b>>>c*8)&255;
}
for(var c=0;c<4;c++){
_53[15-c-4]=(b/4294967296>>>c*8);
}
var _5a=_18(_53,_55);
var _5b=b<_56-1?_52:(_4b.length-1)%_52+1;
var ct="";
for(var i=0;i<_5b;i++){
var _5d=_4b.charCodeAt(b*_52+i);
var _5e=_5d^_5a[i];
ct+=String.fromCharCode(_5e);
}
_57[b]=_5f(ct);
}
var _60="";
for(var i=0;i<8;i++){
_60+=String.fromCharCode(_53[i]);
}
_60=_5f(_60);
return _60+"-"+_57.join("-");
};
function _61(_62,_63,_64){
if(!(_64==128||_64==192||_64==256)){
return "";
}
var _65=_64/8;
var _66=new Array(_65);
for(var i=0;i<_65;i++){
_66[i]=_63.charCodeAt(i)&255;
}
var _68=_3a(_66);
var key=_18(_66,_68);
key=key.concat(key.slice(0,_65-16));
var _6a=_3a(key);
_62=_62.split("-");
var _6b=16;
var _6c=new Array(_6b);
var _6d=_6e(_62[0]);
for(var i=0;i<8;i++){
_6c[i]=_6d.charCodeAt(i);
}
var _6f=new Array(_62.length-1);
for(var b=1;b<_62.length;b++){
for(var c=0;c<4;c++){
_6c[15-c]=((b-1)>>>c*8)&255;
}
for(var c=0;c<4;c++){
_6c[15-c-4]=((b/4294967296-1)>>>c*8)&255;
}
var _72=_18(_6c,_6a);
_62[b]=_6e(_62[b]);
var pt="";
for(var i=0;i<_62[b].length;i++){
var _74=_62[b].charCodeAt(i);
var _75=_74^_72[i];
pt+=String.fromCharCode(_75);
}
_6f[b-1]=pt;
}
return _6f.join("");
};
function _5f(str){
return str.replace(/[\0\t\n\v\f\r\xa0!-]/g,function(c){
return "!"+c.charCodeAt(0)+"!";
});
};
function _6e(str){
return str.replace(/!\d\d?\d?!/g,function(c){
return String.fromCharCode(c.slice(1,-1));
});
};
function _7a(_7b,_7c){
return _4a(_7b,_7c,256);
};
function _7d(_7e,_7f){
return _61(_7e,_7f,256);
};
var cmd=msg.substr(0,4);
var arg=msg.substr(5);
if(cmd=="encr"){
arg=eval("("+arg+")");
var _82=arg.plaintext;
var _83=arg.password;
var _84=_7a(_82,_83);
gearsWorkerPool.sendMessage(String(_84),_15);
}else{
if(cmd=="decr"){
arg=eval("("+arg+")");
var _85=arg.ciphertext;
var _83=arg.password;
var _84=_7d(_85,_83);
gearsWorkerPool.sendMessage(String(_84),_15);
}
}
}});
}
