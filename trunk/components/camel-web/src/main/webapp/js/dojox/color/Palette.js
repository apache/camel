/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.color.Palette"]){
dojo._hasResource["dojox.color.Palette"]=true;
dojo.provide("dojox.color.Palette");
dojo.require("dojox.color");
(function(){
var _1=dojox.color;
_1.Palette=function(_2){
this.colors=[];
if(_2 instanceof dojox.color.Palette){
this.colors=_2.colors.slice(0);
}else{
if(_2 instanceof dojox.color.Color){
this.colors=[null,null,_2,null,null];
}else{
if(dojo.isArray(_2)){
this.colors=dojo.map(_2.slice(0),function(_3){
if(dojo.isString(_3)){
return new dojox.color.Color(_3);
}
return _3;
});
}else{
if(dojo.isString(_2)){
this.colors=[null,null,new dojox.color.Color(_2),null,null];
}
}
}
}
};
function _4(p,_6,_7){
var _8=new dojox.color.Palette();
_8.colors=[];
dojo.forEach(p.colors,function(_9){
var r=(_6=="dr")?_9.r+_7:_9.r,g=(_6=="dg")?_9.g+_7:_9.g,b=(_6=="db")?_9.b+_7:_9.b,a=(_6=="da")?_9.a+_7:_9.a;
_8.colors.push(new dojox.color.Color({r:Math.min(255,Math.max(0,r)),g:Math.min(255,Math.max(0,g)),b:Math.min(255,Math.max(0,b)),a:Math.min(1,Math.max(0,a))}));
});

return _8;
};
function _e(p,_10,val){
var ret=new dojox.color.Palette();
ret.colors=[];
dojo.forEach(p.colors,function(_13){
var o=_13.toCmy(),c=(_10=="dc")?o.c+val:o.c,m=(_10=="dm")?o.m+val:o.m,y=(_10=="dy")?o.y+val:o.y;
ret.colors.push(dojox.color.fromCmy(Math.min(100,Math.max(0,c)),Math.min(100,Math.max(0,m)),Math.min(100,Math.max(0,y))));
});
return ret;
};
function _18(p,_1a,val){
var ret=new dojox.color.Palette();
ret.colors=[];
dojo.forEach(p.colors,function(_1d){
var o=_1d.toCmyk(),c=(_1a=="dc")?o.c+val:o.c,m=(_1a=="dm")?o.m+val:o.m,y=(_1a=="dy")?o.y+val:o.y,k=(_1a=="dk")?o.b+val:o.b;
ret.colors.push(dojox.color.fromCmyk(Math.min(100,Math.max(0,c)),Math.min(100,Math.max(0,m)),Math.min(100,Math.max(0,y)),Math.min(100,Math.max(0,k))));
});
return ret;
};
function _23(p,_25,val){
var ret=new dojox.color.Palette();
ret.colors=[];
dojo.forEach(p.colors,function(_28){
var o=_28.toHsl(),h=(_25=="dh")?o.h+val:o.h,s=(_25=="ds")?o.s+val:o.s,l=(_25=="dl")?o.l+val:o.l;
ret.colors.push(dojox.color.fromHsl(h%360,Math.min(100,Math.max(0,s)),Math.min(100,Math.max(0,l))));
});
return ret;
};
function _2d(p,_2f,val){
var ret=new dojox.color.Palette();
ret.colors=[];
dojo.forEach(p.colors,function(_32){
var o=_32.toHsv(),h=(_2f=="dh")?o.h+val:o.h,s=(_2f=="ds")?o.s+val:o.s,v=(_2f=="dv")?o.v+val:o.v;
ret.colors.push(dojox.color.fromHsv(h%360,Math.min(100,Math.max(0,s)),Math.min(100,Math.max(0,v))));
});
return ret;
};
function _37(val,low,_3a){
return _3a-((_3a-val)*((_3a-low)/_3a));
};
dojo.extend(_1.Palette,{transform:function(_3b){
var fn=_4;
if(_3b.use){
var use=_3b.use.toLowerCase();
if(use.indexOf("hs")==0){
if(use.charAt(2)=="l"){
fn=_23;
}else{
fn=_2d;
}
}else{
if(use.indexOf("cmy")==0){
if(use.charAt(3)=="k"){
fn=_18;
}else{
fn=_e;
}
}
}
}else{
if("dc" in _3b||"dm" in _3b||"dy" in _3b){
if("dk" in _3b){
fn=_18;
}else{
fn=_e;
}
}else{
if("dh" in _3b||"ds" in _3b){
if("dv" in _3b){
fn=_2d;
}else{
fn=_23;
}
}
}
}
var _3e=this;
for(var p in _3b){
if(p=="use"){
continue;
}
_3e=fn(_3e,p,_3b[p]);
}
return _3e;
},clone:function(){
return new _1.Palette(this);
}});
dojo.mixin(_1.Palette,{generators:{analogous:function(_40){
var _41=_40.high||60,low=_40.low||18,_43=dojo.isString(_40.base)?new dojox.color.Color(_40.base):_40.base,hsv=_43.toHsv();
var h=[(hsv.h+low+360)%360,(hsv.h+Math.round(low/2)+360)%360,hsv.h,(hsv.h-Math.round(_41/2)+360)%360,(hsv.h-_41+360)%360];
var s1=Math.max(10,(hsv.s<=95)?hsv.s+5:(100-(hsv.s-95))),s2=(hsv.s>1)?hsv.s-1:21-hsv.s,v1=(hsv.v>=92)?hsv.v-9:Math.max(hsv.v+9,20),v2=(hsv.v<=90)?Math.max(hsv.v+5,20):(95+Math.ceil((hsv.v-90)/2)),s=[s1,s2,hsv.s,s1,s1],v=[v1,v2,hsv.v,v1,v2];
return new _1.Palette(dojo.map(h,function(hue,i){
return dojox.color.fromHsv(hue,s[i],v[i]);
}));
},monochromatic:function(_4e){
var _4f=dojo.isString(_4e.base)?new dojox.color.Color(_4e.base):_4e.base,hsv=_4f.toHsv();
var s1=(hsv.s-30>9)?hsv.s-30:hsv.s+30,s2=hsv.s,v1=_37(hsv.v,20,100),v2=(hsv.v-20>20)?hsv.v-20:hsv.v+60,v3=(hsv.v-50>20)?hsv.v-50:hsv.v+30;
return new _1.Palette([dojox.color.fromHsv(hsv.h,s1,v1),dojox.color.fromHsv(hsv.h,s2,v3),_4f,dojox.color.fromHsv(hsv.h,s1,v3),dojox.color.fromHsv(hsv.h,s2,v2)]);
},triadic:function(_56){
var _57=dojo.isString(_56.base)?new dojox.color.Color(_56.base):_56.base,hsv=_57.toHsv();
var h1=(hsv.h+57+360)%360,h2=(hsv.h-157+360)%360,s1=(hsv.s>20)?hsv.s-10:hsv.s+10,s2=(hsv.s>90)?hsv.s-10:hsv.s+10,s3=(hsv.s>95)?hsv.s-5:hsv.s+5,v1=(hsv.v-20>20)?hsv.v-20:hsv.v+20,v2=(hsv.v-30>20)?hsv.v-30:hsv.v+30,v3=(hsv.v-30>70)?hsv.v-30:hsv.v+30;
return new _1.Palette([dojox.color.fromHsv(h1,s1,hsv.v),dojox.color.fromHsv(hsv.h,s2,v2),_57,dojox.color.fromHsv(h2,s2,v1),dojox.color.fromHsv(h2,s3,v3)]);
},complementary:function(_61){
var _62=dojo.isString(_61.base)?new dojox.color.Color(_61.base):_61.base,hsv=_62.toHsv();
var h1=((hsv.h*2)+137<360)?(hsv.h*2)+137:Math.floor(hsv.h/2)-137,s1=Math.max(hsv.s-10,0),s2=_37(hsv.s,10,100),s3=Math.min(100,hsv.s+20),v1=Math.min(100,hsv.v+30),v2=(hsv.v>20)?hsv.v-30:hsv.v+30;
return new _1.Palette([dojox.color.fromHsv(hsv.h,s1,v1),dojox.color.fromHsv(hsv.h,s2,v2),_62,dojox.color.fromHsv(h1,s3,v2),dojox.color.fromHsv(h1,hsv.s,hsv.v)]);
},splitComplementary:function(_6a){
var _6b=dojo.isString(_6a.base)?new dojox.color.Color(_6a.base):_6a.base,_6c=_6a.da||30,hsv=_6b.toHsv();
var _6e=((hsv.h*2)+137<360)?(hsv.h*2)+137:Math.floor(hsv.h/2)-137,h1=(_6e-_6c+360)%360,h2=(_6e+_6c)%360,s1=Math.max(hsv.s-10,0),s2=_37(hsv.s,10,100),s3=Math.min(100,hsv.s+20),v1=Math.min(100,hsv.v+30),v2=(hsv.v>20)?hsv.v-30:hsv.v+30;
return new _1.Palette([dojox.color.fromHsv(h1,s1,v1),dojox.color.fromHsv(h1,s2,v2),_6b,dojox.color.fromHsv(h2,s3,v2),dojox.color.fromHsv(h2,hsv.s,hsv.v)]);
},compound:function(_76){
var _77=dojo.isString(_76.base)?new dojox.color.Color(_76.base):_76.base,hsv=_77.toHsv();
var h1=((hsv.h*2)+18<360)?(hsv.h*2)+18:Math.floor(hsv.h/2)-18,h2=((hsv.h*2)+120<360)?(hsv.h*2)+120:Math.floor(hsv.h/2)-120,h3=((hsv.h*2)+99<360)?(hsv.h*2)+99:Math.floor(hsv.h/2)-99,s1=(hsv.s-40>10)?hsv.s-40:hsv.s+40,s2=(hsv.s-10>80)?hsv.s-10:hsv.s+10,s3=(hsv.s-25>10)?hsv.s-25:hsv.s+25,v1=(hsv.v-40>10)?hsv.v-40:hsv.v+40,v2=(hsv.v-20>80)?hsv.v-20:hsv.v+20,v3=Math.max(hsv.v,20);
return new _1.Palette([dojox.color.fromHsv(h1,s1,v1),dojox.color.fromHsv(h1,s2,v2),_77,dojox.color.fromHsv(h2,s3,v3),dojox.color.fromHsv(h3,s2,v2)]);
},shades:function(_82){
var _83=dojo.isString(_82.base)?new dojox.color.Color(_82.base):_82.base,hsv=_83.toHsv();
var s=(hsv.s==100&&hsv.v==0)?0:hsv.s,v1=(hsv.v-50>20)?hsv.v-50:hsv.v+30,v2=(hsv.v-25>=20)?hsv.v-25:hsv.v+55,v3=(hsv.v-75>=20)?hsv.v-75:hsv.v+5,v4=Math.max(hsv.v-10,20);
return new _1.Palette([new dojox.color.fromHsv(hsv.h,s,v1),new dojox.color.fromHsv(hsv.h,s,v2),_83,new dojox.color.fromHsv(hsv.h,s,v3),new dojox.color.fromHsv(hsv.h,s,v4)]);
}},generate:function(_8a,_8b){
if(dojo.isFunction(_8b)){
return _8b({base:_8a});
}else{
if(_1.Palette.generators[_8b]){
return _1.Palette.generators[_8b]({base:_8a});
}
}
throw new Error("dojox.color.Palette.generate: the specified generator ('"+_8b+"') does not exist.");
}});
})();
}
