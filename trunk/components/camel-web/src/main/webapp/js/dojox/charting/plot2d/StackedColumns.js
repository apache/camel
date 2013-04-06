/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.StackedColumns"]){
dojo._hasResource["dojox.charting.plot2d.StackedColumns"]=true;
dojo.provide("dojox.charting.plot2d.StackedColumns");
dojo.require("dojox.charting.plot2d.common");
dojo.require("dojox.charting.plot2d.Columns");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.reversed");
(function(){
var df=dojox.lang.functional,dc=dojox.charting.plot2d.common,_3=df.lambda("item.purgeGroup()");
dojo.declare("dojox.charting.plot2d.StackedColumns",dojox.charting.plot2d.Columns,{calculateAxes:function(_4){
var _5=dc.collectStackedStats(this.series);
this._maxRunLength=_5.hmax;
_5.hmin-=0.5;
_5.hmax+=0.5;
this._calc(_4,_5);
return this;
},render:function(_6,_7){
if(this._maxRunLength<=0){
return this;
}
var _8=df.repeat(this._maxRunLength,"-> 0",0);
for(var i=0;i<this.series.length;++i){
var _a=this.series[i];
for(var j=0;j<_a.data.length;++j){
var v=_a.data[j];
if(isNaN(v)){
v=0;
}
_8[j]+=v;
}
}
this.dirty=this.isDirty();
if(this.dirty){
dojo.forEach(this.series,_3);
this.cleanGroup();
var s=this.group;
df.forEachRev(this.series,function(_e){
_e.cleanGroup(s);
});
}
var t=this.chart.theme,_10,_11,_12,f,gap,_15,ht=this._hScaler.scaler.getTransformerFromModel(this._hScaler),vt=this._vScaler.scaler.getTransformerFromModel(this._vScaler),_18=this.events();
f=dc.calculateBarSize(this._hScaler.bounds.scale,this.opt);
gap=f.gap;
_15=f.size;
this.resetEvents();
for(var i=this.series.length-1;i>=0;--i){
var _a=this.series[i];
if(!this.dirty&&!_a.dirty){
continue;
}
_a.cleanGroup();
var s=_a.group;
if(!_a.fill||!_a.stroke){
_10=_a.dyn.color=new dojo.Color(t.next("color"));
}
_11=_a.stroke?_a.stroke:dc.augmentStroke(t.series.stroke,_10);
_12=_a.fill?_a.fill:dc.augmentFill(t.series.fill,_10);
for(var j=0;j<_8.length;++j){
var v=_8[j],_19=vt(v);
if(_15>=1&&_19>=1){
var _1a=s.createRect({x:_7.l+ht(j+0.5)+gap,y:_6.height-_7.b-vt(v),width:_15,height:_19}).setFill(_12).setStroke(_11);
_a.dyn.fill=_1a.getFill();
_a.dyn.stroke=_1a.getStroke();
if(_18){
var o={element:"column",index:j,run:_a,plot:this,hAxis:this.hAxis||null,vAxis:this.vAxis||null,shape:_1a,x:j+0.5,y:v};
this._connectEvents(_1a,o);
}
}
}
_a.dirty=false;
for(var j=0;j<_a.data.length;++j){
var v=_a.data[j];
if(isNaN(v)){
v=0;
}
_8[j]-=v;
}
}
this.dirty=false;
return this;
}});
})();
}
