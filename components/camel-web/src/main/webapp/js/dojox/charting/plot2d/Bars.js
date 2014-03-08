/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Bars"]){
dojo._hasResource["dojox.charting.plot2d.Bars"]=true;
dojo.provide("dojox.charting.plot2d.Bars");
dojo.require("dojox.charting.plot2d.common");
dojo.require("dojox.charting.plot2d.Base");
dojo.require("dojox.lang.utils");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.reversed");
(function(){
var df=dojox.lang.functional,du=dojox.lang.utils,dc=dojox.charting.plot2d.common,_4=df.lambda("item.purgeGroup()");
dojo.declare("dojox.charting.plot2d.Bars",dojox.charting.plot2d.Base,{defaultParams:{hAxis:"x",vAxis:"y",gap:0,shadows:null},optionalParams:{minBarSize:1,maxBarSize:1},constructor:function(_5,_6){
this.opt=dojo.clone(this.defaultParams);
du.updateWithObject(this.opt,_6);
du.updateWithPattern(this.opt,_6,this.optionalParams);
this.series=[];
this.hAxis=this.opt.hAxis;
this.vAxis=this.opt.vAxis;
},calculateAxes:function(_7){
var _8=dc.collectSimpleStats(this.series),t;
_8.hmin-=0.5;
_8.hmax+=0.5;
t=_8.hmin,_8.hmin=_8.vmin,_8.vmin=t;
t=_8.hmax,_8.hmax=_8.vmax,_8.vmax=t;
this._calc(_7,_8);
return this;
},render:function(_a,_b){
this.dirty=this.isDirty();
if(this.dirty){
dojo.forEach(this.series,_4);
this.cleanGroup();
var s=this.group;
df.forEachRev(this.series,function(_d){
_d.cleanGroup(s);
});
}
var t=this.chart.theme,_f,_10,_11,f,gap,_14,ht=this._hScaler.scaler.getTransformerFromModel(this._hScaler),vt=this._vScaler.scaler.getTransformerFromModel(this._vScaler),_17=Math.max(0,this._hScaler.bounds.lower),_18=ht(_17),_19=this.events();
f=dc.calculateBarSize(this._vScaler.bounds.scale,this.opt);
gap=f.gap;
_14=f.size;
this.resetEvents();
for(var i=this.series.length-1;i>=0;--i){
var run=this.series[i];
if(!this.dirty&&!run.dirty){
continue;
}
run.cleanGroup();
var s=run.group;
if(!run.fill||!run.stroke){
_f=run.dyn.color=new dojo.Color(t.next("color"));
}
_10=run.stroke?run.stroke:dc.augmentStroke(t.series.stroke,_f);
_11=run.fill?run.fill:dc.augmentFill(t.series.fill,_f);
for(var j=0;j<run.data.length;++j){
var v=run.data[j],hv=ht(v),_1f=hv-_18,w=Math.abs(_1f);
if(w>=1&&_14>=1){
var _21=s.createRect({x:_b.l+(v<_17?hv:_18),y:_a.height-_b.b-vt(j+1.5)+gap,width:w,height:_14}).setFill(_11).setStroke(_10);
run.dyn.fill=_21.getFill();
run.dyn.stroke=_21.getStroke();
if(_19){
var o={element:"bar",index:j,run:run,plot:this,hAxis:this.hAxis||null,vAxis:this.vAxis||null,shape:_21,x:v,y:j+1.5};
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
