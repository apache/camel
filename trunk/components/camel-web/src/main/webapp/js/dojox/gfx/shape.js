/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.shape"]){
dojo._hasResource["dojox.gfx.shape"]=true;
dojo.provide("dojox.gfx.shape");
dojo.require("dojox.gfx._base");
dojo.declare("dojox.gfx.Shape",null,{constructor:function(){
this.rawNode=null;
this.shape=null;
this.matrix=null;
this.fillStyle=null;
this.strokeStyle=null;
this.bbox=null;
this.parent=null;
this.parentMatrix=null;
},getNode:function(){
return this.rawNode;
},getShape:function(){
return this.shape;
},getTransform:function(){
return this.matrix;
},getFill:function(){
return this.fillStyle;
},getStroke:function(){
return this.strokeStyle;
},getParent:function(){
return this.parent;
},getBoundingBox:function(){
return this.bbox;
},getTransformedBoundingBox:function(){
var b=this.getBoundingBox();
if(!b){
return null;
}
var m=this._getRealMatrix();
var r=[];
var g=dojox.gfx.matrix;
r.push(g.multiplyPoint(m,b.x,b.y));
r.push(g.multiplyPoint(m,b.x+b.width,b.y));
r.push(g.multiplyPoint(m,b.x+b.width,b.y+b.height));
r.push(g.multiplyPoint(m,b.x,b.y+b.height));
return r;
},getEventSource:function(){
return this.rawNode;
},setShape:function(_5){
this.shape=dojox.gfx.makeParameters(this.shape,_5);
this.bbox=null;
return this;
},setFill:function(_6){
if(!_6){
this.fillStyle=null;
return this;
}
var f=null;
if(typeof (_6)=="object"&&"type" in _6){
switch(_6.type){
case "linear":
f=dojox.gfx.makeParameters(dojox.gfx.defaultLinearGradient,_6);
break;
case "radial":
f=dojox.gfx.makeParameters(dojox.gfx.defaultRadialGradient,_6);
break;
case "pattern":
f=dojox.gfx.makeParameters(dojox.gfx.defaultPattern,_6);
break;
}
}else{
f=dojox.gfx.normalizeColor(_6);
}
this.fillStyle=f;
return this;
},setStroke:function(_8){
if(!_8){
this.strokeStyle=null;
return this;
}
if(typeof _8=="string"||dojo.isArray(_8)||_8 instanceof dojo.Color){
_8={color:_8};
}
var s=this.strokeStyle=dojox.gfx.makeParameters(dojox.gfx.defaultStroke,_8);
s.color=dojox.gfx.normalizeColor(s.color);
return this;
},setTransform:function(_a){
this.matrix=dojox.gfx.matrix.clone(_a?dojox.gfx.matrix.normalize(_a):dojox.gfx.matrix.identity);
return this._applyTransform();
},_applyTransform:function(){
return this;
},moveToFront:function(){
var p=this.getParent();
if(p){
p._moveChildToFront(this);
this._moveToFront();
}
return this;
},moveToBack:function(){
var p=this.getParent();
if(p){
p._moveChildToBack(this);
this._moveToBack();
}
return this;
},_moveToFront:function(){
},_moveToBack:function(){
},applyRightTransform:function(_d){
return _d?this.setTransform([this.matrix,_d]):this;
},applyLeftTransform:function(_e){
return _e?this.setTransform([_e,this.matrix]):this;
},applyTransform:function(_f){
return _f?this.setTransform([this.matrix,_f]):this;
},removeShape:function(_10){
if(this.parent){
this.parent.remove(this,_10);
}
return this;
},_setParent:function(_11,_12){
this.parent=_11;
return this._updateParentMatrix(_12);
},_updateParentMatrix:function(_13){
this.parentMatrix=_13?dojox.gfx.matrix.clone(_13):null;
return this._applyTransform();
},_getRealMatrix:function(){
var m=this.matrix;
var p=this.parent;
while(p){
if(p.matrix){
m=dojox.gfx.matrix.multiply(p.matrix,m);
}
p=p.parent;
}
return m;
}});
dojox.gfx.shape._eventsProcessing={connect:function(_16,_17,_18){
return arguments.length>2?dojo.connect(this.getEventSource(),_16,_17,_18):dojo.connect(this.getEventSource(),_16,_17);
},disconnect:function(_19){
dojo.disconnect(_19);
}};
dojo.extend(dojox.gfx.Shape,dojox.gfx.shape._eventsProcessing);
dojox.gfx.shape.Container={_init:function(){
this.children=[];
},add:function(_1a){
var _1b=_1a.getParent();
if(_1b){
_1b.remove(_1a,true);
}
this.children.push(_1a);
return _1a._setParent(this,this._getRealMatrix());
},remove:function(_1c,_1d){
for(var i=0;i<this.children.length;++i){
if(this.children[i]==_1c){
if(_1d){
}else{
_1c.parent=null;
_1c.parentMatrix=null;
}
this.children.splice(i,1);
break;
}
}
return this;
},clear:function(){
this.children=[];
return this;
},_moveChildToFront:function(_1f){
for(var i=0;i<this.children.length;++i){
if(this.children[i]==_1f){
this.children.splice(i,1);
this.children.push(_1f);
break;
}
}
return this;
},_moveChildToBack:function(_21){
for(var i=0;i<this.children.length;++i){
if(this.children[i]==_21){
this.children.splice(i,1);
this.children.unshift(_21);
break;
}
}
return this;
}};
dojo.declare("dojox.gfx.shape.Surface",null,{constructor:function(){
this.rawNode=null;
this._parent=null;
this._nodes=[];
this._events=[];
},destroy:function(){
dojo.forEach(this._nodes,dojo.destroy);
this._nodes=[];
dojo.forEach(this._events,dojo.disconnect);
this._events=[];
this.rawNode=null;
if(dojo.isIE){
while(this._parent.lastChild){
dojo.destroy(this._parent.lastChild);
}
}else{
this._parent.innerHTML="";
}
this._parent=null;
},getEventSource:function(){
return this.rawNode;
},_getRealMatrix:function(){
return null;
},isLoaded:true,onLoad:function(_23){
},whenLoaded:function(_24,_25){
var f=dojo.hitch(_24,_25);
if(this.isLoaded){
f(this);
}else{
var h=dojo.connect(this,"onLoad",function(_28){
dojo.disconnect(h);
f(_28);
});
}
}});
dojo.extend(dojox.gfx.shape.Surface,dojox.gfx.shape._eventsProcessing);
dojo.declare("dojox.gfx.Point",null,{});
dojo.declare("dojox.gfx.Rectangle",null,{});
dojo.declare("dojox.gfx.shape.Rect",dojox.gfx.Shape,{constructor:function(_29){
this.shape=dojo.clone(dojox.gfx.defaultRect);
this.rawNode=_29;
},getBoundingBox:function(){
return this.shape;
}});
dojo.declare("dojox.gfx.shape.Ellipse",dojox.gfx.Shape,{constructor:function(_2a){
this.shape=dojo.clone(dojox.gfx.defaultEllipse);
this.rawNode=_2a;
},getBoundingBox:function(){
if(!this.bbox){
var _2b=this.shape;
this.bbox={x:_2b.cx-_2b.rx,y:_2b.cy-_2b.ry,width:2*_2b.rx,height:2*_2b.ry};
}
return this.bbox;
}});
dojo.declare("dojox.gfx.shape.Circle",dojox.gfx.Shape,{constructor:function(_2c){
this.shape=dojo.clone(dojox.gfx.defaultCircle);
this.rawNode=_2c;
},getBoundingBox:function(){
if(!this.bbox){
var _2d=this.shape;
this.bbox={x:_2d.cx-_2d.r,y:_2d.cy-_2d.r,width:2*_2d.r,height:2*_2d.r};
}
return this.bbox;
}});
dojo.declare("dojox.gfx.shape.Line",dojox.gfx.Shape,{constructor:function(_2e){
this.shape=dojo.clone(dojox.gfx.defaultLine);
this.rawNode=_2e;
},getBoundingBox:function(){
if(!this.bbox){
var _2f=this.shape;
this.bbox={x:Math.min(_2f.x1,_2f.x2),y:Math.min(_2f.y1,_2f.y2),width:Math.abs(_2f.x2-_2f.x1),height:Math.abs(_2f.y2-_2f.y1)};
}
return this.bbox;
}});
dojo.declare("dojox.gfx.shape.Polyline",dojox.gfx.Shape,{constructor:function(_30){
this.shape=dojo.clone(dojox.gfx.defaultPolyline);
this.rawNode=_30;
},setShape:function(_31,_32){
if(_31&&_31 instanceof Array){
dojox.gfx.Shape.prototype.setShape.call(this,{points:_31});
if(_32&&this.shape.points.length){
this.shape.points.push(this.shape.points[0]);
}
}else{
dojox.gfx.Shape.prototype.setShape.call(this,_31);
}
return this;
},getBoundingBox:function(){
if(!this.bbox&&this.shape.points.length){
var p=this.shape.points;
var l=p.length;
var t=p[0];
var _36={l:t.x,t:t.y,r:t.x,b:t.y};
for(var i=1;i<l;++i){
t=p[i];
if(_36.l>t.x){
_36.l=t.x;
}
if(_36.r<t.x){
_36.r=t.x;
}
if(_36.t>t.y){
_36.t=t.y;
}
if(_36.b<t.y){
_36.b=t.y;
}
}
this.bbox={x:_36.l,y:_36.t,width:_36.r-_36.l,height:_36.b-_36.t};
}
return this.bbox;
}});
dojo.declare("dojox.gfx.shape.Image",dojox.gfx.Shape,{constructor:function(_38){
this.shape=dojo.clone(dojox.gfx.defaultImage);
this.rawNode=_38;
},getBoundingBox:function(){
return this.shape;
},setStroke:function(){
return this;
},setFill:function(){
return this;
}});
dojo.declare("dojox.gfx.shape.Text",dojox.gfx.Shape,{constructor:function(_39){
this.fontStyle=null;
this.shape=dojo.clone(dojox.gfx.defaultText);
this.rawNode=_39;
},getFont:function(){
return this.fontStyle;
},setFont:function(_3a){
this.fontStyle=typeof _3a=="string"?dojox.gfx.splitFontString(_3a):dojox.gfx.makeParameters(dojox.gfx.defaultFont,_3a);
this._setFont();
return this;
}});
dojox.gfx.shape.Creator={createShape:function(_3b){
switch(_3b.type){
case dojox.gfx.defaultPath.type:
return this.createPath(_3b);
case dojox.gfx.defaultRect.type:
return this.createRect(_3b);
case dojox.gfx.defaultCircle.type:
return this.createCircle(_3b);
case dojox.gfx.defaultEllipse.type:
return this.createEllipse(_3b);
case dojox.gfx.defaultLine.type:
return this.createLine(_3b);
case dojox.gfx.defaultPolyline.type:
return this.createPolyline(_3b);
case dojox.gfx.defaultImage.type:
return this.createImage(_3b);
case dojox.gfx.defaultText.type:
return this.createText(_3b);
case dojox.gfx.defaultTextPath.type:
return this.createTextPath(_3b);
}
return null;
},createGroup:function(){
return this.createObject(dojox.gfx.Group);
},createRect:function(_3c){
return this.createObject(dojox.gfx.Rect,_3c);
},createEllipse:function(_3d){
return this.createObject(dojox.gfx.Ellipse,_3d);
},createCircle:function(_3e){
return this.createObject(dojox.gfx.Circle,_3e);
},createLine:function(_3f){
return this.createObject(dojox.gfx.Line,_3f);
},createPolyline:function(_40){
return this.createObject(dojox.gfx.Polyline,_40);
},createImage:function(_41){
return this.createObject(dojox.gfx.Image,_41);
},createText:function(_42){
return this.createObject(dojox.gfx.Text,_42);
},createPath:function(_43){
return this.createObject(dojox.gfx.Path,_43);
},createTextPath:function(_44){
return this.createObject(dojox.gfx.TextPath,{}).setText(_44);
},createObject:function(_45,_46){
return null;
}};
}
