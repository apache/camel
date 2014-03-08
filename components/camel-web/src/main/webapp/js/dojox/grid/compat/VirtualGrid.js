/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat.VirtualGrid"]){
dojo._hasResource["dojox.grid.compat.VirtualGrid"]=true;
dojo.provide("dojox.grid.compat.VirtualGrid");
dojo.require("dojox.grid.compat._grid.lib");
dojo.require("dojox.grid.compat._grid.scroller");
dojo.require("dojox.grid.compat._grid.view");
dojo.require("dojox.grid.compat._grid.views");
dojo.require("dojox.grid.compat._grid.layout");
dojo.require("dojox.grid.compat._grid.rows");
dojo.require("dojox.grid.compat._grid.focus");
dojo.require("dojox.grid.compat._grid.selection");
dojo.require("dojox.grid.compat._grid.edit");
dojo.require("dojox.grid.compat._grid.rowbar");
dojo.require("dojox.grid.compat._grid.publicEvents");
dojo.declare("dojox.VirtualGrid",[dijit._Widget,dijit._Templated],{templateString:"<div class=\"dojoxGrid\" hidefocus=\"hidefocus\" role=\"wairole:grid\">\n\t<div class=\"dojoxGrid-master-header\" dojoAttachPoint=\"viewsHeaderNode\"></div>\n\t<div class=\"dojoxGrid-master-view\" dojoAttachPoint=\"viewsNode\"></div>\n\t<span dojoAttachPoint=\"lastFocusNode\" tabindex=\"0\"></span>\n</div>\n",classTag:"dojoxGrid",get:function(_1){
},rowCount:5,keepRows:75,rowsPerPage:25,autoWidth:false,autoHeight:false,autoRender:true,defaultHeight:"15em",structure:"",elasticView:-1,singleClickEdit:false,_click:null,sortInfo:0,themeable:true,buildRendering:function(){
this.inherited(arguments);
if(this.get==dojox.VirtualGrid.prototype.get){
this.get=null;
}
if(!this.domNode.getAttribute("tabIndex")){
this.domNode.tabIndex="0";
}
this.createScroller();
this.createLayout();
this.createViews();
this.createManagers();
dojox.grid.initTextSizePoll();
this.connect(dojox.grid,"textSizeChanged","textSizeChanged");
dojox.grid.funnelEvents(this.domNode,this,"doKeyEvent",dojox.grid.keyEvents);
this.connect(this,"onShow","renderOnIdle");
},postCreate:function(){
this.styleChanged=this._styleChanged;
this.setStructure(this.structure);
this._click=[];
},destroy:function(){
this.domNode.onReveal=null;
this.domNode.onSizeChange=null;
this.edit.destroy();
this.views.destroyViews();
this.inherited(arguments);
},styleChanged:function(){
this.setStyledClass(this.domNode,"");
},_styleChanged:function(){
this.styleChanged();
this.update();
},textSizeChanged:function(){
setTimeout(dojo.hitch(this,"_textSizeChanged"),1);
},_textSizeChanged:function(){
if(this.domNode){
this.views.forEach(function(v){
v.content.update();
});
this.render();
}
},sizeChange:function(){
dojox.grid.jobs.job(this.id+"SizeChange",50,dojo.hitch(this,"update"));
},renderOnIdle:function(){
setTimeout(dojo.hitch(this,"render"),1);
},createManagers:function(){
this.rows=new dojox.grid.rows(this);
this.focus=new dojox.grid.focus(this);
this.selection=new dojox.grid.selection(this);
this.edit=new dojox.grid.edit(this);
},createScroller:function(){
this.scroller=new dojox.grid.scroller.columns();
this.scroller._pageIdPrefix=this.id+"-";
this.scroller.renderRow=dojo.hitch(this,"renderRow");
this.scroller.removeRow=dojo.hitch(this,"rowRemoved");
},createLayout:function(){
this.layout=new dojox.grid.layout(this);
},createViews:function(){
this.views=new dojox.grid.views(this);
this.views.createView=dojo.hitch(this,"createView");
},createView:function(_3){
if(dojo.isAIR){
var _4=window;
var _5=_3.split(".");
for(var i=0;i<_5.length;i++){
if(typeof _4[_5[i]]=="undefined"){
var _7=_5[0];
for(var j=1;j<=i;j++){
_7+="."+_5[j];
}
throw new Error(_7+" is undefined");
}
_4=_4[_5[i]];
}
var c=_4;
}else{
var c=eval(_3);
}
var _a=new c({grid:this});
this.viewsNode.appendChild(_a.domNode);
this.viewsHeaderNode.appendChild(_a.headerNode);
this.views.addView(_a);
return _a;
},buildViews:function(){
for(var i=0,vs;(vs=this.layout.structure[i]);i++){
this.createView(vs.type||dojox._scopeName+".GridView").setStructure(vs);
}
this.scroller.setContentNodes(this.views.getContentNodes());
},setStructure:function(_d){
this.views.destroyViews();
this.structure=_d;
if((this.structure)&&(dojo.isString(this.structure))){
this.structure=dojox.grid.getProp(this.structure);
}
if(!this.structure){
this.structure=window["layout"];
}
if(!this.structure){
return;
}
this.layout.setStructure(this.structure);
this._structureChanged();
},_structureChanged:function(){
this.buildViews();
if(this.autoRender){
this.render();
}
},hasLayout:function(){
return this.layout.cells.length;
},resize:function(_e){
this._sizeBox=_e;
this._resize();
this.sizeChange();
},_getPadBorder:function(){
this._padBorder=this._padBorder||dojo._getPadBorderExtents(this.domNode);
return this._padBorder;
},_resize:function(){
if(!this.domNode.parentNode||this.domNode.parentNode.nodeType!=1||!this.hasLayout()){
return;
}
var _f=this._getPadBorder();
if(this.autoHeight){
this.domNode.style.height="auto";
this.viewsNode.style.height="";
}else{
if(this.flex>0){
}else{
if(this.domNode.clientHeight<=_f.h){
if(this.domNode.parentNode==document.body){
this.domNode.style.height=this.defaultHeight;
}else{
this.fitTo="parent";
}
}
}
}
if(this._sizeBox){
dojo.contentBox(this.domNode,this._sizeBox);
}else{
if(this.fitTo=="parent"){
var h=dojo._getContentBox(this.domNode.parentNode).h;
dojo.marginBox(this.domNode,{h:Math.max(0,h)});
}
}
var h=dojo._getContentBox(this.domNode).h;
if(h==0&&!this.autoHeight){
this.viewsHeaderNode.style.display="none";
}else{
this.viewsHeaderNode.style.display="block";
}
this.adaptWidth();
this.adaptHeight();
this.scroller.defaultRowHeight=this.rows.getDefaultHeightPx()+1;
this.postresize();
},adaptWidth:function(){
var w=this.autoWidth?0:this.domNode.clientWidth||(this.domNode.offsetWidth-this._getPadBorder().w);
var vw=this.views.arrange(1,w);
this.views.onEach("adaptWidth");
if(this.autoWidth){
this.domNode.style.width=vw+"px";
}
},adaptHeight:function(){
var vns=this.viewsHeaderNode.style,t=vns.display=="none"?0:this.views.measureHeader();
vns.height=t+"px";
this.views.normalizeHeaderNodeHeight();
var h=(this.autoHeight?-1:Math.max(this.domNode.clientHeight-t,0)||0);
this.views.onEach("setSize",[0,h]);
this.views.onEach("adaptHeight");
this.scroller.windowHeight=h;
},render:function(){
if(!this.domNode){
return;
}
if(!this.hasLayout()){
this.scroller.init(0,this.keepRows,this.rowsPerPage);
return;
}
this.update=this.defaultUpdate;
this.scroller.init(this.rowCount,this.keepRows,this.rowsPerPage);
this.prerender();
this.setScrollTop(0);
this.postrender();
},prerender:function(){
this.keepRows=this.autoHeight?0:this.constructor.prototype.keepRows;
this.scroller.setKeepInfo(this.keepRows);
this.views.render();
this._resize();
},postrender:function(){
this.postresize();
this.focus.initFocusView();
dojo.setSelectable(this.domNode,false);
},postresize:function(){
if(this.autoHeight){
this.viewsNode.style.height=this.views.measureContent()+"px";
}
},renderRow:function(_16,_17){
this.views.renderRow(_16,_17);
},rowRemoved:function(_18){
this.views.rowRemoved(_18);
},invalidated:null,updating:false,beginUpdate:function(){
if(this.invalidated==null){
this.invalidated={rows:[],count:1,all:false,rowCount:undefined};
}else{
this.invalidated.count++;
}
this.updating=true;
},endUpdate:function(){
var i=this.invalidated;
if(--i.count===0){
this.updating=false;
if(i.rows.length>0){
for(var r in i.rows){
this.updateRow(Number(r));
}
this.invalidated.rows=[];
}
if(i.rowCount!=undefined){
this.updateRowCount(i.rowCount);
i.rowCount=undefined;
}
if(i.all){
this.update();
i.all=false;
}
}
},defaultUpdate:function(){
if(!this.domNode){
return;
}
if(this.updating){
this.invalidated.all=true;
return;
}
this.prerender();
this.scroller.invalidateNodes();
this.setScrollTop(this.scrollTop);
this.postrender();
},update:function(){
this.render();
},updateRow:function(_1b){
_1b=Number(_1b);
if(this.updating){
this.invalidated.rows[_1b]=true;
}else{
this.views.updateRow(_1b,this.rows.getHeight(_1b));
this.scroller.rowHeightChanged(_1b);
}
},updateRowCount:function(_1c){
if(this.updating){
this.invalidated.rowCount=_1c;
}else{
this.rowCount=_1c;
if(this.layout.cells.length){
this.scroller.updateRowCount(_1c);
this.setScrollTop(this.scrollTop);
}
this._resize();
}
},updateRowStyles:function(_1d){
this.views.updateRowStyles(_1d);
},rowHeightChanged:function(_1e){
this.views.renormalizeRow(_1e);
this.scroller.rowHeightChanged(_1e);
},fastScroll:true,delayScroll:false,scrollRedrawThreshold:(dojo.isIE?100:50),scrollTo:function(_1f){
if(!this.fastScroll){
this.setScrollTop(_1f);
return;
}
var _20=Math.abs(this.lastScrollTop-_1f);
this.lastScrollTop=_1f;
if(_20>this.scrollRedrawThreshold||this.delayScroll){
this.delayScroll=true;
this.scrollTop=_1f;
this.views.setScrollTop(_1f);
dojox.grid.jobs.job("dojoxGrid-scroll",200,dojo.hitch(this,"finishScrollJob"));
}else{
this.setScrollTop(_1f);
}
},finishScrollJob:function(){
this.delayScroll=false;
this.setScrollTop(this.scrollTop);
},setScrollTop:function(_21){
this.scrollTop=this.views.setScrollTop(_21);
this.scroller.scroll(this.scrollTop);
},scrollToRow:function(_22){
this.setScrollTop(this.scroller.findScrollTop(_22)+1);
},styleRowNode:function(_23,_24){
if(_24){
this.rows.styleRowNode(_23,_24);
}
},getCell:function(_25){
return this.layout.cells[_25];
},setCellWidth:function(_26,_27){
this.getCell(_26).unitWidth=_27;
},getCellName:function(_28){
return "Cell "+_28.index;
},canSort:function(_29){
},sort:function(){
},getSortAsc:function(_2a){
_2a=_2a==undefined?this.sortInfo:_2a;
return Boolean(_2a>0);
},getSortIndex:function(_2b){
_2b=_2b==undefined?this.sortInfo:_2b;
return Math.abs(_2b)-1;
},setSortIndex:function(_2c,_2d){
var si=_2c+1;
if(_2d!=undefined){
si*=(_2d?1:-1);
}else{
if(this.getSortIndex()==_2c){
si=-this.sortInfo;
}
}
this.setSortInfo(si);
},setSortInfo:function(_2f){
if(this.canSort(_2f)){
this.sortInfo=_2f;
this.sort();
this.update();
}
},doKeyEvent:function(e){
e.dispatch="do"+e.type;
this.onKeyEvent(e);
},_dispatch:function(m,e){
if(m in this){
return this[m](e);
}
},dispatchKeyEvent:function(e){
this._dispatch(e.dispatch,e);
},dispatchContentEvent:function(e){
this.edit.dispatchEvent(e)||e.sourceView.dispatchContentEvent(e)||this._dispatch(e.dispatch,e);
},dispatchHeaderEvent:function(e){
e.sourceView.dispatchHeaderEvent(e)||this._dispatch("doheader"+e.type,e);
},dokeydown:function(e){
this.onKeyDown(e);
},doclick:function(e){
if(e.cellNode){
this.onCellClick(e);
}else{
this.onRowClick(e);
}
},dodblclick:function(e){
if(e.cellNode){
this.onCellDblClick(e);
}else{
this.onRowDblClick(e);
}
},docontextmenu:function(e){
if(e.cellNode){
this.onCellContextMenu(e);
}else{
this.onRowContextMenu(e);
}
},doheaderclick:function(e){
if(e.cellNode){
this.onHeaderCellClick(e);
}else{
this.onHeaderClick(e);
}
},doheaderdblclick:function(e){
if(e.cellNode){
this.onHeaderCellDblClick(e);
}else{
this.onHeaderDblClick(e);
}
},doheadercontextmenu:function(e){
if(e.cellNode){
this.onHeaderCellContextMenu(e);
}else{
this.onHeaderContextMenu(e);
}
},doStartEdit:function(_3d,_3e){
this.onStartEdit(_3d,_3e);
},doApplyCellEdit:function(_3f,_40,_41){
this.onApplyCellEdit(_3f,_40,_41);
},doCancelEdit:function(_42){
this.onCancelEdit(_42);
},doApplyEdit:function(_43){
this.onApplyEdit(_43);
},addRow:function(){
this.updateRowCount(this.rowCount+1);
},removeSelectedRows:function(){
this.updateRowCount(Math.max(0,this.rowCount-this.selection.getSelected().length));
this.selection.clear();
}});
dojo.mixin(dojox.VirtualGrid.prototype,dojox.grid.publicEvents);
}
