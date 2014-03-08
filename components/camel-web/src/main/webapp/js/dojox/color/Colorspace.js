/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.color.Colorspace"]){
dojo._hasResource["dojox.color.Colorspace"]=true;
dojo.provide("dojox.color.Colorspace");
dojo.require("dojox.math.matrix");
dojox.color.Colorspace=new (function(){
var _1=dojox.color;
var _2=dojox.math.matrix;
var _3=this;
var _4={"2":{"E":{x:1/3,y:1/3,t:5400},"D50":{x:0.34567,y:0.3585,t:5000},"D55":{x:0.33242,y:0.34743,t:5500},"D65":{x:0.31271,y:0.32902,t:6500},"D75":{x:0.29902,y:0.31485,t:7500},"A":{x:0.44757,y:0.40745,t:2856},"B":{x:0.34842,y:0.35161,t:4874},"C":{x:0.31006,y:0.31616,t:6774},"9300":{x:0.2848,y:0.2932,t:9300},"F2":{x:0.37207,y:0.37512,t:4200},"F7":{x:0.31285,y:0.32918,t:6500},"F11":{x:0.38054,y:0.37691,t:4000}},"10":{"E":{x:1/3,y:1/3,t:5400},"D50":{x:0.34773,y:0.35952,t:5000},"D55":{x:0.33411,y:0.34877,t:5500},"D65":{x:0.31382,y:0.331,t:6500},"D75":{x:0.29968,y:0.3174,t:7500},"A":{x:0.45117,y:0.40594,t:2856},"B":{x:0.3498,y:0.3527,t:4874},"C":{x:0.31039,y:0.31905,t:6774},"F2":{x:0.37928,y:0.36723,t:4200},"F7":{x:0.31565,y:0.32951,t:6500},"F11":{x:0.38543,y:0.3711,t:4000}}};
var _5={"Adobe RGB 98":[2.2,"D65",0.64,0.33,0.297361,0.21,0.71,0.627355,0.15,0.06,0.075285],"Apple RGB":[1.8,"D65",0.625,0.34,0.244634,0.28,0.595,0.672034,0.155,0.07,0.083332],"Best RGB":[2.2,"D50",0.7347,0.2653,0.228457,0.215,0.775,0.737352,0.13,0.035,0.034191],"Beta RGB":[2.2,"D50",0.6888,0.3112,0.303273,0.1986,0.7551,0.663786,0.1265,0.0352,0.032941],"Bruce RGB":[2.2,"D65",0.64,0.33,0.240995,0.28,0.65,0.683554,0.15,0.06,0.075452],"CIE RGB":[2.2,"E",0.735,0.265,0.176204,0.274,0.717,0.812985,0.167,0.009,0.010811],"ColorMatch RGB":[1.8,"D50",0.63,0.34,0.274884,0.295,0.605,0.658132,0.15,0.075,0.066985],"DON RGB 4":[2.2,"D50",0.696,0.3,0.27835,0.215,0.765,0.68797,0.13,0.035,0.03368],"ECI RGB":[1.8,"D50",0.67,0.33,0.32025,0.21,0.71,0.602071,0.14,0.08,0.077679],"EktaSpace PS5":[2.2,"D50",0.695,0.305,0.260629,0.26,0.7,0.734946,0.11,0.005,0.004425],"NTSC RGB":[2.2,"C",0.67,0.33,0.298839,0.21,0.71,0.586811,0.14,0.08,0.11435],"PAL/SECAM RGB":[2.2,"D65",0.64,0.33,0.222021,0.29,0.6,0.706645,0.15,0.06,0.071334],"Pro Photo RGB":[1.8,"D50",0.7347,0.2653,0.28804,0.1596,0.8404,0.711874,0.0366,0.0001,0.000086],"SMPTE/C RGB":[2.2,"D65",0.63,0.34,0.212395,0.31,0.595,0.701049,0.155,0.07,0.086556],"sRGB":[2.2,"D65",0.64,0.33,0.212656,0.3,0.6,0.715158,0.15,0.06,0.072186],"Wide Gamut RGB":[2.2,"D50",0.735,0.265,0.258187,0.115,0.826,0.724938,0.157,0.018,0.016875]};
var _6={"XYZ scaling":{ma:[[1,0,0],[0,1,0],[0,0,1]],mai:[[1,0,0],[0,1,0],[0,0,1]]},"Bradford":{ma:[[0.8951,-0.7502,0.0389],[0.2664,1.7135,-0.0685],[-0.1614,0.0367,1.0296]],mai:[[0.986993,0.432305,-0.008529],[-0.147054,0.51836,0.040043],[0.159963,0.049291,0.968487]]},"Von Kries":{ma:[[0.40024,-0.2263,0],[0.7076,1.16532,0],[-0.08081,0.0457,0.91822]],mai:[[1.859936,0.361191,0],[-1.129382,0.638812,0],[0.219897,-0.000006,1.089064]]}};
var _7={"XYZ":{"xyY":function(_8,_9){
_9=dojo.mixin({whitepoint:"D65",observer:"10",useApproximation:true},_9||{});
var wp=_3.whitepoint(_9.whitepoint,_9.observer);
var _b=_8.X+_8.Y+_8.Z;
if(_b==0){
var x=wp.x,y=wp.y;
}else{
var x=_8.X/_b,y=_8.Y/_b;
}
return {x:x,y:y,Y:_8.Y};
},"Lab":function(_e,_f){
_f=dojo.mixin({whitepoint:"D65",observer:"10",useApproximation:true},_f||{});
var _10=_3.kappa(_f.useApproximation),_11=_3.epsilon(_f.useApproximation);
var wp=_3.whitepoint(_f.whitepoint,_f.observer);
var xr=_e.X/wp.x,yr=_e.Y/wp.y,zr=_e.z/wp.z;
var fx=(xr>_11)?Math.pow(xr,1/3):(_10*xr+16)/116;
var fy=(yr>_11)?Math.pow(yr,1/3):(_10*yr+16)/116;
var fz=(zr>_11)?Math.pow(zr,1/3):(_10*zr+16)/116;
var L=116*fy-16,a=500*(fx-fy),b=200*(fy-fz);
return {L:L,a:a,b:b};
},"Luv":function(xyz,_1d){
_1d=dojo.mixin({whitepoint:"D65",observer:"10",useApproximation:true},_1d||{});
var _1e=_3.kappa(_1d.useApproximation),_1f=_3.epsilon(_1d.useApproximation);
var wp=_3.whitepoint(_1d.whitepoint,_1d.observer);
var ud=(4*xyz.X)/(xyz.X+15*xyz.Y+3*xyz.Z);
var vd=(9*xyz.Y)/(xyz.X+15*xyz.Y+3*xyz.Z);
var udr=(4*wp.x)/(wp.x+15*wp.y+3*wp.z);
var vdr=(9*wp.y)/(wp.x+15*wp.y+3*wp.z);
var yr=xyz.Y/wp.y;
var L=(yr>_1f)?116*Math.pow(yr,1/3)-16:_1e*yr;
var u=13*L*(ud-udr);
var v=13*L*(vd-vdr);
return {L:L,u:u,v:v};
}},"xyY":{"XYZ":function(xyY){
if(xyY.y==0){
var X=0,Y=0,Z=0;
}else{
var X=(xyY.x*xyY.Y)/xyY.y;
var Y=xyY.Y;
var Z=((1-xyY.x-xyY.y)*xyY.Y)/xyY.y;
}
return {X:X,Y:Y,Z:Z};
}},"Lab":{"XYZ":function(lab,_2e){
_2e=dojo.mixin({whitepoint:"D65",observer:"10",useApproximation:true},_2e||{});
var b=_2e.useApproximation,_30=_3.kappa(b),_31=_3.epsilon(b);
var wp=_3.whitepoint(_2e.whitepoint,_2e.observer);
var yr=(lab.L>(_30*_31))?Math.pow((lab.L+16)/116,3):lab.L/_30;
var fy=(yr>_31)?(lab.L+16)/116:(_30*yr+16)/116;
var fx=(lab.a/500)+fy;
var fz=fy-(lab.b/200);
var _37=Math.pow(fx,3),_38=Math.pow(fz,3);
var xr=(_37>_31)?_37:(116*fx-16)/_30;
var zr=(_38>_31)?_38:(116*fz-16)/_30;
return {X:xr*wp.x,Y:yr*wp.y,Z:zr*wp.z};
},"LCHab":function(lab){
var L=lab.L,C=Math.pow(lab.a*lab.a+lab.b*lab.b,0.5),H=Math.atan(lab.b,lab.a)*(180/Math.PI);
if(H<0){
H+=360;
}
if(H<360){
H-=360;
}
return {L:L,C:C,H:H};
}},"LCHab":{"Lab":function(lch){
var _40=lch.H*(Math.PI/180),L=lch.L,a=lch.C/Math.pow(Math.pow(Math.tan(_40),2)+1,0.5);
if(90<lchH&&lch.H<270){
a=-a;
}
var b=Math.pow(Math.pow(lch.C,2)-Math.pow(a,2),0.5);
if(lch.H>180){
b=-b;
}
return {L:L,a:a,b:b};
}},"Luv":{"XYZ":function(Luv,_45){
_45=dojo.mixin({whitepoint:"D65",observer:"10",useApproximation:true},_45||{});
var b=_45.useApproximation,_47=_3.kappa(b),_48=_3.epsilon(b);
var wp=_3.whitepoint(_45.whitepoint,_45.observer);
var uz=(4*wp.x)/(wp.x+15*wp.y+3*wp.z);
var vz=(9*wp.y)/(wp.x+15*wp.y+3*wp.z);
var Y=(Luv.L>_47*_48)?Math.pow((Luv.L+16)/116,3):Luv.L/_47;
var a=(1/3)*(((52*Luv.L)/(Luv.u+13*Luv.L*uz))-1);
var b=-5*Y,c=-(1/3),d=Y*(((39*Luv.L)/(Luv.v+13*Luv.L*vz))-5);
var X=(d-b)/(a-c),Z=X*a+b;
return {X:X,Y:Y,Z:Z};
},"LCHuv":function(Luv){
var L=Luv.L,C=Math.pow(Luv.u*Luv.u+Luv.v*Luv*v,0.5),H=Math.atan(Luv.v,Luv.u)*(180/Math.PI);
if(H<0){
H+=360;
}
if(H>360){
H-=360;
}
return {L:L,C:C,H:H};
}},"LCHuv":{"Luv":function(LCH){
var _57=LCH.H*(Math.PI/180);
var L=LCH.L,u=LCH.C/Math.pow(Math.pow(Math.tan(_57),2)+1,0.5);
var v=Math.pow(LCH.C*LCH.C-u*u,0.5);
if(90<LCH.H&&LCH.H>270){
u*=-1;
}
if(LCH.H>180){
v*=-1;
}
return {L:L,u:u,v:v};
}}};
var _5b={"CMY":{"CMYK":function(obj,_5d){
return _1.fromCmy(obj).toCmyk();
},"HSL":function(obj,_5f){
return _1.fromCmy(obj).toHsl();
},"HSV":function(obj,_61){
return _1.fromCmy(obj).toHsv();
},"Lab":function(obj,_63){
return _7["XYZ"]["Lab"](_1.fromCmy(obj).toXYZ(_63));
},"LCHab":function(obj,_65){
return _7["Lab"]["LCHab"](_5b["CMY"]["Lab"](obj));
},"LCHuv":function(obj,_67){
return _7["LCHuv"]["Luv"](_7["Luv"]["XYZ"](_1.fromCmy(obj).toXYZ(_67)));
},"Luv":function(obj,_69){
return _7["Luv"]["XYZ"](_1.fromCmy(obj).toXYZ(_69));
},"RGB":function(obj,_6b){
return _1.fromCmy(obj);
},"XYZ":function(obj,_6d){
return _1.fromCmy(obj).toXYZ(_6d);
},"xyY":function(obj,_6f){
return _7["XYZ"]["xyY"](_1.fromCmy(obj).toXYZ(_6f));
}},"CMYK":{"CMY":function(obj,_71){
return _1.fromCmyk(obj).toCmy();
},"HSL":function(obj,_73){
return _1.fromCmyk(obj).toHsl();
},"HSV":function(obj,_75){
return _1.fromCmyk(obj).toHsv();
},"Lab":function(obj,_77){
return _7["XYZ"]["Lab"](_1.fromCmyk(obj).toXYZ(_77));
},"LCHab":function(obj,_79){
return _7["Lab"]["LCHab"](_5b["CMYK"]["Lab"](obj));
},"LCHuv":function(obj,_7b){
return _7["LCHuv"]["Luv"](_7["Luv"]["XYZ"](_1.fromCmyk(obj).toXYZ(_7b)));
},"Luv":function(obj,_7d){
return _7["Luv"]["XYZ"](_1.fromCmyk(obj).toXYZ(_7d));
},"RGB":function(obj,_7f){
return _1.fromCmyk(obj);
},"XYZ":function(obj,_81){
return _1.fromCmyk(obj).toXYZ(_81);
},"xyY":function(obj,_83){
return _7["XYZ"]["xyY"](_1.fromCmyk(obj).toXYZ(_83));
}},"HSL":{"CMY":function(obj,_85){
return _1.fromHsl(obj).toCmy();
},"CMYK":function(obj,_87){
return _1.fromHsl(obj).toCmyk();
},"HSV":function(obj,_89){
return _1.fromHsl(obj).toHsv();
},"Lab":function(obj,_8b){
return _7["XYZ"]["Lab"](_1.fromHsl(obj).toXYZ(_8b));
},"LCHab":function(obj,_8d){
return _7["Lab"]["LCHab"](_5b["CMYK"]["Lab"](obj));
},"LCHuv":function(obj,_8f){
return _7["LCHuv"]["Luv"](_7["Luv"]["XYZ"](_1.fromHsl(obj).toXYZ(_8f)));
},"Luv":function(obj,_91){
return _7["Luv"]["XYZ"](_1.fromHsl(obj).toXYZ(_91));
},"RGB":function(obj,_93){
return _1.fromHsl(obj);
},"XYZ":function(obj,_95){
return _1.fromHsl(obj).toXYZ(_95);
},"xyY":function(obj,_97){
return _7["XYZ"]["xyY"](_1.fromHsl(obj).toXYZ(_97));
}},"HSV":{"CMY":function(obj,_99){
return _1.fromHsv(obj).toCmy();
},"CMYK":function(obj,_9b){
return _1.fromHsv(obj).toCmyk();
},"HSL":function(obj,_9d){
return _1.fromHsv(obj).toHsl();
},"Lab":function(obj,_9f){
return _7["XYZ"]["Lab"](_1.fromHsv(obj).toXYZ(_9f));
},"LCHab":function(obj,_a1){
return _7["Lab"]["LCHab"](_5b["CMYK"]["Lab"](obj));
},"LCHuv":function(obj,_a3){
return _7["LCHuv"]["Luv"](_7["Luv"]["XYZ"](_1.fromHsv(obj).toXYZ(_a3)));
},"Luv":function(obj,_a5){
return _7["Luv"]["XYZ"](_1.fromHsv(obj).toXYZ(_a5));
},"RGB":function(obj,_a7){
return _1.fromHsv(obj);
},"XYZ":function(obj,_a9){
return _1.fromHsv(obj).toXYZ(_a9);
},"xyY":function(obj,_ab){
return _7["XYZ"]["xyY"](_1.fromHsv(obj).toXYZ(_ab));
}},"Lab":{"CMY":function(obj,_ad){
return _1.fromXYZ(_7["Lab"]["XYZ"](obj,_ad)).toCmy();
},"CMYK":function(obj,_af){
return _1.fromXYZ(_7["Lab"]["XYZ"](obj,_af)).toCmyk();
},"HSL":function(obj,_b1){
return _1.fromXYZ(_7["Lab"]["XYZ"](obj,_b1)).toHsl();
},"HSV":function(obj,_b3){
return _1.fromXYZ(_7["Lab"]["XYZ"](obj,_b3)).toHsv();
},"LCHab":function(obj,_b5){
return _7["Lab"]["LCHab"](obj,_b5);
},"LCHuv":function(obj,_b7){
return _7["Luv"]["LCHuv"](_7["Lab"]["XYZ"](obj,_b7),_b7);
},"Luv":function(obj,_b9){
return _7["XYZ"]["Luv"](_7["Lab"]["XYZ"](obj,_b9),_b9);
},"RGB":function(obj,_bb){
return _1.fromXYZ(_7["Lab"]["XYZ"](obj,_bb));
},"XYZ":function(obj,_bd){
return _7["Lab"]["XYZ"](obj,_bd);
},"xyY":function(obj,_bf){
return _7["XYZ"]["xyY"](_7["Lab"]["XYZ"](obj,_bf),_bf);
}},"LCHab":{"CMY":function(obj,_c1){
return _1.fromXYZ(_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_c1),_c1).toCmy();
},"CMYK":function(obj,_c3){
return _1.fromXYZ(_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_c3),_c3).toCmyk();
},"HSL":function(obj,_c5){
return _1.fromXYZ(_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_c5),_c5).toHsl();
},"HSV":function(obj,_c7){
return _1.fromXYZ(_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_c7),_c7).toHsv();
},"Lab":function(obj,_c9){
return _7["Lab"]["LCHab"](obj,_c9);
},"LCHuv":function(obj,_cb){
return _7["Luv"]["LCHuv"](_7["XYZ"]["Luv"](_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_cb),_cb),_cb);
},"Luv":function(obj,_cd){
return _7["XYZ"]["Luv"](_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_cd),_cd);
},"RGB":function(obj,_cf){
return _1.fromXYZ(_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_cf),_cf);
},"XYZ":function(obj,_d1){
return _7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj,_d1),_d1);
},"xyY":function(obj,_d3){
return _7["XYZ"]["xyY"](_7["Lab"]["XYZ"](_7["LCHab"]["Lab"](obj),_d3),_d3);
}},"LCHuv":{"CMY":function(obj,_d5){
return _1.fromXYZ(_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_d5),_d5).toCmy();
},"CMYK":function(obj,_d7){
return _1.fromXYZ(_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_d7),_d7).toCmyk();
},"HSL":function(obj,_d9){
return _1.fromXYZ(_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_d9),_d9).toHsl();
},"HSV":function(obj,_db){
return _1.fromXYZ(_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_db),_db).toHsv();
},"Lab":function(obj,_dd){
return _7["XYZ"]["Lab"](_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_dd),_dd);
},"LCHab":function(obj,_df){
return _7["Lab"]["LCHab"](_7["XYZ"]["Lab"](_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_df),_df),_df);
},"Luv":function(obj,_e1){
return _7["LCHuv"]["Luv"](obj,_e1);
},"RGB":function(obj,_e3){
return _1.fromXYZ(_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_e3),_e3);
},"XYZ":function(obj,_e5){
return _7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_e5);
},"xyY":function(obj,_e7){
return _7["XYZ"]["xyY"](_7["Luv"]["XYZ"](_7["LCHuv"]["Luv"](obj),_e7),_e7);
}},"Luv":{"CMY":function(obj,_e9){
return _1.fromXYZ(_7["Luv"]["XYZ"](obj,_e9),_e9).toCmy();
},"CMYK":function(obj,_eb){
return _1.fromXYZ(_7["Luv"]["XYZ"](obj,_eb),_eb).toCmyk();
},"HSL":function(obj,_ed){
return _1.fromXYZ(_7["Luv"]["XYZ"](obj,_ed),_ed).toHsl();
},"HSV":function(obj,_ef){
return _1.fromXYZ(_7["Luv"]["XYZ"](obj,_ef),_ef).toHsv();
},"Lab":function(obj,_f1){
return _7["XYZ"]["Lab"](_7["Luv"]["XYZ"](obj,_f1),_f1);
},"LCHab":function(obj,_f3){
return _7["Lab"]["LCHab"](_7["XYZ"]["Lab"](_7["Luv"]["XYZ"](obj,_f3),_f3),_f3);
},"LCHuv":function(obj,_f5){
return _7["Luv"]["LCHuv"](obj,_f5);
},"RGB":function(obj,_f7){
return _1.fromXYZ(_7["Luv"]["XYZ"](obj,_f7),_f7);
},"XYZ":function(obj,_f9){
return _7["Luv"]["XYZ"](obj,_f9);
},"xyY":function(obj,_fb){
return _7["XYZ"]["xyY"](_7["Luv"]["XYZ"](obj,_fb),_fb);
}},"RGB":{"CMY":function(obj,_fd){
return obj.toCmy();
},"CMYK":function(obj,_ff){
return obj.toCmyk();
},"HSL":function(obj,_101){
return obj.toHsl();
},"HSV":function(obj,_103){
return obj.toHsv();
},"Lab":function(obj,_105){
return _7["XYZ"]["Lab"](obj.toXYZ(_105),_105);
},"LCHab":function(obj,_107){
return _7["LCHab"]["Lab"](_7["XYZ"]["Lab"](obj.toXYZ(_107),_107),_107);
},"LCHuv":function(obj,_109){
return _7["LCHuv"]["Luv"](_7["XYZ"]["Luv"](obj.toXYZ(_109),_109),_109);
},"Luv":function(obj,_10b){
return _7["XYZ"]["Luv"](obj.toXYZ(_10b),_10b);
},"XYZ":function(obj,_10d){
return obj.toXYZ(_10d);
},"xyY":function(obj,_10f){
return _7["XYZ"]["xyY"](obj.toXYZ(_10f),_10f);
}},"XYZ":{"CMY":function(obj,_111){
return _1.fromXYZ(obj,_111).toCmy();
},"CMYK":function(obj,_113){
return _1.fromXYZ(obj,_113).toCmyk();
},"HSL":function(obj,_115){
return _1.fromXYZ(obj,_115).toHsl();
},"HSV":function(obj,_117){
return _1.fromXYZ(obj,_117).toHsv();
},"Lab":function(obj,_119){
return _7["XYZ"]["Lab"](obj,_119);
},"LCHab":function(obj,_11b){
return _7["Lab"]["LCHab"](_7["XYZ"]["Lab"](obj,_11b),_11b);
},"LCHuv":function(obj,_11d){
return _7["Luv"]["LCHuv"](_7["XYZ"]["Luv"](obj,_11d),_11d);
},"Luv":function(obj,_11f){
return _7["XYZ"]["Luv"](obj,_11f);
},"RGB":function(obj,_121){
return _1.fromXYZ(obj,_121);
},"xyY":function(obj,_123){
return _7["XYZ"]["xyY"](_1.fromXYZ(obj,_123),_123);
}},"xyY":{"CMY":function(obj,_125){
return _1.fromXYZ(_7["xyY"]["XYZ"](obj,_125),_125).toCmy();
},"CMYK":function(obj,_127){
return _1.fromXYZ(_7["xyY"]["XYZ"](obj,_127),_127).toCmyk();
},"HSL":function(obj,_129){
return _1.fromXYZ(_7["xyY"]["XYZ"](obj,_129),_129).toHsl();
},"HSV":function(obj,_12b){
return _1.fromXYZ(_7["xyY"]["XYZ"](obj,_12b),_12b).toHsv();
},"Lab":function(obj,_12d){
return _7["Lab"]["XYZ"](_7["xyY"]["XYZ"](obj,_12d),_12d);
},"LCHab":function(obj,_12f){
return _7["LCHab"]["Lab"](_7["Lab"]["XYZ"](_7["xyY"]["XYZ"](obj,_12f),_12f),_12f);
},"LCHuv":function(obj,_131){
return _7["LCHuv"]["Luv"](_7["Luv"]["XYZ"](_7["xyY"]["XYZ"](obj,_131),_131),_131);
},"Luv":function(obj,_133){
return _7["Luv"]["XYZ"](_7["xyY"]["XYZ"](obj,_133),_133);
},"RGB":function(obj,_135){
return _1.fromXYZ(_7["xyY"]["XYZ"](obj,_135),_135);
},"XYZ":function(obj,_137){
return _7["xyY"]["XYZ"](obj,_137);
}}};
this.whitepoint=function(_138,_139){
_139=_139||"10";
var x=0,y=0,t=0;
if(_4[_139]&&_4[_139][_138]){
x=_4[_139][_138].x;
y=_4[_139][_138].y;
t=_4[_139][_138].t;
}else{
console.warn("dojox.color.Colorspace::whitepoint: either the observer or the whitepoint name was not found. ",_139,_138);
}
var wp={x:x,y:y,z:(1-x-y),t:t,Y:1};
return this.convert(wp,"xyY","XYZ");
};
this.tempToWhitepoint=function(t){
if(t<4000){
console.warn("dojox.color.Colorspace::tempToWhitepoint: can't find a white point for temperatures less than 4000K. (Passed ",t,").");
return {x:0,y:0};
}
if(t>25000){
console.warn("dojox.color.Colorspace::tempToWhitepoint: can't find a white point for temperatures greater than 25000K. (Passed ",t,").");
return {x:0,y:0};
}
var t1=t,t2=t*t,t3=t2*t;
var ten9=Math.pow(10,9),ten6=Math.pow(10,6),ten3=Math.pow(10,3);
if(t<=7000){
var x=(-4.607*ten9/t3)+(2.9678*ten6/t2)+(0.09911*ten3/t)+0.2444063;
}else{
var x=(-2.0064*ten9/t3)+(1.9018*ten6/t2)+(0.24748*ten3/t)+0.23704;
}
var y=-3*x*x+2.87*x-0.275;
return {x:x,y:y};
};
this.primaries=function(_147){
_147=dojo.mixin({profile:"sRGB",whitepoint:"D65",observer:"10",adaptor:"Bradford"},_147||{});
var m=[];
if(_5[_147.profile]){
m=_5[_147.profile].slice(0);
}else{
console.warn("dojox.color.Colorspace::primaries: the passed profile was not found.  ","Available profiles include: ",_5,".  The profile passed was ",_147.profile);
}
var _149={name:_147.profile,gamma:m[0],whitepoint:m[1],xr:m[2],yr:m[3],Yr:m[4],xg:m[5],yg:m[6],Yg:m[7],xb:m[8],yb:m[9],Yb:m[10]};
if(_147.whitepoint!=_149.whitepoint){
var r=this.convert(this.adapt({color:this.convert({x:xr,y:yr,Y:Yr},"xyY","XYZ"),adaptor:_147.adaptor,source:_149.whitepoint,destination:_147.whitepoint}),"XYZ","xyY");
var g=this.convert(this.adapt({color:this.convert({x:xg,y:yg,Y:Yg},"xyY","XYZ"),adaptor:_147.adaptor,source:_149.whitepoint,destination:_147.whitepoint}),"XYZ","xyY");
var b=this.convert(this.adapt({color:this.convert({x:xb,y:yb,Y:Yb},"xyY","XYZ"),adaptor:_147.adaptor,source:_149.whitepoint,destination:_147.whitepoint}),"XYZ","xyY");
_149=dojo.mixin(_149,{xr:r.x,yr:r.y,Yr:r.Y,xg:g.x,yg:g.y,Yg:g.Y,xb:b.x,yb:b.y,Yb:b.Y,whitepoint:_147.whitepoint});
}
return dojo.mixin(_149,{zr:1-_149.xr-_149.yr,zg:1-_149.xg-_149.yg,zb:1-_149.xb-_149.yb});
};
this.adapt=function(_14d){
if(!_14d.color||!_14d.source){
console.error("dojox.color.Colorspace::adapt: color and source arguments are required. ",_14d);
}
_14d=dojo.mixin({adaptor:"Bradford",destination:"D65"},_14d);
var swp=this.whitepoint(_14d.source);
var dwp=this.whitepoint(_14d.destination);
if(_6[_14d.adaptor]){
var ma=_6[_14d.adaptor].ma;
var mai=_6[_14d.adaptor].mai;
}else{
console.warn("dojox.color.Colorspace::adapt: the passed adaptor '",_14d.adaptor,"' was not found.");
}
var dSrc=_2.multiply([[swp.x,swp.y,swp.z]],ma);
var _153=_2.multiply([[dwp.x,dwp.y,dwp.z]],ma);
var _154=[[_153[0][0]/dSrc[0][0],0,0],[0,_153[0][1]/dSrc[0][1],0],[0,0,_153[0][2]/dSrc[0][2]]];
var m=_2.multiply(_2.multiply(ma,_154),mai);
var r=_2.multiply([[_14d.color.X,_14d.color.Y,_14d.color.Z]],m)[0];
return {X:r[0],Y:r[1],Z:r[2]};
};
this.matrix=function(to,_158){
var wp=this.whitepoint(_158.whitepoint);
var Xr=p.xr/p.yr,Yr=1,Zr=(1-p.xr-p.yr)/p.yr;
var Xg=p.xg/p.yg,Yg=1,Zg=(1-p.xg-p.yg)/p.yg;
var Xb=p.xb/p.yb,Yb=1,Zr=(1-p.xb-p.yb)/p.yb;
var m1=[[Xr,Yr,Zr],[Xg,Yg,Zg],[Xb,Yb,Zb]];
var m2=[[wp.X,wp.Y,wp.Z]];
var sm=dojox.math.matrix.multiply(m2,dojox.math.matrix.inverse(m1));
var Sr=sm[0][0],Sg=sm[0][1],Sb=sm[0][2];
var _168=[[Sr*Xr,Sr*Yr,Sr*Zr],[Sg*Xg,Sg*Yg,Sg*Zg],[Sb*Xb,Sb*Yb,Sb*Zb]];
if(to=="RGB"){
return dojox.math.inverse(_168);
}
return _168;
};
this.epsilon=function(_169){
return (_169||typeof (_169)=="undefined")?0.008856:216/24289;
};
this.kappa=function(_16a){
return (_16a||typeof (_16a)=="undefined")?903.3:24389/27;
};
this.convert=function(_16b,from,to,_16e){
if(_5b[from]&&_5b[from][to]){
return _5b[from][to](obj,_16e);
}
console.warn("dojox.color.Colorspace::convert: Can't convert ",_16b," from ",from," to ",to,".");
};
})();
dojo.mixin(dojox.color,{fromXYZ:function(xyz,_170){
_170=_170||{};
var p=dojox.color.Colorspace.primaries(_170);
var m=dojox.color.Colorspace.matrix("RGB",p);
var rgb=dojox.math.matrix.mutliply([[xyz.X,xyz.Y,xyz.Z]],m);
var r=rgb[0][0],g=rgb[0][1],b=rgb[0][2];
if(p.profile=="sRGB"){
var R=(r>0.0031308)?(1.055*Math.pow(r,1/2.4))-0.055:12.92*r;
var G=(g>0.0031308)?(1.055*Math.pow(g,1/2.4))-0.055:12.92*g;
var B=(b>0.0031308)?(1.055*Math.pow(b,1/2.4))-0.055:12.92*b;
}else{
var R=Math.pow(r,1/p.gamma),G=Math.pow(g,1/p.gamma),B=Math.pow(b,1/p.gamma);
}
return new dojox.color.Color({r:Math.floor(R*255),g:Math.floor(G*255),b:Math.floor(B*255)});
}});
dojo.extend(dojox.color.Color,{toXYZ:function(_17a){
_17a=_17a||{};
var p=dojox.color.Colorspace.primaries(_17a);
var m=dojox.color.Colorspace.matrix("XYZ",p);
var _r=this.r/255,_g=this.g/255,_b=this.b/255;
if(p.profile=="sRGB"){
var r=(_r>0.04045)?Math.pow(((_r+0.055)/1.055),2.4):_r/12.92;
var g=(_g>0.04045)?Math.pow(((_g+0.055)/1.055),2.4):_g/12.92;
var b=(_b>0.04045)?Math.pow(((_b+0.055)/1.055),2.4):_b/12.92;
}else{
var r=Math.pow(_r,p.gamma),g=Math.pow(_g,p.gamma),b=Math.pow(_b,p.gamma);
}
var xyz=dojox.math.matrix([[r,g,b]],m);
return {X:xyz[0][0],Y:xyz[0][1],Z:xyz[0][2]};
}});
}
