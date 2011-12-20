/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx3d.matrix"]){
dojo._hasResource["dojox.gfx3d.matrix"]=true;
dojo.provide("dojox.gfx3d.matrix");
dojox.gfx3d.matrix._degToRad=function(_1){
return Math.PI*_1/180;
};
dojox.gfx3d.matrix._radToDeg=function(_2){
return _2/Math.PI*180;
};
dojox.gfx3d.matrix.Matrix3D=function(_3){
if(_3){
if(typeof _3=="number"){
this.xx=this.yy=this.zz=_3;
}else{
if(_3 instanceof Array){
if(_3.length>0){
var m=dojox.gfx3d.matrix.normalize(_3[0]);
for(var i=1;i<_3.length;++i){
var l=m;
var r=dojox.gfx3d.matrix.normalize(_3[i]);
m=new dojox.gfx3d.matrix.Matrix3D();
m.xx=l.xx*r.xx+l.xy*r.yx+l.xz*r.zx;
m.xy=l.xx*r.xy+l.xy*r.yy+l.xz*r.zy;
m.xz=l.xx*r.xz+l.xy*r.yz+l.xz*r.zz;
m.yx=l.yx*r.xx+l.yy*r.yx+l.yz*r.zx;
m.yy=l.yx*r.xy+l.yy*r.yy+l.yz*r.zy;
m.yz=l.yx*r.xz+l.yy*r.yz+l.yz*r.zz;
m.zx=l.zx*r.xx+l.zy*r.yx+l.zz*r.zx;
m.zy=l.zx*r.xy+l.zy*r.yy+l.zz*r.zy;
m.zz=l.zx*r.xz+l.zy*r.yz+l.zz*r.zz;
m.dx=l.xx*r.dx+l.xy*r.dy+l.xz*r.dz+l.dx;
m.dy=l.yx*r.dx+l.yy*r.dy+l.yz*r.dz+l.dy;
m.dz=l.zx*r.dx+l.zy*r.dy+l.zz*r.dz+l.dz;
}
dojo.mixin(this,m);
}
}else{
dojo.mixin(this,_3);
}
}
}
};
dojo.extend(dojox.gfx3d.matrix.Matrix3D,{xx:1,xy:0,xz:0,yx:0,yy:1,yz:0,zx:0,zy:0,zz:1,dx:0,dy:0,dz:0});
dojo.mixin(dojox.gfx3d.matrix,{identity:new dojox.gfx3d.matrix.Matrix3D(),translate:function(a,b,c){
if(arguments.length>1){
return new dojox.gfx3d.matrix.Matrix3D({dx:a,dy:b,dz:c});
}
return new dojox.gfx3d.matrix.Matrix3D({dx:a.x,dy:a.y,dz:a.z});
},scale:function(a,b,c){
if(arguments.length>1){
return new dojox.gfx3d.matrix.Matrix3D({xx:a,yy:b,zz:c});
}
if(typeof a=="number"){
return new dojox.gfx3d.matrix.Matrix3D({xx:a,yy:a,zz:a});
}
return new dojox.gfx3d.matrix.Matrix3D({xx:a.x,yy:a.y,zz:a.z});
},rotateX:function(_e){
var c=Math.cos(_e);
var s=Math.sin(_e);
return new dojox.gfx3d.matrix.Matrix3D({yy:c,yz:-s,zy:s,zz:c});
},rotateXg:function(_11){
return dojox.gfx3d.matrix.rotateX(dojox.gfx3d.matrix._degToRad(_11));
},rotateY:function(_12){
var c=Math.cos(_12);
var s=Math.sin(_12);
return new dojox.gfx3d.matrix.Matrix3D({xx:c,xz:s,zx:-s,zz:c});
},rotateYg:function(_15){
return dojox.gfx3d.matrix.rotateY(dojox.gfx3d.matrix._degToRad(_15));
},rotateZ:function(_16){
var c=Math.cos(_16);
var s=Math.sin(_16);
return new dojox.gfx3d.matrix.Matrix3D({xx:c,xy:-s,yx:s,yy:c});
},rotateZg:function(_19){
return dojox.gfx3d.matrix.rotateZ(dojox.gfx3d.matrix._degToRad(_19));
},cameraTranslate:function(a,b,c){
if(arguments.length>1){
return new dojox.gfx3d.matrix.Matrix3D({dx:-a,dy:-b,dz:-c});
}
return new dojox.gfx3d.matrix.Matrix3D({dx:-a.x,dy:-a.y,dz:-a.z});
},cameraRotateX:function(_1d){
var c=Math.cos(-_1d);
var s=Math.sin(-_1d);
return new dojox.gfx3d.matrix.Matrix3D({yy:c,yz:-s,zy:s,zz:c});
},cameraRotateXg:function(_20){
return dojox.gfx3d.matrix.rotateX(dojox.gfx3d.matrix._degToRad(_20));
},cameraRotateY:function(_21){
var c=Math.cos(-_21);
var s=Math.sin(-_21);
return new dojox.gfx3d.matrix.Matrix3D({xx:c,xz:s,zx:-s,zz:c});
},cameraRotateYg:function(_24){
return dojox.gfx3d.matrix.rotateY(dojox.gfx3d.matrix._degToRad(_24));
},cameraRotateZ:function(_25){
var c=Math.cos(-_25);
var s=Math.sin(-_25);
return new dojox.gfx3d.matrix.Matrix3D({xx:c,xy:-s,yx:s,yy:c});
},cameraRotateZg:function(_28){
return dojox.gfx3d.matrix.rotateZ(dojox.gfx3d.matrix._degToRad(_28));
},normalize:function(_29){
return (_29 instanceof dojox.gfx3d.matrix.Matrix3D)?_29:new dojox.gfx3d.matrix.Matrix3D(_29);
},clone:function(_2a){
var obj=new dojox.gfx3d.matrix.Matrix3D();
for(var i in _2a){
if(typeof (_2a[i])=="number"&&typeof (obj[i])=="number"&&obj[i]!=_2a[i]){
obj[i]=_2a[i];
}
}
return obj;
},invert:function(_2d){
var m=dojox.gfx3d.matrix.normalize(_2d);
var D=m.xx*m.yy*m.zz+m.xy*m.yz*m.zx+m.xz*m.yx*m.zy-m.xx*m.yz*m.zy-m.xy*m.yx*m.zz-m.xz*m.yy*m.zx;
var M=new dojox.gfx3d.matrix.Matrix3D({xx:(m.yy*m.zz-m.yz*m.zy)/D,xy:(m.xz*m.zy-m.xy*m.zz)/D,xz:(m.xy*m.yz-m.xz*m.yy)/D,yx:(m.yz*m.zx-m.yx*m.zz)/D,yy:(m.xx*m.zz-m.xz*m.zx)/D,yz:(m.xz*m.yx-m.xx*m.yz)/D,zx:(m.yx*m.zy-m.yy*m.zx)/D,zy:(m.xy*m.zx-m.xx*m.zy)/D,zz:(m.xx*m.yy-m.xy*m.yx)/D,dx:-1*(m.xy*m.yz*m.dz+m.xz*m.dy*m.zy+m.dx*m.yy*m.zz-m.xy*m.dy*m.zz-m.xz*m.yy*m.dz-m.dx*m.yz*m.zy)/D,dy:(m.xx*m.yz*m.dz+m.xz*m.dy*m.zx+m.dx*m.yx*m.zz-m.xx*m.dy*m.zz-m.xz*m.yx*m.dz-m.dx*m.yz*m.zx)/D,dz:-1*(m.xx*m.yy*m.dz+m.xy*m.dy*m.zx+m.dx*m.yx*m.zy-m.xx*m.dy*m.zy-m.xy*m.yx*m.dz-m.dx*m.yy*m.zx)/D});
return M;
},_multiplyPoint:function(m,x,y,z){
return {x:m.xx*x+m.xy*y+m.xz*z+m.dx,y:m.yx*x+m.yy*y+m.yz*z+m.dy,z:m.zx*x+m.zy*y+m.zz*z+m.dz};
},multiplyPoint:function(_35,a,b,c){
var m=dojox.gfx3d.matrix.normalize(_35);
if(typeof a=="number"&&typeof b=="number"&&typeof c=="number"){
return dojox.gfx3d.matrix._multiplyPoint(m,a,b,c);
}
return dojox.gfx3d.matrix._multiplyPoint(m,a.x,a.y,a.z);
},multiply:function(_3a){
var m=dojox.gfx3d.matrix.normalize(_3a);
for(var i=1;i<arguments.length;++i){
var l=m;
var r=dojox.gfx3d.matrix.normalize(arguments[i]);
m=new dojox.gfx3d.matrix.Matrix3D();
m.xx=l.xx*r.xx+l.xy*r.yx+l.xz*r.zx;
m.xy=l.xx*r.xy+l.xy*r.yy+l.xz*r.zy;
m.xz=l.xx*r.xz+l.xy*r.yz+l.xz*r.zz;
m.yx=l.yx*r.xx+l.yy*r.yx+l.yz*r.zx;
m.yy=l.yx*r.xy+l.yy*r.yy+l.yz*r.zy;
m.yz=l.yx*r.xz+l.yy*r.yz+l.yz*r.zz;
m.zx=l.zx*r.xx+l.zy*r.yx+l.zz*r.zx;
m.zy=l.zx*r.xy+l.zy*r.yy+l.zz*r.zy;
m.zz=l.zx*r.xz+l.zy*r.yz+l.zz*r.zz;
m.dx=l.xx*r.dx+l.xy*r.dy+l.xz*r.dz+l.dx;
m.dy=l.yx*r.dx+l.yy*r.dy+l.yz*r.dz+l.dy;
m.dz=l.zx*r.dx+l.zy*r.dy+l.zz*r.dz+l.dz;
}
return m;
},_project:function(m,x,y,z){
return {x:m.xx*x+m.xy*y+m.xz*z+m.dx,y:m.yx*x+m.yy*y+m.yz*z+m.dy,z:m.zx*x+m.zy*y+m.zz*z+m.dz};
},project:function(_43,a,b,c){
var m=dojox.gfx3d.matrix.normalize(_43);
if(typeof a=="number"&&typeof b=="number"&&typeof c=="number"){
return dojox.gfx3d.matrix._project(m,a,b,c);
}
return dojox.gfx3d.matrix._project(m,a.x,a.y,a.z);
}});
dojox.gfx3d.Matrix3D=dojox.gfx3d.matrix.Matrix3D;
}
