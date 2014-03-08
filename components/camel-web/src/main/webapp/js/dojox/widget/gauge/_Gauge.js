/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.gauge._Gauge"]){
dojo._hasResource["dojox.widget.gauge._Gauge"]=true;
dojo.provide("dojox.widget.gauge._Gauge");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._Container");
dojo.require("dijit._Contained");
dojo.require("dijit.Tooltip");
dojo.require("dojo.fx.easing");
dojo.require("dojox.gfx");
dojo.experimental("dojox.widget.gauge._Gauge");
dojo.declare("dojox.widget.gauge._Gauge",[dijit._Widget,dijit._Templated,dijit._Container],{width:0,height:0,background:null,min:0,max:0,image:null,useRangeStyles:0,useTooltip:true,majorTicks:null,minorTicks:null,_defaultIndicator:null,defaultColors:[[0,84,170,1],[68,119,187,1],[102,153,204,1],[153,187,238,1],[153,204,255,1],[204,238,255,1],[221,238,255,1]],min:null,max:null,surface:null,hideValues:false,gaugeContent:undefined,templateString:"<div>\n\t<div class=\"dojoxGaugeContent\" dojoAttachPoint=\"gaugeContent\"></div>\n\t<div dojoAttachPoint=\"containerNode\"></div>\n\t<div dojoAttachPoint=\"mouseNode\"></div>\n</div>\n",_backgroundDefault:{color:"#E0E0E0"},_rangeData:null,_indicatorData:null,_drag:null,_img:null,_overOverlay:false,_lastHover:"",startup:function(){
if(this.image===null){
this.image={};
}
this.connect(this.gaugeContent,"onmousemove",this.handleMouseMove);
this.connect(this.gaugeContent,"onmouseover",this.handleMouseOver);
this.connect(this.gaugeContent,"onmouseout",this.handleMouseOut);
this.connect(this.gaugeContent,"onmouseup",this.handleMouseUp);
if(!dojo.isArray(this.ranges)){
this.ranges=[];
}
if(!dojo.isArray(this.indicators)){
this.indicators=[];
}
var _1=[],_2=[];
var i;
if(this.hasChildren()){
var _4=this.getChildren();
for(i=0;i<_4.length;i++){
if(/dojox\.widget\..*Indicator/.test(_4[i].declaredClass)){
_2.push(_4[i]);
continue;
}
switch(_4[i].declaredClass){
case "dojox.widget.gauge.Range":
_1.push(_4[i]);
break;
}
}
this.ranges=this.ranges.concat(_1);
this.indicators=this.indicators.concat(_2);
}
if(!this.background){
this.background=this._backgroundDefault;
}
this.background=this.background.color||this.background;
if(!this.surface){
this.createSurface();
}
this.addRanges(this.ranges);
if(this.minorTicks&&this.minorTicks.interval){
this.setMinorTicks(this.minorTicks);
}
if(this.majorTicks&&this.majorTicks.interval){
this.setMajorTicks(this.majorTicks);
}
for(i=0;i<this.indicators.length;i++){
this.addIndicator(this.indicators[i]);
}
},_setTicks:function(_5,_6,_7){
var i;
if(_5&&dojo.isArray(_5._ticks)){
for(i=0;i<_5._ticks.length;i++){
this.removeIndicator(_5._ticks[i]);
}
}
var t={length:_6.length,offset:_6.offset,noChange:true};
if(_6.color){
t.color=_6.color;
}
if(_6.font){
t.font=_6.font;
}
_6._ticks=[];
for(i=this.min;i<=this.max;i+=_6.interval){
t.value=i;
if(_7){
t.label=""+i;
}
_6._ticks.push(this.addIndicator(t));
}
return _6;
},setMinorTicks:function(_a){
this.minorTicks=this._setTicks(this.minorTicks,_a,false);
},setMajorTicks:function(_b){
this.majorTicks=this._setTicks(this.majorTicks,_b,true);
},postCreate:function(){
if(this.hideValues){
dojo.style(this.containerNode,"display","none");
}
dojo.style(this.mouseNode,"width","0");
dojo.style(this.mouseNode,"height","0");
dojo.style(this.mouseNode,"position","absolute");
dojo.style(this.mouseNode,"z-index","100");
if(this.useTooltip){
dijit.showTooltip("test",this.mouseNode);
dijit.hideTooltip(this.mouseNode);
}
},createSurface:function(){
this.gaugeContent.style.width=this.width+"px";
this.gaugeContent.style.height=this.height+"px";
this.surface=dojox.gfx.createSurface(this.gaugeContent,this.width,this.height);
this._background=this.surface.createRect({x:0,y:0,width:this.width,height:this.height});
this._background.setFill(this.background);
if(this.image.url){
this._img=this.surface.createImage({width:this.image.width||this.width,height:this.image.height||this.height,src:this.image.url});
if(this.image.overlay){
this._img.getEventSource().setAttribute("overlay",true);
}
if(this.image.x||this.image.y){
this._img.setTransform({dx:this.image.x||0,dy:this.image.y||0});
}
}
},setBackground:function(_c){
if(!_c){
_c=this._backgroundDefault;
}
this.background=_c.color||_c;
this._background.setFill(this.background);
},addRange:function(_d){
this.addRanges([_d]);
},addRanges:function(_e){
if(!this._rangeData){
this._rangeData=[];
}
var _f;
for(var i=0;i<_e.length;i++){
_f=_e[i];
if((this.min===null)||(_f.low<this.min)){
this.min=_f.low;
}
if((this.max===null)||(_f.high>this.max)){
this.max=_f.high;
}
if(!_f.color){
var _11=this._rangeData.length%this.defaultColors.length;
if(dojox.gfx.svg&&this.useRangeStyles>0){
_11=(this._rangeData.length%this.useRangeStyles)+1;
_f.color={style:"dojoxGaugeRange"+_11};
}else{
_11=this._rangeData.length%this.defaultColors.length;
_f.color=this.defaultColors[_11];
}
}
this._rangeData[this._rangeData.length]=_f;
}
this.draw();
},addIndicator:function(_12){
_12._gauge=this;
if(!_12.declaredClass){
_12=new this._defaultIndicator(_12);
}
if(!_12.hideValue){
this.containerNode.appendChild(_12.domNode);
}
if(!this._indicatorData){
this._indicatorData=[];
}
this._indicatorData[this._indicatorData.length]=_12;
_12.draw();
return _12;
},removeIndicator:function(_13){
for(var i=0;i<this._indicatorData.length;i++){
if(this._indicatorData[i]===_13){
this._indicatorData.splice(i,1);
_13.remove();
break;
}
}
},moveIndicatorToFront:function(_15){
if(_15.shapes){
for(var i=0;i<_15.shapes.length;i++){
_15.shapes[i].moveToFront();
}
}
},drawText:function(txt,x,y,_1a,_1b,_1c,_1d){
var t=this.surface.createText({x:x,y:y,text:txt,align:_1a});
t.setFill(_1c);
t.setFont(_1d);
return t;
},removeText:function(t){
this.surface.rawNode.removeChild(t);
},updateTooltip:function(txt,e){
if(this._lastHover!=txt){
if(txt!==""){
dijit.hideTooltip(this.mouseNode);
dijit.showTooltip(txt,this.mouseNode);
}else{
dijit.hideTooltip(this.mouseNode);
}
this._lastHover=txt;
}
},handleMouseOver:function(_22){
var _23=_22.target.getAttribute("hover");
if(_22.target.getAttribute("overlay")){
this._overOverlay=true;
var r=this.getRangeUnderMouse(_22);
if(r&&r.hover){
_23=r.hover;
}
}
if(this.useTooltip&&!this._drag){
if(_23){
this.updateTooltip(_23,_22);
}else{
this.updateTooltip("",_22);
}
}
},handleMouseOut:function(_25){
if(_25.target.getAttribute("overlay")){
this._overOverlay=false;
}
if(this.useTooltip&&this.mouseNode){
dijit.hideTooltip(this.mouseNode);
}
},handleMouseDown:function(_26){
for(var i=0;i<this._indicatorData.length;i++){
var _28=this._indicatorData[i].shapes;
for(var s=0;s<_28.length;s++){
if(_28[s].getEventSource()==_26.target){
this._drag=this._indicatorData[i];
s=_28.length;
i=this._indicatorData.length;
}
}
}
dojo.stopEvent(_26);
},handleMouseUp:function(_2a){
this._drag=null;
dojo.stopEvent(_2a);
},handleMouseMove:function(_2b){
if(_2b){
dojo.style(this.mouseNode,"left",_2b.pageX+1+"px");
dojo.style(this.mouseNode,"top",_2b.pageY+1+"px");
}
if(this._drag){
this._dragIndicator(this,_2b);
}else{
if(this.useTooltip&&this._overOverlay){
var r=this.getRangeUnderMouse(_2b);
if(r&&r.hover){
this.updateTooltip(r.hover,_2b);
}else{
this.updateTooltip("",_2b);
}
}
}
}});
dojo.declare("dojox.widget.gauge.Range",[dijit._Widget,dijit._Contained],{low:0,high:0,hover:"",color:null,size:0,startup:function(){
this.color=this.color.color||this.color;
}});
dojo.declare("dojox.widget.gauge._Indicator",[dijit._Widget,dijit._Contained,dijit._Templated],{value:0,type:"",color:"black",label:"",font:{family:"sans-serif",size:"12px"},length:0,width:0,offset:0,hover:"",front:false,easing:dojo._defaultEasing,duration:1000,hideValue:false,noChange:false,_gauge:null,title:"",templateString:"<div class=\"dojoxGaugeIndicatorDiv\">\n\t<label class=\"dojoxGaugeIndicatorLabel\" for=\"${title}\">${title}:</label>\n\t<input class=\"dojoxGaugeIndicatorInput\" name=\"${title}\" size=\"5\" value=\"${value}\" dojoAttachPoint=\"valueNode\" dojoAttachEvent=\"onchange:_update\"></input>\n</div>\n",startup:function(){
if(this.onDragMove){
this.onDragMove=dojo.hitch(this.onDragMove);
}
},postCreate:function(){
if(this.title===""){
dojo.style(this.domNode,"display","none");
}
if(dojo.isString(this.easing)){
this.easing=dojo.getObject(this.easing);
}
},_update:function(_2d){
var _2e=this.valueNode.value;
if(_2e===""){
this.value=null;
}else{
this.value=Number(_2e);
this.hover=this.title+": "+_2e;
}
if(this._gauge){
this.draw();
this.valueNode.value=this.value;
if((this.title=="Target"||this.front)&&this._gauge.moveIndicator){
this._gauge.moveIndicatorToFront(this);
}
}
},update:function(_2f){
if(!this.noChange){
this.valueNode.value=_2f;
this._update();
}
},onDragMove:function(){
this.value=Math.floor(this.value);
this.valueNode.value=this.value;
this.hover=this.title+": "+this.value;
},draw:function(_30){
},remove:function(){
for(var i=0;i<this.shapes.length;i++){
this._gauge.surface.remove(this.shapes[i]);
}
if(this.text){
this._gauge.surface.remove(this.text);
}
}});
}
