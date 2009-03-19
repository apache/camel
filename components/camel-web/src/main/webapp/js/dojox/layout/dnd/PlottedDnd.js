/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.layout.dnd.PlottedDnd"]){
dojo._hasResource["dojox.layout.dnd.PlottedDnd"]=true;
dojo.provide("dojox.layout.dnd.PlottedDnd");
dojo.require("dojo.dnd.Source");
dojo.require("dojo.dnd.Manager");
dojo.require("dojox.layout.dnd.Avatar");
dojo.declare("dojox.layout.dnd.PlottedDnd",[dojo.dnd.Source],{GC_OFFSET_X:dojo.dnd.manager().OFFSET_X,GC_OFFSET_Y:dojo.dnd.manager().OFFSET_Y,constructor:function(_1,_2){
this.childBoxes=null;
this.dropIndicator=new dojox.layout.dnd.DropIndicator("dndDropIndicator","div");
this.withHandles=_2.withHandles;
this.handleClasses=_2.handleClasses;
this.opacity=_2.opacity;
this.allowAutoScroll=_2.allowAutoScroll;
this.dom=_2.dom;
this.singular=true;
this.skipForm=true;
this._over=false;
this.defaultHandleClass="GcDndHandle";
this.isDropped=false;
this._timer=null;
this.isOffset=(_2.isOffset)?true:false;
this.offsetDrag=(_2.offsetDrag)?_2.offsetDrag:{x:0,y:0};
this.hideSource=_2.hideSource?_2.hideSource:true;
this._drop=this.dropIndicator.create();
},_calculateCoords:function(_3){
dojo.forEach(this.node.childNodes,function(_4){
var c=dojo.coords(_4,true);
_4.coords={xy:c,w:_4.offsetWidth/2,h:_4.offsetHeight/2,mw:c.w};
if(_3){
_4.coords.mh=c.h;
}
},this);
},_legalMouseDown:function(e){
if(!this.withHandles){
return true;
}
for(var _7=(e.target);_7&&_7!=this.node;_7=_7.parentNode){
if(dojo.hasClass(_7,this.defaultHandleClass)){
return true;
}
}
return false;
},setDndItemSelectable:function(_8,_9){
for(var _a=_8;_a&&_8!=this.node;_a=_a.parentNode){
if(dojo.hasClass(_a,"dojoDndItem")){
dojo.setSelectable(_a,_9);
return;
}
}
},getDraggedWidget:function(_b){
var _c=_b;
while(_c&&_c.nodeName.toLowerCase()!="body"&&!dojo.hasClass(_c,"dojoDndItem")){
_c=_c.parentNode;
}
return (_c)?dijit.byNode(_c):null;
},isAccepted:function(_d){
var _e=(_d)?_d.getAttribute("dndtype"):null;
return (_e&&_e in this.accept);
},onDndStart:function(_f,_10,_11){
this.firstIndicator=(_f==this);
this._calculateCoords(true);
var m=dojo.dnd.manager();
if(_10[0].coords){
this._drop.style.height=_10[0].coords.mh+"px";
dojo.style(m.avatar.node,"width",_10[0].coords.mw+"px");
}else{
this._drop.style.height=m.avatar.node.clientHeight+"px";
}
this.dndNodes=_10;
dojox.layout.dnd.PlottedDnd.superclass.onDndStart.call(this,_f,_10,_11);
if(_f==this&&this.hideSource){
dojo.forEach(_10,function(n){
dojo.style(n,"display","none");
});
}
},onDndCancel:function(){
var m=dojo.dnd.manager();
if(m.source==this&&this.hideSource){
var _15=this.getSelectedNodes();
dojo.forEach(_15,function(n){
dojo.style(n,"display","");
});
}
dojox.layout.dnd.PlottedDnd.superclass.onDndCancel.call(this);
this.deleteDashedZone();
},onDndDrop:function(_17,_18,_19,_1a){
try{
if(!this.isAccepted(_18[0])){
this.onDndCancel();
}else{
if(_17==this&&this._over&&this.dropObject){
this.current=this.dropObject.c;
}
dojox.layout.dnd.PlottedDnd.superclass.onDndDrop.call(this,_17,_18,_19,_1a);
this._calculateCoords(true);
}
}
catch(e){
console.warn(e);
}
},onMouseDown:function(e){
if(this.current==null){
this.selection={};
}else{
if(this.current==this.anchor){
this.anchor=null;
}
}
if(this.current!==null){
var c=dojo.coords(this.current,true);
this.current.coords={xy:c,w:this.current.offsetWidth/2,h:this.current.offsetHeight/2,mh:c.h,mw:c.w};
this._drop.style.height=this.current.coords.mh+"px";
if(this.isOffset){
if(this.offsetDrag.x==0&&this.offsetDrag.y==0){
var _1d=true;
var _1e=dojo.coords(this._getChildByEvent(e));
this.offsetDrag.x=_1e.x-e.pageX;
this.offsetDrag.y=_1e.y-e.clientY;
}
if(this.offsetDrag.y<16&&this.current!=null){
this.offsetDrag.y=this.GC_OFFSET_Y;
}
var m=dojo.dnd.manager();
m.OFFSET_X=this.offsetDrag.x;
m.OFFSET_Y=this.offsetDrag.y;
if(_1d){
this.offsetDrag.x=0;
this.offsetDrag.y=0;
}
}
}
if(dojo.dnd.isFormElement(e)){
this.setDndItemSelectable(e.target,true);
}else{
this.containerSource=true;
var _20=this.getDraggedWidget(e.target);
if(_20&&_20.dragRestriction){
}else{
dojox.layout.dnd.PlottedDnd.superclass.onMouseDown.call(this,e);
}
}
},onMouseUp:function(e){
dojox.layout.dnd.PlottedDnd.superclass.onMouseUp.call(this,e);
this.containerSource=false;
if(!dojo.isIE&&this.mouseDown){
this.setDndItemSelectable(e.target,true);
}
var m=dojo.dnd.manager();
m.OFFSET_X=this.GC_OFFSET_X;
m.OFFSET_Y=this.GC_OFFSET_Y;
},onMouseMove:function(e){
var m=dojo.dnd.manager();
if(this.isDragging){
var _25=false;
if(this.current!=null||(this.current==null&&!this.dropObject)){
if(this.isAccepted(m.nodes[0])||this.containerSource){
_25=this.setIndicatorPosition(e);
}
}
if(this.current!=this.targetAnchor||_25!=this.before){
this._markTargetAnchor(_25);
m.canDrop(!this.current||m.source!=this||!(this.current.id in this.selection));
}
if(this.allowAutoScroll){
this._checkAutoScroll(e);
}
}else{
if(this.mouseDown&&this.isSource){
var _26=this.getSelectedNodes();
if(_26.length){
m.startDrag(this,_26,this.copyState(dojo.dnd.getCopyKeyState(e)));
}
}
if(this.allowAutoScroll){
this._stopAutoScroll();
}
}
},_markTargetAnchor:function(_27){
if(this.current==this.targetAnchor&&this.before==_27){
return;
}
this.targetAnchor=this.current;
this.targetBox=null;
this.before=_27;
},_unmarkTargetAnchor:function(){
if(!this.targetAnchor){
return;
}
this.targetAnchor=null;
this.targetBox=null;
this.before=true;
},setIndicatorPosition:function(e){
var _29=false;
if(this.current){
if(!this.current.coords||this.allowAutoScroll){
this.current.coords={xy:dojo.coords(this.current,true),w:this.current.offsetWidth/2,h:this.current.offsetHeight/2};
}
_29=this.horizontal?(e.pageX-this.current.coords.xy.x)<this.current.coords.w:(e.pageY-this.current.coords.xy.y)<this.current.coords.h;
this.insertDashedZone(_29);
}else{
if(!this.dropObject){
this.insertDashedZone(false);
}
}
return _29;
},onOverEvent:function(){
this._over=true;
dojox.layout.dnd.PlottedDnd.superclass.onOverEvent.call(this);
if(this.isDragging){
var m=dojo.dnd.manager();
if(!this.current&&!this.dropObject&&this.getSelectedNodes()[0]&&this.isAccepted(m.nodes[0])){
this.insertDashedZone(false);
}
}
},onOutEvent:function(){
this._over=false;
this.containerSource=false;
dojox.layout.dnd.PlottedDnd.superclass.onOutEvent.call(this);
if(this.dropObject){
this.deleteDashedZone();
}
},deleteDashedZone:function(){
this._drop.style.display="none";
var _2b=this._drop.nextSibling;
while(_2b!=null){
_2b.coords.xy.y-=parseInt(this._drop.style.height);
_2b=_2b.nextSibling;
}
delete this.dropObject;
},insertDashedZone:function(_2c){
if(this.dropObject){
if(_2c==this.dropObject.b&&((this.current&&this.dropObject.c==this.current.id)||(!this.current&&!this.dropObject.c))){
return;
}else{
this.deleteDashedZone();
}
}
this.dropObject={n:this._drop,c:this.current?this.current.id:null,b:_2c};
if(this.current){
dojo.place(this._drop,this.current,_2c?"before":"after");
if(!this.firstIndicator){
var _2d=this._drop.nextSibling;
while(_2d!=null){
_2d.coords.xy.y+=parseInt(this._drop.style.height);
_2d=_2d.nextSibling;
}
}else{
this.firstIndicator=false;
}
}else{
this.node.appendChild(this._drop);
}
this._drop.style.display="";
},insertNodes:function(_2e,_2f,_30,_31){
if(this.dropObject){
dojo.style(this.dropObject.n,"display","none");
dojox.layout.dnd.PlottedDnd.superclass.insertNodes.call(this,true,_2f,true,this.dropObject.n);
this.deleteDashedZone();
}else{
return dojox.layout.dnd.PlottedDnd.superclass.insertNodes.call(this,_2e,_2f,_30,_31);
}
var _32=dijit.byId(_2f[0].getAttribute("widgetId"));
if(_32){
dojox.layout.dnd._setGcDndHandle(_32,this.withHandles,this.handleClasses);
if(this.hideSource){
dojo.style(_32.domNode,"display","");
}
}
},_checkAutoScroll:function(e){
if(this._timer){
clearTimeout(this._timer);
}
this._stopAutoScroll();
var _34=this.dom,y=this._sumAncestorProperties(_34,"offsetTop");
if((e.pageY-_34.offsetTop+30)>_34.clientHeight){
autoScrollActive=true;
this._autoScrollDown(_34);
}else{
if((_34.scrollTop>0)&&(e.pageY-y)<30){
autoScrollActive=true;
this._autoScrollUp(_34);
}
}
},_autoScrollUp:function(_36){
if(autoScrollActive&&_36.scrollTop>0){
_36.scrollTop-=30;
this._timer=setTimeout(dojo.hitch(this,function(){
this._autoScrollUp(_36);
}),"100");
}
},_autoScrollDown:function(_37){
if(autoScrollActive&&(_37.scrollTop<(_37.scrollHeight-_37.clientHeight))){
_37.scrollTop+=30;
this._timer=setTimeout(dojo.hitch(this,function(){
this._autoScrollDown(_37);
}),"100");
}
},_stopAutoScroll:function(){
this.autoScrollActive=false;
},_sumAncestorProperties:function(_38,_39){
_38=dojo.byId(_38);
if(!_38){
return 0;
}
var _3a=0;
while(_38){
var val=_38[_39];
if(val){
_3a+=val-0;
if(_38==dojo.body()){
break;
}
}
_38=_38.parentNode;
}
return _3a;
}});
dojox.layout.dnd._setGcDndHandle=function(_3c,_3d,_3e,_3f){
var cls="GcDndHandle";
if(!_3f){
dojo.query(".GcDndHandle",_3c.domNode).removeClass(cls);
}
if(!_3d){
dojo.addClass(_3c.domNode,cls);
}else{
var _41=false;
for(var i=_3e.length-1;i>=0;i--){
var _43=dojo.query("."+_3e[i],_3c.domNode)[0];
if(_43){
_41=true;
if(_3e[i]!=cls){
var _44=dojo.query("."+cls,_3c.domNode);
if(_44.length==0){
dojo.removeClass(_3c.domNode,cls);
}else{
_44.removeClass(cls);
}
dojo.addClass(_43,cls);
}
}
}
if(!_41){
dojo.addClass(_3c.domNode,cls);
}
}
};
dojo.declare("dojox.layout.dnd.DropIndicator",null,{constructor:function(cn,tag){
this.tag=tag||"div";
this.style=cn||null;
},isInserted:function(){
return (this.node.parentNode&&this.node.parentNode.nodeType==1);
},create:function(){
if(this.node&&this.isInserted()){
return this.node;
}
var h="90px",el=dojo.doc.createElement(this.tag);
if(this.style){
el.className=this.style;
el.style.height=h;
}else{
dojo.style(el,{position:"relative",border:"1px dashed #F60",margin:"2px",height:h});
}
this.node=el;
return el;
},destroy:function(){
if(!this.node||!this.isInserted()){
return;
}
this.node.parentNode.removeChild(this.node);
this.node=null;
}});
dojo.extend(dojo.dnd.Manager,{canDrop:function(_49){
var _4a=this.target&&_49;
if(this.canDropFlag!=_4a){
this.canDropFlag=_4a;
if(this.avatar){
this.avatar.update();
}
}
},makeAvatar:function(){
return (this.source.declaredClass=="dojox.layout.dnd.PlottedDnd")?new dojox.layout.dnd.Avatar(this,this.source.opacity):new dojo.dnd.Avatar(this);
}});
if(dojo.isIE){
dojox.layout.dnd.handdleIE=[dojo.subscribe("/dnd/start",null,function(){
IEonselectstart=document.body.onselectstart;
document.body.onselectstart=function(e){
return false;
};
}),dojo.subscribe("/dnd/cancel",null,function(){
document.body.onselectstart=IEonselectstart;
}),dojo.subscribe("/dnd/drop",null,function(){
document.body.onselectstart=IEonselectstart;
})];
dojo.addOnWindowUnload(function(){
dojo.forEach(dojox.layout.dnd.handdleIE,dojo.unsubscribe);
});
}
}
