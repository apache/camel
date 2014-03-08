/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.uuid.generateTimeBasedUuid"]){
dojo._hasResource["dojox.uuid.generateTimeBasedUuid"]=true;
dojo.provide("dojox.uuid.generateTimeBasedUuid");
dojox.uuid.generateTimeBasedUuid=function(_1){
var _2=dojox.uuid.generateTimeBasedUuid._generator.generateUuidString(_1);
return _2;
};
dojox.uuid.generateTimeBasedUuid.isValidNode=function(_3){
var _4=16;
var _5=parseInt(_3,_4);
var _6=dojo.isString(_3)&&_3.length==12&&isFinite(_5);
return _6;
};
dojox.uuid.generateTimeBasedUuid.setNode=function(_7){
dojox.uuid.assert((_7===null)||this.isValidNode(_7));
this._uniformNode=_7;
};
dojox.uuid.generateTimeBasedUuid.getNode=function(){
return this._uniformNode;
};
dojox.uuid.generateTimeBasedUuid._generator=new function(){
this.GREGORIAN_CHANGE_OFFSET_IN_HOURS=3394248;
var _8=null;
var _9=null;
var _a=null;
var _b=0;
var _c=null;
var _d=null;
var _e=16;
function _f(_10){
_10[2]+=_10[3]>>>16;
_10[3]&=65535;
_10[1]+=_10[2]>>>16;
_10[2]&=65535;
_10[0]+=_10[1]>>>16;
_10[1]&=65535;
dojox.uuid.assert((_10[0]>>>16)===0);
};
function _11(x){
var _13=new Array(0,0,0,0);
_13[3]=x%65536;
x-=_13[3];
x/=65536;
_13[2]=x%65536;
x-=_13[2];
x/=65536;
_13[1]=x%65536;
x-=_13[1];
x/=65536;
_13[0]=x;
return _13;
};
function _14(_15,_16){
dojox.uuid.assert(dojo.isArray(_15));
dojox.uuid.assert(dojo.isArray(_16));
dojox.uuid.assert(_15.length==4);
dojox.uuid.assert(_16.length==4);
var _17=new Array(0,0,0,0);
_17[3]=_15[3]+_16[3];
_17[2]=_15[2]+_16[2];
_17[1]=_15[1]+_16[1];
_17[0]=_15[0]+_16[0];
_f(_17);
return _17;
};
function _18(_19,_1a){
dojox.uuid.assert(dojo.isArray(_19));
dojox.uuid.assert(dojo.isArray(_1a));
dojox.uuid.assert(_19.length==4);
dojox.uuid.assert(_1a.length==4);
var _1b=false;
if(_19[0]*_1a[0]!==0){
_1b=true;
}
if(_19[0]*_1a[1]!==0){
_1b=true;
}
if(_19[0]*_1a[2]!==0){
_1b=true;
}
if(_19[1]*_1a[0]!==0){
_1b=true;
}
if(_19[1]*_1a[1]!==0){
_1b=true;
}
if(_19[2]*_1a[0]!==0){
_1b=true;
}
dojox.uuid.assert(!_1b);
var _1c=new Array(0,0,0,0);
_1c[0]+=_19[0]*_1a[3];
_f(_1c);
_1c[0]+=_19[1]*_1a[2];
_f(_1c);
_1c[0]+=_19[2]*_1a[1];
_f(_1c);
_1c[0]+=_19[3]*_1a[0];
_f(_1c);
_1c[1]+=_19[1]*_1a[3];
_f(_1c);
_1c[1]+=_19[2]*_1a[2];
_f(_1c);
_1c[1]+=_19[3]*_1a[1];
_f(_1c);
_1c[2]+=_19[2]*_1a[3];
_f(_1c);
_1c[2]+=_19[3]*_1a[2];
_f(_1c);
_1c[3]+=_19[3]*_1a[3];
_f(_1c);
return _1c;
};
function _1d(_1e,_1f){
while(_1e.length<_1f){
_1e="0"+_1e;
}
return _1e;
};
function _20(){
var _21=Math.floor((Math.random()%1)*Math.pow(2,32));
var _22=_21.toString(_e);
while(_22.length<8){
_22="0"+_22;
}
return _22;
};
this.generateUuidString=function(_23){
if(_23){
dojox.uuid.assert(dojox.uuid.generateTimeBasedUuid.isValidNode(_23));
}else{
if(dojox.uuid.generateTimeBasedUuid._uniformNode){
_23=dojox.uuid.generateTimeBasedUuid._uniformNode;
}else{
if(!_8){
var _24=32768;
var _25=Math.floor((Math.random()%1)*Math.pow(2,15));
var _26=(_24|_25).toString(_e);
_8=_26+_20();
}
_23=_8;
}
}
if(!_9){
var _27=32768;
var _28=Math.floor((Math.random()%1)*Math.pow(2,14));
_9=(_27|_28).toString(_e);
}
var now=new Date();
var _2a=now.valueOf();
var _2b=_11(_2a);
if(!_c){
var _2c=_11(60*60);
var _2d=_11(dojox.uuid.generateTimeBasedUuid._generator.GREGORIAN_CHANGE_OFFSET_IN_HOURS);
var _2e=_18(_2d,_2c);
var _2f=_11(1000);
_c=_18(_2e,_2f);
_d=_11(10000);
}
var _30=_2b;
var _31=_14(_c,_30);
var _32=_18(_31,_d);
if(now.valueOf()==_a){
_32[3]+=_b;
_f(_32);
_b+=1;
if(_b==10000){
while(now.valueOf()==_a){
now=new Date();
}
}
}else{
_a=now.valueOf();
_b=1;
}
var _33=_32[2].toString(_e);
var _34=_32[3].toString(_e);
var _35=_1d(_33,4)+_1d(_34,4);
var _36=_32[1].toString(_e);
_36=_1d(_36,4);
var _37=_32[0].toString(_e);
_37=_1d(_37,3);
var _38="-";
var _39="1";
var _3a=_35+_38+_36+_38+_39+_37+_38+_9+_38+_23;
_3a=_3a.toLowerCase();
return _3a;
};
}();
}
