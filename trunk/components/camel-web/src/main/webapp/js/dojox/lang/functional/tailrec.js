/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.tailrec"]){
dojo._hasResource["dojox.lang.functional.tailrec"]=true;
dojo.provide("dojox.lang.functional.tailrec");
dojo.require("dojox.lang.functional.lambda");
dojo.require("dojox.lang.functional.util");
(function(){
var df=dojox.lang.functional,_2=df.inlineLambda,_x="_x";
df.tailrec=function(_4,_5,_6){
var c,t,b,cs,ts,bs,_d={},_e={},_f=function(x){
_d[x]=1;
};
if(typeof _4=="string"){
cs=_2(_4,_x,_f);
}else{
c=df.lambda(_4);
cs="_c.apply(this, _x)";
_e["_c=_t.c"]=1;
}
if(typeof _5=="string"){
ts=_2(_5,_x,_f);
}else{
t=df.lambda(_5);
ts="_t.t.apply(this, _x)";
}
if(typeof _6=="string"){
bs=_2(_6,_x,_f);
}else{
b=df.lambda(_6);
bs="_b.apply(this, _x)";
_e["_b=_t.b"]=1;
}
var _11=df.keys(_d),_12=df.keys(_e),f=new Function([],"var _x=arguments,_t=_x.callee,_c=_t.c,_b=_t.b".concat(_11.length?","+_11.join(","):"",_12.length?",_t=_x.callee,"+_12.join(","):t?",_t=_x.callee":"",";for(;!",cs,";_x=",bs,");return ",ts));
if(c){
f.c=c;
}
if(t){
f.t=t;
}
if(b){
f.b=b;
}
return f;
};
})();
}
