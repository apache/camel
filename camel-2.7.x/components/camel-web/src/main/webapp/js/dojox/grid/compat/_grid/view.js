/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.view"]){
dojo._hasResource["dojox.grid.compat._grid.view"]=true;
dojo.provide("dojox.grid.compat._grid.view");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojox.grid.compat._grid.builder");
dojo.declare("dojox.GridView",[dijit._Widget,dijit._Templated],{defaultWidth:"18em",viewWidth:"",templateString:"<div class=\"dojoxGrid-view\">\n\t<div class=\"dojoxGrid-header\" dojoAttachPoint=\"headerNode\">\n\t\t<div dojoAttachPoint=\"headerNodeContainer\" style=\"width:9000em\">\n\t\t\t<div dojoAttachPoint=\"headerContentNode\"></div>\n\t\t</div>\n\t</div>\n\t<input type=\"checkbox\" class=\"dojoxGrid-hidden-focus\" dojoAttachPoint=\"hiddenFocusNode\" />\n\t<input type=\"checkbox\" class=\"dojoxGrid-hidden-focus\" />\n\t<div class=\"dojoxGrid-scrollbox\" dojoAttachPoint=\"scrollboxNode\">\n\t\t<div class=\"dojoxGrid-content\" dojoAttachPoint=\"contentNode\" hidefocus=\"hidefocus\"></div>\n\t</div>\n</div>\n",themeable:false,classTag:"dojoxGrid",marginBottom:0,rowPad:2,postMixInProperties:function(){
this.rowNodes=[];
},postCreate:function(){
this.connect(this.scrollboxNode,"onscroll","doscroll");
dojox.grid.funnelEvents(this.contentNode,this,"doContentEvent",["mouseover","mouseout","click","dblclick","contextmenu","mousedown"]);
dojox.grid.funnelEvents(this.headerNode,this,"doHeaderEvent",["dblclick","mouseover","mouseout","mousemove","mousedown","click","contextmenu"]);
this.content=new dojox.grid.contentBuilder(this);
this.header=new dojox.grid.headerBuilder(this);
if(!dojo._isBodyLtr()){
this.headerNodeContainer.style.width="";
}
},destroy:function(){
dojox.grid.removeNode(this.headerNode);
this.inherited("destroy",arguments);
},focus:function(){
if(dojo.isWebKit||dojo.isOpera){
this.hiddenFocusNode.focus();
}else{
this.scrollboxNode.focus();
}
},setStructure:function(_1){
var vs=(this.structure=_1);
if(vs.width&&!isNaN(vs.width)){
this.viewWidth=vs.width+"em";
}else{
this.viewWidth=vs.width||this.viewWidth;
}
this.onBeforeRow=vs.onBeforeRow;
this.noscroll=vs.noscroll;
if(this.noscroll){
this.scrollboxNode.style.overflow="hidden";
}
this.testFlexCells();
this.updateStructure();
},testFlexCells:function(){
this.flexCells=false;
for(var j=0,_4;(_4=this.structure.rows[j]);j++){
for(var i=0,_6;(_6=_4[i]);i++){
_6.view=this;
this.flexCells=this.flexCells||_6.isFlex();
}
}
return this.flexCells;
},updateStructure:function(){
this.header.update();
this.content.update();
},getScrollbarWidth:function(){
return (this.noscroll?0:dojox.grid.getScrollbarWidth());
},getColumnsWidth:function(){
return this.headerContentNode.firstChild.offsetWidth;
},getWidth:function(){
return this.viewWidth||(this.getColumnsWidth()+this.getScrollbarWidth())+"px";
},getContentWidth:function(){
return Math.max(0,dojo._getContentBox(this.domNode).w-this.getScrollbarWidth())+"px";
},render:function(){
this.scrollboxNode.style.height="";
this.renderHeader();
},renderHeader:function(){
this.headerContentNode.innerHTML=this.header.generateHtml(this._getHeaderContent);
},_getHeaderContent:function(_7){
var n=_7.name||_7.grid.getCellName(_7);
if(_7.index!=_7.grid.getSortIndex()){
return n;
}
return ["<div class=\"",_7.grid.sortInfo>0?"dojoxGrid-sort-down":"dojoxGrid-sort-up","\"><div class=\"gridArrowButtonChar\">",_7.grid.sortInfo>0?"&#9660;":"&#9650;","</div>",n,"</div>"].join("");
},resize:function(){
this.adaptHeight();
this.adaptWidth();
},hasScrollbar:function(){
return (this.scrollboxNode.clientHeight!=this.scrollboxNode.offsetHeight);
},adaptHeight:function(){
if(!this.grid.autoHeight){
var h=this.domNode.clientHeight;
if(!this.hasScrollbar()){
h-=dojox.grid.getScrollbarWidth();
}
dojox.grid.setStyleHeightPx(this.scrollboxNode,h);
}
},adaptWidth:function(){
if(this.flexCells){
this.contentWidth=this.getContentWidth();
this.headerContentNode.firstChild.style.width=this.contentWidth;
}
var w=this.scrollboxNode.offsetWidth-this.getScrollbarWidth();
w=Math.max(w,this.getColumnsWidth())+"px";
with(this.contentNode){
style.width="";
offsetWidth;
style.width=w;
}
},setSize:function(w,h){
with(this.domNode.style){
if(w){
width=w;
}
height=(h>=0?h+"px":"");
}
with(this.headerNode.style){
if(w){
width=w;
}
}
},renderRow:function(_d,_e){
var _f=this.createRowNode(_d);
this.buildRow(_d,_f,_e);
this.grid.edit.restore(this,_d);
return _f;
},createRowNode:function(_10){
var _11=document.createElement("div");
_11.className=this.classTag+"-row";
_11[dojox.grid.gridViewTag]=this.id;
_11[dojox.grid.rowIndexTag]=_10;
this.rowNodes[_10]=_11;
return _11;
},buildRow:function(_12,_13){
this.buildRowContent(_12,_13);
this.styleRow(_12,_13);
},buildRowContent:function(_14,_15){
_15.innerHTML=this.content.generateHtml(_14,_14);
if(this.flexCells){
_15.firstChild.style.width=this.contentWidth;
}
},rowRemoved:function(_16){
this.grid.edit.save(this,_16);
delete this.rowNodes[_16];
},getRowNode:function(_17){
return this.rowNodes[_17];
},getCellNode:function(_18,_19){
var row=this.getRowNode(_18);
if(row){
return this.content.getCellNode(row,_19);
}
},styleRow:function(_1b,_1c){
_1c._style=dojox.grid.getStyleText(_1c);
this.styleRowNode(_1b,_1c);
},styleRowNode:function(_1d,_1e){
if(_1e){
this.doStyleRowNode(_1d,_1e);
}
},doStyleRowNode:function(_1f,_20){
this.grid.styleRowNode(_1f,_20);
},updateRow:function(_21,_22,_23){
var _24=this.getRowNode(_21);
if(_24){
_24.style.height="";
this.buildRow(_21,_24);
}
return _24;
},updateRowStyles:function(_25){
this.styleRowNode(_25,this.getRowNode(_25));
},lastTop:0,firstScroll:0,doscroll:function(_26){
var _27=dojo._isBodyLtr();
if(this.firstScroll<2){
if((!_27&&this.firstScroll==1)||(_27&&this.firstScroll==0)){
var s=dojo.marginBox(this.headerNodeContainer);
if(dojo.isIE){
this.headerNodeContainer.style.width=s.w+this.getScrollbarWidth()+"px";
}else{
if(dojo.isMoz){
this.headerNodeContainer.style.width=s.w-this.getScrollbarWidth()+"px";
if(_27){
this.scrollboxNode.scrollLeft=this.scrollboxNode.scrollWidth-this.scrollboxNode.clientWidth;
}else{
this.scrollboxNode.scrollLeft=this.scrollboxNode.clientWidth-this.scrollboxNode.scrollWidth;
}
}
}
}
this.firstScroll++;
}
this.headerNode.scrollLeft=this.scrollboxNode.scrollLeft;
var top=this.scrollboxNode.scrollTop;
if(top!=this.lastTop){
this.grid.scrollTo(top);
}
},setScrollTop:function(_2a){
this.lastTop=_2a;
this.scrollboxNode.scrollTop=_2a;
return this.scrollboxNode.scrollTop;
},doContentEvent:function(e){
if(this.content.decorateEvent(e)){
this.grid.onContentEvent(e);
}
},doHeaderEvent:function(e){
if(this.header.decorateEvent(e)){
this.grid.onHeaderEvent(e);
}
},dispatchContentEvent:function(e){
return this.content.dispatchEvent(e);
},dispatchHeaderEvent:function(e){
return this.header.dispatchEvent(e);
},setColWidth:function(_2f,_30){
this.grid.setCellWidth(_2f,_30+"px");
},update:function(){
var _31=this.scrollboxNode.scrollLeft;
this.content.update();
this.grid.update();
this.scrollboxNode.scrollLeft=_31;
this.headerNode.scrollLeft=_31;
}});
}
