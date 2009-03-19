/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx._base"]){
dojo._hasResource["dojox.fx._base"]=true;
dojo.provide("dojox.fx._base");
dojo.require("dojo.fx");
dojo.mixin(dojox.fx,{anim:dojo.anim,animateProperty:dojo.animateProperty,fadeTo:dojo._fade,fadeIn:dojo.fadeIn,fadeOut:dojo.fadeOut,combine:dojo.fx.combine,chain:dojo.fx.chain,slideTo:dojo.fx.slideTo,wipeIn:dojo.fx.wipeIn,wipeOut:dojo.fx.wipeOut});
dojox.fx.sizeTo=function(_1){
var _2=_1.node=dojo.byId(_1.node);
var _3=_1.method||"chain";
if(!_1.duration){
_1.duration=500;
}
if(_3=="chain"){
_1.duration=Math.floor(_1.duration/2);
}
var _4,_5,_6,_7,_8,_9=null;
var _a=(function(n){
return function(){
var cs=dojo.getComputedStyle(n);
var _d=cs.position;
_4=(_d=="absolute"?n.offsetTop:parseInt(cs.top)||0);
_6=(_d=="absolute"?n.offsetLeft:parseInt(cs.left)||0);
_8=parseInt(cs.width);
_9=parseInt(cs.height);
_7=_6-Math.floor((_1.width-_8)/2);
_5=_4-Math.floor((_1.height-_9)/2);
if(_d!="absolute"&&_d!="relative"){
var _e=dojo.coords(n,true);
_4=_e.y;
_6=_e.x;
n.style.position="absolute";
n.style.top=_4+"px";
n.style.left=_6+"px";
}
};
})(_2);
_a();
var _f=dojo.animateProperty(dojo.mixin({properties:{height:{start:_9,end:_1.height||0,unit:"px"},top:{start:_4,end:_5}}},_1));
var _10=dojo.animateProperty(dojo.mixin({properties:{width:{start:_8,end:_1.width||0,unit:"px"},left:{start:_6,end:_7}}},_1));
var _11=dojo.fx[(_1.method=="combine"?"combine":"chain")]([_f,_10]);
dojo.connect(_11,"beforeBegin",_11,_a);
return _11;
};
dojox.fx.slideBy=function(_12){
var _13=_12.node=dojo.byId(_12.node);
var top=null;
var _15=null;
var _16=(function(n){
return function(){
var cs=dojo.getComputedStyle(n);
var pos=cs.position;
top=(pos=="absolute"?n.offsetTop:parseInt(cs.top)||0);
_15=(pos=="absolute"?n.offsetLeft:parseInt(cs.left)||0);
if(pos!="absolute"&&pos!="relative"){
var ret=dojo.coords(n,true);
top=ret.y;
_15=ret.x;
n.style.position="absolute";
n.style.top=top+"px";
n.style.left=_15+"px";
}
};
})(_13);
_16();
var _1b=dojo.animateProperty(dojo.mixin({properties:{top:top+(_12.top||0),left:_15+(_12.left||0)}},_12));
dojo.connect(_1b,"beforeBegin",_1b,_16);
return _1b;
};
dojox.fx.crossFade=function(_1c){
if(dojo.isArray(_1c.nodes)){
var _1d=_1c.nodes[0]=dojo.byId(_1c.nodes[0]);
var op1=dojo.style(_1d,"opacity");
var _1f=_1c.nodes[1]=dojo.byId(_1c.nodes[1]);
var op2=dojo.style(_1f,"opacity");
var _21=dojo.fx.combine([dojo[(op1==0?"fadeIn":"fadeOut")](dojo.mixin({node:_1d},_1c)),dojo[(op1==0?"fadeOut":"fadeIn")](dojo.mixin({node:_1f},_1c))]);
return _21;
}else{
return false;
}
};
dojox.fx.highlight=function(_22){
var _23=_22.node=dojo.byId(_22.node);
_22.duration=_22.duration||400;
var _24=_22.color||"#ffff99";
var _25=dojo.style(_23,"backgroundColor");
var _26=(_25=="transparent"||_25=="rgba(0, 0, 0, 0)")?_25:false;
var _27=dojo.animateProperty(dojo.mixin({properties:{backgroundColor:{start:_24,end:_25}}},_22));
if(_26){
dojo.connect(_27,"onEnd",_27,function(){
_23.style.backgroundColor=_26;
});
}
return _27;
};
dojox.fx.wipeTo=function(_28){
_28.node=dojo.byId(_28.node);
var _29=_28.node,s=_29.style;
var dir=(_28.width?"width":"height");
var _2c=_28[dir];
var _2d={};
_2d[dir]={start:function(){
s.overflow="hidden";
if(s.visibility=="hidden"||s.display=="none"){
s[dir]="1px";
s.display="";
s.visibility="";
return 1;
}else{
var now=dojo.style(_29,dir);
return Math.max(now,1);
}
},end:_2c,unit:"px"};
var _2f=dojo.animateProperty(dojo.mixin({properties:_2d},_28));
return _2f;
};
}
