/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.widget.Legend"]){
dojo._hasResource["dojox.charting.widget.Legend"]=true;
dojo.provide("dojox.charting.widget.Legend");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojox.lang.functional.array");
dojo.require("dojox.lang.functional.fold");
dojo.declare("dojox.charting.widget.Legend",[dijit._Widget,dijit._Templated],{chartRef:"",horizontal:true,templateString:"<table dojoAttachPoint='legendNode' class='dojoxLegendNode'><tbody dojoAttachPoint='legendBody'></tbody></table>",legendNode:null,legendBody:null,postCreate:function(){
if(!this.chart){
if(!this.chartRef){
return;
}
this.chart=dijit.byId(this.chartRef);
if(!this.chart){
var _1=dojo.byId(this.chartRef);
if(_1){
this.chart=dijit.byNode(_1);
}else{

return;
}
}
this.series=this.chart.chart.series;
}else{
this.series=this.chart.series;
}
this.refresh();
},refresh:function(){
var df=dojox.lang.functional;
if(this._surfaces){
dojo.forEach(this._surfaces,function(_3){
_3.destroy();
});
}
this._surfaces=[];
while(this.legendBody.lastChild){
dojo.destroy(this.legendBody.lastChild);
}
if(this.horizontal){
dojo.addClass(this.legendNode,"dojoxLegendHorizontal");
this._tr=dojo.doc.createElement("tr");
this.legendBody.appendChild(this._tr);
}
var s=this.series;
if(s.length==0){
return;
}
if(s[0].chart.stack[0].declaredClass=="dojox.charting.plot2d.Pie"){
var t=s[0].chart.stack[0];
if(typeof t.run.data[0]=="number"){
var _6=df.map(t.run.data,"Math.max(x, 0)");
if(df.every(_6,"<= 0")){
return;
}
var _7=df.map(_6,"/this",df.foldl(_6,"+",0));
dojo.forEach(_7,function(x,i){
this._addLabel(t.dyn[i],t._getLabel(x*100)+"%");
},this);
}else{
dojo.forEach(t.run.data,function(x,i){
this._addLabel(t.dyn[i],x.legend||x.text||x.y);
},this);
}
}else{
dojo.forEach(s,function(x){
this._addLabel(x.dyn,x.legend||x.name);
},this);
}
},_addLabel:function(_d,_e){
var _f=dojo.doc.createElement("td"),_10=dojo.doc.createElement("td"),div=dojo.doc.createElement("div");
dojo.addClass(_f,"dojoxLegendIcon");
dojo.addClass(_10,"dojoxLegendText");
div.style.width="20px";
div.style.height="20px";
_f.appendChild(div);
if(this._tr){
this._tr.appendChild(_f);
this._tr.appendChild(_10);
}else{
var tr=dojo.doc.createElement("tr");
this.legendBody.appendChild(tr);
tr.appendChild(_f);
tr.appendChild(_10);
}
this._makeIcon(div,_d);
_10.innerHTML=String(_e);
},_makeIcon:function(div,dyn){
var mb={h:14,w:14};
var _16=dojox.gfx.createSurface(div,mb.w,mb.h);
this._surfaces.push(_16);
if(dyn.fill){
_16.createRect({x:2,y:2,width:mb.w-4,height:mb.h-4}).setFill(dyn.fill).setStroke(dyn.stroke);
}else{
if(dyn.stroke||dyn.marker){
var _17={x1:0,y1:mb.h/2,x2:mb.w,y2:mb.h/2};
if(dyn.stroke){
_16.createLine(_17).setStroke(dyn.stroke);
}
if(dyn.marker){
var c={x:mb.w/2,y:mb.h/2};
if(dyn.stroke){
_16.createPath({path:"M"+c.x+" "+c.y+" "+dyn.marker}).setFill(dyn.stroke.color).setStroke(dyn.stroke);
}else{
_16.createPath({path:"M"+c.x+" "+c.y+" "+dyn.marker}).setFill(dyn.color).setStroke(dyn.color);
}
}
}else{
_16.createRect({x:2,y:2,width:mb.w-4,height:mb.h-4}).setStroke("black");
_16.createLine({x1:2,y1:2,x2:mb.w-2,y2:mb.h-2}).setStroke("black");
_16.createLine({x1:2,y1:mb.h-2,x2:mb.w-2,y2:2}).setStroke("black");
}
}
}});
}
