/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.filter.htmlstrings"]){
dojo._hasResource["dojox.dtl.filter.htmlstrings"]=true;
dojo.provide("dojox.dtl.filter.htmlstrings");
dojo.require("dojox.dtl._base");
dojo.mixin(dojox.dtl.filter.htmlstrings,{_linebreaksrn:/(\r\n|\n\r)/g,_linebreaksn:/\n{2,}/g,_linebreakss:/(^\s+|\s+$)/g,_linebreaksbr:/\n/g,_removetagsfind:/[a-z0-9]+/g,_striptags:/<[^>]*?>/g,linebreaks:function(_1){
var _2=[];
var dh=dojox.dtl.filter.htmlstrings;
_1=_1.replace(dh._linebreaksrn,"\n");
var _4=_1.split(dh._linebreaksn);
for(var i=0;i<_4.length;i++){
var _6=_4[i].replace(dh._linebreakss,"").replace(dh._linebreaksbr,"<br />");
_2.push("<p>"+_6+"</p>");
}
return _2.join("\n\n");
},linebreaksbr:function(_7){
var dh=dojox.dtl.filter.htmlstrings;
return _7.replace(dh._linebreaksrn,"\n").replace(dh._linebreaksbr,"<br />");
},removetags:function(_9,_a){
var dh=dojox.dtl.filter.htmlstrings;
var _c=[];
var _d;
while(_d=dh._removetagsfind.exec(_a)){
_c.push(_d[0]);
}
_c="("+_c.join("|")+")";
return _9.replace(new RegExp("</?s*"+_c+"s*[^>]*>","gi"),"");
},striptags:function(_e){
return _e.replace(dojox.dtl.filter.htmlstrings._striptags,"");
}});
}
