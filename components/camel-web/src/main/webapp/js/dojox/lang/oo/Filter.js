/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.oo.Filter"]){
dojo._hasResource["dojox.lang.oo.Filter"]=true;
dojo.provide("dojox.lang.oo.Filter");
(function(){
var oo=dojox.lang.oo,F=oo.Filter=function(_3,_4){
this.bag=_3;
this.filter=typeof _4=="object"?function(){
return _4.exec.apply(_4,arguments);
}:_4;
};
var _5=function(_6){
this.map=_6;
};
_5.prototype.exec=function(_7){
return this.map.hasOwnProperty(_7)?this.map[_7]:_7;
};
oo.filter=function(_8,_9){
return new F(_8,new _5(_9));
};
})();
}
