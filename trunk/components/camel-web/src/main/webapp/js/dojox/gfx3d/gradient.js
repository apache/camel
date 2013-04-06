/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx3d.gradient"]){
dojo._hasResource["dojox.gfx3d.gradient"]=true;
dojo.provide("dojox.gfx3d.gradient");
dojo.require("dojox.gfx3d.vector");
dojo.require("dojox.gfx3d.matrix");
(function(){
var _1=function(a,b){
return Math.sqrt(Math.pow(b.x-a.x,2)+Math.pow(b.y-a.y,2));
};
var N=32;
dojox.gfx3d.gradient=function(_5,_6,_7,_8,_9,to,_b){
var m=dojox.gfx3d.matrix,v=dojox.gfx3d.vector,mx=m.normalize(_b),f=m.multiplyPoint(mx,_8*Math.cos(_9)+_7.x,_8*Math.sin(_9)+_7.y,_7.z),t=m.multiplyPoint(mx,_8*Math.cos(to)+_7.x,_8*Math.sin(to)+_7.y,_7.z),c=m.multiplyPoint(mx,_7.x,_7.y,_7.z),_12=(to-_9)/N,r=_1(f,t)/2,mod=_5[_6.type],fin=_6.finish,pmt=_6.color,_17=[{offset:0,color:mod.call(_5,v.substract(f,c),fin,pmt)}];
for(var a=_9+_12;a<to;a+=_12){
var p=m.multiplyPoint(mx,_8*Math.cos(a)+_7.x,_8*Math.sin(a)+_7.y,_7.z),df=_1(f,p),dt=_1(t,p);
_17.push({offset:df/(df+dt),color:mod.call(_5,v.substract(p,c),fin,pmt)});
}
_17.push({offset:1,color:mod.call(_5,v.substract(t,c),fin,pmt)});
return {type:"linear",x1:0,y1:-r,x2:0,y2:r,colors:_17};
};
})();
}
