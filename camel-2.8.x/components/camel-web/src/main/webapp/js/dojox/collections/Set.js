/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.collections.Set"]){
dojo._hasResource["dojox.collections.Set"]=true;
dojo.provide("dojox.collections.Set");
dojo.require("dojox.collections.ArrayList");
(function(){
var _1=dojox.collections;
_1.Set=new (function(){
function _2(_3){
if(_3.constructor==Array){
return new dojox.collections.ArrayList(_3);
}
return _3;
};
this.union=function(_4,_5){
_4=_2(_4);
_5=_2(_5);
var _6=new dojox.collections.ArrayList(_4.toArray());
var e=_5.getIterator();
while(!e.atEnd()){
var _8=e.get();
if(!_6.contains(_8)){
_6.add(_8);
}
}
return _6;
};
this.intersection=function(_9,_a){
_9=_2(_9);
_a=_2(_a);
var _b=new dojox.collections.ArrayList();
var e=_a.getIterator();
while(!e.atEnd()){
var _d=e.get();
if(_9.contains(_d)){
_b.add(_d);
}
}
return _b;
};
this.difference=function(_e,_f){
_e=_2(_e);
_f=_2(_f);
var _10=new dojox.collections.ArrayList();
var e=_e.getIterator();
while(!e.atEnd()){
var _12=e.get();
if(!_f.contains(_12)){
_10.add(_12);
}
}
return _10;
};
this.isSubSet=function(_13,_14){
_13=_2(_13);
_14=_2(_14);
var e=_13.getIterator();
while(!e.atEnd()){
if(!_14.contains(e.get())){
return false;
}
}
return true;
};
this.isSuperSet=function(_16,_17){
_16=_2(_16);
_17=_2(_17);
var e=_17.getIterator();
while(!e.atEnd()){
if(!_16.contains(e.get())){
return false;
}
}
return true;
};
})();
})();
}
