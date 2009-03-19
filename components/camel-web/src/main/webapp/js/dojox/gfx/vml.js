/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.vml"]){
dojo._hasResource["dojox.gfx.vml"]=true;
dojo.provide("dojox.gfx.vml");
dojo.require("dojox.gfx._base");
dojo.require("dojox.gfx.shape");
dojo.require("dojox.gfx.path");
dojo.require("dojox.gfx.arc");
(function(){
var g=dojox.gfx,m=g.matrix,_3=g.vml,sh=g.shape;
_3.xmlns="urn:schemas-microsoft-com:vml";
_3.text_alignment={start:"left",middle:"center",end:"right"};
_3._parseFloat=function(_5){
return _5.match(/^\d+f$/i)?parseInt(_5)/65536:parseFloat(_5);
};
_3._bool={"t":1,"true":1};
dojo.extend(g.Shape,{setFill:function(_6){
if(!_6){
this.fillStyle=null;
this.rawNode.filled="f";
return this;
}
if(typeof _6=="object"&&"type" in _6){
var i,f,fo,a,s;
switch(_6.type){
case "linear":
var _c=this._getRealMatrix();
s=[];
f=g.makeParameters(g.defaultLinearGradient,_6);
a=f.colors;
this.fillStyle=f;
dojo.forEach(a,function(v,i,a){
a[i].color=g.normalizeColor(v.color);
});
if(a[0].offset>0){
s.push("0 "+a[0].color.toHex());
}
for(i=0;i<a.length;++i){
s.push(a[i].offset.toFixed(8)+" "+a[i].color.toHex());
}
i=a.length-1;
if(a[i].offset<1){
s.push("1 "+a[i].color.toHex());
}
fo=this.rawNode.fill;
fo.colors.value=s.join(";");
fo.method="sigma";
fo.type="gradient";
var fc1=_c?m.multiplyPoint(_c,f.x1,f.y1):{x:f.x1,y:f.y1},fc2=_c?m.multiplyPoint(_c,f.x2,f.y2):{x:f.x2,y:f.y2};
fo.angle=(m._radToDeg(Math.atan2(fc2.x-fc1.x,fc2.y-fc1.y))+180)%360;
fo.on=true;
break;
case "radial":
f=g.makeParameters(g.defaultRadialGradient,_6);
this.fillStyle=f;
var l=parseFloat(this.rawNode.style.left),t=parseFloat(this.rawNode.style.top),w=parseFloat(this.rawNode.style.width),h=parseFloat(this.rawNode.style.height),c=isNaN(w)?1:2*f.r/w;
a=[];
if(f.colors[0].offset>0){
a.push({offset:1,color:g.normalizeColor(f.colors[0].color)});
}
dojo.forEach(f.colors,function(v,i){
a.push({offset:1-v.offset*c,color:g.normalizeColor(v.color)});
});
i=a.length-1;
while(i>=0&&a[i].offset<0){
--i;
}
if(i<a.length-1){
var q=a[i],p=a[i+1];
p.color=dojo.blendColors(q.color,p.color,q.offset/(q.offset-p.offset));
p.offset=0;
while(a.length-i>2){
a.pop();
}
}
i=a.length-1,s=[];
if(a[i].offset>0){
s.push("0 "+a[i].color.toHex());
}
for(;i>=0;--i){
s.push(a[i].offset.toFixed(8)+" "+a[i].color.toHex());
}
fo=this.rawNode.fill;
fo.colors.value=s.join(";");
fo.method="sigma";
fo.type="gradientradial";
if(isNaN(w)||isNaN(h)||isNaN(l)||isNaN(t)){
fo.focusposition="0.5 0.5";
}else{
fo.focusposition=((f.cx-l)/w).toFixed(8)+" "+((f.cy-t)/h).toFixed(8);
}
fo.focussize="0 0";
fo.on=true;
break;
case "pattern":
f=g.makeParameters(g.defaultPattern,_6);
this.fillStyle=f;
fo=this.rawNode.fill;
fo.type="tile";
fo.src=f.src;
if(f.width&&f.height){
fo.size.x=g.px2pt(f.width);
fo.size.y=g.px2pt(f.height);
}
fo.alignShape="f";
fo.position.x=0;
fo.position.y=0;
fo.origin.x=f.width?f.x/f.width:0;
fo.origin.y=f.height?f.y/f.height:0;
fo.on=true;
break;
}
this.rawNode.fill.opacity=1;
return this;
}
this.fillStyle=g.normalizeColor(_6);
this.rawNode.fill.method="any";
this.rawNode.fill.type="solid";
this.rawNode.fillcolor=this.fillStyle.toHex();
this.rawNode.fill.opacity=this.fillStyle.a;
this.rawNode.filled=true;
return this;
},setStroke:function(_1b){
if(!_1b){
this.strokeStyle=null;
this.rawNode.stroked="f";
return this;
}
if(typeof _1b=="string"||dojo.isArray(_1b)||_1b instanceof dojo.Color){
_1b={color:_1b};
}
var s=this.strokeStyle=g.makeParameters(g.defaultStroke,_1b);
s.color=g.normalizeColor(s.color);
var rn=this.rawNode;
rn.stroked=true;
rn.strokecolor=s.color.toCss();
rn.strokeweight=s.width+"px";
if(rn.stroke){
rn.stroke.opacity=s.color.a;
rn.stroke.endcap=this._translate(this._capMap,s.cap);
if(typeof s.join=="number"){
rn.stroke.joinstyle="miter";
rn.stroke.miterlimit=s.join;
}else{
rn.stroke.joinstyle=s.join;
}
rn.stroke.dashstyle=s.style=="none"?"Solid":s.style;
}
return this;
},_capMap:{butt:"flat"},_capMapReversed:{flat:"butt"},_translate:function(_1e,_1f){
return (_1f in _1e)?_1e[_1f]:_1f;
},_applyTransform:function(){
if(this.fillStyle&&this.fillStyle.type=="linear"){
this.setFill(this.fillStyle);
}
var _20=this._getRealMatrix();
if(!_20){
return this;
}
var _21=this.rawNode.skew;
if(typeof _21=="undefined"){
for(var i=0;i<this.rawNode.childNodes.length;++i){
if(this.rawNode.childNodes[i].tagName=="skew"){
_21=this.rawNode.childNodes[i];
break;
}
}
}
if(_21){
_21.on="f";
var mt=_20.xx.toFixed(8)+" "+_20.xy.toFixed(8)+" "+_20.yx.toFixed(8)+" "+_20.yy.toFixed(8)+" 0 0",_24=Math.floor(_20.dx).toFixed()+"px "+Math.floor(_20.dy).toFixed()+"px",s=this.rawNode.style,l=parseFloat(s.left),t=parseFloat(s.top),w=parseFloat(s.width),h=parseFloat(s.height);
if(isNaN(l)){
l=0;
}
if(isNaN(t)){
t=0;
}
if(isNaN(w)){
w=1;
}
if(isNaN(h)){
h=1;
}
var _2a=(-l/w-0.5).toFixed(8)+" "+(-t/h-0.5).toFixed(8);
_21.matrix=mt;
_21.origin=_2a;
_21.offset=_24;
_21.on=true;
}
return this;
},_setDimensions:function(_2b,_2c){
return this;
},setRawNode:function(_2d){
_2d.stroked="f";
_2d.filled="f";
this.rawNode=_2d;
},_moveToFront:function(){
this.rawNode.parentNode.appendChild(this.rawNode);
return this;
},_moveToBack:function(){
var r=this.rawNode,p=r.parentNode,n=p.firstChild;
p.insertBefore(r,n);
if(n.tagName=="rect"){
n.swapNode(r);
}
return this;
},_getRealMatrix:function(){
return this.parentMatrix?new g.Matrix2D([this.parentMatrix,this.matrix]):this.matrix;
}});
dojo.declare("dojox.gfx.Group",dojox.gfx.Shape,{constructor:function(){
_3.Container._init.call(this);
},_applyTransform:function(){
var _31=this._getRealMatrix();
for(var i=0;i<this.children.length;++i){
this.children[i]._updateParentMatrix(_31);
}
return this;
},_setDimensions:function(_33,_34){
var r=this.rawNode,rs=r.style,bs=this.bgNode.style;
rs.width=_33;
rs.height=_34;
r.coordsize=_33+" "+_34;
bs.width=_33;
bs.height=_34;
for(var i=0;i<this.children.length;++i){
this.children[i]._setDimensions(_33,_34);
}
return this;
}});
g.Group.nodeType="group";
dojo.declare("dojox.gfx.Rect",dojox.gfx.shape.Rect,{setShape:function(_39){
var _3a=this.shape=g.makeParameters(this.shape,_39);
this.bbox=null;
var _3b=this.rawNode.style;
_3b.left=_3a.x.toFixed();
_3b.top=_3a.y.toFixed();
_3b.width=(typeof _3a.width=="string"&&_3a.width.indexOf("%")>=0)?_3a.width:_3a.width.toFixed();
_3b.height=(typeof _3a.width=="string"&&_3a.height.indexOf("%")>=0)?_3a.height:_3a.height.toFixed();
var r=Math.min(1,(_3a.r/Math.min(parseFloat(_3a.width),parseFloat(_3a.height)))).toFixed(8);
var _3d=this.rawNode.parentNode,_3e=null;
if(_3d){
if(_3d.lastChild!=this.rawNode){
for(var i=0;i<_3d.childNodes.length;++i){
if(_3d.childNodes[i]==this.rawNode){
_3e=_3d.childNodes[i+1];
break;
}
}
}
_3d.removeChild(this.rawNode);
}
this.rawNode.arcsize=r;
if(_3d){
if(_3e){
_3d.insertBefore(this.rawNode,_3e);
}else{
_3d.appendChild(this.rawNode);
}
}
return this.setTransform(this.matrix).setFill(this.fillStyle).setStroke(this.strokeStyle);
}});
g.Rect.nodeType="roundrect";
dojo.declare("dojox.gfx.Ellipse",dojox.gfx.shape.Ellipse,{setShape:function(_40){
var _41=this.shape=g.makeParameters(this.shape,_40);
this.bbox=null;
var _42=this.rawNode.style;
_42.left=(_41.cx-_41.rx).toFixed();
_42.top=(_41.cy-_41.ry).toFixed();
_42.width=(_41.rx*2).toFixed();
_42.height=(_41.ry*2).toFixed();
return this.setTransform(this.matrix);
}});
g.Ellipse.nodeType="oval";
dojo.declare("dojox.gfx.Circle",dojox.gfx.shape.Circle,{setShape:function(_43){
var _44=this.shape=g.makeParameters(this.shape,_43);
this.bbox=null;
var _45=this.rawNode.style;
_45.left=(_44.cx-_44.r).toFixed();
_45.top=(_44.cy-_44.r).toFixed();
_45.width=(_44.r*2).toFixed();
_45.height=(_44.r*2).toFixed();
return this;
}});
g.Circle.nodeType="oval";
dojo.declare("dojox.gfx.Line",dojox.gfx.shape.Line,{constructor:function(_46){
if(_46){
_46.setAttribute("dojoGfxType","line");
}
},setShape:function(_47){
var _48=this.shape=g.makeParameters(this.shape,_47);
this.bbox=null;
this.rawNode.path.v="m"+_48.x1.toFixed()+" "+_48.y1.toFixed()+"l"+_48.x2.toFixed()+" "+_48.y2.toFixed()+"e";
return this.setTransform(this.matrix);
}});
g.Line.nodeType="shape";
dojo.declare("dojox.gfx.Polyline",dojox.gfx.shape.Polyline,{constructor:function(_49){
if(_49){
_49.setAttribute("dojoGfxType","polyline");
}
},setShape:function(_4a,_4b){
if(_4a&&_4a instanceof Array){
this.shape=g.makeParameters(this.shape,{points:_4a});
if(_4b&&this.shape.points.length){
this.shape.points.push(this.shape.points[0]);
}
}else{
this.shape=g.makeParameters(this.shape,_4a);
}
this.bbox=null;
var _4c=[],p=this.shape.points;
if(p.length>0){
_4c.push("m");
var k=1;
if(typeof p[0]=="number"){
_4c.push(p[0].toFixed());
_4c.push(p[1].toFixed());
k=2;
}else{
_4c.push(p[0].x.toFixed());
_4c.push(p[0].y.toFixed());
}
if(p.length>k){
_4c.push("l");
for(var i=k;i<p.length;++i){
if(typeof p[i]=="number"){
_4c.push(p[i].toFixed());
}else{
_4c.push(p[i].x.toFixed());
_4c.push(p[i].y.toFixed());
}
}
}
}
_4c.push("e");
this.rawNode.path.v=_4c.join(" ");
return this.setTransform(this.matrix);
}});
g.Polyline.nodeType="shape";
dojo.declare("dojox.gfx.Image",dojox.gfx.shape.Image,{setShape:function(_50){
var _51=this.shape=g.makeParameters(this.shape,_50);
this.bbox=null;
this.rawNode.firstChild.src=_51.src;
return this.setTransform(this.matrix);
},_applyTransform:function(){
var _52=this._getRealMatrix(),_53=this.rawNode,s=_53.style,_55=this.shape;
if(_52){
_52=m.multiply(_52,{dx:_55.x,dy:_55.y});
}else{
_52=m.normalize({dx:_55.x,dy:_55.y});
}
if(_52.xy==0&&_52.yx==0&&_52.xx>0&&_52.yy>0){
s.filter="";
s.width=Math.floor(_52.xx*_55.width);
s.height=Math.floor(_52.yy*_55.height);
s.left=Math.floor(_52.dx);
s.top=Math.floor(_52.dy);
}else{
var ps=_53.parentNode.style;
s.left="0px";
s.top="0px";
s.width=ps.width;
s.height=ps.height;
_52=m.multiply(_52,{xx:_55.width/parseInt(s.width),yy:_55.height/parseInt(s.height)});
var f=_53.filters["DXImageTransform.Microsoft.Matrix"];
if(f){
f.M11=_52.xx;
f.M12=_52.xy;
f.M21=_52.yx;
f.M22=_52.yy;
f.Dx=_52.dx;
f.Dy=_52.dy;
}else{
s.filter="progid:DXImageTransform.Microsoft.Matrix(M11="+_52.xx+", M12="+_52.xy+", M21="+_52.yx+", M22="+_52.yy+", Dx="+_52.dx+", Dy="+_52.dy+")";
}
}
return this;
},_setDimensions:function(_58,_59){
var r=this.rawNode,f=r.filters["DXImageTransform.Microsoft.Matrix"];
if(f){
var s=r.style;
s.width=_58;
s.height=_59;
return this._applyTransform();
}
return this;
}});
g.Image.nodeType="rect";
dojo.declare("dojox.gfx.Text",dojox.gfx.shape.Text,{constructor:function(_5d){
if(_5d){
_5d.setAttribute("dojoGfxType","text");
}
this.fontStyle=null;
},_alignment:{start:"left",middle:"center",end:"right"},setShape:function(_5e){
this.shape=g.makeParameters(this.shape,_5e);
this.bbox=null;
var r=this.rawNode,s=this.shape,x=s.x,y=s.y.toFixed();
switch(s.align){
case "middle":
x-=5;
break;
case "end":
x-=10;
break;
}
this.rawNode.path.v="m"+x.toFixed()+","+y+"l"+(x+10).toFixed()+","+y+"e";
var p=null,t=null,c=r.childNodes;
for(var i=0;i<c.length;++i){
var tag=c[i].tagName;
if(tag=="path"){
p=c[i];
if(t){
break;
}
}else{
if(tag=="textpath"){
t=c[i];
if(p){
break;
}
}
}
}
if(!p){
p=this.rawNode.ownerDocument.createElement("v:path");
r.appendChild(p);
}
if(!t){
t=this.rawNode.ownerDocument.createElement("v:textpath");
r.appendChild(t);
}
p.textPathOk=true;
t.on=true;
var a=_3.text_alignment[s.align];
t.style["v-text-align"]=a?a:"left";
t.style["text-decoration"]=s.decoration;
t.style["v-rotate-letters"]=s.rotated;
t.style["v-text-kern"]=s.kerning;
t.string=s.text;
return this.setTransform(this.matrix);
},_setFont:function(){
var f=this.fontStyle,c=this.rawNode.childNodes;
for(var i=0;i<c.length;++i){
if(c[i].tagName=="textpath"){
c[i].style.font=g.makeFontString(f);
break;
}
}
this.setTransform(this.matrix);
},_getRealMatrix:function(){
var _6c=g.Shape.prototype._getRealMatrix.call(this);
if(_6c){
_6c=m.multiply(_6c,{dy:-g.normalizedLength(this.fontStyle?this.fontStyle.size:"10pt")*0.35});
}
return _6c;
},getTextWidth:function(){
var _6d=this.rawNode,_6e=_6d.style.display;
_6d.style.display="inline";
var _6f=g.pt2px(parseFloat(_6d.currentStyle.width));
_6d.style.display=_6e;
return _6f;
}});
g.Text.nodeType="shape";
g.path._calcArc=function(_70){
var _71=Math.cos(_70),_72=Math.sin(_70),p2={x:_71+(4/3)*(1-_71),y:_72-(4/3)*_71*(1-_71)/_72};
return {s:{x:_71,y:-_72},c1:{x:p2.x,y:-p2.y},c2:p2,e:{x:_71,y:_72}};
};
dojo.declare("dojox.gfx.Path",dojox.gfx.path.Path,{constructor:function(_74){
if(_74&&!_74.getAttribute("dojoGfxType")){
_74.setAttribute("dojoGfxType","path");
}
this.vmlPath="";
this.lastControl={};
},_updateWithSegment:function(_75){
var _76=dojo.clone(this.last);
g.Path.superclass._updateWithSegment.apply(this,arguments);
var _77=this[this.renderers[_75.action]](_75,_76);
if(typeof this.vmlPath=="string"){
this.vmlPath+=_77.join("");
this.rawNode.path.v=this.vmlPath+" r0,0 e";
}else{
Array.prototype.push.apply(this.vmlPath,_77);
}
},setShape:function(_78){
this.vmlPath=[];
this.lastControl.type="";
g.Path.superclass.setShape.apply(this,arguments);
this.vmlPath=this.vmlPath.join("");
this.rawNode.path.v=this.vmlPath+" r0,0 e";
return this;
},_pathVmlToSvgMap:{m:"M",l:"L",t:"m",r:"l",c:"C",v:"c",qb:"Q",x:"z",e:""},renderers:{M:"_moveToA",m:"_moveToR",L:"_lineToA",l:"_lineToR",H:"_hLineToA",h:"_hLineToR",V:"_vLineToA",v:"_vLineToR",C:"_curveToA",c:"_curveToR",S:"_smoothCurveToA",s:"_smoothCurveToR",Q:"_qCurveToA",q:"_qCurveToR",T:"_qSmoothCurveToA",t:"_qSmoothCurveToR",A:"_arcTo",a:"_arcTo",Z:"_closePath",z:"_closePath"},_addArgs:function(_79,_7a,_7b,_7c){
var n=_7a instanceof Array?_7a:_7a.args;
for(var i=_7b;i<_7c;++i){
_79.push(" ",n[i].toFixed());
}
},_adjustRelCrd:function(_7f,_80,_81){
var n=_80 instanceof Array?_80:_80.args,l=n.length,_84=new Array(l),i=0,x=_7f.x,y=_7f.y;
if(typeof x!="number"){
_84[0]=x=n[0];
_84[1]=y=n[1];
i=2;
}
if(typeof _81=="number"&&_81!=2){
var j=_81;
while(j<=l){
for(;i<j;i+=2){
_84[i]=x+n[i];
_84[i+1]=y+n[i+1];
}
x=_84[j-2];
y=_84[j-1];
j+=_81;
}
}else{
for(;i<l;i+=2){
_84[i]=(x+=n[i]);
_84[i+1]=(y+=n[i+1]);
}
}
return _84;
},_adjustRelPos:function(_89,_8a){
var n=_8a instanceof Array?_8a:_8a.args,l=n.length,_8d=new Array(l);
for(var i=0;i<l;++i){
_8d[i]=(_89+=n[i]);
}
return _8d;
},_moveToA:function(_8f){
var p=[" m"],n=_8f instanceof Array?_8f:_8f.args,l=n.length;
this._addArgs(p,n,0,2);
if(l>2){
p.push(" l");
this._addArgs(p,n,2,l);
}
this.lastControl.type="";
return p;
},_moveToR:function(_93,_94){
return this._moveToA(this._adjustRelCrd(_94,_93));
},_lineToA:function(_95){
var p=[" l"],n=_95 instanceof Array?_95:_95.args;
this._addArgs(p,n,0,n.length);
this.lastControl.type="";
return p;
},_lineToR:function(_98,_99){
return this._lineToA(this._adjustRelCrd(_99,_98));
},_hLineToA:function(_9a,_9b){
var p=[" l"],y=" "+_9b.y.toFixed(),n=_9a instanceof Array?_9a:_9a.args,l=n.length;
for(var i=0;i<l;++i){
p.push(" ",n[i].toFixed(),y);
}
this.lastControl.type="";
return p;
},_hLineToR:function(_a1,_a2){
return this._hLineToA(this._adjustRelPos(_a2.x,_a1),_a2);
},_vLineToA:function(_a3,_a4){
var p=[" l"],x=" "+_a4.x.toFixed(),n=_a3 instanceof Array?_a3:_a3.args,l=n.length;
for(var i=0;i<l;++i){
p.push(x," ",n[i].toFixed());
}
this.lastControl.type="";
return p;
},_vLineToR:function(_aa,_ab){
return this._vLineToA(this._adjustRelPos(_ab.y,_aa),_ab);
},_curveToA:function(_ac){
var p=[],n=_ac instanceof Array?_ac:_ac.args,l=n.length,lc=this.lastControl;
for(var i=0;i<l;i+=6){
p.push(" c");
this._addArgs(p,n,i,i+6);
}
lc.x=n[l-4];
lc.y=n[l-3];
lc.type="C";
return p;
},_curveToR:function(_b2,_b3){
return this._curveToA(this._adjustRelCrd(_b3,_b2,6));
},_smoothCurveToA:function(_b4,_b5){
var p=[],n=_b4 instanceof Array?_b4:_b4.args,l=n.length,lc=this.lastControl,i=0;
if(lc.type!="C"){
p.push(" c");
this._addArgs(p,[_b5.x,_b5.y],0,2);
this._addArgs(p,n,0,4);
lc.x=n[0];
lc.y=n[1];
lc.type="C";
i=4;
}
for(;i<l;i+=4){
p.push(" c");
this._addArgs(p,[2*_b5.x-lc.x,2*_b5.y-lc.y],0,2);
this._addArgs(p,n,i,i+4);
lc.x=n[i];
lc.y=n[i+1];
}
return p;
},_smoothCurveToR:function(_bb,_bc){
return this._smoothCurveToA(this._adjustRelCrd(_bc,_bb,4),_bc);
},_qCurveToA:function(_bd){
var p=[],n=_bd instanceof Array?_bd:_bd.args,l=n.length,lc=this.lastControl;
for(var i=0;i<l;i+=4){
p.push(" qb");
this._addArgs(p,n,i,i+4);
}
lc.x=n[l-4];
lc.y=n[l-3];
lc.type="Q";
return p;
},_qCurveToR:function(_c3,_c4){
return this._qCurveToA(this._adjustRelCrd(_c4,_c3,4));
},_qSmoothCurveToA:function(_c5,_c6){
var p=[],n=_c5 instanceof Array?_c5:_c5.args,l=n.length,lc=this.lastControl,i=0;
if(lc.type!="Q"){
p.push(" qb");
this._addArgs(p,[lc.x=_c6.x,lc.y=_c6.y],0,2);
lc.type="Q";
this._addArgs(p,n,0,2);
i=2;
}
for(;i<l;i+=2){
p.push(" qb");
this._addArgs(p,[lc.x=2*_c6.x-lc.x,lc.y=2*_c6.y-lc.y],0,2);
this._addArgs(p,n,i,i+2);
}
return p;
},_qSmoothCurveToR:function(_cc,_cd){
return this._qSmoothCurveToA(this._adjustRelCrd(_cd,_cc,2),_cd);
},_arcTo:function(_ce,_cf){
var p=[],n=_ce.args,l=n.length,_d3=_ce.action=="a";
for(var i=0;i<l;i+=7){
var x1=n[i+5],y1=n[i+6];
if(_d3){
x1+=_cf.x;
y1+=_cf.y;
}
var _d7=g.arc.arcAsBezier(_cf,n[i],n[i+1],n[i+2],n[i+3]?1:0,n[i+4]?1:0,x1,y1);
for(var j=0;j<_d7.length;++j){
p.push(" c");
var t=_d7[j];
this._addArgs(p,t,0,t.length);
}
_cf.x=x1;
_cf.y=y1;
}
this.lastControl.type="";
return p;
},_closePath:function(){
this.lastControl.type="";
return ["x"];
}});
g.Path.nodeType="shape";
dojo.declare("dojox.gfx.TextPath",dojox.gfx.Path,{constructor:function(_da){
if(_da){
_da.setAttribute("dojoGfxType","textpath");
}
this.fontStyle=null;
if(!("text" in this)){
this.text=dojo.clone(g.defaultTextPath);
}
if(!("fontStyle" in this)){
this.fontStyle=dojo.clone(g.defaultFont);
}
},setText:function(_db){
this.text=g.makeParameters(this.text,typeof _db=="string"?{text:_db}:_db);
this._setText();
return this;
},setFont:function(_dc){
this.fontStyle=typeof _dc=="string"?g.splitFontString(_dc):g.makeParameters(g.defaultFont,_dc);
this._setFont();
return this;
},_setText:function(){
this.bbox=null;
var r=this.rawNode,s=this.text,p=null,t=null,c=r.childNodes;
for(var i=0;i<c.length;++i){
var tag=c[i].tagName;
if(tag=="path"){
p=c[i];
if(t){
break;
}
}else{
if(tag=="textpath"){
t=c[i];
if(p){
break;
}
}
}
}
if(!p){
p=this.rawNode.ownerDocument.createElement("v:path");
r.appendChild(p);
}
if(!t){
t=this.rawNode.ownerDocument.createElement("v:textpath");
r.appendChild(t);
}
p.textPathOk=true;
t.on=true;
var a=_3.text_alignment[s.align];
t.style["v-text-align"]=a?a:"left";
t.style["text-decoration"]=s.decoration;
t.style["v-rotate-letters"]=s.rotated;
t.style["v-text-kern"]=s.kerning;
t.string=s.text;
},_setFont:function(){
var f=this.fontStyle,c=this.rawNode.childNodes;
for(var i=0;i<c.length;++i){
if(c[i].tagName=="textpath"){
c[i].style.font=g.makeFontString(f);
break;
}
}
}});
g.TextPath.nodeType="shape";
dojo.declare("dojox.gfx.Surface",dojox.gfx.shape.Surface,{constructor:function(){
_3.Container._init.call(this);
},setDimensions:function(_e8,_e9){
this.width=g.normalizedLength(_e8);
this.height=g.normalizedLength(_e9);
if(!this.rawNode){
return this;
}
var cs=this.clipNode.style,r=this.rawNode,rs=r.style,bs=this.bgNode.style,ps=this._parent.style,i;
ps.width=_e8;
ps.height=_e9;
cs.width=_e8;
cs.height=_e9;
cs.clip="rect(0px "+_e8+"px "+_e9+"px 0px)";
rs.width=_e8;
rs.height=_e9;
r.coordsize=_e8+" "+_e9;
bs.width=_e8;
bs.height=_e9;
for(i=0;i<this.children.length;++i){
this.children[i]._setDimensions(_e8,_e9);
}
return this;
},getDimensions:function(){
var t=this.rawNode?{width:g.normalizedLength(this.rawNode.style.width),height:g.normalizedLength(this.rawNode.style.height)}:null;
if(t.width<=0){
t.width=this.width;
}
if(t.height<=0){
t.height=this.height;
}
return t;
}});
dojox.gfx.createSurface=function(_f1,_f2,_f3){
if(!_f2){
_f2="100%";
}
if(!_f3){
_f3="100%";
}
var s=new g.Surface(),p=dojo.byId(_f1),c=s.clipNode=p.ownerDocument.createElement("div"),r=s.rawNode=p.ownerDocument.createElement("v:group"),cs=c.style,rs=r.style;
s._parent=p;
s._nodes.push(c);
p.style.width=_f2;
p.style.height=_f3;
cs.position="absolute";
cs.width=_f2;
cs.height=_f3;
cs.clip="rect(0px "+_f2+"px "+_f3+"px 0px)";
rs.position="absolute";
rs.width=_f2;
rs.height=_f3;
r.coordsize=(_f2=="100%"?_f2:parseFloat(_f2))+" "+(_f3=="100%"?_f3:parseFloat(_f3));
r.coordorigin="0 0";
var b=s.bgNode=r.ownerDocument.createElement("v:rect"),bs=b.style;
bs.left=bs.top=0;
bs.width=rs.width;
bs.height=rs.height;
b.filled=b.stroked="f";
r.appendChild(b);
c.appendChild(r);
p.appendChild(c);
s.width=g.normalizedLength(_f2);
s.height=g.normalizedLength(_f3);
return s;
};
_3.Container={_init:function(){
sh.Container._init.call(this);
},add:function(_fc){
if(this!=_fc.getParent()){
this.rawNode.appendChild(_fc.rawNode);
if(!_fc.getParent()){
_fc.setFill(_fc.getFill());
_fc.setStroke(_fc.getStroke());
}
sh.Container.add.apply(this,arguments);
}
return this;
},remove:function(_fd,_fe){
if(this==_fd.getParent()){
if(this.rawNode==_fd.rawNode.parentNode){
this.rawNode.removeChild(_fd.rawNode);
}
sh.Container.remove.apply(this,arguments);
}
return this;
},clear:function(){
var r=this.rawNode;
while(r.firstChild!=r.lastChild){
if(r.firstChild!=this.bgNode){
r.removeChild(r.firstChild);
}
if(r.lastChild!=this.bgNode){
r.removeChild(r.lastChild);
}
}
return sh.Container.clear.apply(this,arguments);
},_moveChildToFront:sh.Container._moveChildToFront,_moveChildToBack:sh.Container._moveChildToBack};
dojo.mixin(sh.Creator,{createGroup:function(){
var node=this.createObject(g.Group,null);
var r=node.rawNode.ownerDocument.createElement("v:rect");
r.style.left=r.style.top=0;
r.style.width=node.rawNode.style.width;
r.style.height=node.rawNode.style.height;
r.filled=r.stroked="f";
node.rawNode.appendChild(r);
node.bgNode=r;
return node;
},createImage:function(_102){
if(!this.rawNode){
return null;
}
var _103=new g.Image(),doc=this.rawNode.ownerDocument,node=doc.createElement("v:rect");
node.stroked="f";
node.style.width=this.rawNode.style.width;
node.style.height=this.rawNode.style.height;
var img=doc.createElement("v:imagedata");
node.appendChild(img);
_103.setRawNode(node);
this.rawNode.appendChild(node);
_103.setShape(_102);
this.add(_103);
return _103;
},createObject:function(_107,_108){
if(!this.rawNode){
return null;
}
var _109=new _107(),node=this.rawNode.ownerDocument.createElement("v:"+_107.nodeType);
_109.setRawNode(node);
this.rawNode.appendChild(node);
switch(_107){
case g.Group:
case g.Line:
case g.Polyline:
case g.Image:
case g.Text:
case g.Path:
case g.TextPath:
this._overrideSize(node);
}
_109.setShape(_108);
this.add(_109);
return _109;
},_overrideSize:function(node){
var s=this.rawNode.style,w=s.width,h=s.height;
node.style.width=w;
node.style.height=h;
node.coordsize=parseInt(w)+" "+parseInt(h);
}});
dojo.extend(g.Group,_3.Container);
dojo.extend(g.Group,sh.Creator);
dojo.extend(g.Surface,_3.Container);
dojo.extend(g.Surface,sh.Creator);
})();
}
