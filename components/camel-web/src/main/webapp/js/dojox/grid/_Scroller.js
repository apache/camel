/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid._Scroller"]){
dojo._hasResource["dojox.grid._Scroller"]=true;
dojo.provide("dojox.grid._Scroller");
(function(){
var _1=function(_2){
var i=0,n,p=_2.parentNode;
while((n=p.childNodes[i++])){
if(n==_2){
return i-1;
}
}
return -1;
};
var _6=function(_7){
if(!_7){
return;
}
var _8=function(_9){
return _9.domNode&&dojo.isDescendant(_9.domNode,_7,true);
};
var ws=dijit.registry.filter(_8);
for(var i=0,w;(w=ws[i]);i++){
w.destroy();
}
delete ws;
};
var _d=function(_e){
var _f=dojo.byId(_e);
return (_f&&_f.tagName?_f.tagName.toLowerCase():"");
};
var _10=function(_11,_12){
var _13=[];
var i=0,n;
while((n=_11.childNodes[i++])){
if(_d(n)==_12){
_13.push(n);
}
}
return _13;
};
var _16=function(_17){
return _10(_17,"div");
};
dojo.declare("dojox.grid._Scroller",null,{constructor:function(_18){
this.setContentNodes(_18);
this.pageHeights=[];
this.pageNodes=[];
this.stack=[];
},rowCount:0,defaultRowHeight:32,keepRows:100,contentNode:null,scrollboxNode:null,defaultPageHeight:0,keepPages:10,pageCount:0,windowHeight:0,firstVisibleRow:0,lastVisibleRow:0,averageRowHeight:0,page:0,pageTop:0,init:function(_19,_1a,_1b){
switch(arguments.length){
case 3:
this.rowsPerPage=_1b;
case 2:
this.keepRows=_1a;
case 1:
this.rowCount=_19;
}
this.defaultPageHeight=this.defaultRowHeight*this.rowsPerPage;
this.pageCount=this._getPageCount(this.rowCount,this.rowsPerPage);
this.setKeepInfo(this.keepRows);
this.invalidate();
if(this.scrollboxNode){
this.scrollboxNode.scrollTop=0;
this.scroll(0);
this.scrollboxNode.onscroll=dojo.hitch(this,"onscroll");
}
},_getPageCount:function(_1c,_1d){
return _1c?(Math.ceil(_1c/_1d)||1):0;
},destroy:function(){
this.invalidateNodes();
delete this.contentNodes;
delete this.contentNode;
delete this.scrollboxNode;
},setKeepInfo:function(_1e){
this.keepRows=_1e;
this.keepPages=!this.keepRows?this.keepRows:Math.max(Math.ceil(this.keepRows/this.rowsPerPage),2);
},setContentNodes:function(_1f){
this.contentNodes=_1f;
this.colCount=(this.contentNodes?this.contentNodes.length:0);
this.pageNodes=[];
for(var i=0;i<this.colCount;i++){
this.pageNodes[i]=[];
}
},getDefaultNodes:function(){
return this.pageNodes[0]||[];
},invalidate:function(){
this.invalidateNodes();
this.pageHeights=[];
this.height=(this.pageCount?(this.pageCount-1)*this.defaultPageHeight+this.calcLastPageHeight():0);
this.resize();
},updateRowCount:function(_21){
this.invalidateNodes();
this.rowCount=_21;
var _22=this.pageCount;
this.pageCount=this._getPageCount(this.rowCount,this.rowsPerPage);
if(this.pageCount<_22){
for(var i=_22-1;i>=this.pageCount;i--){
this.height-=this.getPageHeight(i);
delete this.pageHeights[i];
}
}else{
if(this.pageCount>_22){
this.height+=this.defaultPageHeight*(this.pageCount-_22-1)+this.calcLastPageHeight();
}
}
this.resize();
},pageExists:function(_24){
return Boolean(this.getDefaultPageNode(_24));
},measurePage:function(_25){
var n=this.getDefaultPageNode(_25);
return (n&&n.innerHTML)?n.offsetHeight:0;
},positionPage:function(_27,_28){
for(var i=0;i<this.colCount;i++){
this.pageNodes[i][_27].style.top=_28+"px";
}
},repositionPages:function(_2a){
var _2b=this.getDefaultNodes();
var _2c=0;
for(var i=0;i<this.stack.length;i++){
_2c=Math.max(this.stack[i],_2c);
}
var n=_2b[_2a];
var y=(n?this.getPageNodePosition(n)+this.getPageHeight(_2a):0);
for(var p=_2a+1;p<=_2c;p++){
n=_2b[p];
if(n){
if(this.getPageNodePosition(n)==y){
return;
}
this.positionPage(p,y);
}
y+=this.getPageHeight(p);
}
},installPage:function(_31){
for(var i=0;i<this.colCount;i++){
this.contentNodes[i].appendChild(this.pageNodes[i][_31]);
}
},preparePage:function(_33,_34){
var p=(_34?this.popPage():null);
for(var i=0;i<this.colCount;i++){
var _37=this.pageNodes[i];
var _38=(p===null?this.createPageNode():this.invalidatePageNode(p,_37));
_38.pageIndex=_33;
_38.id=(this._pageIdPrefix||"")+"page-"+_33;
_37[_33]=_38;
}
},renderPage:function(_39){
var _3a=[];
for(var i=0;i<this.colCount;i++){
_3a[i]=this.pageNodes[i][_39];
}
for(var i=0,j=_39*this.rowsPerPage;(i<this.rowsPerPage)&&(j<this.rowCount);i++,j++){
this.renderRow(j,_3a);
}
},removePage:function(_3d){
for(var i=0,j=_3d*this.rowsPerPage;i<this.rowsPerPage;i++,j++){
this.removeRow(j);
}
},destroyPage:function(_40){
for(var i=0;i<this.colCount;i++){
var n=this.invalidatePageNode(_40,this.pageNodes[i]);
if(n){
dojo.destroy(n);
}
}
},pacify:function(_43){
},pacifying:false,pacifyTicks:200,setPacifying:function(_44){
if(this.pacifying!=_44){
this.pacifying=_44;
this.pacify(this.pacifying);
}
},startPacify:function(){
this.startPacifyTicks=new Date().getTime();
},doPacify:function(){
var _45=(new Date().getTime()-this.startPacifyTicks)>this.pacifyTicks;
this.setPacifying(true);
this.startPacify();
return _45;
},endPacify:function(){
this.setPacifying(false);
},resize:function(){
if(this.scrollboxNode){
this.windowHeight=this.scrollboxNode.clientHeight;
}
for(var i=0;i<this.colCount;i++){
dojox.grid.util.setStyleHeightPx(this.contentNodes[i],this.height);
}
this.needPage(this.page,this.pageTop);
var _47=(this.page<this.pageCount-1)?this.rowsPerPage:((this.rowCount%this.rowsPerPage)||this.rowsPerPage);
var _48=this.getPageHeight(this.page);
this.averageRowHeight=(_48>0&&_47>0)?(_48/_47):0;
},calcLastPageHeight:function(){
if(!this.pageCount){
return 0;
}
var _49=this.pageCount-1;
var _4a=((this.rowCount%this.rowsPerPage)||(this.rowsPerPage))*this.defaultRowHeight;
this.pageHeights[_49]=_4a;
return _4a;
},updateContentHeight:function(_4b){
this.height+=_4b;
this.resize();
},updatePageHeight:function(_4c){
if(this.pageExists(_4c)){
var oh=this.getPageHeight(_4c);
var h=(this.measurePage(_4c))||(oh);
this.pageHeights[_4c]=h;
if((h)&&(oh!=h)){
this.updateContentHeight(h-oh);
this.repositionPages(_4c);
}
}
},rowHeightChanged:function(_4f){
this.updatePageHeight(Math.floor(_4f/this.rowsPerPage));
},invalidateNodes:function(){
while(this.stack.length){
this.destroyPage(this.popPage());
}
},createPageNode:function(){
var p=document.createElement("div");
p.style.position="absolute";
p.style[dojo._isBodyLtr()?"left":"right"]="0";
return p;
},getPageHeight:function(_51){
var ph=this.pageHeights[_51];
return (ph!==undefined?ph:this.defaultPageHeight);
},pushPage:function(_53){
return this.stack.push(_53);
},popPage:function(){
return this.stack.shift();
},findPage:function(_54){
var i=0,h=0;
for(var ph=0;i<this.pageCount;i++,h+=ph){
ph=this.getPageHeight(i);
if(h+ph>=_54){
break;
}
}
this.page=i;
this.pageTop=h;
},buildPage:function(_58,_59,_5a){
this.preparePage(_58,_59);
this.positionPage(_58,_5a);
this.installPage(_58);
this.renderPage(_58);
this.pushPage(_58);
},needPage:function(_5b,_5c){
var h=this.getPageHeight(_5b),oh=h;
if(!this.pageExists(_5b)){
this.buildPage(_5b,this.keepPages&&(this.stack.length>=this.keepPages),_5c);
h=this.measurePage(_5b)||h;
this.pageHeights[_5b]=h;
if(h&&(oh!=h)){
this.updateContentHeight(h-oh);
}
}else{
this.positionPage(_5b,_5c);
}
return h;
},onscroll:function(){
this.scroll(this.scrollboxNode.scrollTop);
},scroll:function(_5f){
this.grid.scrollTop=_5f;
if(this.colCount){
this.startPacify();
this.findPage(_5f);
var h=this.height;
var b=this.getScrollBottom(_5f);
for(var p=this.page,y=this.pageTop;(p<this.pageCount)&&((b<0)||(y<b));p++){
y+=this.needPage(p,y);
}
this.firstVisibleRow=this.getFirstVisibleRow(this.page,this.pageTop,_5f);
this.lastVisibleRow=this.getLastVisibleRow(p-1,y,b);
if(h!=this.height){
this.repositionPages(p-1);
}
this.endPacify();
}
},getScrollBottom:function(_64){
return (this.windowHeight>=0?_64+this.windowHeight:-1);
},processNodeEvent:function(e,_66){
var t=e.target;
while(t&&(t!=_66)&&t.parentNode&&(t.parentNode.parentNode!=_66)){
t=t.parentNode;
}
if(!t||!t.parentNode||(t.parentNode.parentNode!=_66)){
return false;
}
var _68=t.parentNode;
e.topRowIndex=_68.pageIndex*this.rowsPerPage;
e.rowIndex=e.topRowIndex+_1(t);
e.rowTarget=t;
return true;
},processEvent:function(e){
return this.processNodeEvent(e,this.contentNode);
},renderRow:function(_6a,_6b){
},removeRow:function(_6c){
},getDefaultPageNode:function(_6d){
return this.getDefaultNodes()[_6d];
},positionPageNode:function(_6e,_6f){
},getPageNodePosition:function(_70){
return _70.offsetTop;
},invalidatePageNode:function(_71,_72){
var p=_72[_71];
if(p){
delete _72[_71];
this.removePage(_71,p);
_6(p);
p.innerHTML="";
}
return p;
},getPageRow:function(_74){
return _74*this.rowsPerPage;
},getLastPageRow:function(_75){
return Math.min(this.rowCount,this.getPageRow(_75+1))-1;
},getFirstVisibleRow:function(_76,_77,_78){
if(!this.pageExists(_76)){
return 0;
}
var row=this.getPageRow(_76);
var _7a=this.getDefaultNodes();
var _7b=_16(_7a[_76]);
for(var i=0,l=_7b.length;i<l&&_77<_78;i++,row++){
_77+=_7b[i].offsetHeight;
}
return (row?row-1:row);
},getLastVisibleRow:function(_7e,_7f,_80){
if(!this.pageExists(_7e)){
return 0;
}
var _81=this.getDefaultNodes();
var row=this.getLastPageRow(_7e);
var _83=_16(_81[_7e]);
for(var i=_83.length-1;i>=0&&_7f>_80;i--,row--){
_7f-=_83[i].offsetHeight;
}
return row+1;
},findTopRow:function(_85){
var _86=this.getDefaultNodes();
var _87=_16(_86[this.page]);
for(var i=0,l=_87.length,t=this.pageTop,h;i<l;i++){
h=_87[i].offsetHeight;
t+=h;
if(t>=_85){
this.offset=h-(t-_85);
return i+this.page*this.rowsPerPage;
}
}
return -1;
},findScrollTop:function(_8c){
var _8d=Math.floor(_8c/this.rowsPerPage);
var t=0;
for(var i=0;i<_8d;i++){
t+=this.getPageHeight(i);
}
this.pageTop=t;
this.needPage(_8d,this.pageTop);
var _90=this.getDefaultNodes();
var _91=_16(_90[_8d]);
var r=_8c-this.rowsPerPage*_8d;
for(var i=0,l=_91.length;i<l&&i<r;i++){
t+=_91[i].offsetHeight;
}
return t;
},dummy:0});
})();
}
