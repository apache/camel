/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.gauge.BarIndicator"]){
dojo._hasResource["dojox.widget.gauge.BarIndicator"]=true;
dojo.provide("dojox.widget.gauge.BarIndicator");
dojo.require("dojox.widget.BarGauge");
dojo.experimental("dojox.widget.gauge.BarIndicator");
dojo.declare("dojox.widget.gauge.BarIndicator",[dojox.widget.gauge.BarLineIndicator],{_getShapes:function(){
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
if(_2==this.dataX){
_2=this.dataX+1;
}
var y=this._gauge.dataY+Math.floor((this._gauge.dataHeight-this.width)/2)+this.offset;
var _4=[];
_4[0]=this._gauge.surface.createRect({x:this._gauge.dataX,y:y,width:_2-this._gauge.dataX,height:this.width});
_4[0].setStroke({color:this.color});
_4[0].setFill(this.color);
_4[1]=this._gauge.surface.createLine({x1:this._gauge.dataX,y1:y,x2:_2,y2:y});
_4[1].setStroke({color:this.highlight});
if(this.highlight2){
y--;
_4[2]=this._gauge.surface.createLine({x1:this._gauge.dataX,y1:y,x2:_2,y2:y});
_4[2].setStroke({color:this.highlight2});
}
return _4;
},_createShapes:function(_5){
for(var i in this.shapes){
i=this.shapes[i];
var _7={};
for(var j in i){
_7[j]=i[j];
}
if(i.shape.type=="line"){
_7.shape.x2=_5+_7.shape.x1;
}else{
if(i.shape.type=="rect"){
_7.width=_5;
}
}
i.setShape(_7);
}
},_move:function(_9){
var _a=false;
var c;
var v=this.value;
if(v<this.min){
v=this.min;
}
if(v>this.max){
v=this.max;
}
c=this._gauge._getPosition(this.currentValue);
this.currentValue=v;
v=this._gauge._getPosition(v)-this._gauge.dataX;
if(_9){
this._createShapes(v);
}else{
if(c!=v){
var _d=new dojo._Animation({curve:[c,v],duration:this.duration,easing:this.easing});
dojo.connect(_d,"onAnimate",dojo.hitch(this,this._createShapes));
_d.play();
}
}
}});
}
