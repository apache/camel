/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.analytics.Urchin"]){
dojo._hasResource["dojox.analytics.Urchin"]=true;
dojo.provide("dojox.analytics.Urchin");
dojo.declare("dojox.analytics.Urchin",null,{acct:dojo.config.urchin,loadInterval:42,decay:0.5,timeout:4200,constructor:function(_1){
this.tracker=null;
dojo.mixin(this,_1);
this._loadGA();
},_loadGA:function(){
var _2=("https:"==document.location.protocol)?"https://ssl.":"http://www.";
dojo.create("script",{src:_2+"google-analytics.com/ga.js"},dojo.doc.getElementsByTagName("head")[0]);
setTimeout(dojo.hitch(this,"_checkGA"),this.loadInterval);
},_checkGA:function(){
if(this.loadInterval>this.timeout){
return;
}
setTimeout(dojo.hitch(this,!window["_gat"]?"_checkGA":"_gotGA"),this.loadInterval);
this.loadInterval*=(this.decay+1);
},_gotGA:function(){
this.tracker=_gat._getTracker(this.acct);
this.tracker._initData();
this.GAonLoad.apply(this,arguments);
},GAonLoad:function(){
this.trackPageView();
},trackPageView:function(_3){
this.tracker._trackPageview.apply(this,arguments);
}});
}
