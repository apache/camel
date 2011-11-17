/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.scroller"]){
dojo._hasResource["dojox.grid.compat._grid.scroller"]=true;
dojo.provide("dojox.grid.compat._grid.scroller");
dojo.declare("dojox.grid.scroller.base",null,{constructor:function(){
this.pageHeights=[];
this.stack=[];
},rowCount:0,defaultRowHeight:10,keepRows:100,contentNode:null,scrollboxNode:null,defaultPageHeight:0,keepPages:10,pageCount:0,windowHeight:0,firstVisibleRow:0,lastVisibleRow:0,page:0,pageTop:0,init:function(_1,_2,_3){
switch(arguments.length){
case 3:
this.rowsPerPage=_3;
case 2:
this.keepRows=_2;
case 1:
this.rowCount=_1;
}
this.defaultPageHeight=this.defaultRowHeight*this.rowsPerPage;
this.pageCount=Math.ceil(this.rowCount/this.rowsPerPage);
this.setKeepInfo(this.keepRows);
this.invalidate();
if(this.scrollboxNode){
this.scrollboxNode.scrollTop=0;
this.scroll(0);
this.scrollboxNode.onscroll=dojo.hitch(this,"onscroll");
}
},setKeepInfo:function(_4){
this.keepRows=_4;
this.keepPages=!this.keepRows?this.keepRows:Math.max(Math.ceil(this.keepRows/this.rowsPerPage),2);
},invalidate:function(){
this.invalidateNodes();
this.pageHeights=[];
this.height=(this.pageCount?(this.pageCount-1)*this.defaultPageHeight+this.calcLastPageHeight():0);
this.resize();
},updateRowCount:function(_5){
this.invalidateNodes();
this.rowCount=_5;
var _6=this.pageCount;
this.pageCount=Math.ceil(this.rowCount/this.rowsPerPage);
if(this.pageCount<_6){
for(var i=_6-1;i>=this.pageCount;i--){
this.height-=this.getPageHeight(i);
delete this.pageHeights[i];
}
}else{
if(this.pageCount>_6){
this.height+=this.defaultPageHeight*(this.pageCount-_6-1)+this.calcLastPageHeight();
}
}
this.resize();
},pageExists:function(_8){
},measurePage:function(_9){
},positionPage:function(_a,_b){
},repositionPages:function(_c){
},installPage:function(_d){
},preparePage:function(_e,_f,_10){
},renderPage:function(_11){
},removePage:function(_12){
},pacify:function(_13){
},pacifying:false,pacifyTicks:200,setPacifying:function(_14){
if(this.pacifying!=_14){
this.pacifying=_14;
this.pacify(this.pacifying);
}
},startPacify:function(){
this.startPacifyTicks=new Date().getTime();
},doPacify:function(){
var _15=(new Date().getTime()-this.startPacifyTicks)>this.pacifyTicks;
this.setPacifying(true);
this.startPacify();
return _15;
},endPacify:function(){
this.setPacifying(false);
},resize:function(){
if(this.scrollboxNode){
this.windowHeight=this.scrollboxNode.clientHeight;
}
dojox.grid.setStyleHeightPx(this.contentNode,this.height);
},calcLastPageHeight:function(){
if(!this.pageCount){
return 0;
}
var _16=this.pageCount-1;
var _17=((this.rowCount%this.rowsPerPage)||(this.rowsPerPage))*this.defaultRowHeight;
this.pageHeights[_16]=_17;
return _17;
},updateContentHeight:function(_18){
this.height+=_18;
this.resize();
},updatePageHeight:function(_19){
if(this.pageExists(_19)){
var oh=this.getPageHeight(_19);
var h=(this.measurePage(_19))||(oh);
this.pageHeights[_19]=h;
if((h)&&(oh!=h)){
this.updateContentHeight(h-oh);
this.repositionPages(_19);
}
}
},rowHeightChanged:function(_1c){
this.updatePageHeight(Math.floor(_1c/this.rowsPerPage));
},invalidateNodes:function(){
while(this.stack.length){
this.destroyPage(this.popPage());
}
},createPageNode:function(){
var p=document.createElement("div");
p.style.position="absolute";
p.style[dojo._isBodyLtr()?"left":"right"]="0";
return p;
},getPageHeight:function(_1e){
var ph=this.pageHeights[_1e];
return (ph!==undefined?ph:this.defaultPageHeight);
},pushPage:function(_20){
return this.stack.push(_20);
},popPage:function(){
return this.stack.shift();
},findPage:function(_21){
var i=0,h=0;
for(var ph=0;i<this.pageCount;i++,h+=ph){
ph=this.getPageHeight(i);
if(h+ph>=_21){
break;
}
}
this.page=i;
this.pageTop=h;
},buildPage:function(_25,_26,_27){
this.preparePage(_25,_26);
this.positionPage(_25,_27);
this.installPage(_25);
this.renderPage(_25);
this.pushPage(_25);
},needPage:function(_28,_29){
var h=this.getPageHeight(_28),oh=h;
if(!this.pageExists(_28)){
this.buildPage(_28,this.keepPages&&(this.stack.length>=this.keepPages),_29);
h=this.measurePage(_28)||h;
this.pageHeights[_28]=h;
if(h&&(oh!=h)){
this.updateContentHeight(h-oh);
}
}else{
this.positionPage(_28,_29);
}
return h;
},onscroll:function(){
this.scroll(this.scrollboxNode.scrollTop);
},scroll:function(_2c){
this.startPacify();
this.findPage(_2c);
var h=this.height;
var b=this.getScrollBottom(_2c);
for(var p=this.page,y=this.pageTop;(p<this.pageCount)&&((b<0)||(y<b));p++){
y+=this.needPage(p,y);
}
this.firstVisibleRow=this.getFirstVisibleRow(this.page,this.pageTop,_2c);
this.lastVisibleRow=this.getLastVisibleRow(p-1,y,b);
if(h!=this.height){
this.repositionPages(p-1);
}
this.endPacify();
},getScrollBottom:function(_31){
return (this.windowHeight>=0?_31+this.windowHeight:-1);
},processNodeEvent:function(e,_33){
var t=e.target;
while(t&&(t!=_33)&&t.parentNode&&(t.parentNode.parentNode!=_33)){
t=t.parentNode;
}
if(!t||!t.parentNode||(t.parentNode.parentNode!=_33)){
return false;
}
var _35=t.parentNode;
e.topRowIndex=_35.pageIndex*this.rowsPerPage;
e.rowIndex=e.topRowIndex+dojox.grid.indexInParent(t);
e.rowTarget=t;
return true;
},processEvent:function(e){
return this.processNodeEvent(e,this.contentNode);
},dummy:0});
dojo.declare("dojox.grid.scroller",dojox.grid.scroller.base,{constructor:function(){
this.pageNodes=[];
},renderRow:function(_37,_38){
},removeRow:function(_39){
},getDefaultNodes:function(){
return this.pageNodes;
},getDefaultPageNode:function(_3a){
return this.getDefaultNodes()[_3a];
},positionPageNode:function(_3b,_3c){
_3b.style.top=_3c+"px";
},getPageNodePosition:function(_3d){
return _3d.offsetTop;
},repositionPageNodes:function(_3e,_3f){
var _40=0;
for(var i=0;i<this.stack.length;i++){
_40=Math.max(this.stack[i],_40);
}
var n=_3f[_3e];
var y=(n?this.getPageNodePosition(n)+this.getPageHeight(_3e):0);
for(var p=_3e+1;p<=_40;p++){
n=_3f[p];
if(n){
if(this.getPageNodePosition(n)==y){
return;
}
this.positionPage(p,y);
}
y+=this.getPageHeight(p);
}
},invalidatePageNode:function(_45,_46){
var p=_46[_45];
if(p){
delete _46[_45];
this.removePage(_45,p);
dojox.grid.cleanNode(p);
p.innerHTML="";
}
return p;
},preparePageNode:function(_48,_49,_4a){
var p=(_49===null?this.createPageNode():this.invalidatePageNode(_49,_4a));
p.pageIndex=_48;
p.id=(this._pageIdPrefix||"")+"page-"+_48;
_4a[_48]=p;
},pageExists:function(_4c){
return Boolean(this.getDefaultPageNode(_4c));
},measurePage:function(_4d){
var p=this.getDefaultPageNode(_4d);
var h=p.offsetHeight;
if(!this._defaultRowHeight){
if(p){
this._defaultRowHeight=8;
var fr=p.firstChild;
if(fr){
var _51=dojo.doc.createTextNode("T");
fr.appendChild(_51);
this._defaultRowHeight=fr.offsetHeight;
fr.removeChild(_51);
}
}
}
return (this.rowsPerPage==h)?(h*this._defaultRowHeight):h;
},positionPage:function(_52,_53){
this.positionPageNode(this.getDefaultPageNode(_52),_53);
},repositionPages:function(_54){
this.repositionPageNodes(_54,this.getDefaultNodes());
},preparePage:function(_55,_56){
this.preparePageNode(_55,(_56?this.popPage():null),this.getDefaultNodes());
},installPage:function(_57){
this.contentNode.appendChild(this.getDefaultPageNode(_57));
},destroyPage:function(_58){
var p=this.invalidatePageNode(_58,this.getDefaultNodes());
dojox.grid.removeNode(p);
},renderPage:function(_5a){
var _5b=this.pageNodes[_5a];
for(var i=0,j=_5a*this.rowsPerPage;(i<this.rowsPerPage)&&(j<this.rowCount);i++,j++){
this.renderRow(j,_5b);
}
},removePage:function(_5e){
for(var i=0,j=_5e*this.rowsPerPage;i<this.rowsPerPage;i++,j++){
this.removeRow(j);
}
},getPageRow:function(_61){
return _61*this.rowsPerPage;
},getLastPageRow:function(_62){
return Math.min(this.rowCount,this.getPageRow(_62+1))-1;
},getFirstVisibleRowNodes:function(_63,_64,_65,_66){
var row=this.getPageRow(_63);
var _68=dojox.grid.divkids(_66[_63]);
for(var i=0,l=_68.length;i<l&&_64<_65;i++,row++){
_64+=_68[i].offsetHeight;
}
return (row?row-1:row);
},getFirstVisibleRow:function(_6b,_6c,_6d){
if(!this.pageExists(_6b)){
return 0;
}
return this.getFirstVisibleRowNodes(_6b,_6c,_6d,this.getDefaultNodes());
},getLastVisibleRowNodes:function(_6e,_6f,_70,_71){
var row=this.getLastPageRow(_6e);
var _73=dojox.grid.divkids(_71[_6e]);
for(var i=_73.length-1;i>=0&&_6f>_70;i--,row--){
_6f-=_73[i].offsetHeight;
}
return row+1;
},getLastVisibleRow:function(_75,_76,_77){
if(!this.pageExists(_75)){
return 0;
}
return this.getLastVisibleRowNodes(_75,_76,_77,this.getDefaultNodes());
},findTopRowForNodes:function(_78,_79){
var _7a=dojox.grid.divkids(_79[this.page]);
for(var i=0,l=_7a.length,t=this.pageTop,h;i<l;i++){
h=_7a[i].offsetHeight;
t+=h;
if(t>=_78){
this.offset=h-(t-_78);
return i+this.page*this.rowsPerPage;
}
}
return -1;
},findScrollTopForNodes:function(_7f,_80){
var _81=Math.floor(_7f/this.rowsPerPage);
var t=0;
for(var i=0;i<_81;i++){
t+=this.getPageHeight(i);
}
this.pageTop=t;
this.needPage(_81,this.pageTop);
var _84=dojox.grid.divkids(_80[_81]);
var r=_7f-this.rowsPerPage*_81;
for(var i=0,l=_84.length;i<l&&i<r;i++){
t+=_84[i].offsetHeight;
}
return t;
},findTopRow:function(_87){
return this.findTopRowForNodes(_87,this.getDefaultNodes());
},findScrollTop:function(_88){
return this.findScrollTopForNodes(_88,this.getDefaultNodes());
},dummy:0});
dojo.declare("dojox.grid.scroller.columns",dojox.grid.scroller,{constructor:function(_89){
this.setContentNodes(_89);
},setContentNodes:function(_8a){
this.contentNodes=_8a;
this.colCount=(this.contentNodes?this.contentNodes.length:0);
this.pageNodes=[];
for(var i=0;i<this.colCount;i++){
this.pageNodes[i]=[];
}
},getDefaultNodes:function(){
return this.pageNodes[0]||[];
},scroll:function(_8c){
if(this.colCount){
dojox.grid.scroller.prototype.scroll.call(this,_8c);
}
},resize:function(){
if(this.scrollboxNode){
this.windowHeight=this.scrollboxNode.clientHeight;
}
for(var i=0;i<this.colCount;i++){
dojox.grid.setStyleHeightPx(this.contentNodes[i],this.height);
}
},positionPage:function(_8e,_8f){
for(var i=0;i<this.colCount;i++){
this.positionPageNode(this.pageNodes[i][_8e],_8f);
}
},preparePage:function(_91,_92){
var p=(_92?this.popPage():null);
for(var i=0;i<this.colCount;i++){
this.preparePageNode(_91,p,this.pageNodes[i]);
}
},installPage:function(_95){
for(var i=0;i<this.colCount;i++){
this.contentNodes[i].appendChild(this.pageNodes[i][_95]);
}
},destroyPage:function(_97){
for(var i=0;i<this.colCount;i++){
dojox.grid.removeNode(this.invalidatePageNode(_97,this.pageNodes[i]));
}
},renderPage:function(_99){
var _9a=[];
for(var i=0;i<this.colCount;i++){
_9a[i]=this.pageNodes[i][_99];
}
for(var i=0,j=_99*this.rowsPerPage;(i<this.rowsPerPage)&&(j<this.rowCount);i++,j++){
this.renderRow(j,_9a);
}
}});
}
