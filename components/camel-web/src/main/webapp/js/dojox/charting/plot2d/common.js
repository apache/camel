/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.common"]){
dojo._hasResource["dojox.charting.plot2d.common"]=true;
dojo.provide("dojox.charting.plot2d.common");
dojo.require("dojo.colors");
dojo.require("dojox.gfx");
dojo.require("dojox.lang.functional");
(function(){
var df=dojox.lang.functional,dc=dojox.charting.plot2d.common;
dojo.mixin(dojox.charting.plot2d.common,{makeStroke:function(_3){
if(!_3){
return _3;
}
if(typeof _3=="string"||_3 instanceof dojo.Color){
_3={color:_3};
}
return dojox.gfx.makeParameters(dojox.gfx.defaultStroke,_3);
},augmentColor:function(_4,_5){
var t=new dojo.Color(_4),c=new dojo.Color(_5);
c.a=t.a;
return c;
},augmentStroke:function(_8,_9){
var s=dc.makeStroke(_8);
if(s){
s.color=dc.augmentColor(s.color,_9);
}
return s;
},augmentFill:function(_b,_c){
var fc,c=new dojo.Color(_c);
if(typeof _b=="string"||_b instanceof dojo.Color){
return dc.augmentColor(_b,_c);
}
return _b;
},defaultStats:{hmin:Number.POSITIVE_INFINITY,hmax:Number.NEGATIVE_INFINITY,vmin:Number.POSITIVE_INFINITY,vmax:Number.NEGATIVE_INFINITY},collectSimpleStats:function(_f){
var _10=dojo.clone(dc.defaultStats);
for(var i=0;i<_f.length;++i){
var run=_f[i];
if(!run.data.length){
continue;
}
if(typeof run.data[0]=="number"){
var _13=_10.vmin,_14=_10.vmax;
if(!("ymin" in run)||!("ymax" in run)){
dojo.forEach(run.data,function(val,i){
var x=i+1,y=val;
if(isNaN(y)){
y=0;
}
_10.hmin=Math.min(_10.hmin,x);
_10.hmax=Math.max(_10.hmax,x);
_10.vmin=Math.min(_10.vmin,y);
_10.vmax=Math.max(_10.vmax,y);
});
}
if("ymin" in run){
_10.vmin=Math.min(_13,run.ymin);
}
if("ymax" in run){
_10.vmax=Math.max(_14,run.ymax);
}
}else{
var _19=_10.hmin,_1a=_10.hmax,_13=_10.vmin,_14=_10.vmax;
if(!("xmin" in run)||!("xmax" in run)||!("ymin" in run)||!("ymax" in run)){
dojo.forEach(run.data,function(val,i){
var x=val.x,y=val.y;
if(isNaN(x)){
x=0;
}
if(isNaN(y)){
y=0;
}
_10.hmin=Math.min(_10.hmin,x);
_10.hmax=Math.max(_10.hmax,x);
_10.vmin=Math.min(_10.vmin,y);
_10.vmax=Math.max(_10.vmax,y);
});
}
if("xmin" in run){
_10.hmin=Math.min(_19,run.xmin);
}
if("xmax" in run){
_10.hmax=Math.max(_1a,run.xmax);
}
if("ymin" in run){
_10.vmin=Math.min(_13,run.ymin);
}
if("ymax" in run){
_10.vmax=Math.max(_14,run.ymax);
}
}
}
return _10;
},calculateBarSize:function(_1f,opt,_21){
if(!_21){
_21=1;
}
var gap=opt.gap,_23=(_1f-2*gap)/_21;
if("minBarSize" in opt){
_23=Math.max(_23,opt.minBarSize);
}
if("maxBarSize" in opt){
_23=Math.min(_23,opt.maxBarSize);
}
_23=Math.max(_23,1);
gap=(_1f-_23*_21)/2;
return {size:_23,gap:gap};
},collectStackedStats:function(_24){
var _25=dojo.clone(dc.defaultStats);
if(_24.length){
_25.hmin=Math.min(_25.hmin,1);
_25.hmax=df.foldl(_24,"seed, run -> Math.max(seed, run.data.length)",_25.hmax);
for(var i=0;i<_25.hmax;++i){
var v=_24[0].data[i];
if(isNaN(v)){
v=0;
}
_25.vmin=Math.min(_25.vmin,v);
for(var j=1;j<_24.length;++j){
var t=_24[j].data[i];
if(isNaN(t)){
t=0;
}
v+=t;
}
_25.vmax=Math.max(_25.vmax,v);
}
}
return _25;
},curve:function(a,_2b){
var arr=a.slice(0);
if(_2b=="x"){
arr[arr.length]=arr[0];
}
var p=dojo.map(arr,function(_2e,i){
if(i==0){
return "M"+_2e.x+","+_2e.y;
}
if(!isNaN(_2b)){
var dx=_2e.x-arr[i-1].x,dy=arr[i-1].y;
return "C"+(_2e.x-(_2b-1)*(dx/_2b))+","+dy+" "+(_2e.x-(dx/_2b))+","+_2e.y+" "+_2e.x+","+_2e.y;
}else{
if(_2b=="X"||_2b=="x"||_2b=="S"){
var p0,p1=arr[i-1],p2=arr[i],p3;
var _36,_37,_38,_39;
var f=1/6;
if(i==1){
if(_2b=="x"){
p0=arr[arr.length-2];
}else{
p0=p1;
}
f=1/3;
}else{
p0=arr[i-2];
}
if(i==(arr.length-1)){
if(_2b=="x"){
p3=arr[1];
}else{
p3=p2;
}
f=1/3;
}else{
p3=arr[i+1];
}
var _3b=Math.sqrt((p2.x-p1.x)*(p2.x-p1.x)+(p2.y-p1.y)*(p2.y-p1.y));
var _3c=Math.sqrt((p2.x-p0.x)*(p2.x-p0.x)+(p2.y-p0.y)*(p2.y-p0.y));
var _3d=Math.sqrt((p3.x-p1.x)*(p3.x-p1.x)+(p3.y-p1.y)*(p3.y-p1.y));
var _3e=_3c*f;
var _3f=_3d*f;
if(_3e>_3b/2&&_3f>_3b/2){
_3e=_3b/2;
_3f=_3b/2;
}else{
if(_3e>_3b/2){
_3e=_3b/2;
_3f=_3b/2*_3d/_3c;
}else{
if(_3f>_3b/2){
_3f=_3b/2;
_3e=_3b/2*_3c/_3d;
}
}
}
if(_2b=="S"){
if(p0==p1){
_3e=0;
}
if(p2==p3){
_3f=0;
}
}
_36=p1.x+_3e*(p2.x-p0.x)/_3c;
_37=p1.y+_3e*(p2.y-p0.y)/_3c;
_38=p2.x-_3f*(p3.x-p1.x)/_3d;
_39=p2.y-_3f*(p3.y-p1.y)/_3d;
}
}
return "C"+(_36+","+_37+" "+_38+","+_39+" "+p2.x+","+p2.y);
});
return p.join(" ");
}});
})();
}
