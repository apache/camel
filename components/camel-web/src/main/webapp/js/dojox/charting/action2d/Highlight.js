/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.action2d.Highlight"]){
dojo._hasResource["dojox.charting.action2d.Highlight"]=true;
dojo.provide("dojox.charting.action2d.Highlight");
dojo.require("dojox.charting.action2d.Base");
dojo.require("dojox.color");
(function(){
var _1=100,_2=75,_3=50,c=dojox.color,cc=function(_6){
return function(){
return _6;
};
},hl=function(_8){
var a=new c.Color(_8),x=a.toHsl();
if(x.s==0){
x.l=x.l<50?100:0;
}else{
x.s=_1;
if(x.l<_3){
x.l=_2;
}else{
if(x.l>_2){
x.l=_3;
}else{
x.l=x.l-_3>_2-x.l?_3:_2;
}
}
}
return c.fromHsl(x);
};
dojo.declare("dojox.charting.action2d.Highlight",dojox.charting.action2d.Base,{defaultParams:{duration:400,easing:dojo.fx.easing.backOut},optionalParams:{highlight:"red"},constructor:function(_b,_c,_d){
var a=_d&&_d.highlight;
this.colorFun=a?(dojo.isFunction(a)?a:cc(a)):hl;
this.connect();
},process:function(o){
if(!o.shape||!(o.type in this.overOutEvents)){
return;
}
var _10=o.run.name,_11=o.index,_12,_13,_14;
if(_10 in this.anim){
_12=this.anim[_10][_11];
}else{
this.anim[_10]={};
}
if(_12){
_12.action.stop(true);
}else{
var _15=o.shape.getFill();
if(!_15||!(_15 instanceof dojo.Color)){
return;
}
this.anim[_10][_11]=_12={start:_15,end:this.colorFun(_15)};
}
var _16=_12.start,end=_12.end;
if(o.type=="onmouseout"){
var t=_16;
_16=end;
end=t;
}
_12.action=dojox.gfx.fx.animateFill({shape:o.shape,duration:this.duration,easing:this.easing,color:{start:_16,end:end}});
if(o.type=="onmouseout"){
dojo.connect(_12.action,"onEnd",this,function(){
if(this.anim[_10]){
delete this.anim[_10][_11];
}
});
}
_12.action.play();
}});
})();
}
