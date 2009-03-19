/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.focus"]){
dojo._hasResource["dojox.grid.compat._grid.focus"]=true;
dojo.provide("dojox.grid.compat._grid.focus");
dojo.declare("dojox.grid.focus",null,{constructor:function(_1){
this.grid=_1;
this.cell=null;
this.rowIndex=-1;
dojo.connect(this.grid.domNode,"onfocus",this,"doFocus");
},tabbingOut:false,focusClass:"dojoxGrid-cell-focus",focusView:null,initFocusView:function(){
this.focusView=this.grid.views.getFirstScrollingView();
},isFocusCell:function(_2,_3){
return (this.cell==_2)&&(this.rowIndex==_3);
},isLastFocusCell:function(){
return (this.rowIndex==this.grid.rowCount-1)&&(this.cell.index==this.grid.layout.cellCount-1);
},isFirstFocusCell:function(){
return (this.rowIndex==0)&&(this.cell.index==0);
},isNoFocusCell:function(){
return (this.rowIndex<0)||!this.cell;
},_focusifyCellNode:function(_4){
var n=this.cell&&this.cell.getNode(this.rowIndex);
if(n){
dojo.toggleClass(n,this.focusClass,_4);
if(_4){
this.scrollIntoView();
try{
if(!this.grid.edit.isEditing()){
dojox.grid.fire(n,"focus");
}
}
catch(e){
}
}
}
},scrollIntoView:function(){
if(!this.cell){
return;
}
var c=this.cell,s=c.view.scrollboxNode,sr={w:s.clientWidth,l:s.scrollLeft,t:s.scrollTop,h:s.clientHeight},n=c.getNode(this.rowIndex),r=c.view.getRowNode(this.rowIndex),rt=this.grid.scroller.findScrollTop(this.rowIndex);
if(n.offsetLeft+n.offsetWidth>sr.l+sr.w){
s.scrollLeft=n.offsetLeft+n.offsetWidth-sr.w;
}else{
if(n.offsetLeft<sr.l){
s.scrollLeft=n.offsetLeft;
}
}
if(rt+r.offsetHeight>sr.t+sr.h){
this.grid.setScrollTop(rt+r.offsetHeight-sr.h);
}else{
if(rt<sr.t){
this.grid.setScrollTop(rt);
}
}
},styleRow:function(_c){
return;
},setFocusIndex:function(_d,_e){
this.setFocusCell(this.grid.getCell(_e),_d);
},setFocusCell:function(_f,_10){
if(_f&&!this.isFocusCell(_f,_10)){
this.tabbingOut=false;
this.focusGridView();
this._focusifyCellNode(false);
this.cell=_f;
this.rowIndex=_10;
this._focusifyCellNode(true);
}
if(dojo.isOpera){
setTimeout(dojo.hitch(this.grid,"onCellFocus",this.cell,this.rowIndex),1);
}else{
this.grid.onCellFocus(this.cell,this.rowIndex);
}
},next:function(){
var row=this.rowIndex,col=this.cell.index+1,cc=this.grid.layout.cellCount-1,rc=this.grid.rowCount-1;
if(col>cc){
col=0;
row++;
}
if(row>rc){
col=cc;
row=rc;
}
this.setFocusIndex(row,col);
},previous:function(){
var row=(this.rowIndex||0),col=(this.cell.index||0)-1;
if(col<0){
col=this.grid.layout.cellCount-1;
row--;
}
if(row<0){
row=0;
col=0;
}
this.setFocusIndex(row,col);
},move:function(_17,_18){
var rc=this.grid.rowCount-1,cc=this.grid.layout.cellCount-1,r=this.rowIndex,i=this.cell.index,row=Math.min(rc,Math.max(0,r+_17)),col=Math.min(cc,Math.max(0,i+_18));
this.setFocusIndex(row,col);
if(_17){
this.grid.updateRow(r);
}
},previousKey:function(e){
if(this.isFirstFocusCell()){
this.tabOut(this.grid.domNode);
}else{
dojo.stopEvent(e);
this.previous();
}
},nextKey:function(e){
if(this.isLastFocusCell()){
this.tabOut(this.grid.lastFocusNode);
}else{
dojo.stopEvent(e);
this.next();
}
},tabOut:function(_21){
this.tabbingOut=true;
_21.focus();
},focusGridView:function(){
dojox.grid.fire(this.focusView,"focus");
},focusGrid:function(_22){
this.focusGridView();
this._focusifyCellNode(true);
},doFocus:function(e){
if(e&&e.target!=e.currentTarget){
return;
}
if(!this.tabbingOut&&this.isNoFocusCell()){
this.setFocusIndex(0,0);
}
this.tabbingOut=false;
}});
}
