/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.canvas"]){
dojo._hasResource["dojox.gfx.canvas"]=true;
dojo.provide("dojox.gfx.canvas");
dojo.require("dojox.gfx._base");
dojo.require("dojox.gfx.shape");
dojo.require("dojox.gfx.path");
dojo.require("dojox.gfx.arc");
dojo.require("dojox.gfx.decompose");
dojo.experimental("dojox.gfx.canvas");
(function(){
var g=dojox.gfx,gs=g.shape,ga=g.arc,m=g.matrix,mp=m.multiplyPoint,pi=Math.PI,_7=2*pi,_8=pi/2;
dojo.extend(g.Shape,{_render:function(_9){
_9.save();
this._renderTransform(_9);
this._renderShape(_9);
this._renderFill(_9,true);
this._renderStroke(_9,true);
_9.restore();
},_renderTransform:function(_a){
if("canvasTransform" in this){
var t=this.canvasTransform;
_a.translate(t.dx,t.dy);
_a.rotate(t.angle2);
_a.scale(t.sx,t.sy);
_a.rotate(t.angle1);
}
},_renderShape:function(_c){
},_renderFill:function(_d,_e){
if("canvasFill" in this){
if("canvasFillImage" in this){
this.canvasFill=_d.createPattern(this.canvasFillImage,"repeat");
delete this.canvasFillImage;
}
_d.fillStyle=this.canvasFill;
if(_e){
_d.fill();
}
}else{
_d.fillStyle="rgba(0,0,0,0.0)";
}
},_renderStroke:function(_f,_10){
var s=this.strokeStyle;
if(s){
_f.strokeStyle=s.color.toString();
_f.lineWidth=s.width;
_f.lineCap=s.cap;
if(typeof s.join=="number"){
_f.lineJoin="miter";
_f.miterLimit=s.join;
}else{
_f.lineJoin=s.join;
}
if(_10){
_f.stroke();
}
}else{
if(!_10){
_f.strokeStyle="rgba(0,0,0,0.0)";
}
}
},getEventSource:function(){
return null;
},connect:function(){
},disconnect:function(){
}});
var _12=function(_13,_14,_15){
var old=_13.prototype[_14];
_13.prototype[_14]=_15?function(){
this.surface.makeDirty();
old.apply(this,arguments);
_15.call(this);
return this;
}:function(){
this.surface.makeDirty();
return old.apply(this,arguments);
};
};
_12(g.Shape,"setTransform",function(){
if(this.matrix){
this.canvasTransform=g.decompose(this.matrix);
}else{
delete this.canvasTransform;
}
});
_12(g.Shape,"setFill",function(){
var fs=this.fillStyle,f;
if(fs){
if(typeof (fs)=="object"&&"type" in fs){
var ctx=this.surface.rawNode.getContext("2d");
switch(fs.type){
case "linear":
case "radial":
f=fs.type=="linear"?ctx.createLinearGradient(fs.x1,fs.y1,fs.x2,fs.y2):ctx.createRadialGradient(fs.cx,fs.cy,0,fs.cx,fs.cy,fs.r);
dojo.forEach(fs.colors,function(_1a){
f.addColorStop(_1a.offset,g.normalizeColor(_1a.color).toString());
});
break;
case "pattern":
var img=new Image(fs.width,fs.height);
this.surface.downloadImage(img,fs.src);
this.canvasFillImage=img;
}
}else{
f=fs.toString();
}
this.canvasFill=f;
}else{
delete this.canvasFill;
}
});
_12(g.Shape,"setStroke");
_12(g.Shape,"setShape");
dojo.declare("dojox.gfx.Group",g.Shape,{constructor:function(){
gs.Container._init.call(this);
},_render:function(ctx){
ctx.save();
this._renderTransform(ctx);
this._renderFill(ctx);
this._renderStroke(ctx);
for(var i=0;i<this.children.length;++i){
this.children[i]._render(ctx);
}
ctx.restore();
}});
dojo.declare("dojox.gfx.Rect",gs.Rect,{_renderShape:function(ctx){
var s=this.shape,r=Math.min(s.r,s.height/2,s.width/2),xl=s.x,xr=xl+s.width,yt=s.y,yb=yt+s.height,xl2=xl+r,xr2=xr-r,yt2=yt+r,yb2=yb-r;
ctx.beginPath();
ctx.moveTo(xl2,yt);
if(r){
ctx.arc(xr2,yt2,r,-_8,0,false);
ctx.arc(xr2,yb2,r,0,_8,false);
ctx.arc(xl2,yb2,r,_8,pi,false);
ctx.arc(xl2,yt2,r,pi,_8,false);
}else{
ctx.lineTo(xr2,yt);
ctx.lineTo(xr,yb2);
ctx.lineTo(xl2,yb);
ctx.lineTo(xl,yt2);
}
ctx.closePath();
}});
var _29=[];
(function(){
var u=ga.curvePI4;
_29.push(u.s,u.c1,u.c2,u.e);
for(var a=45;a<360;a+=45){
var r=m.rotateg(a);
_29.push(mp(r,u.c1),mp(r,u.c2),mp(r,u.e));
}
})();
dojo.declare("dojox.gfx.Ellipse",gs.Ellipse,{setShape:function(){
g.Ellipse.superclass.setShape.apply(this,arguments);
var s=this.shape,t,c1,c2,r=[],M=m.normalize([m.translate(s.cx,s.cy),m.scale(s.rx,s.ry)]);
t=mp(M,_29[0]);
r.push([t.x,t.y]);
for(var i=1;i<_29.length;i+=3){
c1=mp(M,_29[i]);
c2=mp(M,_29[i+1]);
t=mp(M,_29[i+2]);
r.push([c1.x,c1.y,c2.x,c2.y,t.x,t.y]);
}
this.canvasEllipse=r;
return this;
},_renderShape:function(ctx){
var r=this.canvasEllipse;
ctx.beginPath();
ctx.moveTo.apply(ctx,r[0]);
for(var i=1;i<r.length;++i){
ctx.bezierCurveTo.apply(ctx,r[i]);
}
ctx.closePath();
}});
dojo.declare("dojox.gfx.Circle",gs.Circle,{_renderShape:function(ctx){
var s=this.shape;
ctx.beginPath();
ctx.arc(s.cx,s.cy,s.r,0,_7,1);
}});
dojo.declare("dojox.gfx.Line",gs.Line,{_renderShape:function(ctx){
var s=this.shape;
ctx.beginPath();
ctx.moveTo(s.x1,s.y1);
ctx.lineTo(s.x2,s.y2);
}});
dojo.declare("dojox.gfx.Polyline",gs.Polyline,{setShape:function(){
g.Polyline.superclass.setShape.apply(this,arguments);
var p=this.shape.points,f=p[0],r=[],c,i;
if(p.length){
if(typeof f=="number"){
r.push(f,p[1]);
i=2;
}else{
r.push(f.x,f.y);
i=1;
}
for(;i<p.length;++i){
c=p[i];
if(typeof c=="number"){
r.push(c,p[++i]);
}else{
r.push(c.x,c.y);
}
}
}
this.canvasPolyline=r;
return this;
},_renderShape:function(ctx){
var p=this.canvasPolyline;
if(p.length){
ctx.beginPath();
ctx.moveTo(p[0],p[1]);
for(var i=2;i<p.length;i+=2){
ctx.lineTo(p[i],p[i+1]);
}
}
}});
dojo.declare("dojox.gfx.Image",gs.Image,{setShape:function(){
g.Image.superclass.setShape.apply(this,arguments);
var img=new Image();
this.surface.downloadImage(img,this.shape.src);
this.canvasImage=img;
return this;
},_renderShape:function(ctx){
var s=this.shape;
ctx.drawImage(this.canvasImage,s.x,s.y,s.width,s.height);
}});
dojo.declare("dojox.gfx.Text",gs.Text,{_renderShape:function(ctx){
var s=this.shape;
}});
_12(g.Text,"setFont");
var _48={M:"_moveToA",m:"_moveToR",L:"_lineToA",l:"_lineToR",H:"_hLineToA",h:"_hLineToR",V:"_vLineToA",v:"_vLineToR",C:"_curveToA",c:"_curveToR",S:"_smoothCurveToA",s:"_smoothCurveToR",Q:"_qCurveToA",q:"_qCurveToR",T:"_qSmoothCurveToA",t:"_qSmoothCurveToR",A:"_arcTo",a:"_arcTo",Z:"_closePath",z:"_closePath"};
dojo.declare("dojox.gfx.Path",g.path.Path,{constructor:function(){
this.lastControl={};
},setShape:function(){
this.canvasPath=[];
return g.Path.superclass.setShape.apply(this,arguments);
},_updateWithSegment:function(_49){
var _4a=dojo.clone(this.last);
this[_48[_49.action]](this.canvasPath,_49.action,_49.args);
this.last=_4a;
g.Path.superclass._updateWithSegment.apply(this,arguments);
},_renderShape:function(ctx){
var r=this.canvasPath;
ctx.beginPath();
for(var i=0;i<r.length;i+=2){
ctx[r[i]].apply(ctx,r[i+1]);
}
},_moveToA:function(_4e,_4f,_50){
_4e.push("moveTo",[_50[0],_50[1]]);
for(var i=2;i<_50.length;i+=2){
_4e.push("lineTo",[_50[i],_50[i+1]]);
}
this.last.x=_50[_50.length-2];
this.last.y=_50[_50.length-1];
this.lastControl={};
},_moveToR:function(_52,_53,_54){
if("x" in this.last){
_52.push("moveTo",[this.last.x+=_54[0],this.last.y+=_54[1]]);
}else{
_52.push("moveTo",[this.last.x=_54[0],this.last.y=_54[1]]);
}
for(var i=2;i<_54.length;i+=2){
_52.push("lineTo",[this.last.x+=_54[i],this.last.y+=_54[i+1]]);
}
this.lastControl={};
},_lineToA:function(_56,_57,_58){
for(var i=0;i<_58.length;i+=2){
_56.push("lineTo",[_58[i],_58[i+1]]);
}
this.last.x=_58[_58.length-2];
this.last.y=_58[_58.length-1];
this.lastControl={};
},_lineToR:function(_5a,_5b,_5c){
for(var i=0;i<_5c.length;i+=2){
_5a.push("lineTo",[this.last.x+=_5c[i],this.last.y+=_5c[i+1]]);
}
this.lastControl={};
},_hLineToA:function(_5e,_5f,_60){
for(var i=0;i<_60.length;++i){
_5e.push("lineTo",[_60[i],this.last.y]);
}
this.last.x=_60[_60.length-1];
this.lastControl={};
},_hLineToR:function(_62,_63,_64){
for(var i=0;i<_64.length;++i){
_62.push("lineTo",[this.last.x+=_64[i],this.last.y]);
}
this.lastControl={};
},_vLineToA:function(_66,_67,_68){
for(var i=0;i<_68.length;++i){
_66.push("lineTo",[this.last.x,_68[i]]);
}
this.last.y=_68[_68.length-1];
this.lastControl={};
},_vLineToR:function(_6a,_6b,_6c){
for(var i=0;i<_6c.length;++i){
_6a.push("lineTo",[this.last.x,this.last.y+=_6c[i]]);
}
this.lastControl={};
},_curveToA:function(_6e,_6f,_70){
for(var i=0;i<_70.length;i+=6){
_6e.push("bezierCurveTo",_70.slice(i,i+6));
}
this.last.x=_70[_70.length-2];
this.last.y=_70[_70.length-1];
this.lastControl.x=_70[_70.length-4];
this.lastControl.y=_70[_70.length-3];
this.lastControl.type="C";
},_curveToR:function(_72,_73,_74){
for(var i=0;i<_74.length;i+=6){
_72.push("bezierCurveTo",[this.last.x+_74[i],this.last.y+_74[i+1],this.lastControl.x=this.last.x+_74[i+2],this.lastControl.y=this.last.y+_74[i+3],this.last.x+_74[i+4],this.last.y+_74[i+5]]);
this.last.x+=_74[i+4];
this.last.y+=_74[i+5];
}
this.lastControl.type="C";
},_smoothCurveToA:function(_76,_77,_78){
for(var i=0;i<_78.length;i+=4){
var _7a=this.lastControl.type=="C";
_76.push("bezierCurveTo",[_7a?2*this.last.x-this.lastControl.x:this.last.x,_7a?2*this.last.y-this.lastControl.y:this.last.y,_78[i],_78[i+1],_78[i+2],_78[i+3]]);
this.lastControl.x=_78[i];
this.lastControl.y=_78[i+1];
this.lastControl.type="C";
}
this.last.x=_78[_78.length-2];
this.last.y=_78[_78.length-1];
},_smoothCurveToR:function(_7b,_7c,_7d){
for(var i=0;i<_7d.length;i+=4){
var _7f=this.lastControl.type=="C";
_7b.push("bezierCurveTo",[_7f?2*this.last.x-this.lastControl.x:this.last.x,_7f?2*this.last.y-this.lastControl.y:this.last.y,this.last.x+_7d[i],this.last.y+_7d[i+1],this.last.x+_7d[i+2],this.last.y+_7d[i+3]]);
this.lastControl.x=this.last.x+_7d[i];
this.lastControl.y=this.last.y+_7d[i+1];
this.lastControl.type="C";
this.last.x+=_7d[i+2];
this.last.y+=_7d[i+3];
}
},_qCurveToA:function(_80,_81,_82){
for(var i=0;i<_82.length;i+=4){
_80.push("quadraticCurveTo",_82.slice(i,i+4));
}
this.last.x=_82[_82.length-2];
this.last.y=_82[_82.length-1];
this.lastControl.x=_82[_82.length-4];
this.lastControl.y=_82[_82.length-3];
this.lastControl.type="Q";
},_qCurveToR:function(_84,_85,_86){
for(var i=0;i<_86.length;i+=4){
_84.push("quadraticCurveTo",[this.lastControl.x=this.last.x+_86[i],this.lastControl.y=this.last.y+_86[i+1],this.last.x+_86[i+2],this.last.y+_86[i+3]]);
this.last.x+=_86[i+2];
this.last.y+=_86[i+3];
}
this.lastControl.type="Q";
},_qSmoothCurveToA:function(_88,_89,_8a){
for(var i=0;i<_8a.length;i+=2){
var _8c=this.lastControl.type=="Q";
_88.push("quadraticCurveTo",[this.lastControl.x=_8c?2*this.last.x-this.lastControl.x:this.last.x,this.lastControl.y=_8c?2*this.last.y-this.lastControl.y:this.last.y,_8a[i],_8a[i+1]]);
this.lastControl.type="Q";
}
this.last.x=_8a[_8a.length-2];
this.last.y=_8a[_8a.length-1];
},_qSmoothCurveToR:function(_8d,_8e,_8f){
for(var i=0;i<_8f.length;i+=2){
var _91=this.lastControl.type=="Q";
_8d.push("quadraticCurveTo",[this.lastControl.x=_91?2*this.last.x-this.lastControl.x:this.last.x,this.lastControl.y=_91?2*this.last.y-this.lastControl.y:this.last.y,this.last.x+_8f[i],this.last.y+_8f[i+1]]);
this.lastControl.type="Q";
this.last.x+=_8f[i];
this.last.y+=_8f[i+1];
}
},_arcTo:function(_92,_93,_94){
var _95=_93=="a";
for(var i=0;i<_94.length;i+=7){
var x1=_94[i+5],y1=_94[i+6];
if(_95){
x1+=this.last.x;
y1+=this.last.y;
}
var _99=ga.arcAsBezier(this.last,_94[i],_94[i+1],_94[i+2],_94[i+3]?1:0,_94[i+4]?1:0,x1,y1);
dojo.forEach(_99,function(p){
_92.push("bezierCurveTo",p);
});
this.last.x=x1;
this.last.y=y1;
}
this.lastControl={};
},_closePath:function(_9b,_9c,_9d){
_9b.push("closePath",[]);
this.lastControl={};
}});
dojo.forEach(["moveTo","lineTo","hLineTo","vLineTo","curveTo","smoothCurveTo","qCurveTo","qSmoothCurveTo","arcTo","closePath"],function(_9e){
_12(g.Path,_9e);
});
dojo.declare("dojox.gfx.TextPath",g.path.TextPath,{_renderShape:function(ctx){
var s=this.shape;
}});
dojo.declare("dojox.gfx.Surface",gs.Surface,{constructor:function(){
gs.Container._init.call(this);
this.pendingImageCount=0;
this.makeDirty();
},setDimensions:function(_a1,_a2){
this.width=g.normalizedLength(_a1);
this.height=g.normalizedLength(_a2);
if(!this.rawNode){
return this;
}
this.rawNode.width=_a1;
this.rawNode.height=_a2;
this.makeDirty();
return this;
},getDimensions:function(){
return this.rawNode?{width:this.rawNode.width,height:this.rawNode.height}:null;
},_render:function(){
if(this.pendingImageCount){
return;
}
var ctx=this.rawNode.getContext("2d");
ctx.save();
ctx.clearRect(0,0,this.rawNode.width,this.rawNode.height);
for(var i=0;i<this.children.length;++i){
this.children[i]._render(ctx);
}
ctx.restore();
if("pendingRender" in this){
clearTimeout(this.pendingRender);
delete this.pendingRender;
}
},makeDirty:function(){
if(!this.pendingImagesCount&&!("pendingRender" in this)){
this.pendingRender=setTimeout(dojo.hitch(this,this._render),0);
}
},downloadImage:function(img,url){
var _a7=dojo.hitch(this,this.onImageLoad);
if(!this.pendingImageCount++&&"pendingRender" in this){
clearTimeout(this.pendingRender);
delete this.pendingRender;
}
img.onload=_a7;
img.onerror=_a7;
img.onabort=_a7;
img.src=url;
},onImageLoad:function(){
if(!--this.pendingImageCount){
this._render();
}
},getEventSource:function(){
return null;
},connect:function(){
},disconnect:function(){
}});
g.createSurface=function(_a8,_a9,_aa){
if(!_a9){
_a9="100%";
}
if(!_aa){
_aa="100%";
}
var s=new g.Surface(),p=dojo.byId(_a8),c=p.ownerDocument.createElement("canvas");
c.width=_a9;
c.height=_aa;
p.appendChild(c);
s.rawNode=c;
s._parent=p;
s.surface=s;
return s;
};
var C=gs.Container,_af={add:function(_b0){
this.surface.makeDirty();
return C.add.apply(this,arguments);
},remove:function(_b1,_b2){
this.surface.makeDirty();
return C.remove.apply(this,arguments);
},clear:function(){
this.surface.makeDirty();
return C.clear.apply(this,arguments);
},_moveChildToFront:function(_b3){
this.surface.makeDirty();
return C._moveChildToFront.apply(this,arguments);
},_moveChildToBack:function(_b4){
this.surface.makeDirty();
return C._moveChildToBack.apply(this,arguments);
}};
dojo.mixin(gs.Creator,{createObject:function(_b5,_b6){
var _b7=new _b5();
_b7.surface=this.surface;
_b7.setShape(_b6);
this.add(_b7);
return _b7;
}});
dojo.extend(g.Group,_af);
dojo.extend(g.Group,gs.Creator);
dojo.extend(g.Surface,_af);
dojo.extend(g.Surface,gs.Creator);
})();
}
