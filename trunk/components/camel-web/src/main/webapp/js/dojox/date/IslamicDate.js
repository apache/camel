/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.date.IslamicDate"]){
dojo._hasResource["dojox.date.IslamicDate"]=true;
dojo.provide("dojox.date.IslamicDate");
dojo.experimental("dojox.date.IslamicDate");
dojo.require("dojo.date.locale");
dojo.require("dojo.date");
dojo.requireLocalization("dojo.cldr","islamic",null,"ROOT,ar");
dojo.declare("dojox.date.IslamicDate",null,{_date:0,_month:0,_year:0,_hours:0,_minutes:0,_seconds:0,_milliseconds:0,_day:0,_GREGORIAN_EPOCH:1721425.5,_ISLAMIC_EPOCH:1948439.5,constructor:function(){
var _1=arguments.length;
if(_1==0){
var d=new Date();
this._day=d.getDay();
this.fromGregorian(d);
}else{
if(_1==1){
this.parse(arguments[0]);
}else{
if(_1>=3){
this._year=arguments[0];
this._month=arguments[1];
this._date=arguments[2];
this._hours=arguments[3]||0;
this._minutes=arguments[4]||0;
this._seconds=arguments[5]||0;
this._milliseconds=arguments[6]||0;
}
}
}
},getDate:function(){
return parseInt(this._date);
},getMonth:function(){
return parseInt(this._month);
},getFullYear:function(){
return parseInt(this._year);
},getDay:function(){
var gd=this.toGregorian();
return gd.getDay();
},getHours:function(){
return this._hours;
},getMinutes:function(){
return this._minutes;
},getSeconds:function(){
return this._seconds;
},getMilliseconds:function(){
return this._milliseconds;
},setDate:function(_4){
_4=parseInt(_4);
if(_4>0&&_4<=this.getDaysInIslamicMonth(this._month,this._year)){
this._date=_4;
}else{
var _5;
if(_4>0){
for(_5=this.getDaysInIslamicMonth(this._month,this._year);_4>_5;_4-=_5,_5=this.getDaysInIslamicMonth(this._month,this._year)){
this._month++;
if(this._month>=12){
this._year++;
this._month-=12;
}
}
this._date=_4;
}else{
for(_5=this.getDaysInIslamicMonth((this._month-1)>=0?(this._month-1):11,((this._month-1)>=0)?this._year:this._year-1);_4<=0;_5=this.getDaysInIslamicMonth((this._month-1)>=0?(this._month-1):11,((this._month-1)>=0)?this._year:this._year-1)){
this._month--;
if(this._month<0){
this._year--;
this._month+=12;
}
_4+=_5;
}
this._date=_4;
}
}
return this;
},setYear:function(_6){
this._year=parseInt(_6);
},setMonth:function(_7){
this._year+=Math.floor(_7/12);
this._month=Math.floor(_7%12);
},setHours:function(){
var _8=arguments.length;
var _9=0;
if(_8>=1){
_9=parseInt(arguments[0]);
}
if(_8>=2){
this._minutes=parseInt(arguments[1]);
}
if(_8>=3){
this._seconds=parseInt(arguments[2]);
}
if(_8==4){
this._milliseconds=parseInt(arguments[3]);
}
while(_9>=24){
this._date++;
var _a=this.getDaysInIslamicMonth(this._month,this._year);
if(this._date>_a){
this._month++;
if(this._month>=12){
this._year++;
this._month-=12;
}
this._date-=_a;
}
_9-=24;
}
this._hours=_9;
},setMinutes:function(_b){
while(_b>=60){
this._hours++;
if(this._hours>=24){
this._date++;
this._hours-=24;
var _c=this.getDaysInIslamicMonth(this._month,this._year);
if(this._date>_c){
this._month++;
if(this._month>=12){
this._year++;
this._month-=12;
}
this._date-=_c;
}
}
_b-=60;
}
this._minutes=_b;
},setSeconds:function(_d){
while(_d>=60){
this._minutes++;
if(this._minutes>=60){
this._hours++;
this._minutes-=60;
if(this._hours>=24){
this._date++;
this._hours-=24;
var _e=this.getDaysInIslamicMonth(this._month,this._year);
if(this._date>_e){
this._month++;
if(this._month>=12){
this._year++;
this._month-=12;
}
this._date-=_e;
}
}
}
_d-=60;
}
this._seconds=_d;
},setMilliseconds:function(_f){
while(_f>=1000){
this.setSeconds++;
if(this.setSeconds>=60){
this._minutes++;
this.setSeconds-=60;
if(this._minutes>=60){
this._hours++;
this._minutes-=60;
if(this._hours>=24){
this._date++;
this._hours-=24;
var _10=this.getDaysInIslamicMonth(this._month,this._year);
if(this._date>_10){
this._month++;
if(this._month>=12){
this._year++;
this._month-=12;
}
this._date-=_10;
}
}
}
}
_f-=1000;
}
this._milliseconds=_f;
},toString:function(){
var x=new Date();
x.setHours(this._hours);
x.setMinutes(this._minutes);
x.setSeconds(this._seconds);
x.setMilliseconds(this._milliseconds);
var _12=x.toTimeString();
return (dojox.date.IslamicDate.weekDays[this.getDay()]+" "+dojox.date.IslamicDate.months[this._month]+" "+this._date+" "+this._year+" "+_12);
},toGregorian:function(){
var _13=this._year;
var _14=this._month;
var _15=this._date;
var _16=_15+Math.ceil(29.5*_14)+(_13-1)*354+Math.floor((3+(11*_13))/30)+this._ISLAMIC_EPOCH-1;
var wjd=Math.floor(_16-0.5)+0.5,_18=wjd-this._GREGORIAN_EPOCH,_19=Math.floor(_18/146097),dqc=this._mod(_18,146097),_1b=Math.floor(dqc/36524),_1c=this._mod(dqc,36524),_1d=Math.floor(_1c/1461),_1e=this._mod(_1c,1461),_1f=Math.floor(_1e/365),_20=(_19*400)+(_1b*100)+(_1d*4)+_1f;
if(!(_1b==4||_1f==4)){
_20++;
}
var _21=this._GREGORIAN_EPOCH+(365*(_20-1))+Math.floor((_20-1)/4)-(Math.floor((_20-1)/100))+Math.floor((_20-1)/400);
var _22=wjd-_21;
var tjd=(this._GREGORIAN_EPOCH-1)+(365*(_20-1))+Math.floor((_20-1)/4)-(Math.floor((_20-1)/100))+Math.floor((_20-1)/400)+Math.floor((739/12)+((dojo.date.isLeapYear(new Date(_20,3,1))?-1:-2))+1);
var _24=((wjd<tjd)?0:(dojo.date.isLeapYear(new Date(_20,3,1))?1:2));
var _25=Math.floor((((_22+_24)*12)+373)/367);
var _26=(this._GREGORIAN_EPOCH-1)+(365*(_20-1))+Math.floor((_20-1)/4)-(Math.floor((_20-1)/100))+Math.floor((_20-1)/400)+Math.floor((((367*_25)-362)/12)+((_25<=2)?0:(dojo.date.isLeapYear(new Date(_20,_25,1))?-1:-2))+1);
var day=(wjd-_26);
var _28=new Date(_20,_25-1,day);
_28.setHours(this._hours);
_28.setMilliseconds(this._milliseconds);
_28.setMinutes(this._minutes);
_28.setSeconds(this._seconds);
return _28;
},fromGregorian:function(_29){
var _2a=new Date(_29);
var _2b=_2a.getFullYear(),_2c=_2a.getMonth(),_2d=_2a.getDate();
var _2e=(this._GREGORIAN_EPOCH-1)+(365*(_2b-1))+Math.floor((_2b-1)/4)+(-Math.floor((_2b-1)/100))+Math.floor((_2b-1)/400)+Math.floor((((367*(_2c+1))-362)/12)+(((_2c+1)<=2)?0:(dojo.date.isLeapYear(_2a)?-1:-2))+_2d)+(Math.floor(_2a.getSeconds()+60*(_2a.getMinutes()+60*_2a.getHours())+0.5)/86400);
_2e=Math.floor(_2e)+0.5;
var _2f=_2e-1948440;
var _30=Math.floor((30*_2f+10646)/10631);
var _31=Math.ceil((_2f-29-this._yearStart(_30))/29.5);
_31=Math.min(_31,11);
var _32=Math.ceil(_2f-this._monthStart(_30,_31))+1;
this._date=_32;
this._month=_31;
this._year=_30;
this._hours=_2a.getHours();
this._minutes=_2a.getMinutes();
this._seconds=_2a.getSeconds();
this._milliseconds=_2a.getMilliseconds();
this._day=_2a.getDay();
return this;
},parse:function(_33){
var _34=_33.toString();
var _35=/\d{1,2}\D\d{1,2}\D\d{4}/;
var sD,jd,mD=_34.match(_35);
if(mD){
mD=mD.toString();
sD=mD.split(/\D/);
this._month=sD[0]-1;
this._date=sD[1];
this._year=sD[2];
}else{
mD=_34.match(/\D{4,}\s\d{1,2}\s\d{4}/);
if(mD){
mD=mD.toString();
var _39=mD.match(/\d{1,2}\s\d{4}/);
_39=_39.toString();
var _3a=mD.replace(/\s\d{1,2}\s\d{4}/,"");
_3a=_3a.toString();
this._month=dojo.indexOf(this._months,_3a);
sD=_39.split(/\s/);
this._date=sD[0];
this._year=sD[1];
}
}
var _3b=_34.match(/\d{2}:/);
if(_3b!=null){
_3b=_3b.toString();
var _3c=_3b.split(":");
this._hours=_3c[0];
_3b=_34.match(/\d{2}:\d{2}/);
if(_3b){
_3b=_3b.toString();
_3c=_3b.split(":");
}
this._minutes=_3c[1]!=null?_3c[1]:0;
_3b=_34.match(/\d{2}:\d{2}:\d{2}/);
if(_3b){
_3b=_3b.toString();
_3c=_3b.split(":");
}
this._seconds=_3c[2]!=null?_3c[2]:0;
}else{
this._hours=0;
this._minutes=0;
this._seconds=0;
}
this._milliseconds=0;
},valueOf:function(){
var _3d=this.toGregorian();
return _3d.valueOf();
},_yearStart:function(_3e){
return (_3e-1)*354+Math.floor((3+11*_3e)/30);
},_monthStart:function(_3f,_40){
return Math.ceil(29.5*_40)+(_3f-1)*354+Math.floor((3+11*_3f)/30);
},_civilLeapYear:function(_41){
return (14+11*_41)%30<11;
},getDaysInIslamicMonth:function(_42,_43){
var _44=0;
_44=29+((_42+1)%2);
if(_42==11&&this._civilLeapYear(_43)){
_44++;
}
return _44;
},_mod:function(a,b){
return a-(b*Math.floor(a/b));
}});
dojox.date.IslamicDate.getDaysInIslamicMonth=function(_47){
return new dojox.date.IslamicDate().getDaysInIslamicMonth(_47.getMonth(),_47.getFullYear());
};
dojox.date.IslamicDate._getNames=function(_48,_49,use,_4b){
var _4c;
var _4d=dojo.i18n.getLocalization("dojo.cldr","islamic",_4b);
var _4e=[_48,use,_49];
if(use=="standAlone"){
_4c=_4d[_4e.join("-")];
}
_4e[1]="format";
return (_4c||_4d[_4e.join("-")]).concat();
};
dojox.date.IslamicDate.weekDays=dojox.date.IslamicDate._getNames("days","wide","format");
dojox.date.IslamicDate.months=dojox.date.IslamicDate._getNames("months","wide","format");
}
