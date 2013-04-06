/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.views"]){
dojo._hasResource["dojox.grid.compat._grid.views"]=true;
dojo.provide("dojox.grid.compat._grid.views");
dojo.declare("dojox.grid.views",null,{constructor:function(_1){
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
h=Math.max(h,(n.firstChild.clientHeight)||(n.firstChild.offsetHeight));
}
h=(h>=0?h:0);
var hpx=h+"px";
for(var i=0,n;(n=_12[i]);i++){
if(n.firstChild.clientHeight!=h){
n.firstChild.style.height=hpx;
}
}
if(_12&&_12[0]){
_12[0].parentNode.offsetHeight;
}
},resetHeaderNodeHeight:function(){
for(var i=0,v,n;(v=this.views[i]);i++){
n=v.headerContentNode.firstChild;
if(n){
n.style.height="";
}
}
},renormalizeRow:function(_1b){
var _1c=[];
for(var i=0,v,n;(v=this.views[i])&&(n=v.getRowNode(_1b));i++){
n.firstChild.style.height="";
_1c.push(n);
}
this.normalizeRowNodeHeights(_1c);
},getViewWidth:function(_20){
return this.views[_20].getWidth()||this.defaultWidth;
},measureHeader:function(){
this.resetHeaderNodeHeight();
this.forEach(function(_21){
_21.headerContentNode.style.height="";
});
var h=0;
this.forEach(function(_23){
h=Math.max(_23.headerNode.offsetHeight,h);
});
return h;
},measureContent:function(){
var h=0;
this.forEach(function(_25){
h=Math.max(_25.domNode.offsetHeight,h);
});
return h;
},findClient:function(_26){
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
var _31=function(v,l){
with(v.domNode.style){
if(!dojo._isBodyLtr()){
right=l+"px";
}else{
left=l+"px";
}
top=0+"px";
}
with(v.headerNode.style){
if(!dojo._isBodyLtr()){
right=l+"px";
}else{
left=l+"px";
}
top=0;
}
};
for(i=0;(v=this.views[i])&&(i<c);i++){
vw=this.getViewWidth(i);
v.setSize(vw,0);
_31(v,l);
vw=v.domNode.offsetWidth;
l+=vw;
}
i++;
var r=w;
for(var j=len-1;(v=this.views[j])&&(i<=j);j--){
vw=this.getViewWidth(j);
v.setSize(vw,0);
vw=v.domNode.offsetWidth;
r-=vw;
_31(v,r);
}
if(c<len){
v=this.views[c];
vw=Math.max(1,r-l);
v.setSize(vw+"px",0);
_31(v,l);
}
return l;
},renderRow:function(_36,_37){
var _38=[];
for(var i=0,v,n,_3c;(v=this.views[i])&&(n=_37[i]);i++){
_3c=v.renderRow(_36);
n.appendChild(_3c);
_38.push(_3c);
}
this.normalizeRowNodeHeights(_38);
},rowRemoved:function(_3d){
this.onEach("rowRemoved",[_3d]);
},updateRow:function(_3e,_3f){
for(var i=0,v;v=this.views[i];i++){
v.updateRow(_3e,_3f);
}
this.renormalizeRow(_3e);
},updateRowStyles:function(_42){
this.onEach("updateRowStyles",[_42]);
},setScrollTop:function(_43){
var top=_43;
for(var i=0,v;v=this.views[i];i++){
top=v.setScrollTop(_43);
}
return top;
},getFirstScrollingView:function(){
for(var i=0,v;(v=this.views[i]);i++){
if(v.hasScrollbar()){
return v;
}
}
}});
}
