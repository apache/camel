/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Bubble"]){
dojo._hasResource["dojox.charting.plot2d.Bubble"]=true;
dojo.provide("dojox.charting.plot2d.Bubble");
dojo.require("dojox.charting.plot2d.Base");
dojo.require("dojox.lang.functional");
(function(){
var df=dojox.lang.functional,du=dojox.lang.utils,dc=dojox.charting.plot2d.common,_4=df.lambda("item.purgeGroup()");
dojo.declare("dojox.charting.plot2d.Bubble",dojox.charting.plot2d.Base,{defaultParams:{hAxis:"x",vAxis:"y"},optionalParams:{},constructor:function(_5,_6){
this.opt=dojo.clone(this.defaultParams);
du.updateWithObject(this.opt,_6);
this.series=[];
this.hAxis=this.opt.hAxis;
this.vAxis=this.opt.vAxis;
},calculateAxes:function(_7){
this._calc(_7,dc.collectSimpleStats(this.series));
return this;
},render:function(_8,_9){
this.dirty=this.isDirty();
if(this.dirty){
dojo.forEach(this.series,_4);
this.cleanGroup();
var s=this.group;
df.forEachRev(this.series,function(_b){
_b.cleanGroup(s);
});
}
var t=this.chart.theme,_d,_e,_f,_10,_11,ht=this._hScaler.scaler.getTransformerFromModel(this._hScaler),vt=this._vScaler.scaler.getTransformerFromModel(this._vScaler),_14=this.events();
this.resetEvents();
for(var i=this.series.length-1;i>=0;--i){
var run=this.series[i];
if(!this.dirty&&!run.dirty){
continue;
}
run.cleanGroup();
if(!run.data.length){
run.dirty=false;
continue;
}
if(typeof run.data[0]=="number"){
console.warn("dojox.charting.plot2d.Bubble: the data in the following series cannot be rendered as a bubble chart; ",run);
continue;
}
var s=run.group,_17=dojo.map(run.data,function(v,i){
return {x:ht(v.x)+_9.l,y:_8.height-_9.b-vt(v.y),radius:this._vScaler.bounds.scale*(v.size/2)};
},this);
if(run.fill){
_f=run.fill;
}else{
if(run.stroke){
_f=run.stroke;
}else{
_f=run.dyn.color=new dojo.Color(t.next("color"));
}
}
run.dyn.fill=_f;
_d=run.dyn.stroke=run.stroke?dc.makeStroke(run.stroke):dc.augmentStroke(t.series.stroke,_f);
var _1a=null,_1b=null,_1c=null;
if(this.opt.shadows&&_d){
var sh=this.opt.shadows,_11=new dojo.Color([0,0,0,0.2]),_10=dojo.clone(_e?_e:_d);
_10.color=_11;
_10.width+=sh.dw?sh.dw:0;
run.dyn.shadow=_10;
var _1e=dojo.map(_17,function(_1f){
var sh=this.opt.shadows;
return s.createCircle({cx:_1f.x+sh.dx,cy:_1f.y+sh.dy,r:_1f.radius}).setStroke(_10).setFill(_11);
},this);
}
if(run.outline||t.series.outline){
_e=dc.makeStroke(run.outline?run.outline:t.series.outline);
_e.width=2*_e.width+_d.width;
run.dyn.outline=_e;
_1b=dojo.map(_17,function(_21){
s.createCircle({cx:_21.x,cy:_21.y,r:_21.radius}).setStroke(_e);
},this);
}
_1a=dojo.map(_17,function(_22){
return s.createCircle({cx:_22.x,cy:_22.y,r:_22.radius}).setStroke(_d).setFill(_f);
},this);
if(_14){
dojo.forEach(_1a,function(s,i){
var o={element:"circle",index:i,run:run,plot:this,hAxis:this.hAxis||null,vAxis:this.vAxis||null,shape:s,outline:_1b&&_1b[i]||null,shadow:_1c&&_1c[i]||null,x:run.data[i].x,y:run.data[i].y,r:run.data[i].size/2,cx:_17[i].x,cy:_17[i].y,cr:_17[i].radius};
this._connectEvents(s,o);
},this);
}
run.dirty=false;
}
this.dirty=false;
return this;
}});
})();
}
