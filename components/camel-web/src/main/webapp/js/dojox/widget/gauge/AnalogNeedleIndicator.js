/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.gauge.AnalogNeedleIndicator"]){
dojo._hasResource["dojox.widget.gauge.AnalogNeedleIndicator"]=true;
dojo.provide("dojox.widget.gauge.AnalogNeedleIndicator");
dojo.require("dojox.widget.AnalogGauge");
dojo.experimental("dojox.widget.gauge.AnalogNeedleIndicator");
dojo.declare("dojox.widget.gauge.AnalogNeedleIndicator",[dojox.widget.gauge.AnalogLineIndicator],{_getShapes:function(){
if(!this._gauge){
return null;
}
var x=Math.floor(this.width/2);
var _2=this.width*5;
var _3=(this.width&1);
var _4=[];
var _5={color:this.color,width:1};
if(this.color.type){
_5.color=this.color.colors[0].color;
}
var xy=(Math.sqrt(2)*(x));
_4[0]=this._gauge.surface.createPath().setStroke(_5).setFill(this.color).moveTo(xy,-xy).arcTo((2*x),(2*x),0,0,0,-xy,-xy).lineTo(0,-this.length).closePath();
_4[1]=this._gauge.surface.createCircle({cx:0,cy:0,r:this.width}).setStroke({color:this.color}).setFill(this.color);
return _4;
}});
}
