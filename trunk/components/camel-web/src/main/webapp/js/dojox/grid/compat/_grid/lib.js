/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.lib"]){
dojo._hasResource["dojox.grid.compat._grid.lib"]=true;
dojo.provide("dojox.grid.compat._grid.lib");
dojo.mixin(dojox.grid,{na:"...",nop:function(){
},getTdIndex:function(td){
return td.cellIndex>=0?td.cellIndex:dojo.indexOf(td.parentNode.cells,td);
},getTrIndex:function(tr){
return tr.rowIndex>=0?tr.rowIndex:dojo.indexOf(tr.parentNode.childNodes,tr);
},getTr:function(_3,_4){
return _3&&((_3.rows||0)[_4]||_3.childNodes[_4]);
},getTd:function(_5,_6,_7){
return (dojox.grid.getTr(inTable,_6)||0)[_7];
},findTable:function(_8){
for(var n=_8;n&&n.tagName!="TABLE";n=n.parentNode){
}
return n;
},ascendDom:function(_a,_b){
for(var n=_a;n&&_b(n);n=n.parentNode){
}
return n;
},makeNotTagName:function(_d){
var _e=_d.toUpperCase();
return function(_f){
return _f.tagName!=_e;
};
},fire:function(ob,ev,_12){
var fn=ob&&ev&&ob[ev];
return fn&&(_12?fn.apply(ob,_12):ob[ev]());
},setStyleText:function(_14,_15){
if(_14.style.cssText==undefined){
_14.setAttribute("style",_15);
}else{
_14.style.cssText=_15;
}
},getStyleText:function(_16,_17){
return (_16.style.cssText==undefined?_16.getAttribute("style"):_16.style.cssText);
},setStyle:function(_18,_19,_1a){
if(_18&&_18.style[_19]!=_1a){
_18.style[_19]=_1a;
}
},setStyleHeightPx:function(_1b,_1c){
if(_1c>=0){
dojox.grid.setStyle(_1b,"height",_1c+"px");
}
},mouseEvents:["mouseover","mouseout","mousedown","mouseup","click","dblclick","contextmenu"],keyEvents:["keyup","keydown","keypress"],funnelEvents:function(_1d,_1e,_1f,_20){
var _21=(_20?_20:dojox.grid.mouseEvents.concat(dojox.grid.keyEvents));
for(var i=0,l=_21.length;i<l;i++){
dojo.connect(_1d,"on"+_21[i],_1e,_1f);
}
},removeNode:function(_24){
_24=dojo.byId(_24);
_24&&_24.parentNode&&_24.parentNode.removeChild(_24);
return _24;
},getScrollbarWidth:function(){
if(this._scrollBarWidth){
return this._scrollBarWidth;
}
this._scrollBarWidth=18;
try{
var e=document.createElement("div");
e.style.cssText="top:0;left:0;width:100px;height:100px;overflow:scroll;position:absolute;visibility:hidden;";
document.body.appendChild(e);
this._scrollBarWidth=e.offsetWidth-e.clientWidth;
document.body.removeChild(e);
delete e;
}
catch(ex){
}
return this._scrollBarWidth;
},getRef:function(_26,_27,_28){
var obj=_28||dojo.global,_2a=_26.split("."),_2b=_2a.pop();
for(var i=0,p;obj&&(p=_2a[i]);i++){
obj=(p in obj?obj[p]:(_27?obj[p]={}:undefined));
}
return {obj:obj,prop:_2b};
},getProp:function(_2e,_2f,_30){
with(dojox.grid.getRef(_2e,_2f,_30)){
return (obj)&&(prop)&&(prop in obj?obj[prop]:(_2f?obj[prop]={}:undefined));
}
},indexInParent:function(_31){
var i=0,n,p=_31.parentNode;
while((n=p.childNodes[i++])){
if(n==_31){
return i-1;
}
}
return -1;
},cleanNode:function(_35){
if(!_35){
return;
}
var _36=function(inW){
return inW.domNode&&dojo.isDescendant(inW.domNode,_35,true);
};
var ws=dijit.registry.filter(_36);
for(var i=0,w;(w=ws[i]);i++){
w.destroy();
}
delete ws;
},getTagName:function(_3b){
var _3c=dojo.byId(_3b);
return (_3c&&_3c.tagName?_3c.tagName.toLowerCase():"");
},nodeKids:function(_3d,_3e){
var _3f=[];
var i=0,n;
while((n=_3d.childNodes[i++])){
if(dojox.grid.getTagName(n)==_3e){
_3f.push(n);
}
}
return _3f;
},divkids:function(_42){
return dojox.grid.nodeKids(_42,"div");
},focusSelectNode:function(_43){
try{
dojox.grid.fire(_43,"focus");
dojox.grid.fire(_43,"select");
}
catch(e){
}
},whenIdle:function(){
setTimeout(dojo.hitch.apply(dojo,arguments),0);
},arrayCompare:function(inA,inB){
for(var i=0,l=inA.length;i<l;i++){
if(inA[i]!=inB[i]){
return false;
}
}
return (inA.length==inB.length);
},arrayInsert:function(_48,_49,_4a){
if(_48.length<=_49){
_48[_49]=_4a;
}else{
_48.splice(_49,0,_4a);
}
},arrayRemove:function(_4b,_4c){
_4b.splice(_4c,1);
},arraySwap:function(_4d,inI,inJ){
var _50=_4d[inI];
_4d[inI]=_4d[inJ];
_4d[inJ]=_50;
},initTextSizePoll:function(_51){
var f=document.createElement("div");
with(f.style){
top="0px";
left="0px";
position="absolute";
visibility="hidden";
}
f.innerHTML="TheQuickBrownFoxJumpedOverTheLazyDog";
document.body.appendChild(f);
var fw=f.offsetWidth;
var job=function(){
if(f.offsetWidth!=fw){
fw=f.offsetWidth;
dojox.grid.textSizeChanged();
}
};
window.setInterval(job,_51||200);
dojox.grid.initTextSizePoll=dojox.grid.nop;
},textSizeChanged:function(){
}});
dojox.grid.jobs={cancel:function(_55){
if(_55){
window.clearTimeout(_55);
}
},jobs:[],job:function(_56,_57,_58){
dojox.grid.jobs.cancelJob(_56);
var job=function(){
delete dojox.grid.jobs.jobs[_56];
_58();
};
dojox.grid.jobs.jobs[_56]=setTimeout(job,_57);
},cancelJob:function(_5a){
dojox.grid.jobs.cancel(dojox.grid.jobs.jobs[_5a]);
}};
}
