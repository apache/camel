/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.filter.strings"]){
dojo._hasResource["dojox.dtl.filter.strings"]=true;
dojo.provide("dojox.dtl.filter.strings");
dojo.require("dojox.dtl.filter.htmlstrings");
dojo.require("dojox.string.sprintf");
dojo.require("dojox.string.tokenize");
dojo.mixin(dojox.dtl.filter.strings,{_urlquote:function(_1,_2){
if(!_2){
_2="/";
}
return dojox.string.tokenize(_1,/([^\w-_.])/g,function(_3){
if(_2.indexOf(_3)==-1){
if(_3==" "){
return "+";
}else{
return "%"+_3.charCodeAt(0).toString(16).toUpperCase();
}
}
return _3;
}).join("");
},addslashes:function(_4){
return _4.replace(/\\/g,"\\\\").replace(/"/g,"\\\"").replace(/'/g,"\\'");
},capfirst:function(_5){
_5=""+_5;
return _5.charAt(0).toUpperCase()+_5.substring(1);
},center:function(_6,_7){
_7=_7||_6.length;
_6=_6+"";
var _8=_7-_6.length;
if(_8%2){
_6=_6+" ";
_8-=1;
}
for(var i=0;i<_8;i+=2){
_6=" "+_6+" ";
}
return _6;
},cut:function(_a,_b){
_b=_b+""||"";
_a=_a+"";
return _a.replace(new RegExp(_b,"g"),"");
},_fix_ampersands:/&(?!(\w+|#\d+);)/g,fix_ampersands:function(_c){
return _c.replace(dojox.dtl.filter.strings._fix_ampersands,"&amp;");
},floatformat:function(_d,_e){
_e=parseInt(_e||-1,10);
_d=parseFloat(_d);
var m=_d-_d.toFixed(0);
if(!m&&_e<0){
return _d.toFixed();
}
_d=_d.toFixed(Math.abs(_e));
return (_e<0)?parseFloat(_d)+"":_d;
},iriencode:function(_10){
return dojox.dtl.filter.strings._urlquote(_10,"/#%[]=:;$&()+,!");
},linenumbers:function(_11){
var df=dojox.dtl.filter;
var _13=_11.split("\n");
var _14=[];
var _15=(_13.length+"").length;
for(var i=0,_17;i<_13.length;i++){
_17=_13[i];
_14.push(df.strings.ljust(i+1,_15)+". "+dojox.dtl._base.escape(_17));
}
return _14.join("\n");
},ljust:function(_18,arg){
_18=_18+"";
arg=parseInt(arg,10);
while(_18.length<arg){
_18=_18+" ";
}
return _18;
},lower:function(_1a){
return (_1a+"").toLowerCase();
},make_list:function(_1b){
var _1c=[];
if(typeof _1b=="number"){
_1b=_1b+"";
}
if(_1b.charAt){
for(var i=0;i<_1b.length;i++){
_1c.push(_1b.charAt(i));
}
return _1c;
}
if(typeof _1b=="object"){
for(var key in _1b){
_1c.push(_1b[key]);
}
return _1c;
}
return [];
},rjust:function(_1f,arg){
_1f=_1f+"";
arg=parseInt(arg,10);
while(_1f.length<arg){
_1f=" "+_1f;
}
return _1f;
},slugify:function(_21){
_21=_21.replace(/[^\w\s-]/g,"").toLowerCase();
return _21.replace(/[\-\s]+/g,"-");
},_strings:{},stringformat:function(_22,arg){
arg=""+arg;
var _24=dojox.dtl.filter.strings._strings;
if(!_24[arg]){
_24[arg]=new dojox.string.sprintf.Formatter("%"+arg);
}
return _24[arg].format(_22);
},title:function(_25){
var _26,_27="";
for(var i=0,_29;i<_25.length;i++){
_29=_25.charAt(i);
if(_26==" "||_26=="\n"||_26=="\t"||!_26){
_27+=_29.toUpperCase();
}else{
_27+=_29.toLowerCase();
}
_26=_29;
}
return _27;
},_truncatewords:/[ \n\r\t]/,truncatewords:function(_2a,arg){
arg=parseInt(arg,10);
if(!arg){
return _2a;
}
for(var i=0,j=_2a.length,_2e=0,_2f,_30;i<_2a.length;i++){
_2f=_2a.charAt(i);
if(dojox.dtl.filter.strings._truncatewords.test(_30)){
if(!dojox.dtl.filter.strings._truncatewords.test(_2f)){
++_2e;
if(_2e==arg){
return _2a.substring(0,j+1);
}
}
}else{
if(!dojox.dtl.filter.strings._truncatewords.test(_2f)){
j=i;
}
}
_30=_2f;
}
return _2a;
},_truncate_words:/(&.*?;|<.*?>|(\w[\w\-]*))/g,_truncate_tag:/<(\/)?([^ ]+?)(?: (\/)| .*?)?>/,_truncate_singlets:{br:true,col:true,link:true,base:true,img:true,param:true,area:true,hr:true,input:true},truncatewords_html:function(_31,arg){
arg=parseInt(arg,10);
if(arg<=0){
return "";
}
var _33=dojox.dtl.filter.strings;
var _34=0;
var _35=[];
var _36=dojox.string.tokenize(_31,_33._truncate_words,function(all,_38){
if(_38){
++_34;
if(_34<arg){
return _38;
}else{
if(_34==arg){
return _38+" ...";
}
}
}
var tag=all.match(_33._truncate_tag);
if(!tag||_34>=arg){
return;
}
var _3a=tag[1];
var _3b=tag[2].toLowerCase();
var _3c=tag[3];
if(_3a||_33._truncate_singlets[_3b]){
}else{
if(_3a){
var i=dojo.indexOf(_35,_3b);
if(i!=-1){
_35=_35.slice(i+1);
}
}else{
_35.unshift(_3b);
}
}
return all;
}).join("");
_36=_36.replace(/\s+$/g,"");
for(var i=0,tag;tag=_35[i];i++){
_36+="</"+tag+">";
}
return _36;
},upper:function(_40){
return _40.toUpperCase();
},urlencode:function(_41){
return dojox.dtl.filter.strings._urlquote(_41);
},_urlize:/^((?:[(>]|&lt;)*)(.*?)((?:[.,)>\n]|&gt;)*)$/,_urlize2:/^\S+@[a-zA-Z0-9._-]+\.[a-zA-Z0-9._-]+$/,urlize:function(_42){
return dojox.dtl.filter.strings.urlizetrunc(_42);
},urlizetrunc:function(_43,arg){
arg=parseInt(arg);
return dojox.string.tokenize(_43,/(\S+)/g,function(_45){
var _46=dojox.dtl.filter.strings._urlize.exec(_45);
if(!_46){
return _45;
}
var _47=_46[1];
var _48=_46[2];
var _49=_46[3];
var _4a=_48.indexOf("www.")==0;
var _4b=_48.indexOf("@")!=-1;
var _4c=_48.indexOf(":")!=-1;
var _4d=_48.indexOf("http://")==0;
var _4e=_48.indexOf("https://")==0;
var _4f=/[a-zA-Z0-9]/.test(_48.charAt(0));
var _50=_48.substring(_48.length-4);
var _51=_48;
if(arg>3){
_51=_51.substring(0,arg-3)+"...";
}
if(_4a||(!_4b&&!_4d&&_48.length&&_4f&&(_50==".org"||_50==".net"||_50==".com"))){
return "<a href=\"http://"+_48+"\" rel=\"nofollow\">"+_51+"</a>";
}else{
if(_4d||_4e){
return "<a href=\""+_48+"\" rel=\"nofollow\">"+_51+"</a>";
}else{
if(_4b&&!_4a&&!_4c&&dojox.dtl.filter.strings._urlize2.test(_48)){
return "<a href=\"mailto:"+_48+"\">"+_48+"</a>";
}
}
}
return _45;
}).join("");
},wordcount:function(_52){
_52=dojo.trim(_52);
if(!_52){
return 0;
}
return _52.split(/\s+/g).length;
},wordwrap:function(_53,arg){
arg=parseInt(arg);
var _55=[];
var _56=_53.split(/\s+/g);
if(_56.length){
var _57=_56.shift();
_55.push(_57);
var pos=_57.length-_57.lastIndexOf("\n")-1;
for(var i=0;i<_56.length;i++){
_57=_56[i];
if(_57.indexOf("\n")!=-1){
var _5a=_57.split(/\n/g);
}else{
var _5a=[_57];
}
pos+=_5a[0].length+1;
if(arg&&pos>arg){
_55.push("\n");
pos=_5a[_5a.length-1].length;
}else{
_55.push(" ");
if(_5a.length>1){
pos=_5a[_5a.length-1].length;
}
}
_55.push(_57);
}
}
return _55.join("");
}});
}
