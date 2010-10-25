/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xmpp.RosterService"]){
dojo._hasResource["dojox.xmpp.RosterService"]=true;
dojo.provide("dojox.xmpp.RosterService");
dojox.xmpp.roster={ADDED:101,CHANGED:102,REMOVED:103};
dojo.declare("dojox.xmpp.RosterService",null,{constructor:function(_1){
this.session=_1;
},addRosterItem:function(_2,_3,_4){
if(!_2){
throw new Error("Roster::addRosterItem() - User ID is null");
}
var _5=this.session.getNextIqId();
var _6={id:_5,from:this.session.jid+"/"+this.session.resource,type:"set"};
var _7=new dojox.string.Builder(dojox.xmpp.util.createElement("iq",_6,false));
_7.append(dojox.xmpp.util.createElement("query",{xmlns:"jabber:iq:roster"},false));
_2=dojox.xmpp.util.encodeJid(_2);
if(_2.indexOf("@")==-1){
_2=_2+"@"+this.session.domain;
}
_7.append(dojox.xmpp.util.createElement("item",{jid:_2,name:dojox.xmpp.util.xmlEncode(_3)},false));
if(_4){
for(var i=0;i<_4.length;i++){
_7.append("<group>");
_7.append(_4[i]);
_7.append("</group>");
}
}
_7.append("</item></query></iq>");
var _9=this.session.dispatchPacket(_7.toString(),"iq",_6.id);
_9.addCallback(this,"verifyRoster");
return _9;
},updateRosterItem:function(_a,_b,_c){
if(_a.indexOf("@")==-1){
_a+=_a+"@"+this.session.domain;
}
var _d={id:this.session.getNextIqId(),from:this.session.jid+"/"+this.session.resource,type:"set"};
var _e=new dojox.string.Builder(dojox.xmpp.util.createElement("iq",_d,false));
_e.append(dojox.xmpp.util.createElement("query",{xmlns:"jabber:iq:roster"},false));
var i=this.session.getRosterIndex(_a);
if(i==-1){
return;
}
var _10={jid:_a};
if(_b){
_10.name=_b;
}else{
if(this.session.roster[i].name){
_10.name=this.session.roster[i].name;
}
}
if(_10.name){
_10.name=dojox.xmpp.util.xmlEncode(_10.name);
}
_e.append(dojox.xmpp.util.createElement("item",_10,false));
var _11=_c?_c:this.session.roster[i].groups;
if(_11){
for(var x=0;x<_11.length;x++){
_e.append("<group>");
_e.append(_11[x]);
_e.append("</group>");
}
}
_e.append("</item></query></iq>");
var def=this.session.dispatchPacket(_e.toString(),"iq",_d.id);
def.addCallback(this,"verifyRoster");
return def;
},verifyRoster:function(res){
if(res.getAttribute("type")=="result"){
}else{
var err=this.session.processXmppError(res);
this.onAddRosterItemFailed(err);
}
return res;
},addRosterItemToGroup:function(jid,_17){
if(!jid){
throw new Error("Roster::addRosterItemToGroup() JID is null or undefined");
}
if(!_17){
throw new Error("Roster::addRosterItemToGroup() group is null or undefined");
}
var _18=this.session.getRosterIndex(jid);
if(_18==-1){
return;
}
var _19=this.session.roster[_18];
var _1a=[];
var _1b=false;
for(var i=0;((_19<_19.groups.length)&&(!_1b));i++){
if(_19.groups[i]!=_17){
continue;
}
_1b=true;
}
if(!_1b){
return this.updateRosterItem(jid,_19.name,_19.groups.concat(_17),_18);
}
return dojox.xmpp.xmpp.INVALID_ID;
},removeRosterGroup:function(_1d){
var _1e=this.session.roster;
for(var i=0;i<_1e.length;i++){
var _20=_1e[i];
if(_20.groups.length>0){
for(var j=0;j<_20.groups.length;j++){
if(_20.groups[j]==_1d){
_20.groups.splice(j,1);
this.updateRosterItem(_20.jid,_20.name,_20.groups);
}
}
}
}
},renameRosterGroup:function(_22,_23){
var _24=this.session.roster;
for(var i=0;i<_24.length;i++){
var _26=_24[i];
if(_26.groups.length>0){
for(var j=0;j<_26.groups.length;j++){
if(_26.groups[j]==_22){
_26.groups[j]=_23;
this.updateRosterItem(_26.jid,_26.name,_26.groups);
}
}
}
}
},removeRosterItemFromGroup:function(jid,_29){
if(!jid){
throw new Error("Roster::addRosterItemToGroup() JID is null or undefined");
}
if(!_29){
throw new Error("Roster::addRosterItemToGroup() group is null or undefined");
}
var _2a=this.session.getRosterIndex(jid);
if(_2a==-1){
return;
}
var _2b=this.session.roster[_2a];
var _2c=false;
for(var i=0;((i<_2b.groups.length)&&(!_2c));i++){
if(_2b.groups[i]!=_29){
continue;
}
_2c=true;
_2a=i;
}
if(_2c==true){
_2b.groups.splice(_2a,1);
return this.updateRosterItem(jid,_2b.name,_2b.groups);
}
return dojox.xmpp.xmpp.INVALID_ID;
},rosterItemRenameGroup:function(jid,_2f,_30){
if(!jid){
throw new Error("Roster::rosterItemRenameGroup() JID is null or undefined");
}
if(!_30){
throw new Error("Roster::rosterItemRenameGroup() group is null or undefined");
}
var _31=this.session.getRosterIndex(jid);
if(_31==-1){
return;
}
var _32=this.session.roster[_31];
var _33=false;
for(var i=0;((i<_32.groups.length)&&(!_33));i++){
if(_32.groups[i]==_2f){
_32.groups[i]=_30;
_33=true;
}
}
if(_33==true){
return this.updateRosterItem(jid,_32.name,_32.groups);
}
return dojox.xmpp.xmpp.INVALID_ID;
},renameRosterItem:function(jid,_36){
if(!jid){
throw new Error("Roster::addRosterItemToGroup() JID is null or undefined");
}
if(!_36){
throw new Error("Roster::addRosterItemToGroup() New Name is null or undefined");
}
var _37=this.session.getRosterIndex(jid);
if(_37==-1){
return;
}
return this.updateRosterItem(jid,_36,this.session.roster.groups,_37);
},removeRosterItem:function(jid){
if(!jid){
throw new Error("Roster::addRosterItemToGroup() JID is null or undefined");
}
var req={id:this.session.getNextIqId(),from:this.session.jid+"/"+this.session.resource,type:"set"};
var _3a=new dojox.string.Builder(dojox.xmpp.util.createElement("iq",req,false));
_3a.append(dojox.xmpp.util.createElement("query",{xmlns:"jabber:iq:roster"},false));
if(jid.indexOf("@")==-1){
jid+=jid+"@"+this.session.domain;
}
_3a.append(dojox.xmpp.util.createElement("item",{jid:jid,subscription:"remove"},true));
_3a.append("</query></iq>");
var def=this.session.dispatchPacket(_3a.toString(),"iq",req.id);
def.addCallback(this,"verifyRoster");
return def;
},getAvatar:function(jid){
},publishAvatar:function(_3d,_3e){
},onVerifyRoster:function(id){
},onVerifyRosterFailed:function(err){
}});
}
