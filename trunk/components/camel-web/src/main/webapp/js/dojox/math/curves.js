/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.math.curves"]){
dojo._hasResource["dojox.math.curves"]=true;
dojo.provide("dojox.math.curves");
dojo.mixin(dojox.math.curves,{Line:function(_1,_2){
this.start=_1;
this.end=_2;
this.dimensions=_1.length;
for(var i=0;i<_1.length;i++){
_1[i]=Number(_1[i]);
}
for(var i=0;i<_2.length;i++){
_2[i]=Number(_2[i]);
}
this.getValue=function(n){
var _5=new Array(this.dimensions);
for(var i=0;i<this.dimensions;i++){
_5[i]=((this.end[i]-this.start[i])*n)+this.start[i];
}
return _5;
};
return this;
},Bezier:function(_7){
this.getValue=function(_8){
if(_8>=1){
return this.p[this.p.length-1];
}
if(_8<=0){
return this.p[0];
}
var _9=new Array(this.p[0].length);
for(var k=0;j<this.p[0].length;k++){
_9[k]=0;
}
for(var j=0;j<this.p[0].length;j++){
var C=0;
var D=0;
for(var i=0;i<this.p.length;i++){
C+=this.p[i][j]*this.p[this.p.length-1][0]*dojox.math.bernstein(_8,this.p.length,i);
}
for(var l=0;l<this.p.length;l++){
D+=this.p[this.p.length-1][0]*dojox.math.bernstein(_8,this.p.length,l);
}
_9[j]=C/D;
}
return _9;
};
this.p=_7;
return this;
},CatmullRom:function(_10,c){
this.getValue=function(_12){
var _13=_12*(this.p.length-1);
var _14=Math.floor(_13);
var _15=_13-_14;
var i0=_14-1;
if(i0<0){
i0=0;
}
var i=_14;
var i1=_14+1;
if(i1>=this.p.length){
i1=this.p.length-1;
}
var i2=_14+2;
if(i2>=this.p.length){
i2=this.p.length-1;
}
var u=_15;
var u2=_15*_15;
var u3=_15*_15*_15;
var _1d=new Array(this.p[0].length);
for(var k=0;k<this.p[0].length;k++){
var x1=(-this.c*this.p[i0][k])+((2-this.c)*this.p[i][k])+((this.c-2)*this.p[i1][k])+(this.c*this.p[i2][k]);
var x2=(2*this.c*this.p[i0][k])+((this.c-3)*this.p[i][k])+((3-2*this.c)*this.p[i1][k])+(-this.c*this.p[i2][k]);
var x3=(-this.c*this.p[i0][k])+(this.c*this.p[i1][k]);
var x4=this.p[i][k];
_1d[k]=x1*u3+x2*u2+x3*u+x4;
}
return _1d;
};
if(!c){
this.c=0.7;
}else{
this.c=c;
}
this.p=_10;
return this;
},Arc:function(_23,end,ccw){
function _26(a,b){
var c=new Array(a.length);
for(var i=0;i<a.length;i++){
c[i]=a[i]+b[i];
}
return c;
};
function _2b(a){
var b=new Array(a.length);
for(var i=0;i<a.length;i++){
b[i]=-a[i];
}
return b;
};
var _2f=dojox.math.midpoint(_23,end);
var _30=_26(_2b(_2f),_23);
var rad=Math.sqrt(Math.pow(_30[0],2)+Math.pow(_30[1],2));
var _32=dojox.math.radiansToDegrees(Math.atan(_30[1]/_30[0]));
if(_30[0]<0){
_32-=90;
}else{
_32+=90;
}
dojox.math.curves.CenteredArc.call(this,_2f,rad,_32,_32+(ccw?-180:180));
},CenteredArc:function(_33,_34,_35,end){
this.center=_33;
this.radius=_34;
this.start=_35||0;
this.end=end;
this.getValue=function(n){
var _38=new Array(2);
var _39=dojox.math.degreesToRadians(this.start+((this.end-this.start)*n));
_38[0]=this.center[0]+this.radius*Math.sin(_39);
_38[1]=this.center[1]-this.radius*Math.cos(_39);
return _38;
};
return this;
},Circle:function(_3a,_3b){
dojox.math.curves.CenteredArc.call(this,_3a,_3b,0,360);
return this;
},Path:function(){
var _3c=[];
var _3d=[];
var _3e=[];
var _3f=0;
this.add=function(_40,_41){
if(_41<0){
console.error("dojox.math.curves.Path.add: weight cannot be less than 0");
}
_3c.push(_40);
_3d.push(_41);
_3f+=_41;
_42();
};
this.remove=function(_43){
for(var i=0;i<_3c.length;i++){
if(_3c[i]==_43){
_3c.splice(i,1);
_3f-=_3d.splice(i,1)[0];
break;
}
}
_42();
};
this.removeAll=function(){
_3c=[];
_3d=[];
_3f=0;
};
this.getValue=function(n){
var _46=false,_47=0;
for(var i=0;i<_3e.length;i++){
var r=_3e[i];
if(n>=r[0]&&n<r[1]){
var _4a=(n-r[0])/r[2];
_47=_3c[i].getValue(_4a);
_46=true;
break;
}
}
if(!_46){
_47=_3c[_3c.length-1].getValue(1);
}
for(var j=0;j<i;j++){
_47=dojox.math.points.translate(_47,_3c[j].getValue(1));
}
return _47;
};
function _42(){
var _4c=0;
for(var i=0;i<_3d.length;i++){
var end=_4c+_3d[i]/_3f;
var len=end-_4c;
_3e[i]=[_4c,end,len];
_4c=end;
}
};
return this;
}});
}
