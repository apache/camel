/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.Chart3D"]){
dojo._hasResource["dojox.charting.Chart3D"]=true;
dojo.provide("dojox.charting.Chart3D");
dojo.require("dojox.gfx3d");
(function(){
var _1={x:0,y:0,z:1},v=dojox.gfx3d.vector,n=dojox.gfx.normalizedLength;
dojo.declare("dojox.charting.Chart3D",null,{constructor:function(_4,_5,_6,_7){
this.node=dojo.byId(_4);
this.surface=dojox.gfx.createSurface(this.node,n(this.node.style.width),n(this.node.style.height));
this.view=this.surface.createViewport();
this.view.setLights(_5.lights,_5.ambient,_5.specular);
this.view.setCameraTransform(_6);
this.theme=_7;
this.walls=[];
this.plots=[];
},generate:function(){
return this._generateWalls()._generatePlots();
},invalidate:function(){
this.view.invalidate();
return this;
},render:function(){
this.view.render();
return this;
},addPlot:function(_8){
return this._add(this.plots,_8);
},removePlot:function(_9){
return this._remove(this.plots,_9);
},addWall:function(_a){
return this._add(this.walls,_a);
},removeWall:function(_b){
return this._remove(this.walls,_b);
},_add:function(_c,_d){
if(!dojo.some(_c,function(i){
return i==_d;
})){
_c.push(_d);
this.view.invalidate();
}
return this;
},_remove:function(_f,_10){
var a=dojo.filter(_f,function(i){
return i!=_10;
});
return a.length<_f.length?(_f=a,this.invalidate()):this;
},_generateWalls:function(){
for(var i=0;i<this.walls.length;++i){
if(v.dotProduct(_1,this.walls[i].normal)>0){
this.walls[i].generate(this);
}
}
return this;
},_generatePlots:function(){
var _14=0,m=dojox.gfx3d.matrix,i=0;
for(;i<this.plots.length;++i){
_14+=this.plots[i].getDepth();
}
for(--i;i>=0;--i){
var _17=this.view.createScene();
_17.setTransform(m.translate(0,0,-_14));
this.plots[i].generate(this,_17);
_14-=this.plots[i].getDepth();
}
return this;
}});
})();
}
