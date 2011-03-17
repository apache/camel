/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.svg"]){
dojo._hasResource["dojox.gfx.svg"]=true;
dojo.provide("dojox.gfx.svg");
dojo.require("dojox.gfx._base");
dojo.require("dojox.gfx.shape");
dojo.require("dojox.gfx.path");
dojox.gfx.svg.xmlns={xlink:"http://www.w3.org/1999/xlink",svg:"http://www.w3.org/2000/svg"};
dojox.gfx.svg.getRef=function(_1){
if(!_1||_1=="none"){
return null;
}
if(_1.match(/^url\(#.+\)$/)){
return dojo.byId(_1.slice(5,-1));
}
if(_1.match(/^#dojoUnique\d+$/)){
return dojo.byId(_1.slice(1));
}
return null;
};
dojox.gfx.svg.dasharray={solid:"none",shortdash:[4,1],shortdot:[1,1],shortdashdot:[4,1,1,1],shortdashdotdot:[4,1,1,1,1,1],dot:[1,3],dash:[4,3],longdash:[8,3],dashdot:[4,3,1,3],longdashdot:[8,3,1,3],longdashdotdot:[8,3,1,3,1,3]};
dojo.extend(dojox.gfx.Shape,{setFill:function(_2){
if(!_2){
this.fillStyle=null;
this.rawNode.setAttribute("fill","none");
this.rawNode.setAttribute("fill-opacity",0);
return this;
}
var f;
var _4=function(x){
this.setAttribute(x,f[x].toFixed(8));
};
if(typeof (_2)=="object"&&"type" in _2){
switch(_2.type){
case "linear":
f=dojox.gfx.makeParameters(dojox.gfx.defaultLinearGradient,_2);
var _6=this._setFillObject(f,"linearGradient");
dojo.forEach(["x1","y1","x2","y2"],_4,_6);
break;
case "radial":
f=dojox.gfx.makeParameters(dojox.gfx.defaultRadialGradient,_2);
var _6=this._setFillObject(f,"radialGradient");
dojo.forEach(["cx","cy","r"],_4,_6);
break;
case "pattern":
f=dojox.gfx.makeParameters(dojox.gfx.defaultPattern,_2);
var _7=this._setFillObject(f,"pattern");
dojo.forEach(["x","y","width","height"],_4,_7);
break;
}
this.fillStyle=f;
return this;
}
var f=dojox.gfx.normalizeColor(_2);
this.fillStyle=f;
this.rawNode.setAttribute("fill",f.toCss());
this.rawNode.setAttribute("fill-opacity",f.a);
this.rawNode.setAttribute("fill-rule","evenodd");
return this;
},setStroke:function(_8){
if(!_8){
this.strokeStyle=null;
this.rawNode.setAttribute("stroke","none");
this.rawNode.setAttribute("stroke-opacity",0);
return this;
}
if(typeof _8=="string"||dojo.isArray(_8)||_8 instanceof dojo.Color){
_8={color:_8};
}
var s=this.strokeStyle=dojox.gfx.makeParameters(dojox.gfx.defaultStroke,_8);
s.color=dojox.gfx.normalizeColor(s.color);
var rn=this.rawNode;
if(s){
rn.setAttribute("stroke",s.color.toCss());
rn.setAttribute("stroke-opacity",s.color.a);
rn.setAttribute("stroke-width",s.width);
rn.setAttribute("stroke-linecap",s.cap);
if(typeof s.join=="number"){
rn.setAttribute("stroke-linejoin","miter");
rn.setAttribute("stroke-miterlimit",s.join);
}else{
rn.setAttribute("stroke-linejoin",s.join);
}
var da=s.style.toLowerCase();
if(da in dojox.gfx.svg.dasharray){
da=dojox.gfx.svg.dasharray[da];
}
if(da instanceof Array){
da=dojo.clone(da);
for(var i=0;i<da.length;++i){
da[i]*=s.width;
}
if(s.cap!="butt"){
for(var i=0;i<da.length;i+=2){
da[i]-=s.width;
if(da[i]<1){
da[i]=1;
}
}
for(var i=1;i<da.length;i+=2){
da[i]+=s.width;
}
}
da=da.join(",");
}
rn.setAttribute("stroke-dasharray",da);
rn.setAttribute("dojoGfxStrokeStyle",s.style);
}
return this;
},_getParentSurface:function(){
var _d=this.parent;
for(;_d&&!(_d instanceof dojox.gfx.Surface);_d=_d.parent){
}
return _d;
},_setFillObject:function(f,_f){
var _10=dojox.gfx.svg.xmlns.svg;
this.fillStyle=f;
var _11=this._getParentSurface(),_12=_11.defNode,_13=this.rawNode.getAttribute("fill"),ref=dojox.gfx.svg.getRef(_13);
if(ref){
_13=ref;
if(_13.tagName.toLowerCase()!=_f.toLowerCase()){
var id=_13.id;
_13.parentNode.removeChild(_13);
_13=document.createElementNS(_10,_f);
_13.setAttribute("id",id);
_12.appendChild(_13);
}else{
while(_13.childNodes.length){
_13.removeChild(_13.lastChild);
}
}
}else{
_13=document.createElementNS(_10,_f);
_13.setAttribute("id",dojox.gfx._base._getUniqueId());
_12.appendChild(_13);
}
if(_f=="pattern"){
_13.setAttribute("patternUnits","userSpaceOnUse");
var img=document.createElementNS(_10,"image");
img.setAttribute("x",0);
img.setAttribute("y",0);
img.setAttribute("width",f.width.toFixed(8));
img.setAttribute("height",f.height.toFixed(8));
img.setAttributeNS(dojox.gfx.svg.xmlns.xlink,"href",f.src);
_13.appendChild(img);
}else{
_13.setAttribute("gradientUnits","userSpaceOnUse");
for(var i=0;i<f.colors.length;++i){
var c=f.colors[i],t=document.createElementNS(_10,"stop"),cc=c.color=dojox.gfx.normalizeColor(c.color);
t.setAttribute("offset",c.offset.toFixed(8));
t.setAttribute("stop-color",cc.toCss());
t.setAttribute("stop-opacity",cc.a);
_13.appendChild(t);
}
}
this.rawNode.setAttribute("fill","url(#"+_13.getAttribute("id")+")");
this.rawNode.removeAttribute("fill-opacity");
this.rawNode.setAttribute("fill-rule","evenodd");
return _13;
},_applyTransform:function(){
var _1b=this.matrix;
if(_1b){
var tm=this.matrix;
this.rawNode.setAttribute("transform","matrix("+tm.xx.toFixed(8)+","+tm.yx.toFixed(8)+","+tm.xy.toFixed(8)+","+tm.yy.toFixed(8)+","+tm.dx.toFixed(8)+","+tm.dy.toFixed(8)+")");
}else{
this.rawNode.removeAttribute("transform");
}
return this;
},setRawNode:function(_1d){
var r=this.rawNode=_1d;
r.setAttribute("fill","none");
r.setAttribute("fill-opacity",0);
r.setAttribute("stroke","none");
r.setAttribute("stroke-opacity",0);
r.setAttribute("stroke-width",1);
r.setAttribute("stroke-linecap","butt");
r.setAttribute("stroke-linejoin","miter");
r.setAttribute("stroke-miterlimit",4);
},setShape:function(_1f){
this.shape=dojox.gfx.makeParameters(this.shape,_1f);
for(var i in this.shape){
if(i!="type"){
this.rawNode.setAttribute(i,this.shape[i]);
}
}
return this;
},_moveToFront:function(){
this.rawNode.parentNode.appendChild(this.rawNode);
return this;
},_moveToBack:function(){
this.rawNode.parentNode.insertBefore(this.rawNode,this.rawNode.parentNode.firstChild);
return this;
}});
dojo.declare("dojox.gfx.Group",dojox.gfx.Shape,{constructor:function(){
dojox.gfx.svg.Container._init.call(this);
},setRawNode:function(_21){
this.rawNode=_21;
}});
dojox.gfx.Group.nodeType="g";
dojo.declare("dojox.gfx.Rect",dojox.gfx.shape.Rect,{setShape:function(_22){
this.shape=dojox.gfx.makeParameters(this.shape,_22);
this.bbox=null;
for(var i in this.shape){
if(i!="type"&&i!="r"){
this.rawNode.setAttribute(i,this.shape[i]);
}
}
if(this.shape.r){
this.rawNode.setAttribute("ry",this.shape.r);
this.rawNode.setAttribute("rx",this.shape.r);
}
return this;
}});
dojox.gfx.Rect.nodeType="rect";
dojox.gfx.Ellipse=dojox.gfx.shape.Ellipse;
dojox.gfx.Ellipse.nodeType="ellipse";
dojox.gfx.Circle=dojox.gfx.shape.Circle;
dojox.gfx.Circle.nodeType="circle";
dojox.gfx.Line=dojox.gfx.shape.Line;
dojox.gfx.Line.nodeType="line";
dojo.declare("dojox.gfx.Polyline",dojox.gfx.shape.Polyline,{setShape:function(_24,_25){
if(_24&&_24 instanceof Array){
this.shape=dojox.gfx.makeParameters(this.shape,{points:_24});
if(_25&&this.shape.points.length){
this.shape.points.push(this.shape.points[0]);
}
}else{
this.shape=dojox.gfx.makeParameters(this.shape,_24);
}
this.box=null;
var _26=[],p=this.shape.points;
for(var i=0;i<p.length;++i){
if(typeof p[i]=="number"){
_26.push(p[i].toFixed(8));
}else{
_26.push(p[i].x.toFixed(8));
_26.push(p[i].y.toFixed(8));
}
}
this.rawNode.setAttribute("points",_26.join(" "));
return this;
}});
dojox.gfx.Polyline.nodeType="polyline";
dojo.declare("dojox.gfx.Image",dojox.gfx.shape.Image,{setShape:function(_29){
this.shape=dojox.gfx.makeParameters(this.shape,_29);
this.bbox=null;
var _2a=this.rawNode;
for(var i in this.shape){
if(i!="type"&&i!="src"){
_2a.setAttribute(i,this.shape[i]);
}
}
_2a.setAttributeNS(dojox.gfx.svg.xmlns.xlink,"href",this.shape.src);
return this;
}});
dojox.gfx.Image.nodeType="image";
dojo.declare("dojox.gfx.Text",dojox.gfx.shape.Text,{setShape:function(_2c){
this.shape=dojox.gfx.makeParameters(this.shape,_2c);
this.bbox=null;
var r=this.rawNode,s=this.shape;
r.setAttribute("x",s.x);
r.setAttribute("y",s.y);
r.setAttribute("text-anchor",s.align);
r.setAttribute("text-decoration",s.decoration);
r.setAttribute("rotate",s.rotated?90:0);
r.setAttribute("kerning",s.kerning?"auto":0);
r.setAttribute("text-rendering","optimizeLegibility");
r.textContent=s.text;
return this;
},getTextWidth:function(){
var _2f=this.rawNode,_30=_2f.parentNode,_31=_2f.cloneNode(true);
_31.style.visibility="hidden";
var _32=0,_33=_31.firstChild.nodeValue;
_30.appendChild(_31);
if(_33!=""){
while(!_32){
_32=parseInt(_31.getBBox().width);
}
}
_30.removeChild(_31);
return _32;
}});
dojox.gfx.Text.nodeType="text";
dojo.declare("dojox.gfx.Path",dojox.gfx.path.Path,{_updateWithSegment:function(_34){
dojox.gfx.Path.superclass._updateWithSegment.apply(this,arguments);
if(typeof (this.shape.path)=="string"){
this.rawNode.setAttribute("d",this.shape.path);
}
},setShape:function(_35){
dojox.gfx.Path.superclass.setShape.apply(this,arguments);
this.rawNode.setAttribute("d",this.shape.path);
return this;
}});
dojox.gfx.Path.nodeType="path";
dojo.declare("dojox.gfx.TextPath",dojox.gfx.path.TextPath,{_updateWithSegment:function(_36){
dojox.gfx.Path.superclass._updateWithSegment.apply(this,arguments);
this._setTextPath();
},setShape:function(_37){
dojox.gfx.Path.superclass.setShape.apply(this,arguments);
this._setTextPath();
return this;
},_setTextPath:function(){
if(typeof this.shape.path!="string"){
return;
}
var r=this.rawNode;
if(!r.firstChild){
var tp=document.createElementNS(dojox.gfx.svg.xmlns.svg,"textPath"),tx=document.createTextNode("");
tp.appendChild(tx);
r.appendChild(tp);
}
var ref=r.firstChild.getAttributeNS(dojox.gfx.svg.xmlns.xlink,"href"),_3c=ref&&dojox.gfx.svg.getRef(ref);
if(!_3c){
var _3d=this._getParentSurface();
if(_3d){
var _3e=_3d.defNode;
_3c=document.createElementNS(dojox.gfx.svg.xmlns.svg,"path");
var id=dojox.gfx._base._getUniqueId();
_3c.setAttribute("id",id);
_3e.appendChild(_3c);
r.firstChild.setAttributeNS(dojox.gfx.svg.xmlns.xlink,"href","#"+id);
}
}
if(_3c){
_3c.setAttribute("d",this.shape.path);
}
},_setText:function(){
var r=this.rawNode;
if(!r.firstChild){
var tp=document.createElementNS(dojox.gfx.svg.xmlns.svg,"textPath"),tx=document.createTextNode("");
tp.appendChild(tx);
r.appendChild(tp);
}
r=r.firstChild;
var t=this.text;
r.setAttribute("alignment-baseline","middle");
switch(t.align){
case "middle":
r.setAttribute("text-anchor","middle");
r.setAttribute("startOffset","50%");
break;
case "end":
r.setAttribute("text-anchor","end");
r.setAttribute("startOffset","100%");
break;
default:
r.setAttribute("text-anchor","start");
r.setAttribute("startOffset","0%");
break;
}
r.setAttribute("baseline-shift","0.5ex");
r.setAttribute("text-decoration",t.decoration);
r.setAttribute("rotate",t.rotated?90:0);
r.setAttribute("kerning",t.kerning?"auto":0);
r.firstChild.data=t.text;
}});
dojox.gfx.TextPath.nodeType="text";
dojo.declare("dojox.gfx.Surface",dojox.gfx.shape.Surface,{constructor:function(){
dojox.gfx.svg.Container._init.call(this);
},destroy:function(){
this.defNode=null;
this.inherited(arguments);
},setDimensions:function(_44,_45){
if(!this.rawNode){
return this;
}
this.rawNode.setAttribute("width",_44);
this.rawNode.setAttribute("height",_45);
return this;
},getDimensions:function(){
return this.rawNode?{width:this.rawNode.getAttribute("width"),height:this.rawNode.getAttribute("height")}:null;
}});
dojox.gfx.createSurface=function(_46,_47,_48){
var s=new dojox.gfx.Surface();
s.rawNode=document.createElementNS(dojox.gfx.svg.xmlns.svg,"svg");
s.rawNode.setAttribute("width",_47);
s.rawNode.setAttribute("height",_48);
var _4a=document.createElementNS(dojox.gfx.svg.xmlns.svg,"defs");
s.rawNode.appendChild(_4a);
s.defNode=_4a;
s._parent=dojo.byId(_46);
s._parent.appendChild(s.rawNode);
return s;
};
dojox.gfx.svg.Font={_setFont:function(){
var f=this.fontStyle;
this.rawNode.setAttribute("font-style",f.style);
this.rawNode.setAttribute("font-variant",f.variant);
this.rawNode.setAttribute("font-weight",f.weight);
this.rawNode.setAttribute("font-size",f.size);
this.rawNode.setAttribute("font-family",f.family);
}};
dojox.gfx.svg.Container={_init:function(){
dojox.gfx.shape.Container._init.call(this);
},add:function(_4c){
if(this!=_4c.getParent()){
this.rawNode.appendChild(_4c.rawNode);
dojox.gfx.shape.Container.add.apply(this,arguments);
}
return this;
},remove:function(_4d,_4e){
if(this==_4d.getParent()){
if(this.rawNode==_4d.rawNode.parentNode){
this.rawNode.removeChild(_4d.rawNode);
}
dojox.gfx.shape.Container.remove.apply(this,arguments);
}
return this;
},clear:function(){
var r=this.rawNode;
while(r.lastChild){
r.removeChild(r.lastChild);
}
var d=this.defNode;
if(d){
while(d.lastChild){
d.removeChild(d.lastChild);
}
r.appendChild(d);
}
return dojox.gfx.shape.Container.clear.apply(this,arguments);
},_moveChildToFront:dojox.gfx.shape.Container._moveChildToFront,_moveChildToBack:dojox.gfx.shape.Container._moveChildToBack};
dojo.mixin(dojox.gfx.shape.Creator,{createObject:function(_51,_52){
if(!this.rawNode){
return null;
}
var _53=new _51(),_54=document.createElementNS(dojox.gfx.svg.xmlns.svg,_51.nodeType);
_53.setRawNode(_54);
this.rawNode.appendChild(_54);
_53.setShape(_52);
this.add(_53);
return _53;
}});
dojo.extend(dojox.gfx.Text,dojox.gfx.svg.Font);
dojo.extend(dojox.gfx.TextPath,dojox.gfx.svg.Font);
dojo.extend(dojox.gfx.Group,dojox.gfx.svg.Container);
dojo.extend(dojox.gfx.Group,dojox.gfx.shape.Creator);
dojo.extend(dojox.gfx.Surface,dojox.gfx.svg.Container);
dojo.extend(dojox.gfx.Surface,dojox.gfx.shape.Creator);
}
