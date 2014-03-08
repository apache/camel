/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.timing.ThreadPool"]){
dojo._hasResource["dojox.timing.ThreadPool"]=true;
dojo.provide("dojox.timing.ThreadPool");
dojo.require("dojox.timing");
dojo.experimental("dojox.timing.ThreadPool");
(function(){
var t=dojox.timing;
t.threadStates={UNSTARTED:"unstarted",STOPPED:"stopped",PENDING:"pending",RUNNING:"running",SUSPENDED:"suspended",WAITING:"waiting",COMPLETE:"complete",ERROR:"error"};
t.threadPriorities={LOWEST:1,BELOWNORMAL:2,NORMAL:3,ABOVENORMAL:4,HIGHEST:5};
t.Thread=function(fn,_3){
var _4=this;
this.state=t.threadStates.UNSTARTED;
this.priority=_3||t.threadPriorities.NORMAL;
this.lastError=null;
this.func=fn;
this.invoke=function(){
_4.state=t.threadStates.RUNNING;
try{
fn(this);
_4.state=t.threadStates.COMPLETE;
}
catch(e){
_4.lastError=e;
_4.state=t.threadStates.ERROR;
}
};
};
t.ThreadPool=new (function(_5,_6){
var _7=this;
var _8=_5;
var _9=_8;
var _a=_6;
var _b=Math.floor((_a/2)/_8);
var _c=[];
var _d=new Array(_8+1);
var _e=new dojox.timing.Timer();
var _f=function(){
var _10=_d[0]={};
for(var i=0;i<_d.length;i++){
window.clearTimeout(_d[i]);
var _12=_c.shift();
if(typeof (_12)=="undefined"){
break;
}
_10["thread-"+i]=_12;
_d[i]=window.setTimeout(_12.invoke,(_b*i));
}
_9=_8-(i-1);
};
this.getMaxThreads=function(){
return _8;
};
this.getAvailableThreads=function(){
return _9;
};
this.getTickInterval=function(){
return _a;
};
this.queueUserWorkItem=function(fn){
var _14=fn;
if(_14 instanceof Function){
_14=new t.Thread(_14);
}
var idx=_c.length;
for(var i=0;i<_c.length;i++){
if(_c[i].priority<_14.priority){
idx=i;
break;
}
}
if(idx<_c.length){
_c.splice(idx,0,_14);
}else{
_c.push(_14);
}
return true;
};
this.removeQueuedUserWorkItem=function(_17){
if(_17 instanceof Function){
var idx=-1;
for(var i=0;i<_c.length;i++){
if(_c[i].func==_17){
idx=i;
break;
}
}
if(idx>-1){
_c.splice(idx,1);
return true;
}
return false;
}
var idx=-1;
for(var i=0;i<_c.length;i++){
if(_c[i]==_17){
idx=i;
break;
}
}
if(idx>-1){
_c.splice(idx,1);
return true;
}
return false;
};
this.start=function(){
_e.start();
};
this.stop=function(){
_e.stop();
};
this.abort=function(){
this.stop();
for(var i=1;i<_d.length;i++){
if(_d[i]){
window.clearTimeout(_d[i]);
}
}
for(var _1b in _d[0]){
this.queueUserWorkItem(_1b);
}
_d[0]={};
};
this.reset=function(){
this.abort();
_c=[];
};
this.sleep=function(_1c){
_e.stop();
window.setTimeout(_e.start,_1c);
};
_e.onTick=_7.invoke;
})(16,5000);
})();
}
