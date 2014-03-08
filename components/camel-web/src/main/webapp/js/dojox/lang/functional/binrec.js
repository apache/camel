/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.binrec"]){
dojo._hasResource["dojox.lang.functional.binrec"]=true;
dojo.provide("dojox.lang.functional.binrec");
dojo.require("dojox.lang.functional.lambda");
dojo.require("dojox.lang.functional.util");
(function(){
var df=dojox.lang.functional,_2=df.inlineLambda,_x="_x",_4=["_z.r","_r","_z.a"];
df.binrec=function(_5,_6,_7,_8){
var c,t,b,a,cs,ts,bs,as,_11={},_12={},_13=function(x){
_11[x]=1;
};
if(typeof _5=="string"){
cs=_2(_5,_x,_13);
}else{
c=df.lambda(_5);
cs="_c.apply(this, _x)";
_12["_c=_t.c"]=1;
}
if(typeof _6=="string"){
ts=_2(_6,_x,_13);
}else{
t=df.lambda(_6);
ts="_t.apply(this, _x)";
}
if(typeof _7=="string"){
bs=_2(_7,_x,_13);
}else{
b=df.lambda(_7);
bs="_b.apply(this, _x)";
_12["_b=_t.b"]=1;
}
if(typeof _8=="string"){
as=_2(_8,_4,_13);
}else{
a=df.lambda(_8);
as="_a.call(this, _z.r, _r, _z.a)";
_12["_a=_t.a"]=1;
}
var _15=df.keys(_11),_16=df.keys(_12),f=new Function([],"var _x=arguments,_y,_z,_r".concat(_15.length?","+_15.join(","):"",_16.length?",_t=_x.callee,"+_16.join(","):"",t?(_16.length?",_t=_t.t":"_t=_x.callee.t"):"",";while(!",cs,"){_r=",bs,";_y={p:_y,a:_r[1]};_z={p:_z,a:_x};_x=_r[0]}for(;;){do{_r=",ts,";if(!_z)return _r;while(\"r\" in _z){_r=",as,";if(!(_z=_z.p))return _r}_z.r=_r;_x=_y.a;_y=_y.p}while(",cs,");do{_r=",bs,";_y={p:_y,a:_r[1]};_z={p:_z,a:_x};_x=_r[0]}while(!",cs,")}"));
if(c){
f.c=c;
}
if(t){
f.t=t;
}
if(b){
f.b=b;
}
if(a){
f.a=a;
}
return f;
};
})();
}
