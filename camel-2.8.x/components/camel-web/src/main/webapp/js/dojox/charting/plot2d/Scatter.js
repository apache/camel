/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Scatter"]){
dojo._hasResource["dojox.charting.plot2d.Scatter"]=true;
dojo.provide("dojox.charting.plot2d.Scatter");
dojo.require("dojox.charting.plot2d.Default");
dojo.declare("dojox.charting.plot2d.Scatter",dojox.charting.plot2d.Default,{constructor:function(){
this.opt.lines=false;
this.opt.markers=true;
}});
}
