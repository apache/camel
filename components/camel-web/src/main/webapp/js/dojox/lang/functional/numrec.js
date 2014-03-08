/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.numrec"]){
dojo._hasResource["dojox.lang.functional.numrec"]=true;
dojo.provide("dojox.lang.functional.numrec");
dojo.require("dojox.lang.functional.lambda");
dojo.require("dojox.lang.functional.util");
(function(){
var df=dojox.lang.functional,_2=df.inlineLambda,_3=["_r","_i"];
df.numrec=function(_4,_5){
var a,as,_8={},_9=function(x){
_8[x]=1;
};
if(typeof _5=="string"){
as=_2(_5,_3,_9);
}else{
a=df.lambda(_5);
as="_a.call(this, _r, _i)";
}
var _b=df.keys(_8),f=new Function(["_x"],"var _t=arguments.callee,_r=_t.t,_i".concat(_b.length?","+_b.join(","):"",a?",_a=_t.a":"",";for(_i=1;_i<=_x;++_i){_r=",as,"}return _r"));
f.t=_4;
if(a){
f.a=a;
}
return f;
};
})();
}
