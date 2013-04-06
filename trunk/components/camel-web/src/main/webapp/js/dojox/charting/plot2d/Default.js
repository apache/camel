/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Default"]){
dojo._hasResource["dojox.charting.plot2d.Default"]=true;
dojo.provide("dojox.charting.plot2d.Default");
dojo.require("dojox.charting.plot2d.common");
dojo.require("dojox.charting.plot2d.Base");
dojo.require("dojox.lang.utils");
dojo.require("dojox.lang.functional");
dojo.require("dojox.lang.functional.reversed");
(function(){
var df=dojox.lang.functional,du=dojox.lang.utils,dc=dojox.charting.plot2d.common,_4=df.lambda("item.purgeGroup()");
dojo.declare("dojox.charting.plot2d.Default",dojox.charting.plot2d.Base,{defaultParams:{hAxis:"x",vAxis:"y",lines:true,areas:false,markers:false,shadows:0,tension:0},optionalParams:{},constructor:function(_5,_6){
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
var t=this.chart.theme,_d,_e,_f,_10,_11=this.events();
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
var s=run.group,_14,ht=this._hScaler.scaler.getTransformerFromModel(this._hScaler),vt=this._vScaler.scaler.getTransformerFromModel(this._vScaler);
if(typeof run.data[0]=="number"){
_14=dojo.map(run.data,function(v,i){
return {x:ht(i+1)+_9.l,y:_8.height-_9.b-vt(v)};
},this);
}else{
_14=dojo.map(run.data,function(v,i){
return {x:ht(v.x)+_9.l,y:_8.height-_9.b-vt(v.y)};
},this);
}
if(!run.fill||!run.stroke){
_f=run.dyn.color=new dojo.Color(t.next("color"));
}
var _1b=this.opt.tension?dc.curve(_14,this.opt.tension):"";
if(this.opt.areas){
var _1c=run.fill?run.fill:dc.augmentFill(t.series.fill,_f);
var _1d=dojo.clone(_14);
if(this.opt.tension){
var _1e="L"+_1d[_1d.length-1].x+","+(_8.height-_9.b)+" L"+_1d[0].x+","+(_8.height-_9.b)+" L"+_1d[0].x+","+_1d[0].y;
run.dyn.fill=s.createPath(_1b+" "+_1e).setFill(_1c).getFill();
}else{
_1d.push({x:_14[_14.length-1].x,y:_8.height-_9.b});
_1d.push({x:_14[0].x,y:_8.height-_9.b});
_1d.push(_14[0]);
run.dyn.fill=s.createPolyline(_1d).setFill(_1c).getFill();
}
}
if(this.opt.lines||this.opt.markers){
_d=run.stroke?dc.makeStroke(run.stroke):dc.augmentStroke(t.series.stroke,_f);
if(run.outline||t.series.outline){
_e=dc.makeStroke(run.outline?run.outline:t.series.outline);
_e.width=2*_e.width+_d.width;
}
}
if(this.opt.markers){
_10=run.dyn.marker=run.marker?run.marker:t.next("marker");
}
var _1f=null,_20=null,_21=null;
if(this.opt.shadows&&_d){
var sh=this.opt.shadows,_23=new dojo.Color([0,0,0,0.3]),_24=dojo.map(_14,function(c){
return {x:c.x+sh.dx,y:c.y+sh.dy};
}),_26=dojo.clone(_e?_e:_d);
_26.color=_23;
_26.width+=sh.dw?sh.dw:0;
if(this.opt.lines){
if(this.opt.tension){
run.dyn.shadow=s.createPath(dc.curve(_24,this.opt.tension)).setStroke(_26).getStroke();
}else{
run.dyn.shadow=s.createPolyline(_24).setStroke(_26).getStroke();
}
}
if(this.opt.markers){
_21=dojo.map(_24,function(c){
return s.createPath("M"+c.x+" "+c.y+" "+_10).setStroke(_26).setFill(_23);
},this);
}
}
if(this.opt.lines){
if(_e){
if(this.opt.tension){
run.dyn.outline=s.createPath(_1b).setStroke(_e).getStroke();
}else{
run.dyn.outline=s.createPolyline(_14).setStroke(_e).getStroke();
}
}
if(this.opt.tension){
run.dyn.stroke=s.createPath(_1b).setStroke(_d).getStroke();
}else{
run.dyn.stroke=s.createPolyline(_14).setStroke(_d).getStroke();
}
}
if(this.opt.markers){
_1f=new Array(_14.length);
_20=new Array(_14.length);
dojo.forEach(_14,function(c,i){
var _2a="M"+c.x+" "+c.y+" "+_10;
if(_e){
_20[i]=s.createPath(_2a).setStroke(_e);
}
_1f[i]=s.createPath(_2a).setStroke(_d).setFill(_d.color);
},this);
if(_11){
dojo.forEach(_1f,function(s,i){
var o={element:"marker",index:i,run:run,plot:this,hAxis:this.hAxis||null,vAxis:this.vAxis||null,shape:s,outline:_20[i]||null,shadow:_21&&_21[i]||null,cx:_14[i].x,cy:_14[i].y};
if(typeof run.data[0]=="number"){
o.x=i+1;
o.y=run.data[i];
}else{
o.x=run.data[i].x;
o.y=run.data[i].y;
}
this._connectEvents(s,o);
},this);
}
}
run.dirty=false;
}
this.dirty=false;
return this;
}});
})();
}
