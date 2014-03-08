/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.oo.Decorator"]){
dojo._hasResource["dojox.lang.oo.Decorator"]=true;
dojo.provide("dojox.lang.oo.Decorator");
(function(){
var oo=dojox.lang.oo,D=oo.Decorator=function(_3,_4){
this.value=_3;
this.decorator=typeof _4=="object"?function(){
return _4.exec.apply(_4,arguments);
}:_4;
};
oo.makeDecorator=function(_5){
return function(_6){
return new D(_6,_5);
};
};
})();
}
