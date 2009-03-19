/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.decompose"]){
dojo._hasResource["dojox.gfx.decompose"]=true;
dojo.provide("dojox.gfx.decompose");
dojo.require("dojox.gfx.matrix");
(function(){
var m=dojox.gfx.matrix;
var eq=function(a,b){
return Math.abs(a-b)<=0.000001*(Math.abs(a)+Math.abs(b));
};
var _5=function(r1,m1,r2,m2){
if(!isFinite(r1)){
return r2;
}else{
if(!isFinite(r2)){
return r1;
}
}
m1=Math.abs(m1),m2=Math.abs(m2);
return (m1*r1+m2*r2)/(m1+m2);
};
var _a=function(_b){
var M=new m.Matrix2D(_b);
return dojo.mixin(M,{dx:0,dy:0,xy:M.yx,yx:M.xy});
};
var _d=function(_e){
return (_e.xx*_e.yy<0||_e.xy*_e.yx>0)?-1:1;
};
var _f=function(_10){
var M=m.normalize(_10),b=-M.xx-M.yy,c=M.xx*M.yy-M.xy*M.yx,d=Math.sqrt(b*b-4*c),l1=-(b+(b<0?-d:d))/2,l2=c/l1,vx1=M.xy/(l1-M.xx),vy1=1,vx2=M.xy/(l2-M.xx),vy2=1;
if(eq(l1,l2)){
vx1=1,vy1=0,vx2=0,vy2=1;
}
if(!isFinite(vx1)){
vx1=1,vy1=(l1-M.xx)/M.xy;
if(!isFinite(vy1)){
vx1=(l1-M.yy)/M.yx,vy1=1;
if(!isFinite(vx1)){
vx1=1,vy1=M.yx/(l1-M.yy);
}
}
}
if(!isFinite(vx2)){
vx2=1,vy2=(l2-M.xx)/M.xy;
if(!isFinite(vy2)){
vx2=(l2-M.yy)/M.yx,vy2=1;
if(!isFinite(vx2)){
vx2=1,vy2=M.yx/(l2-M.yy);
}
}
}
var d1=Math.sqrt(vx1*vx1+vy1*vy1),d2=Math.sqrt(vx2*vx2+vy2*vy2);
if(!isFinite(vx1/=d1)){
vx1=0;
}
if(!isFinite(vy1/=d1)){
vy1=0;
}
if(!isFinite(vx2/=d2)){
vx2=0;
}
if(!isFinite(vy2/=d2)){
vy2=0;
}
return {value1:l1,value2:l2,vector1:{x:vx1,y:vy1},vector2:{x:vx2,y:vy2}};
};
var _1d=function(M,_1f){
var _20=_d(M),a=_1f.angle1=(Math.atan2(M.yx,M.yy)+Math.atan2(-_20*M.xy,_20*M.xx))/2,cos=Math.cos(a),sin=Math.sin(a);
_1f.sx=_5(M.xx/cos,cos,-M.xy/sin,sin);
_1f.sy=_5(M.yy/cos,cos,M.yx/sin,sin);
return _1f;
};
var _24=function(M,_26){
var _27=_d(M),a=_26.angle2=(Math.atan2(_27*M.yx,_27*M.xx)+Math.atan2(-M.xy,M.yy))/2,cos=Math.cos(a),sin=Math.sin(a);
_26.sx=_5(M.xx/cos,cos,M.yx/sin,sin);
_26.sy=_5(M.yy/cos,cos,-M.xy/sin,sin);
return _26;
};
dojox.gfx.decompose=function(_2b){
var M=m.normalize(_2b),_2d={dx:M.dx,dy:M.dy,sx:1,sy:1,angle1:0,angle2:0};
if(eq(M.xy,0)&&eq(M.yx,0)){
return dojo.mixin(_2d,{sx:M.xx,sy:M.yy});
}
if(eq(M.xx*M.yx,-M.xy*M.yy)){
return _1d(M,_2d);
}
if(eq(M.xx*M.xy,-M.yx*M.yy)){
return _24(M,_2d);
}
var MT=_a(M),u=_f([M,MT]),v=_f([MT,M]),U=new m.Matrix2D({xx:u.vector1.x,xy:u.vector2.x,yx:u.vector1.y,yy:u.vector2.y}),VT=new m.Matrix2D({xx:v.vector1.x,xy:v.vector1.y,yx:v.vector2.x,yy:v.vector2.y}),S=new m.Matrix2D([m.invert(U),M,m.invert(VT)]);
_1d(VT,_2d);
S.xx*=_2d.sx;
S.yy*=_2d.sy;
_24(U,_2d);
S.xx*=_2d.sx;
S.yy*=_2d.sy;
return dojo.mixin(_2d,{sx:S.xx,sy:S.yy});
};
})();
}
