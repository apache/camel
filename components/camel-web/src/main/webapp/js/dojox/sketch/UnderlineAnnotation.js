/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sketch.UnderlineAnnotation"]){
dojo._hasResource["dojox.sketch.UnderlineAnnotation"]=true;
dojo.provide("dojox.sketch.UnderlineAnnotation");
dojo.require("dojox.sketch.Annotation");
dojo.require("dojox.sketch.Anchor");
(function(){
var ta=dojox.sketch;
ta.UnderlineAnnotation=function(_2,id){
ta.Annotation.call(this,_2,id);
this.transform={dx:0,dy:0};
this.start={x:0,y:0};
this.property("label","#");
this.labelShape=null;
this.lineShape=null;
};
ta.UnderlineAnnotation.prototype=new ta.Annotation;
var p=ta.UnderlineAnnotation.prototype;
p.constructor=ta.UnderlineAnnotation;
p.type=function(){
return "Underline";
};
p.getType=function(){
return ta.UnderlineAnnotation;
};
p.apply=function(_5){
if(!_5){
return;
}
if(_5.documentElement){
_5=_5.documentElement;
}
this.readCommonAttrs(_5);
for(var i=0;i<_5.childNodes.length;i++){
var c=_5.childNodes[i];
if(c.localName=="text"){
this.property("label",c.childNodes[0].nodeValue);
var _8=c.getAttribute("style");
var m=_8.match(/fill:([^;]+);/);
if(m){
var _a=this.property("stroke");
_a.collor=m[1];
this.property("stroke",_a);
this.property("fill",_a.collor);
}
}
}
};
p.initialize=function(_b){
this.apply(_b);
this.shape=this.figure.group.createGroup();
this.shape.getEventSource().setAttribute("id",this.id);
this.labelShape=this.shape.createText({x:0,y:0,text:this.property("label"),decoration:"underline",align:"start"});
this.labelShape.getEventSource().setAttribute("id",this.id+"-labelShape");
this.lineShape=this.shape.createLine({x1:1,x2:this.labelShape.getTextWidth(),y1:2,y2:2});
this.lineShape.getEventSource().setAttribute("shape-rendering","crispEdges");
this.draw();
};
p.destroy=function(){
if(!this.shape){
return;
}
this.shape.remove(this.labelShape);
this.shape.remove(this.lineShape);
this.figure.group.remove(this.shape);
this.shape=this.lineShape=this.labelShape=null;
};
p.getBBox=function(){
var b=this.getTextBox();
var z=this.figure.zoomFactor;
return {x:0,y:(b.h*-1+4)/z,width:(b.w+2)/z,height:b.h/z};
};
p.draw=function(_e){
this.apply(_e);
this.shape.setTransform(this.transform);
this.labelShape.setShape({x:0,y:0,text:this.property("label")}).setFill(this.property("fill"));
this.zoom();
};
p.zoom=function(_f){
if(this.labelShape){
_f=_f||this.figure.zoomFactor;
var _10=dojox.gfx.renderer=="vml"?0:2/_f;
ta.Annotation.prototype.zoom.call(this,_f);
_f=dojox.gfx.renderer=="vml"?1:_f;
this.lineShape.setShape({x1:0,x2:this.getBBox().width-_10,y1:2,y2:2}).setStroke({color:this.property("fill"),width:1/_f});
if(this.mode==ta.Annotation.Modes.Edit){
this.drawBBox();
}
}
};
p.serialize=function(){
var s=this.property("stroke");
return "<g "+this.writeCommonAttrs()+">"+"<text style=\"fill:"+this.property("fill")+";\" font-weight=\"bold\" text-decoration=\"underline\" "+"x=\"0\" y=\"0\">"+this.property("label")+"</text>"+"</g>";
};
ta.Annotation.register("Underline");
})();
}
