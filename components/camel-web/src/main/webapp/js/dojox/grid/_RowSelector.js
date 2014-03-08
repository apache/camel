/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid._RowSelector"]){
dojo._hasResource["dojox.grid._RowSelector"]=true;
dojo.provide("dojox.grid._RowSelector");
dojo.require("dojox.grid._View");
dojo.declare("dojox.grid._RowSelector",dojox.grid._View,{defaultWidth:"2em",noscroll:true,padBorderWidth:2,buildRendering:function(){
this.inherited("buildRendering",arguments);
this.scrollboxNode.style.overflow="hidden";
this.headerNode.style.visibility="hidden";
},getWidth:function(){
return this.viewWidth||this.defaultWidth;
},buildRowContent:function(_1,_2){
var w=this.contentNode.offsetWidth-this.padBorderWidth;
_2.innerHTML="<table class=\"dojoxGridRowbarTable\" style=\"width:"+w+"px;\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" role=\""+(dojo.isFF<3?"wairole:":"")+"presentation\"><tr><td class=\"dojoxGridRowbarInner\">&nbsp;</td></tr></table>";
},renderHeader:function(){
},resize:function(){
this.adaptHeight();
},adaptWidth:function(){
},doStyleRowNode:function(_4,_5){
var n=["dojoxGridRowbar"];
if(this.grid.rows.isOver(_4)){
n.push("dojoxGridRowbarOver");
}
if(this.grid.selection.isSelected(_4)){
n.push("dojoxGridRowbarSelected");
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
