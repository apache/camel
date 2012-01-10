/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.oo.aop"]){
dojo._hasResource["dojox.lang.oo.aop"]=true;
dojo.provide("dojox.lang.oo.aop");
dojo.require("dojox.lang.oo.Decorator");
dojo.require("dojox.lang.oo.chain");
dojo.require("dojox.lang.oo.general");
(function(){
var oo=dojox.lang.oo,md=oo.makeDecorator,_3=oo.aop;
_3.before=oo.chain.before;
_3.around=oo.general.wrap;
_3.afterReturning=md(function(_4,_5,_6){
return dojo.isFunction(_6)?function(){
var _7=_6.apply(this,arguments);
_5.call(this,_7);
return _7;
}:function(){
_5.call(this);
};
});
_3.afterThrowing=md(function(_8,_9,_a){
return dojo.isFunction(_a)?function(){
var _b;
try{
_b=_a.apply(this,arguments);
}
catch(e){
_9.call(this,e);
throw e;
}
return _b;
}:_a;
});
_3.after=md(function(_c,_d,_e){
return dojo.isFunction(_e)?function(){
var _f;
try{
_f=_e.apply(this,arguments);
}
finally{
_d.call(this);
}
return _f;
}:function(){
_d.call(this);
};
});
})();
}
