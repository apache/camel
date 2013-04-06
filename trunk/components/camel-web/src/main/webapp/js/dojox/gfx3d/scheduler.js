/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx3d.scheduler"]){
dojo._hasResource["dojox.gfx3d.scheduler"]=true;
dojo.provide("dojox.gfx3d.scheduler");
dojo.provide("dojox.gfx3d.drawer");
dojo.require("dojox.gfx3d.vector");
dojo.mixin(dojox.gfx3d.scheduler,{zOrder:function(_1,_2){
_2=_2?_2:dojox.gfx3d.scheduler.order;
_1.sort(function(a,b){
return _2(b)-_2(a);
});
return _1;
},bsp:function(_5,_6){

_6=_6?_6:dojox.gfx3d.scheduler.outline;
var p=new dojox.gfx3d.scheduler.BinarySearchTree(_5[0],_6);
dojo.forEach(_5.slice(1),function(_8){
p.add(_8,_6);
});
return p.iterate(_6);
},order:function(it){
return it.getZOrder();
},outline:function(it){
return it.getOutline();
}});
dojo.declare("dojox.gfx3d.scheduler.BinarySearchTree",null,{constructor:function(_b,_c){
this.plus=null;
this.minus=null;
this.object=_b;
var o=_c(_b);
this.orient=o[0];
this.normal=dojox.gfx3d.vector.normalize(o);
},add:function(_e,_f){
var _10=0.5,o=_f(_e),v=dojox.gfx3d.vector,n=this.normal,a=this.orient;
if(dojo.every(o,function(_15){
return Math.floor(_10+v.dotProduct(n,v.substract(_15,a)))<=0;
})){
if(this.minus){
this.minus.add(_e,_f);
}else{
this.minus=new dojox.gfx3d.scheduler.BinarySearchTree(_e,_f);
}
}else{
if(dojo.every(o,function(_16){
return Math.floor(_10+v.dotProduct(n,v.substract(_16,a)))>=0;
})){
if(this.plus){
this.plus.add(_e,_f);
}else{
this.plus=new dojox.gfx3d.scheduler.BinarySearchTree(_e,_f);
}
}else{
dojo.forEach(o,function(_17){

});
throw "The case: polygon cross siblings' plate is not implemneted yet";
}
}
},iterate:function(_18){
var _19=0.5;
var v=dojox.gfx3d.vector;
var _1b=[];
var _1c=null;
var _1d={x:0,y:0,z:-10000};
if(Math.floor(_19+v.dotProduct(this.normal,v.substract(_1d,this.orient)))<=0){
_1c=[this.plus,this.minus];
}else{
_1c=[this.minus,this.plus];
}
if(_1c[0]){
_1b=_1b.concat(_1c[0].iterate());
}
_1b.push(this.object);
if(_1c[1]){
_1b=_1b.concat(_1c[1].iterate());
}
return _1b;
}});
dojo.mixin(dojox.gfx3d.drawer,{conservative:function(_1e,_1f,_20){

dojo.forEach(this.objects,function(_21){
_21.destroy();
});
dojo.forEach(_1f,function(_22){
_22.draw(_20.lighting);
});
},chart:function(_23,_24,_25){

dojo.forEach(this.todos,function(_26){
_26.draw(_25.lighting);
});
}});
}
