/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.io.httpParse"]){
dojo._hasResource["dojox.io.httpParse"]=true;
dojo.provide("dojox.io.httpParse");
dojox.io.httpParse=function(_1,_2,_3){
var _4=[];
var _5=_1.length;
do{
var _6={};
var _7=_1.match(/(\n*[^\n]+)/);
if(!_7){
return null;
}
_1=_1.substring(_7[0].length+1);
_7=_7[1];
var _8=_1.match(/([^\n]+\n)*/)[0];
_1=_1.substring(_8.length);
var _9=_1.substring(0,1);
_1=_1.substring(1);
_8=(_2||"")+_8;
var _a=_8;
_8=_8.match(/[^:\n]+:[^\n]+\n/g);
for(var j=0;j<_8.length;j++){
var _c=_8[j].indexOf(":");
_6[_8[j].substring(0,_c)]=_8[j].substring(_c+1).replace(/(^[ \r\n]*)|([ \r\n]*)$/g,"");
}
_7=_7.split(" ");
var _d={status:parseInt(_7[1],10),statusText:_7[2],readyState:3,getAllResponseHeaders:function(){
return _a;
},getResponseHeader:function(_e){
return _6[_e];
}};
var _f=_6["Content-Length"];
var _10;
if(_f){
if(_f<=_1.length){
_10=_1.substring(0,_f);
}else{
return _4;
}
}else{
if((_10=_1.match(/(.*)HTTP\/\d\.\d \d\d\d[\w\s]*\n/))){
_10=_10[0];
}else{
if(!_3||_9=="\n"){
_10=_1;
}else{
return _4;
}
}
}
_4.push(_d);
_1=_1.substring(_10.length);
_d.responseText=_10;
_d.readyState=4;
_d._lastIndex=_5-_1.length;
}while(_1);
return _4;
};
}
