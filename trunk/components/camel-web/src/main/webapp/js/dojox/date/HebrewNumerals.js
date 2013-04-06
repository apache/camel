/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.date.HebrewNumerals"]){
dojo._hasResource["dojox.date.HebrewNumerals"]=true;
dojo.provide("dojox.date.HebrewNumerals");
dojo.experimental("dojox.date.HebrewNumerals");
(function(){
var _1=["א","ב","ג","ד","ה","ו","ז","ח","ט"];
var _2=["י","כ","ל","מ","נ","ס","ע","פ","צ"];
var _3=["ק","ר","ש","ת"];
var _4=["יה","יו","טו","טז"];
var _5=["א'","ב'","ג'","ד'","ה'","ו'","ז'","ח'","ט'","י'","י\"א","י\"ב","י\"ג"];
var _6=["'"];
dojox.date.HebrewNumerals.getYearHebrewLetters=function(_7){
var _8="",_9="";
_7=_7%1000;
var i=0,n=4,j=9;
while(_7){
if(_7>=n*100){
_8=_8.concat(_3[n-1]);
_7-=n*100;
continue;
}else{
if(n>1){
n--;
continue;
}else{
if(_7>=j*10){
_8=_8.concat(_2[j-1]);
_7-=j*10;
}else{
if(j>1){
j--;
continue;
}else{
if(_7>0){
_8=_8.concat(_1[_7-1]);
_7=0;
}
}
}
}
}
}
var _d="";
var _e=_8.indexOf(_4[0]);
if(_e>-1){
_8=_d.concat(_8.substr(_8[0],_e),_4[2],_8.substr(_8[_e+2],_8.length-_e-2));
}else{
if((_e=_8.indexOf(_4[1]))>-1){
_8=_d.concat(_8.substr(_8[0],_e),_4[3],_8.substr(_8[_e+2],_8.length-_e-2));
}
}
if(_8.length>1){
var _f=_8.charAt(_8.length-1);
_8=_9.concat(_8.substr(0,_8.length-1),"\"",_f);
}else{
_8=_8.concat(_6[0]);
}
return _8;
};
dojox.date.HebrewNumerals.parseYearHebrewLetters=function(_10){
var _11=0,i=0,j=0;
for(j=0;j<_10.length;j++){
for(i=1;i<=5;i++){
if(_10.charAt(j)==_3[i-1]){
_11+=100*i;
continue;
}
}
for(i=1;i<=9;i++){
if(_10.charAt(j)==_2[i-1]){
_11+=10*i;
continue;
}
}
for(i=1;i<=9;i++){
if(_10.charAt(j)==_1[i-1]){
_11+=i;
}
}
}
return _11+5000;
};
dojox.date.HebrewNumerals.getDayHebrewLetters=function(day,_15){
var str="";
var j=3;
while(day){
if(day>=j*10){
str=str.concat(_2[j-1]);
day-=j*10;
}else{
if(j>1){
j--;
continue;
}else{
if(day>0){
str=str.concat(_1[day-1]);
day=0;
}
}
}
}
var _18="";
var ind=str.indexOf(_4[0]);
if(ind>-1){
str=_18.concat(str.substr(str[0],ind),_4[2],str.substr(str[ind+2],str.length-ind-2));
}else{
if((ind=str.indexOf(_4[1]))>-1){
str=_18.concat(str.substr(str[0],ind),_4[3],str.substr(str[ind+2],str.length-ind-2));
}
}
if(!_15){
var _1a="";
if(str.length>1){
var _1b=str.charAt(str.length-1);
str=_1a.concat(str.substr(0,str.length-1),"\"",_1b);
}else{
str=str.concat(_6[0]);
}
}
return str;
};
dojox.date.HebrewNumerals.parseDayHebrewLetters=function(day){
var _1d=0,i=0;
for(var j=0;j<day.length;j++){
for(i=1;i<=9;i++){
if(day.charAt(j)==_2[i-1]){
_1d+=10*i;
continue;
}
}
for(i=1;i<=9;i++){
if(day.charAt(j)==_1[i-1]){
_1d+=i;
}
}
}
return _1d;
};
dojox.date.HebrewNumerals.getMonthHebrewLetters=function(_20,_21,_22){
return _5[_20];
};
dojox.date.HebrewNumerals.parseMonthHebrewLetters=function(_23){
var _24=dojox.date.HebrewNumerals.parseDayHebrewLetters(_23)-1;
if(_24==-1){
console.warn("The month name is incorrect , set 0");
_24=0;
}
return _24;
};
})();
}
