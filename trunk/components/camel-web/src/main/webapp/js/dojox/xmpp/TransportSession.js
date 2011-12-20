/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xmpp.TransportSession"]){
dojo._hasResource["dojox.xmpp.TransportSession"]=true;
dojo.provide("dojox.xmpp.TransportSession");
dojo.require("dojox.xmpp.util");
dojo.require("dojo.io.script");
dojo.require("dojo.io.iframe");
dojo.require("dojox.data.dom");
dojox.xmpp.TransportSession=function(_1){
if(_1&&dojo.isObject(_1)){
dojo.mixin(this,_1);
if(this.useScriptSrcTransport){
this.transportIframes=[];
}
}
};
dojox.xmpp.TransportSession._iframeOnload=function(_2){
var _3=dojo.io.iframe.doc(dojo.byId("xmpp-transport-"+_2));
_3.write("<script>var isLoaded=true; var rid=0; var transmiting=false; function _BOSH_(msg) { transmiting=false; parent.dojox.xmpp.TransportSession.handleBOSH(msg, rid); } </script>");
};
dojox.xmpp.TransportSession.handleBOSH=function(_4,_5){
};
dojo.extend(dojox.xmpp.TransportSession,{rid:0,hold:1,polling:1000,secure:false,wait:60,lang:"en",submitContentType:"text/xml; charset=utf=8",serviceUrl:"/httpbind",defaultResource:"dojoIm",domain:"imserver.com",sendTimeout:(this.wait+20)*1000,useScriptSrcTransport:false,keepAliveTimer:null,state:"NotReady",transmitState:"Idle",protocolPacketQueue:[],outboundQueue:[],outboundRequests:{},inboundQueue:[],deferredRequests:{},matchTypeIdAttribute:{},open:function(){
this.status="notReady";
this.rid=Math.round(Math.random()*1000000000);
this.protocolPacketQueue=[];
this.outboundQueue=[];
this.outboundRequests={};
this.inboundQueue=[];
this.deferredRequests={};
this.matchTypeIdAttribute={};
this.keepAliveTimer=setTimeout(dojo.hitch(this,"_keepAlive"),10000);
if(this.useScriptSrcTransport){
dojo.connect(dojox.xmpp.TransportSession,"handleBOSH",this,"processScriptSrc");
this.transportIframes=[];
for(var i=0;i<=this.hold;i++){
var _7=dojo.io.iframe.create("xmpp-transport-"+i,dojox._scopeName+".xmpp.TransportSession._iframeOnload("+i+");");
this.transportIframes.push(_7);
if(i==0){
dojo.connect(_7,"onload",this,"_sendLogin");
}
}
}else{
this._sendLogin();
}
},_sendLogin:function(){
var _8=this.rid++;
var _9={content:this.submitContentType,hold:this.hold,rid:_8,to:this.domain,secure:this.secure,wait:this.wait,"xml:lang":this.lang,xmlns:dojox.xmpp.xmpp.BODY_NS};
var _a=dojox.xmpp.util.createElement("body",_9,true);
this.addToOutboundQueue(_a,_8);
},processScriptSrc:function(_b,_c){
var _d=dojox.data.dom.createDocument(_b,"text/xml");
if(_d){
this.processDocument(_d,_c);
}else{
}
},_keepAlive:function(){
if(this.state=="wait"||this.isTerminated()){
return;
}
this._dispatchPacket();
this.keepAliveTimer=setTimeout(dojo.hitch(this,"_keepAlive"),10000);
},close:function(_e){
var _f=this.rid++;
var req={sid:this.sid,rid:_f,type:"terminate"};
var _11=null;
if(_e){
_11=new dojox.string.Builder(dojox.xmpp.util.createElement("body",req,false));
_11.append(_e);
_11.append("</body>");
}else{
_11=new dojox.string.Builder(dojox.xmpp.util.createElement("body",req,false));
}
this.addToOutboundQueue(_11.toString(),_f);
this.state=="Terminate";
},dispatchPacket:function(msg,_13,_14,_15){
if(msg){
this.protocolPacketQueue.push(msg);
}
var def=new dojo.Deferred();
if(_13&&_14){
def.protocolMatchType=_13;
def.matchId=_14;
def.matchProperty=_15||"id";
if(def.matchProperty!="id"){
this.matchTypeIdAttribute[_13]=def.matchProperty;
}
}
this.deferredRequests[def.protocolMatchType+"-"+def.matchId]=def;
if(!this.dispatchTimer){
this.dispatchTimer=setTimeout(dojo.hitch(this,"_dispatchPacket"),600);
}
return def;
},_dispatchPacket:function(){
clearTimeout(this.dispatchTimer);
delete this.dispatchTimer;
if(!this.sid){

return;
}
if(!this.authId){

return;
}
if(this.transmitState!="error"&&(this.protocolPacketQueue.length==0)&&(this.outboundQueue.length>0)){
return;
}
if(this.state=="wait"||this.isTerminated()){
return;
}
var req={sid:this.sid};
if(this.protocolPacketQueue.length>0){
req.rid=this.rid++;
var _18=new dojox.string.Builder(dojox.xmpp.util.createElement("body",req,false));
_18.append(this.processProtocolPacketQueue());
_18.append("</body>");
delete this.lastPollTime;
}else{
if(this.lastPollTime){
var now=new Date().getTime();
if(now-this.lastPollTime<this.polling){
this.dispatchTimer=setTimeout(dojo.hitch(this,"_dispatchPacket"),this.polling-(now-this.lastPollTime)+10);
return;
}
}
req.rid=this.rid++;
this.lastPollTime=new Date().getTime();
var _18=new dojox.string.Builder(dojox.xmpp.util.createElement("body",req,true));
}
this.addToOutboundQueue(_18.toString(),req.rid);
},redispatchPacket:function(rid){
var env=this.outboundRequests[rid];
this.sendXml(env,rid);
},addToOutboundQueue:function(msg,rid){
this.outboundQueue.push({msg:msg,rid:rid});
this.outboundRequests[rid]=msg;
this.sendXml(msg,rid);
},removeFromOutboundQueue:function(rid){
for(var i=0;i<this.outboundQueue.length;i++){
if(rid==this.outboundQueue[i]["rid"]){
this.outboundQueue.splice(i,1);
break;
}
}
delete this.outboundRequests[rid];
},processProtocolPacketQueue:function(){
var _20=new dojox.string.Builder();
for(var i=0;i<this.protocolPacketQueue.length;i++){
_20.append(this.protocolPacketQueue[i]);
}
this.protocolPacketQueue=[];
return _20.toString();
},findOpenIframe:function(){
for(var i=0;i<this.transportIframes.length;i++){
var _23=this.transportIframes[i];
var win=_23.contentWindow;
if(win.isLoaded&&!win.transmiting){
return _23;
}
}
},sendXml:function(_25,rid){
if(this.isTerminated()){
return;
}
this.transmitState="transmitting";
if(this.useScriptSrcTransport){
var _27=this.findOpenIframe();
var _28=dojo.io.iframe.doc(_27);
_27.contentWindow.rid=rid;
_27.contentWindow.transmiting=true;
dojo.io.script.attach("rid-"+rid,this.serviceUrl+"?"+encodeURIComponent(_25),_28);
}else{
var def=dojo.rawXhrPost({contentType:"text/xml",url:this.serviceUrl,postData:_25,handleAs:"xml",error:dojo.hitch(this,function(res,io){
return this.processError(io.xhr.responseXML,io.xhr.status,rid);
}),timeout:this.sendTimeout});
def.addCallback(this,function(res){
return this.processDocument(res,rid);
});
return def;
}
},processDocument:function(doc,rid){
if(this.isTerminated()){
return;
}
this.transmitState="idle";
var _2f=doc.firstChild;
if(_2f.nodeName!="body"){
}
if(this.outboundQueue.length<1){
return;
}
var _30=this.outboundQueue[0]["rid"];
if(rid==_30){
this.removeFromOutboundQueue(rid);
this.processResponse(_2f,rid);
this.processInboundQueue();
}else{
var gap=rid-_30;
if(gap<this.hold+2){
this.addToInboundQueue(doc,rid);
}else{
}
}
return doc;
},processInboundQueue:function(){
while(this.inboundQueue.length>0){
var _32=this.inboundQueue.shift();
this.processDocument(_32["doc"],_32["rid"]);
}
},addToInboundQueue:function(doc,rid){
for(var i=0;i<this.inboundQueue.length;i++){
if(rid<this.inboundQueue[i]["rid"]){
continue;
}
this.inboundQueue.splice(i,0,{doc:doc,rid:rid});
}
},processResponse:function(_36,rid){
if(_36.getAttribute("type")=="terminate"){
var _38=_36.firstChild.firstChild;
var _39="";
if(_38.nodeName=="conflict"){
_39="conflict";
}
this.setState("Terminate",_39);
return;
}
if((this.state!="Ready")&&(this.state!="Terminate")){
var sid=_36.getAttribute("sid");
if(sid){
this.sid=sid;
}else{
throw new Error("No sid returned during xmpp session startup");
}
this.authId=_36.getAttribute("authid");
if(this.authId==""){
if(this.authRetries--<1){
console.error("Unable to obtain Authorization ID");
this.terminateSession();
}
}
this.wait=_36.getAttribute("wait");
if(_36.getAttribute("polling")){
this.polling=parseInt(_36.getAttribute("polling"))*1000;
}
this.inactivity=_36.getAttribute("inactivity");
this.setState("Ready");
}
dojo.forEach(_36.childNodes,function(_3b){
this.processProtocolResponse(_3b,rid);
},this);
if(this.transmitState=="idle"){
this.dispatchPacket();
}
},processProtocolResponse:function(msg,rid){
this.onProcessProtocolResponse(msg);
var key=msg.nodeName+"-"+msg.getAttribute("id");
var def=this.deferredRequests[key];
if(def){
def.callback(msg);
delete this.deferredRequests[key];
}
},setState:function(_40,_41){
if(this.state!=_40){
if(this["on"+_40]){
this["on"+_40](_40,this.state,_41);
}
this.state=_40;
}
},isTerminated:function(){
return this.state=="Terminate";
},processError:function(err,_43,rid){
if(this.isTerminated()){
return;
}
if(_43!=200){
this.setState("Terminate",_45);
return;
}
if(err&&err.dojoType&&err.dojoType=="timeout"){
}
this.removeFromOutboundQueue(rid);
if(err&&err.firstChild){
if(err.firstChild.getAttribute("type")=="terminate"){
var _46=err.firstChild.firstChild;
var _45="";
if(_46&&_46.nodeName=="conflict"){
_45="conflict";
}
this.setState("Terminate",_45);
return;
}
}
this.transmitState="error";
setTimeout(dojo.hitch(this,function(){
this.dispatchPacket();
}),200);
return true;
},onTerminate:function(_47,_48,_49){
},onProcessProtocolResponse:function(msg){
},onReady:function(_4b,_4c){
}});
}
