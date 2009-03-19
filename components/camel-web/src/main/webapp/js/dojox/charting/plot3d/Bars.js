/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot3d.Bars"]){
dojo._hasResource["dojox.charting.plot3d.Bars"]=true;
dojo.provide("dojox.charting.plot3d.Bars");
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
dojo.declare("dojox.charting.plot3d.Bars",dojox.charting.plot3d.Base,{constructor:function(_7,_8,_9){
this.depth="auto";
this.gap=0;
this.data=[];
this.material={type:"plastic",finish:"dull",color:"lime"};
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
var _e=this.width/this.data.length,_f=0,_10=this.depth=="auto"?_e-2*this.gap:this.depth,_11=this.height/_1(this.data,Math.max);
if(!_d){
_d=_c.view;
}
for(var i=0;i<this.data.length;++i,_f+=_e){
_d.createCube({bottom:{x:_f+this.gap,y:0,z:0},top:{x:_f+_e-this.gap,y:this.data[i]*_11,z:_10}}).setFill(this.material);
}
}});
})();
}
