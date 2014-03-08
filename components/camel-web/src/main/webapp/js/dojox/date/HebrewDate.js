/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.date.HebrewDate"]){
dojo._hasResource["dojox.date.HebrewDate"]=true;
dojo.provide("dojox.date.HebrewDate");
dojo.experimental("dojox.date.HebrewDate");
dojo.declare("dojox.date.HebrewDate",null,{TISHRI:0,HESHVAN:1,KISLEV:2,TEVET:3,SHEVAT:4,ADAR_1:5,ADAR:6,NISAN:7,IYAR:8,SIVAN:9,TAMUZ:10,AV:11,ELUL:12,_HOUR_PARTS:1080,_DAY_PARTS:24*1080,_MONTH_DAYS:29,_MONTH_FRACT:12*1080+793,_MONTH_PARTS:29*24*1080+12*1080+793,BAHARAD:11*1080+204,JAN_1_1_JULIAN_DAY:1721426,_MONTH_LENGTH:[[30,30,30],[29,29,30],[29,30,30],[29,29,29],[30,30,30],[30,30,30],[29,29,29],[30,30,30],[29,29,29],[30,30,30],[29,29,29],[30,30,30],[29,29,29]],_MONTH_START:[[0,0,0],[30,30,30],[59,59,60],[88,89,90],[117,118,119],[147,148,149],[147,148,149],[176,177,178],[206,207,208],[235,236,237],[265,266,267],[294,295,296],[324,325,326],[353,354,355]],LEAP_MONTH_START:[[0,0,0],[30,30,30],[59,59,60],[88,89,90],[117,118,119],[147,148,149],[177,178,179],[206,207,208],[236,237,238],[265,266,267],[295,296,297],[324,325,326],[354,355,356],[383,384,385]],GREGORIAN_MONTH_COUNT:[[31,31,0,0],[28,29,31,31],[31,31,59,60],[30,30,90,91],[31,31,120,121],[30,30,151,152],[31,31,181,182],[31,31,212,213],[30,30,243,244],[31,31,273,274],[30,30,304,305],[31,31,334,335]],_date:0,_month:0,_year:0,_hours:0,_minutes:0,_seconds:0,_milliseconds:0,_day:0,constructor:function(){
var _1=arguments.length;
if(_1==0){
var _2=new Date();
var _3=this._computeHebrewFields(_2);
this._date=_3[2];
this._month=_3[1];
this._year=_3[0];
this._hours=_2.getHours();
this._minutes=_2.getMinutes();
this._seconds=_2.getSeconds();
this._milliseconds=_2.getMilliseconds();
this._day=_2.getDay();
}else{
if(_1==1){
this._year=arguments[0].getFullYear();
this._month=arguments[0].getMonth();
this._date=arguments[0].getDate();
this._hours=arguments[0].getHours();
this._minutes=arguments[0].getMinutes();
this._seconds=arguments[0].getSeconds();
this._milliseconds=arguments[0].getMilliseconds();
}else{
if(_1>=3){
this._year=parseInt(arguments[0]);
this._month=parseInt(arguments[1]);
this._date=parseInt(arguments[2]);
if(!this.isLeapYear(this._year)&&this._month>=5){
this._month++;
}
if(this._month>12||(!this.isLeapYear(this._year)&&this._month>11)){
console.warn("the month is incorrect , set 0");
this._month=0;
}
this._hours=(arguments[3]!=null)?parseInt(arguments[3]):0;
this._minutes=(arguments[4]!=null)?parseInt(arguments[4]):0;
this._seconds=(arguments[5]!=null)?parseInt(arguments[5]):0;
this._milliseconds=(arguments[6]!=null)?parseInt(arguments[6]):0;
}
}
}
var _4=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
_4+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
_4+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
_4+=(this._date-1);
this._day=((_4+1)%7);
},getDate:function(){
return parseInt(this._date);
},getMonth:function(){
return parseInt(this._month);
},getFullYear:function(){
return parseInt(this._year);
},getHours:function(){
return this._hours;
},getMinutes:function(){
return this._minutes;
},getSeconds:function(){
return this._seconds;
},getMilliseconds:function(){
return this._milliseconds;
},setDate:function(_5){
_5=parseInt(_5);
if(_5>0){
for(var _6=this.getDaysInHebrewMonth(this._month,this._year);_5>_6;_5-=_6,_6=this.getDaysInHebrewMonth(this._month,this._year)){
this._month++;
if(!this.isLeapYear(this._year)&&(this._month==5)){
this._month++;
}
if(this._month>=13){
this._year++;
this._month-=13;
}
}
this._date=_5;
}else{
for(_6=this.getDaysInHebrewMonth((this._month-1)>=0?(this._month-1):12,((this._month-1)>=0)?this._year:this._year-1);_5<=0;_6=this.getDaysInHebrewMonth((this._month-1)>=0?(this._month-1):12,((this._month-1)>=0)?this._year:this._year-1)){
this._month--;
if(!this.isLeapYear(this._year)&&this._month==5){
this._month--;
}
if(this._month<0){
this._year--;
this._month+=13;
}
_5+=_6;
}
this._date=_5;
}
var _7=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
_7+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
_7+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
_7+=(this._date-1);
this._day=((_7+1)%7);
return this;
},setYear:function(_8){
this._year=parseInt(_8);
if(!this.isLeapYear(this._year)&&this._month==6){
this._month--;
}
var _9=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
_9+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
_9+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
_9+=(this._date-1);
this._day=((_9+1)%7);
return this;
},setMonth:function(_a){
var _b=parseInt(_a);
if(!this.isLeapYear(this._year)&&_b>5){
_b++;
}
if(_b>=0){
while(_b>12){
this._year++;
_b-=13;
if(!this.isLeapYear(this._year)&&_b>5){
_b++;
}
}
}else{
while(_b<0){
this._year--;
_b+=13;
if(!this.isLeapYear(this._year)&&_b<=5){
_b--;
}
}
}
this._month=_b;
var _c=this.getDaysInHebrewMonth(this._month,this._year);
if(_c<this._date){
this._date=_c;
}
var _d=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
_d+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
_d+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
_d+=(this._date-1);
this._day=((_d+1)%7);
return this;
},setHours:function(){
var _e=arguments.length;
var _f=0;
if(_e>=1){
_f=parseInt(arguments[0]);
}
if(_e>=2){
this._minutes=parseInt(arguments[1]);
}
if(_e>=3){
this._seconds=parseInt(arguments[2]);
}
if(_e==4){
this._milliseconds=parseInt(arguments[3]);
}
while(_f>=24){
this._date++;
var _10=this.getDaysInHebrewMonth(this._month,this._year);
if(this._date>_10){
this._month++;
if(!this.isLeapYear(this._year)&&(this._month==5)){
this._month++;
}
if(this._month>=13){
this._year++;
this._month-=13;
}
this._date-=_10;
}
_f-=24;
}
this._hours=_f;
var day=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
day+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
day+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
day+=(this._date-1);
this._day=((day+1)%7);
return this;
},setMinutes:function(_12){
while(_12>=60){
this._hours++;
if(this._hours>=24){
this._date++;
this._hours-=24;
var _13=this.getDaysInHebrewMonth(this._month,this._year);
if(this._date>_13){
this._month++;
if(!this.isLeapYear(this._year)&&(this._month==5)){
this._month++;
}
if(this._month>=13){
this._year++;
this._month-=13;
}
this._date-=_13;
}
}
_12-=60;
}
this._minutes=_12;
var day=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
day+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
day+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
day+=(this._date-1);
this._day=((day+1)%7);
return this;
},setSeconds:function(_15){
while(_15>=60){
this._minutes++;
if(this._minutes>=60){
this._hours++;
this._minutes-=60;
if(this._hours>=24){
this._date++;
this._hours-=24;
var _16=this.getDaysInHebrewMonth(this._month,this._year);
if(this._date>_16){
this._month++;
if(!this.isLeapYear(this._year)&&(this._month==5)){
this._month++;
}
if(this._month>=13){
this._year++;
this._month-=13;
}
this._date-=_16;
}
}
}
_15-=60;
}
this._seconds=_15;
var day=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
day+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
day+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
day+=(this._date-1);
this._day=((day+1)%7);
return this;
},setMilliseconds:function(_18){
while(_18>=1000){
this.setSeconds++;
if(this.setSeconds>=60){
this._minutes++;
this._seconds-=60;
if(this._minutes>=60){
this._hours++;
this._minutes-=60;
if(this._hours>=24){
this._date++;
this._hours-=24;
var _19=this.getDaysInHebrewMonth(this._month,this._year);
if(this._date>_19){
this._month++;
if(!this.isLeapYear(this._year)&&(this._month==5)){
this._month++;
}
if(this._month>=13){
this._year++;
this._month-=13;
}
this._date-=_19;
}
}
}
}
_18-=1000;
}
this._milliseconds=_18;
var day=this._startOfYear(this._year);
if(this._month!=0){
if(this.isLeapYear(this._year)){
day+=this.LEAP_MONTH_START[this._month][this._yearType(this._year)];
}else{
day+=this._MONTH_START[this._month][this._yearType(this._year)];
}
}
day+=(this._date-1);
this._day=((day+1)%7);
return this;
},toString:function(){
return this._date+", "+((!this.isLeapYear(this._year)&&this._month>5)?this._month:(this._month+1))+", "+this._year+"  "+this._hours+":"+this._minutes+":"+this._seconds;
},valueOf:function(){
return this.toGregorian().valueOf();
},getDaysInHebrewMonth:function(_1b,_1c){
switch(_1b){
case this.HESHVAN:
case this.KISLEV:
return this._MONTH_LENGTH[_1b][this._yearType(_1c)];
default:
return this._MONTH_LENGTH[_1b][0];
}
},_yearType:function(_1d){
var _1e=this._handleGetYearLength(Number(_1d));
if(_1e>380){
_1e-=30;
}
switch(_1e){
case 353:
return 0;
case 354:
return 1;
case 355:
return 2;
}
throw new Error("Illegal year length "+_1e+" in year "+_1d);
},_handleGetYearLength:function(_1f){
return this._startOfYear(_1f+1)-this._startOfYear(_1f);
},_startOfYear:function(_20){
var _21=Math.floor((235*_20-234)/19);
var _22=_21*this._MONTH_FRACT+this.BAHARAD;
var day=_21*29+Math.floor(_22/this._DAY_PARTS);
_22%=this._DAY_PARTS;
var wd=day%7;
if(wd==2||wd==4||wd==6){
day+=1;
wd=day%7;
}
if(wd==1&&_22>15*this._HOUR_PARTS+204&&!this.isLeapYear(_20)){
day+=2;
}else{
if(wd==0&&_22>21*this._HOUR_PARTS+589&&this.isLeapYear(_20-1)){
day+=1;
}
}
return day;
},isLeapYear:function(_25){
var x=(_25*12+17)%19;
return x>=((x<0)?-7:12);
},fromGregorian:function(_27){
var _28=this._computeHebrewFields(_27);
this._year=_28[0];
this._month=_28[1];
this._date=_28[2];
this._hours=_27.getHours();
this._milliseconds=_27.getMilliseconds();
this._minutes=_27.getMinutes();
this._seconds=_27.getSeconds();
return this;
},_computeHebrewFields:function(_29){
var _2a=this._getJulianDayFromGregorianDate(_29);
var d=_2a-347997;
var m=Math.floor((d*this._DAY_PARTS)/this._MONTH_PARTS);
var _2d=Math.floor((19*m+234)/235)+1;
var ys=this._startOfYear(_2d);
var _2f=(d-ys);
while(_2f<1){
_2d--;
ys=this._startOfYear(_2d);
_2f=d-ys;
}
var _30=this._yearType(_2d);
var _31=this.isLeapYear(_2d)?this.LEAP_MONTH_START:this._MONTH_START;
var _32=0;
while(_2f>_31[_32][_30]){
_32++;
}
_32--;
var _33=_2f-_31[_32][_30];
return [_2d,_32,_33];
},toGregorian:function(){
var _34=this._year;
var _35=this._month;
var _36=this._date;
var day=this._startOfYear(_34);
if(_35!=0){
if(this.isLeapYear(_34)){
day+=this.LEAP_MONTH_START[_35][this._yearType(_34)];
}else{
day+=this._MONTH_START[_35][this._yearType(_34)];
}
}
var _38=(_36+day+347997);
var _39=_38-this.JAN_1_1_JULIAN_DAY;
var rem=new Array(1);
var _3b=this._floorDivide(_39,146097,rem);
var _3c=this._floorDivide(rem[0],36524,rem);
var n4=this._floorDivide(rem[0],1461,rem);
var n1=this._floorDivide(rem[0],365,rem);
var _3f=400*_3b+100*_3c+4*n4+n1;
var _40=rem[0];
if(_3c==4||n1==4){
_40=365;
}else{
++_3f;
}
var _41=!(_3f%4)&&(_3f%100||!(_3f%400));
var _42=0;
var _43=_41?60:59;
if(_40>=_43){
_42=_41?1:2;
}
var _44=Math.floor((12*(_40+_42)+6)/367);
var _45=_40-this.GREGORIAN_MONTH_COUNT[_44][_41?3:2]+1;
return new Date(_3f,_44,_45,this._hours,this._minutes,this._seconds,this._milliseconds);
},_floorDivide:function(_46,_47,_48){
if(_46>=0){
_48[0]=(_46%_47);
return Math.floor(_46/_47);
}
var _49=Math.floor(_46/_47);
_48[0]=_46-(_49*_47);
return _49;
},getDay:function(){
var _4a=this._year;
var _4b=this._month;
var _4c=this._date;
var day=this._startOfYear(_4a);
if(_4b!=0){
if(this.isLeapYear(_4a)){
day+=this.LEAP_MONTH_START[_4b][this._yearType(_4a)];
}else{
day+=this._MONTH_START[_4b][this._yearType(_4a)];
}
}
day+=_4c-1;
return (day+1)%7;
},_getJulianDayFromGregorianDate:function(_4e){
var _4f=_4e.getFullYear();
var _50=_4e.getMonth();
var d=_4e.getDate();
var _52=!(_4f%4)&&(_4f%100||!(_4f%400));
var y=_4f-1;
var _54=365*y+Math.floor(y/4)-Math.floor(y/100)+Math.floor(y/400)+this.JAN_1_1_JULIAN_DAY-1;
if(_50!=0){
_54+=this.GREGORIAN_MONTH_COUNT[_50][_52?3:2];
}
_54+=d;
return _54;
}});
dojox.date.HebrewDate.fromGregorian=function(_55){
var _56=new dojox.date.HebrewDate();
return _56.fromGregorian(_55);
};
dojox.date.HebrewDate.add=function(_57,_58,_59){
var _5a=new dojox.date.HebrewDate(_57);
switch(_58){
case "day":
_5a.setDate(_57.getDate()+_59);
break;
case "weekday":
var day=_57.getDay();
if(((day+_59)<5)&&((day+_59)>0)){
_5a.setDate(_57.getDate()+_59);
}else{
var _5c=0;
var _5d=0;
if(day==5){
day=4;
_5d=(_59>0)?-1:1;
}else{
if(day==6){
day=4;
_5d=(_59>0)?-2:2;
}
}
var add=(_59>0)?(5-day-1):(0-day);
var _5f=_59-add;
var div=parseInt(_5f/5);
if((_5f%5)!=0){
_5c=(_59>0)?2:-2;
}
_5c=_5c+div*7+_5f%5+add;
_5a.setDate(_57.getDate()+_5c+_5d);
}
break;
case "year":
_5a.setYear(_57.getFullYear()+_59);
break;
case "week":
_59*=7;
_5a.setDate(_57.getDate()+_59);
break;
case "month":
var _61=_57.getMonth();
_5a.setMonth(_57.getMonth()+_59);
break;
case "hour":
_5a.setHours(_57.getHours()+_59);
break;
case "minute":
_5a.setMinutes(_57.getMinutes()+_59);
break;
case "second":
_5a.setSeconds(_57.getSeconds()+_59);
break;
case "millisecond":
_5a.setMilliseconds(_57.getMilliseconds()+_59);
break;
}
return _5a;
};
dojox.date.HebrewDate.difference=function(_62,_63,_64){
_63=_63||new dojox.date.HebrewDate();
_64=_64||"day";
var _65=_62.getFullYear()-_63.getFullYear();
var _66=1;
switch(_64){
case "weekday":
var _67=Math.round(dojox.date.HebrewDate.difference(_62,_63,"day"));
var _68=parseInt(dojox.date.HebrewDate.difference(_62,_63,"week"));
var mod=_67%7;
if(mod==0){
_67=_68*5;
}else{
var adj=0;
var _6b=_63.getDay();
var _6c=_62.getDay();
_68=parseInt(_67/7);
mod=_67%7;
var _6d=new dojox.date.HebrewDate(_63);
_6d.setDate(_6d.getDate()+(_68*7));
var _6e=_6d.getDay();
if(_67>0){
switch(true){
case _6b==5:
adj=-1;
break;
case _6b==6:
adj=0;
break;
case _6c==5:
adj=-1;
break;
case _6c==6:
adj=-2;
break;
case (_6e+mod)>5:
adj=-2;
}
}else{
if(_67<0){
switch(true){
case _6b==5:
adj=0;
break;
case _6b==6:
adj=1;
break;
case _6c==5:
adj=2;
break;
case _6c==6:
adj=1;
break;
case (_6e+mod)<0:
adj=2;
}
}
}
_67+=adj;
_67-=(_68*2);
}
_66=_67;
break;
case "year":
_66=_65;
break;
case "month":
var _6f=(_62.toGregorian()>_63.toGregorian())?_62:_63;
var _70=(_62.toGregorian()>_63.toGregorian())?_63:_62;
var _71=_6f.getMonth();
var _72=_70.getMonth();
if(_65==0){
_66=(!_62.isLeapYear(_62.getFullYear())&&_6f.getMonth()>5&&_70.getMonth()<=5)?(_6f.getMonth()-_70.getMonth()-1):(_6f.getMonth()-_70.getMonth());
}else{
_66=(!_70.isLeapYear(_70.getFullYear())&&_72<6)?(13-_72-1):(13-_72);
_66+=(!_6f.isLeapYear(_6f.getFullYear())&&_71>5)?(_71-1):_71;
var i=_70.getFullYear()+1;
var e=_6f.getFullYear();
for(i;i<e;i++){
_66+=_70.isLeapYear(i)?13:12;
}
}
if(_62.toGregorian()<_63.toGregorian()){
_66=-_66;
}
break;
case "week":
_66=parseInt(dojox.date.HebrewDate.difference(_62,_63,"day")/7);
break;
case "day":
_66/=24;
case "hour":
_66/=60;
case "minute":
_66/=60;
case "second":
_66/=1000;
case "millisecond":
_66*=_62.toGregorian().getTime()-_63.toGregorian().getTime();
}
return Math.round(_66);
};
}
