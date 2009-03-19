/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Pie"]){
dojo._hasResource["dojox.charting.plot2d.Pie"]=true;
dojo.provide("dojox.charting.plot2d.Pie");
dojo.require("dojox.charting.Element");
dojo.require("dojox.charting.axis2d.common");
dojo.require("dojox.charting.plot2d.common");
dojo.require("dojox.lang.functional");
dojo.require("dojox.gfx");
(function(){
var df=dojox.lang.functional,du=dojox.lang.utils,dc=dojox.charting.plot2d.common,da=dojox.charting.axis2d.common,g=dojox.gfx;
dojo.declare("dojox.charting.plot2d.Pie",dojox.charting.Element,{defaultParams:{labels:true,ticks:false,fixed:true,precision:1,labelOffset:20,labelStyle:"default",htmlLabels:true},optionalParams:{font:"",fontColor:"",radius:0},constructor:function(_6,_7){
this.opt=dojo.clone(this.defaultParams);
du.updateWithObject(this.opt,_7);
du.updateWithPattern(this.opt,_7,this.optionalParams);
this.run=null;
this.dyn=[];
},destroy:function(){
this.resetEvents();
this.inherited(arguments);
},clear:function(){
this.dirty=true;
this.dyn=[];
this.run=null;
return this;
},setAxis:function(_8){
return this;
},addSeries:function(_9){
this.run=_9;
return this;
},calculateAxes:function(_a){
return this;
},getRequiredColors:function(){
return this.run?this.run.data.length:0;
},plotEvent:function(o){
},connect:function(_c,_d){
this.dirty=true;
return dojo.connect(this,"plotEvent",_c,_d);
},events:function(){
var ls=this.plotEvent._listeners;
if(!ls||!ls.length){
return false;
}
for(var i in ls){
if(!(i in Array.prototype)){
return true;
}
}
return false;
},resetEvents:function(){
this.plotEvent({type:"onplotreset",plot:this});
},_connectEvents:function(_10,o){
_10.connect("onmouseover",this,function(e){
o.type="onmouseover";
o.event=e;
this.plotEvent(o);
});
_10.connect("onmouseout",this,function(e){
o.type="onmouseout";
o.event=e;
this.plotEvent(o);
});
_10.connect("onclick",this,function(e){
o.type="onclick";
o.event=e;
this.plotEvent(o);
});
},render:function(dim,_16){
if(!this.dirty){
return this;
}
this.dirty=false;
this.cleanGroup();
var s=this.group,_18,t=this.chart.theme;
this.resetEvents();
if(!this.run||!this.run.data.length){
return this;
}
var rx=(dim.width-_16.l-_16.r)/2,ry=(dim.height-_16.t-_16.b)/2,r=Math.min(rx,ry),_1d="font" in this.opt?this.opt.font:t.axis.font,_1e=_1d?g.normalizedLength(g.splitFontString(_1d).size):0,_1f="fontColor" in this.opt?this.opt.fontColor:t.axis.fontColor,_20=0,_21,_22,_23,_24,_25,_26,run=this.run.data,_28=this.events();
if(typeof run[0]=="number"){
_22=df.map(run,"Math.max(x, 0)");
if(df.every(_22,"<= 0")){
return this;
}
_23=df.map(_22,"/this",df.foldl(_22,"+",0));
if(this.opt.labels){
_24=dojo.map(_23,function(x){
return x>0?this._getLabel(x*100)+"%":"";
},this);
}
}else{
_22=df.map(run,"Math.max(x.y, 0)");
if(df.every(_22,"<= 0")){
return this;
}
_23=df.map(_22,"/this",df.foldl(_22,"+",0));
if(this.opt.labels){
_24=dojo.map(_23,function(x,i){
if(x<=0){
return "";
}
var v=run[i];
return "text" in v?v.text:this._getLabel(x*100)+"%";
},this);
}
}
if(this.opt.labels){
_25=df.foldl1(df.map(_24,function(_2d){
return dojox.gfx._base._getTextBox(_2d,{font:_1d}).w;
},this),"Math.max(a, b)")/2;
if(this.opt.labelOffset<0){
r=Math.min(rx-2*_25,ry-_1e)+this.opt.labelOffset;
}
_26=r-this.opt.labelOffset;
}
if("radius" in this.opt){
r=this.opt.radius;
_26=r-this.opt.labelOffset;
}
var _2e={cx:_16.l+rx,cy:_16.t+ry,r:r};
this.dyn=[];
dojo.some(_23,function(_2f,i){
if(_2f<=0){
return false;
}
var v=run[i];
if(_2f>=1){
var _32,_33,_34;
if(typeof v=="object"){
_32="color" in v?v.color:new dojo.Color(t.next("color"));
_33="fill" in v?v.fill:dc.augmentFill(t.series.fill,_32);
_34="stroke" in v?v.stroke:dc.augmentStroke(t.series.stroke,_32);
}else{
_32=new dojo.Color(t.next("color"));
_33=dc.augmentFill(t.series.fill,_32);
_34=dc.augmentStroke(t.series.stroke,_32);
}
var _35=s.createCircle(_2e).setFill(_33).setStroke(_34);
this.dyn.push({color:_32,fill:_33,stroke:_34});
if(_28){
var o={element:"slice",index:i,run:this.run,plot:this,shape:_35,x:i,y:typeof v=="number"?v:v.y,cx:_2e.cx,cy:_2e.cy,cr:r};
this._connectEvents(_35,o);
}
return true;
}
var end=_20+_2f*2*Math.PI;
if(i+1==_23.length){
end=2*Math.PI;
}
var _38=end-_20,x1=_2e.cx+r*Math.cos(_20),y1=_2e.cy+r*Math.sin(_20),x2=_2e.cx+r*Math.cos(end),y2=_2e.cy+r*Math.sin(end);
var _32,_33,_34;
if(typeof v=="object"){
_32="color" in v?v.color:new dojo.Color(t.next("color"));
_33="fill" in v?v.fill:dc.augmentFill(t.series.fill,_32);
_34="stroke" in v?v.stroke:dc.augmentStroke(t.series.stroke,_32);
}else{
_32=new dojo.Color(t.next("color"));
_33=dc.augmentFill(t.series.fill,_32);
_34=dc.augmentStroke(t.series.stroke,_32);
}
var _35=s.createPath({}).moveTo(_2e.cx,_2e.cy).lineTo(x1,y1).arcTo(r,r,0,_38>Math.PI,true,x2,y2).lineTo(_2e.cx,_2e.cy).closePath().setFill(_33).setStroke(_34);
this.dyn.push({color:_32,fill:_33,stroke:_34});
if(_28){
var o={element:"slice",index:i,run:this.run,plot:this,shape:_35,x:i,y:typeof v=="number"?v:v.y,cx:_2e.cx,cy:_2e.cy,cr:r};
this._connectEvents(_35,o);
}
_20=end;
return false;
},this);
if(this.opt.labels){
_20=0;
dojo.some(_23,function(_3d,i){
if(_3d<=0){
return false;
}
if(_3d>=1){
var v=run[i],_40=da.createText[this.opt.htmlLabels&&dojox.gfx.renderer!="vml"?"html":"gfx"](this.chart,s,_2e.cx,_2e.cy+_1e/2,"middle",_24[i],_1d,(typeof v=="object"&&"fontColor" in v)?v.fontColor:_1f);
if(this.opt.htmlLabels){
this.htmlElements.push(_40);
}
return true;
}
var end=_20+_3d*2*Math.PI,v=run[i];
if(i+1==_23.length){
end=2*Math.PI;
}
var _42=(_20+end)/2,x=_2e.cx+_26*Math.cos(_42),y=_2e.cy+_26*Math.sin(_42)+_1e/2;
var _40=da.createText[this.opt.htmlLabels&&dojox.gfx.renderer!="vml"?"html":"gfx"](this.chart,s,x,y,"middle",_24[i],_1d,(typeof v=="object"&&"fontColor" in v)?v.fontColor:_1f);
if(this.opt.htmlLabels){
this.htmlElements.push(_40);
}
_20=end;
return false;
},this);
}
return this;
},_getLabel:function(_45){
return this.opt.fixed?_45.toFixed(this.opt.precision):_45.toString();
}});
})();
}
