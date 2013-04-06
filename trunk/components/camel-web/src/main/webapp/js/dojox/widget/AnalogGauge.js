/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.AnalogGauge"]){
dojo._hasResource["dojox.widget.AnalogGauge"]=true;
dojo.provide("dojox.widget.AnalogGauge");
dojo.require("dojox.gfx");
dojo.require("dojox.widget.gauge._Gauge");
dojo.experimental("dojox.widget.AnalogGauge");
dojo.declare("dojox.widget.gauge.AnalogLineIndicator",[dojox.widget.gauge._Indicator],{_getShapes:function(){
var _1=[];
_1[0]=this._gauge.surface.createLine({x1:0,y1:-this.offset,x2:0,y2:-this.length-this.offset}).setStroke({color:this.color,width:this.width});
return _1;
},draw:function(_2){
if(this.shapes){
this._move(_2);
}else{
if(this.text){
this._gauge.surface.rawNode.removeChild(this.text);
this.text=null;
}
var v=this.value;
if(v<this._gauge.min){
v=this._gauge.min;
}
if(v>this._gauge.max){
v=this._gauge.max;
}
var a=this._gauge._getAngle(v);
this.color=this.color||"#000000";
this.length=this.length||this._gauge.radius;
this.width=this.width||1;
this.offset=this.offset||0;
this.highlight=this.highlight||"#D0D0D0";
this.shapes=this._getShapes(this._gauge,this);
if(this.shapes){
for(var s=0;s<this.shapes.length;s++){
this.shapes[s].setTransform([{dx:this._gauge.cx,dy:this._gauge.cy},dojox.gfx.matrix.rotateg(a)]);
if(this.hover){
this.shapes[s].getEventSource().setAttribute("hover",this.hover);
}
if(this.onDragMove&&!this.noChange){
this._gauge.connect(this.shapes[s].getEventSource(),"onmousedown",this._gauge.handleMouseDown);
this.shapes[s].getEventSource().style.cursor="pointer";
}
}
}
if(this.label){
var _6=this.length+this.offset;
var x=this._gauge.cx+(_6+5)*Math.sin(this._gauge._getRadians(a));
var y=this._gauge.cy-(_6+5)*Math.cos(this._gauge._getRadians(a));
var _9="start";
if(a<=-10){
_9="end";
}
if(a>-10&&a<10){
_9="middle";
}
var _a="bottom";
if((a<-90)||(a>90)){
_a="top";
}
this.text=this._gauge.drawText(""+this.label,x,y,_9,_a,this.color,this.font);
}
this.currentValue=this.value;
}
},_move:function(_b){
var v=this.value;
if(v<this._gauge.min){
v=this._gauge.min;
}
if(v>this._gauge.max){
v=this._gauge.max;
}
var c=this.currentValue;
if(_b){
var _e=this._gauge._getAngle(v);
for(var i in this.shapes){
this.shapes[i].setTransform([{dx:this._gauge.cx,dy:this._gauge.cy},dojox.gfx.matrix.rotateg(_e)]);
if(this.hover){
this.shapes[i].getEventSource().setAttribute("hover",this.hover);
}
}
}else{
if(c!=v){
var _10=new dojo._Animation({curve:[c,v],duration:this.duration,easing:this.easing});
dojo.connect(_10,"onAnimate",dojo.hitch(this,function(_11){
for(var i in this.shapes){
this.shapes[i].setTransform([{dx:this._gauge.cx,dy:this._gauge.cy},dojox.gfx.matrix.rotateg(this._gauge._getAngle(_11))]);
if(this.hover){
this.shapes[i].getEventSource().setAttribute("hover",this.hover);
}
}
this.currentValue=_11;
}));
_10.play();
}
}
}});
dojo.declare("dojox.widget.AnalogGauge",dojox.widget.gauge._Gauge,{startAngle:-90,endAngle:90,cx:0,cy:0,radius:0,_defaultIndicator:dojox.widget.gauge.AnalogLineIndicator,startup:function(){
if(this.getChildren){
dojo.forEach(this.getChildren(),function(_13){
_13.startup();
});
}
this.startAngle=Number(this.startAngle);
this.endAngle=Number(this.endAngle);
this.cx=Number(this.cx);
if(!this.cx){
this.cx=this.width/2;
}
this.cy=Number(this.cy);
if(!this.cy){
this.cy=this.height/2;
}
this.radius=Number(this.radius);
if(!this.radius){
this.radius=Math.min(this.cx,this.cy)-25;
}
this._oppositeMiddle=(this.startAngle+this.endAngle)/2+180;
this.inherited(arguments);
},_getAngle:function(_14){
return (_14-this.min)/(this.max-this.min)*(this.endAngle-this.startAngle)+this.startAngle;
},_getValueForAngle:function(_15){
if(_15>this._oppositeMiddle){
_15-=360;
}
return (_15-this.startAngle)*(this.max-this.min)/(this.endAngle-this.startAngle)+this.min;
},_getRadians:function(_16){
return _16*Math.PI/180;
},_getDegrees:function(_17){
return _17*180/Math.PI;
},draw:function(){
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
},drawRange:function(_19){
var _1a;
if(_19.shape){
this.surface.remove(_19.shape);
_19.shape=null;
}
var a1;
var a2;
if((_19.low==this.min)&&(_19.high==this.max)&&((this.endAngle-this.startAngle)==360)){
_1a=this.surface.createCircle({cx:this.cx,cy:this.cy,r:this.radius});
}else{
a1=this._getRadians(this._getAngle(_19.low));
a2=this._getRadians(this._getAngle(_19.high));
var x1=this.cx+this.radius*Math.sin(a1);
var y1=this.cy-this.radius*Math.cos(a1);
var x2=this.cx+this.radius*Math.sin(a2);
var y2=this.cy-this.radius*Math.cos(a2);
var big=0;
if((a2-a1)>Math.PI){
big=1;
}
_1a=this.surface.createPath();
if(_19.size){
_1a.moveTo(this.cx+(this.radius-_19.size)*Math.sin(a1),this.cy-(this.radius-_19.size)*Math.cos(a1));
}else{
_1a.moveTo(this.cx,this.cy);
}
_1a.lineTo(x1,y1);
_1a.arcTo(this.radius,this.radius,0,big,1,x2,y2);
if(_19.size){
_1a.lineTo(this.cx+(this.radius-_19.size)*Math.sin(a2),this.cy-(this.radius-_19.size)*Math.cos(a2));
_1a.arcTo((this.radius-_19.size),(this.radius-_19.size),0,big,0,this.cx+(this.radius-_19.size)*Math.sin(a1),this.cy-(this.radius-_19.size)*Math.cos(a1));
}
_1a.closePath();
}
if(dojo.isArray(_19.color)||dojo.isString(_19.color)){
_1a.setStroke({color:_19.color});
_1a.setFill(_19.color);
}else{
if(_19.color.type){
a1=this._getRadians(this._getAngle(_19.low));
a2=this._getRadians(this._getAngle(_19.high));
_19.color.x1=this.cx+(this.radius*Math.sin(a1))/2;
_19.color.x2=this.cx+(this.radius*Math.sin(a2))/2;
_19.color.y1=this.cy-(this.radius*Math.cos(a1))/2;
_19.color.y2=this.cy-(this.radius*Math.cos(a2))/2;
_1a.setFill(_19.color);
_1a.setStroke({color:_19.color.colors[0].color});
}else{
_1a.setStroke({color:"green"});
_1a.setFill("green");
_1a.getEventSource().setAttribute("class",_19.color.style);
}
}
if(_19.hover){
_1a.getEventSource().setAttribute("hover",_19.hover);
}
_19.shape=_1a;
},getRangeUnderMouse:function(_22){
var _23=null;
var pos=dojo.coords(this.gaugeContent);
var x=_22.clientX-pos.x;
var y=_22.clientY-pos.y;
var r=Math.sqrt((y-this.cy)*(y-this.cy)+(x-this.cx)*(x-this.cx));
if(r<this.radius){
var _28=this._getDegrees(Math.atan2(y-this.cy,x-this.cx)+Math.PI/2);
var _29=this._getValueForAngle(_28);
if(this._rangeData){
for(var i=0;(i<this._rangeData.length)&&!_23;i++){
if((Number(this._rangeData[i].low)<=_29)&&(Number(this._rangeData[i].high)>=_29)){
_23=this._rangeData[i];
}
}
}
}
return _23;
},_dragIndicator:function(_2b,_2c){
var pos=dojo.coords(_2b.gaugeContent);
var x=_2c.clientX-pos.x;
var y=_2c.clientY-pos.y;
var _30=_2b._getDegrees(Math.atan2(y-_2b.cy,x-_2b.cx)+Math.PI/2);
var _31=_2b._getValueForAngle(_30);
if(_31<_2b.min){
_31=_2b.min;
}
if(_31>_2b.max){
_31=_2b.max;
}
_2b._drag.value=_31;
_2b._drag.currentValue=_31;
_2b._drag.onDragMove(_2b._drag);
_2b._drag.draw(true);
dojo.stopEvent(_2c);
}});
}
