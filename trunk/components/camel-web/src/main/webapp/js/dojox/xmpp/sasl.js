/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xmpp.sasl"]){
dojo._hasResource["dojox.xmpp.sasl"]=true;
dojo.provide("dojox.xmpp.sasl");
dojo.require("dojox.xmpp.util");
dojox.xmpp.sasl.saslNS="urn:ietf:params:xml:ns:xmpp-sasl";
dojox.xmpp.sasl.SunWebClientAuth=function(_1){
var _2={xmlns:dojox.xmpp.sasl.saslNS,mechanism:"SUN-COMMS-CLIENT-PROXY-AUTH"};
var _3=dojox.xmpp.util.createElement("auth",_2,true);
_1.dispatchPacket(_3);
};
dojox.xmpp.sasl.SaslPlain=function(_4){
var _5={xmlns:dojox.xmpp.sasl.saslNS,mechanism:"PLAIN"};
var _6=new dojox.string.Builder(dojox.xmpp.util.createElement("auth",_5,false));
var id=_4.jid;
var _8=_4.jid.indexOf("@");
if(_8!=-1){
id=_4.jid.substring(0,_8);
}
var _9="\x00"+id+"\x00"+_4.password;
_9=dojox.xmpp.util.Base64.encode(_9);
_6.append(_9);
_6.append("</auth>");
_4.dispatchPacket(_6.toString());
};
}
