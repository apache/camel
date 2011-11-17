/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.oo.general"]){
dojo._hasResource["dojox.lang.oo.general"]=true;
dojo.provide("dojox.lang.oo.general");
dojo.require("dojox.lang.oo.Decorator");
(function(){
var oo=dojox.lang.oo,md=oo.makeDecorator,_3=oo.general;
_3.augment=md(function(_4,_5,_6){
return typeof _6=="undefined"?_5:_6;
});
_3.override=md(function(_7,_8,_9){
return typeof _9!="undefined"?_8:_9;
});
_3.shuffle=md(function(_a,_b,_c){
return dojo.isFunction(_c)?function(){
return _c.apply(this,_b.apply(this,arguments));
}:_c;
});
_3.wrap=md(function(_d,_e,_f){
return function(){
return _e.call(this,_f,arguments);
};
});
_3.tap=md(function(_10,_11,_12){
return function(){
_11.apply(this,arguments);
return this;
};
});
_3.before=md(function(_13,_14,_15){
return dojo.isFunction(_15)?function(){
_14.apply(this,arguments);
return _15.apply(this,arguments);
}:_14;
});
_3.after=md(function(_16,_17,_18){
return dojo.isFunction(_18)?function(){
_18.apply(this,arguments);
return _17.apply(this,arguments);
}:_17;
});
})();
}
