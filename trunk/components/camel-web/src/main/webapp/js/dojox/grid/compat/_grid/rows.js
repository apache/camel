/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.rows"]){
dojo._hasResource["dojox.grid.compat._grid.rows"]=true;
dojo.provide("dojox.grid.compat._grid.rows");
dojo.declare("dojox.grid.rows",null,{constructor:function(_1){
this.grid=_1;
},linesToEms:2,defaultRowHeight:1,overRow:-2,getHeight:function(_2){
return "";
},getDefaultHeightPx:function(){
return 32;
},prepareStylingRow:function(_3,_4){
return {index:_3,node:_4,odd:Boolean(_3&1),selected:this.grid.selection.isSelected(_3),over:this.isOver(_3),customStyles:"",customClasses:"dojoxGrid-row"};
},styleRowNode:function(_5,_6){
var _7=this.prepareStylingRow(_5,_6);
this.grid.onStyleRow(_7);
this.applyStyles(_7);
},applyStyles:function(_8){
with(_8){
node.className=customClasses;
var h=node.style.height;
dojox.grid.setStyleText(node,customStyles+";"+(node._style||""));
node.style.height=h;
}
},updateStyles:function(_a){
this.grid.updateRowStyles(_a);
},setOverRow:function(_b){
var _c=this.overRow;
this.overRow=_b;
if((_c!=this.overRow)&&(_c>=0)){
this.updateStyles(_c);
}
this.updateStyles(this.overRow);
},isOver:function(_d){
return (this.overRow==_d);
}});
}
