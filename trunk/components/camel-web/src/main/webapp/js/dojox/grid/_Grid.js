/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid._Grid"]){
dojo._hasResource["dojox.grid._Grid"]=true;
dojo.provide("dojox.grid._Grid");
dojo.require("dijit.dijit");
dojo.require("dijit.Menu");
dojo.require("dojox.html.metrics");
dojo.require("dojox.grid.util");
dojo.require("dojox.grid._Scroller");
dojo.require("dojox.grid._Layout");
dojo.require("dojox.grid._View");
dojo.require("dojox.grid._ViewManager");
dojo.require("dojox.grid._RowManager");
dojo.require("dojox.grid._FocusManager");
dojo.require("dojox.grid._EditManager");
dojo.require("dojox.grid.Selection");
dojo.require("dojox.grid._RowSelector");
dojo.require("dojox.grid._Events");
dojo.requireLocalization("dijit","loading",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
(function(){
var _1={cancel:function(_2){
if(_2){
clearTimeout(_2);
}
},jobs:[],job:function(_3,_4,_5){
_1.cancelJob(_3);
var _6=function(){
delete _1.jobs[_3];
_5();
};
_1.jobs[_3]=setTimeout(_6,_4);
},cancelJob:function(_7){
_1.cancel(_1.jobs[_7]);
}};
dojo.declare("dojox.grid._Grid",[dijit._Widget,dijit._Templated,dojox.grid._Events],{templateString:"<div class=\"dojoxGrid\" hidefocus=\"hidefocus\" role=\"wairole:grid\" dojoAttachEvent=\"onmouseout:_mouseOut\">\n\t<div class=\"dojoxGridMasterHeader\" dojoAttachPoint=\"viewsHeaderNode\" tabindex=\"-1\"></div>\n\t<div class=\"dojoxGridMasterView\" dojoAttachPoint=\"viewsNode\"></div>\n\t<div class=\"dojoxGridMasterMessages\" style=\"display: none;\" dojoAttachPoint=\"messagesNode\"></div>\n\t<span dojoAttachPoint=\"lastFocusNode\" tabindex=\"0\"></span>\n</div>\n",classTag:"dojoxGrid",get:function(_8){
},rowCount:5,keepRows:75,rowsPerPage:25,autoWidth:false,autoHeight:"",autoRender:true,defaultHeight:"15em",height:"",structure:null,elasticView:-1,singleClickEdit:false,selectionMode:"extended",rowSelector:"",columnReordering:false,headerMenu:null,placeholderLabel:"GridColumns",selectable:false,_click:null,loadingMessage:"<span class='dojoxGridLoading'>${loadingState}</span>",errorMessage:"<span class='dojoxGridError'>${errorState}</span>",noDataMessage:"",sortInfo:0,themeable:true,_placeholders:null,buildRendering:function(){
this.inherited(arguments);
if(this.get==dojox.grid._Grid.prototype.get){
this.get=null;
}
if(!this.domNode.getAttribute("tabIndex")){
this.domNode.tabIndex="0";
}
this.createScroller();
this.createLayout();
this.createViews();
this.createManagers();
this.createSelection();
this.connect(this.selection,"onSelected","onSelected");
this.connect(this.selection,"onDeselected","onDeselected");
this.connect(this.selection,"onChanged","onSelectionChanged");
dojox.html.metrics.initOnFontResize();
this.connect(dojox.html.metrics,"onFontResize","textSizeChanged");
dojox.grid.util.funnelEvents(this.domNode,this,"doKeyEvent",dojox.grid.util.keyEvents);
this.connect(this,"onShow","renderOnIdle");
},postMixInProperties:function(){
this.inherited(arguments);
var _9=dojo.i18n.getLocalization("dijit","loading",this.lang);
this.loadingMessage=dojo.string.substitute(this.loadingMessage,_9);
this.errorMessage=dojo.string.substitute(this.errorMessage,_9);
if(this.srcNodeRef&&this.srcNodeRef.style.height){
this.height=this.srcNodeRef.style.height;
}
this._setAutoHeightAttr(this.autoHeight,true);
},postCreate:function(){
this.styleChanged=this._styleChanged;
this._placeholders=[];
this._setHeaderMenuAttr(this.headerMenu);
this._setStructureAttr(this.structure);
this._click=[];
},destroy:function(){
this.domNode.onReveal=null;
this.domNode.onSizeChange=null;
delete this._click;
this.edit.destroy();
delete this.edit;
this.views.destroyViews();
if(this.scroller){
this.scroller.destroy();
delete this.scroller;
}
if(this.focus){
this.focus.destroy();
delete this.focus;
}
if(this.headerMenu&&this._placeholders.length){
dojo.forEach(this._placeholders,function(p){
p.unReplace(true);
});
this.headerMenu.unBindDomNode(this.viewsHeaderNode);
}
this.inherited(arguments);
},_setAutoHeightAttr:function(ah,_c){
if(typeof ah=="string"){
if(!ah||ah=="false"){
ah=false;
}else{
if(ah=="true"){
ah=true;
}else{
ah=window.parseInt(ah,10);
if(isNaN(ah)){
ah=false;
}
if(ah<0){
ah=true;
}else{
if(ah===0){
ah=false;
}
}
}
}
}
this.autoHeight=ah;
if(typeof ah=="boolean"){
this._autoHeight=ah;
}else{
if(typeof ah=="number"){
this._autoHeight=(ah>=this.attr("rowCount"));
}else{
this._autoHeight=false;
}
}
if(this._started&&!_c){
this.render();
}
},_getRowCountAttr:function(){
return this.updating&&this.invalidated&&this.invalidated.rowCount!=undefined?this.invalidated.rowCount:this.rowCount;
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
_1.job(this.id+"SizeChange",50,dojo.hitch(this,"update"));
},renderOnIdle:function(){
setTimeout(dojo.hitch(this,"render"),1);
},createManagers:function(){
this.rows=new dojox.grid._RowManager(this);
this.focus=new dojox.grid._FocusManager(this);
this.edit=new dojox.grid._EditManager(this);
},createSelection:function(){
this.selection=new dojox.grid.Selection(this);
},createScroller:function(){
this.scroller=new dojox.grid._Scroller();
this.scroller.grid=this;
this.scroller._pageIdPrefix=this.id+"-";
this.scroller.renderRow=dojo.hitch(this,"renderRow");
this.scroller.removeRow=dojo.hitch(this,"rowRemoved");
},createLayout:function(){
this.layout=new dojox.grid._Layout(this);
this.connect(this.layout,"moveColumn","onMoveColumn");
},onMoveColumn:function(){
this.render();
this._resize();
},createViews:function(){
this.views=new dojox.grid._ViewManager(this);
this.views.createView=dojo.hitch(this,"createView");
},createView:function(_e,_f){
var c=dojo.getObject(_e);
var _11=new c({grid:this,index:_f});
this.viewsNode.appendChild(_11.domNode);
this.viewsHeaderNode.appendChild(_11.headerNode);
this.views.addView(_11);
return _11;
},buildViews:function(){
for(var i=0,vs;(vs=this.layout.structure[i]);i++){
this.createView(vs.type||dojox._scopeName+".grid._View",i).setStructure(vs);
}
this.scroller.setContentNodes(this.views.getContentNodes());
},_setStructureAttr:function(_14){
var s=_14;
if(s&&dojo.isString(s)){
dojo.deprecated("dojox.grid._Grid.attr('structure', 'objVar')","use dojox.grid._Grid.attr('structure', objVar) instead","2.0");
s=dojo.getObject(s);
}
this.structure=s;
if(!s){
if(this.layout.structure){
s=this.layout.structure;
}else{
return;
}
}
this.views.destroyViews();
if(s!==this.layout.structure){
this.layout.setStructure(s);
}
this._structureChanged();
},setStructure:function(_16){
dojo.deprecated("dojox.grid._Grid.setStructure(obj)","use dojox.grid._Grid.attr('structure', obj) instead.","2.0");
this._setStructureAttr(_16);
},getColumnTogglingItems:function(){
return dojo.map(this.layout.cells,function(_17){
if(!_17.menuItems){
_17.menuItems=[];
}
var _18=this;
var _19=new dijit.CheckedMenuItem({label:_17.name,checked:!_17.hidden,_gridCell:_17,onChange:function(_1a){
if(_18.layout.setColumnVisibility(this._gridCell.index,_1a)){
var _1b=this._gridCell.menuItems;
if(_1b.length>1){
dojo.forEach(_1b,function(_1c){
if(_1c!==this){
_1c.setAttribute("checked",_1a);
}
},this);
}
var _1a=dojo.filter(_18.layout.cells,function(c){
if(c.menuItems.length>1){
dojo.forEach(c.menuItems,"item.attr('disabled', false);");
}else{
c.menuItems[0].attr("disabled",false);
}
return !c.hidden;
});
if(_1a.length==1){
dojo.forEach(_1a[0].menuItems,"item.attr('disabled', true);");
}
}
},destroy:function(){
var _1e=dojo.indexOf(this._gridCell.menuItems,this);
this._gridCell.menuItems.splice(_1e,1);
delete this._gridCell;
dijit.CheckedMenuItem.prototype.destroy.apply(this,arguments);
}});
_17.menuItems.push(_19);
return _19;
},this);
},_setHeaderMenuAttr:function(_1f){
if(this._placeholders.length){
dojo.forEach(this._placeholders,function(p){
p.unReplace(true);
});
this._placeholders=[];
}
if(this.headerMenu){
this.headerMenu.unBindDomNode(this.viewsHeaderNode);
}
this.headerMenu=_1f;
if(!_1f){
return;
}
this.headerMenu.bindDomNode(this.viewsHeaderNode);
if(this.headerMenu.getPlaceholders){
this._placeholders=this.headerMenu.getPlaceholders(this.placeholderLabel);
}
},setHeaderMenu:function(_21){
dojo.deprecated("dojox.grid._Grid.setHeaderMenu(obj)","use dojox.grid._Grid.attr('headerMenu', obj) instead.","2.0");
this._setHeaderMenuAttr(_21);
},setupHeaderMenu:function(){
if(this._placeholders&&this._placeholders.length){
dojo.forEach(this._placeholders,function(p){
if(p._replaced){
p.unReplace(true);
}
p.replace(this.getColumnTogglingItems());
},this);
}
},_fetch:function(_23){
this.setScrollTop(0);
},getItem:function(_24){
return null;
},showMessage:function(_25){
if(_25){
this.messagesNode.innerHTML=_25;
this.messagesNode.style.display="";
}else{
this.messagesNode.innerHTML="";
this.messagesNode.style.display="none";
}
},_structureChanged:function(){
this.buildViews();
if(this.autoRender&&this._started){
this.render();
}
},hasLayout:function(){
return this.layout.cells.length;
},resize:function(_26,_27){
this._resize(_26,_27);
this.sizeChange();
},_getPadBorder:function(){
this._padBorder=this._padBorder||dojo._getPadBorderExtents(this.domNode);
return this._padBorder;
},_getHeaderHeight:function(){
var vns=this.viewsHeaderNode.style,t=vns.display=="none"?0:this.views.measureHeader();
vns.height=t+"px";
this.views.normalizeHeaderNodeHeight();
return t;
},_resize:function(_2a,_2b){
var pn=this.domNode.parentNode;
if(!pn||pn.nodeType!=1||!this.hasLayout()||pn.style.visibility=="hidden"||pn.style.display=="none"){
return;
}
var _2d=this._getPadBorder();
var hh=0;
if(this._autoHeight){
this.domNode.style.height="auto";
this.viewsNode.style.height="";
}else{
if(typeof this.autoHeight=="number"){
var h=hh=this._getHeaderHeight();
h+=(this.scroller.averageRowHeight*this.autoHeight);
this.domNode.style.height=h+"px";
}else{
if(this.flex>0){
}else{
if(this.domNode.clientHeight<=_2d.h){
if(pn==document.body){
this.domNode.style.height=this.defaultHeight;
}else{
if(this.height){
this.domNode.style.height=this.height;
}else{
this.fitTo="parent";
}
}
}
}
}
}
if(_2b){
_2a=_2b;
}
if(_2a){
dojo.marginBox(this.domNode,_2a);
this.height=this.domNode.style.height;
delete this.fitTo;
}else{
if(this.fitTo=="parent"){
var h=dojo._getContentBox(pn).h;
dojo.marginBox(this.domNode,{h:Math.max(0,h)});
}
}
var h=dojo._getContentBox(this.domNode).h;
if(h==0&&!this._autoHeight){
this.viewsHeaderNode.style.display="none";
}else{
this.viewsHeaderNode.style.display="block";
hh=this._getHeaderHeight();
}
this.adaptWidth();
this.adaptHeight(hh);
this.postresize();
},adaptWidth:function(){
var w=this.autoWidth?0:this.domNode.clientWidth||(this.domNode.offsetWidth-this._getPadBorder().w),vw=this.views.arrange(1,w);
this.views.onEach("adaptWidth");
if(this.autoWidth){
this.domNode.style.width=vw+"px";
}
},adaptHeight:function(_32){
var t=_32||this._getHeaderHeight();
var h=(this._autoHeight?-1:Math.max(this.domNode.clientHeight-t,0)||0);
this.views.onEach("setSize",[0,h]);
this.views.onEach("adaptHeight");
if(!this._autoHeight){
var _35=0,_36=0;
var _37=dojo.filter(this.views.views,function(v){
var has=v.hasHScrollbar();
if(has){
_35++;
}else{
_36++;
}
return (!has);
});
if(_35>0&&_36>0){
dojo.forEach(_37,function(v){
v.adaptHeight(true);
});
}
}
if(this.autoHeight===true||h!=-1||(typeof this.autoHeight=="number"&&this.autoHeight>=this.attr("rowCount"))){
this.scroller.windowHeight=h;
}else{
this.scroller.windowHeight=Math.max(this.domNode.clientHeight-t,0);
}
},startup:function(){
if(this._started){
return;
}
this.inherited(arguments);
if(this.autoRender){
this.render();
}
},render:function(){
if(!this.domNode){
return;
}
if(!this._started){
return;
}
if(!this.hasLayout()){
this.scroller.init(0,this.keepRows,this.rowsPerPage);
return;
}
this.update=this.defaultUpdate;
this._render();
},_render:function(){
this.scroller.init(this.attr("rowCount"),this.keepRows,this.rowsPerPage);
this.prerender();
this.setScrollTop(0);
this.postrender();
},prerender:function(){
this.keepRows=this._autoHeight?0:this.keepRows;
this.scroller.setKeepInfo(this.keepRows);
this.views.render();
this._resize();
},postrender:function(){
this.postresize();
this.focus.initFocusView();
dojo.setSelectable(this.domNode,this.selectable);
},postresize:function(){
if(this._autoHeight){
var _3b=Math.max(this.views.measureContent())+"px";
this.viewsNode.style.height=_3b;
}
},renderRow:function(_3c,_3d){
this.views.renderRow(_3c,_3d);
},rowRemoved:function(_3e){
this.views.rowRemoved(_3e);
},invalidated:null,updating:false,beginUpdate:function(){
this.invalidated=[];
this.updating=true;
},endUpdate:function(){
this.updating=false;
var i=this.invalidated,r;
if(i.all){
this.update();
}else{
if(i.rowCount!=undefined){
this.updateRowCount(i.rowCount);
}else{
for(r in i){
this.updateRow(Number(r));
}
}
}
this.invalidated=null;
},defaultUpdate:function(){
if(!this.domNode){
return;
}
if(this.updating){
this.invalidated.all=true;
return;
}
var _41=this.scrollTop;
this.prerender();
this.scroller.invalidateNodes();
this.setScrollTop(_41);
this.postrender();
},update:function(){
this.render();
},updateRow:function(_42){
_42=Number(_42);
if(this.updating){
this.invalidated[_42]=true;
}else{
this.views.updateRow(_42);
this.scroller.rowHeightChanged(_42);
}
},updateRows:function(_43,_44){
_43=Number(_43);
_44=Number(_44);
if(this.updating){
for(var i=0;i<_44;i++){
this.invalidated[i+_43]=true;
}
}else{
for(var i=0;i<_44;i++){
this.views.updateRow(i+_43);
}
this.scroller.rowHeightChanged(_43);
}
},updateRowCount:function(_46){
if(this.updating){
this.invalidated.rowCount=_46;
}else{
this.rowCount=_46;
this._setAutoHeightAttr(this.autoHeight,true);
if(this.layout.cells.length){
this.scroller.updateRowCount(_46);
}
this._resize();
if(this.layout.cells.length){
this.setScrollTop(this.scrollTop);
}
}
},updateRowStyles:function(_47){
this.views.updateRowStyles(_47);
},rowHeightChanged:function(_48){
this.views.renormalizeRow(_48);
this.scroller.rowHeightChanged(_48);
},fastScroll:true,delayScroll:false,scrollRedrawThreshold:(dojo.isIE?100:50),scrollTo:function(_49){
if(!this.fastScroll){
this.setScrollTop(_49);
return;
}
var _4a=Math.abs(this.lastScrollTop-_49);
this.lastScrollTop=_49;
if(_4a>this.scrollRedrawThreshold||this.delayScroll){
this.delayScroll=true;
this.scrollTop=_49;
this.views.setScrollTop(_49);
_1.job("dojoxGridScroll",200,dojo.hitch(this,"finishScrollJob"));
}else{
this.setScrollTop(_49);
}
},finishScrollJob:function(){
this.delayScroll=false;
this.setScrollTop(this.scrollTop);
},setScrollTop:function(_4b){
this.scroller.scroll(this.views.setScrollTop(_4b));
},scrollToRow:function(_4c){
this.setScrollTop(this.scroller.findScrollTop(_4c)+1);
},styleRowNode:function(_4d,_4e){
if(_4e){
this.rows.styleRowNode(_4d,_4e);
}
},_mouseOut:function(e){
this.rows.setOverRow(-2);
},getCell:function(_50){
return this.layout.cells[_50];
},setCellWidth:function(_51,_52){
this.getCell(_51).unitWidth=_52;
},getCellName:function(_53){
return "Cell "+_53.index;
},canSort:function(_54){
},sort:function(){
},getSortAsc:function(_55){
_55=_55==undefined?this.sortInfo:_55;
return Boolean(_55>0);
},getSortIndex:function(_56){
_56=_56==undefined?this.sortInfo:_56;
return Math.abs(_56)-1;
},setSortIndex:function(_57,_58){
var si=_57+1;
if(_58!=undefined){
si*=(_58?1:-1);
}else{
if(this.getSortIndex()==_57){
si=-this.sortInfo;
}
}
this.setSortInfo(si);
},setSortInfo:function(_5a){
if(this.canSort(_5a)){
this.sortInfo=_5a;
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
},doStartEdit:function(_68,_69){
this.onStartEdit(_68,_69);
},doApplyCellEdit:function(_6a,_6b,_6c){
this.onApplyCellEdit(_6a,_6b,_6c);
},doCancelEdit:function(_6d){
this.onCancelEdit(_6d);
},doApplyEdit:function(_6e){
this.onApplyEdit(_6e);
},addRow:function(){
this.updateRowCount(this.attr("rowCount")+1);
},removeSelectedRows:function(){
this.updateRowCount(Math.max(0,this.attr("rowCount")-this.selection.getSelected().length));
this.selection.clear();
}});
dojox.grid._Grid.markupFactory=function(_6f,_70,_71,_72){
var d=dojo;
var _74=function(n){
var w=d.attr(n,"width")||"auto";
if((w!="auto")&&(w.slice(-2)!="em")&&(w.slice(-1)!="%")){
w=parseInt(w)+"px";
}
return w;
};
if(!_6f.structure&&_70.nodeName.toLowerCase()=="table"){
_6f.structure=d.query("> colgroup",_70).map(function(cg){
var sv=d.attr(cg,"span");
var v={noscroll:(d.attr(cg,"noscroll")=="true")?true:false,__span:(!!sv?parseInt(sv):1),cells:[]};
if(d.hasAttr(cg,"width")){
v.width=_74(cg);
}
return v;
});
if(!_6f.structure.length){
_6f.structure.push({__span:Infinity,cells:[]});
}
d.query("thead > tr",_70).forEach(function(tr,_7b){
var _7c=0;
var _7d=0;
var _7e;
var _7f=null;
d.query("> th",tr).map(function(th){
if(!_7f){
_7e=0;
_7f=_6f.structure[0];
}else{
if(_7c>=(_7e+_7f.__span)){
_7d++;
_7e+=_7f.__span;
var _81=_7f;
_7f=_6f.structure[_7d];
}
}
var _82={name:d.trim(d.attr(th,"name")||th.innerHTML),colSpan:parseInt(d.attr(th,"colspan")||1,10),type:d.trim(d.attr(th,"cellType")||"")};
_7c+=_82.colSpan;
var _83=d.attr(th,"rowspan");
if(_83){
_82.rowSpan=_83;
}
if(d.hasAttr(th,"width")){
_82.width=_74(th);
}
if(d.hasAttr(th,"relWidth")){
_82.relWidth=window.parseInt(dojo.attr(th,"relWidth"),10);
}
if(d.hasAttr(th,"hidden")){
_82.hidden=d.attr(th,"hidden")=="true";
}
if(_72){
_72(th,_82);
}
_82.type=_82.type?dojo.getObject(_82.type):dojox.grid.cells.Cell;
if(_82.type&&_82.type.markupFactory){
_82.type.markupFactory(th,_82);
}
if(!_7f.cells[_7b]){
_7f.cells[_7b]=[];
}
_7f.cells[_7b].push(_82);
});
});
}
return new _71(_6f,_70);
};
})();
}
