/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid._Builder"]){
dojo._hasResource["dojox.grid._Builder"]=true;
dojo.provide("dojox.grid._Builder");
dojo.require("dojox.grid.util");
dojo.require("dojo.dnd.Moveable");
(function(){
var dg=dojox.grid;
var _2=function(td){
return td.cellIndex>=0?td.cellIndex:dojo.indexOf(td.parentNode.cells,td);
};
var _4=function(tr){
return tr.rowIndex>=0?tr.rowIndex:dojo.indexOf(tr.parentNode.childNodes,tr);
};
var _6=function(_7,_8){
return _7&&((_7.rows||0)[_8]||_7.childNodes[_8]);
};
var _9=function(_a){
for(var n=_a;n&&n.tagName!="TABLE";n=n.parentNode){
}
return n;
};
var _c=function(_d,_e){
for(var n=_d;n&&_e(n);n=n.parentNode){
}
return n;
};
var _10=function(_11){
var _12=_11.toUpperCase();
return function(_13){
return _13.tagName!=_12;
};
};
var _14=dojox.grid.util.rowIndexTag;
var _15=dojox.grid.util.gridViewTag;
dg._Builder=dojo.extend(function(_16){
if(_16){
this.view=_16;
this.grid=_16.grid;
}
},{view:null,_table:"<table class=\"dojoxGridRowTable\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" role=\"wairole:presentation\"",getTableArray:function(){
var _17=[this._table];
if(this.view.viewWidth){
_17.push([" style=\"width:",this.view.viewWidth,";\""].join(""));
}
_17.push(">");
return _17;
},generateCellMarkup:function(_18,_19,_1a,_1b){
var _1c=[],_1d;
var _1e=dojo.isFF<3?"wairole:":"";
if(_1b){
_1d=["<th tabIndex=\"-1\" role=\"",_1e,"columnheader\""];
}else{
_1d=["<td tabIndex=\"-1\" role=\"",_1e,"gridcell\""];
}
_18.colSpan&&_1d.push(" colspan=\"",_18.colSpan,"\"");
_18.rowSpan&&_1d.push(" rowspan=\"",_18.rowSpan,"\"");
_1d.push(" class=\"dojoxGridCell ");
_18.classes&&_1d.push(_18.classes," ");
_1a&&_1d.push(_1a," ");
_1c.push(_1d.join(""));
_1c.push("");
_1d=["\" idx=\"",_18.index,"\" style=\""];
if(_19&&_19[_19.length-1]!=";"){
_19+=";";
}
_1d.push(_18.styles,_19||"",_18.hidden?"display:none;":"");
_18.unitWidth&&_1d.push("width:",_18.unitWidth,";");
_1c.push(_1d.join(""));
_1c.push("");
_1d=["\""];
_18.attrs&&_1d.push(" ",_18.attrs);
_1d.push(">");
_1c.push(_1d.join(""));
_1c.push("");
_1c.push("</td>");
return _1c;
},isCellNode:function(_1f){
return Boolean(_1f&&_1f!=dojo.doc&&dojo.attr(_1f,"idx"));
},getCellNodeIndex:function(_20){
return _20?Number(dojo.attr(_20,"idx")):-1;
},getCellNode:function(_21,_22){
for(var i=0,row;row=_6(_21.firstChild,i);i++){
for(var j=0,_26;_26=row.cells[j];j++){
if(this.getCellNodeIndex(_26)==_22){
return _26;
}
}
}
},findCellTarget:function(_27,_28){
var n=_27;
while(n&&(!this.isCellNode(n)||(n.offsetParent&&_15 in n.offsetParent.parentNode&&n.offsetParent.parentNode[_15]!=this.view.id))&&(n!=_28)){
n=n.parentNode;
}
return n!=_28?n:null;
},baseDecorateEvent:function(e){
e.dispatch="do"+e.type;
e.grid=this.grid;
e.sourceView=this.view;
e.cellNode=this.findCellTarget(e.target,e.rowNode);
e.cellIndex=this.getCellNodeIndex(e.cellNode);
e.cell=(e.cellIndex>=0?this.grid.getCell(e.cellIndex):null);
},findTarget:function(_2b,_2c){
var n=_2b;
while(n&&(n!=this.domNode)&&(!(_2c in n)||(_15 in n&&n[_15]!=this.view.id))){
n=n.parentNode;
}
return (n!=this.domNode)?n:null;
},findRowTarget:function(_2e){
return this.findTarget(_2e,_14);
},isIntraNodeEvent:function(e){
try{
return (e.cellNode&&e.relatedTarget&&dojo.isDescendant(e.relatedTarget,e.cellNode));
}
catch(x){
return false;
}
},isIntraRowEvent:function(e){
try{
var row=e.relatedTarget&&this.findRowTarget(e.relatedTarget);
return !row&&(e.rowIndex==-1)||row&&(e.rowIndex==row.gridRowIndex);
}
catch(x){
return false;
}
},dispatchEvent:function(e){
if(e.dispatch in this){
return this[e.dispatch](e);
}
},domouseover:function(e){
if(e.cellNode&&(e.cellNode!=this.lastOverCellNode)){
this.lastOverCellNode=e.cellNode;
this.grid.onMouseOver(e);
}
this.grid.onMouseOverRow(e);
},domouseout:function(e){
if(e.cellNode&&(e.cellNode==this.lastOverCellNode)&&!this.isIntraNodeEvent(e,this.lastOverCellNode)){
this.lastOverCellNode=null;
this.grid.onMouseOut(e);
if(!this.isIntraRowEvent(e)){
this.grid.onMouseOutRow(e);
}
}
},domousedown:function(e){
if(e.cellNode){
this.grid.onMouseDown(e);
}
this.grid.onMouseDownRow(e);
}});
dg._ContentBuilder=dojo.extend(function(_36){
dg._Builder.call(this,_36);
},dg._Builder.prototype,{update:function(){
this.prepareHtml();
},prepareHtml:function(){
var _37=this.grid.get,_38=this.view.structure.cells;
for(var j=0,row;(row=_38[j]);j++){
for(var i=0,_3c;(_3c=row[i]);i++){
_3c.get=_3c.get||(_3c.value==undefined)&&_37;
_3c.markup=this.generateCellMarkup(_3c,_3c.cellStyles,_3c.cellClasses,false);
}
}
},generateHtml:function(_3d,_3e){
var _3f=this.getTableArray(),v=this.view,_41=v.structure.cells,_42=this.grid.getItem(_3e);
dojox.grid.util.fire(this.view,"onBeforeRow",[_3e,_41]);
for(var j=0,row;(row=_41[j]);j++){
if(row.hidden||row.header){
continue;
}
_3f.push(!row.invisible?"<tr>":"<tr class=\"dojoxGridInvisible\">");
for(var i=0,_46,m,cc,cs;(_46=row[i]);i++){
m=_46.markup,cc=_46.customClasses=[],cs=_46.customStyles=[];
m[5]=_46.format(_3e,_42);
m[1]=cc.join(" ");
m[3]=cs.join(";");
_3f.push.apply(_3f,m);
}
_3f.push("</tr>");
}
_3f.push("</table>");
return _3f.join("");
},decorateEvent:function(e){
e.rowNode=this.findRowTarget(e.target);
if(!e.rowNode){
return false;
}
e.rowIndex=e.rowNode[_14];
this.baseDecorateEvent(e);
e.cell=this.grid.getCell(e.cellIndex);
return true;
}});
dg._HeaderBuilder=dojo.extend(function(_4b){
this.moveable=null;
dg._Builder.call(this,_4b);
},dg._Builder.prototype,{_skipBogusClicks:false,overResizeWidth:4,minColWidth:1,update:function(){
if(this.tableMap){
this.tableMap.mapRows(this.view.structure.cells);
}else{
this.tableMap=new dg._TableMap(this.view.structure.cells);
}
},generateHtml:function(_4c,_4d){
var _4e=this.getTableArray(),_4f=this.view.structure.cells;
dojox.grid.util.fire(this.view,"onBeforeRow",[-1,_4f]);
for(var j=0,row;(row=_4f[j]);j++){
if(row.hidden){
continue;
}
_4e.push(!row.invisible?"<tr>":"<tr class=\"dojoxGridInvisible\">");
for(var i=0,_53,_54;(_53=row[i]);i++){
_53.customClasses=[];
_53.customStyles=[];
if(this.view.simpleStructure){
if(_53.headerClasses){
if(_53.headerClasses.indexOf("dojoDndItem")==-1){
_53.headerClasses+=" dojoDndItem";
}
}else{
_53.headerClasses="dojoDndItem";
}
if(_53.attrs){
if(_53.attrs.indexOf("dndType='gridColumn'")==-1){
_53.attrs+=" dndType='gridColumn_"+this.grid.id+"'";
}
}else{
_53.attrs="dndType='gridColumn_"+this.grid.id+"'";
}
}
_54=this.generateCellMarkup(_53,_53.headerStyles,_53.headerClasses,true);
_54[5]=(_4d!=undefined?_4d:_4c(_53));
_54[3]=_53.customStyles.join(";");
_54[1]=_53.customClasses.join(" ");
_4e.push(_54.join(""));
}
_4e.push("</tr>");
}
_4e.push("</table>");
return _4e.join("");
},getCellX:function(e){
var x=e.layerX;
if(dojo.isMoz){
var n=_c(e.target,_10("th"));
x-=(n&&n.offsetLeft)||0;
var t=e.sourceView.getScrollbarWidth();
if(!dojo._isBodyLtr()&&e.sourceView.headerNode.scrollLeft<t){
x-=t;
}
}
var n=_c(e.target,function(){
if(!n||n==e.cellNode){
return false;
}
x+=(n.offsetLeft<0?0:n.offsetLeft);
return true;
});
return x;
},decorateEvent:function(e){
this.baseDecorateEvent(e);
e.rowIndex=-1;
e.cellX=this.getCellX(e);
return true;
},prepareResize:function(e,mod){
do{
var i=_2(e.cellNode);
e.cellNode=(i?e.cellNode.parentNode.cells[i+mod]:null);
e.cellIndex=(e.cellNode?this.getCellNodeIndex(e.cellNode):-1);
}while(e.cellNode&&e.cellNode.style.display=="none");
return Boolean(e.cellNode);
},canResize:function(e){
if(!e.cellNode||e.cellNode.colSpan>1){
return false;
}
var _5e=this.grid.getCell(e.cellIndex);
return !_5e.noresize&&!_5e.canResize();
},overLeftResizeArea:function(e){
if(dojo._isBodyLtr()){
return (e.cellIndex>0)&&(e.cellX<this.overResizeWidth)&&this.prepareResize(e,-1);
}
var t=e.cellNode&&(e.cellX<this.overResizeWidth);
return t;
},overRightResizeArea:function(e){
if(dojo._isBodyLtr()){
return e.cellNode&&(e.cellX>=e.cellNode.offsetWidth-this.overResizeWidth);
}
return (e.cellIndex>0)&&(e.cellX>=e.cellNode.offsetWidth-this.overResizeWidth)&&this.prepareResize(e,-1);
},domousemove:function(e){
if(!this.moveable){
var c=(this.overRightResizeArea(e)?"e-resize":(this.overLeftResizeArea(e)?"w-resize":""));
if(c&&!this.canResize(e)){
c="not-allowed";
}
if(dojo.isIE){
var t=e.sourceView.headerNode.scrollLeft;
e.sourceView.headerNode.style.cursor=c||"";
e.sourceView.headerNode.scrollLeft=t;
}else{
e.sourceView.headerNode.style.cursor=c||"";
}
if(c){
dojo.stopEvent(e);
}
}
},domousedown:function(e){
if(!this.moveable){
if((this.overRightResizeArea(e)||this.overLeftResizeArea(e))&&this.canResize(e)){
this.beginColumnResize(e);
}else{
this.grid.onMouseDown(e);
this.grid.onMouseOverRow(e);
}
}
},doclick:function(e){
if(this._skipBogusClicks){
dojo.stopEvent(e);
return true;
}
},beginColumnResize:function(e){
this.moverDiv=document.createElement("div");
dojo.style(this.moverDiv,{position:"absolute",left:0});
dojo.body().appendChild(this.moverDiv);
var m=this.moveable=new dojo.dnd.Moveable(this.moverDiv);
var _69=[],_6a=this.tableMap.findOverlappingNodes(e.cellNode);
for(var i=0,_6c;(_6c=_6a[i]);i++){
_69.push({node:_6c,index:this.getCellNodeIndex(_6c),width:_6c.offsetWidth});
}
var _6d=e.sourceView;
var adj=dojo._isBodyLtr()?1:-1;
var _6f=e.grid.views.views;
var _70=[];
for(var i=_6d.idx+adj,_71;(_71=_6f[i]);i=i+adj){
_70.push({node:_71.headerNode,left:window.parseInt(_71.headerNode.style.left)});
}
var _72=_6d.headerContentNode.firstChild;
var _73={scrollLeft:e.sourceView.headerNode.scrollLeft,view:_6d,node:e.cellNode,index:e.cellIndex,w:dojo.contentBox(e.cellNode).w,vw:dojo.contentBox(_6d.headerNode).w,table:_72,tw:dojo.contentBox(_72).w,spanners:_69,followers:_70};
m.onMove=dojo.hitch(this,"doResizeColumn",_73);
dojo.connect(m,"onMoveStop",dojo.hitch(this,function(){
this.endResizeColumn(_73);
if(_73.node.releaseCapture){
_73.node.releaseCapture();
}
this.moveable.destroy();
delete this.moveable;
this.moveable=null;
}));
_6d.convertColPctToFixed();
if(e.cellNode.setCapture){
e.cellNode.setCapture();
}
m.onMouseDown(e);
},doResizeColumn:function(_74,_75,_76){
var _77=dojo._isBodyLtr();
var _78=_77?_76.l:-_76.l;
var w=_74.w+_78;
var vw=_74.vw+_78;
var tw=_74.tw+_78;
if(w>=this.minColWidth){
for(var i=0,s,sw;(s=_74.spanners[i]);i++){
sw=s.width+_78;
s.node.style.width=sw+"px";
_74.view.setColWidth(s.index,sw);
}
for(var i=0,f,fl;(f=_74.followers[i]);i++){
fl=f.left+_78;
f.node.style.left=fl+"px";
}
_74.node.style.width=w+"px";
_74.view.setColWidth(_74.index,w);
_74.view.headerNode.style.width=vw+"px";
_74.view.setColumnsWidth(tw);
if(!_77){
_74.view.headerNode.scrollLeft=_74.scrollLeft+_78;
}
}
if(_74.view.flexCells&&!_74.view.testFlexCells()){
var t=_9(_74.node);
t&&(t.style.width="");
}
},endResizeColumn:function(_82){
dojo.destroy(this.moverDiv);
delete this.moverDiv;
this._skipBogusClicks=true;
var _83=dojo.connect(_82.view,"update",this,function(){
dojo.disconnect(_83);
this._skipBogusClicks=false;
});
setTimeout(dojo.hitch(_82.view,"update"),50);
}});
dg._TableMap=dojo.extend(function(_84){
this.mapRows(_84);
},{map:null,mapRows:function(_85){
var _86=_85.length;
if(!_86){
return;
}
this.map=[];
for(var j=0,row;(row=_85[j]);j++){
this.map[j]=[];
}
for(var j=0,row;(row=_85[j]);j++){
for(var i=0,x=0,_8b,_8c,_8d;(_8b=row[i]);i++){
while(this.map[j][x]){
x++;
}
this.map[j][x]={c:i,r:j};
_8d=_8b.rowSpan||1;
_8c=_8b.colSpan||1;
for(var y=0;y<_8d;y++){
for(var s=0;s<_8c;s++){
this.map[j+y][x+s]=this.map[j][x];
}
}
x+=_8c;
}
}
},dumpMap:function(){
for(var j=0,row,h="";(row=this.map[j]);j++,h=""){
for(var i=0,_94;(_94=row[i]);i++){
h+=_94.r+","+_94.c+"   ";
}
}
},getMapCoords:function(_95,_96){
for(var j=0,row;(row=this.map[j]);j++){
for(var i=0,_9a;(_9a=row[i]);i++){
if(_9a.c==_96&&_9a.r==_95){
return {j:j,i:i};
}
}
}
return {j:-1,i:-1};
},getNode:function(_9b,_9c,_9d){
var row=_9b&&_9b.rows[_9c];
return row&&row.cells[_9d];
},_findOverlappingNodes:function(_9f,_a0,_a1){
var _a2=[];
var m=this.getMapCoords(_a0,_a1);
var row=this.map[m.j];
for(var j=0,row;(row=this.map[j]);j++){
if(j==m.j){
continue;
}
var rw=row[m.i];
var n=(rw?this.getNode(_9f,rw.r,rw.c):null);
if(n){
_a2.push(n);
}
}
return _a2;
},findOverlappingNodes:function(_a8){
return this._findOverlappingNodes(_9(_a8),_4(_a8.parentNode),_2(_a8));
}});
})();
}
