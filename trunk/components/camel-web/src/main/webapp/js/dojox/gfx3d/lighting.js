/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx3d.lighting"]){
dojo._hasResource["dojox.gfx3d.lighting"]=true;
dojo.provide("dojox.gfx3d.lighting");
dojo.require("dojox.gfx._base");
(function(){
var _1=dojox.gfx3d.lighting;
dojo.mixin(dojox.gfx3d.lighting,{black:function(){
return {r:0,g:0,b:0,a:1};
},white:function(){
return {r:1,g:1,b:1,a:1};
},toStdColor:function(c){
c=dojox.gfx.normalizeColor(c);
return {r:c.r/255,g:c.g/255,b:c.b/255,a:c.a};
},fromStdColor:function(c){
return new dojo.Color([Math.round(255*c.r),Math.round(255*c.g),Math.round(255*c.b),c.a]);
},scaleColor:function(s,c){
return {r:s*c.r,g:s*c.g,b:s*c.b,a:s*c.a};
},addColor:function(a,b){
return {r:a.r+b.r,g:a.g+b.g,b:a.b+b.b,a:a.a+b.a};
},multiplyColor:function(a,b){
return {r:a.r*b.r,g:a.g*b.g,b:a.b*b.b,a:a.a*b.a};
},saturateColor:function(c){
return {r:c.r<0?0:c.r>1?1:c.r,g:c.g<0?0:c.g>1?1:c.g,b:c.b<0?0:c.b>1?1:c.b,a:c.a<0?0:c.a>1?1:c.a};
},mixColor:function(c1,c2,s){
return _1.addColor(_1.scaleColor(s,c1),_1.scaleColor(1-s,c2));
},diff2Color:function(c1,c2){
var r=c1.r-c2.r;
var g=c1.g-c2.g;
var b=c1.b-c2.b;
var a=c1.a-c2.a;
return r*r+g*g+b*b+a*a;
},length2Color:function(c){
return c.r*c.r+c.g*c.g+c.b*c.b+c.a*c.a;
},dot:function(a,b){
return a.x*b.x+a.y*b.y+a.z*b.z;
},scale:function(s,v){
return {x:s*v.x,y:s*v.y,z:s*v.z};
},add:function(a,b){
return {x:a.x+b.x,y:a.y+b.y,z:a.z+b.z};
},saturate:function(v){
return Math.min(Math.max(v,0),1);
},length:function(v){
return Math.sqrt(dojox.gfx3d.lighting.dot(v,v));
},normalize:function(v){
return _1.scale(1/_1.length(v),v);
},faceforward:function(n,i){
var p=dojox.gfx3d.lighting;
var s=p.dot(i,n)<0?1:-1;
return p.scale(s,n);
},reflect:function(i,n){
var p=dojox.gfx3d.lighting;
return p.add(i,p.scale(-2*p.dot(i,n),n));
},diffuse:function(_25,_26){
var c=_1.black();
for(var i=0;i<_26.length;++i){
var l=_26[i],d=_1.dot(_1.normalize(l.direction),_25);
c=_1.addColor(c,_1.scaleColor(d,l.color));
}
return _1.saturateColor(c);
},specular:function(_2b,v,_2d,_2e){
var c=_1.black();
for(var i=0;i<_2e.length;++i){
var l=_2e[i],h=_1.normalize(_1.add(_1.normalize(l.direction),v)),s=Math.pow(Math.max(0,_1.dot(_2b,h)),1/_2d);
c=_1.addColor(c,_1.scaleColor(s,l.color));
}
return _1.saturateColor(c);
},phong:function(_34,v,_36,_37){
_34=_1.normalize(_34);
var c=_1.black();
for(var i=0;i<_37.length;++i){
var l=_37[i],r=_1.reflect(_1.scale(-1,_1.normalize(v)),_34),s=Math.pow(Math.max(0,_1.dot(r,_1.normalize(l.direction))),_36);
c=_1.addColor(c,_1.scaleColor(s,l.color));
}
return _1.saturateColor(c);
}});
dojo.declare("dojox.gfx3d.lighting.Model",null,{constructor:function(_3d,_3e,_3f,_40){
this.incident=_1.normalize(_3d);
this.lights=[];
for(var i=0;i<_3e.length;++i){
var l=_3e[i];
this.lights.push({direction:_1.normalize(l.direction),color:_1.toStdColor(l.color)});
}
this.ambient=_1.toStdColor(_3f.color?_3f.color:"white");
this.ambient=_1.scaleColor(_3f.intensity,this.ambient);
this.ambient=_1.scaleColor(this.ambient.a,this.ambient);
this.ambient.a=1;
this.specular=_1.toStdColor(_40?_40:"white");
this.specular=_1.scaleColor(this.specular.a,this.specular);
this.specular.a=1;
this.npr_cool={r:0,g:0,b:0.4,a:1};
this.npr_warm={r:0.4,g:0.4,b:0.2,a:1};
this.npr_alpha=0.2;
this.npr_beta=0.6;
this.npr_scale=0.6;
},constant:function(_43,_44,_45){
_45=_1.toStdColor(_45);
var _46=_45.a,_47=_1.scaleColor(_46,_45);
_47.a=_46;
return _1.fromStdColor(_1.saturateColor(_47));
},matte:function(_48,_49,_4a){
if(typeof _49=="string"){
_49=_1.finish[_49];
}
_4a=_1.toStdColor(_4a);
_48=_1.faceforward(_1.normalize(_48),this.incident);
var _4b=_1.scaleColor(_49.Ka,this.ambient),_4c=_1.saturate(-4*_1.dot(_48,this.incident)),_4d=_1.scaleColor(_4c*_49.Kd,_1.diffuse(_48,this.lights)),_4e=_1.scaleColor(_4a.a,_1.multiplyColor(_4a,_1.addColor(_4b,_4d)));
_4e.a=_4a.a;
return _1.fromStdColor(_1.saturateColor(_4e));
},metal:function(_4f,_50,_51){
if(typeof _50=="string"){
_50=_1.finish[_50];
}
_51=_1.toStdColor(_51);
_4f=_1.faceforward(_1.normalize(_4f),this.incident);
var v=_1.scale(-1,this.incident),_53,_54,_55=_1.scaleColor(_50.Ka,this.ambient),_56=_1.saturate(-4*_1.dot(_4f,this.incident));
if("phong" in _50){
_53=_1.scaleColor(_56*_50.Ks*_50.phong,_1.phong(_4f,v,_50.phong_size,this.lights));
}else{
_53=_1.scaleColor(_56*_50.Ks,_1.specular(_4f,v,_50.roughness,this.lights));
}
_54=_1.scaleColor(_51.a,_1.addColor(_1.multiplyColor(_51,_55),_1.multiplyColor(this.specular,_53)));
_54.a=_51.a;
return _1.fromStdColor(_1.saturateColor(_54));
},plastic:function(_57,_58,_59){
if(typeof _58=="string"){
_58=_1.finish[_58];
}
_59=_1.toStdColor(_59);
_57=_1.faceforward(_1.normalize(_57),this.incident);
var v=_1.scale(-1,this.incident),_5b,_5c,_5d=_1.scaleColor(_58.Ka,this.ambient),_5e=_1.saturate(-4*_1.dot(_57,this.incident)),_5f=_1.scaleColor(_5e*_58.Kd,_1.diffuse(_57,this.lights));
if("phong" in _58){
_5b=_1.scaleColor(_5e*_58.Ks*_58.phong,_1.phong(_57,v,_58.phong_size,this.lights));
}else{
_5b=_1.scaleColor(_5e*_58.Ks,_1.specular(_57,v,_58.roughness,this.lights));
}
_5c=_1.scaleColor(_59.a,_1.addColor(_1.multiplyColor(_59,_1.addColor(_5d,_5f)),_1.multiplyColor(this.specular,_5b)));
_5c.a=_59.a;
return _1.fromStdColor(_1.saturateColor(_5c));
},npr:function(_60,_61,_62){
if(typeof _61=="string"){
_61=_1.finish[_61];
}
_62=_1.toStdColor(_62);
_60=_1.faceforward(_1.normalize(_60),this.incident);
var _63=_1.scaleColor(_61.Ka,this.ambient),_64=_1.saturate(-4*_1.dot(_60,this.incident)),_65=_1.scaleColor(_64*_61.Kd,_1.diffuse(_60,this.lights)),_66=_1.scaleColor(_62.a,_1.multiplyColor(_62,_1.addColor(_63,_65))),_67=_1.addColor(this.npr_cool,_1.scaleColor(this.npr_alpha,_66)),_68=_1.addColor(this.npr_warm,_1.scaleColor(this.npr_beta,_66)),d=(1+_1.dot(this.incident,_60))/2,_66=_1.scaleColor(this.npr_scale,_1.addColor(_66,_1.mixColor(_67,_68,d)));
_66.a=_62.a;
return _1.fromStdColor(_1.saturateColor(_66));
}});
})();
dojox.gfx3d.lighting.finish={defaults:{Ka:0.1,Kd:0.6,Ks:0,roughness:0.05},dull:{Ka:0.1,Kd:0.6,Ks:0.5,roughness:0.15},shiny:{Ka:0.1,Kd:0.6,Ks:1,roughness:0.001},glossy:{Ka:0.1,Kd:0.6,Ks:1,roughness:0.0001},phong_dull:{Ka:0.1,Kd:0.6,Ks:0.5,phong:0.5,phong_size:1},phong_shiny:{Ka:0.1,Kd:0.6,Ks:1,phong:1,phong_size:200},phong_glossy:{Ka:0.1,Kd:0.6,Ks:1,phong:1,phong_size:300},luminous:{Ka:1,Kd:0,Ks:0,roughness:0.05},metalA:{Ka:0.35,Kd:0.3,Ks:0.8,roughness:1/20},metalB:{Ka:0.3,Kd:0.4,Ks:0.7,roughness:1/60},metalC:{Ka:0.25,Kd:0.5,Ks:0.8,roughness:1/80},metalD:{Ka:0.15,Kd:0.6,Ks:0.8,roughness:1/100},metalE:{Ka:0.1,Kd:0.7,Ks:0.8,roughness:1/120}};
}
