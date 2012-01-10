/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Columns"]){
dojo._hasResource["dojox.charting.plot2d.Columns"]=true;
dojo.provide("dojox.charting.plot2d.Columns");
dojo.require("dojox.charting.plot2d.common");
dojo.require("dojox.charting.plot2d.Base");
dojo.require("dojox.lang.utils");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.reversed");
(function(){
var df=dojox.lang.functional,du=dojox.lang.utils,dc=dojox.charting.plot2d.common,_4=df.lambda("item.purgeGroup()");
dojo.declare("dojox.charting.plot2d.Columns",dojox.charting.plot2d.Base,{defaultParams:{hAxis:"x",vAxis:"y",gap:0,shadows:null},optionalParams:{minBarSize:1,maxBarSize:1},constructor:function(_5,_6){
this.opt=dojo.clone(this.defaultParams);
du.updateWithObject(this.opt,_6);
du.updateWithPattern(this.opt,_6,this.optionalParams);
this.series=[];
this.hAxis=this.opt.hAxis;
this.vAxis=this.opt.vAxis;
},calculateAxes:function(_7){
var _8=dc.collectSimpleStats(this.series);
_8.hmin-=0.5;
_8.hmax+=0.5;
this._calc(_7,_8);
return this;
},render:function(_9,_a){
this.dirty=this.isDirty();
if(this.dirty){
dojo.forEach(this.series,_4);
this.cleanGroup();
var s=this.group;
df.forEachRev(this.series,function(_c){
_c.cleanGroup(s);
});
}
var t=this.chart.theme,_e,_f,_10,f,gap,_13,ht=this._hScaler.scaler.getTransformerFromModel(this._hScaler),vt=this._vScaler.scaler.getTransformerFromModel(this._vScaler),_16=Math.max(0,this._vScaler.bounds.lower),_17=vt(_16),_18=this.events();
f=dc.calculateBarSize(this._hScaler.bounds.scale,this.opt);
gap=f.gap;
_13=f.size;
this.resetEvents();
for(var i=this.series.length-1;i>=0;--i){
var run=this.series[i];
if(!this.dirty&&!run.dirty){
continue;
}
run.cleanGroup();
var s=run.group;
if(!run.fill||!run.stroke){
_e=run.dyn.color=new dojo.Color(t.next("color"));
}
_f=run.stroke?run.stroke:dc.augmentStroke(t.series.stroke,_e);
_10=run.fill?run.fill:dc.augmentFill(t.series.fill,_e);
for(var j=0;j<run.data.length;++j){
var v=run.data[j],vv=vt(v),_1e=vv-_17,h=Math.abs(_1e);
if(_13>=1&&h>=1){
var _20={x:_a.l+ht(j+0.5)+gap,y:_9.height-_a.b-(v>_16?vv:_17),width:_13,height:h},_21=s.createRect(_20).setFill(_10).setStroke(_f);
run.dyn.fill=_21.getFill();
run.dyn.stroke=_21.getStroke();
if(_18){
var o={element:"column",index:j,run:run,plot:this,hAxis:this.hAxis||null,vAxis:this.vAxis||null,shape:_21,x:j+0.5,y:v};
this._connectEvents(_21,o);
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
