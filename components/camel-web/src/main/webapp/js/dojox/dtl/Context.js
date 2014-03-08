/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.Context"]){
dojo._hasResource["dojox.dtl.Context"]=true;
dojo.provide("dojox.dtl.Context");
dojo.require("dojox.dtl._base");
dojox.dtl.Context=dojo.extend(function(_1){
this._this={};
dojox.dtl._Context.call(this,_1);
},dojox.dtl._Context.prototype,{getKeys:function(){
var _2=[];
for(var _3 in this){
if(this.hasOwnProperty(_3)&&_3!="_dicts"&&_3!="_this"){
_2.push(_3);
}
}
return _2;
},extend:function(_4){
return dojo.delegate(this,_4);
},filter:function(_5){
var _6=new dojox.dtl.Context();
var _7=[];
var i,_9;
if(_5 instanceof dojox.dtl.Context){
_7=_5.getKeys();
}else{
if(typeof _5=="object"){
for(var _a in _5){
_7.push(_a);
}
}else{
for(i=0;_9=arguments[i];i++){
if(typeof _9=="string"){
_7.push(_9);
}
}
}
}
for(i=0,_a;_a=_7[i];i++){
_6[_a]=this[_a];
}
return _6;
},setThis:function(_b){
this._this=_b;
},getThis:function(){
return this._this;
},hasKey:function(_c){
if(typeof this[_c]!="undefined"){
return true;
}
for(var i=0,_e;_e=this._dicts[i];i++){
if(typeof _e[_c]!="undefined"){
return true;
}
}
return false;
}});
}
