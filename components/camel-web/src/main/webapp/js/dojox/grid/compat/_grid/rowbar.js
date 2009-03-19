/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.rowbar"]){
dojo._hasResource["dojox.grid.compat._grid.rowbar"]=true;
dojo.provide("dojox.grid.compat._grid.rowbar");
dojo.require("dojox.grid.compat._grid.view");
dojo.declare("dojox.GridRowView",dojox.GridView,{defaultWidth:"3em",noscroll:true,padBorderWidth:2,buildRendering:function(){
this.inherited("buildRendering",arguments);
this.scrollboxNode.style.overflow="hidden";
this.headerNode.style.visibility="hidden";
},getWidth:function(){
return this.viewWidth||this.defaultWidth;
},buildRowContent:function(_1,_2){
var w=this.contentNode.offsetWidth-this.padBorderWidth;
_2.innerHTML="<table style=\"width:"+w+"px;\" role=\"wairole:presentation\"><tr><td class=\"dojoxGrid-rowbar-inner\"></td></tr></table>";
},renderHeader:function(){
},resize:function(){
this.adaptHeight();
},adaptWidth:function(){
},doStyleRowNode:function(_4,_5){
var n=["dojoxGrid-rowbar"];
if(this.grid.rows.isOver(_4)){
n.push("dojoxGrid-rowbar-over");
}
if(this.grid.selection.isSelected(_4)){
n.push("dojoxGrid-rowbar-selected");
}
_5.className=n.join(" ");
},domouseover:function(e){
this.grid.onMouseOverRow(e);
},domouseout:function(e){
if(!this.isIntraRowEvent(e)){
this.grid.onMouseOutRow(e);
}
}});
}
