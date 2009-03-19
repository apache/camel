/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx.Shadow"]){
dojo._hasResource["dojox.fx.Shadow"]=true;
dojo.provide("dojox.fx.Shadow");
dojo.experimental("dojox.fx.Shadow");
dojo.require("dijit._Widget");
dojo.require("dojo.NodeList-fx");
dojo.declare("dojox.fx.Shadow",dijit._Widget,{shadowPng:dojo.moduleUrl("dojox.fx","resources/shadow"),shadowThickness:7,shadowOffset:3,opacity:0.75,animate:false,node:null,startup:function(){
this.inherited(arguments);
this.node.style.position="relative";
this.pieces={};
var x1=-1*this.shadowThickness;
var y0=this.shadowOffset;
var y1=this.shadowOffset+this.shadowThickness;
this._makePiece("tl","top",y0,"left",x1);
this._makePiece("l","top",y1,"left",x1,"scale");
this._makePiece("tr","top",y0,"left",0);
this._makePiece("r","top",y1,"left",0,"scale");
this._makePiece("bl","top",0,"left",x1);
this._makePiece("b","top",0,"left",0,"crop");
this._makePiece("br","top",0,"left",0);
this.nodeList=dojo.query(".shadowPiece",this.node);
this.setOpacity(this.opacity);
this.resize();
},_makePiece:function(_4,_5,_6,_7,_8,_9){
var _a;
var _b=this.shadowPng+_4.toUpperCase()+".png";
if(dojo.isIE<7){
_a=dojo.create("div");
_a.style.filter="progid:DXImageTransform.Microsoft.AlphaImageLoader(src='"+_b+"'"+(_9?", sizingMethod='"+_9+"'":"")+")";
}else{
_a=dojo.create("img",{src:_b});
}
_a.style.position="absolute";
_a.style[_5]=_6+"px";
_a.style[_7]=_8+"px";
_a.style.width=this.shadowThickness+"px";
_a.style.height=this.shadowThickness+"px";
dojo.addClass(_a,"shadowPiece");
this.pieces[_4]=_a;
this.node.appendChild(_a);
},setOpacity:function(n,_d){
if(dojo.isIE){
return;
}
if(!_d){
_d={};
}
if(this.animate){
var _e=[];
this.nodeList.forEach(function(_f){
_e.push(dojo._fade(dojo.mixin(_d,{node:_f,end:n})));
});
dojo.fx.combine(_e).play();
}else{
this.nodeList.style("opacity",n);
}
},setDisabled:function(_10){
if(_10){
if(this.disabled){
return;
}
if(this.animate){
this.nodeList.fadeOut().play();
}else{
this.nodeList.style("visibility","hidden");
}
this.disabled=true;
}else{
if(!this.disabled){
return;
}
if(this.animate){
this.nodeList.fadeIn().play();
}else{
this.nodeList.style("visibility","visible");
}
this.disabled=false;
}
},resize:function(_11){
var x;
var y;
if(_11){
x=_11.x;
y=_11.y;
}else{
var co=dojo._getBorderBox(this.node);
x=co.w;
y=co.h;
}
var _15=y-(this.shadowOffset+this.shadowThickness);
if(_15<0){
_15=0;
}
if(y<1){
y=1;
}
if(x<1){
x=1;
}
with(this.pieces){
l.style.height=_15+"px";
r.style.height=_15+"px";
b.style.width=x+"px";
bl.style.top=y+"px";
b.style.top=y+"px";
br.style.top=y+"px";
tr.style.left=x+"px";
r.style.left=x+"px";
br.style.left=x+"px";
}
}});
}
