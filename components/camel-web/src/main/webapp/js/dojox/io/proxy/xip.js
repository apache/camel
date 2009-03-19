/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.io.proxy.xip"]){
dojo._hasResource["dojox.io.proxy.xip"]=true;
dojo.provide("dojox.io.proxy.xip");
dojo.require("dojo.io.iframe");
dojo.require("dojox.data.dom");
dojox.io.proxy.xip={xipClientUrl:((dojo.config||djConfig)["xipClientUrl"])||dojo.moduleUrl("dojox.io.proxy","xip_client.html"),urlLimit:4000,_callbackName:(dojox._scopeName||"dojox")+".io.proxy.xip.fragmentReceived",_state:{},_stateIdCounter:0,_isWebKit:navigator.userAgent.indexOf("WebKit")!=-1,send:function(_1){
var _2=this.xipClientUrl;
if(_2.split(":")[0].match(/javascript/i)||_1._ifpServerUrl.split(":")[0].match(/javascript/i)){
return;
}
var _3=_2.indexOf(":");
var _4=_2.indexOf("/");
if(_3==-1||_4<_3){
var _5=window.location.href;
if(_4==0){
_2=_5.substring(0,_5.indexOf("/",9))+_2;
}else{
_2=_5.substring(0,(_5.lastIndexOf("/")+1))+_2;
}
}
this.fullXipClientUrl=_2;
if(typeof document.postMessage!="undefined"){
document.addEventListener("message",dojo.hitch(this,this.fragmentReceivedEvent),false);
}
this.send=this._realSend;
return this._realSend(_1);
},_realSend:function(_6){
var _7="XhrIframeProxy"+(this._stateIdCounter++);
_6._stateId=_7;
var _8=_6._ifpServerUrl+"#0:init:id="+_7+"&client="+encodeURIComponent(this.fullXipClientUrl)+"&callback="+encodeURIComponent(this._callbackName);
this._state[_7]={facade:_6,stateId:_7,clientFrame:dojo.io.iframe.create(_7,"",_8),isSending:false,serverUrl:_6._ifpServerUrl,requestData:null,responseMessage:"",requestParts:[],idCounter:1,partIndex:0,serverWindow:null};
return _7;
},receive:function(_9,_a){
var _b={};
var _c=_a.split("&");
for(var i=0;i<_c.length;i++){
if(_c[i]){
var _e=_c[i].split("=");
_b[decodeURIComponent(_e[0])]=decodeURIComponent(_e[1]);
}
}
var _f=this._state[_9];
var _10=_f.facade;
_10._setResponseHeaders(_b.responseHeaders);
if(_b.status==0||_b.status){
_10.status=parseInt(_b.status,10);
}
if(_b.statusText){
_10.statusText=_b.statusText;
}
if(_b.responseText){
_10.responseText=_b.responseText;
var _11=_10.getResponseHeader("Content-Type");
if(_11){
var _12=_11.split(";")[0];
if(_12.indexOf("application/xml")==0||_12.indexOf("text/xml")==0){
_10.responseXML=dojox.data.dom.createDocument(_b.responseText,_11);
}
}
}
_10.readyState=4;
this.destroyState(_9);
},frameLoaded:function(_13){
var _14=this._state[_13];
var _15=_14.facade;
var _16=[];
for(var _17 in _15._requestHeaders){
_16.push(_17+": "+_15._requestHeaders[_17]);
}
var _18={uri:_15._uri};
if(_16.length>0){
_18.requestHeaders=_16.join("\r\n");
}
if(_15._method){
_18.method=_15._method;
}
if(_15._bodyData){
_18.data=_15._bodyData;
}
this.sendRequest(_13,dojo.objectToQuery(_18));
},destroyState:function(_19){
var _1a=this._state[_19];
if(_1a){
delete this._state[_19];
var _1b=_1a.clientFrame.parentNode;
_1b.removeChild(_1a.clientFrame);
_1a.clientFrame=null;
_1a=null;
}
},createFacade:function(){
if(arguments&&arguments[0]&&arguments[0].iframeProxyUrl){
return new dojox.io.proxy.xip.XhrIframeFacade(arguments[0].iframeProxyUrl);
}else{
return dojox.io.proxy.xip._xhrObjOld.apply(dojo,arguments);
}
},sendRequest:function(_1c,_1d){
var _1e=this._state[_1c];
if(!_1e.isSending){
_1e.isSending=true;
_1e.requestData=_1d||"";
_1e.serverWindow=frames[_1e.stateId];
if(!_1e.serverWindow){
_1e.serverWindow=document.getElementById(_1e.stateId).contentWindow;
}
if(typeof document.postMessage=="undefined"){
if(_1e.serverWindow.contentWindow){
_1e.serverWindow=_1e.serverWindow.contentWindow;
}
}
this.sendRequestStart(_1c);
}
},sendRequestStart:function(_1f){
var _20=this._state[_1f];
_20.requestParts=[];
var _21=_20.requestData;
var _22=_20.serverUrl.length;
var _23=this.urlLimit-_22;
var _24=0;
while((_21.length-_24)+_22>this.urlLimit){
var _25=_21.substring(_24,_24+_23);
var _26=_25.lastIndexOf("%");
if(_26==_25.length-1||_26==_25.length-2){
_25=_25.substring(0,_26);
}
_20.requestParts.push(_25);
_24+=_25.length;
}
_20.requestParts.push(_21.substring(_24,_21.length));
_20.partIndex=0;
this.sendRequestPart(_1f);
},sendRequestPart:function(_27){
var _28=this._state[_27];
if(_28.partIndex<_28.requestParts.length){
var _29=_28.requestParts[_28.partIndex];
var cmd="part";
if(_28.partIndex+1==_28.requestParts.length){
cmd="end";
}else{
if(_28.partIndex==0){
cmd="start";
}
}
this.setServerUrl(_27,cmd,_29);
_28.partIndex++;
}
},setServerUrl:function(_2b,cmd,_2d){
var _2e=this.makeServerUrl(_2b,cmd,_2d);
var _2f=this._state[_2b];
if(this._isWebKit){
_2f.serverWindow.location=_2e;
}else{
_2f.serverWindow.location.replace(_2e);
}
},makeServerUrl:function(_30,cmd,_32){
var _33=this._state[_30];
var _34=_33.serverUrl+"#"+(_33.idCounter++)+":"+cmd;
if(_32){
_34+=":"+_32;
}
return _34;
},fragmentReceivedEvent:function(evt){
if(evt.uri.split("#")[0]==this.fullXipClientUrl){
this.fragmentReceived(evt.data);
}
},fragmentReceived:function(_36){
var _37=_36.indexOf("#");
var _38=_36.substring(0,_37);
var _39=_36.substring(_37+1,_36.length);
var msg=this.unpackMessage(_39);
var _3b=this._state[_38];
switch(msg.command){
case "loaded":
this.frameLoaded(_38);
break;
case "ok":
this.sendRequestPart(_38);
break;
case "start":
_3b.responseMessage=""+msg.message;
this.setServerUrl(_38,"ok");
break;
case "part":
_3b.responseMessage+=msg.message;
this.setServerUrl(_38,"ok");
break;
case "end":
this.setServerUrl(_38,"ok");
_3b.responseMessage+=msg.message;
this.receive(_38,_3b.responseMessage);
break;
}
},unpackMessage:function(_3c){
var _3d=_3c.split(":");
var _3e=_3d[1];
_3c=_3d[2]||"";
var _3f=null;
if(_3e=="init"){
var _40=_3c.split("&");
_3f={};
for(var i=0;i<_40.length;i++){
var _42=_40[i].split("=");
_3f[decodeURIComponent(_42[0])]=decodeURIComponent(_42[1]);
}
}
return {command:_3e,message:_3c,config:_3f};
}};
dojox.io.proxy.xip._xhrObjOld=dojo._xhrObj;
dojo._xhrObj=dojox.io.proxy.xip.createFacade;
dojox.io.proxy.xip.XhrIframeFacade=function(_43){
this._requestHeaders={};
this._allResponseHeaders=null;
this._responseHeaders={};
this._method=null;
this._uri=null;
this._bodyData=null;
this.responseText=null;
this.responseXML=null;
this.status=null;
this.statusText=null;
this.readyState=0;
this._ifpServerUrl=_43;
this._stateId=null;
};
dojo.extend(dojox.io.proxy.xip.XhrIframeFacade,{open:function(_44,uri){
this._method=_44;
this._uri=uri;
this.readyState=1;
},setRequestHeader:function(_46,_47){
this._requestHeaders[_46]=_47;
},send:function(_48){
this._bodyData=_48;
this._stateId=dojox.io.proxy.xip.send(this);
this.readyState=2;
},abort:function(){
dojox.io.proxy.xip.destroyState(this._stateId);
},getAllResponseHeaders:function(){
return this._allResponseHeaders;
},getResponseHeader:function(_49){
return this._responseHeaders[_49];
},_setResponseHeaders:function(_4a){
if(_4a){
this._allResponseHeaders=_4a;
_4a=_4a.replace(/\r/g,"");
var _4b=_4a.split("\n");
for(var i=0;i<_4b.length;i++){
if(_4b[i]){
var _4d=_4b[i].split(": ");
this._responseHeaders[_4d[0]]=_4d[1];
}
}
}
}});
}
