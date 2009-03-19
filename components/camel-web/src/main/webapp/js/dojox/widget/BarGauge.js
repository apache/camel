/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.BarGauge"]){
dojo._hasResource["dojox.widget.BarGauge"]=true;
dojo.provide("dojox.widget.BarGauge");
dojo.require("dojox.gfx");
dojo.require("dojox.widget.gauge._Gauge");
dojo.experimental("dojox.widget.BarGauge");
dojo.declare("dojox.widget.gauge.BarLineIndicator",[dojox.widget.gauge._Indicator],{width:1,_getShapes:function(){
if(!this._gauge){
return null;
}
var v=this.value;
if(v<this._gauge.min){
v=this._gauge.min;
}
if(v>this._gauge.max){
v=this._gauge.max;
}
var _2=this._gauge._getPosition(v);
var _3=[];
if(this.width>1){
_3[0]=this._gauge.surface.createRect({x:_2,y:this._gauge.dataY+this.offset,width:this.width,height:this.length});
_3[0].setStroke({color:this.color});
_3[0].setFill(this.color);
}else{
_3[0]=this._gauge.surface.createLine({x1:_2,y1:this._gauge.dataY+this.offset,x2:_2,y2:this._gauge.dataY+this.offset+this.length});
_3[0].setStroke({color:this.color});
}
return _3;
},draw:function(_4){
var i;
if(this.shapes){
this._move(_4);
}else{
if(this.shapes){
for(i=0;i<this.shapes.length;i++){
this._gauge.surface.remove(this.shapes[i]);
}
this.shapes=null;
}
if(this.text){
this._gauge.surface.rawNode.removeChild(this.text);
this.text=null;
}
this.color=this.color||"#000000";
this.length=this.length||this._gauge.dataHeight;
this.width=this.width||3;
this.offset=this.offset||0;
this.highlight=this.highlight||"#4D4D4D";
this.highlight2=this.highlight2||"#A3A3A3";
this.shapes=this._getShapes(this._gauge,this);
if(this.label){
var v=this.value;
if(v<this._gauge.min){
v=this._gauge.min;
}
if(v>this._gauge.max){
v=this._gauge.max;
}
var _7=this._gauge._getPosition(v);
this.text=this._gauge.drawText(""+this.label,_7,this._gauge.dataY+this.offset-5,"middle","top",this.color,this.font);
}
for(i=0;i<this.shapes.length;i++){
if(this.hover){
this.shapes[i].getEventSource().setAttribute("hover",this.hover);
}
if(this.onDragMove&&!this.noChange){
this._gauge.connect(this.shapes[i].getEventSource(),"onmousedown",this._gauge.handleMouseDown);
this.shapes[i].getEventSource().style.cursor="pointer";
}
}
this.currentValue=this.value;
}
},_move:function(_8){
var v=this.value;
if(v<this.min){
v=this.min;
}
if(v>this.max){
v=this.max;
}
var c=this._gauge._getPosition(this.currentValue);
this.currentValue=v;
v=this._gauge._getPosition(v)-this._gauge.dataX;
if(_8){
this.shapes[0].applyTransform(dojox.gfx.matrix.translate(v-(this.shapes[0].matrix?this.shapes[0].matrix.dx:0),0));
}else{
var _b=new dojo._Animation({curve:[c,v],duration:this.duration,easing:this.easing});
dojo.connect(_b,"onAnimate",dojo.hitch(this,function(_c){
this.shapes[0].applyTransform(dojox.gfx.matrix.translate(_c-(this.shapes[0].matrix?this.shapes[0].matrix.dx:0),0));
}));
_b.play();
}
}});
dojo.declare("dojox.widget.BarGauge",dojox.widget.gauge._Gauge,{dataX:5,dataY:5,dataWidth:0,dataHeight:0,_defaultIndicator:dojox.widget.gauge.BarLineIndicator,startup:function(){
if(this.getChildren){
dojo.forEach(this.getChildren(),function(_d){
_d.startup();
});
}
if(!this.dataWidth){
this.dataWidth=this.gaugeWidth-10;
}
if(!this.dataHeight){
this.dataHeight=this.gaugeHeight-10;
}
this.inherited(arguments);
},_getPosition:function(_e){
return this.dataX+Math.floor((_e-this.min)/(this.max-this.min)*this.dataWidth);
},_getValueForPosition:function(_f){
return (_f-this.dataX)*(this.max-this.min)/this.dataWidth+this.min;
},draw:function(){
if(!this.surface){
this.createSurface();
}
var i;
if(this._rangeData){
for(i=0;i<this._rangeData.length;i++){
this.drawRange(this._rangeData[i]);
}
if(this._img&&this.image.overlay){
this._img.moveToFront();
}
}
if(this._indicatorData){
for(i=0;i<this._indicatorData.length;i++){
this._indicatorData[i].draw();
}
}
},drawRange:function(_11){
if(_11.shape){
this.surface.remove(_11.shape);
_11.shape=null;
}
var x1=this._getPosition(_11.low);
var x2=this._getPosition(_11.high);
var _14=this.surface.createRect({x:x1,y:this.dataY,width:x2-x1,height:this.dataHeight});
if(dojo.isArray(_11.color)||dojo.isString(_11.color)){
_14.setStroke({color:_11.color});
_14.setFill(_11.color);
}else{
if(_11.color.type){
var y=this.dataY+this.dataHeight/2;
_11.color.x1=x1;
_11.color.x2=x2;
_11.color.y1=y;
_11.color.y2=y;
_14.setFill(_11.color);
_14.setStroke({color:_11.color.colors[0].color});
}else{
_14.setStroke({color:"green"});
_14.setFill("green");
_14.getEventSource().setAttribute("class",_11.color.style);
}
}
if(_11.hover){
_14.getEventSource().setAttribute("hover",_11.hover);
}
_11.shape=_14;
},getRangeUnderMouse:function(_16){
var _17=null;
var pos=dojo.coords(this.gaugeContent);
var x=_16.clientX-pos.x;
var _1a=this._getValueForPosition(x);
if(this._rangeData){
for(var i=0;(i<this._rangeData.length)&&!_17;i++){
if((Number(this._rangeData[i].low)<=_1a)&&(Number(this._rangeData[i].high)>=_1a)){
_17=this._rangeData[i];
}
}
}
return _17;
},_dragIndicator:function(_1c,_1d){
var pos=dojo.coords(_1c.gaugeContent);
var x=_1d.clientX-pos.x;
var _20=_1c._getValueForPosition(x);
if(_20<_1c.min){
_20=_1c.min;
}
if(_20>_1c.max){
_20=_1c.max;
}
_1c._drag.value=_20;
_1c._drag.onDragMove(_1c._drag);
_1c._drag.draw(true);
dojo.stopEvent(_1d);
}});
}
