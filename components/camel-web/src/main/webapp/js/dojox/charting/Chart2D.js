/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.Chart2D"]){
dojo._hasResource["dojox.charting.Chart2D"]=true;
dojo.provide("dojox.charting.Chart2D");
dojo.require("dojox.gfx");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.fold");
dojo.require("dojox.lang.functional.reversed");
dojo.require("dojox.charting.Theme");
dojo.require("dojox.charting.Series");
dojo.require("dojox.charting.axis2d.Default");
dojo.require("dojox.charting.plot2d.Default");
dojo.require("dojox.charting.plot2d.Lines");
dojo.require("dojox.charting.plot2d.Areas");
dojo.require("dojox.charting.plot2d.Markers");
dojo.require("dojox.charting.plot2d.MarkersOnly");
dojo.require("dojox.charting.plot2d.Scatter");
dojo.require("dojox.charting.plot2d.Stacked");
dojo.require("dojox.charting.plot2d.StackedLines");
dojo.require("dojox.charting.plot2d.StackedAreas");
dojo.require("dojox.charting.plot2d.Columns");
dojo.require("dojox.charting.plot2d.StackedColumns");
dojo.require("dojox.charting.plot2d.ClusteredColumns");
dojo.require("dojox.charting.plot2d.Bars");
dojo.require("dojox.charting.plot2d.StackedBars");
dojo.require("dojox.charting.plot2d.ClusteredBars");
dojo.require("dojox.charting.plot2d.Grid");
dojo.require("dojox.charting.plot2d.Pie");
dojo.require("dojox.charting.plot2d.Bubble");
(function(){
var df=dojox.lang.functional,dc=dojox.charting,_3=df.lambda("item.clear()"),_4=df.lambda("item.purgeGroup()"),_5=df.lambda("item.destroy()"),_6=df.lambda("item.dirty = false"),_7=df.lambda("item.dirty = true");
dojo.declare("dojox.charting.Chart2D",null,{constructor:function(_8,_9){
if(!_9){
_9={};
}
this.margins=_9.margins?_9.margins:{l:10,t:10,r:10,b:10};
this.stroke=_9.stroke;
this.fill=_9.fill;
this.theme=null;
this.axes={};
this.stack=[];
this.plots={};
this.series=[];
this.runs={};
this.dirty=true;
this.coords=null;
this.node=dojo.byId(_8);
var _a=dojo.marginBox(_8);
this.surface=dojox.gfx.createSurface(this.node,_a.w,_a.h);
},destroy:function(){
dojo.forEach(this.series,_5);
dojo.forEach(this.stack,_5);
df.forIn(this.axes,_5);
this.surface.destroy();
},getCoords:function(){
if(!this.coords){
this.coords=dojo.coords(this.node,true);
}
return this.coords;
},setTheme:function(_b){
this.theme=_b._clone();
this.dirty=true;
return this;
},addAxis:function(_c,_d){
var _e;
if(!_d||!("type" in _d)){
_e=new dc.axis2d.Default(this,_d);
}else{
_e=typeof _d.type=="string"?new dc.axis2d[_d.type](this,_d):new _d.type(this,_d);
}
_e.name=_c;
_e.dirty=true;
if(_c in this.axes){
this.axes[_c].destroy();
}
this.axes[_c]=_e;
this.dirty=true;
return this;
},getAxis:function(_f){
return this.axes[_f];
},removeAxis:function(_10){
if(_10 in this.axes){
this.axes[_10].destroy();
delete this.axes[_10];
this.dirty=true;
}
return this;
},addPlot:function(_11,_12){
var _13;
if(!_12||!("type" in _12)){
_13=new dc.plot2d.Default(this,_12);
}else{
_13=typeof _12.type=="string"?new dc.plot2d[_12.type](this,_12):new _12.type(this,_12);
}
_13.name=_11;
_13.dirty=true;
if(_11 in this.plots){
this.stack[this.plots[_11]].destroy();
this.stack[this.plots[_11]]=_13;
}else{
this.plots[_11]=this.stack.length;
this.stack.push(_13);
}
this.dirty=true;
return this;
},removePlot:function(_14){
if(_14 in this.plots){
var _15=this.plots[_14];
delete this.plots[_14];
this.stack[_15].destroy();
this.stack.splice(_15,1);
df.forIn(this.plots,function(idx,_17,_18){
if(idx>_15){
_18[_17]=idx-1;
}
});
this.dirty=true;
}
return this;
},addSeries:function(_19,_1a,_1b){
var run=new dc.Series(this,_1a,_1b);
if(_19 in this.runs){
this.series[this.runs[_19]].destroy();
this.series[this.runs[_19]]=run;
}else{
this.runs[_19]=this.series.length;
this.series.push(run);
}
run.name=_19;
this.dirty=true;
if(!("ymin" in run)&&"min" in run){
run.ymin=run.min;
}
if(!("ymax" in run)&&"max" in run){
run.ymax=run.max;
}
return this;
},removeSeries:function(_1d){
if(_1d in this.runs){
var _1e=this.runs[_1d],_1f=this.series[_1e].plot;
delete this.runs[_1d];
this.series[_1e].destroy();
this.series.splice(_1e,1);
df.forIn(this.runs,function(idx,_21,_22){
if(idx>_1e){
_22[_21]=idx-1;
}
});
this.dirty=true;
}
return this;
},updateSeries:function(_23,_24){
if(_23 in this.runs){
var run=this.series[this.runs[_23]];
run.data=_24;
run.dirty=true;
this._invalidateDependentPlots(run.plot,false);
this._invalidateDependentPlots(run.plot,true);
}
return this;
},resize:function(_26,_27){
var box;
switch(arguments.length){
case 0:
box=dojo.marginBox(this.node);
break;
case 1:
box=_26;
break;
default:
box={w:_26,h:_27};
break;
}
dojo.marginBox(this.node,box);
this.surface.setDimensions(box.w,box.h);
this.dirty=true;
this.coords=null;
return this.render();
},getGeometry:function(){
var ret={};
df.forIn(this.axes,function(_2a){
if(_2a.initialized()){
ret[_2a.name]={name:_2a.name,vertical:_2a.vertical,scaler:_2a.scaler,ticks:_2a.ticks};
}
});
return ret;
},setAxisWindow:function(_2b,_2c,_2d){
var _2e=this.axes[_2b];
if(_2e){
_2e.setWindow(_2c,_2d);
}
return this;
},setWindow:function(sx,sy,dx,dy){
if(!("plotArea" in this)){
this.calculateGeometry();
}
df.forIn(this.axes,function(_33){
var _34,_35,_36=_33.getScaler().bounds,s=_36.span/(_36.upper-_36.lower);
if(_33.vertical){
_34=sy;
_35=dy/s/_34;
}else{
_34=sx;
_35=dx/s/_34;
}
_33.setWindow(_34,_35);
});
return this;
},calculateGeometry:function(){
if(this.dirty){
return this.fullGeometry();
}
dojo.forEach(this.stack,function(_38){
if(_38.dirty||(_38.hAxis&&this.axes[_38.hAxis].dirty)||(_38.vAxis&&this.axes[_38.vAxis].dirty)){
_38.calculateAxes(this.plotArea);
}
},this);
return this;
},fullGeometry:function(){
this._makeDirty();
dojo.forEach(this.stack,_3);
if(!this.theme){
this.setTheme(new dojox.charting.Theme(dojox.charting._def));
}
dojo.forEach(this.series,function(run){
if(!(run.plot in this.plots)){
var _3a=new dc.plot2d.Default(this,{});
_3a.name=run.plot;
this.plots[run.plot]=this.stack.length;
this.stack.push(_3a);
}
this.stack[this.plots[run.plot]].addSeries(run);
},this);
dojo.forEach(this.stack,function(_3b){
if(_3b.hAxis){
_3b.setAxis(this.axes[_3b.hAxis]);
}
if(_3b.vAxis){
_3b.setAxis(this.axes[_3b.vAxis]);
}
},this);
var dim=this.dim=this.surface.getDimensions();
dim.width=dojox.gfx.normalizedLength(dim.width);
dim.height=dojox.gfx.normalizedLength(dim.height);
df.forIn(this.axes,_3);
dojo.forEach(this.stack,function(_3d){
_3d.calculateAxes(dim);
});
var _3e=this.offsets={l:0,r:0,t:0,b:0};
df.forIn(this.axes,function(_3f){
df.forIn(_3f.getOffsets(),function(o,i){
_3e[i]+=o;
});
});
df.forIn(this.margins,function(o,i){
_3e[i]+=o;
});
this.plotArea={width:dim.width-_3e.l-_3e.r,height:dim.height-_3e.t-_3e.b};
df.forIn(this.axes,_3);
dojo.forEach(this.stack,function(_44){
_44.calculateAxes(this.plotArea);
},this);
return this;
},render:function(){
if(this.theme){
this.theme.clear();
}
if(this.dirty){
return this.fullRender();
}
this.calculateGeometry();
df.forEachRev(this.stack,function(_45){
_45.render(this.dim,this.offsets);
},this);
df.forIn(this.axes,function(_46){
_46.render(this.dim,this.offsets);
},this);
this._makeClean();
if(this.surface.render){
this.surface.render();
}
return this;
},fullRender:function(){
this.fullGeometry();
var _47=this.offsets,dim=this.dim;
var _49=df.foldl(this.stack,"z + plot.getRequiredColors()",0);
this.theme.defineColors({num:_49,cache:false});
dojo.forEach(this.series,_4);
df.forIn(this.axes,_4);
dojo.forEach(this.stack,_4);
this.surface.clear();
var t=this.theme,_4b=t.plotarea&&t.plotarea.fill,_4c=t.plotarea&&t.plotarea.stroke;
if(_4b){
this.surface.createRect({x:_47.l,y:_47.t,width:dim.width-_47.l-_47.r,height:dim.height-_47.t-_47.b}).setFill(_4b);
}
if(_4c){
this.surface.createRect({x:_47.l,y:_47.t,width:dim.width-_47.l-_47.r-1,height:dim.height-_47.t-_47.b-1}).setStroke(_4c);
}
df.foldr(this.stack,function(z,_4e){
return _4e.render(dim,_47),0;
},0);
_4b=this.fill?this.fill:(t.chart&&t.chart.fill);
_4c=this.stroke?this.stroke:(t.chart&&t.chart.stroke);
if(_4b=="inherit"){
var _4f=this.node,_4b=new dojo.Color(dojo.style(_4f,"backgroundColor"));
while(_4b.a==0&&_4f!=document.documentElement){
_4b=new dojo.Color(dojo.style(_4f,"backgroundColor"));
_4f=_4f.parentNode;
}
}
if(_4b){
if(_47.l){
this.surface.createRect({width:_47.l,height:dim.height+1}).setFill(_4b);
}
if(_47.r){
this.surface.createRect({x:dim.width-_47.r,width:_47.r+1,height:dim.height+1}).setFill(_4b);
}
if(_47.t){
this.surface.createRect({width:dim.width+1,height:_47.t}).setFill(_4b);
}
if(_47.b){
this.surface.createRect({y:dim.height-_47.b,width:dim.width+1,height:_47.b+2}).setFill(_4b);
}
}
if(_4c){
this.surface.createRect({width:dim.width-1,height:dim.height-1}).setStroke(_4c);
}
df.forIn(this.axes,function(_50){
_50.render(dim,_47);
});
this._makeClean();
if(this.surface.render){
this.surface.render();
}
return this;
},connectToPlot:function(_51,_52,_53){
return _51 in this.plots?this.stack[this.plots[_51]].connect(_52,_53):null;
},_makeClean:function(){
dojo.forEach(this.axes,_6);
dojo.forEach(this.stack,_6);
dojo.forEach(this.series,_6);
this.dirty=false;
},_makeDirty:function(){
dojo.forEach(this.axes,_7);
dojo.forEach(this.stack,_7);
dojo.forEach(this.series,_7);
this.dirty=true;
},_invalidateDependentPlots:function(_54,_55){
if(_54 in this.plots){
var _56=this.stack[this.plots[_54]],_57,_58=_55?"vAxis":"hAxis";
if(_56[_58]){
_57=this.axes[_56[_58]];
if(_57.dependOnData()){
_57.dirty=true;
dojo.forEach(this.stack,function(p){
if(p[_58]&&p[_58]==_56[_58]){
p.dirty=true;
}
});
}
}else{
_56.dirty=true;
}
}
}});
})();
}
