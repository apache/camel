/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.arc"]){
dojo._hasResource["dojox.gfx.arc"]=true;
dojo.provide("dojox.gfx.arc");
dojo.require("dojox.gfx.matrix");
(function(){
var m=dojox.gfx.matrix,_2=function(_3){
var _4=Math.cos(_3),_5=Math.sin(_3),p2={x:_4+(4/3)*(1-_4),y:_5-(4/3)*_4*(1-_4)/_5};
return {s:{x:_4,y:-_5},c1:{x:p2.x,y:-p2.y},c2:p2,e:{x:_4,y:_5}};
},_7=2*Math.PI,_8=Math.PI/4,_9=Math.PI/8,_a=_8+_9,_b=_2(_9);
dojo.mixin(dojox.gfx.arc,{unitArcAsBezier:_2,curvePI4:_b,arcAsBezier:function(_c,rx,ry,_f,_10,_11,x,y){
_10=Boolean(_10);
_11=Boolean(_11);
var _14=m._degToRad(_f),rx2=rx*rx,ry2=ry*ry,pa=m.multiplyPoint(m.rotate(-_14),{x:(_c.x-x)/2,y:(_c.y-y)/2}),_18=pa.x*pa.x,_19=pa.y*pa.y,c1=Math.sqrt((rx2*ry2-rx2*_19-ry2*_18)/(rx2*_19+ry2*_18));
if(isNaN(c1)){
c1=0;
}
var ca={x:c1*rx*pa.y/ry,y:-c1*ry*pa.x/rx};
if(_10==_11){
ca={x:-ca.x,y:-ca.y};
}
var c=m.multiplyPoint([m.translate((_c.x+x)/2,(_c.y+y)/2),m.rotate(_14)],ca);
var _1d=m.normalize([m.translate(c.x,c.y),m.rotate(_14),m.scale(rx,ry)]);
var _1e=m.invert(_1d),sp=m.multiplyPoint(_1e,_c),ep=m.multiplyPoint(_1e,x,y),_21=Math.atan2(sp.y,sp.x),_22=Math.atan2(ep.y,ep.x),_23=_21-_22;
if(_11){
_23=-_23;
}
if(_23<0){
_23+=_7;
}else{
if(_23>_7){
_23-=_7;
}
}
var _24=_9,_25=_b,_26=_11?_24:-_24,_27=[];
for(var _28=_23;_28>0;_28-=_8){
if(_28<_a){
_24=_28/2;
_25=_2(_24);
_26=_11?_24:-_24;
_28=0;
}
var c1,c2,e,M=m.normalize([_1d,m.rotate(_21+_26)]);
if(_11){
c1=m.multiplyPoint(M,_25.c1);
c2=m.multiplyPoint(M,_25.c2);
e=m.multiplyPoint(M,_25.e);
}else{
c1=m.multiplyPoint(M,_25.c2);
c2=m.multiplyPoint(M,_25.c1);
e=m.multiplyPoint(M,_25.s);
}
_27.push([c1.x,c1.y,c2.x,c2.y,e.x,e.y]);
_21+=2*_26;
}
return _27;
}});
})();
}
