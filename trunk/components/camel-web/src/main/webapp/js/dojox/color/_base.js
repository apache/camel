/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.color._base"]){
dojo._hasResource["dojox.color._base"]=true;
dojo.provide("dojox.color._base");
dojo.require("dojo.colors");
dojox.color.Color=dojo.Color;
dojox.color.blend=dojo.blendColors;
dojox.color.fromRgb=dojo.colorFromRgb;
dojox.color.fromHex=dojo.colorFromHex;
dojox.color.fromArray=dojo.colorFromArray;
dojox.color.fromString=dojo.colorFromString;
dojox.color.greyscale=dojo.colors.makeGrey;
dojo.mixin(dojox.color,{fromCmy:function(_1,_2,_3){
if(dojo.isArray(_1)){
_2=_1[1],_3=_1[2],_1=_1[0];
}else{
if(dojo.isObject(_1)){
_2=_1.m,_3=_1.y,_1=_1.c;
}
}
_1/=100,_2/=100,_3/=100;
var r=1-_1,g=1-_2,b=1-_3;
return new dojox.color.Color({r:Math.round(r*255),g:Math.round(g*255),b:Math.round(b*255)});
},fromCmyk:function(_7,_8,_9,_a){
if(dojo.isArray(_7)){
_8=_7[1],_9=_7[2],_a=_7[3],_7=_7[0];
}else{
if(dojo.isObject(_7)){
_8=_7.m,_9=_7.y,_a=_7.b,_7=_7.c;
}
}
_7/=100,_8/=100,_9/=100,_a/=100;
var r,g,b;
r=1-Math.min(1,_7*(1-_a)+_a);
g=1-Math.min(1,_8*(1-_a)+_a);
b=1-Math.min(1,_9*(1-_a)+_a);
return new dojox.color.Color({r:Math.round(r*255),g:Math.round(g*255),b:Math.round(b*255)});
},fromHsl:function(_e,_f,_10){
if(dojo.isArray(_e)){
_f=_e[1],_10=_e[2],_e=_e[0];
}else{
if(dojo.isObject(_e)){
_f=_e.s,_10=_e.l,_e=_e.h;
}
}
_f/=100;
_10/=100;
while(_e<0){
_e+=360;
}
while(_e>=360){
_e-=360;
}
var r,g,b;
if(_e<120){
r=(120-_e)/60,g=_e/60,b=0;
}else{
if(_e<240){
r=0,g=(240-_e)/60,b=(_e-120)/60;
}else{
r=(_e-240)/60,g=0,b=(360-_e)/60;
}
}
r=2*_f*Math.min(r,1)+(1-_f);
g=2*_f*Math.min(g,1)+(1-_f);
b=2*_f*Math.min(b,1)+(1-_f);
if(_10<0.5){
r*=_10,g*=_10,b*=_10;
}else{
r=(1-_10)*r+2*_10-1;
g=(1-_10)*g+2*_10-1;
b=(1-_10)*b+2*_10-1;
}
return new dojox.color.Color({r:Math.round(r*255),g:Math.round(g*255),b:Math.round(b*255)});
},fromHsv:function(hue,_15,_16){
if(dojo.isArray(hue)){
_15=hue[1],_16=hue[2],hue=hue[0];
}else{
if(dojo.isObject(hue)){
_15=hue.s,_16=hue.v,hue=hue.h;
}
}
if(hue==360){
hue=0;
}
_15/=100;
_16/=100;
var r,g,b;
if(_15==0){
r=_16,b=_16,g=_16;
}else{
var _1a=hue/60,i=Math.floor(_1a),f=_1a-i;
var p=_16*(1-_15);
var q=_16*(1-(_15*f));
var t=_16*(1-(_15*(1-f)));
switch(i){
case 0:
r=_16,g=t,b=p;
break;
case 1:
r=q,g=_16,b=p;
break;
case 2:
r=p,g=_16,b=t;
break;
case 3:
r=p,g=q,b=_16;
break;
case 4:
r=t,g=p,b=_16;
break;
case 5:
r=_16,g=p,b=q;
break;
}
}
return new dojox.color.Color({r:Math.round(r*255),g:Math.round(g*255),b:Math.round(b*255)});
}});
dojo.extend(dojox.color.Color,{toCmy:function(){
var _20=1-(this.r/255),_21=1-(this.g/255),_22=1-(this.b/255);
return {c:Math.round(_20*100),m:Math.round(_21*100),y:Math.round(_22*100)};
},toCmyk:function(){
var _23,_24,_25,_26;
var r=this.r/255,g=this.g/255,b=this.b/255;
_26=Math.min(1-r,1-g,1-b);
_23=(1-r-_26)/(1-_26);
_24=(1-g-_26)/(1-_26);
_25=(1-b-_26)/(1-_26);
return {c:Math.round(_23*100),m:Math.round(_24*100),y:Math.round(_25*100),b:Math.round(_26*100)};
},toHsl:function(){
var r=this.r/255,g=this.g/255,b=this.b/255;
var min=Math.min(r,b,g),max=Math.max(r,g,b);
var _2f=max-min;
var h=0,s=0,l=(min+max)/2;
if(l>0&&l<1){
s=_2f/((l<0.5)?(2*l):(2-2*l));
}
if(_2f>0){
if(max==r&&max!=g){
h+=(g-b)/_2f;
}
if(max==g&&max!=b){
h+=(2+(b-r)/_2f);
}
if(max==b&&max!=r){
h+=(4+(r-g)/_2f);
}
h*=60;
}
return {h:h,s:Math.round(s*100),l:Math.round(l*100)};
},toHsv:function(){
var r=this.r/255,g=this.g/255,b=this.b/255;
var min=Math.min(r,b,g),max=Math.max(r,g,b);
var _38=max-min;
var h=null,s=(max==0)?0:(_38/max);
if(s==0){
h=0;
}else{
if(r==max){
h=60*(g-b)/_38;
}else{
if(g==max){
h=120+60*(b-r)/_38;
}else{
h=240+60*(r-g)/_38;
}
}
if(h<0){
h+=360;
}
}
return {h:h,s:Math.round(s*100),v:Math.round(max*100)};
}});
}
