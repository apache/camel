/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.plot2d.Base"]){
dojo._hasResource["dojox.charting.plot2d.Base"]=true;
dojo.provide("dojox.charting.plot2d.Base");
dojo.require("dojox.charting.scaler.primitive");
dojo.require("dojox.charting.Element");
dojo.require("dojox.charting.plot2d.common");
dojo.declare("dojox.charting.plot2d.Base",dojox.charting.Element,{destroy:function(){
this.resetEvents();
this.inherited(arguments);
},clear:function(){
this.series=[];
this._hAxis=null;
this._vAxis=null;
this.dirty=true;
return this;
},setAxis:function(_1){
if(_1){
this[_1.vertical?"_vAxis":"_hAxis"]=_1;
}
return this;
},addSeries:function(_2){
this.series.push(_2);
return this;
},calculateAxes:function(_3){
return this;
},isDirty:function(){
return this.dirty||this._hAxis&&this._hAxis.dirty||this._vAxis&&this._vAxis.dirty;
},render:function(_4,_5){
return this;
},getRequiredColors:function(){
return this.series.length;
},plotEvent:function(o){
},connect:function(_7,_8){
this.dirty=true;
return dojo.connect(this,"plotEvent",_7,_8);
},events:function(){
var ls=this.plotEvent._listeners;
if(!ls||!ls.length){
return false;
}
for(var i in ls){
if(!(i in Array.prototype)){
return true;
}
}
return false;
},resetEvents:function(){
this.plotEvent({type:"onplotreset",plot:this});
},_calc:function(_b,_c){
if(this._hAxis){
if(!this._hAxis.initialized()){
this._hAxis.calculate(_c.hmin,_c.hmax,_b.width);
}
this._hScaler=this._hAxis.getScaler();
}else{
this._hScaler=dojox.charting.scaler.primitive.buildScaler(_c.hmin,_c.hmax,_b.width);
}
if(this._vAxis){
if(!this._vAxis.initialized()){
this._vAxis.calculate(_c.vmin,_c.vmax,_b.height);
}
this._vScaler=this._vAxis.getScaler();
}else{
this._vScaler=dojox.charting.scaler.primitive.buildScaler(_c.vmin,_c.vmax,_b.height);
}
},_connectEvents:function(_d,o){
_d.connect("onmouseover",this,function(e){
o.type="onmouseover";
o.event=e;
this.plotEvent(o);
});
_d.connect("onmouseout",this,function(e){
o.type="onmouseout";
o.event=e;
this.plotEvent(o);
});
_d.connect("onclick",this,function(e){
o.type="onclick";
o.event=e;
this.plotEvent(o);
});
}});
}
