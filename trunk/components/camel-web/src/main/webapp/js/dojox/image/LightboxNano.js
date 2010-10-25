/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.LightboxNano"]){
dojo._hasResource["dojox.image.LightboxNano"]=true;
dojo.provide("dojox.image.LightboxNano");
dojo.require("dojo.fx");
(function(d){
var _2=function(){
var _3=d.global,_4=d.doc,w=0,h=0,de=_4.documentElement,_8=de.clientWidth,_9=de.clientHeight,_a=d._docScroll();
if(d.isMozilla){
var _b=_c,_d=_e,_f=_8,_10=_9,_c=_4.body.clientWidth,_e=_4.body.clientHeight;
if(_c>_8){
_b=_8;
_f=_c;
}
if(_e>_9){
_d=_9;
_10=_e;
}
w=(_f>_3.innerWidth)?_b:_f;
h=(_10>_3.innerHeight)?_d:_10;
}else{
if(_3.innerWidth){
w=_3.innerWidth;
h=_3.innerHeight;
}else{
if(d.isIE&&de&&_9){
w=_8;
h=_9;
}else{
if(d.body().clientWidth){
w=d.body().clientWidth;
h=d.body().clientHeight;
}
}
}
}
return {w:w,h:h,l:_a.x,t:_a.y};
};
d.declare("dojox.image.LightboxNano",null,{href:"",duration:500,preloadDelay:5000,_node:null,_start:null,_end:null,_img:null,_bg:null,_onClickEvt:null,_connects:null,_loading:false,_loadingNode:null,constructor:function(p,n){
var _13=this;
d.mixin(_13,p);
n=dojo.byId(n);
if(!/a/i.test(n.tagName)){
var a=d.doc.createElement("a");
a.href=_13.href;
a.className=n.className;
n.className="";
d.place(a,n,"after");
a.appendChild(n);
n=a;
}
d.style(n,{display:"block",position:"relative"});
d.place(_13._createDiv("dojoxEnlarge"),n);
_13._node=n;
d.setSelectable(n,false);
_13._onClickEvt=d.connect(n,"onclick",_13,"_load");
setTimeout(function(){
(new Image()).src=_13.href;
_13._hideLoading();
},_13.preloadDelay);
},destroy:function(){
var a=this._connects||[];
a.push(this._onClickEvt);
d.forEach(a,function(e){
d.disconnect(e);
});
d.destroy(this._node);
},_createDiv:function(_17,_18){
var e=d.doc.createElement("div");
e.className=_17;
d.style(e,{position:"absolute",display:_18?"":"none"});
return e;
},_load:function(e){
var _1b=this;
d.stopEvent(e);
if(!_1b._loading){
_1b._loading=true;
_1b._reset();
var n=d.query("img",_1b._node)[0],a=d._abs(n,true),c=d.contentBox(n),b=d._getBorderExtents(n),i=d.doc.createElement("img"),ln=_1b._loadingNode;
if(ln==null){
_1b._loadingNode=ln=_1b._createDiv("dojoxLoading",true);
d.place(ln,_1b._node);
var l=d.marginBox(ln);
d.style(ln,{left:parseInt((c.w-l.w)/2)+"px",top:parseInt((c.h-l.h)/2)+"px"});
}
c.x=a.x-10+b.l;
c.y=a.y-10+b.t;
_1b._start=c;
_1b._img=i;
_1b._connects=[d.connect(i,"onload",_1b,"_show")];
d.style(i,{visibility:"hidden",cursor:"pointer",position:"absolute",top:0,left:0,zIndex:9999999});
d.body().appendChild(i);
i.src=_1b.href;
}
},_hideLoading:function(){
if(this._loadingNode){
d.style(this._loadingNode,"display","none");
}
this._loadingNode=false;
},_show:function(){
var _23=this,vp=_2(),w=_23._img.width,h=_23._img.height,vpw=parseInt((vp.w-20)*0.9),vph=parseInt((vp.h-20)*0.9),dd=d.doc,bg=dd.createElement("div"),ln=_23._loadingNode;
if(_23._loadingNode){
_23._hideLoading();
}
d.style(_23._img,{border:"10px solid #fff",visibility:"visible"});
d.style(_23._node,"visibility","hidden");
_23._loading=false;
_23._connects=_23._connects.concat([d.connect(dd,"onmousedown",_23,"_hide"),d.connect(dd,"onkeypress",_23,"_key"),d.connect(window,"onresize",_23,"_sizeBg")]);
if(w>vpw){
h=h*vpw/w;
w=vpw;
}
if(h>vph){
w=w*vph/h;
h=vph;
}
_23._end={x:(vp.w-20-w)/2+vp.l,y:(vp.h-20-h)/2+vp.t,w:w,h:h};
d.style(bg,{backgroundColor:"#000",opacity:0,position:"absolute",zIndex:9999998});
d.body().appendChild(bg);
_23._bg=bg;
_23._sizeBg();
d.fx.combine([_23._anim(_23._img,_23._coords(_23._start,_23._end)),_23._anim(bg,{opacity:0.5})]).play();
},_sizeBg:function(){
var dd=d.doc.documentElement;
d.style(this._bg,{top:0,left:0,width:dd.scrollWidth+"px",height:dd.scrollHeight+"px"});
},_key:function(e){
d.stopEvent(e);
this._hide();
},_coords:function(s,e){
return {left:{start:s.x,end:e.x},top:{start:s.y,end:e.y},width:{start:s.w,end:e.w},height:{start:s.h,end:e.h}};
},_hide:function(){
var _30=this;
d.forEach(_30._connects,function(e){
d.disconnect(e);
});
_30._connects=[];
d.fx.combine([_30._anim(_30._img,_30._coords(_30._end,_30._start),"_reset"),_30._anim(_30._bg,{opacity:0})]).play();
},_reset:function(){
d.style(this._node,"visibility","visible");
d.forEach([this._img,this._bg],function(n){
d.destroy(n);
n=null;
});
this._node.focus();
},_anim:function(_33,_34,_35){
return d.animateProperty({node:_33,duration:this.duration,properties:_34,onEnd:_35?d.hitch(this,_35):null});
}});
})(dojo);
}
