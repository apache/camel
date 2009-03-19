/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx.split"]){
dojo._hasResource["dojox.fx.split"]=true;
dojo.provide("dojox.fx.split");
dojo.require("dojo.fx");
dojo.require("dojo.fx.easing");
dojo.mixin(dojox.fx,{_split:function(_1){
_1.rows=_1.rows||3;
_1.columns=_1.columns||3;
_1.duration=_1.duration||1000;
var _2=_1.node=dojo.byId(_1.node),_3=dojo.coords(_2,true),_4=Math.ceil(_3.h/_1.rows),_5=Math.ceil(_3.w/_1.columns),_6=dojo.create(_2.tagName),_7=[],_8=dojo.create(_2.tagName),_9;
dojo.style(_6,{position:"absolute",padding:"0",margin:"0",border:"none",top:_3.y+"px",left:_3.x+"px",height:_3.h+"px",width:_3.w+"px",background:"none",overflow:_1.crop?"hidden":"visible"});
_2.parentNode.appendChild(_6);
dojo.style(_8,{position:"absolute",border:"none",padding:"0",margin:"0",height:_4+"px",width:_5+"px",overflow:"hidden"});
for(var y=0;y<_1.rows;y++){
for(var x=0;x<_1.columns;x++){
_9=dojo.clone(_8);
pieceContents=dojo.clone(_2);
pieceContents.style.filter="";
dojo.style(_9,{border:"none",overflow:"hidden",top:(_4*y)+"px",left:(_5*x)+"px"});
dojo.style(pieceContents,{position:"static",opacity:"1",marginTop:(-y*_4)+"px",marginLeft:(-x*_5)+"px"});
_9.appendChild(pieceContents);
_6.appendChild(_9);
var _c=_1.pieceAnimation(_9,x,y,_3);
if(dojo.isArray(_c)){
_7=_7.concat(_c);
}else{
_7.push(_c);
}
}
}
var _d=dojo.fx.combine(_7);
dojo.connect(_d,"onEnd",_d,function(){
_6.parentNode.removeChild(_6);
});
if(_1.onPlay){
dojo.connect(_d,"onPlay",_d,_1.onPlay);
}
if(_1.onEnd){
dojo.connect(_d,"onEnd",_d,_1.onEnd);
}
return _d;
},explode:function(_e){
var _f=_e.node=dojo.byId(_e.node);
_e.rows=_e.rows||3;
_e.columns=_e.columns||3;
_e.distance=_e.distance||1;
_e.duration=_e.duration||1000;
_e.random=_e.random||0;
if(!_e.fade){
_e.fade=true;
}
if(typeof _e.sync=="undefined"){
_e.sync=true;
}
_e.random=Math.abs(_e.random);
_e.pieceAnimation=function(_10,x,y,_13){
var _14=_13.h/_e.rows,_15=_13.w/_e.columns,_16=_e.distance*2,_17=_e.duration,ps=_10.style,_19=parseInt(ps.top),_1a=parseInt(ps.left),_1b=0,_1c=0,_1d=0;
if(_e.random){
var _1e=(Math.random()*_e.random)+Math.max(1-_e.random,0);
_16*=_1e;
_17*=_1e;
_1b=((_e.unhide&&_e.sync)||(!_e.unhide&&!_e.sync))?(_e.duration-_17):0;
_1c=Math.random()-0.5;
_1d=Math.random()-0.5;
}
var _1f=((_13.h-_14)/2-_14*y),_20=((_13.w-_15)/2-_15*x),_21=Math.sqrt(Math.pow(_20,2)+Math.pow(_1f,2)),_22=parseInt(_19-_1f*_16+_21*_1d),_23=parseInt(_1a-_20*_16+_21*_1c);
var _24=dojo.animateProperty({node:_10,duration:_17,delay:_1b,easing:(_e.easing||(_e.unhide?dojo.fx.easing.sinOut:dojo.fx.easing.circOut)),beforeBegin:(_e.unhide?function(){
if(_e.fade){
dojo.style(_10,{opacity:"0"});
}
ps.top=_22+"px";
ps.left=_23+"px";
}:undefined),properties:{top:(_e.unhide?{start:_22,end:_19}:{start:_19,end:_22}),left:(_e.unhide?{start:_23,end:_1a}:{start:_1a,end:_23})}});
if(_e.fade){
var _25=dojo.animateProperty({node:_10,duration:_17,delay:_1b,easing:(_e.fadeEasing||dojo.fx.easing.quadOut),properties:{opacity:(_e.unhide?{start:"0",end:"1"}:{start:"1",end:"0"})}});
return (_e.unhide?[_25,_24]:[_24,_25]);
}else{
return _24;
}
};
var _26=dojox.fx._split(_e);
if(_e.unhide){
dojo.connect(_26,"onEnd",null,function(){
dojo.style(_f,{opacity:"1"});
});
}else{
dojo.connect(_26,"onPlay",null,function(){
dojo.style(_f,{opacity:"0"});
});
}
return _26;
},converge:function(_27){
_27.unhide=true;
return dojox.fx.explode(_27);
},disintegrate:function(_28){
var _29=_28.node=dojo.byId(_28.node);
_28.rows=_28.rows||5;
_28.columns=_28.columns||5;
_28.duration=_28.duration||1500;
_28.interval=_28.interval||_28.duration/(_28.rows+_28.columns*2);
_28.distance=_28.distance||1.5;
_28.random=_28.random||0;
if(typeof _28.fade=="undefined"){
_28.fade=true;
}
var _2a=Math.abs(_28.random),_2b=_28.duration-(_28.rows+_28.columns)*_28.interval;
_28.pieceAnimation=function(_2c,x,y,_2f){
var _30=Math.random()*(_28.rows+_28.columns)*_28.interval,ps=_2c.style,_32=(_28.reverseOrder||_28.distance<0)?((x+y)*_28.interval):(((_28.rows+_28.columns)-(x+y))*_28.interval),_33=_30*_2a+Math.max(1-_2a,0)*_32,_34={};
if(_28.unhide){
_34.top={start:(parseInt(ps.top)-_2f.h*_28.distance),end:parseInt(ps.top)};
if(_28.fade){
_34.opacity={start:"0",end:"1"};
}
}else{
_34.top={end:(parseInt(ps.top)+_2f.h*_28.distance)};
if(_28.fade){
_34.opacity={end:"0"};
}
}
var _35=dojo.animateProperty({node:_2c,duration:_2b,delay:_33,easing:(_28.easing||(_28.unhide?dojo.fx.easing.sinIn:dojo.fx.easing.circIn)),properties:_34,beforeBegin:(_28.unhide?function(){
if(_28.fade){
dojo.style(_2c,{opacity:"0"});
}
ps.top=_34.top.start+"px";
}:undefined)});
return _35;
};
var _36=dojox.fx._split(_28);
if(_28.unhide){
dojo.connect(_36,"onEnd",_36,function(){
dojo.style(_29,{opacity:"1"});
});
}else{
dojo.connect(_36,"onPlay",_36,function(){
dojo.style(_29,{opacity:"0"});
});
}
return _36;
},build:function(_37){
_37.unhide=true;
return dojox.fx.disintegrate(_37);
},shear:function(_38){
var _39=_38.node=dojo.byId(_38.node);
_38.rows=_38.rows||6;
_38.columns=_38.columns||6;
_38.duration=_38.duration||1000;
_38.interval=_38.interval||0;
_38.distance=_38.distance||1;
_38.random=_38.random||0;
if(typeof (_38.fade)=="undefined"){
_38.fade=true;
}
var _3a=Math.abs(_38.random),_3b=(_38.duration-(_38.rows+_38.columns)*Math.abs(_38.interval));
_38.pieceAnimation=function(_3c,x,y,_3f){
var _40=!(x%2),_41=!(y%2),_42=Math.random()*_3b,_43=(_38.reverseOrder)?(((_38.rows+_38.columns)-(x+y))*_38.interval):((x+y)*_38.interval),_44=_42*_3a+Math.max(1-_3a,0)*_43,_45={},ps=_3c.style;
if(_38.fade){
_45.opacity=(_38.unhide?{start:"0",end:"1"}:{end:"0"});
}
if(_38.columns==1){
_40=_41;
}else{
if(_38.rows==1){
_41=!_40;
}
}
var _47=parseInt(ps.left),top=parseInt(ps.top),_49=_38.distance*_3f.w,_4a=_38.distance*_3f.h;
if(_38.unhide){
if(_40==_41){
_45.left=_40?{start:(_47-_49),end:_47}:{start:(_47+_49),end:_47};
}else{
_45.top=_40?{start:(top+_4a),end:top}:{start:(top-_4a),end:top};
}
}else{
if(_40==_41){
_45.left=_40?{end:(_47-_49)}:{end:(_47+_49)};
}else{
_45.top=_40?{end:(top+_4a)}:{end:(top-_4a)};
}
}
var _4b=dojo.animateProperty({node:_3c,duration:_3b,delay:_44,easing:(_38.easing||dojo.fx.easing.sinInOut),properties:_45,beforeBegin:(_38.unhide?function(){
if(_38.fade){
ps.opacity="0";
}
if(_40==_41){
ps.left=_45.left.start+"px";
}else{
ps.top=_45.top.start+"px";
}
}:undefined)});
return _4b;
};
var _4c=dojox.fx._split(_38);
if(_38.unhide){
dojo.connect(_4c,"onEnd",_4c,function(){
dojo.style(_39,{opacity:"1"});
});
}else{
dojo.connect(_4c,"onPlay",_4c,function(){
dojo.style(_39,{opacity:"0"});
});
}
return _4c;
},unShear:function(_4d){
_4d.unhide=true;
return dojox.fx.shear(_4d);
},pinwheel:function(_4e){
var _4f=_4e.node=dojo.byId(_4e.node);
_4e.rows=_4e.rows||4;
_4e.columns=_4e.columns||4;
_4e.duration=_4e.duration||1000;
_4e.interval=_4e.interval||0;
_4e.distance=_4e.distance||1;
_4e.random=_4e.random||0;
if(typeof _4e.fade=="undefined"){
_4e.fade=true;
}
var _50=(_4e.duration-(_4e.rows+_4e.columns)*Math.abs(_4e.interval));
_4e.pieceAnimation=function(_51,x,y,_54){
var _55=_54.h/_4e.rows,_56=_54.w/_4e.columns,_57=!(x%2),_58=!(y%2),_59=Math.random()*_50,_5a=(_4e.interval<0)?(((_4e.rows+_4e.columns)-(x+y))*_4e.interval*-1):((x+y)*_4e.interval),_5b=_59*_4e.random+Math.max(1-_4e.random,0)*_5a,_5c={},ps=_51.style;
if(_4e.fade){
_5c.opacity=(_4e.unhide?{start:0,end:1}:{end:0});
}
if(_4e.columns==1){
_57=!_58;
}else{
if(_4e.rows==1){
_58=_57;
}
}
var _5e=parseInt(ps.left),top=parseInt(ps.top);
if(_57){
if(_58){
_5c.top=_4e.unhide?{start:top+_55*_4e.distance,end:top}:{start:top,end:top+_55*_4e.distance};
}else{
_5c.left=_4e.unhide?{start:_5e+_56*_4e.distance,end:_5e}:{start:_5e,end:_5e+_56*_4e.distance};
}
}
if(_57!=_58){
_5c.width=_4e.unhide?{start:_56*(1-_4e.distance),end:_56}:{start:_56,end:_56*(1-_4e.distance)};
}else{
_5c.height=_4e.unhide?{start:_55*(1-_4e.distance),end:_55}:{start:_55,end:_55*(1-_4e.distance)};
}
var _60=dojo.animateProperty({node:_51,duration:_50,delay:_5b,easing:(_4e.easing||dojo.fx.easing.sinInOut),properties:_5c,beforeBegin:(_4e.unhide?function(){
if(_4e.fade){
dojo.style(_51,"opacity",0);
}
if(_57){
if(_58){
ps.top=(top+_55*(1-_4e.distance))+"px";
}else{
ps.left=(_5e+_56*(1-_4e.distance))+"px";
}
}else{
ps.left=_5e+"px";
ps.top=top+"px";
}
if(_57!=_58){
ps.width=(_56*(1-_4e.distance))+"px";
}else{
ps.height=(_55*(1-_4e.distance))+"px";
}
}:undefined)});
return _60;
};
var _61=dojox.fx._split(_4e);
if(_4e.unhide){
dojo.connect(_61,"onEnd",_61,function(){
dojo.style(_4f,{opacity:"1"});
});
}else{
dojo.connect(_61,"play",_61,function(){
dojo.style(_4f,{opacity:"0"});
});
}
return _61;
},unPinwheel:function(_62){
_62.unhide=true;
return dojox.fx.pinwheel(_62);
},blockFadeOut:function(_63){
var _64=_63.node=dojo.byId(_63.node);
_63.rows=_63.rows||5;
_63.columns=_63.columns||5;
_63.duration=_63.duration||1000;
_63.interval=_63.interval||_63.duration/(_63.rows+_63.columns*2);
_63.random=_63.random||0;
var _65=Math.abs(_63.random),_66=_63.duration-(_63.rows+_63.columns)*_63.interval;
_63.pieceAnimation=function(_67,x,y,_6a){
var _6b=Math.random()*_63.duration,_6c=(_63.reverseOrder)?(((_63.rows+_63.columns)-(x+y))*Math.abs(_63.interval)):((x+y)*_63.interval),_6d=_6b*_65+Math.max(1-_65,0)*_6c,_6e=dojo.animateProperty({node:_67,duration:_66,delay:_6d,easing:(_63.easing||dojo.fx.easing.sinInOut),properties:{opacity:(_63.unhide?{start:"0",end:"1"}:{start:"1",end:"0"})},beforeBegin:(_63.unhide?function(){
dojo.style(_67,{opacity:"0"});
}:function(){
_67.style.filter="";
})});
return _6e;
};
var _6f=dojox.fx._split(_63);
if(_63.unhide){
dojo.connect(_6f,"onEnd",_6f,function(){
dojo.style(_64,{opacity:"1"});
});
}else{
dojo.connect(_6f,"onPlay",_6f,function(){
dojo.style(_64,{opacity:"0"});
});
}
return _6f;
},blockFadeIn:function(_70){
_70.unhide=true;
return dojox.fx.blockFadeOut(_70);
}});
}
