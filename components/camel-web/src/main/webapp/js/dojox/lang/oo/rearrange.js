/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.oo.rearrange"]){
dojo._hasResource["dojox.lang.oo.rearrange"]=true;
dojo.provide("dojox.lang.oo.rearrange");
dojox.lang.oo.rearrange=function(_1,_2){
for(var _3 in _2){
if(_2.hasOwnProperty(_3)&&_3 in _1){
var _4=_2[_3],_5=_1[_3];
if(!(delete _1[_3])){
_1[_3]=undefined;
}
if(_4){
_1[_4]=_5;
}
}
}
return _1;
};
}
