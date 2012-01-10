/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Stacked"]){
dojo._hasResource["dojox.charting.plot2d.Stacked"]=true;
dojo.provide("dojox.charting.plot2d.Stacked");
dojo.require("dojox.charting.plot2d.common");
dojo.require("dojox.charting.plot2d.Default");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.sequence");
dojo.require("dojox.lang.functional.reversed");
(function(){
var df=dojox.lang.functional,dc=dojox.charting.plot2d.common,_3=df.lambda("item.purgeGroup()");
dojo.declare("dojox.charting.plot2d.Stacked",dojox.charting.plot2d.Default,{calculateAxes:function(_4){
var _5=dc.collectStackedStats(this.series);
this._maxRunLength=_5.hmax;
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
var t=this.chart.theme,_10,_11,_12,_13,_14=this.events(),ht=this._hScaler.scaler.getTransformerFromModel(this._hScaler),vt=this._vScaler.scaler.getTransformerFromModel(this._vScaler);
this.resetEvents();
for(var i=this.series.length-1;i>=0;--i){
var _a=this.series[i];
if(!this.dirty&&!_a.dirty){
continue;
}
_a.cleanGroup();
var s=_a.group,_17=dojo.map(_8,function(v,i){
return {x:ht(i+1)+_7.l,y:_6.height-_7.b-vt(v)};
},this);
if(!_a.fill||!_a.stroke){
_12=new dojo.Color(t.next("color"));
}
var _1a=this.opt.tension?dc.curve(_17,this.opt.tension):"";
if(this.opt.areas){
var _1b=dojo.clone(_17);
var _1c=_a.fill?_a.fill:dc.augmentFill(t.series.fill,_12);
if(this.opt.tension){
var p=dc.curve(_1b,this.opt.tension);
p+=" L"+_17[_17.length-1].x+","+(_6.height-_7.b)+" L"+_17[0].x+","+(_6.height-_7.b)+" L"+_17[0].x+","+_17[0].y;
_a.dyn.fill=s.createPath(p).setFill(_1c).getFill();
}else{
_1b.push({x:_17[_17.length-1].x,y:_6.height-_7.b});
_1b.push({x:_17[0].x,y:_6.height-_7.b});
_1b.push(_17[0]);
_a.dyn.fill=s.createPolyline(_1b).setFill(_1c).getFill();
}
}
if(this.opt.lines||this.opt.markers){
_10=_a.stroke?dc.makeStroke(_a.stroke):dc.augmentStroke(t.series.stroke,_12);
if(_a.outline||t.series.outline){
_11=dc.makeStroke(_a.outline?_a.outline:t.series.outline);
_11.width=2*_11.width+_10.width;
}
}
if(this.opt.markers){
_13=_a.dyn.marker=_a.marker?_a.marker:t.next("marker");
}
var _1e,_1f,_20;
if(this.opt.shadows&&_10){
var sh=this.opt.shadows,_22=new dojo.Color([0,0,0,0.3]),_23=dojo.map(_17,function(c){
return {x:c.x+sh.dx,y:c.y+sh.dy};
}),_25=dojo.clone(_11?_11:_10);
_25.color=_22;
_25.width+=sh.dw?sh.dw:0;
if(this.opt.lines){
if(this.opt.tension){
_a.dyn.shadow=s.createPath(dc.curve(_23,this.opt.tension)).setStroke(_25).getStroke();
}else{
_a.dyn.shadow=s.createPolyline(_23).setStroke(_25).getStroke();
}
}
if(this.opt.markers){
_20=dojo.map(_23,function(c){
return s.createPath("M"+c.x+" "+c.y+" "+_13).setStroke(_25).setFill(_22);
},this);
}
}
if(this.opt.lines){
if(_11){
if(this.opt.tension){
_a.dyn.outline=s.createPath(_1a).setStroke(_11).getStroke();
}else{
_a.dyn.outline=s.createPolyline(_17).setStroke(_11).getStroke();
}
}
if(this.opt.tension){
_a.dyn.stroke=s.createPath(_1a).setStroke(_10).getStroke();
}else{
_a.dyn.stroke=s.createPolyline(_17).setStroke(_10).getStroke();
}
}
if(this.opt.markers){
_1e=new Array(_17.length);
_1f=new Array(_17.length);
dojo.forEach(_17,function(c,i){
var _29="M"+c.x+" "+c.y+" "+_13;
if(_11){
_1f[i]=s.createPath(_29).setStroke(_11);
}
_1e[i]=s.createPath(_29).setStroke(_10).setFill(_10.color);
},this);
if(_14){
dojo.forEach(_1e,function(s,i){
var o={element:"marker",index:i,run:_a,plot:this,hAxis:this.hAxis||null,vAxis:this.vAxis||null,shape:s,outline:_1f[i]||null,shadow:_20&&_20[i]||null,cx:_17[i].x,cy:_17[i].y,x:i+1,y:_a.data[i]};
this._connectEvents(s,o);
},this);
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
