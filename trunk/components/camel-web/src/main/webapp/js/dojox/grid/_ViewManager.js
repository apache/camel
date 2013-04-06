/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid._ViewManager"]){
dojo._hasResource["dojox.grid._ViewManager"]=true;
dojo.provide("dojox.grid._ViewManager");
dojo.declare("dojox.grid._ViewManager",null,{constructor:function(_1){
this.grid=_1;
},defaultWidth:200,views:[],resize:function(){
this.onEach("resize");
},render:function(){
this.onEach("render");
},addView:function(_2){
_2.idx=this.views.length;
this.views.push(_2);
},destroyViews:function(){
for(var i=0,v;v=this.views[i];i++){
v.destroy();
}
this.views=[];
},getContentNodes:function(){
var _5=[];
for(var i=0,v;v=this.views[i];i++){
_5.push(v.contentNode);
}
return _5;
},forEach:function(_8){
for(var i=0,v;v=this.views[i];i++){
_8(v,i);
}
},onEach:function(_b,_c){
_c=_c||[];
for(var i=0,v;v=this.views[i];i++){
if(_b in v){
v[_b].apply(v,_c);
}
}
},normalizeHeaderNodeHeight:function(){
var _f=[];
for(var i=0,v;(v=this.views[i]);i++){
if(v.headerContentNode.firstChild){
_f.push(v.headerContentNode);
}
}
this.normalizeRowNodeHeights(_f);
},normalizeRowNodeHeights:function(_12){
var h=0;
for(var i=0,n,o;(n=_12[i]);i++){
h=Math.max(h,dojo.marginBox(n.firstChild).h);
}
h=(h>=0?h:0);
for(var i=0,n;(n=_12[i]);i++){
dojo.marginBox(n.firstChild,{h:h});
}
if(_12&&_12[0]&&_12[0].parentNode){
_12[0].parentNode.offsetHeight;
}
},resetHeaderNodeHeight:function(){
for(var i=0,v,n;(v=this.views[i]);i++){
n=v.headerContentNode.firstChild;
if(n){
n.style.height="";
}
}
},renormalizeRow:function(_1a){
var _1b=[];
for(var i=0,v,n;(v=this.views[i])&&(n=v.getRowNode(_1a));i++){
n.firstChild.style.height="";
_1b.push(n);
}
this.normalizeRowNodeHeights(_1b);
},getViewWidth:function(_1f){
return this.views[_1f].getWidth()||this.defaultWidth;
},measureHeader:function(){
this.resetHeaderNodeHeight();
this.forEach(function(_20){
_20.headerContentNode.style.height="";
});
var h=0;
this.forEach(function(_22){
h=Math.max(_22.headerNode.offsetHeight,h);
});
return h;
},measureContent:function(){
var h=0;
this.forEach(function(_24){
h=Math.max(_24.domNode.offsetHeight,h);
});
return h;
},findClient:function(_25){
var c=this.grid.elasticView||-1;
if(c<0){
for(var i=1,v;(v=this.views[i]);i++){
if(v.viewWidth){
for(i=1;(v=this.views[i]);i++){
if(!v.viewWidth){
c=i;
break;
}
}
break;
}
}
}
if(c<0){
c=Math.floor(this.views.length/2);
}
return c;
},arrange:function(l,w){
var i,v,vw,len=this.views.length;
var c=(w<=0?len:this.findClient());
var _30=function(v,l){
var ds=v.domNode.style;
var hs=v.headerNode.style;
if(!dojo._isBodyLtr()){
ds.right=l+"px";
hs.right=l+"px";
}else{
ds.left=l+"px";
hs.left=l+"px";
}
ds.top=0+"px";
hs.top=0;
};
for(i=0;(v=this.views[i])&&(i<c);i++){
vw=this.getViewWidth(i);
v.setSize(vw,0);
_30(v,l);
if(v.headerContentNode&&v.headerContentNode.firstChild){
vw=v.getColumnsWidth()+v.getScrollbarWidth();
}else{
vw=v.domNode.offsetWidth;
}
l+=vw;
}
i++;
var r=w;
for(var j=len-1;(v=this.views[j])&&(i<=j);j--){
vw=this.getViewWidth(j);
v.setSize(vw,0);
vw=v.domNode.offsetWidth;
r-=vw;
_30(v,r);
}
if(c<len){
v=this.views[c];
vw=Math.max(1,r-l);
v.setSize(vw+"px",0);
_30(v,l);
}
return l;
},renderRow:function(_37,_38){
var _39=[];
for(var i=0,v,n,_3d;(v=this.views[i])&&(n=_38[i]);i++){
_3d=v.renderRow(_37);
n.appendChild(_3d);
_39.push(_3d);
}
this.normalizeRowNodeHeights(_39);
},rowRemoved:function(_3e){
this.onEach("rowRemoved",[_3e]);
},updateRow:function(_3f){
for(var i=0,v;v=this.views[i];i++){
v.updateRow(_3f);
}
this.renormalizeRow(_3f);
},updateRowStyles:function(_42){
this.onEach("updateRowStyles",[_42]);
},setScrollTop:function(_43){
var top=_43;
for(var i=0,v;v=this.views[i];i++){
top=v.setScrollTop(_43);
if(dojo.isIE&&v.headerNode&&v.scrollboxNode){
v.headerNode.scrollLeft=v.scrollboxNode.scrollLeft;
}
}
return top;
},getFirstScrollingView:function(){
for(var i=0,v;(v=this.views[i]);i++){
if(v.hasHScrollbar()||v.hasVScrollbar()){
return v;
}
}
}});
}
