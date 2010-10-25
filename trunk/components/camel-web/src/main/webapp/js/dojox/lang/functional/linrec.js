/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.linrec"]){
dojo._hasResource["dojox.lang.functional.linrec"]=true;
dojo.provide("dojox.lang.functional.linrec");
dojo.require("dojox.lang.functional.lambda");
dojo.require("dojox.lang.functional.util");
(function(){
var df=dojox.lang.functional,_2=df.inlineLambda,_x="_x",_4=["_r","_y.a"];
df.linrec=function(_5,_6,_7,_8){
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
ts="_t.t.apply(this, _x)";
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
as="_a.call(this, _r, _y.a)";
_12["_a=_t.a"]=1;
}
var _15=df.keys(_11),_16=df.keys(_12),f=new Function([],"var _x=arguments,_y,_r".concat(_15.length?","+_15.join(","):"",_16.length?",_t=_x.callee,"+_16.join(","):t?",_t=_x.callee":"",";for(;!",cs,";_x=",bs,"){_y={p:_y,a:_x}}_r=",ts,";for(;_y;_y=_y.p){_r=",as,"}return _r"));
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
