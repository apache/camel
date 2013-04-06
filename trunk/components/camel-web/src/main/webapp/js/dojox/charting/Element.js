/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.Element"]){
dojo._hasResource["dojox.charting.Element"]=true;
dojo.provide("dojox.charting.Element");
dojo.declare("dojox.charting.Element",null,{constructor:function(_1){
this.chart=_1;
this.group=null;
this.htmlElements=[];
this.dirty=true;
},createGroup:function(_2){
if(!_2){
_2=this.chart.surface;
}
if(!this.group){
this.group=_2.createGroup();
}
return this;
},purgeGroup:function(){
this.destroyHtmlElements();
if(this.group){
this.group.clear();
this.group.removeShape();
this.group=null;
}
this.dirty=true;
return this;
},cleanGroup:function(_3){
this.destroyHtmlElements();
if(!_3){
_3=this.chart.surface;
}
if(this.group){
this.group.clear();
}else{
this.group=_3.createGroup();
}
this.dirty=true;
return this;
},destroyHtmlElements:function(){
if(this.htmlElements.length){
dojo.forEach(this.htmlElements,dojo.destroy);
this.htmlElements=[];
}
},destroy:function(){
this.purgeGroup();
}});
}
