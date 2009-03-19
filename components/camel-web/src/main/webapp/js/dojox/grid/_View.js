/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid._View"]){
dojo._hasResource["dojox.grid._View"]=true;
dojo.provide("dojox.grid._View");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojox.grid._Builder");
dojo.require("dojox.html.metrics");
dojo.require("dojox.grid.util");
dojo.require("dojo.dnd.Source");
dojo.require("dojo.dnd.Manager");
(function(){
var _1=function(_2,_3){
return _2.style.cssText==undefined?_2.getAttribute("style"):_2.style.cssText;
};
dojo.declare("dojox.grid._View",[dijit._Widget,dijit._Templated],{defaultWidth:"18em",viewWidth:"",templateString:"<div class=\"dojoxGridView\" role=\"presentation\">\n\t<div class=\"dojoxGridHeader\" dojoAttachPoint=\"headerNode\" role=\"presentation\">\n\t\t<div dojoAttachPoint=\"headerNodeContainer\" style=\"width:9000em\" role=\"presentation\">\n\t\t\t<div dojoAttachPoint=\"headerContentNode\" role=\"presentation\"></div>\n\t\t</div>\n\t</div>\n\t<input type=\"checkbox\" class=\"dojoxGridHiddenFocus\" dojoAttachPoint=\"hiddenFocusNode\" />\n\t<input type=\"checkbox\" class=\"dojoxGridHiddenFocus\" />\n\t<div class=\"dojoxGridScrollbox\" dojoAttachPoint=\"scrollboxNode\" role=\"presentation\">\n\t\t<div class=\"dojoxGridContent\" dojoAttachPoint=\"contentNode\" hidefocus=\"hidefocus\" role=\"presentation\"></div>\n\t</div>\n</div>\n",themeable:false,classTag:"dojoxGrid",marginBottom:0,rowPad:2,_togglingColumn:-1,postMixInProperties:function(){
this.rowNodes=[];
},postCreate:function(){
this.connect(this.scrollboxNode,"onscroll","doscroll");
dojox.grid.util.funnelEvents(this.contentNode,this,"doContentEvent",["mouseover","mouseout","click","dblclick","contextmenu","mousedown"]);
dojox.grid.util.funnelEvents(this.headerNode,this,"doHeaderEvent",["dblclick","mouseover","mouseout","mousemove","mousedown","click","contextmenu"]);
this.content=new dojox.grid._ContentBuilder(this);
this.header=new dojox.grid._HeaderBuilder(this);
if(!dojo._isBodyLtr()){
this.headerNodeContainer.style.width="";
}
},destroy:function(){
dojo.destroy(this.headerNode);
delete this.headerNode;
dojo.forEach(this.rowNodes,dojo.destroy);
this.rowNodes=[];
if(this.source){
this.source.destroy();
}
this.inherited(arguments);
},focus:function(){
if(dojo.isWebKit||dojo.isOpera){
this.hiddenFocusNode.focus();
}else{
this.scrollboxNode.focus();
}
},setStructure:function(_4){
var vs=(this.structure=_4);
if(vs.width&&!isNaN(vs.width)){
this.viewWidth=vs.width+"em";
}else{
this.viewWidth=vs.width||(vs.noscroll?"auto":this.viewWidth);
}
this.onBeforeRow=vs.onBeforeRow;
this.onAfterRow=vs.onAfterRow;
this.noscroll=vs.noscroll;
if(this.noscroll){
this.scrollboxNode.style.overflow="hidden";
}
this.simpleStructure=Boolean(vs.cells.length==1);
this.testFlexCells();
this.updateStructure();
},testFlexCells:function(){
this.flexCells=false;
for(var j=0,_7;(_7=this.structure.cells[j]);j++){
for(var i=0,_9;(_9=_7[i]);i++){
_9.view=this;
this.flexCells=this.flexCells||_9.isFlex();
}
}
return this.flexCells;
},updateStructure:function(){
this.header.update();
this.content.update();
},getScrollbarWidth:function(){
var _a=this.hasVScrollbar();
var _b=dojo.style(this.scrollboxNode,"overflow");
if(this.noscroll||!_b||_b=="hidden"){
_a=false;
}else{
if(_b=="scroll"){
_a=true;
}
}
return (_a?dojox.html.metrics.getScrollbar().w:0);
},getColumnsWidth:function(){
return this.headerContentNode.firstChild.offsetWidth;
},setColumnsWidth:function(_c){
this.headerContentNode.firstChild.style.width=_c+"px";
if(this.viewWidth){
this.viewWidth=_c+"px";
}
},getWidth:function(){
return this.viewWidth||(this.getColumnsWidth()+this.getScrollbarWidth())+"px";
},getContentWidth:function(){
return Math.max(0,dojo._getContentBox(this.domNode).w-this.getScrollbarWidth())+"px";
},render:function(){
this.scrollboxNode.style.height="";
this.renderHeader();
if(this._togglingColumn>=0){
this.setColumnsWidth(this.getColumnsWidth()-this._togglingColumn);
this._togglingColumn=-1;
}
var _d=this.grid.layout.cells;
var _e=dojo.hitch(this,function(_f,_10){
var inc=_10?-1:1;
var idx=this.header.getCellNodeIndex(_f)+inc;
var _13=_d[idx];
while(_13&&_13.getHeaderNode()&&_13.getHeaderNode().style.display=="none"){
idx+=inc;
_13=_d[idx];
}
if(_13){
return _13.getHeaderNode();
}
return null;
});
if(this.grid.columnReordering&&this.simpleStructure){
if(this.source){
this.source.destroy();
}
this.source=new dojo.dnd.Source(this.headerContentNode.firstChild.rows[0],{horizontal:true,accept:["gridColumn_"+this.grid.id],viewIndex:this.index,onMouseDown:dojo.hitch(this,function(e){
this.header.decorateEvent(e);
if((this.header.overRightResizeArea(e)||this.header.overLeftResizeArea(e))&&this.header.canResize(e)&&!this.header.moveable){
this.header.beginColumnResize(e);
}else{
if(this.grid.headerMenu){
this.grid.headerMenu.onCancel(true);
}
if(e.button===(dojo.isIE?1:0)){
dojo.dnd.Source.prototype.onMouseDown.call(this.source,e);
}
}
}),_markTargetAnchor:dojo.hitch(this,function(_15){
var src=this.source;
if(src.current==src.targetAnchor&&src.before==_15){
return;
}
if(src.targetAnchor&&_e(src.targetAnchor,src.before)){
src._removeItemClass(_e(src.targetAnchor,src.before),src.before?"After":"Before");
}
dojo.dnd.Source.prototype._markTargetAnchor.call(src,_15);
if(src.targetAnchor&&_e(src.targetAnchor,src.before)){
src._addItemClass(_e(src.targetAnchor,src.before),src.before?"After":"Before");
}
}),_unmarkTargetAnchor:dojo.hitch(this,function(){
var src=this.source;
if(!src.targetAnchor){
return;
}
if(src.targetAnchor&&_e(src.targetAnchor,src.before)){
src._removeItemClass(_e(src.targetAnchor,src.before),src.before?"After":"Before");
}
dojo.dnd.Source.prototype._unmarkTargetAnchor.call(src);
}),destroy:dojo.hitch(this,function(){
dojo.disconnect(this._source_conn);
dojo.unsubscribe(this._source_sub);
dojo.dnd.Source.prototype.destroy.call(this.source);
})});
this._source_conn=dojo.connect(this.source,"onDndDrop",this,"_onDndDrop");
this._source_sub=dojo.subscribe("/dnd/drop/before",this,"_onDndDropBefore");
this.source.startup();
}
},_onDndDropBefore:function(_18,_19,_1a){
if(dojo.dnd.manager().target!==this.source){
return;
}
this.source._targetNode=this.source.targetAnchor;
this.source._beforeTarget=this.source.before;
var _1b=this.grid.views.views;
var _1c=_1b[_18.viewIndex];
var _1d=_1b[this.index];
if(_1d!=_1c){
var s=_1c.convertColPctToFixed();
var t=_1d.convertColPctToFixed();
if(s||t){
setTimeout(function(){
_1c.update();
_1d.update();
},50);
}
}
},_onDndDrop:function(_20,_21,_22){
if(dojo.dnd.manager().target!==this.source){
if(dojo.dnd.manager().source===this.source){
this._removingColumn=true;
}
return;
}
var _23=function(n){
return n?dojo.attr(n,"idx"):null;
};
var w=dojo.marginBox(_21[0]).w;
if(_20.viewIndex!==this.index){
var _26=this.grid.views.views;
var _27=_26[_20.viewIndex];
var _28=_26[this.index];
if(_27.viewWidth&&_27.viewWidth!="auto"){
_27.setColumnsWidth(_27.getColumnsWidth()-w);
}
if(_28.viewWidth&&_28.viewWidth!="auto"){
_28.setColumnsWidth(_28.getColumnsWidth());
}
}
var stn=this.source._targetNode;
var stb=this.source._beforeTarget;
var _2b=this.grid.layout;
var idx=this.index;
delete this.source._targetNode;
delete this.source._beforeTarget;
window.setTimeout(function(){
_2b.moveColumn(_20.viewIndex,idx,_23(_21[0]),_23(stn),stb);
},1);
},renderHeader:function(){
this.headerContentNode.innerHTML=this.header.generateHtml(this._getHeaderContent);
if(this.flexCells){
this.contentWidth=this.getContentWidth();
this.headerContentNode.firstChild.style.width=this.contentWidth;
}
dojox.grid.util.fire(this,"onAfterRow",[-1,this.structure.cells,this.headerContentNode]);
},_getHeaderContent:function(_2d){
var n=_2d.name||_2d.grid.getCellName(_2d);
var ret=["<div class=\"dojoxGridSortNode"];
if(_2d.index!=_2d.grid.getSortIndex()){
ret.push("\">");
}else{
ret=ret.concat([" ",_2d.grid.sortInfo>0?"dojoxGridSortUp":"dojoxGridSortDown","\"><div class=\"dojoxGridArrowButtonChar\">",_2d.grid.sortInfo>0?"&#9650;":"&#9660;","</div><div class=\"dojoxGridArrowButtonNode\"></div>"]);
}
ret=ret.concat([n,"</div>"]);
return ret.join("");
},resize:function(){
this.adaptHeight();
this.adaptWidth();
},hasHScrollbar:function(_30){
if(this._hasHScroll==undefined||_30){
if(this.noscroll){
this._hasHScroll=false;
}else{
var _31=dojo.style(this.scrollboxNode,"overflow");
if(_31=="hidden"){
this._hasHScroll=false;
}else{
if(_31=="scroll"){
this._hasHScroll=true;
}else{
this._hasHScroll=(this.scrollboxNode.offsetWidth<this.contentNode.offsetWidth);
}
}
}
}
return this._hasHScroll;
},hasVScrollbar:function(_32){
if(this._hasVScroll==undefined||_32){
if(this.noscroll){
this._hasVScroll=false;
}else{
var _33=dojo.style(this.scrollboxNode,"overflow");
if(_33=="hidden"){
this._hasVScroll=false;
}else{
if(_33=="scroll"){
this._hasVScroll=true;
}else{
this._hasVScroll=(this.scrollboxNode.offsetHeight<this.contentNode.offsetHeight);
}
}
}
}
return this._hasVScroll;
},convertColPctToFixed:function(){
var _34=false;
var _35=dojo.query("th",this.headerContentNode);
var _36=dojo.map(_35,function(c){
var w=c.style.width;
if(w&&w.slice(-1)=="%"){
_34=true;
return dojo.contentBox(c).w;
}else{
if(w&&w.slice(-2)=="px"){
return window.parseInt(w,10);
}
}
return -1;
});
if(_34){
dojo.forEach(this.grid.layout.cells,function(_39,idx){
if(_39.view==this){
var _3b=_39.layoutIndex;
this.setColWidth(idx,_36[_3b]);
_35[_3b].style.width=_39.unitWidth;
}
},this);
return true;
}
return false;
},adaptHeight:function(_3c){
if(!this.grid._autoHeight){
var h=this.domNode.clientHeight;
if(_3c){
h-=dojox.html.metrics.getScrollbar().h;
}
dojox.grid.util.setStyleHeightPx(this.scrollboxNode,h);
}
this.hasVScrollbar(true);
},adaptWidth:function(){
if(this.flexCells){
this.contentWidth=this.getContentWidth();
this.headerContentNode.firstChild.style.width=this.contentWidth;
}
var w=this.scrollboxNode.offsetWidth-this.getScrollbarWidth();
if(!this._removingColumn){
w=Math.max(w,this.getColumnsWidth())+"px";
}else{
w=Math.min(w,this.getColumnsWidth())+"px";
this._removingColumn=false;
}
var cn=this.contentNode;
cn.style.width=w;
this.hasHScrollbar(true);
},setSize:function(w,h){
var ds=this.domNode.style;
var hs=this.headerNode.style;
if(w){
ds.width=w;
hs.width=w;
}
ds.height=(h>=0?h+"px":"");
},renderRow:function(_44){
var _45=this.createRowNode(_44);
this.buildRow(_44,_45);
this.grid.edit.restore(this,_44);
if(this._pendingUpdate){
window.clearTimeout(this._pendingUpdate);
}
this._pendingUpdate=window.setTimeout(dojo.hitch(this,function(){
window.clearTimeout(this._pendingUpdate);
delete this._pendingUpdate;
this.grid._resize();
}),50);
return _45;
},createRowNode:function(_46){
var _47=document.createElement("div");
_47.className=this.classTag+"Row";
_47[dojox.grid.util.gridViewTag]=this.id;
_47[dojox.grid.util.rowIndexTag]=_46;
this.rowNodes[_46]=_47;
return _47;
},buildRow:function(_48,_49){
this.buildRowContent(_48,_49);
this.styleRow(_48,_49);
},buildRowContent:function(_4a,_4b){
_4b.innerHTML=this.content.generateHtml(_4a,_4a);
if(this.flexCells&&this.contentWidth){
_4b.firstChild.style.width=this.contentWidth;
}
dojox.grid.util.fire(this,"onAfterRow",[_4a,this.structure.cells,_4b]);
},rowRemoved:function(_4c){
this.grid.edit.save(this,_4c);
delete this.rowNodes[_4c];
},getRowNode:function(_4d){
return this.rowNodes[_4d];
},getCellNode:function(_4e,_4f){
var row=this.getRowNode(_4e);
if(row){
return this.content.getCellNode(row,_4f);
}
},getHeaderCellNode:function(_51){
if(this.headerContentNode){
return this.header.getCellNode(this.headerContentNode,_51);
}
},styleRow:function(_52,_53){
_53._style=_1(_53);
this.styleRowNode(_52,_53);
},styleRowNode:function(_54,_55){
if(_55){
this.doStyleRowNode(_54,_55);
}
},doStyleRowNode:function(_56,_57){
this.grid.styleRowNode(_56,_57);
},updateRow:function(_58){
var _59=this.getRowNode(_58);
if(_59){
_59.style.height="";
this.buildRow(_58,_59);
}
return _59;
},updateRowStyles:function(_5a){
this.styleRowNode(_5a,this.getRowNode(_5a));
},lastTop:0,firstScroll:0,doscroll:function(_5b){
var _5c=dojo._isBodyLtr();
if(this.firstScroll<2){
if((!_5c&&this.firstScroll==1)||(_5c&&this.firstScroll==0)){
var s=dojo.marginBox(this.headerNodeContainer);
if(dojo.isIE){
this.headerNodeContainer.style.width=s.w+this.getScrollbarWidth()+"px";
}else{
if(dojo.isMoz){
this.headerNodeContainer.style.width=s.w-this.getScrollbarWidth()+"px";
this.scrollboxNode.scrollLeft=_5c?this.scrollboxNode.clientWidth-this.scrollboxNode.scrollWidth:this.scrollboxNode.scrollWidth-this.scrollboxNode.clientWidth;
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
},setScrollTop:function(_5f){
this.lastTop=_5f;
this.scrollboxNode.scrollTop=_5f;
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
},setColWidth:function(_64,_65){
this.grid.setCellWidth(_64,_65+"px");
},update:function(){
this.content.update();
this.grid.update();
var _66=this.scrollboxNode.scrollLeft;
this.scrollboxNode.scrollLeft=_66;
this.headerNode.scrollLeft=_66;
}});
dojo.declare("dojox.grid._GridAvatar",dojo.dnd.Avatar,{construct:function(){
var dd=dojo.doc;
var a=dd.createElement("table");
a.cellPadding=a.cellSpacing="0";
a.className="dojoxGridDndAvatar";
a.style.position="absolute";
a.style.zIndex=1999;
a.style.margin="0px";
var b=dd.createElement("tbody");
var tr=dd.createElement("tr");
var td=dd.createElement("td");
var img=dd.createElement("td");
tr.className="dojoxGridDndAvatarItem";
img.className="dojoxGridDndAvatarItemImage";
img.style.width="16px";
var _6d=this.manager.source,_6e;
if(_6d.creator){
_6e=_6d._normailzedCreator(_6d.getItem(this.manager.nodes[0].id).data,"avatar").node;
}else{
_6e=this.manager.nodes[0].cloneNode(true);
if(_6e.tagName.toLowerCase()=="tr"){
var _6f=dd.createElement("table"),_70=dd.createElement("tbody");
_70.appendChild(_6e);
_6f.appendChild(_70);
_6e=_6f;
}else{
if(_6e.tagName.toLowerCase()=="th"){
var _6f=dd.createElement("table"),_70=dd.createElement("tbody"),r=dd.createElement("tr");
_6f.cellPadding=_6f.cellSpacing="0";
r.appendChild(_6e);
_70.appendChild(r);
_6f.appendChild(_70);
_6e=_6f;
}
}
}
_6e.id="";
td.appendChild(_6e);
tr.appendChild(img);
tr.appendChild(td);
dojo.style(tr,"opacity",0.9);
b.appendChild(tr);
a.appendChild(b);
this.node=a;
var m=dojo.dnd.manager();
this.oldOffsetY=m.OFFSET_Y;
m.OFFSET_Y=1;
},destroy:function(){
dojo.dnd.manager().OFFSET_Y=this.oldOffsetY;
this.inherited(arguments);
}});
var _73=dojo.dnd.manager().makeAvatar;
dojo.dnd.manager().makeAvatar=function(){
var src=this.source;
if(src.viewIndex!==undefined){
return new dojox.grid._GridAvatar(this);
}
return _73.call(dojo.dnd.manager());
};
})();
}
