/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.gauge.AnalogArcIndicator"]){
dojo._hasResource["dojox.widget.gauge.AnalogArcIndicator"]=true;
dojo.provide("dojox.widget.gauge.AnalogArcIndicator");
dojo.require("dojox.widget.AnalogGauge");
dojo.experimental("dojox.widget.gauge.AnalogArcIndicator");
dojo.declare("dojox.widget.gauge.AnalogArcIndicator",[dojox.widget.gauge.AnalogLineIndicator],{_createArc:function(_1){
if(this.shapes[0]){
var a=this._gauge._getRadians(this._gauge._getAngle(_1));
var _3=Math.cos(a);
var _4=Math.sin(a);
var sa=this._gauge._getRadians(this._gauge.startAngle);
var _6=Math.cos(sa);
var _7=Math.sin(sa);
var _8=this.offset+this.width;
var p=["M"];
p.push(this._gauge.cx+this.offset*_7);
p.push(this._gauge.cy-this.offset*_6);
p.push("A",this.offset,this.offset,0,((a-sa)>Math.PI)?1:0,1);
p.push(this._gauge.cx+this.offset*_4);
p.push(this._gauge.cy-this.offset*_3);
p.push("L");
p.push(this._gauge.cx+_8*_4);
p.push(this._gauge.cy-_8*_3);
p.push("A",_8,_8,0,((a-sa)>Math.PI)?1:0,0);
p.push(this._gauge.cx+_8*_7);
p.push(this._gauge.cy-_8*_6);
this.shapes[0].setShape(p.join(" "));
this.currentValue=_1;
}
},draw:function(_a){
var v=this.value;
if(v<this._gauge.min){
v=this._gauge.min;
}
if(v>this._gauge.max){
v=this._gauge.max;
}
if(this.shapes){
if(_a){
this._createArc(v);
}else{
var _c=new dojo._Animation({curve:[this.currentValue,v],duration:this.duration,easing:this.easing});
dojo.connect(_c,"onAnimate",dojo.hitch(this,this._createArc));
_c.play();
}
}else{
var _d={color:this.color,width:1};
if(this.color.type){
_d.color=this.color.colors[0].color;
}
this.shapes=[this._gauge.surface.createPath().setStroke(_d).setFill(this.color)];
this._createArc(v);
if(this.hover){
this.shapes[0].getEventSource().setAttribute("hover",this.hover);
}
if(this.onDragMove&&!this.noChange){
this._gauge.connect(this.shapes[0].getEventSource(),"onmousedown",this._gauge.handleMouseDown);
this.shapes[0].getEventSource().style.cursor="pointer";
}
}
}});
}
