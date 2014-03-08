/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sketch.LeadAnnotation"]){
dojo._hasResource["dojox.sketch.LeadAnnotation"]=true;
dojo.provide("dojox.sketch.LeadAnnotation");
dojo.require("dojox.sketch.Annotation");
dojo.require("dojox.sketch.Anchor");
(function(){
var ta=dojox.sketch;
ta.LeadAnnotation=function(_2,id){
ta.Annotation.call(this,_2,id);
this.transform={dx:0,dy:0};
this.start={x:0,y:0};
this.control={x:100,y:-50};
this.end={x:200,y:0};
this.textPosition={x:0,y:0};
this.textOffset=4;
this.textAlign="middle";
this.textYOffset=10;
this.pathShape=null;
this.labelShape=null;
this.anchors.start=new ta.Anchor(this,"start");
this.anchors.control=new ta.Anchor(this,"control");
this.anchors.end=new ta.Anchor(this,"end");
};
ta.LeadAnnotation.prototype=new ta.Annotation;
var p=ta.LeadAnnotation.prototype;
p.constructor=ta.LeadAnnotation;
p.type=function(){
return "Lead";
};
p.getType=function(){
return ta.LeadAnnotation;
};
p._pos=function(){
var _5=this.textOffset,x=0,y=0;
var _8=this.calculate.slope(this.control,this.end);
if(Math.abs(_8)>=1){
x=this.end.x+this.calculate.dx(this.control,this.end,_5);
if(this.control.y>this.end.y){
y=this.end.y-_5;
}else{
y=this.end.y+_5+this.textYOffset;
}
}else{
if(_8==0){
x=this.end.x+_5;
y=this.end.y+this.textYOffset;
}else{
if(this.start.x>this.end.x){
x=this.end.x-_5;
this.textAlign="end";
}else{
x=this.end.x+_5;
this.textAlign="start";
}
if(this.start.y<this.end.y){
y=this.end.y+this.calculate.dy(this.control,this.end,_5)+this.textYOffset;
}else{
y=this.end.y+this.calculate.dy(this.control,this.end,-_5);
}
}
}
this.textPosition={x:x,y:y};
};
p.apply=function(_9){
if(!_9){
return;
}
if(_9.documentElement){
_9=_9.documentElement;
}
this.readCommonAttrs(_9);
for(var i=0;i<_9.childNodes.length;i++){
var c=_9.childNodes[i];
if(c.localName=="text"){
this.property("label",c.childNodes.length?c.childNodes[0].nodeValue:"");
}else{
if(c.localName=="path"){
var d=c.getAttribute("d").split(" ");
var s=d[0].split(",");
this.start.x=parseFloat(s[0].substr(1),10);
this.start.y=parseFloat(s[1],10);
s=d[1].split(",");
this.control.x=parseFloat(s[0].substr(1),10);
this.control.y=parseFloat(s[1],10);
s=d[2].split(",");
this.end.x=parseFloat(s[0],10);
this.end.y=parseFloat(s[1],10);
var _e=this.property("stroke");
var _f=c.getAttribute("style");
var m=_f.match(/stroke:([^;]+);/);
if(m){
_e.color=m[1];
this.property("fill",m[1]);
}
m=_f.match(/stroke-width:([^;]+);/);
if(m){
_e.width=m[1];
}
this.property("stroke",_e);
}
}
}
};
p.initialize=function(obj){
this.apply(obj);
this._pos();
this.shape=this.figure.group.createGroup();
this.shape.getEventSource().setAttribute("id",this.id);
this.pathShape=this.shape.createPath("M"+this.start.x+","+this.start.y+" Q"+this.control.x+","+this.control.y+" "+this.end.x+","+this.end.y+" l0,0");
this.labelShape=this.shape.createText({x:this.textPosition.x,y:this.textPosition.y,text:this.property("label"),align:this.textAlign});
this.labelShape.getEventSource().setAttribute("id",this.id+"-labelShape");
this.draw();
};
p.destroy=function(){
if(!this.shape){
return;
}
this.shape.remove(this.pathShape);
this.shape.remove(this.labelShape);
this.figure.group.remove(this.shape);
this.shape=this.pathShape=this.labelShape=null;
};
p.getBBox=function(){
var x=Math.min(this.start.x,this.control.x,this.end.x);
var y=Math.min(this.start.y,this.control.y,this.end.y);
var w=Math.max(this.start.x,this.control.x,this.end.x)-x;
var h=Math.max(this.start.y,this.control.y,this.end.y)-y;
return {x:x,y:y,width:w,height:h};
};
p.draw=function(obj){
this.apply(obj);
this._pos();
this.shape.setTransform(this.transform);
this.pathShape.setShape("M"+this.start.x+","+this.start.y+" Q"+this.control.x+","+this.control.y+" "+this.end.x+","+this.end.y+" l0,0");
this.labelShape.setShape({x:this.textPosition.x,y:this.textPosition.y,text:this.property("label")}).setFill(this.property("fill"));
this.zoom();
};
p.serialize=function(){
var _17=this.property("stroke");
return "<g "+this.writeCommonAttrs()+">"+"<path style=\"stroke:"+_17.color+";stroke-width:"+_17.width+";fill:none;\" d=\""+"M"+this.start.x+","+this.start.y+" "+"Q"+this.control.x+","+this.control.y+" "+this.end.x+","+this.end.y+"\" />"+"<text style=\"fill:"+_17.color+";text-anchor:"+this.textAlign+"\" font-weight=\"bold\" "+"x=\""+this.textPosition.x+"\" "+"y=\""+this.textPosition.y+"\">"+this.property("label")+"</text>"+"</g>";
};
ta.Annotation.register("Lead");
})();
}
