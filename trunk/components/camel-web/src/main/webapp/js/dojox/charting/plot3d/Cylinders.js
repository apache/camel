/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot3d.Cylinders"]){
dojo._hasResource["dojox.charting.plot3d.Cylinders"]=true;
dojo.provide("dojox.charting.plot3d.Cylinders");
dojo.require("dojox.charting.plot3d.Base");
(function(){
var _1=function(a,f,o){
a=typeof a=="string"?a.split(""):a;
o=o||dojo.global;
var z=a[0];
for(var i=1;i<a.length;z=f.call(o,z,a[i++])){
}
return z;
};
dojo.declare("dojox.charting.plot3d.Cylinders",dojox.charting.plot3d.Base,{constructor:function(_7,_8,_9){
this.depth="auto";
this.gap=0;
this.data=[];
this.material={type:"plastic",finish:"shiny",color:"lime"};
this.outline=null;
if(_9){
if("depth" in _9){
this.depth=_9.depth;
}
if("gap" in _9){
this.gap=_9.gap;
}
if("material" in _9){
var m=_9.material;
if(typeof m=="string"||m instanceof dojo.Color){
this.material.color=m;
}else{
this.material=m;
}
}
if("outline" in _9){
this.outline=_9.outline;
}
}
},getDepth:function(){
if(this.depth=="auto"){
var w=this.width;
if(this.data&&this.data.length){
w=w/this.data.length;
}
return w-2*this.gap;
}
return this.depth;
},generate:function(_c,_d){
if(!this.data){
return this;
}
var _e=this.width/this.data.length,_f=0,_10=this.height/_1(this.data,Math.max);
if(!_d){
_d=_c.view;
}
for(var i=0;i<this.data.length;++i,_f+=_e){
_d.createCylinder({center:{x:_f+_e/2,y:0,z:0},radius:_e/2-this.gap,height:this.data[i]*_10}).setTransform(dojox.gfx3d.matrix.rotateXg(-90)).setFill(this.material).setStroke(this.outline);
}
}});
})();
}
