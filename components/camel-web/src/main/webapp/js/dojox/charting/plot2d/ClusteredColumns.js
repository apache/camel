/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.ClusteredColumns"]){
dojo._hasResource["dojox.charting.plot2d.ClusteredColumns"]=true;
dojo.provide("dojox.charting.plot2d.ClusteredColumns");
dojo.require("dojox.charting.plot2d.common");
dojo.require("dojox.charting.plot2d.Columns");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.reversed");
(function(){
var df=dojox.lang.functional,dc=dojox.charting.plot2d.common,_3=df.lambda("item.purgeGroup()");
dojo.declare("dojox.charting.plot2d.ClusteredColumns",dojox.charting.plot2d.Columns,{render:function(_4,_5){
this.dirty=this.isDirty();
if(this.dirty){
dojo.forEach(this.series,_3);
this.cleanGroup();
var s=this.group;
df.forEachRev(this.series,function(_7){
_7.cleanGroup(s);
});
}
var t=this.chart.theme,_9,_a,_b,f,_d,_e,_f,ht=this._hScaler.scaler.getTransformerFromModel(this._hScaler),vt=this._vScaler.scaler.getTransformerFromModel(this._vScaler),_12=Math.max(0,this._vScaler.bounds.lower),_13=vt(_12),_14=this.events();
f=dc.calculateBarSize(this._hScaler.bounds.scale,this.opt,this.series.length);
_d=f.gap;
_e=_f=f.size;
this.resetEvents();
for(var i=0;i<this.series.length;++i){
var run=this.series[i],_17=_f*i;
if(!this.dirty&&!run.dirty){
continue;
}
run.cleanGroup();
var s=run.group;
if(!run.fill||!run.stroke){
_9=run.dyn.color=new dojo.Color(t.next("color"));
}
_a=run.stroke?run.stroke:dc.augmentStroke(t.series.stroke,_9);
_b=run.fill?run.fill:dc.augmentFill(t.series.fill,_9);
for(var j=0;j<run.data.length;++j){
var v=run.data[j],vv=vt(v),_1b=vv-_13,h=Math.abs(_1b);
if(_e>=1&&h>=1){
var _1d=s.createRect({x:_5.l+ht(j+0.5)+_d+_17,y:_4.height-_5.b-(v>_12?vv:_13),width:_e,height:h}).setFill(_b).setStroke(_a);
run.dyn.fill=_1d.getFill();
run.dyn.stroke=_1d.getStroke();
if(_14){
var o={element:"column",index:j,run:run,plot:this,hAxis:this.hAxis||null,vAxis:this.vAxis||null,shape:_1d,x:j+0.5,y:v};
this._connectEvents(_1d,o);
}
}
}
run.dirty=false;
}
this.dirty=false;
return this;
}});
})();
}
