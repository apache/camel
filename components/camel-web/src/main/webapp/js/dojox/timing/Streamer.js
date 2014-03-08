/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.timing.Streamer"]){
dojo._hasResource["dojox.timing.Streamer"]=true;
dojo.provide("dojox.timing.Streamer");
dojo.require("dojox.timing._base");
dojox.timing.Streamer=function(_1,_2,_3,_4,_5){
var _6=this;
var _7=[];
this.interval=_3||1000;
this.minimumSize=_4||10;
this.inputFunction=_1||function(q){
};
this.outputFunction=_2||function(_9){
};
var _a=new dojox.timing.Timer(this.interval);
var _b=function(){
_6.onTick(_6);
if(_7.length<_6.minimumSize){
_6.inputFunction(_7);
}
var _c=_7.shift();
while(typeof (_c)=="undefined"&&_7.length>0){
_c=_7.shift();
}
if(typeof (_c)=="undefined"){
_6.stop();
return;
}
_6.outputFunction(_c);
};
this.setInterval=function(ms){
this.interval=ms;
_a.setInterval(ms);
};
this.onTick=function(_e){
};
this.start=function(){
if(typeof (this.inputFunction)=="function"&&typeof (this.outputFunction)=="function"){
_a.start();
return;
}
throw new Error("You cannot start a Streamer without an input and an output function.");
};
this.onStart=function(){
};
this.stop=function(){
_a.stop();
};
this.onStop=function(){
};
_a.onTick=this.tick;
_a.onStart=this.onStart;
_a.onStop=this.onStop;
if(_5){
_7.concat(_5);
}
};
}
