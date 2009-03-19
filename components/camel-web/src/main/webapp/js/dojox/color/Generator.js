/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.color.Generator"]){
dojo._hasResource["dojox.color.Generator"]=true;
dojo.provide("dojox.color.Generator");
dojo.deprecated("dojox.color.Generator",1.3);
dojox.color.Generator=new (function(){
var _1=dojox.color;
var _2=function(_3){
if(!_3){
console.warn("dojox.color.Generator:: no base color was passed. ",_3);
return null;
}
if(!_3.toHsv){
_3=new _1.Color(_3);
}
return _3;
};
var _4=function(n,_6,_7){
var _8=[],i,_a=(_6-_7)/n,_b=_6;
for(i=0;i<n;i++,_b-=_a){
_8.push(_b);
}
return _8;
};
var _c=function(_d,_e,_f){
var c=_f.length-1,a=[],r,g,b;
for(var i=0;i<_e;i++){
if(i<_f.length){
r=_d.r+(255-_d.r)*_f[i],g=_d.g+(255-_d.g)*_f[i],b=_d.b+(255-_d.b)*_f[i];
a.push(new _1.Color({r:r,g:g,b:b}));
}else{
if(i==_f.length){
a.push(_d);
}else{
if(c<0){
c=_f.length-1;
}
r=_d.r*(1-_f[c]),g=_d.g*(1-_f[c]),b=_d.b*(1-_f[c--]);
a.push(new _1.Color({r:r,g:g,b:b}));
}
}
}
return a;
};
var _16=function(_17,_18){
var ret=[];
for(var i=0;i<_17[0].length;i++){
for(var j=0;j<_17.length;j++){
ret.push(_17[j][i]);
}
}
return ret.slice(0,_18);
};
this.analogous=function(_1c){
_1c=dojo.mixin({series:4,num:32,angleHigh:30,angleLow:8,high:0.5,low:0.15},_1c||{});
var _1d=_2(_1c.base,"analogous");
if(!_1d){
return [];
}
var num=_1c.num,hsv=_1d.toHsv();
var _20=_1c.series+1,_21=Math.ceil(num/_20);
var fs=_4(Math.floor(_21/2),_1c.high,_1c.low);
var ang=[];
var gen=Math.floor(_1c.series/2);
for(var i=1;i<=gen;i++){
var a=hsv.h+((_1c.angleLow*i)+1);
if(a>=360){
a-=360;
}
ang.push(a);
}
ang.push(0);
for(i=1;i<=gen;i++){
a=hsv.h-(_1c.angleHigh*i);
if(a<0){
a+=360;
}
ang.push(a);
}
var m=[],cur=0;
for(i=0;i<_20;i++){
m.push(_c(_1.fromHsv({h:ang[cur++],s:hsv.s,v:hsv.v}),_21,fs));
}
return _16(m,num);
};
this.monochromatic=function(_29){
_29=dojo.mixin({num:32,high:0.5,low:0.15},_29||{});
var _2a=_2(_29.base,"monochromatic");
if(!_2a){
return [];
}
var fs=_4(Math.floor(_29.num/2),_29.high,_29.low);
var a=_c(_2a,_29.num,fs);
return a;
};
this.triadic=function(_2d){
_2d=dojo.mixin({num:32,high:0.5,low:0.15},_2d||{});
var _2e=_2(_2d.base,"triadic");
if(!_2e){
return [];
}
var num=_2d.num,_30=3,_31=Math.ceil(num/_30),fs=_4(Math.floor(_31/2),_2d.high,_2d.low);
var m=[],hsv=_2e.toHsv();
var h1=hsv.h+57,h2=hsv.h-157;
if(h1>360){
h1-=360;
}
if(h2<0){
h2+=360;
}
var s1=(hsv.s>=20)?hsv.s-10:hsv.s+10;
var s2=(hsv.s>=95)?hsv.s-5:hsv.s+5;
var v2=(hsv.v>=70)?hsv.v-30:hsv.v+30;
m.push(_c(dojox.color.fromHsv({h:h1,s:s1,v:hsv.v}),_31,fs));
m.push(_c(_2e,_31,fs));
m.push(_c(dojox.color.fromHsv({h:h2,s:s2,v:v2}),_31,fs));
return _16(m,num);
};
this.complementary=function(_3a){
_3a=dojo.mixin({num:32,high:0.5,low:0.15},_3a||{});
var _3b=_2(_3a.base,"complimentary");
if(!_3b){
return [];
}
var num=_3a.num,_3d=2,_3e=Math.ceil(num/_3d),fs=_4(Math.floor(_3e/2),_3a.high,_3a.low);
var m=[],hsv=_3b.toHsv();
var _42=(hsv.h+120)%360;
m.push(_c(_3b,_3e,fs));
m.push(_c(dojox.color.fromHsv({h:_42,s:hsv.s,v:hsv.v}),_3e,fs));
return _16(m,num);
};
this.splitComplementary=function(_43){
_43=dojo.mixin({num:32,angle:30,high:0.5,low:0.15},_43||{});
var _44=_2(_43.base,"splitComplementary");
if(!_44){
return [];
}
var num=_43.num,_46=3,_47=Math.ceil(num/_46),fs=_4(Math.floor(_47/2),_43.high,_43.low);
var m=[],hsv=_44.toHsv();
var _4b=(hsv.h+120)%360;
var _4c=_4b-_43.angle,_4d=(_4b+_43.angle)%360;
if(_4c<0){
_4c+=360;
}
m.push(_c(_44,_47,fs));
m.push(_c(dojox.color.fromHsv({h:_4c,s:hsv.s,v:hsv.v}),_47,fs));
m.push(_c(dojox.color.fromHsv({h:_4d,s:hsv.s,v:hsv.v}),_47,fs));
return _16(m,num);
};
this.compound=function(_4e){
_4e=dojo.mixin({num:32,angle:30,high:0.5,low:0.15},_4e||{});
var _4f=_2(_4e.base,"compound");
if(!_4f){
return [];
}
var num=_4e.num,_51=4,_52=Math.ceil(num/_51),fs=_4(Math.floor(_52/2),_4e.high,_4e.low);
var m=[],hsv=_4f.toHsv();
var _56=(hsv.h+120)%360;
var h1=(hsv.h+_4e.angle)%360,h2=_56-_4e.angle,h3=_56-(_4e.angle/2);
if(h2<0){
h2+=360;
}
if(h3<0){
h3+=360;
}
var s1=(hsv.s>=90&&hsv.s<=100)?hsv.s-10:hsv.s+10;
var s2=(hsv.s<=35)?hsv.s+25:hsv.s-25;
var v1=hsv.v-20;
var v2=hsv.v;
m.push(_c(_4f,_52,fs));
m.push(_c(dojox.color.fromHsv({h:h1,s:s1,v:v1}),_52,fs));
m.push(_c(dojox.color.fromHsv({h:h2,s:s1,v:v1}),_52,fs));
m.push(_c(dojox.color.fromHsv({h:h3,s:s2,v:v2}),_52,fs));
return _16(m,num);
};
this.shades=function(_5e){
_5e=dojo.mixin({num:32,high:1.5,low:0.5},_5e||{});
var _5f=_2(_5e.base,"shades");
if(!_5f){
return [];
}
var num=_5e.num,hsv=_5f.toHsv();
var _62=(_5e.high-_5e.low)/num,cur=_5e.low;
var a=[];
for(var i=0;i<num;i++,cur+=_62){
a.push(_1.fromHsv({h:hsv.h,s:hsv.s,v:Math.min(Math.round(hsv.v*cur),100)}));
}
return a;
};
})();
}
