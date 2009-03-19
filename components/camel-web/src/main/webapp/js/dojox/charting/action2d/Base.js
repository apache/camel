/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.action2d.Base"]){
dojo._hasResource["dojox.charting.action2d.Base"]=true;
dojo.provide("dojox.charting.action2d.Base");
dojo.require("dojo.fx.easing");
dojo.require("dojox.lang.functional.object");
dojo.require("dojox.gfx.fx");
(function(){
var _1=400,_2=dojo.fx.easing.backOut,df=dojox.lang.functional;
dojo.declare("dojox.charting.action2d.Base",null,{overOutEvents:{onmouseover:1,onmouseout:1},constructor:function(_4,_5,_6){
this.chart=_4;
this.plot=_5?_5:"default";
this.anim={};
if(!_6){
_6={};
}
this.duration=_6.duration?_6.duration:_1;
this.easing=_6.easing?_6.easing:_2;
},connect:function(){
this.handle=this.chart.connectToPlot(this.plot,this,"process");
},disconnect:function(){
if(this.handle){
dojo.disconnect(this.handle);
this.handle=null;
}
},reset:function(){
},destroy:function(){
if(this.handle){
this.disconnect();
}
df.forIn(this.anim,function(o){
df.forIn(o,function(_8){
_8.action.stop(true);
});
});
this.anim={};
}});
})();
}
