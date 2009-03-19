/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.place"]){
dojo._hasResource["dijit._base.place"]=true;
dojo.provide("dijit._base.place");
dojo.require("dojo.AdapterRegistry");
dijit.getViewport=function(){
var _1=(dojo.doc.compatMode=="BackCompat")?dojo.body():dojo.doc.documentElement;
var _2=dojo._docScroll();
return {w:_1.clientWidth,h:_1.clientHeight,l:_2.x,t:_2.y};
};
dijit.placeOnScreen=function(_3,_4,_5,_6){
var _7=dojo.map(_5,function(_8){
var c={corner:_8,pos:{x:_4.x,y:_4.y}};
if(_6){
c.pos.x+=_8.charAt(1)=="L"?_6.x:-_6.x;
c.pos.y+=_8.charAt(0)=="T"?_6.y:-_6.y;
}
return c;
});
return dijit._place(_3,_7);
};
dijit._place=function(_a,_b,_c){
var _d=dijit.getViewport();
if(!_a.parentNode||String(_a.parentNode.tagName).toLowerCase()!="body"){
dojo.body().appendChild(_a);
}
var _e=null;
dojo.some(_b,function(_f){
var _10=_f.corner;
var pos=_f.pos;
if(_c){
_c(_a,_f.aroundCorner,_10);
}
var _12=_a.style;
var _13=_12.display;
var _14=_12.visibility;
_12.visibility="hidden";
_12.display="";
var mb=dojo.marginBox(_a);
_12.display=_13;
_12.visibility=_14;
var _16=(_10.charAt(1)=="L"?pos.x:Math.max(_d.l,pos.x-mb.w)),_17=(_10.charAt(0)=="T"?pos.y:Math.max(_d.t,pos.y-mb.h)),_18=(_10.charAt(1)=="L"?Math.min(_d.l+_d.w,_16+mb.w):pos.x),_19=(_10.charAt(0)=="T"?Math.min(_d.t+_d.h,_17+mb.h):pos.y),_1a=_18-_16,_1b=_19-_17,_1c=(mb.w-_1a)+(mb.h-_1b);
if(_e==null||_1c<_e.overflow){
_e={corner:_10,aroundCorner:_f.aroundCorner,x:_16,y:_17,w:_1a,h:_1b,overflow:_1c};
}
return !_1c;
});
_a.style.left=_e.x+"px";
_a.style.top=_e.y+"px";
if(_e.overflow&&_c){
_c(_a,_e.aroundCorner,_e.corner);
}
return _e;
};
dijit.placeOnScreenAroundNode=function(_1d,_1e,_1f,_20){
_1e=dojo.byId(_1e);
var _21=_1e.style.display;
_1e.style.display="";
var _22=_1e.offsetWidth;
var _23=_1e.offsetHeight;
var _24=dojo.coords(_1e,true);
_1e.style.display=_21;
return dijit._placeOnScreenAroundRect(_1d,_24.x,_24.y,_22,_23,_1f,_20);
};
dijit.placeOnScreenAroundRectangle=function(_25,_26,_27,_28){
return dijit._placeOnScreenAroundRect(_25,_26.x,_26.y,_26.width,_26.height,_27,_28);
};
dijit._placeOnScreenAroundRect=function(_29,x,y,_2c,_2d,_2e,_2f){
var _30=[];
for(var _31 in _2e){
_30.push({aroundCorner:_31,corner:_2e[_31],pos:{x:x+(_31.charAt(1)=="L"?0:_2c),y:y+(_31.charAt(0)=="T"?0:_2d)}});
}
return dijit._place(_29,_30,_2f);
};
dijit.placementRegistry=new dojo.AdapterRegistry();
dijit.placementRegistry.register("node",function(n,x){
return typeof x=="object"&&typeof x.offsetWidth!="undefined"&&typeof x.offsetHeight!="undefined";
},dijit.placeOnScreenAroundNode);
dijit.placementRegistry.register("rect",function(n,x){
return typeof x=="object"&&"x" in x&&"y" in x&&"width" in x&&"height" in x;
},dijit.placeOnScreenAroundRectangle);
dijit.placeOnScreenAroundElement=function(_36,_37,_38,_39){
return dijit.placementRegistry.match.apply(dijit.placementRegistry,arguments);
};
}
