/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.Series"]){
dojo._hasResource["dojox.charting.Series"]=true;
dojo.provide("dojox.charting.Series");
dojo.require("dojox.charting.Element");
dojo.declare("dojox.charting.Series",dojox.charting.Element,{constructor:function(_1,_2,_3){
dojo.mixin(this,_3);
if(typeof this.plot!="string"){
this.plot="default";
}
this.data=_2;
this.dirty=true;
this.clear();
},clear:function(){
this.dyn={};
}});
}
