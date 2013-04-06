/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xmpp.xmppSession"]){
dojo._hasResource["dojox.xmpp.xmppSession"]=true;
dojo.provide("dojox.xmpp.xmppSession");
dojo.require("dojox.xmpp.TransportSession");
dojo.require("dojox.xmpp.RosterService");
dojo.require("dojox.xmpp.PresenceService");
dojo.require("dojox.xmpp.UserService");
dojo.require("dojox.xmpp.ChatService");
dojo.require("dojox.xmpp.sasl");
dojox.xmpp.xmpp={STREAM_NS:"http://etherx.jabber.org/streams",CLIENT_NS:"jabber:client",STANZA_NS:"urn:ietf:params:xml:ns:xmpp-stanzas",SASL_NS:"urn:ietf:params:xml:ns:xmpp-sasl",BIND_NS:"urn:ietf:params:xml:ns:xmpp-bind",BODY_NS:"http://jabber.org/protocol/httpbind",XHTML_BODY_NS:"http://www.w3.org/1999/xhtml",XHTML_IM_NS:"http://jabber.org/protocol/xhtml-im",INACTIVE:"Inactive",CONNECTED:"Connected",ACTIVE:"Active",TERMINATE:"Terminate",LOGIN_FAILURE:"LoginFailure",INVALID_ID:-1,NO_ID:0,error:{BAD_REQUEST:"bad-request",CONFLICT:"conflict",FEATURE_NOT_IMPLEMENTED:"feature-not-implemented",FORBIDDEN:"forbidden",GONE:"gone",INTERNAL_SERVER_ERROR:"internal-server-error",ITEM_NOT_FOUND:"item-not-found",ID_MALFORMED:"jid-malformed",NOT_ACCEPTABLE:"not-acceptable",NOT_ALLOWED:"not-allowed",NOT_AUTHORIZED:"not-authorized",SERVICE_UNAVAILABLE:"service-unavailable",SUBSCRIPTION_REQUIRED:"subscription-required",UNEXPECTED_REQUEST:"unexpected-request"}};
dojox.xmpp.xmppSession=function(_1){
if(_1&&dojo.isObject(_1)){
dojo.mixin(this,_1);
}
this.session=new dojox.xmpp.TransportSession(_1);
dojo.connect(this.session,"onReady",this,"onTransportReady");
dojo.connect(this.session,"onTerminate",this,"onTransportTerminate");
dojo.connect(this.session,"onProcessProtocolResponse",this,"processProtocolResponse");
};
dojo.extend(dojox.xmpp.xmppSession,{roster:[],chatRegister:[],_iqId:Math.round(Math.random()*1000000000),open:function(_2,_3,_4){
if(!_2){
throw new Error("User id cannot be null");
}else{
this.jid=_2;
if(_2.indexOf("@")==-1){
this.jid=this.jid+"@"+this.domain;
}
}
if(_3){
this.password=_3;
}
if(_4){
this.resource=_4;
}
this.session.open();
},close:function(){
this.state=dojox.xmpp.xmpp.TERMINATE;
this.session.close(dojox.xmpp.util.createElement("presence",{type:"unavailable",xmlns:dojox.xmpp.xmpp.CLIENT_NS},true));
},processProtocolResponse:function(_5){
var _6=_5.nodeName;
var _7=_6.indexOf(":");
if(_7>0){
_6=_6.substring(_7+1);
}
switch(_6){
case "iq":
case "presence":
case "message":
case "features":
this[_6+"Handler"](_5);
break;
default:
if(_5.getAttribute("xmlns")==dojox.xmpp.xmpp.SASL_NS){
this.saslHandler(_5);
}
}
},messageHandler:function(_8){
switch(_8.getAttribute("type")){
case "chat":
this.chatHandler(_8);
break;
case "normal":
default:
this.simpleMessageHandler(_8);
}
},iqHandler:function(_9){
if(_9.getAttribute("type")=="set"){
this.iqSetHandler(_9);
return;
}else{
if(_9.getAttribute("type")=="get"){
return;
}
}
},presenceHandler:function(_a){
switch(_a.getAttribute("type")){
case "subscribe":
this.presenceSubscriptionRequest(_a.getAttribute("from"));
break;
case "subscribed":
case "unsubscribed":
break;
case "error":
this.processXmppError(_a);
break;
default:
this.presenceUpdate(_a);
break;
}
},featuresHandler:function(_b){
var _c=[];
var _d=false;
if(_b.hasChildNodes()){
for(var i=0;i<_b.childNodes.length;i++){
var n=_b.childNodes[i];
switch(n.nodeName){
case "mechanisms":
for(var x=0;x<n.childNodes.length;x++){
_c.push(n.childNodes[x].firstChild.nodeValue);
}
break;
case "bind":
_d=true;
break;
}
}
}
if(this.state==dojox.xmpp.xmpp.CONNECTED&&_d){
for(var i=0;i<_c.length;i++){
if(_c[i]=="SUN-COMMS-CLIENT-PROXY-AUTH"){
dojox.xmpp.sasl.SunWebClientAuth(this);
break;
}else{
if(_c[i]=="PLAIN"){
dojox.xmpp.sasl.SaslPlain(this);
break;
}else{
console.error("No suitable auth mechanism found for: ",_c[i]);
}
}
}
delete this.password;
}
},saslHandler:function(msg){
if(msg.nodeName=="success"){
this.bindResource();
return;
}
if(msg.hasChildNodes()){
this.onLoginFailure(msg.firstChild.nodeName);
}
},chatHandler:function(msg){
var _13={from:msg.getAttribute("from"),to:msg.getAttribute("to")};
var _14=null;
for(var i=0;i<msg.childNodes.length;i++){
var n=msg.childNodes[i];
if(n.hasChildNodes()){
switch(n.nodeName){
case "thread":
_13.chatid=n.firstChild.nodeValue;
break;
case "body":
if(!n.getAttribute("xmlns")||(n.getAttribute("xmlns")=="")){
_13.body=n.firstChild.nodeValue;
}
break;
case "subject":
_13.subject=n.firstChild.nodeValue;
case "html":
if(n.getAttribute("xmlns")==dojox.xmpp.xmpp.XHTML_IM_NS){
_13.xhtml=n.getElementsByTagName("body")[0];
}
break;
case "x":
break;
default:
}
}
}
var _17=-1;
if(_13.chatid){
for(var i=0;i<this.chatRegister.length;i++){
var ci=this.chatRegister[i];
if(ci&&ci.chatid==_13.chatid){
_17=i;
break;
}
}
}else{
for(var i=0;i<this.chatRegister.length;i++){
var ci=this.chatRegister[i];
if(ci){
if(ci.uid==this.getBareJid(_13.from)){
_17=i;
}
}
}
}
if(_17>-1&&_14){
var _19=this.chatRegister[_17];
_19.setState(_14);
if(_19.firstMessage){
if(_14==dojox.xmpp.chat.ACTIVE_STATE){
_19.useChatState=(_14!=null)?true:false;
_19.firstMessage=false;
}
}
}
if((!_13.body||_13.body=="")&&!_13.xhtml){
return;
}
if(_17>-1){
var _19=this.chatRegister[_17];
_19.recieveMessage(_13);
}else{
var _1a=new dojox.xmpp.ChatService();
_1a.uid=this.getBareJid(_13.from);
_1a.chatid=_13.chatid;
_1a.firstMessage=true;
if(!_14||_14!=dojox.xmpp.chat.ACTIVE_STATE){
this.useChatState=false;
}
this.registerChatInstance(_1a,_13);
}
},simpleMessageHandler:function(msg){
},registerChatInstance:function(_1c,_1d){
_1c.setSession(this);
this.chatRegister.push(_1c);
this.onRegisterChatInstance(_1c,_1d);
_1c.recieveMessage(_1d,true);
},iqSetHandler:function(msg){
if(msg.hasChildNodes()){
var fn=msg.firstChild;
switch(fn.nodeName){
case "query":
if(fn.getAttribute("xmlns")=="jabber:iq:roster"){
this.rosterSetHandler(fn);
this.sendIqResult(msg.getAttribute("id"),msg.getAttribute("from"));
}
break;
default:
break;
}
}
},sendIqResult:function(_20,to){
var req={id:_20,to:to||this.domain,type:"result",from:this.jid+"/"+this.resource};
this.dispatchPacket(dojox.xmpp.util.createElement("iq",req,true));
},rosterSetHandler:function(_23){
for(var i=0;i<_23.childNodes.length;i++){
var n=_23.childNodes[i];
if(n.nodeName=="item"){
var _26=false;
var _27=-1;
var _28=null;
var _29=null;
for(var x=0;x<this.roster.length;x++){
var r=this.roster[x];
if(n.getAttribute("jid")==r.jid){
_26=true;
if(n.getAttribute("subscription")=="remove"){
_28={id:r.jid,name:r.name,groups:[]};
for(var y=0;y<r.groups.length;y++){
_28.groups.push(r.groups[y]);
}
this.roster.splice(x,1);
_27=dojox.xmpp.roster.REMOVED;
}else{
_29=dojo.clone(r);
var _2d=n.getAttribute("name");
if(_2d){
this.roster[x].name=_2d;
}
r.groups=[];
if(n.getAttribute("subscription")){
r.status=n.getAttribute("subscription");
}
r.substatus=dojox.xmpp.presence.SUBSCRIPTION_SUBSTATUS_NONE;
if(n.getAttribute("ask")=="subscribe"){
r.substatus=dojox.xmpp.presence.SUBSCRIPTION_REQUEST_PENDING;
}
for(var y=0;y<n.childNodes.length;y++){
var _2e=n.childNodes[y];
if((_2e.nodeName=="group")&&(_2e.hasChildNodes())){
var _2f=_2e.firstChild.nodeValue;
r.groups.push(_2f);
}
}
_28=r;
_27=dojox.xmpp.roster.CHANGED;
}
break;
}
}
if(!_26&&(n.getAttribute("subscription")!="remove")){
r=this.createRosterEntry(n);
_28=r;
_27=dojox.xmpp.roster.ADDED;
}
switch(_27){
case dojox.xmpp.roster.ADDED:
this.onRosterAdded(_28);
break;
case dojox.xmpp.roster.REMOVED:
this.onRosterRemoved(_28);
break;
case dojox.xmpp.roster.CHANGED:
this.onRosterChanged(_28,_29);
break;
}
}
}
},presenceUpdate:function(msg){
if(msg.getAttribute("to")){
var jid=this.getBareJid(msg.getAttribute("to"));
if(jid!=this.jid){
return;
}
}
var _32=this.getResourceFromJid(msg.getAttribute("from"));
if(!_32){
return;
}
var p={from:this.getBareJid(msg.getAttribute("from")),resource:_32,show:dojox.xmpp.presence.STATUS_ONLINE,priority:5,hasAvatar:false};
if(msg.getAttribute("type")=="unavailable"){
p.show=dojox.xmpp.presence.STATUS_OFFLINE;
}
for(var i=0;i<msg.childNodes.length;i++){
var n=msg.childNodes[i];
if(n.hasChildNodes()){
switch(n.nodeName){
case "status":
case "show":
p[n.nodeName]=n.firstChild.nodeValue;
break;
case "status":
p.priority=parseInt(n.firstChild.nodeValue);
break;
case "x":
if(n.firstChild&&n.firstChild.firstChild&&n.firstChild.firstChild.nodeValue!=""){
p.avatarHash=n.firstChild.firstChild.nodeValue;
p.hasAvatar=true;
}
break;
}
}
}
this.onPresenceUpdate(p);
},retrieveRoster:function(){
var _36={id:this.getNextIqId(),from:this.jid+"/"+this.resource,type:"get"};
var req=new dojox.string.Builder(dojox.xmpp.util.createElement("iq",_36,false));
req.append(dojox.xmpp.util.createElement("query",{xmlns:"jabber:iq:roster"},true));
req.append("</iq>");
var def=this.dispatchPacket(req,"iq",_36.id);
def.addCallback(this,"onRetrieveRoster");
},getRosterIndex:function(jid){
if(jid.indexOf("@")==-1){
jid+="@"+this.domain;
}
for(var i=0;i<this.roster.length;i++){
if(jid==this.roster[i].jid){
return i;
}
}
return -1;
},createRosterEntry:function(_3b){
var re={name:_3b.getAttribute("name"),jid:_3b.getAttribute("jid"),groups:[],status:dojox.xmpp.presence.SUBSCRIPTION_NONE,substatus:dojox.xmpp.presence.SUBSCRIPTION_SUBSTATUS_NONE};
if(!re.name){
re.name=re.id;
}
for(var i=0;i<_3b.childNodes.length;i++){
var n=_3b.childNodes[i];
if(n.nodeName=="group"&&n.hasChildNodes()){
re.groups.push(n.firstChild.nodeValue);
}
}
if(_3b.getAttribute("subscription")){
re.status=_3b.getAttribute("subscription");
}
if(_3b.getAttribute("ask")=="subscribe"){
re.substatus=dojox.xmpp.presence.SUBSCRIPTION_REQUEST_PENDING;
}
return re;
},bindResource:function(){
var _3f={xmlns:"jabber:client",id:this.getNextIqId(),type:"set"};
var _40=new dojox.string.Builder(dojox.xmpp.util.createElement("iq",_3f,false));
_40.append(dojox.xmpp.util.createElement("bind",{xmlns:dojox.xmpp.xmpp.BIND_NS},false));
if(this.resource){
_40.append(dojox.xmpp.util.createElement("resource"));
_40.append(this.resource);
_40.append("</resource>");
}
_40.append("</bind></iq>");
var def=this.dispatchPacket(_40,"iq",_3f.id);
def.addCallback(this,"onBindResource");
},getNextIqId:function(){
return "im_"+this._iqId++;
},presenceSubscriptionRequest:function(msg){
this.onSubscriptionRequest(msg);
},dispatchPacket:function(msg,_44,_45){
if(this.state!="Terminate"){
return this.session.dispatchPacket(msg,_44,_45);
}else{
}
},setState:function(_46,_47){
if(this.state!=_46){
if(this["on"+_46]){
this["on"+_46](_46,this.state,_47);
}
this.state=_46;
}
},search:function(_48,_49,_4a){
var req={id:this.getNextIqId(),"xml:lang":this.lang,type:"set",from:this.jid+"/"+this.resource,to:_49};
var _4c=new dojox.string.Builder(dojox.xmpp.util.createElement("iq",req,false));
_4c.append(dojox.xmpp.util.createElement("query",{xmlns:"jabber:iq:search"},false));
_4c.append(dojox.xmpp.util.createElement(_4a,{},false));
_4c.append(_48);
_4c.append("</").append(_4a).append(">");
_4c.append("</query></iq>");
var def=this.dispatchPacket(_4c.toString,"iq",req.id);
def.addCallback(this,"_onSearchResults");
},_onSearchResults:function(msg){
if((msg.getAttribute("type")=="result")&&(msg.hasChildNodes())){
this.onSearchResults([]);
}
},onLogin:function(){
this.retrieveRoster();
},onLoginFailure:function(msg){
},onBindResource:function(msg){
if(msg.getAttribute("type")=="result"){
if((msg.hasChildNodes())&&(msg.firstChild.nodeName=="bind")){
var _51=msg.firstChild;
if((_51.hasChildNodes())&&(_51.firstChild.nodeName=="jid")){
if(_51.firstChild.hasChildNodes()){
var _52=_51.firstChild.firstChild.nodeValue;
this.jid=this.getBareJid(_52);
this.resource=this.getResourceFromJid(_52);
}
}
}else{
}
this.onLogin();
}else{
if(msg.getAttribute("type")=="error"){
var err=this.processXmppError(msg);
this.onLoginFailure(err);
}
}
return msg;
},onSearchResults:function(_54){
},onRetrieveRoster:function(msg){
if((msg.getAttribute("type")=="result")&&msg.hasChildNodes()){
var _56=msg.getElementsByTagName("query")[0];
if(_56.getAttribute("xmlns")=="jabber:iq:roster"){
for(var i=0;i<_56.childNodes.length;i++){
if(_56.childNodes[i].nodeName=="item"){
this.roster[i]=this.createRosterEntry(_56.childNodes[i]);
}
}
}
}else{
if(msg.getAttribute("type")=="error"){
}
}
this.setState(dojox.xmpp.xmpp.ACTIVE);
this.onRosterUpdated();
return msg;
},onRosterUpdated:function(){
},onSubscriptionRequest:function(req){
},onPresenceUpdate:function(p){
},onTransportReady:function(){
this.setState(dojox.xmpp.xmpp.CONNECTED);
this.rosterService=new dojox.xmpp.RosterService(this);
this.presenceService=new dojox.xmpp.PresenceService(this);
this.userService=new dojox.xmpp.UserService(this);
},onTransportTerminate:function(_5a,_5b,_5c){
this.setState(dojox.xmpp.xmpp.TERMINATE,_5c);
},onConnected:function(){
},onTerminate:function(_5d,_5e,_5f){
},onActive:function(){
},onRegisterChatInstance:function(_60,_61){
},onRosterAdded:function(ri){
},onRosterRemoved:function(ri){
},onRosterChanged:function(ri,_65){
},processXmppError:function(msg){
var err={stanzaType:msg.nodeName,id:msg.getAttribute("id")};
for(var i=0;i<msg.childNodes.length;i++){
var n=msg.childNodes[i];
switch(n.nodeName){
case "error":
err.errorType=n.getAttribute("type");
for(var x=0;x<n.childNodes.length;x++){
var cn=n.childNodes[x];
if((cn.nodeName=="text")&&(cn.getAttribute("xmlns")==dojox.xmpp.xmpp.STANZA_NS)&&cn.hasChildNodes()){
err.message=cn.firstChild.nodeValue;
}else{
if((cn.getAttribute("xmlns")==dojox.xmpp.xmpp.STANZA_NS)&&(!cn.hasChildNodes())){
err.condition=cn.nodeName;
}
}
}
break;
default:
break;
}
}
return err;
},sendStanzaError:function(_6c,to,id,_6f,_70,_71){
var req={type:"error"};
if(to){
req.to=to;
}
if(id){
req.id=id;
}
var _73=new dojox.string.Builder(dojox.xmpp.util.createElement(_6c,req,false));
_73.append(dojox.xmpp.util.createElement("error",{type:_6f},false));
_73.append(dojox.xmpp.util.createElement("condition",{xmlns:dojox.xmpp.xmpp.STANZA_NS},true));
if(_71){
var _74={xmlns:dojox.xmpp.xmpp.STANZA_NS,"xml:lang":this.lang};
_73.append(dojox.xmpp.util.createElement("text",_74,false));
_73.append(_71).append("</text>");
}
_73.append("</error></").append(_6c).append(">");
this.dispatchPacket(_73.toString());
},getBareJid:function(jid){
var i=jid.indexOf("/");
if(i!=-1){
return jid.substring(0,i);
}
return jid;
},getResourceFromJid:function(jid){
var i=jid.indexOf("/");
if(i!=-1){
return jid.substring((i+1),jid.length);
}
return "";
}});
}
