/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.scroll"]){
dojo._hasResource["dijit._base.scroll"]=true;
dojo.provide("dijit._base.scroll");
dijit.scrollIntoView=function(_1){
try{
_1=dojo.byId(_1);
var _2=dojo.doc;
var _3=dojo.body();
var _4=_3.parentNode;
if((!(dojo.isFF>=3||dojo.isIE||dojo.isWebKit)||_1==_3||_1==_4)&&(typeof _1.scrollIntoView=="function")){
_1.scrollIntoView(false);
return;
}
var _5=dojo._isBodyLtr();
var _6=!(_5||(dojo.isIE>=8&&!_7));
var _8=_3;
var _7=_2.compatMode=="BackCompat";
if(_7){
_4._offsetWidth=_4._clientWidth=_3._offsetWidth=_3.clientWidth;
_4._offsetHeight=_4._clientHeight=_3._offsetHeight=_3.clientHeight;
}else{
if(dojo.isWebKit){
_3._offsetWidth=_3._clientWidth=_4.clientWidth;
_3._offsetHeight=_3._clientHeight=_4.clientHeight;
}else{
_8=_4;
}
_4._offsetHeight=_4.clientHeight;
_4._offsetWidth=_4.clientWidth;
}
function _9(_a){
var ie=dojo.isIE;
return ((ie<=6||(ie>=7&&_7))?false:(dojo.style(_a,"position").toLowerCase()=="fixed"));
};
function _c(_d){
var _e=_d.parentNode;
var _f=_d.offsetParent;
if(_f==null||_9(_d)){
_f=_4;
_e=(_d==_3)?_4:null;
}
_d._offsetParent=_f;
_d._parent=_e;
var bp=dojo._getBorderExtents(_d);
_d._borderStart={H:(dojo.isIE>=8&&!_5&&!_7)?(bp.w-bp.l):bp.l,V:bp.t};
_d._borderSize={H:bp.w,V:bp.h};
_d._offsetStart={H:_d.offsetLeft,V:_d.offsetTop};
_d._scrolledAmount={H:_d.scrollLeft,V:_d.scrollTop};
_d._offsetSize={H:_d._offsetWidth||_d.offsetWidth,V:_d._offsetHeight||_d.offsetHeight};
_d._clientSize={H:_d._clientWidth||_d.clientWidth,V:_d._clientHeight||_d.clientHeight};
if(_d!=_3&&_d!=_4&&_d!=_1){
for(var dir in _d._offsetSize){
var _12=_d._offsetSize[dir]-_d._clientSize[dir]-_d._borderSize[dir];
var _13=_d._clientSize[dir]>0&&_12>0;
if(_13){
_d._offsetSize[dir]-=_12;
if(dojo.isIE&&_6&&dir=="H"){
_d._offsetStart[dir]+=_12;
}
}
}
}
};
var _14=_1;
while(_14!=null){
if(_9(_14)){
_1.scrollIntoView(false);
return;
}
_c(_14);
_14=_14._parent;
}
if(dojo.isIE&&_1._parent){
var _15=_1._offsetParent;
_1._offsetStart.H+=_15._borderStart.H;
_1._offsetStart.V+=_15._borderStart.V;
}
if(dojo.isIE>=7&&_8==_4&&_6&&_3._offsetStart&&_3._offsetStart.H==0){
var _16=_4.scrollWidth-_4._offsetSize.H;
if(_16>0){
_3._offsetStart.H=-_16;
}
}
if(dojo.isIE<=6&&!_7){
_4._offsetSize.H+=_4._borderSize.H;
_4._offsetSize.V+=_4._borderSize.V;
}
if(_6&&_3._offsetStart&&_8==_4&&_4._scrolledAmount){
var ofs=_3._offsetStart.H;
if(ofs<0){
_4._scrolledAmount.H+=ofs;
_3._offsetStart.H=0;
}
}
_14=_1;
while(_14){
var _18=_14._parent;
if(!_18){
break;
}
if(_18.tagName=="TD"){
var _19=_18._parent._parent._parent;
if(_18!=_14._offsetParent&&_18._offsetParent!=_14._offsetParent){
_18=_19;
}
}
var _1a=_14._offsetParent==_18;
for(var dir in _14._offsetStart){
if(dojo.isIE>=8&&!_1a&&!_7){
_18._scrolledAmount[dir]=0;
}
var _1c=false;
var _1d=dir=="H"?"V":"H";
if(_6&&dir=="H"&&(_18!=_4)&&(_18!=_3)&&(dojo.isIE||dojo.isWebKit)&&_18._clientSize.H>0&&_18.scrollWidth>_18._clientSize.H){
var _1e=_18.scrollWidth-_18._clientSize.H;
if(_1e>0){
_18._scrolledAmount.H-=_1e;
_1c=true;
}
}
if(_18._offsetParent.tagName=="TABLE"){
if(dojo.isIE>=8&&!_7){
}else{
if(dojo.isIE){
_18._offsetStart[dir]-=_18._offsetParent._borderStart[dir];
_18._borderStart[dir]=_18._borderSize[dir]=0;
}else{
_18._offsetStart[dir]+=_18._offsetParent._borderStart[dir];
}
}
}
if(dojo.isIE){
_18._offsetStart[dir]+=_18._offsetParent._borderStart[dir];
}
var _1f=_14._offsetStart[dir]-_18._scrolledAmount[dir]-(_1a?0:_18._offsetStart[dir])-_18._borderStart[dir];
var _20=_1f+_14._offsetSize[dir]-_18._offsetSize[dir]+_18._borderSize[dir];
var _21=(dir=="H")?"scrollLeft":"scrollTop";
var _22=dir=="H"&&_6;
var _23=_22?-_20:_1f;
var _24=_22?-_1f:_20;
var _25=(_23*_24<=0)?0:Math[(_23<0)?"max":"min"](_23,_24);
if(_25!=0){
var _26=_18[_21];
_18[_21]+=(_22)?-_25:_25;
var _27=_18[_21]-_26;
}
if(_1a){
_14._offsetStart[dir]+=_18._offsetStart[dir];
}
_14._offsetStart[dir]-=_18[_21];
}
_14._parent=_18._parent;
_14._offsetParent=_18._offsetParent;
}
_18=_1;
while(_18&&_18.removeAttribute){
next=_18.parentNode;
_18.removeAttribute("_offsetParent");
_18.removeAttribute("_parent");
_18=next;
}
}
catch(error){
console.error("scrollIntoView: "+error);
_1.scrollIntoView(false);
}
};
}
