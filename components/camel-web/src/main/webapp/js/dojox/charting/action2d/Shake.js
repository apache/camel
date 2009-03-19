/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.action2d.Shake"]){
dojo._hasResource["dojox.charting.action2d.Shake"]=true;
dojo.provide("dojox.charting.action2d.Shake");
dojo.require("dojox.charting.action2d.Base");
dojo.require("dojox.gfx.matrix");
(function(){
var _1=3,m=dojox.gfx.matrix,gf=dojox.gfx.fx;
dojo.declare("dojox.charting.action2d.Shake",dojox.charting.action2d.Base,{defaultParams:{duration:400,easing:dojo.fx.easing.backOut,shiftX:_1,shiftY:_1},optionalParams:{},constructor:function(_4,_5,_6){
if(!_6){
_6={};
}
this.shiftX=typeof _6.shiftX=="number"?_6.shiftX:_1;
this.shiftY=typeof _6.shiftY=="number"?_6.shiftY:_1;
this.connect();
},process:function(o){
if(!o.shape||!(o.type in this.overOutEvents)){
return;
}
var _8=o.run.name,_9=o.index,_a=[],_b,_c=o.type=="onmouseover"?this.shiftX:-this.shiftX,_d=o.type=="onmouseover"?this.shiftY:-this.shiftY;
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
var _e={shape:o.shape,duration:this.duration,easing:this.easing,transform:[{name:"translate",start:[this.shiftX,this.shiftY],end:[0,0]},m.identity]};
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
