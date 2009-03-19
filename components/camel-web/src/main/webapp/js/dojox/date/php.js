/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.date.php"]){
dojo._hasResource["dojox.date.php"]=true;
dojo.provide("dojox.date.php");
dojo.require("dojo.date");
dojo.require("dojox.string.tokenize");
dojox.date.php.format=function(_1,_2){
var df=new dojox.date.php.DateFormat(_2);
return df.format(_1);
};
dojox.date.php.DateFormat=function(_4){
if(!this.regex){
var _5=[];
for(var _6 in this.constructor.prototype){
if(dojo.isString(_6)&&_6.length==1&&dojo.isFunction(this[_6])){
_5.push(_6);
}
}
this.constructor.prototype.regex=new RegExp("(?:(\\\\.)|(["+_5.join("")+"]))","g");
}
var _7=[];
this.tokens=dojox.string.tokenize(_4,this.regex,function(_8,_9,i){
if(_9){
_7.push([i,_9]);
return _9;
}
if(_8){
return _8.charAt(1);
}
});
this.replacements=_7;
};
dojo.extend(dojox.date.php.DateFormat,{weekdays:["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"],weekdays_3:["Sun","Mon","Tue","Wed","Thu","Fri","Sat"],months:["January","February","March","April","May","June","July","August","September","October","November","December"],months_3:["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],monthdays:[31,28,31,30,31,30,31,31,30,31,30,31],format:function(_b){
this.date=_b;
for(var i=0,_d;_d=this.replacements[i];i++){
this.tokens[_d[0]]=this[_d[1]]();
}
return this.tokens.join("");
},d:function(){
var j=this.j();
return (j.length==1)?"0"+j:j;
},D:function(){
return this.weekdays_3[this.date.getDay()];
},j:function(){
return this.date.getDate()+"";
},l:function(){
return this.weekdays[this.date.getDay()];
},N:function(){
var w=this.w();
return (!w)?7:w;
},S:function(){
switch(this.date.getDate()){
case 11:
case 12:
case 13:
return "th";
case 1:
case 21:
case 31:
return "st";
case 2:
case 22:
return "nd";
case 3:
case 23:
return "rd";
default:
return "th";
}
},w:function(){
return this.date.getDay()+"";
},z:function(){
var _10=this.date.getTime()-new Date(this.date.getFullYear(),0,1).getTime();
return Math.floor(_10/86400000)+"";
},W:function(){
var _11;
var _12=new Date(this.date.getFullYear(),0,1).getDay()+1;
var w=this.date.getDay()+1;
var z=parseInt(this.z());
if(z<=(8-_12)&&_12>4){
var _15=new Date(this.date.getFullYear()-1,this.date.getMonth(),this.date.getDate());
if(_12==5||(_12==6&&dojo.date.isLeapYear(_15))){
_11=53;
}else{
_11=52;
}
}else{
var i;
if(Boolean(this.L())){
i=366;
}else{
i=365;
}
if((i-z)<(4-w)){
_11=1;
}else{
var j=z+(7-w)+(_12-1);
_11=Math.ceil(j/7);
if(_12>4){
--_11;
}
}
}
return _11;
},F:function(){
return this.months[this.date.getMonth()];
},m:function(){
var n=this.n();
return (n.length==1)?"0"+n:n;
},M:function(){
return this.months_3[this.date.getMonth()];
},n:function(){
return this.date.getMonth()+1+"";
},t:function(){
return (Boolean(this.L())&&this.date.getMonth()==1)?29:this.monthdays[this.getMonth()];
},L:function(){
return (dojo.date.isLeapYear(this.date))?"1":"0";
},o:function(){
},Y:function(){
return this.date.getFullYear()+"";
},y:function(){
return this.Y().slice(-2);
},a:function(){
return this.date.getHours()>=12?"pm":"am";
},b:function(){
return this.a().toUpperCase();
},B:function(){
var off=this.date.getTimezoneOffset()+60;
var _1a=(this.date.getHours()*3600)+(this.date.getMinutes()*60)+this.getSeconds()+(off*60);
var _1b=Math.abs(Math.floor(_1a/86.4)%1000)+"";
while(_1b.length<2){
_1b="0"+_1b;
}
return _1b;
},g:function(){
return (this.date.getHours()>12)?this.date.getHours()-12+"":this.date.getHours()+"";
},G:function(){
return this.date.getHours()+"";
},h:function(){
var g=this.g();
return (g.length==1)?"0"+g:g;
},H:function(){
var G=this.G();
return (G.length==1)?"0"+G:G;
},i:function(){
var _1e=this.date.getMinutes()+"";
return (_1e.length==1)?"0"+_1e:_1e;
},s:function(){
var _1f=this.date.getSeconds()+"";
return (_1f.length==1)?"0"+_1f:_1f;
},e:function(){
return dojo.date.getTimezoneName(this.date);
},I:function(){
},O:function(){
var off=Math.abs(this.date.getTimezoneOffset());
var _21=Math.floor(off/60)+"";
var _22=(off%60)+"";
if(_21.length==1){
_21="0"+_21;
}
if(_22.length==1){
_21="0"+_22;
}
return ((this.date.getTimezoneOffset()<0)?"+":"-")+_21+_22;
},P:function(){
var O=this.O();
return O.substring(0,2)+":"+O.substring(2,4);
},T:function(){
return this.e().substring(0,3);
},Z:function(){
return this.date.getTimezoneOffset()*-60;
},c:function(){
return this.Y()+"-"+this.m()+"-"+this.d()+"T"+this.h()+":"+this.i()+":"+this.s()+this.P();
},r:function(){
return this.D()+", "+this.d()+" "+this.M()+" "+this.Y()+" "+this.H()+":"+this.i()+":"+this.s()+" "+this.O();
},U:function(){
return Math.floor(this.date.getTime()/1000);
}});
}
