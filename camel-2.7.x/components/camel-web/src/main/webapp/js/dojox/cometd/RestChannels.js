/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.cometd.RestChannels"]){
dojo._hasResource["dojox.cometd.RestChannels"]=true;
dojo.provide("dojox.cometd.RestChannels");
dojo.require("dojox.rpc.Client");
dojo.requireIf(dojox.data&&!!dojox.data.JsonRestStore,"dojox.data.restListener");
(function(){
dojo.declare("dojox.cometd.RestChannels",null,{constructor:function(_1){
dojo.mixin(this,_1);
if(dojox.rpc.Rest&&this.autoSubscribeRoot){
var _2=dojox.rpc.Rest._get;
var _3=this;
dojox.rpc.Rest._get=function(_4,id){
var _6=dojo.xhrGet;
dojo.xhrGet=function(r){
var _8=_3.autoSubscribeRoot;
return (_8&&r.url.substring(0,_8.length)==_8)?_3.get(r.url,r):_6(r);
};
var _9=_2.apply(this,arguments);
dojo.xhrGet=_6;
return _9;
};
}
if(dojox.data&&dojox.data.restListener){
this.receive=dojox.data.restListener;
}
},absoluteUrl:function(_a,_b){
return new dojo._Url(_a,_b)+"";
},acceptType:"application/rest+json,application/http;q=0.9,*/*;q=0.7",subscriptions:{},subCallbacks:{},autoReconnectTime:3000,reloadDataOnReconnect:true,sendAsJson:false,url:"/channels",autoSubscribeRoot:"/",open:function(){
this.started=true;
if(!this.connected){
this.connectionId=dojox.rpc.Client.clientId;
var _c=this.createdClientId?"Client-Id":"Create-Client-Id";
this.createdClientId=true;
var _d={Accept:this.acceptType};
_d[_c]=this.connectionId;
var _e=dojo.xhrPost({headers:_d,url:this.url,noStatus:true});
var _f=this;
this.lastIndex=0;
var _10,_11=function(_12){
if(typeof dojo=="undefined"){
return null;
}
if(xhr&&xhr.status>400){
return _10(true);
}
if(typeof _12=="string"){
_12=_12.substring(_f.lastIndex);
}
var _14=xhr&&(xhr.contentType||xhr.getResponseHeader("Content-Type"))||(typeof _12!="string"&&"already json");
var _15=_f.onprogress(xhr,_12,_14);
if(_15){
if(_10()){
return new Error(_15);
}
}
if(!xhr||xhr.readyState==4){
xhr=null;
if(_f.connected){
_f.connected=false;
_f.open();
}
}
return _12;
};
_10=function(_16){
if(xhr&&xhr.status==409){

_f.disconnected();
return null;
}
_f.createdClientId=false;
_f.disconnected();
return _16;
};
_e.addCallbacks(_11,_10);
var xhr=_e.ioArgs.xhr;
if(xhr){
xhr.onreadystatechange=function(){
var _17;
try{
if(xhr.readyState==3){
_f.readyState=3;
_17=xhr.responseText;
}
}
catch(e){
}
if(typeof _17=="string"){
_11(_17);
}
};
}
if(window.attachEvent){
attachEvent("onunload",function(){
_f.connected=false;
if(xhr){
xhr.abort();
}
});
}
this.connected=true;
}
},_send:function(_18,_19,_1a){
if(this.sendAsJson){
_19.postBody=dojo.toJson({target:_19.url,method:_18,content:_1a,params:_19.content,subscribe:_19.headers["Subscribe"]});
_19.url=this.url;
_18="POST";
}else{
_19.postData=dojo.toJson(_1a);
}
return dojo.xhr(_18,_19,_19.postBody);
},subscribe:function(_1b,_1c){
_1c=_1c||{};
_1c.url=this.absoluteUrl(this.url,_1b);
if(_1c.headers){
delete _1c.headers.Range;
}
var _1d=this.subscriptions[_1b];
var _1e=_1c.method||"HEAD";
var _1f=_1c.since;
var _20=_1c.callback;
var _21=_1c.headers||(_1c.headers={});
this.subscriptions[_1b]=_1f||_1d||0;
var _22=this.subCallbacks[_1b];
if(_20){
this.subCallbacks[_1b]=_22?function(m){
_22(m);
_20(m);
}:_20;
}
if(!this.connected){
this.open();
}
if(_1d===undefined||_1d!=_1f){
_21["Cache-Control"]="max-age=0";
_1f=typeof _1f=="number"?new Date(_1f).toUTCString():_1f;
if(_1f){
_21["Subscribe-Since"]=_1f;
}
_21["Subscribe"]=_1c.unsubscribe?"none":"*";
var dfd=this._send(_1e,_1c);
var _25=this;
dfd.addBoth(function(_26){
var xhr=dfd.ioArgs.xhr;
if(!(_26 instanceof Error)){
if(_1c.confirmation){
_1c.confirmation();
}
}
if(xhr&&xhr.getResponseHeader("Subscribed")=="OK"){
var _28=xhr.getResponseHeader("Last-Modified");
if(xhr.responseText){
_25.subscriptions[_1b]=_28||new Date().toUTCString();
}else{
return null;
}
}else{
if(xhr&&!(_26 instanceof Error)){
delete _25.subscriptions[_1b];
}
}
if(!(_26 instanceof Error)){
var _29={responseText:xhr&&xhr.responseText,channel:_1b,getResponseHeader:function(_2a){
return xhr.getResponseHeader(_2a);
},getAllResponseHeaders:function(){
return xhr.getAllResponseHeaders();
},result:_26};
if(_25.subCallbacks[_1b]){
_25.subCallbacks[_1b](_29);
}
}else{
if(_25.subCallbacks[_1b]){
_25.subCallbacks[_1b](xhr);
}
}
return _26;
});
return dfd;
}
return null;
},publish:function(_2b,_2c){
return this._send("POST",{url:_2b,contentType:"application/json"},_2c);
},_processMessage:function(_2d){
_2d.event=_2d.event||_2d.getResponseHeader("Event");
if(_2d.event=="connection-conflict"){
return "conflict";
}
try{
_2d.result=_2d.result||dojo.fromJson(_2d.responseText);
}
catch(e){
}
var _2e=this;
var loc=_2d.channel=new dojo._Url(this.url,_2d.source||_2d.getResponseHeader("Content-Location"))+"";
if(loc in this.subscriptions&&_2d.getResponseHeader){
this.subscriptions[loc]=_2d.getResponseHeader("Last-Modified");
}
if(this.subCallbacks[loc]){
setTimeout(function(){
_2e.subCallbacks[loc](_2d);
},0);
}
this.receive(_2d);
return null;
},onprogress:function(xhr,_31,_32){
if(!_32||_32.match(/application\/rest\+json/)){
var _33=_31.length;
_31=_31.replace(/^\s*[,\[]?/,"[").replace(/[,\]]?\s*$/,"]");
try{
var _34=dojo.fromJson(_31);
this.lastIndex+=_33;
}
catch(e){
}
}else{
if(dojox.io&&dojox.io.httpParse&&_32.match(/application\/http/)){
var _35="";
if(xhr&&xhr.getAllResponseHeaders){
_35=xhr.getAllResponseHeaders();
}
_34=dojox.io.httpParse(_31,_35,xhr.readyState!=4);
}else{
if(typeof _31=="object"){
_34=_31;
}
}
}
if(_34){
for(var i=0;i<_34.length;i++){
if(this._processMessage(_34[i])){
return "conflict";
}
}
return null;
}
if(!xhr){
return "error";
}
if(xhr.readyState!=4){
return null;
}
if(xhr.__proto__){
xhr={channel:"channel",__proto__:xhr};
}
return this._processMessage(xhr);
},get:function(_37,_38){
(_38=_38||{}).method="GET";
return this.subscribe(_37,_38);
},receive:function(_39){
},disconnected:function(){
var _3a=this;
if(this.connected){
this.connected=false;
if(this.started){
setTimeout(function(){
var _3b=_3a.subscriptions;
_3a.subscriptions={};
for(var i in _3b){
if(_3a.reloadDataOnReconnect&&dojox.rpc.JsonRest){
delete dojox.rpc.Rest._index[i];
dojox.rpc.JsonRest.fetch(i);
}else{
_3a.subscribe(i,{since:_3b[i]});
}
}
_3a.open();
},this.autoReconnectTime);
}
}
},unsubscribe:function(_3d,_3e){
_3e=_3e||{};
_3e.unsubscribe=true;
this.subscribe(_3d,_3e);
},disconnect:function(){
this.started=false;
this.xhr.abort();
}});
var _3f=dojox.cometd.RestChannels.defaultInstance=new dojox.cometd.RestChannels();
if(dojox.cometd.connectionTypes){
_3f.startup=function(_40){
_3f.open();
this._cometd._deliver({channel:"/meta/connect",successful:true});
};
_3f.check=function(_41,_42,_43){
for(var i=0;i<_41.length;i++){
if(_41[i]=="rest-channels"){
return !_43;
}
}
return false;
};
_3f.deliver=function(_45){
};
dojo.connect(this,"receive",null,function(_46){
_46.data=_46.result;
this._cometd._deliver(_46);
});
_3f.sendMessages=function(_47){
for(var i=0;i<_47.length;i++){
var _49=_47[i];
var _4a=_49.channel;
var _4b=this._cometd;
var _4c={confirmation:function(){
_4b._deliver({channel:_4a,successful:true});
}};
if(_4a=="/meta/subscribe"){
this.subscribe(_49.subscription,_4c);
}else{
if(_4a=="/meta/unsubscribe"){
this.unsubscribe(_49.subscription,_4c);
}else{
if(_4a=="/meta/connect"){
_4c.confirmation();
}else{
if(_4a=="/meta/disconnect"){
_3f.disconnect();
_4c.confirmation();
}else{
if(_4a.substring(0,6)!="/meta/"){
this.publish(_4a,_49.data);
}
}
}
}
}
}
};
dojox.cometd.connectionTypes.register("rest-channels",_3f.check,_3f,false,true);
}
})();
}
