/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xmpp.util"]){
dojo._hasResource["dojox.xmpp.util"]=true;
dojo.provide("dojox.xmpp.util");
dojo.require("dojox.string.Builder");
dojox.xmpp.util.xmlEncode=function(_1){
if(_1){
_1=_1.replace("&","&amp;").replace(">","&gt;").replace("<","&lt;").replace("'","&apos;").replace("\"","&quot;");
}
return _1;
};
dojox.xmpp.util.encodeJid=function(_2){
var _3=new dojox.string.Builder();
for(var i=0;i<_2.length;i++){
var ch=_2.charAt(i);
var _6=ch;
switch(ch){
case " ":
_6="\\20";
break;
case "\"":
_6="\\22";
break;
case "#":
_6="\\23";
break;
case "&":
_6="\\26";
break;
case "'":
_6="\\27";
break;
case "/":
_6="\\2f";
break;
case ":":
_6="\\3a";
break;
case "<":
_6="\\3c";
break;
case ">":
_6="\\3e";
break;
}
_3.append(_6);
}
return _3.toString();
};
dojox.xmpp.util.decodeJid=function(_7){
_7=_7.replace(/\\([23][02367acef])/g,function(_8){
switch(_8){
case "\\20":
return " ";
case "\\22":
return "\"";
case "\\23":
return "#";
case "\\26":
return "&";
case "\\27":
return "'";
case "\\2f":
return "/";
case "\\3a":
return ":";
case "\\3c":
return "<";
case "\\3e":
return ">";
}
return "ARG";
});
return _7;
};
dojox.xmpp.util.createElement=function(_9,_a,_b){
var _c=new dojox.string.Builder("<");
_c.append(_9+" ");
for(var _d in _a){
_c.append(_d+"=\"");
_c.append(_a[_d]);
_c.append("\" ");
}
if(_b){
_c.append("/>");
}else{
_c.append(">");
}
return _c.toString();
};
dojox.xmpp.util.stripHtml=function(_e){
var re=/<[^>]*?>/gi;
for(var i=0;i<arguments.length;i++){
}
return _e.replace(re,"");
};
dojox.xmpp.util.decodeHtmlEntities=function(str){
var ta=dojo.doc.createElement("textarea");
ta.innerHTML=str.replace(/</g,"&lt;").replace(/>/g,"&gt;");
return ta.value;
};
dojox.xmpp.util.htmlToPlain=function(str){
str=dojox.xmpp.util.decodeHtmlEntities(str);
str=str.replace(/<br\s*[i\/]{0,1}>/gi,"\n");
str=dojox.xmpp.util.stripHtml(str);
return str;
};
dojox.xmpp.util.Base64={};
dojox.xmpp.util.Base64.keylist="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
dojox.xmpp.util.Base64.encode=function(_14){
var _15="";
var c1,c2,c3;
var _19,_1a,_1b,_1c;
var i=0;
do{
c1=c2=c3="";
_19=_1a=_1b=_1c="";
c1=_14.charCodeAt(i++);
c2=_14.charCodeAt(i++);
c3=_14.charCodeAt(i++);
_19=c1>>2;
_1a=((c1&3)<<4)|(c2>>4);
_1b=((c2&15)<<2)|(c3>>6);
_1c=c3&63;
if(isNaN(c2)){
_1b=_1c=64;
}else{
if(isNaN(c3)){
_1c=64;
}
}
_15=_15+dojox.xmpp.util.Base64.keylist.charAt(_19)+dojox.xmpp.util.Base64.keylist.charAt(_1a)+dojox.xmpp.util.Base64.keylist.charAt(_1b)+dojox.xmpp.util.Base64.keylist.charAt(_1c);
}while(i<_14.length);
return _15;
};
dojox.xmpp.util.Base64.decode=function(_1e){
var _1f="";
var c1,c2,c3;
var _23,_24,_25,_26="";
var i=0;
do{
c1=c2=c3="";
_23=_24=_25=_26="";
_23=dojox.xmpp.util.Base64.keylist.indexOf(_1e.charAt(i++));
_24=dojox.xmpp.util.Base64.keylist.indexOf(_1e.charAt(i++));
_25=dojox.xmpp.util.Base64.keylist.indexOf(_1e.charAt(i++));
_26=dojox.xmpp.util.Base64.keylist.indexOf(_1e.charAt(i++));
c1=(_23<<2)|(_24>>4);
c2=((_24&15)<<4)|(_25>>2);
c3=((_25&3)<<6)|_26;
_1f=_1f+String.fromCharCode(c1);
if(_25!=64){
_1f=_1f+String.fromCharCode(c2);
}
if(_26!=64){
_1f=_1f+String.fromCharCode(c3);
}
}while(i<_1e.length);
return _1f;
};
}
