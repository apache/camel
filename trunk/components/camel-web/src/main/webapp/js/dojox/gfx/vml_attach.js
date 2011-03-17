/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


dojo.require("dojox.gfx.vml");
dojo.experimental("dojox.gfx.vml_attach");
(function(){
dojox.gfx.attachNode=function(_1){
if(!_1){
return null;
}
var s=null;
switch(_1.tagName.toLowerCase()){
case dojox.gfx.Rect.nodeType:
s=new dojox.gfx.Rect(_1);
_3(s);
break;
case dojox.gfx.Ellipse.nodeType:
if(_1.style.width==_1.style.height){
s=new dojox.gfx.Circle(_1);
_4(s);
}else{
s=new dojox.gfx.Ellipse(_1);
_5(s);
}
break;
case dojox.gfx.Path.nodeType:
switch(_1.getAttribute("dojoGfxType")){
case "line":
s=new dojox.gfx.Line(_1);
_6(s);
break;
case "polyline":
s=new dojox.gfx.Polyline(_1);
_7(s);
break;
case "path":
s=new dojox.gfx.Path(_1);
_8(s);
break;
case "text":
s=new dojox.gfx.Text(_1);
_9(s);
_a(s);
_b(s);
break;
case "textpath":
s=new dojox.gfx.TextPath(_1);
_8(s);
_9(s);
_a(s);
break;
}
break;
case dojox.gfx.Image.nodeType:
switch(_1.getAttribute("dojoGfxType")){
case "image":
s=new dojox.gfx.Image(_1);
_c(s);
_d(s);
break;
}
break;
default:
return null;
}
if(!(s instanceof dojox.gfx.Image)){
_e(s);
_f(s);
if(!(s instanceof dojox.gfx.Text)){
_10(s);
}
}
return s;
};
dojox.gfx.attachSurface=function(_11){
var s=new dojox.gfx.Surface();
s.clipNode=_11;
var r=s.rawNode=_11.firstChild;
var b=r.firstChild;
if(!b||b.tagName!="rect"){
return null;
}
s.bgNode=r;
return s;
};
var _e=function(_15){
var _16=null,r=_15.rawNode,fo=r.fill;
if(fo.on&&fo.type=="gradient"){
var _16=dojo.clone(dojox.gfx.defaultLinearGradient),rad=dojox.gfx.matrix._degToRad(fo.angle);
_16.x2=Math.cos(rad);
_16.y2=Math.sin(rad);
_16.colors=[];
var _1a=fo.colors.value.split(";");
for(var i=0;i<_1a.length;++i){
var t=_1a[i].match(/\S+/g);
if(!t||t.length!=2){
continue;
}
_16.colors.push({offset:dojox.gfx.vml._parseFloat(t[0]),color:new dojo.Color(t[1])});
}
}else{
if(fo.on&&fo.type=="gradientradial"){
var _16=dojo.clone(dojox.gfx.defaultRadialGradient),w=parseFloat(r.style.width),h=parseFloat(r.style.height);
_16.cx=isNaN(w)?0:fo.focusposition.x*w;
_16.cy=isNaN(h)?0:fo.focusposition.y*h;
_16.r=isNaN(w)?1:w/2;
_16.colors=[];
var _1a=fo.colors.value.split(";");
for(var i=_1a.length-1;i>=0;--i){
var t=_1a[i].match(/\S+/g);
if(!t||t.length!=2){
continue;
}
_16.colors.push({offset:dojox.gfx.vml._parseFloat(t[0]),color:new dojo.Color(t[1])});
}
}else{
if(fo.on&&fo.type=="tile"){
var _16=dojo.clone(dojox.gfx.defaultPattern);
_16.width=dojox.gfx.pt2px(fo.size.x);
_16.height=dojox.gfx.pt2px(fo.size.y);
_16.x=fo.origin.x*_16.width;
_16.y=fo.origin.y*_16.height;
_16.src=fo.src;
}else{
if(fo.on&&r.fillcolor){
_16=new dojo.Color(r.fillcolor+"");
_16.a=fo.opacity;
}
}
}
}
_15.fillStyle=_16;
};
var _f=function(_1f){
var r=_1f.rawNode;
if(!r.stroked){
_1f.strokeStyle=null;
return;
}
var _21=_1f.strokeStyle=dojo.clone(dojox.gfx.defaultStroke),rs=r.stroke;
_21.color=new dojo.Color(r.strokecolor.value);
_21.width=dojox.gfx.normalizedLength(r.strokeweight+"");
_21.color.a=rs.opacity;
_21.cap=this._translate(this._capMapReversed,rs.endcap);
_21.join=rs.joinstyle=="miter"?rs.miterlimit:rs.joinstyle;
_21.style=rs.dashstyle;
};
var _10=function(_23){
var s=_23.rawNode.skew,sm=s.matrix,so=s.offset;
_23.matrix=dojox.gfx.matrix.normalize({xx:sm.xtox,xy:sm.ytox,yx:sm.xtoy,yy:sm.ytoy,dx:dojox.gfx.pt2px(so.x),dy:dojox.gfx.pt2px(so.y)});
};
var _27=function(_28){
_28.bgNode=_28.rawNode.firstChild;
};
var _3=function(_29){
var r=_29.rawNode,_2b=r.outerHTML.match(/arcsize = \"(\d*\.?\d+[%f]?)\"/)[1],_2c=r.style,_2d=parseFloat(_2c.width),_2e=parseFloat(_2c.height);
_2b=(_2b.indexOf("%")>=0)?parseFloat(_2b)/100:dojox.gfx.vml._parseFloat(_2b);
_29.shape=dojox.gfx.makeParameters(dojox.gfx.defaultRect,{x:parseInt(_2c.left),y:parseInt(_2c.top),width:_2d,height:_2e,r:Math.min(_2d,_2e)*_2b});
};
var _5=function(_2f){
var _30=_2f.rawNode.style,rx=parseInt(_30.width)/2,ry=parseInt(_30.height)/2;
_2f.shape=dojox.gfx.makeParameters(dojox.gfx.defaultEllipse,{cx:parseInt(_30.left)+rx,cy:parseInt(_30.top)+ry,rx:rx,ry:ry});
};
var _4=function(_33){
var _34=_33.rawNode.style,r=parseInt(_34.width)/2;
_33.shape=dojox.gfx.makeParameters(dojox.gfx.defaultCircle,{cx:parseInt(_34.left)+r,cy:parseInt(_34.top)+r,r:r});
};
var _6=function(_36){
var _37=_36.shape=dojo.clone(dojox.gfx.defaultLine),p=_36.rawNode.path.v.match(dojox.gfx.pathVmlRegExp);
do{
if(p.length<7||p[0]!="m"||p[3]!="l"||p[6]!="e"){
break;
}
_37.x1=parseInt(p[1]);
_37.y1=parseInt(p[2]);
_37.x2=parseInt(p[4]);
_37.y2=parseInt(p[5]);
}while(false);
};
var _7=function(_39){
var _3a=_39.shape=dojo.clone(dojox.gfx.defaultPolyline),p=_39.rawNode.path.v.match(dojox.gfx.pathVmlRegExp);
do{
if(p.length<3||p[0]!="m"){
break;
}
var x=parseInt(p[0]),y=parseInt(p[1]);
if(isNaN(x)||isNaN(y)){
break;
}
_3a.points.push({x:x,y:y});
if(p.length<6||p[3]!="l"){
break;
}
for(var i=4;i<p.length;i+=2){
x=parseInt(p[i]);
y=parseInt(p[i+1]);
if(isNaN(x)||isNaN(y)){
break;
}
_3a.points.push({x:x,y:y});
}
}while(false);
};
var _c=function(_3f){
_3f.shape=dojo.clone(dojox.gfx.defaultImage);
_3f.shape.src=_3f.rawNode.firstChild.src;
};
var _d=function(_40){
var m=_40.rawNode.filters["DXImageTransform.Microsoft.Matrix"];
_40.matrix=dojox.gfx.matrix.normalize({xx:m.M11,xy:m.M12,yx:m.M21,yy:m.M22,dx:m.Dx,dy:m.Dy});
};
var _9=function(_42){
var _43=_42.shape=dojo.clone(dojox.gfx.defaultText),r=_42.rawNode,p=r.path.v.match(dojox.gfx.pathVmlRegExp);
do{
if(!p||p.length!=7){
break;
}
var c=r.childNodes,i=0;
for(;i<c.length&&c[i].tagName!="textpath";++i){
}
if(i>=c.length){
break;
}
var s=c[i].style;
_43.text=c[i].string;
switch(s["v-text-align"]){
case "left":
_43.x=parseInt(p[1]);
_43.align="start";
break;
case "center":
_43.x=(parseInt(p[1])+parseInt(p[4]))/2;
_43.align="middle";
break;
case "right":
_43.x=parseInt(p[4]);
_43.align="end";
break;
}
_43.y=parseInt(p[2]);
_43.decoration=s["text-decoration"];
_43.rotated=s["v-rotate-letters"].toLowerCase() in dojox.gfx.vml._bool;
_43.kerning=s["v-text-kern"].toLowerCase() in dojox.gfx.vml._bool;
return;
}while(false);
_42.shape=null;
};
var _a=function(_49){
var _4a=_49.fontStyle=dojo.clone(dojox.gfx.defaultFont),c=_49.rawNode.childNodes,i=0;
for(;i<c.length&&c[i].tagName=="textpath";++i){
}
if(i>=c.length){
_49.fontStyle=null;
return;
}
var s=c[i].style;
_4a.style=s.fontstyle;
_4a.variant=s.fontvariant;
_4a.weight=s.fontweight;
_4a.size=s.fontsize;
_4a.family=s.fontfamily;
};
var _b=function(_4e){
_10(_4e);
var _4f=_4e.matrix,fs=_4e.fontStyle;
if(_4f&&fs){
_4e.matrix=dojox.gfx.matrix.multiply(_4f,{dy:dojox.gfx.normalizedLength(fs.size)*0.35});
}
};
var _8=function(_51){
var _52=_51.shape=dojo.clone(dojox.gfx.defaultPath),p=_51.rawNode.path.v.match(dojox.gfx.pathVmlRegExp),t=[],_55=false,map=dojox.gfx.Path._pathVmlToSvgMap;
for(var i=0;i<p.length;++p){
var s=p[i];
if(s in map){
_55=false;
t.push(map[s]);
}else{
if(!_55){
var n=parseInt(s);
if(isNaN(n)){
_55=true;
}else{
t.push(n);
}
}
}
}
var l=t.length;
if(l>=4&&t[l-1]==""&&t[l-2]==0&&t[l-3]==0&&t[l-4]=="l"){
t.splice(l-4,4);
}
if(l){
_52.path=t.join(" ");
}
};
})();
