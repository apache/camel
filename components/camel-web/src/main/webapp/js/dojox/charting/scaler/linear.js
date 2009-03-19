/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.scaler.linear"]){
dojo._hasResource["dojox.charting.scaler.linear"]=true;
dojo.provide("dojox.charting.scaler.linear");
dojo.require("dojox.charting.scaler.common");
(function(){
var _1=3,dc=dojox.charting,_3=dc.scaler,_4=_3.common,_5=_4.findString,_6=_4.getNumericLabel;
var _7=function(_8,_9,_a,_b,_c,_d,_e){
_a=dojo.delegate(_a);
if(!_b){
if(_a.fixUpper=="major"){
_a.fixUpper="minor";
}
if(_a.fixLower=="major"){
_a.fixLower="minor";
}
}
if(!_c){
if(_a.fixUpper=="minor"){
_a.fixUpper="micro";
}
if(_a.fixLower=="minor"){
_a.fixLower="micro";
}
}
if(!_d){
if(_a.fixUpper=="micro"){
_a.fixUpper="none";
}
if(_a.fixLower=="micro"){
_a.fixLower="none";
}
}
var _f=_5(_a.fixLower,["major"])?Math.floor(_a.min/_b)*_b:_5(_a.fixLower,["minor"])?Math.floor(_a.min/_c)*_c:_5(_a.fixLower,["micro"])?Math.floor(_a.min/_d)*_d:_a.min,_10=_5(_a.fixUpper,["major"])?Math.ceil(_a.max/_b)*_b:_5(_a.fixUpper,["minor"])?Math.ceil(_a.max/_c)*_c:_5(_a.fixUpper,["micro"])?Math.ceil(_a.max/_d)*_d:_a.max;
if(_a.useMin){
_8=_f;
}
if(_a.useMax){
_9=_10;
}
var _11=(!_b||_a.useMin&&_5(_a.fixLower,["major"]))?_8:Math.ceil(_8/_b)*_b,_12=(!_c||_a.useMin&&_5(_a.fixLower,["major","minor"]))?_8:Math.ceil(_8/_c)*_c,_13=(!_d||_a.useMin&&_5(_a.fixLower,["major","minor","micro"]))?_8:Math.ceil(_8/_d)*_d,_14=!_b?0:(_a.useMax&&_5(_a.fixUpper,["major"])?Math.round((_9-_11)/_b):Math.floor((_9-_11)/_b))+1,_15=!_c?0:(_a.useMax&&_5(_a.fixUpper,["major","minor"])?Math.round((_9-_12)/_c):Math.floor((_9-_12)/_c))+1,_16=!_d?0:(_a.useMax&&_5(_a.fixUpper,["major","minor","micro"])?Math.round((_9-_13)/_d):Math.floor((_9-_13)/_d))+1,_17=_c?Math.round(_b/_c):0,_18=_d?Math.round(_c/_d):0,_19=_b?Math.floor(Math.log(_b)/Math.LN10):0,_1a=_c?Math.floor(Math.log(_c)/Math.LN10):0,_1b=_e/(_9-_8);
if(!isFinite(_1b)){
_1b=1;
}
return {bounds:{lower:_f,upper:_10,from:_8,to:_9,scale:_1b,span:_e},major:{tick:_b,start:_11,count:_14,prec:_19},minor:{tick:_c,start:_12,count:_15,prec:_1a},micro:{tick:_d,start:_13,count:_16,prec:0},minorPerMajor:_17,microPerMinor:_18,scaler:_3.linear};
};
dojo.mixin(dojox.charting.scaler.linear,{buildScaler:function(min,max,_1e,_1f){
var h={fixUpper:"none",fixLower:"none",natural:false};
if(_1f){
if("fixUpper" in _1f){
h.fixUpper=String(_1f.fixUpper);
}
if("fixLower" in _1f){
h.fixLower=String(_1f.fixLower);
}
if("natural" in _1f){
h.natural=Boolean(_1f.natural);
}
}
if("min" in _1f){
min=_1f.min;
}
if("max" in _1f){
max=_1f.max;
}
if(_1f.includeZero){
if(min>0){
min=0;
}
if(max<0){
max=0;
}
}
h.min=min;
h.useMin=true;
h.max=max;
h.useMax=true;
if("from" in _1f){
min=_1f.from;
h.useMin=false;
}
if("to" in _1f){
max=_1f.to;
h.useMax=false;
}
if(max<=min){
return _7(min,max,h,0,0,0,_1e);
}
var mag=Math.floor(Math.log(max-min)/Math.LN10),_22=_1f&&("majorTickStep" in _1f)?_1f.majorTickStep:Math.pow(10,mag),_23=0,_24=0,_25;
if(_1f&&("minorTickStep" in _1f)){
_23=_1f.minorTickStep;
}else{
do{
_23=_22/10;
if(!h.natural||_23>0.9){
_25=_7(min,max,h,_22,_23,0,_1e);
if(_25.bounds.scale*_25.minor.tick>_1){
break;
}
}
_23=_22/5;
if(!h.natural||_23>0.9){
_25=_7(min,max,h,_22,_23,0,_1e);
if(_25.bounds.scale*_25.minor.tick>_1){
break;
}
}
_23=_22/2;
if(!h.natural||_23>0.9){
_25=_7(min,max,h,_22,_23,0,_1e);
if(_25.bounds.scale*_25.minor.tick>_1){
break;
}
}
return _7(min,max,h,_22,0,0,_1e);
}while(false);
}
if(_1f&&("microTickStep" in _1f)){
_24=_1f.microTickStep;
_25=_7(min,max,h,_22,_23,_24,_1e);
}else{
do{
_24=_23/10;
if(!h.natural||_24>0.9){
_25=_7(min,max,h,_22,_23,_24,_1e);
if(_25.bounds.scale*_25.micro.tick>_1){
break;
}
}
_24=_23/5;
if(!h.natural||_24>0.9){
_25=_7(min,max,h,_22,_23,_24,_1e);
if(_25.bounds.scale*_25.micro.tick>_1){
break;
}
}
_24=_23/2;
if(!h.natural||_24>0.9){
_25=_7(min,max,h,_22,_23,_24,_1e);
if(_25.bounds.scale*_25.micro.tick>_1){
break;
}
}
_24=0;
}while(false);
}
return _24?_25:_7(min,max,h,_22,_23,0,_1e);
},buildTicks:function(_26,_27){
var _28,_29,_2a,_2b=_26.major.start,_2c=_26.minor.start,_2d=_26.micro.start;
if(_27.microTicks&&_26.micro.tick){
_28=_26.micro.tick,_29=_2d;
}else{
if(_27.minorTicks&&_26.minor.tick){
_28=_26.minor.tick,_29=_2c;
}else{
if(_26.major.tick){
_28=_26.major.tick,_29=_2b;
}else{
return null;
}
}
}
var _2e=1/_26.bounds.scale;
if(_26.bounds.to<=_26.bounds.from||isNaN(_2e)||!isFinite(_2e)||_28<=0||isNaN(_28)||!isFinite(_28)){
return null;
}
var _2f=[],_30=[],_31=[];
while(_29<=_26.bounds.to+_2e){
if(Math.abs(_2b-_29)<_28/2){
_2a={value:_2b};
if(_27.majorLabels){
_2a.label=_6(_2b,_26.major.prec,_27);
}
_2f.push(_2a);
_2b+=_26.major.tick;
_2c+=_26.minor.tick;
_2d+=_26.micro.tick;
}else{
if(Math.abs(_2c-_29)<_28/2){
if(_27.minorTicks){
_2a={value:_2c};
if(_27.minorLabels&&(_26.minMinorStep<=_26.minor.tick*_26.bounds.scale)){
_2a.label=_6(_2c,_26.minor.prec,_27);
}
_30.push(_2a);
}
_2c+=_26.minor.tick;
_2d+=_26.micro.tick;
}else{
if(_27.microTicks){
_31.push({value:_2d});
}
_2d+=_26.micro.tick;
}
}
_29+=_28;
}
return {major:_2f,minor:_30,micro:_31};
},getTransformerFromModel:function(_32){
var _33=_32.bounds.from,_34=_32.bounds.scale;
return function(x){
return (x-_33)*_34;
};
},getTransformerFromPlot:function(_36){
var _37=_36.bounds.from,_38=_36.bounds.scale;
return function(x){
return x/_38+_37;
};
}});
})();
}
