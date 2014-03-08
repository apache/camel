/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


dojo.require("dojox.gfx.svg");
dojo.experimental("dojox.gfx.svg_attach");
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
s=new dojox.gfx.Ellipse(_1);
_4(s,dojox.gfx.defaultEllipse);
break;
case dojox.gfx.Polyline.nodeType:
s=new dojox.gfx.Polyline(_1);
_4(s,dojox.gfx.defaultPolyline);
break;
case dojox.gfx.Path.nodeType:
s=new dojox.gfx.Path(_1);
_4(s,dojox.gfx.defaultPath);
break;
case dojox.gfx.Circle.nodeType:
s=new dojox.gfx.Circle(_1);
_4(s,dojox.gfx.defaultCircle);
break;
case dojox.gfx.Line.nodeType:
s=new dojox.gfx.Line(_1);
_4(s,dojox.gfx.defaultLine);
break;
case dojox.gfx.Image.nodeType:
s=new dojox.gfx.Image(_1);
_4(s,dojox.gfx.defaultImage);
break;
case dojox.gfx.Text.nodeType:
var t=_1.getElementsByTagName("textPath");
if(t&&t.length){
s=new dojox.gfx.TextPath(_1);
_4(s,dojox.gfx.defaultPath);
_6(s);
}else{
s=new dojox.gfx.Text(_1);
_7(s);
}
_8(s);
break;
default:
return null;
}
if(!(s instanceof dojox.gfx.Image)){
_9(s);
_a(s);
}
_b(s);
return s;
};
dojox.gfx.attachSurface=function(_c){
var s=new dojox.gfx.Surface();
s.rawNode=_c;
var _e=_c.getElementsByTagName("defs");
if(_e.length==0){
return null;
}
s.defNode=_e[0];
return s;
};
var _9=function(_f){
var _10=_f.rawNode.getAttribute("fill");
if(_10=="none"){
_f.fillStyle=null;
return;
}
var _11=null,_12=dojox.gfx.svg.getRef(_10);
if(_12){
switch(_12.tagName.toLowerCase()){
case "lineargradient":
_11=_13(dojox.gfx.defaultLinearGradient,_12);
dojo.forEach(["x1","y1","x2","y2"],function(x){
_11[x]=_12.getAttribute(x);
});
break;
case "radialgradient":
_11=_13(dojox.gfx.defaultRadialGradient,_12);
dojo.forEach(["cx","cy","r"],function(x){
_11[x]=_12.getAttribute(x);
});
_11.cx=_12.getAttribute("cx");
_11.cy=_12.getAttribute("cy");
_11.r=_12.getAttribute("r");
break;
case "pattern":
_11=dojo.lang.shallowCopy(dojox.gfx.defaultPattern,true);
dojo.forEach(["x","y","width","height"],function(x){
_11[x]=_12.getAttribute(x);
});
_11.src=_12.firstChild.getAttributeNS(dojox.gfx.svg.xmlns.xlink,"href");
break;
}
}else{
_11=new dojo.Color(_10);
var _17=_f.rawNode.getAttribute("fill-opacity");
if(_17!=null){
_11.a=_17;
}
}
_f.fillStyle=_11;
};
var _13=function(_18,_19){
var _1a=dojo.clone(_18);
_1a.colors=[];
for(var i=0;i<_19.childNodes.length;++i){
_1a.colors.push({offset:_19.childNodes[i].getAttribute("offset"),color:new dojo.Color(_19.childNodes[i].getAttribute("stop-color"))});
}
return _1a;
};
var _a=function(_1c){
var _1d=_1c.rawNode,_1e=_1d.getAttribute("stroke");
if(_1e==null||_1e=="none"){
_1c.strokeStyle=null;
return;
}
var _1f=_1c.strokeStyle=dojo.clone(dojox.gfx.defaultStroke);
var _20=new dojo.Color(_1e);
if(_20){
_1f.color=_20;
_1f.color.a=_1d.getAttribute("stroke-opacity");
_1f.width=_1d.getAttribute("stroke-width");
_1f.cap=_1d.getAttribute("stroke-linecap");
_1f.join=_1d.getAttribute("stroke-linejoin");
if(_1f.join=="miter"){
_1f.join=_1d.getAttribute("stroke-miterlimit");
}
_1f.style=_1d.getAttribute("dojoGfxStrokeStyle");
}
};
var _b=function(_21){
var _22=_21.rawNode.getAttribute("transform");
if(_22.match(/^matrix\(.+\)$/)){
var t=_22.slice(7,-1).split(",");
_21.matrix=dojox.gfx.matrix.normalize({xx:parseFloat(t[0]),xy:parseFloat(t[2]),yx:parseFloat(t[1]),yy:parseFloat(t[3]),dx:parseFloat(t[4]),dy:parseFloat(t[5])});
}else{
_21.matrix=null;
}
};
var _8=function(_24){
var _25=_24.fontStyle=dojo.clone(dojox.gfx.defaultFont),r=_24.rawNode;
_25.style=r.getAttribute("font-style");
_25.variant=r.getAttribute("font-variant");
_25.weight=r.getAttribute("font-weight");
_25.size=r.getAttribute("font-size");
_25.family=r.getAttribute("font-family");
};
var _4=function(_27,def){
var _29=_27.shape=dojo.clone(def),r=_27.rawNode;
for(var i in _29){
_29[i]=r.getAttribute(i);
}
};
var _3=function(_2c){
_4(_2c,dojox.gfx.defaultRect);
_2c.shape.r=Math.min(_2c.rawNode.getAttribute("rx"),_2c.rawNode.getAttribute("ry"));
};
var _7=function(_2d){
var _2e=_2d.shape=dojo.clone(dojox.gfx.defaultText),r=_2d.rawNode;
_2e.x=r.getAttribute("x");
_2e.y=r.getAttribute("y");
_2e.align=r.getAttribute("text-anchor");
_2e.decoration=r.getAttribute("text-decoration");
_2e.rotated=parseFloat(r.getAttribute("rotate"))!=0;
_2e.kerning=r.getAttribute("kerning")=="auto";
_2e.text=r.firstChild.nodeValue;
};
var _6=function(_30){
var _31=_30.shape=dojo.clone(dojox.gfx.defaultTextPath),r=_30.rawNode;
_31.align=r.getAttribute("text-anchor");
_31.decoration=r.getAttribute("text-decoration");
_31.rotated=parseFloat(r.getAttribute("rotate"))!=0;
_31.kerning=r.getAttribute("kerning")=="auto";
_31.text=r.firstChild.nodeValue;
};
})();
