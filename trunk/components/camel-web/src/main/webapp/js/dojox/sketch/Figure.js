/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sketch.Figure"]){
dojo._hasResource["dojox.sketch.Figure"]=true;
dojo.provide("dojox.sketch.Figure");
dojo.experimental("dojox.sketch");
dojo.require("dojox.gfx");
dojo.require("dojox.sketch.UndoStack");
(function(){
var ta=dojox.sketch;
ta.tools={};
ta.registerTool=function(_2,fn){
ta.tools[_2]=fn;
};
ta.Figure=function(_4){
var _5=this;
this.annCounter=1;
this.shapes=[];
this.image=null;
this.imageSrc=null;
this.size={w:0,h:0};
this.surface=null;
this.group=null;
this.node=null;
this.zoomFactor=1;
this.tools=null;
this.obj={};
dojo.mixin(this,_4);
this.selected=[];
this.hasSelections=function(){
return this.selected.length>0;
};
this.isSelected=function(_6){
for(var i=0;i<_5.selected.length;i++){
if(_5.selected[i]==_6){
return true;
}
}
return false;
};
this.select=function(_8){
if(!_5.isSelected(_8)){
_5.clearSelections();
_5.selected=[_8];
}
_8.setMode(ta.Annotation.Modes.View);
_8.setMode(ta.Annotation.Modes.Edit);
};
this.deselect=function(_9){
var _a=-1;
for(var i=0;i<_5.selected.length;i++){
if(_5.selected[i]==_9){
_a=i;
break;
}
}
if(_a>-1){
_9.setMode(ta.Annotation.Modes.View);
_5.selected.splice(_a,1);
}
return _9;
};
this.clearSelections=function(){
for(var i=0;i<_5.selected.length;i++){
_5.selected[i].setMode(ta.Annotation.Modes.View);
}
_5.selected=[];
};
this.replaceSelection=function(n,o){
if(!_5.isSelected(o)){
_5.select(n);
return;
}
var _f=-1;
for(var i=0;i<_5.selected.length;i++){
if(_5.selected[i]==o){
_f=i;
break;
}
}
if(_f>-1){
_5.selected.splice(_f,1,n);
}
};
this._c=null;
this._ctr=null;
this._lp=null;
this._action=null;
this._prevState=null;
this._startPoint=null;
this._ctool=null;
this._start=null;
this._end=null;
this._absEnd=null;
this._cshape=null;
this._dblclick=function(e){
var o=_5._fromEvt(e);
if(o){
_5.onDblClickShape(o,e);
}
};
this._keydown=function(e){
var _14=false;
if(e.ctrlKey){
if(e.keyCode===90){
_5.undo();
_14=true;
}else{
if(e.keyCode===89){
_5.redo();
_14=true;
}
}
}
if(e.keyCode===46||e.keyCode===8){
_5._delete(_5.selected);
_14=true;
}
if(_14){
dojo.stopEvent(e);
}
};
this._md=function(e){
var o=_5._fromEvt(e);
_5._startPoint={x:e.pageX,y:e.pageY};
_5._ctr=dojo._abs(_5.node);
_5._ctr={x:_5._ctr.x,y:_5._ctr.y};
var X=e.clientX-_5._ctr.x,Y=e.clientY-_5._ctr.y;
_5._lp={x:X,y:Y};
_5._start={x:X,y:Y};
_5._end={x:X,y:Y};
_5._absEnd={x:X,y:Y};
if(!o){
_5.clearSelections();
_5._ctool.onMouseDown(e);
}else{
if(o.type&&o.type()!="Anchor"){
if(!_5.isSelected(o)){
_5.select(o);
_5._sameShapeSelected=false;
}else{
_5._sameShapeSelected=true;
}
}
o.beginEdit();
_5._c=o;
}
};
this._mm=function(e){
if(!_5._ctr){
return;
}
var x=e.clientX-_5._ctr.x;
var y=e.clientY-_5._ctr.y;
var dx=x-_5._lp.x;
var dy=y-_5._lp.y;
_5._absEnd={x:x,y:y};
if(_5._c){
_5._c.setBinding({dx:dx/_5.zoomFactor,dy:dy/_5.zoomFactor});
_5._lp={x:x,y:y};
}else{
_5._end={x:dx,y:dy};
var _1e={x:Math.min(_5._start.x,_5._absEnd.x),y:Math.min(_5._start.y,_5._absEnd.y),width:Math.abs(_5._start.x-_5._absEnd.x),height:Math.abs(_5._start.y-_5._absEnd.y)};
if(_1e.width&&_1e.height){
_5._ctool.onMouseMove(e,_1e);
}
}
};
this._mu=function(e){
if(_5._c){
_5._c.endEdit();
}else{
_5._ctool.onMouseUp(e);
}
_5._c=_5._ctr=_5._lp=_5._action=_5._prevState=_5._startPoint=null;
_5._cshape=_5._start=_5._end=_5._absEnd=null;
};
this.initUndoStack();
};
var p=ta.Figure.prototype;
p.initUndoStack=function(){
this.history=new ta.UndoStack(this);
};
p.setTool=function(t){
this._ctool=t;
};
p._delete=function(arr,_23){
for(var i=0;i<arr.length;i++){
arr[i].setMode(ta.Annotation.Modes.View);
arr[i].destroy(_23);
this.remove(arr[i]);
this._remove(arr[i]);
if(!_23){
arr[i].onRemove();
}
}
arr.splice(0,arr.length);
};
p.onDblClickShape=function(_25,e){
if(_25["onDblClick"]){
_25.onDblClick(e);
}
};
p.onCreateShape=function(_27){
};
p.onBeforeCreateShape=function(_28){
};
p.initialize=function(_29){
this.node=_29;
this.surface=dojox.gfx.createSurface(_29,this.size.w,this.size.h);
this.group=this.surface.createGroup();
this._cons=[];
var es=this.surface.getEventSource();
this._cons.push(dojo.connect(es,"ondraggesture",dojo.stopEvent),dojo.connect(es,"ondragenter",dojo.stopEvent),dojo.connect(es,"ondragover",dojo.stopEvent),dojo.connect(es,"ondragexit",dojo.stopEvent),dojo.connect(es,"ondragstart",dojo.stopEvent),dojo.connect(es,"onselectstart",dojo.stopEvent),dojo.connect(es,"onmousedown",this._md),dojo.connect(es,"onmousemove",this._mm),dojo.connect(es,"onmouseup",this._mu),dojo.connect(es,"onclick",this,"onClick"),dojo.connect(es,"ondblclick",this._dblclick),dojo.connect(es.ownerDocument,"onkeydown",this._keydown));
this.image=this.group.createImage({width:this.size.w,height:this.size.h,src:this.imageSrc});
};
p.destroy=function(_2b){
if(!this.node){
return;
}
if(!_2b){
if(this.history){
this.history.destroy();
}
if(this._subscribed){
dojo.unsubscribe(this._subscribed);
delete this._subscribed;
}
}
dojo.forEach(this._cons,dojo.disconnect);
this._cons=[];
dojo.empty(this.node);
this.group=this.surface=null;
this.obj={};
this.shapes=[];
};
p.nextKey=function(){
return "annotation-"+this.annCounter++;
};
p.draw=function(){
};
p.zoom=function(pct){
this.zoomFactor=pct/100;
var w=this.size.w*this.zoomFactor;
var h=this.size.h*this.zoomFactor;
this.surface.setDimensions(w,h);
this.group.setTransform(dojox.gfx.matrix.scale(this.zoomFactor,this.zoomFactor));
for(var i=0;i<this.shapes.length;i++){
this.shapes[i].zoom(this.zoomFactor);
}
};
p.getFit=function(){
var wF=(this.node.parentNode.clientWidth-5)/this.size.w;
var hF=(this.node.parentNode.clientHeight-5)/this.size.h;
return Math.min(wF,hF)*100;
};
p.unzoom=function(){
this.zoomFactor=1;
this.surface.setDimensions(this.size.w,this.size.h);
this.group.setTransform();
};
p._add=function(obj){
this.obj[obj._key]=obj;
};
p._remove=function(obj){
if(this.obj[obj._key]){
delete this.obj[obj._key];
}
};
p._get=function(key){
if(key&&key.indexOf("bounding")>-1){
key=key.replace("-boundingBox","");
}else{
if(key&&key.indexOf("-labelShape")>-1){
key=key.replace("-labelShape","");
}
}
return this.obj[key];
};
p._keyFromEvt=function(e){
var key=e.target.id+"";
if(key.length==0){
var p=e.target.parentNode;
var _38=this.surface.getEventSource();
while(p&&p.id.length==0&&p!=_38){
p=p.parentNode;
}
key=p.id;
}
return key;
};
p._fromEvt=function(e){
return this._get(this._keyFromEvt(e));
};
p.add=function(_3a){
for(var i=0;i<this.shapes.length;i++){
if(this.shapes[i]==_3a){
return true;
}
}
this.shapes.push(_3a);
return true;
};
p.remove=function(_3c){
var idx=-1;
for(var i=0;i<this.shapes.length;i++){
if(this.shapes[i]==_3c){
idx=i;
break;
}
}
if(idx>-1){
this.shapes.splice(idx,1);
}
return _3c;
};
p.get=function(id){
for(var i=0;i<this.shapes.length;i++){
if(this.shapes[i].id==id){
return this.shapes[i];
}
}
return null;
};
p.convert=function(ann,t){
var _43=t+"Annotation";
if(!ta[_43]){
return;
}
var _44=ann.type(),id=ann.id,_46=ann.label,_47=ann.mode,_48=ann.tokenId;
var _49,end,_4b,_4c;
switch(_44){
case "Preexisting":
case "Lead":
_4c={dx:ann.transform.dx,dy:ann.transform.dy};
_49={x:ann.start.x,y:ann.start.y};
end={x:ann.end.x,y:ann.end.y};
var cx=end.x-((end.x-_49.x)/2);
var cy=end.y-((end.y-_49.y)/2);
_4b={x:cx,y:cy};
break;
case "SingleArrow":
case "DoubleArrow":
_4c={dx:ann.transform.dx,dy:ann.transform.dy};
_49={x:ann.start.x,y:ann.start.y};
end={x:ann.end.x,y:ann.end.y};
_4b={x:ann.control.x,y:ann.control.y};
break;
case "Underline":
_4c={dx:ann.transform.dx,dy:ann.transform.dy};
_49={x:ann.start.x,y:ann.start.y};
_4b={x:_49.x+50,y:_49.y+50};
end={x:_49.x+100,y:_49.y+100};
break;
case "Brace":
}
var n=new ta[_43](this,id);
if(n.type()=="Underline"){
n.transform={dx:_4c.dx+_49.x,dy:_4c.dy+_49.y};
}else{
if(n.transform){
n.transform=_4c;
}
if(n.start){
n.start=_49;
}
}
if(n.end){
n.end=end;
}
if(n.control){
n.control=_4b;
}
n.label=_46;
n.token=dojo.lang.shallowCopy(ann.token);
n.initialize();
this.replaceSelection(n,ann);
this._remove(ann);
this.remove(ann);
ann.destroy();
n.setMode(_47);
};
p.setValue=function(_50){
var obj=dojox.xml.DomParser.parse(_50);
var _52=this.node;
this.load(obj,_52);
this.zoom(this.zoomFactor*100);
};
p.load=function(obj,n){
if(this.surface){
this.destroy(true);
}
var _55=obj.documentElement;
this.size={w:parseFloat(_55.getAttribute("width"),10),h:parseFloat(_55.getAttribute("height"),10)};
var g=_55.childrenByName("g")[0];
var img=g.childrenByName("image")[0];
this.imageSrc=img.getAttribute("xlink:href");
this.initialize(n);
var ann=g.childrenByName("g");
for(var i=0;i<ann.length;i++){
this._loadAnnotation(ann[i]);
}
if(this._loadDeferred){
this._loadDeferred.callback(this);
this._loadDeferred=null;
}
this.onLoad();
};
p.onLoad=function(){
};
p.onClick=function(){
};
p._loadAnnotation=function(obj){
var _5b=obj.getAttribute("dojoxsketch:type")+"Annotation";
if(ta[_5b]){
var a=new ta[_5b](this,obj.id);
a.initialize(obj);
this.nextKey();
a.setMode(ta.Annotation.Modes.View);
this._add(a);
return a;
}
return null;
};
p.onUndo=function(){
};
p.onBeforeUndo=function(){
};
p.onRedo=function(){
};
p.onBeforeRedo=function(){
};
p.undo=function(){
if(this.history){
this.onBeforeUndo();
this.history.undo();
this.onUndo();
}
};
p.redo=function(){
if(this.history){
this.onBeforeRedo();
this.history.redo();
this.onRedo();
}
};
p.serialize=function(){
var s="<svg xmlns=\"http://www.w3.org/2000/svg\" "+"xmlns:xlink=\"http://www.w3.org/1999/xlink\" "+"xmlns:dojoxsketch=\"http://dojotoolkit.org/dojox/sketch\" "+"width=\""+this.size.w+"\" height=\""+this.size.h+"\">"+"<g>"+"<image xlink:href=\""+this.imageSrc+"\" x=\"0\" y=\"0\" width=\""+this.size.w+"\" height=\""+this.size.h+"\" />";
for(var i=0;i<this.shapes.length;i++){
s+=this.shapes[i].serialize();
}
s+="</g></svg>";
return s;
};
p.getValue=p.serialize;
})();
}
