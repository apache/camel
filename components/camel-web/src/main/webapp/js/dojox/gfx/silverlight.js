/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.silverlight"]){
dojo._hasResource["dojox.gfx.silverlight"]=true;
dojo.provide("dojox.gfx.silverlight");
dojo.require("dojox.gfx._base");
dojo.require("dojox.gfx.shape");
dojo.require("dojox.gfx.path");
dojo.experimental("dojox.gfx.silverlight");
dojox.gfx.silverlight.dasharray={solid:"none",shortdash:[4,1],shortdot:[1,1],shortdashdot:[4,1,1,1],shortdashdotdot:[4,1,1,1,1,1],dot:[1,3],dash:[4,3],longdash:[8,3],dashdot:[4,3,1,3],longdashdot:[8,3,1,3],longdashdotdot:[8,3,1,3,1,3]};
dojox.gfx.silverlight.fontweight={normal:400,bold:700};
dojox.gfx.silverlight.caps={butt:"Flat",round:"Round",square:"Square"};
dojox.gfx.silverlight.joins={bevel:"Bevel",round:"Round"};
dojox.gfx.silverlight.fonts={serif:"Times New Roman",times:"Times New Roman","sans-serif":"Arial",helvetica:"Arial",monotone:"Courier New",courier:"Courier New"};
dojox.gfx.silverlight.hexColor=function(_1){
var c=dojox.gfx.normalizeColor(_1),t=c.toHex(),a=Math.round(c.a*255);
a=(a<0?0:a>255?255:a).toString(16);
return "#"+(a.length<2?"0"+a:a)+t.slice(1);
};
dojo.extend(dojox.gfx.Shape,{setFill:function(_5){
var p=this.rawNode.getHost().content,r=this.rawNode,f;
if(!_5){
this.fillStyle=null;
this._setFillAttr(null);
return this;
}
if(typeof (_5)=="object"&&"type" in _5){
switch(_5.type){
case "linear":
this.fillStyle=f=dojox.gfx.makeParameters(dojox.gfx.defaultLinearGradient,_5);
var _9=p.createFromXaml("<LinearGradientBrush/>");
_9.mappingMode="Absolute";
_9.startPoint=f.x1+","+f.y1;
_9.endPoint=f.x2+","+f.y2;
dojo.forEach(f.colors,function(c){
var t=p.createFromXaml("<GradientStop/>");
t.offset=c.offset;
t.color=dojox.gfx.silverlight.hexColor(c.color);
_9.gradientStops.add(t);
});
this._setFillAttr(_9);
break;
case "radial":
this.fillStyle=f=dojox.gfx.makeParameters(dojox.gfx.defaultRadialGradient,_5);
var _c=p.createFromXaml("<RadialGradientBrush/>"),c=dojox.gfx.matrix.multiplyPoint(dojox.gfx.matrix.invert(this._getAdjustedMatrix()),f.cx,f.cy),pt=c.x+","+c.y;
_c.mappingMode="Absolute";
_c.gradientOrigin=pt;
_c.center=pt;
_c.radiusX=_c.radiusY=f.r;
dojo.forEach(f.colors,function(c){
var t=p.createFromXaml("<GradientStop/>");
t.offset=c.offset;
t.color=dojox.gfx.silverlight.hexColor(c.color);
_c.gradientStops.add(t);
});
this._setFillAttr(_c);
break;
case "pattern":
this.fillStyle=null;
this._setFillAttr(null);
break;
}
return this;
}
this.fillStyle=f=dojox.gfx.normalizeColor(_5);
var scb=p.createFromXaml("<SolidColorBrush/>");
scb.color=f.toHex();
scb.opacity=f.a;
this._setFillAttr(scb);
return this;
},_setFillAttr:function(f){
this.rawNode.fill=f;
},setStroke:function(_13){
var p=this.rawNode.getHost().content,r=this.rawNode;
if(!_13){
this.strokeStyle=null;
r.stroke=null;
return this;
}
if(typeof _13=="string"||dojo.isArray(_13)||_13 instanceof dojo.Color){
_13={color:_13};
}
var s=this.strokeStyle=dojox.gfx.makeParameters(dojox.gfx.defaultStroke,_13);
s.color=dojox.gfx.normalizeColor(s.color);
if(s){
var scb=p.createFromXaml("<SolidColorBrush/>");
scb.color=s.color.toHex();
scb.opacity=s.color.a;
r.stroke=scb;
r.strokeThickness=s.width;
r.strokeStartLineCap=r.strokeEndLineCap=r.strokeDashCap=dojox.gfx.silverlight.caps[s.cap];
if(typeof s.join=="number"){
r.strokeLineJoin="Miter";
r.strokeMiterLimit=s.join;
}else{
r.strokeLineJoin=dojox.gfx.silverlight.joins[s.join];
}
var da=s.style.toLowerCase();
if(da in dojox.gfx.silverlight.dasharray){
da=dojox.gfx.silverlight.dasharray[da];
}
if(da instanceof Array){
da=dojo.clone(da);
if(s.cap!="butt"){
for(var i=0;i<da.length;i+=2){
--da[i];
if(da[i]<1){
da[i]=1;
}
}
for(var i=1;i<da.length;i+=2){
++da[i];
}
}
r.strokeDashArray=da.join(",");
}else{
r.strokeDashArray=null;
}
}
return this;
},_getParentSurface:function(){
var _1a=this.parent;
for(;_1a&&!(_1a instanceof dojox.gfx.Surface);_1a=_1a.parent){
}
return _1a;
},_applyTransform:function(){
var tm=this._getAdjustedMatrix(),r=this.rawNode;
if(tm){
var p=this.rawNode.getHost().content,m=p.createFromXaml("<MatrixTransform/>"),mm=p.createFromXaml("<Matrix/>");
mm.m11=tm.xx;
mm.m21=tm.xy;
mm.m12=tm.yx;
mm.m22=tm.yy;
mm.offsetX=tm.dx;
mm.offsetY=tm.dy;
m.matrix=mm;
r.renderTransform=m;
}else{
r.renderTransform=null;
}
return this;
},setRawNode:function(_20){
_20.fill=null;
_20.stroke=null;
this.rawNode=_20;
},_moveToFront:function(){
var c=this.parent.rawNode.children,r=this.rawNode;
c.remove(r);
c.add(r);
return this;
},_moveToBack:function(){
var c=this.parent.rawNode.children,r=this.rawNode;
c.remove(r);
c.insert(0,r);
return this;
},_getAdjustedMatrix:function(){
return this.matrix;
}});
dojo.declare("dojox.gfx.Group",dojox.gfx.Shape,{constructor:function(){
dojox.gfx.silverlight.Container._init.call(this);
},setRawNode:function(_25){
this.rawNode=_25;
}});
dojox.gfx.Group.nodeType="Canvas";
dojo.declare("dojox.gfx.Rect",dojox.gfx.shape.Rect,{setShape:function(_26){
this.shape=dojox.gfx.makeParameters(this.shape,_26);
this.bbox=null;
var r=this.rawNode,n=this.shape;
r.width=n.width;
r.height=n.height;
r.radiusX=r.radiusY=n.r;
return this._applyTransform();
},_getAdjustedMatrix:function(){
var m=this.matrix,s=this.shape,d={dx:s.x,dy:s.y};
return new dojox.gfx.Matrix2D(m?[m,d]:d);
}});
dojox.gfx.Rect.nodeType="Rectangle";
dojo.declare("dojox.gfx.Ellipse",dojox.gfx.shape.Ellipse,{setShape:function(_2c){
this.shape=dojox.gfx.makeParameters(this.shape,_2c);
this.bbox=null;
var r=this.rawNode,n=this.shape;
r.width=2*n.rx;
r.height=2*n.ry;
return this._applyTransform();
},_getAdjustedMatrix:function(){
var m=this.matrix,s=this.shape,d={dx:s.cx-s.rx,dy:s.cy-s.ry};
return new dojox.gfx.Matrix2D(m?[m,d]:d);
}});
dojox.gfx.Ellipse.nodeType="Ellipse";
dojo.declare("dojox.gfx.Circle",dojox.gfx.shape.Circle,{setShape:function(_32){
this.shape=dojox.gfx.makeParameters(this.shape,_32);
this.bbox=null;
var r=this.rawNode,n=this.shape;
r.width=r.height=2*n.r;
return this._applyTransform();
},_getAdjustedMatrix:function(){
var m=this.matrix,s=this.shape,d={dx:s.cx-s.r,dy:s.cy-s.r};
return new dojox.gfx.Matrix2D(m?[m,d]:d);
}});
dojox.gfx.Circle.nodeType="Ellipse";
dojo.declare("dojox.gfx.Line",dojox.gfx.shape.Line,{setShape:function(_38){
this.shape=dojox.gfx.makeParameters(this.shape,_38);
this.bbox=null;
var r=this.rawNode,n=this.shape;
r.x1=n.x1;
r.y1=n.y1;
r.x2=n.x2;
r.y2=n.y2;
return this;
}});
dojox.gfx.Line.nodeType="Line";
dojo.declare("dojox.gfx.Polyline",dojox.gfx.shape.Polyline,{setShape:function(_3b,_3c){
if(_3b&&_3b instanceof Array){
this.shape=dojox.gfx.makeParameters(this.shape,{points:_3b});
if(_3c&&this.shape.points.length){
this.shape.points.push(this.shape.points[0]);
}
}else{
this.shape=dojox.gfx.makeParameters(this.shape,_3b);
}
this.box=null;
var p=this.shape.points,rp=[];
for(var i=0;i<p.length;++i){
if(typeof p[i]=="number"){
rp.push(p[i],p[++i]);
}else{
rp.push(p[i].x,p[i].y);
}
}
this.rawNode.points=rp.join(",");
return this;
}});
dojox.gfx.Polyline.nodeType="Polyline";
dojo.declare("dojox.gfx.Image",dojox.gfx.shape.Image,{setShape:function(_40){
this.shape=dojox.gfx.makeParameters(this.shape,_40);
this.bbox=null;
var r=this.rawNode,n=this.shape;
r.width=n.width;
r.height=n.height;
r.source=n.src;
return this._applyTransform();
},_getAdjustedMatrix:function(){
var m=this.matrix,s=this.shape,d={dx:s.x,dy:s.y};
return new dojox.gfx.Matrix2D(m?[m,d]:d);
},setRawNode:function(_46){
this.rawNode=_46;
}});
dojox.gfx.Image.nodeType="Image";
dojo.declare("dojox.gfx.Text",dojox.gfx.shape.Text,{setShape:function(_47){
this.shape=dojox.gfx.makeParameters(this.shape,_47);
this.bbox=null;
var r=this.rawNode,s=this.shape;
r.text=s.text;
r.textDecorations=s.decoration==="underline"?"Underline":"None";
r["Canvas.Left"]=-10000;
r["Canvas.Top"]=-10000;
if(!this._delay){
this._delay=window.setTimeout(dojo.hitch(this,"_delayAlignment"),10);
}
return this;
},_delayAlignment:function(){
var r=this.rawNode,s=this.shape,w=r.actualWidth,h=r.actualHeight,x=s.x,y=s.y-h*0.75;
switch(s.align){
case "middle":
x-=w/2;
break;
case "end":
x-=w;
break;
}
this._delta={dx:x,dy:y};
r["Canvas.Left"]=0;
r["Canvas.Top"]=0;
this._applyTransform();
delete this._delay;
},_getAdjustedMatrix:function(){
var m=this.matrix,d=this._delta,x;
if(m){
x=d?[m,d]:m;
}else{
x=d?d:{};
}
return new dojox.gfx.Matrix2D(x);
},setStroke:function(){
return this;
},_setFillAttr:function(f){
this.rawNode.foreground=f;
},setRawNode:function(_54){
this.rawNode=_54;
},getTextWidth:function(){
return this.rawNode.actualWidth;
}});
dojox.gfx.Text.nodeType="TextBlock";
dojo.declare("dojox.gfx.Path",dojox.gfx.path.Path,{_updateWithSegment:function(_55){
dojox.gfx.Path.superclass._updateWithSegment.apply(this,arguments);
var p=this.shape.path;
if(typeof (p)=="string"){
this.rawNode.data=p?p:null;
}
},setShape:function(_57){
dojox.gfx.Path.superclass.setShape.apply(this,arguments);
var p=this.shape.path;
this.rawNode.data=p?p:null;
return this;
}});
dojox.gfx.Path.nodeType="Path";
dojo.declare("dojox.gfx.TextPath",dojox.gfx.path.TextPath,{_updateWithSegment:function(_59){
},setShape:function(_5a){
},_setText:function(){
}});
dojox.gfx.TextPath.nodeType="text";
dojox.gfx.silverlight.surfaces={};
dojox.gfx.silverlight.nullFunc=function(){
};
dojo.declare("dojox.gfx.Surface",dojox.gfx.shape.Surface,{constructor:function(){
dojox.gfx.silverlight.Container._init.call(this);
},destroy:function(){
window[this._onLoadName]=dojox.gfx.silverlight.nullFunc;
delete dojox.gfx.silverlight.surfaces[this.rawNode.name];
this.inherited(arguments);
},setDimensions:function(_5b,_5c){
this.width=dojox.gfx.normalizedLength(_5b);
this.height=dojox.gfx.normalizedLength(_5c);
var p=this.rawNode&&this.rawNode.getHost();
if(p){
p.width=_5b;
p.height=_5c;
}
return this;
},getDimensions:function(){
var p=this.rawNode&&this.rawNode.getHost();
var t=p?{width:p.content.actualWidth,height:p.content.actualHeight}:null;
if(t.width<=0){
t.width=this.width;
}
if(t.height<=0){
t.height=this.height;
}
return t;
}});
dojox.gfx.createSurface=function(_60,_61,_62){
var s=new dojox.gfx.Surface();
_60=dojo.byId(_60);
s._parent=_60;
var t=_60.ownerDocument.createElement("script");
t.type="text/xaml";
t.id=dojox.gfx._base._getUniqueId();
t.text="<?xml version='1.0'?><Canvas xmlns='http://schemas.microsoft.com/client/2007' Name='"+dojox.gfx._base._getUniqueId()+"'/>";
_60.parentNode.insertBefore(t,_60);
s._nodes.push(t);
var obj,_66=dojox.gfx._base._getUniqueId(),_67="__"+dojox.gfx._base._getUniqueId()+"_onLoad";
s._onLoadName=_67;
window[_67]=function(_68){

if(!s.rawNode){
s.rawNode=dojo.byId(_66).content.root;
dojox.gfx.silverlight.surfaces[s.rawNode.name]=_60;
s.onLoad(s);

}
};
if(dojo.isSafari){
obj="<embed type='application/x-silverlight' id='"+_66+"' width='"+_61+"' height='"+_62+" background='transparent'"+" source='#"+t.id+"'"+" windowless='true'"+" maxFramerate='60'"+" onLoad='"+_67+"'"+" onError='__dojoSilverlightError'"+" /><iframe style='visibility:hidden;height:0;width:0'/>";
}else{
obj="<object type='application/x-silverlight' data='data:application/x-silverlight,' id='"+_66+"' width='"+_61+"' height='"+_62+"'>"+"<param name='background' value='transparent' />"+"<param name='source' value='#"+t.id+"' />"+"<param name='windowless' value='true' />"+"<param name='maxFramerate' value='60' />"+"<param name='onLoad' value='"+_67+"' />"+"<param name='onError' value='__dojoSilverlightError' />"+"</object>";
}
_60.innerHTML=obj;
var _69=dojo.byId(_66);
if(_69.content&&_69.content.root){
s.rawNode=_69.content.root;
dojox.gfx.silverlight.surfaces[s.rawNode.name]=_60;
}else{
s.rawNode=null;
s.isLoaded=false;
}
s._nodes.push(_69);
s.width=dojox.gfx.normalizedLength(_61);
s.height=dojox.gfx.normalizedLength(_62);
return s;
};
__dojoSilverlightError=function(_6a,err){
var t="Silverlight Error:\n"+"Code: "+err.ErrorCode+"\n"+"Type: "+err.ErrorType+"\n"+"Message: "+err.ErrorMessage+"\n";
switch(err.ErrorType){
case "ParserError":
t+="XamlFile: "+err.xamlFile+"\n"+"Line: "+err.lineNumber+"\n"+"Position: "+err.charPosition+"\n";
break;
case "RuntimeError":
t+="MethodName: "+err.methodName+"\n";
if(err.lineNumber!=0){
t+="Line: "+err.lineNumber+"\n"+"Position: "+err.charPosition+"\n";
}
break;
}
console.error(t);
};
dojox.gfx.silverlight.Font={_setFont:function(){
var f=this.fontStyle,r=this.rawNode,fw=dojox.gfx.silverlight.fontweight,fo=dojox.gfx.silverlight.fonts,t=f.family.toLowerCase();
r.fontStyle=f.style=="italic"?"Italic":"Normal";
r.fontWeight=f.weight in fw?fw[f.weight]:f.weight;
r.fontSize=dojox.gfx.normalizedLength(f.size);
r.fontFamily=t in fo?fo[t]:f.family;
if(!this._delay){
this._delay=window.setTimeout(dojo.hitch(this,"_delayAlignment"),10);
}
}};
dojox.gfx.silverlight.Container={_init:function(){
dojox.gfx.shape.Container._init.call(this);
},add:function(_72){
if(this!=_72.getParent()){
dojox.gfx.shape.Container.add.apply(this,arguments);
this.rawNode.children.add(_72.rawNode);
}
return this;
},remove:function(_73,_74){
if(this==_73.getParent()){
var _75=_73.rawNode.getParent();
if(_75){
_75.children.remove(_73.rawNode);
}
dojox.gfx.shape.Container.remove.apply(this,arguments);
}
return this;
},clear:function(){
this.rawNode.children.clear();
return dojox.gfx.shape.Container.clear.apply(this,arguments);
},_moveChildToFront:dojox.gfx.shape.Container._moveChildToFront,_moveChildToBack:dojox.gfx.shape.Container._moveChildToBack};
dojo.mixin(dojox.gfx.shape.Creator,{createObject:function(_76,_77){
if(!this.rawNode){
return null;
}
var _78=new _76();
var _79=this.rawNode.getHost().content.createFromXaml("<"+_76.nodeType+"/>");
_78.setRawNode(_79);
_78.setShape(_77);
this.add(_78);
return _78;
}});
dojo.extend(dojox.gfx.Text,dojox.gfx.silverlight.Font);
dojo.extend(dojox.gfx.Group,dojox.gfx.silverlight.Container);
dojo.extend(dojox.gfx.Group,dojox.gfx.shape.Creator);
dojo.extend(dojox.gfx.Surface,dojox.gfx.silverlight.Container);
dojo.extend(dojox.gfx.Surface,dojox.gfx.shape.Creator);
(function(){
var _7a=dojox.gfx.silverlight.surfaces;
var _7b=function(s,a){
var ev={target:s,currentTarget:s,preventDefault:function(){
},stopPropagation:function(){
}};
if(a){
try{
ev.ctrlKey=a.ctrl;
ev.shiftKey=a.shift;
var p=a.getPosition(null);
ev.x=ev.offsetX=ev.layerX=p.x;
ev.y=ev.offsetY=ev.layerY=p.y;
var _80=_7a[s.getHost().content.root.name];
var t=dojo._abs(_80);
ev.clientX=t.x+p.x;
ev.clientY=t.y+p.y;
}
catch(e){
}
}
return ev;
};
var _82=function(s,a){
var ev={keyCode:a.platformKeyCode,ctrlKey:a.ctrl,shiftKey:a.shift};
return ev;
};
var _86={onclick:{name:"MouseLeftButtonUp",fix:_7b},onmouseenter:{name:"MouseEnter",fix:_7b},onmouseleave:{name:"MouseLeave",fix:_7b},onmouseover:{name:"MouseEnter",fix:_7b},onmouseout:{name:"MouseLeave",fix:_7b},onmousedown:{name:"MouseLeftButtonDown",fix:_7b},onmouseup:{name:"MouseLeftButtonUp",fix:_7b},onmousemove:{name:"MouseMove",fix:_7b},onkeydown:{name:"KeyDown",fix:_82},onkeyup:{name:"KeyUp",fix:_82}};
var _87={connect:function(_88,_89,_8a){
var _8b,n=_88 in _86?_86[_88]:{name:_88,fix:function(){
return {};
}};
if(arguments.length>2){
_8b=this.getEventSource().addEventListener(n.name,function(s,a){
dojo.hitch(_89,_8a)(n.fix(s,a));
});
}else{
_8b=this.getEventSource().addEventListener(n.name,function(s,a){
_89(n.fix(s,a));
});
}
return {name:n.name,token:_8b};
},disconnect:function(_91){
this.getEventSource().removeEventListener(_91.name,_91.token);
}};
dojo.extend(dojox.gfx.Shape,_87);
dojo.extend(dojox.gfx.Surface,_87);
dojox.gfx.equalSources=function(a,b){
return a&&b&&a.equals(b);
};
})();
}
