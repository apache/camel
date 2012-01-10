/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.collections._base"]){
dojo._hasResource["dojox.collections._base"]=true;
dojo.provide("dojox.collections._base");
dojox.collections.DictionaryEntry=function(k,v){
this.key=k;
this.value=v;
this.valueOf=function(){
return this.value;
};
this.toString=function(){
return String(this.value);
};
};
dojox.collections.Iterator=function(_3){
var a=_3;
var _5=0;
this.element=a[_5]||null;
this.atEnd=function(){
return (_5>=a.length);
};
this.get=function(){
if(this.atEnd()){
return null;
}
this.element=a[_5++];
return this.element;
};
this.map=function(fn,_7){
return dojo.map(a,fn,_7);
};
this.reset=function(){
_5=0;
this.element=a[_5];
};
};
dojox.collections.DictionaryIterator=function(_8){
var a=[];
var _a={};
for(var p in _8){
if(!_a[p]){
a.push(_8[p]);
}
}
var _c=0;
this.element=a[_c]||null;
this.atEnd=function(){
return (_c>=a.length);
};
this.get=function(){
if(this.atEnd()){
return null;
}
this.element=a[_c++];
return this.element;
};
this.map=function(fn,_e){
return dojo.map(a,fn,_e);
};
this.reset=function(){
_c=0;
this.element=a[_c];
};
};
}
