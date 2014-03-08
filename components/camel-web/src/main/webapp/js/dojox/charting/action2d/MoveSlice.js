/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.action2d.MoveSlice"]){
dojo._hasResource["dojox.charting.action2d.MoveSlice"]=true;
dojo.provide("dojox.charting.action2d.MoveSlice");
dojo.require("dojox.charting.action2d.Base");
dojo.require("dojox.gfx.matrix");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.scan");
dojo.require("dojox.lang.functional.fold");
(function(){
var _1=1.05,_2=7,m=dojox.gfx.matrix,gf=dojox.gfx.fx,df=dojox.lang.functional;
dojo.declare("dojox.charting.action2d.MoveSlice",dojox.charting.action2d.Base,{defaultParams:{duration:400,easing:dojo.fx.easing.backOut,scale:_1,shift:_2},optionalParams:{},constructor:function(_6,_7,_8){
if(!_8){
_8={};
}
this.scale=typeof _8.scale=="number"?_8.scale:_1;
this.shift=typeof _8.shift=="number"?_8.shift:_2;
this.connect();
},process:function(o){
if(!o.shape||o.element!="slice"||!(o.type in this.overOutEvents)){
return;
}
if(!this.angles){
if(typeof o.run.data[0]=="number"){
this.angles=df.map(df.scanl(o.run.data,"+",0),"* 2 * Math.PI / this",df.foldl(o.run.data,"+",0));
}else{
this.angles=df.map(df.scanl(o.run.data,"a + b.y",0),"* 2 * Math.PI / this",df.foldl(o.run.data,"a + b.y",0));
}
}
var _a=o.index,_b,_c,_d,_e,_f=(this.angles[_a]+this.angles[_a+1])/2,_10=m.rotateAt(-_f,o.cx,o.cy),_11=m.rotateAt(_f,o.cx,o.cy);
_b=this.anim[_a];
if(_b){
_b.action.stop(true);
}else{
this.anim[_a]=_b={};
}
if(o.type=="onmouseover"){
_d=0;
_e=this.shift;
_c=this.scale;
}else{
_d=this.shift;
_e=0;
_c=1/this.scale;
}
_b.action=dojox.gfx.fx.animateTransform({shape:o.shape,duration:this.duration,easing:this.easing,transform:[_11,{name:"translate",start:[_d,0],end:[_e,0]},{name:"scaleAt",start:[1,o.cx,o.cy],end:[_c,o.cx,o.cy]},_10]});
if(o.type=="onmouseout"){
dojo.connect(_b.action,"onEnd",this,function(){
delete this.anim[_a];
});
}
_b.action.play();
},reset:function(){
delete this.angles;
}});
})();
}
