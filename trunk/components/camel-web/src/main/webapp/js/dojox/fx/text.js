/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx.text"]){
dojo._hasResource["dojox.fx.text"]=true;
dojo.provide("dojox.fx.text");
dojo.require("dojo.fx");
dojo.require("dojo.fx.easing");
dojox.fx.text._split=function(_1){
var _2=_1.node=dojo.byId(_1.node),s=_2.style,cs=dojo.getComputedStyle(_2),_5=dojo.coords(_2,true);
_1.duration=_1.duration||1000;
_1.words=_1.words||false;
var _6=(_1.text&&typeof (_1.text)=="string")?_1.text:_2.innerHTML,_7=s.height,_8=s.width,_9=[];
dojo.style(_2,{height:cs.height,width:cs.width});
var _a=/(<\/?\w+((\s+\w+(\s*=\s*(?:".*?"|'.*?'|[^'">\s]+))?)+\s*|\s*)\/?>)/g;
var _b=(_1.words?/(<\/?\w+((\s+\w+(\s*=\s*(?:".*?"|'.*?'|[^'">\s]+))?)+\s*|\s*)\/?>)\s*|([^\s<]+\s*)/g:/(<\/?\w+((\s+\w+(\s*=\s*(?:".*?"|'.*?'|[^'">\s]+))?)+\s*|\s*)\/?>)\s*|([^\s<]\s*)/g);
var _c=(typeof _1.text=="string")?_1.text.match(_b):_2.innerHTML.match(_b);
var _d="";
var _e=0;
var _f=0;
for(var i=0;i<_c.length;i++){
var _11=_c[i];
if(!_11.match(_a)){
_d+="<span>"+_11+"</span>";
_e++;
}else{
_d+=_11;
}
}
_2.innerHTML=_d;
function _12(_13){
var _14=_13.nextSibling;
if(_13.tagName=="SPAN"&&_13.childNodes.length==1&&_13.firstChild.nodeType==3){
var _15=dojo.coords(_13,true);
_f++;
dojo.style(_13,{padding:0,margin:0,top:(_1.crop?"0px":_15.t+"px"),left:(_1.crop?"0px":_15.l+"px"),display:"inline"});
var _16=_1.pieceAnimation(_13,_15,_5,_f,_e);
if(dojo.isArray(_16)){
_9=_9.concat(_16);
}else{
_9[_9.length]=_16;
}
}else{
if(_13.firstChild){
_12(_13.firstChild);
}
}
if(_14){
_12(_14);
}
};
_12(_2.firstChild);
var _17=dojo.fx.combine(_9);
dojo.connect(_17,"onEnd",_17,function(){
_2.innerHTML=_6;
dojo.style(_2,{height:_7,width:_8});
});
if(_1.onPlay){
dojo.connect(_17,"onPlay",_17,_1.onPlay);
}
if(_1.onEnd){
dojo.connect(_17,"onEnd",_17,_1.onEnd);
}
return _17;
};
dojox.fx.text.explode=function(_18){
var _19=_18.node=dojo.byId(_18.node);
var s=_19.style;
_18.distance=_18.distance||1;
_18.duration=_18.duration||1000;
_18.random=_18.random||0;
if(typeof (_18.fade)=="undefined"){
_18.fade=true;
}
if(typeof (_18.sync)=="undefined"){
_18.sync=true;
}
_18.random=Math.abs(_18.random);
_18.pieceAnimation=function(_1b,_1c,_1d,_1e,_1f){
var _20=_1c.h;
var _21=_1c.w;
var _22=_18.distance*2;
var _23=_18.duration;
var _24=parseFloat(_1b.style.top);
var _25=parseFloat(_1b.style.left);
var _26=0;
var _27=0;
var _28=0;
if(_18.random){
var _29=(Math.random()*_18.random)+Math.max(1-_18.random,0);
_22*=_29;
_23*=_29;
_26=((_18.unhide&&_18.sync)||(!_18.unhide&&!_18.sync))?(_18.duration-_23):0;
_27=Math.random()-0.5;
_28=Math.random()-0.5;
}
var _2a=((_1d.h-_20)/2-(_1c.y-_1d.y));
var _2b=((_1d.w-_21)/2-(_1c.x-_1d.x));
var _2c=Math.sqrt(Math.pow(_2b,2)+Math.pow(_2a,2));
var _2d=_24-_2a*_22+_2c*_28;
var _2e=_25-_2b*_22+_2c*_27;
var _2f=dojo.animateProperty({node:_1b,duration:_23,delay:_26,easing:(_18.easing||(_18.unhide?dojo.fx.easing.sinOut:dojo.fx.easing.circOut)),beforeBegin:(_18.unhide?function(){
if(_18.fade){
dojo.style(_1b,"opacity",0);
}
_1b.style.position=_18.crop?"relative":"absolute";
_1b.style.top=_2d+"px";
_1b.style.left=_2e+"px";
}:function(){
_1b.style.position=_18.crop?"relative":"absolute";
}),properties:{top:(_18.unhide?{start:_2d,end:_24}:{start:_24,end:_2d}),left:(_18.unhide?{start:_2e,end:_25}:{start:_25,end:_2e})}});
if(_18.fade){
var _30=dojo.animateProperty({node:_1b,duration:_23,delay:_26,easing:(_18.fadeEasing||dojo.fx.easing.quadOut),properties:{opacity:(_18.unhide?{start:0,end:1}:{end:0})}});
return (_18.unhide?[_30,_2f]:[_2f,_30]);
}else{
return _2f;
}
};
var _31=dojox.fx.text._split(_18);
return _31;
};
dojox.fx.text.converge=function(_32){
_32.unhide=true;
return dojox.fx.text.explode(_32);
};
dojox.fx.text.disintegrate=function(_33){
var _34=_33.node=dojo.byId(_33.node);
var s=_34.style;
_33.duration=_33.duration||1500;
_33.distance=_33.distance||1.5;
_33.random=_33.random||0;
if(!_33.fade){
_33.fade=true;
}
var _36=Math.abs(_33.random);
_33.pieceAnimation=function(_37,_38,_39,_3a,_3b){
var _3c=_38.h;
var _3d=_38.w;
var _3e=_33.interval||(_33.duration/(1.5*_3b));
var _3f=(_33.duration-_3b*_3e);
var _40=Math.random()*_3b*_3e;
var _41=(_33.reverseOrder||_33.distance<0)?(_3a*_3e):((_3b-_3a)*_3e);
var _42=_40*_36+Math.max(1-_36,0)*_41;
var _43={};
if(_33.unhide){
_43.top={start:(parseFloat(_37.style.top)-_39.h*_33.distance),end:parseFloat(_37.style.top)};
if(_33.fade){
_43.opacity={start:0,end:1};
}
}else{
_43.top={end:(parseFloat(_37.style.top)+_39.h*_33.distance)};
if(_33.fade){
_43.opacity={end:0};
}
}
var _44=dojo.animateProperty({node:_37,duration:_3f,delay:_42,easing:(_33.easing||(_33.unhide?dojo.fx.easing.sinIn:dojo.fx.easing.circIn)),properties:_43,beforeBegin:(_33.unhide?function(){
if(_33.fade){
dojo.style(_37,"opacity",0);
}
_37.style.position=_33.crop?"relative":"absolute";
_37.style.top=_43.top.start+"px";
}:function(){
_37.style.position=_33.crop?"relative":"absolute";
})});
return _44;
};
var _45=dojox.fx.text._split(_33);
return _45;
};
dojox.fx.text.build=function(_46){
_46.unhide=true;
return dojox.fx.text.disintegrate(_46);
};
dojox.fx.text.blockFadeOut=function(_47){
var _48=_47.node=dojo.byId(_47.node);
var s=_48.style;
_47.duration=_47.duration||1000;
_47.random=_47.random||0;
var _4a=Math.abs(_47.random);
_47.pieceAnimation=function(_4b,_4c,_4d,_4e,_4f){
var _50=_47.interval||(_47.duration/(1.5*_4f));
var _51=(_47.duration-_4f*_50);
var _52=Math.random()*_4f*_50;
var _53=(_47.reverseOrder)?((_4f-_4e)*_50):(_4e*_50);
var _54=_52*_4a+Math.max(1-_4a,0)*_53;
var _55=dojo.animateProperty({node:_4b,duration:_51,delay:_54,easing:(_47.easing||dojo.fx.easing.sinInOut),properties:{opacity:(_47.unhide?{start:0,end:1}:{end:0})},beforeBegin:(_47.unhide?function(){
dojo.style(_4b,"opacity",0);
}:undefined)});
return _55;
};
var _56=dojox.fx.text._split(_47);
return _56;
};
dojox.fx.text.blockFadeIn=function(_57){
_57.unhide=true;
return dojox.fx.text.blockFadeOut(_57);
};
dojox.fx.text.backspace=function(_58){
var _59=_58.node=dojo.byId(_58.node);
var s=_59.style;
_58.words=false;
_58.duration=_58.duration||2000;
_58.random=_58.random||0;
var _5b=Math.abs(_58.random);
var _5c=10;
_58.pieceAnimation=function(_5d,_5e,_5f,_60,_61){
var _62=_58.interval||(_58.duration/(1.5*_61));
var _63=_5d.textContent;
var _64=_63.match(/\s/g);
if(typeof (_58.wordDelay)=="undefined"){
_58.wordDelay=_62*2;
}
if(!_58.unhide){
_5c=(_61-_60-1)*_62;
}
var _65,_66;
if(_58.fixed){
if(_58.unhide){
var _65=function(){
dojo.style(_5d,"opacity",0);
};
}
}else{
if(_58.unhide){
var _65=function(){
_5d.style.display="none";
};
var _66=function(){
_5d.style.display="inline";
};
}else{
var _66=function(){
_5d.style.display="none";
};
}
}
var _67=dojo.animateProperty({node:_5d,duration:1,delay:_5c,easing:(_58.easing||dojo.fx.easing.sinInOut),properties:{opacity:(_58.unhide?{start:0,end:1}:{end:0})},beforeBegin:_65,onEnd:_66});
if(_58.unhide){
var _68=Math.random()*_63.length*_62;
var _69=_68*_5b/2+Math.max(1-_5b/2,0)*_58.wordDelay;
_5c+=_68*_5b+Math.max(1-_5b,0)*_62*_63.length+(_69*(_64&&_63.lastIndexOf(_64[_64.length-1])==_63.length-1));
}
return _67;
};
var _6a=dojox.fx.text._split(_58);
return _6a;
};
dojox.fx.text.type=function(_6b){
_6b.unhide=true;
return dojox.fx.text.backspace(_6b);
};
}
