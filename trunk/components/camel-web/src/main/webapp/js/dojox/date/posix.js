/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.date.posix"]){
dojo._hasResource["dojox.date.posix"]=true;
dojo.provide("dojox.date.posix");
dojo.require("dojo.date");
dojo.require("dojo.date.locale");
dojo.require("dojo.string");
dojox.date.posix.strftime=function(_1,_2,_3){
var _4=null;
var _=function(s,n){
return dojo.string.pad(s,n||2,_4||"0");
};
var _8=dojo.date.locale._getGregorianBundle(_3);
var $=function(_a){
switch(_a){
case "a":
return dojo.date.locale.getNames("days","abbr","format",_3)[_1.getDay()];
case "A":
return dojo.date.locale.getNames("days","wide","format",_3)[_1.getDay()];
case "b":
case "h":
return dojo.date.locale.getNames("months","abbr","format",_3)[_1.getMonth()];
case "B":
return dojo.date.locale.getNames("months","wide","format",_3)[_1.getMonth()];
case "c":
return dojo.date.locale.format(_1,{formatLength:"full",locale:_3});
case "C":
return _(Math.floor(_1.getFullYear()/100));
case "d":
return _(_1.getDate());
case "D":
return $("m")+"/"+$("d")+"/"+$("y");
case "e":
if(_4==null){
_4=" ";
}
return _(_1.getDate());
case "f":
if(_4==null){
_4=" ";
}
return _(_1.getMonth()+1);
case "g":
break;
case "G":
dojo.unimplemented("unimplemented modifier 'G'");
break;
case "F":
return $("Y")+"-"+$("m")+"-"+$("d");
case "H":
return _(_1.getHours());
case "I":
return _(_1.getHours()%12||12);
case "j":
return _(dojo.date.locale._getDayOfYear(_1),3);
case "k":
if(_4==null){
_4=" ";
}
return _(_1.getHours());
case "l":
if(_4==null){
_4=" ";
}
return _(_1.getHours()%12||12);
case "m":
return _(_1.getMonth()+1);
case "M":
return _(_1.getMinutes());
case "n":
return "\n";
case "p":
return _8[_1.getHours()<12?"am":"pm"];
case "r":
return $("I")+":"+$("M")+":"+$("S")+" "+$("p");
case "R":
return $("H")+":"+$("M");
case "S":
return _(_1.getSeconds());
case "t":
return "\t";
case "T":
return $("H")+":"+$("M")+":"+$("S");
case "u":
return String(_1.getDay()||7);
case "U":
return _(dojo.date.locale._getWeekOfYear(_1));
case "V":
return _(dojox.date.posix.getIsoWeekOfYear(_1));
case "W":
return _(dojo.date.locale._getWeekOfYear(_1,1));
case "w":
return String(_1.getDay());
case "x":
return dojo.date.locale.format(_1,{selector:"date",formatLength:"full",locale:_3});
case "X":
return dojo.date.locale.format(_1,{selector:"time",formatLength:"full",locale:_3});
case "y":
return _(_1.getFullYear()%100);
case "Y":
return String(_1.getFullYear());
case "z":
var _b=_1.getTimezoneOffset();
return (_b>0?"-":"+")+_(Math.floor(Math.abs(_b)/60))+":"+_(Math.abs(_b)%60);
case "Z":
return dojo.date.getTimezoneName(_1);
case "%":
return "%";
}
};
var _c="";
var i=0;
var _e=0;
var _f=null;
while((_e=_2.indexOf("%",i))!=-1){
_c+=_2.substring(i,_e++);
switch(_2.charAt(_e++)){
case "_":
_4=" ";
break;
case "-":
_4="";
break;
case "0":
_4="0";
break;
case "^":
_f="upper";
break;
case "*":
_f="lower";
break;
case "#":
_f="swap";
break;
default:
_4=null;
_e--;
break;
}
var _10=$(_2.charAt(_e++));
switch(_f){
case "upper":
_10=_10.toUpperCase();
break;
case "lower":
_10=_10.toLowerCase();
break;
case "swap":
var _11=_10.toLowerCase();
var _12="";
var ch="";
for(var j=0;j<_10.length;j++){
ch=_10.charAt(j);
_12+=(ch==_11.charAt(j))?ch.toUpperCase():ch.toLowerCase();
}
_10=_12;
break;
default:
break;
}
_f=null;
_c+=_10;
i=_e;
}
_c+=_2.substring(i);
return _c;
};
dojox.date.posix.getStartOfWeek=function(_15,_16){
if(isNaN(_16)){
_16=dojo.cldr.supplemental.getFirstDayOfWeek?dojo.cldr.supplemental.getFirstDayOfWeek():0;
}
var _17=_16;
if(_15.getDay()>=_16){
_17-=_15.getDay();
}else{
_17-=(7-_15.getDay());
}
var _18=new Date(_15);
_18.setHours(0,0,0,0);
return dojo.date.add(_18,"day",_17);
};
dojox.date.posix.setIsoWeekOfYear=function(_19,_1a){
if(!_1a){
return _19;
}
var _1b=dojox.date.posix.getIsoWeekOfYear(_19);
var _1c=_1a-_1b;
if(_1a<0){
var _1d=dojox.date.posix.getIsoWeeksInYear(_19);
_1c=(_1d+_1a+1)-_1b;
}
return dojo.date.add(_19,"week",_1c);
};
dojox.date.posix.getIsoWeekOfYear=function(_1e){
var _1f=dojox.date.posix.getStartOfWeek(_1e,1);
var _20=new Date(_1e.getFullYear(),0,4);
_20=dojox.date.posix.getStartOfWeek(_20,1);
var _21=_1f.getTime()-_20.getTime();
if(_21<0){
return dojox.date.posix.getIsoWeeksInYear(_1f);
}
return Math.ceil(_21/604800000)+1;
};
dojox.date.posix.getIsoWeeksInYear=function(_22){
function p(y){
return y+Math.floor(y/4)-Math.floor(y/100)+Math.floor(y/400);
};
var y=_22.getFullYear();
return (p(y)%7==4||p(y-1)%7==3)?53:52;
};
}
