/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.cometd.timesync"]){
dojo._hasResource["dojox.cometd.timesync"]=true;
dojo.provide("dojox.cometd.timesync");
dojo.require("dojox.cometd._base");
dojox.cometd.timesync=new function(){
this._window=10;
this._minWindow=4;
this._offsets=[];
this.offset=0;
this.samples=0;
this.getServerTime=function(){
return new Date().getTime()+this.offset;
};
this.getServerDate=function(){
return new Date(this.getServerTime());
};
this.setTimeout=function(_1,_2){
var ts=(_2 instanceof Date)?_2.getTime():(0+_2);
var tc=ts-this.offset;
var _5=tc-new Date().getTime();
if(_5<=0){
_5=1;
}
return setTimeout(_1,_5);
};
this._in=function(_6){
var _7=_6.channel;
if(_7&&_7.indexOf("/meta/")==0){
if(_6.ext&&_6.ext.timesync){
var _8=_6.ext.timesync;
var _9=new Date().getTime();
this._offsets.push(_8.ts-_8.tc-(_9-_8.tc-_8.p)/2);
if(this._offsets.length>this._window){
this._offsets.shift();
}
this.samples++;
var _a=0;
for(var i in this._offsets){
_a+=this._offsets[i];
}
this.offset=parseInt((_a/this._offsets.length).toFixed());
if(this.samples<this._minWindow){
setTimeout(dojox._scopeName+".cometd.publish('/meta/ping',null)",100);
}
}
}
return _6;
};
this._out=function(_c){
var _d=_c.channel;
if(_d&&_d.indexOf("/meta/")==0){
var _e=new Date().getTime();
if(!_c.ext){
_c.ext={};
}
_c.ext.timesync={tc:_e};
}
return _c;
};
};
dojox.cometd._extendInList.push(dojo.hitch(dojox.cometd.timesync,"_in"));
dojox.cometd._extendOutList.push(dojo.hitch(dojox.cometd.timesync,"_out"));
}
