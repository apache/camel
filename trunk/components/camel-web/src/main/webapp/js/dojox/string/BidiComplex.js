/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.string.BidiComplex"]){
dojo._hasResource["dojox.string.BidiComplex"]=true;
dojo.provide("dojox.string.BidiComplex");
dojo.experimental("dojox.string.BidiComplex");
(function(){
var _1=[];
dojox.string.BidiComplex.attachInput=function(_2,_3){
_2.alt=_3;
dojo.connect(_2,"onkeydown",this,"_ceKeyDown");
dojo.connect(_2,"onkeyup",this,"_ceKeyUp");
dojo.connect(_2,"oncut",this,"_ceCutText");
dojo.connect(_2,"oncopy",this,"_ceCopyText");
_2.value=dojox.string.BidiComplex.createDisplayString(_2.value,_2.alt);
};
dojox.string.BidiComplex.createDisplayString=function(_4,_5){
_4=dojox.string.BidiComplex.stripSpecialCharacters(_4);
var _6=dojox.string.BidiComplex._parse(_4,_5);
var _7="‪"+_4;
var _8=1;
dojo.forEach(_6,function(n){
if(n!=null){
var _a=_7.substring(0,n+_8);
var _b=_7.substring(n+_8,_7.length);
_7=_a+"‎"+_b;
_8++;
}
});
return _7;
};
dojox.string.BidiComplex.stripSpecialCharacters=function(_c){
return _c.replace(/[\u200E\u200F\u202A-\u202E]/g,"");
};
dojox.string.BidiComplex._ceKeyDown=function(_d){
var _e=dojo.isIE?_d.srcElement:_d.target;
_1=_e.value;
};
dojox.string.BidiComplex._ceKeyUp=function(_f){
var LRM="‎";
var _11=dojo.isIE?_f.srcElement:_f.target;
var _12=_11.value;
var _13=_f.keyCode;
if((_13==dojo.keys.HOME)||(_13==dojo.keys.END)||(_13==dojo.keys.SHIFT)){
return;
}
var _14,_15;
var _16=dojox.string.BidiComplex._getCaretPos(_f,_11);
if(_16){
_14=_16[0];
_15=_16[1];
}
if(dojo.isIE){
var _17=_14,_18=_15;
if(_13==dojo.keys.LEFT_ARROW){
if((_12.charAt(_15-1)==LRM)&&(_14==_15)){
dojox.string.BidiComplex._setSelectedRange(_11,_14-1,_15-1);
}
return;
}
if(_13==dojo.keys.RIGHT_ARROW){
if(_12.charAt(_15-1)==LRM){
_18=_15+1;
if(_14==_15){
_17=_14+1;
}
}
dojox.string.BidiComplex._setSelectedRange(_11,_17,_18);
return;
}
}else{
if(_13==dojo.keys.LEFT_ARROW){
if(_12.charAt(_15-1)==LRM){
dojox.string.BidiComplex._setSelectedRange(_11,_14-1,_15-1);
}
return;
}
if(_13==dojo.keys.RIGHT_ARROW){
if(_12.charAt(_15-1)==LRM){
dojox.string.BidiComplex._setSelectedRange(_11,_14+1,_15+1);
}
return;
}
}
var _19=dojox.string.BidiComplex.createDisplayString(_12,_11.alt);
if(_12!=_19){
window.status=_12+" c="+_15;
_11.value=_19;
if((_13==dojo.keys.DELETE)&&(_19.charAt(_15)==LRM)){
_11.value=_19.substring(0,_15)+_19.substring(_15+2,_19.length);
}
if(_13==dojo.keys.DELETE){
dojox.string.BidiComplex._setSelectedRange(_11,_14,_15);
}else{
if(_13==dojo.keys.BACKSPACE){
if((_1.length>=_15)&&(_1.charAt(_15-1)==LRM)){
dojox.string.BidiComplex._setSelectedRange(_11,_14-1,_15-1);
}else{
dojox.string.BidiComplex._setSelectedRange(_11,_14,_15);
}
}else{
if(_11.value.charAt(_15)!=LRM){
dojox.string.BidiComplex._setSelectedRange(_11,_14+1,_15+1);
}
}
}
}
};
dojox.string.BidiComplex._processCopy=function(_1a,_1b,_1c){
if(_1b==null){
if(dojo.isIE){
var _1d=document.selection.createRange();
_1b=_1d.text;
}else{
_1b=_1a.value.substring(_1a.selectionStart,_1a.selectionEnd);
}
}
var _1e=dojox.string.BidiComplex.stripSpecialCharacters(_1b);
if(dojo.isIE){
window.clipboardData.setData("Text",_1e);
}
return true;
};
dojox.string.BidiComplex._ceCopyText=function(_1f){
if(dojo.isIE){
_1f.returnValue=false;
}
return dojox.string.BidiComplex._processCopy(_1f,null,false);
};
dojox.string.BidiComplex._ceCutText=function(_20){
var ret=dojox.string.BidiComplex._processCopy(_20,null,false);
if(!ret){
return false;
}
if(dojo.isIE){
document.selection.clear();
}else{
var _22=_20.selectionStart;
_20.value=_20.value.substring(0,_22)+_20.value.substring(_20.selectionEnd);
_20.setSelectionRange(_22,_22);
}
return true;
};
dojox.string.BidiComplex._getCaretPos=function(_23,_24){
if(dojo.isIE){
var _25=0,_26=document.selection.createRange().duplicate(),_27=_26.duplicate(),_28=_26.text.length;
if(_24.type=="textarea"){
_27.moveToElementText(_24);
}else{
_27.expand("textedit");
}
while(_26.compareEndPoints("StartToStart",_27)>0){
_26.moveStart("character",-1);
++_25;
}
return [_25,_25+_28];
}
return [_23.target.selectionStart,_23.target.selectionEnd];
};
dojox.string.BidiComplex._setSelectedRange=function(_29,_2a,_2b){
if(dojo.isIE){
var _2c=_29.createTextRange();
if(_2c){
if(_29.type=="textarea"){
_2c.moveToElementText(_29);
}else{
_2c.expand("textedit");
}
_2c.collapse();
_2c.moveEnd("character",_2b);
_2c.moveStart("character",_2a);
_2c.select();
}
}else{
_29.selectionStart=_2a;
_29.selectionEnd=_2b;
}
};
var _2d=function(c){
return (c>="0"&&c<="9")||(c>"ÿ");
};
var _2f=function(c){
return (c>="A"&&c<="Z")||(c>="a"&&c<="z");
};
var _31=function(_32,i,_34){
while(i>0){
if(i==_34){
return false;
}
i--;
if(_2d(_32.charAt(i))){
return true;
}
if(_2f(_32.charAt(i))){
return false;
}
}
return false;
};
dojox.string.BidiComplex._parse=function(str,_36){
var _37=-1,_38=[];
var _39={FILE_PATH:"/\\:.",URL:"/:.?=&#",XPATH:"/\\:.<>=[]",EMAIL:"<>@.,;"}[_36];
switch(_36){
case "FILE_PATH":
case "URL":
case "XPATH":
dojo.forEach(str,function(ch,i){
if(_39.indexOf(ch)>=0&&_31(str,i,_37)){
_37=i;
_38.push(i);
}
});
break;
case "EMAIL":
var _3c=false;
dojo.forEach(str,function(ch,i){
if(ch=="\""){
if(_31(str,i,_37)){
_37=i;
_38.push(i);
}
i++;
var i1=str.indexOf("\"",i);
if(i1>=i){
i=i1;
}
if(_31(str,i,_37)){
_37=i;
_38.push(i);
}
}
if(_39.indexOf(ch)>=0&&_31(str,i,_37)){
_37=i;
_38.push(i);
}
});
}
return _38;
};
})();
}
