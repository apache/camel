/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.date.HebrewLocale"]){
dojo._hasResource["dojox.date.HebrewLocale"]=true;
dojo.provide("dojox.date.HebrewLocale");
dojo.experimental("dojox.date.HebrewLocale");
dojo.require("dojox.date.HebrewDate");
dojo.require("dojox.date.HebrewNumerals");
dojo.require("dojo.regexp");
dojo.require("dojo.string");
dojo.require("dojo.i18n");
dojo.requireLocalization("dojo.cldr","hebrew",null,"ROOT,he");
(function(){
function _1(_2,_3,_4,_5,_6){
return _6.replace(/([a-z])\1*/ig,function(_7){
var s,_9;
var c=_7.charAt(0);
var l=_7.length;
var _c=["abbr","wide","narrow"];
switch(c){
case "y":
if(_4=="he"){
s=dojox.date.HebrewNumerals.getYearHebrewLetters(_2.getFullYear());
}else{
s=String(_2.getFullYear());
}
break;
case "M":
var m=_2.getMonth();
if(l<3){
if(!_2.isLeapYear(_2.getFullYear())&&m>5){
m--;
}
if(_4=="he"){
s=dojox.date.HebrewNumerals.getMonthHebrewLetters(m);
}else{
s=m+1;
_9=true;
}
}else{
if(!_2.isLeapYear(_2.getFullYear())&&m==6){
m--;
}
var _e=["months","format",_c[l-3]].join("-");
s=_3[_e][m];
}
break;
case "d":
if(_4=="he"){
s=dojox.date.HebrewNumerals.getDayHebrewLetters(_2.getDate());
}else{
s=_2.getDate();
_9=true;
}
break;
case "E":
var d=_2.getDay();
if(l<3){
s=d+1;
_9=true;
}else{
var _10=["days","format",_c[l-3]].join("-");
s=_3[_10][d];
}
break;
case "a":
var _11=(_2.getHours()<12)?"am":"pm";
s=_3[_11];
break;
case "h":
case "H":
case "K":
case "k":
var h=_2.getHours();
switch(c){
case "h":
s=(h%12)||12;
break;
case "H":
s=h;
break;
case "K":
s=(h%12);
break;
case "k":
s=h||24;
break;
}
_9=true;
break;
case "m":
s=_2.getMinutes();
_9=true;
break;
case "s":
s=_2.getSeconds();
_9=true;
break;
case "S":
s=Math.round(_2.getMilliseconds()*Math.pow(10,l-3));
_9=true;
break;
default:
throw new Error("dojox.date.HebrewLocale.formatPattern: invalid pattern char: "+_6);
}
if(_9){
s=dojo.string.pad(s,l);
}
return s;
});
};
dojox.date.HebrewLocale.format=function(_13,_14){
_14=_14||{};
var _15=dojo.i18n.normalizeLocale(_14.locale);
var _16=_14.formatLength||"short";
var _17=dojox.date.HebrewLocale._getHebrewBundle(_15);
var str=[];
var _19=dojo.hitch(this,_1,_13,_17,_15,_14.fullYear);
if(_14.selector!="time"){
var _1a=_14.datePattern||_17["dateFormat-"+_16];
if(_1a){
str.push(_1b(_1a,_19));
}
}
if(_14.selector!="date"){
var _1c=_14.timePattern||_17["timeFormat-"+_16];
if(_1c){
str.push(_1b(_1c,_19));
}
}
var _1d=str.join(" ");
return _1d;
};
dojox.date.HebrewLocale.regexp=function(_1e){
return dojox.date.HebrewLocale._parseInfo(_1e).regexp;
};
dojox.date.HebrewLocale._parseInfo=function(_1f){
_1f=_1f||{};
var _20=dojo.i18n.normalizeLocale(_1f.locale);
var _21=dojox.date.HebrewLocale._getHebrewBundle(_20);
var _22=_1f.formatLength||"short";
var _23=_1f.datePattern||_21["dateFormat-"+_22];
var _24=_1f.timePattern||_21["timeFormat-"+_22];
var _25;
if(_1f.selector=="date"){
_25=_23;
}else{
if(_1f.selector=="time"){
_25=_24;
}else{
_25=(typeof (_24)=="undefined")?_23:_23+" "+_24;
}
}
var _26=[];
var re=_1b(_25,dojo.hitch(this,_28,_26,_21,_1f));
return {regexp:re,tokens:_26,bundle:_21};
};
dojox.date.HebrewLocale.parse=function(_29,_2a){
if(!_2a){
_2a={};
}
var _2b=dojox.date.HebrewLocale._parseInfo(_2a);
var _2c=_2b.tokens,_2d=_2b.bundle;
var re=new RegExp("^"+_2b.regexp+"$");
var _2f=re.exec(_29);
var _30=dojo.i18n.normalizeLocale(_2a.locale);
if(!_2f){

return null;
}
var _31,_32;
var _33=[5730,3,23,0,0,0,0];
var _34="";
var _35=0;
var _36=["abbr","wide","narrow"];
var _37=dojo.every(_2f,function(v,i){
if(!i){
return true;
}
var _3a=_2c[i-1];
var l=_3a.length;
switch(_3a.charAt(0)){
case "y":
if(_30=="he"){
_33[0]=dojox.date.HebrewNumerals.parseYearHebrewLetters(v);
}else{
_33[0]=Number(v);
}
break;
case "M":
if(l>2){
var _3c=_2d["months-format-"+_36[l-3]].concat();
if(!_2a.strict){
v=v.replace(".","").toLowerCase();
_3c=dojo.map(_3c,function(s){
return s.replace(".","").toLowerCase();
});
}
v=dojo.indexOf(_3c,v);
if(v==-1){
return false;
}
_35=l;
}else{
if(_30=="he"){
v=dojox.date.HebrewNumerals.parseMonthHebrewLetters(v);
}else{
v--;
}
}
_33[1]=Number(v);
break;
case "D":
_33[1]=0;
case "d":
if(_30=="he"){
_33[2]=dojox.date.HebrewNumerals.parseDayHebrewLetters(v);
}else{
_33[2]=Number(v);
}
break;
case "a":
var am=_2a.am||_2d.am;
var pm=_2a.pm||_2d.pm;
if(!_2a.strict){
var _40=/\./g;
v=v.replace(_40,"").toLowerCase();
am=am.replace(_40,"").toLowerCase();
pm=pm.replace(_40,"").toLowerCase();
}
if(_2a.strict&&v!=am&&v!=pm){
return false;
}
_34=(v==pm)?"p":(v==am)?"a":"";
break;
case "K":
if(v==24){
v=0;
}
case "h":
case "H":
case "k":
_33[3]=Number(v);
break;
case "m":
_33[4]=Number(v);
break;
case "s":
_33[5]=Number(v);
break;
case "S":
_33[6]=Number(v);
}
return true;
});
var _41=+_33[3];
if(_34==="p"&&_41<12){
_33[3]=_41+12;
}else{
if(_34==="a"&&_41==12){
_33[3]=0;
}
}
var _42=new dojox.date.HebrewDate(_33[0],_33[1],_33[2],_33[3],_33[4],_33[5],_33[6]);
if((_35>2)&&(_33[1]>5)&&!_42.isLeapYear(_42.getFullYear())){
_42=new dojox.date.HebrewDate(_33[0],_33[1]-1,_33[2],_33[3],_33[4],_33[5],_33[6]);
}
return _42;
};
function _1b(_43,_44,_45,_46){
var _47=function(x){
return x;
};
_44=_44||_47;
_45=_45||_47;
_46=_46||_47;
var _49=_43.match(/(''|[^'])+/g);
var _4a=_43.charAt(0)=="'";
dojo.forEach(_49,function(_4b,i){
if(!_4b){
_49[i]="";
}else{
_49[i]=(_4a?_45:_44)(_4b);
_4a=!_4a;
}
});
return _46(_49.join(""));
};
function _28(_4d,_4e,_4f,_50){
_50=dojo.regexp.escapeString(_50);
var _51=dojo.i18n.normalizeLocale(_4f.locale);
return _50.replace(/([a-z])\1*/ig,function(_52){
var s;
var c=_52.charAt(0);
var l=_52.length;
var p2="",p3="";
if(_4f.strict){
if(l>1){
p2="0"+"{"+(l-1)+"}";
}
if(l>2){
p3="0"+"{"+(l-2)+"}";
}
}else{
p2="0?";
p3="0{0,2}";
}
switch(c){
case "y":
s="\\S+";
break;
case "M":
if(_51=="he"){
s=(l>2)?"\\S+ ?\\S+":"\\S{1,4}";
}else{
s=(l>2)?"\\S+ ?\\S+":p2+"[1-9]|1[0-2]";
}
break;
case "d":
if(_51=="he"){
s="\\S['\"']{1,2}\\S?";
}else{
s="[12]\\d|"+p2+"[1-9]|30";
}
break;
case "E":
if(_51=="he"){
s=(l>3)?"\\S+ ?\\S+":"\\S";
}else{
s="\\S+";
}
break;
case "h":
s=p2+"[1-9]|1[0-2]";
break;
case "k":
s=p2+"\\d|1[01]";
break;
case "H":
s=p2+"\\d|1\\d|2[0-3]";
break;
case "K":
s=p2+"[1-9]|1\\d|2[0-4]";
break;
case "m":
case "s":
s=p2+"\\d|[0-5]\\d";
break;
case "S":
s="\\d{"+l+"}";
break;
case "a":
var am=_4f.am||_4e.am||"AM";
var pm=_4f.pm||_4e.pm||"PM";
if(_4f.strict){
s=am+"|"+pm;
}else{
s=am+"|"+pm;
if(am!=am.toLowerCase()){
s+="|"+am.toLowerCase();
}
if(pm!=pm.toLowerCase()){
s+="|"+pm.toLowerCase();
}
}
break;
default:
s=".*";
}
if(_4d){
_4d.push(_52);
}
return "("+s+")";
}).replace(/[\xa0 ]/g,"[\\s\\xa0]");
};
})();
(function(){
var _5a=[];
dojox.date.HebrewLocale.addCustomFormats=function(_5b,_5c){
_5a.push({pkg:_5b,name:_5c});
};
dojox.date.HebrewLocale._getHebrewBundle=function(_5d){
var _5e={};
dojo.forEach(_5a,function(_5f){
var _60=dojo.i18n.getLocalization(_5f.pkg,_5f.name,_5d);
_5e=dojo.mixin(_5e,_60);
},this);
return _5e;
};
})();
dojox.date.HebrewLocale.addCustomFormats("dojo.cldr","hebrew");
dojox.date.HebrewLocale.getNames=function(_61,_62,_63,_64){
var _65;
var _66=dojox.date.HebrewLocale._getHebrewBundle;
var _67=[_61,_63,_62];
if(_63=="standAlone"){
var key=_67.join("-");
_65=_66(_64)[key];
if(_65===_66("ROOT")[key]){
_65=undefined;
}
}
_67[1]="format";
return (_65||_66(_64)[_67.join("-")]).concat();
};
}
