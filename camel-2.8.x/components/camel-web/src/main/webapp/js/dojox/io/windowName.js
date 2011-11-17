/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.io.windowName"]){
dojo._hasResource["dojox.io.windowName"]=true;
dojo.provide("dojox.io.windowName");
dojox.io.windowName={send:function(_1,_2){
_2.url+=(_2.url.match(/\?/)?"&":"?")+"windowname="+(_2.authElement?"auth":true);
var _3=_2.authElement;
var _4=function(_5){
try{
var _6=_7.ioArgs.frame.contentWindow.document;
_6.write(" ");
_6.close();
}
catch(e){
}
(_3||dojo.body()).removeChild(_7.ioArgs.outerFrame);
return _5;
};
var _7=dojo._ioSetArgs(_2,_4,_4,_4);
if(_2.timeout){
setTimeout(function(){
if(_7.fired==-1){
_7.callback(new Error("Timeout"));
}
},_2.timeout);
}
var _8=dojox.io.windowName;
if(dojo.body()){
_8._send(_7,_1,_3,_2.onAuthLoad);
}else{
dojo.addOnLoad(function(){
_8._send(_7,_1,_3,_2.onAuthLoad);
});
}
return _7;
},_send:function(_9,_a,_b,_c){
var _d=_9.ioArgs;
var _e=dojox.io.windowName._frameNum++;
var _f=(dojo.config["dojoCallbackUrl"]||dojo.moduleUrl("dojo","resources/blank.html"))+"#"+_e;
var _10=new dojo._Url(window.location,_f);
var doc=dojo.doc;
var _12=_b||dojo.body();
function _13(_14){
_14.style.width="100%";
_14.style.height="100%";
_14.style.border="0px";
};
if(dojo.isMoz&&![].reduce){
var _15=doc.createElement("iframe");
_13(_15);
if(!_b){
_15.style.display="none";
}
_12.appendChild(_15);
var _16=_15.contentWindow;
doc=_16.document;
doc.write("<html><body margin='0px'><iframe style='width:100%;height:100%;border:0px' name='protectedFrame'></iframe></body></html>");
doc.close();
var _17=_16[0];
_16.__defineGetter__(0,function(){
});
_16.__defineGetter__("protectedFrame",function(){
});
doc=_17.document;
doc.write("<html><body margin='0px'></body></html>");
doc.close();
_12=doc.body;
}
var _18=_d.frame=_18=doc.createElement(dojo.isIE?"<iframe name=\""+_10+"\" onload=\"dojox.io.windowName["+_e+"]()\">":"iframe");
_13(_18);
_d.outerFrame=_15=_15||_18;
if(!_b){
_15.style.display="none";
}
var _19=0;
function _1a(){
var _1b=_18.contentWindow.name;
if(typeof _1b=="string"){
if(_1b!=_10){
_19=2;
_9.ioArgs.hash=_18.contentWindow.location.hash;
_9.callback(_1b);
}
}
};
dojox.io.windowName[_e]=_18.onload=function(){
try{
if(!dojo.isMoz&&_18.contentWindow.location=="about:blank"){
return;
}
}
catch(e){
}
if(!_19){
_19=1;
if(_b){
if(_c){
_c();
}
}else{
_18.contentWindow.location=_f;
}
}
try{
if(_19<2){
_1a();
}
}
catch(e){
}
};
_18.name=_10;
if(_a.match(/GET/i)){
dojo._ioAddQueryToUrl(_d);
_18.src=_d.url;
_12.appendChild(_18);
if(_18.contentWindow){
_18.contentWindow.location.replace(_d.url);
}
}else{
if(_a.match(/POST/i)){
_12.appendChild(_18);
var _1c=dojo.doc.createElement("form");
dojo.body().appendChild(_1c);
var _1d=dojo.queryToObject(_d.query);
for(var i in _1d){
var _1f=_1d[i];
_1f=_1f instanceof Array?_1f:[_1f];
for(var j=0;j<_1f.length;j++){
var _21=doc.createElement("input");
_21.type="hidden";
_21.name=i;
_21.value=_1f[j];
_1c.appendChild(_21);
}
}
_1c.method="POST";
_1c.action=_d.url;
_1c.target=_10;
_1c.submit();
_1c.parentNode.removeChild(_1c);
}else{
throw new Error("Method "+_a+" not supported with the windowName transport");
}
}
if(_18.contentWindow){
_18.contentWindow.name=_10;
}
},_frameNum:0};
}
