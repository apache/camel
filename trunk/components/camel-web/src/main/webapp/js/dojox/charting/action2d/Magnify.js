/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.action2d.Magnify"]){
dojo._hasResource["dojox.charting.action2d.Magnify"]=true;
dojo.provide("dojox.charting.action2d.Magnify");
dojo.require("dojox.charting.action2d.Base");
dojo.require("dojox.gfx.matrix");
(function(){
var _1=2,m=dojox.gfx.matrix,gf=dojox.gfx.fx;
dojo.declare("dojox.charting.action2d.Magnify",dojox.charting.action2d.Base,{defaultParams:{duration:400,easing:dojo.fx.easing.backOut,scale:_1},optionalParams:{},constructor:function(_4,_5,_6){
this.scale=_6&&typeof _6.scale=="number"?_6.scale:_1;
this.connect();
},process:function(o){
if(!o.shape||!(o.type in this.overOutEvents)||!("cx" in o)||!("cy" in o)){
return;
}
var _8=o.run.name,_9=o.index,_a=[],_b,_c,_d;
if(_8 in this.anim){
_b=this.anim[_8][_9];
}else{
this.anim[_8]={};
}
if(_b){
_b.action.stop(true);
}else{
this.anim[_8][_9]=_b={};
}
if(o.type=="onmouseover"){
_c=m.identity;
_d=this.scale;
}else{
_c=m.scaleAt(this.scale,o.cx,o.cy);
_d=1/this.scale;
}
var _e={shape:o.shape,duration:this.duration,easing:this.easing,transform:[{name:"scaleAt",start:[1,o.cx,o.cy],end:[_d,o.cx,o.cy]},_c]};
if(o.shape){
_a.push(gf.animateTransform(_e));
}
if(o.oultine){
_e.shape=o.outline;
_a.push(gf.animateTransform(_e));
}
if(o.shadow){
_e.shape=o.shadow;
_a.push(gf.animateTransform(_e));
}
if(!_a.length){
delete this.anim[_8][_9];
return;
}
_b.action=dojo.fx.combine(_a);
if(o.type=="onmouseout"){
dojo.connect(_b.action,"onEnd",this,function(){
if(this.anim[_8]){
delete this.anim[_8][_9];
}
});
}
_b.action.play();
}});
})();
}
