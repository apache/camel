/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.layout.SplitContainer"]){
dojo._hasResource["dijit.layout.SplitContainer"]=true;
dojo.provide("dijit.layout.SplitContainer");
dojo.require("dojo.cookie");
dojo.require("dijit.layout._LayoutWidget");
dojo.declare("dijit.layout.SplitContainer",dijit.layout._LayoutWidget,{constructor:function(){
dojo.deprecated("dijit.layout.SplitContainer is deprecated","use BorderContainer with splitter instead",2);
},activeSizing:false,sizerWidth:7,orientation:"horizontal",persist:true,baseClass:"dijitSplitContainer",postMixInProperties:function(){
this.inherited("postMixInProperties",arguments);
this.isHorizontal=(this.orientation=="horizontal");
},postCreate:function(){
this.inherited(arguments);
this.sizers=[];
if(dojo.isMozilla){
this.domNode.style.overflow="-moz-scrollbars-none";
}
if(typeof this.sizerWidth=="object"){
try{
this.sizerWidth=parseInt(this.sizerWidth.toString());
}
catch(e){
this.sizerWidth=7;
}
}
var _1=dojo.doc.createElement("div");
this.virtualSizer=_1;
_1.style.position="relative";
_1.style.zIndex=10;
_1.className=this.isHorizontal?"dijitSplitContainerVirtualSizerH":"dijitSplitContainerVirtualSizerV";
this.domNode.appendChild(_1);
dojo.setSelectable(_1,false);
},destroy:function(){
delete this.virtualSizer;
dojo.forEach(this._ownconnects,dojo.disconnect);
this.inherited(arguments);
},startup:function(){
if(this._started){
return;
}
dojo.forEach(this.getChildren(),function(_2,i,_4){
this._setupChild(_2);
if(i<_4.length-1){
this._addSizer();
}
},this);
if(this.persist){
this._restoreState();
}
this.inherited(arguments);
},_setupChild:function(_5){
this.inherited(arguments);
_5.domNode.style.position="absolute";
dojo.addClass(_5.domNode,"dijitSplitPane");
},_onSizerMouseDown:function(e){
if(e.target.id){
for(var i=0;i<this.sizers.length;i++){
if(this.sizers[i].id==e.target.id){
break;
}
}
if(i<this.sizers.length){
this.beginSizing(e,i);
}
}
},_addSizer:function(_8){
_8=_8===undefined?this.sizers.length:_8;
var _9=dojo.doc.createElement("div");
_9.id=dijit.getUniqueId("dijit_layout_SplitterContainer_Splitter");
this.sizers.splice(_8,0,_9);
this.domNode.appendChild(_9);
_9.className=this.isHorizontal?"dijitSplitContainerSizerH":"dijitSplitContainerSizerV";
var _a=dojo.doc.createElement("div");
_a.className="thumb";
_a.id=_9.id;
_9.appendChild(_a);
this.connect(_9,"onmousedown","_onSizerMouseDown");
dojo.setSelectable(_9,false);
},removeChild:function(_b){
if(this.sizers.length){
var i=dojo.indexOf(this.getChildren(),_b);
if(i!=-1){
if(i==this.sizers.length){
i--;
}
dojo.destroy(this.sizers[i]);
this.sizers.splice(i,1);
}
}
this.inherited(arguments);
if(this._started){
this.layout();
}
},addChild:function(_d,_e){
this.inherited(arguments);
if(this._started){
var _f=this.getChildren();
if(_f.length>1){
this._addSizer(_e);
}
this.layout();
}
},layout:function(){
this.paneWidth=this._contentBox.w;
this.paneHeight=this._contentBox.h;
var _10=this.getChildren();
if(!_10.length){
return;
}
var _11=this.isHorizontal?this.paneWidth:this.paneHeight;
if(_10.length>1){
_11-=this.sizerWidth*(_10.length-1);
}
var _12=0;
dojo.forEach(_10,function(_13){
_12+=_13.sizeShare;
});
var _14=_11/_12;
var _15=0;
dojo.forEach(_10.slice(0,_10.length-1),function(_16){
var _17=Math.round(_14*_16.sizeShare);
_16.sizeActual=_17;
_15+=_17;
});
_10[_10.length-1].sizeActual=_11-_15;
this._checkSizes();
var pos=0;
var _19=_10[0].sizeActual;
this._movePanel(_10[0],pos,_19);
_10[0].position=pos;
pos+=_19;
if(!this.sizers){
return;
}
dojo.some(_10.slice(1),function(_1a,i){
if(!this.sizers[i]){
return true;
}
this._moveSlider(this.sizers[i],pos,this.sizerWidth);
this.sizers[i].position=pos;
pos+=this.sizerWidth;
_19=_1a.sizeActual;
this._movePanel(_1a,pos,_19);
_1a.position=pos;
pos+=_19;
},this);
},_movePanel:function(_1c,pos,_1e){
if(this.isHorizontal){
_1c.domNode.style.left=pos+"px";
_1c.domNode.style.top=0;
var box={w:_1e,h:this.paneHeight};
if(_1c.resize){
_1c.resize(box);
}else{
dojo.marginBox(_1c.domNode,box);
}
}else{
_1c.domNode.style.left=0;
_1c.domNode.style.top=pos+"px";
var box={w:this.paneWidth,h:_1e};
if(_1c.resize){
_1c.resize(box);
}else{
dojo.marginBox(_1c.domNode,box);
}
}
},_moveSlider:function(_20,pos,_22){
if(this.isHorizontal){
_20.style.left=pos+"px";
_20.style.top=0;
dojo.marginBox(_20,{w:_22,h:this.paneHeight});
}else{
_20.style.left=0;
_20.style.top=pos+"px";
dojo.marginBox(_20,{w:this.paneWidth,h:_22});
}
},_growPane:function(_23,_24){
if(_23>0){
if(_24.sizeActual>_24.sizeMin){
if((_24.sizeActual-_24.sizeMin)>_23){
_24.sizeActual=_24.sizeActual-_23;
_23=0;
}else{
_23-=_24.sizeActual-_24.sizeMin;
_24.sizeActual=_24.sizeMin;
}
}
}
return _23;
},_checkSizes:function(){
var _25=0;
var _26=0;
var _27=this.getChildren();
dojo.forEach(_27,function(_28){
_26+=_28.sizeActual;
_25+=_28.sizeMin;
});
if(_25<=_26){
var _29=0;
dojo.forEach(_27,function(_2a){
if(_2a.sizeActual<_2a.sizeMin){
_29+=_2a.sizeMin-_2a.sizeActual;
_2a.sizeActual=_2a.sizeMin;
}
});
if(_29>0){
var _2b=this.isDraggingLeft?_27.reverse():_27;
dojo.forEach(_2b,function(_2c){
_29=this._growPane(_29,_2c);
},this);
}
}else{
dojo.forEach(_27,function(_2d){
_2d.sizeActual=Math.round(_26*(_2d.sizeMin/_25));
});
}
},beginSizing:function(e,i){
var _30=this.getChildren();
this.paneBefore=_30[i];
this.paneAfter=_30[i+1];
this.isSizing=true;
this.sizingSplitter=this.sizers[i];
if(!this.cover){
this.cover=dojo.create("div",{style:{position:"absolute",zIndex:5,top:0,left:0,width:"100%",height:"100%"}},this.domNode);
}else{
this.cover.style.zIndex=5;
}
this.sizingSplitter.style.zIndex=6;
this.originPos=dojo.coords(_30[0].domNode,true);
if(this.isHorizontal){
var _31=e.layerX||e.offsetX||0;
var _32=e.pageX;
this.originPos=this.originPos.x;
}else{
var _31=e.layerY||e.offsetY||0;
var _32=e.pageY;
this.originPos=this.originPos.y;
}
this.startPoint=this.lastPoint=_32;
this.screenToClientOffset=_32-_31;
this.dragOffset=this.lastPoint-this.paneBefore.sizeActual-this.originPos-this.paneBefore.position;
if(!this.activeSizing){
this._showSizingLine();
}
this._ownconnects=[];
this._ownconnects.push(dojo.connect(dojo.doc.documentElement,"onmousemove",this,"changeSizing"));
this._ownconnects.push(dojo.connect(dojo.doc.documentElement,"onmouseup",this,"endSizing"));
dojo.stopEvent(e);
},changeSizing:function(e){
if(!this.isSizing){
return;
}
this.lastPoint=this.isHorizontal?e.pageX:e.pageY;
this.movePoint();
if(this.activeSizing){
this._updateSize();
}else{
this._moveSizingLine();
}
dojo.stopEvent(e);
},endSizing:function(e){
if(!this.isSizing){
return;
}
if(this.cover){
this.cover.style.zIndex=-1;
}
if(!this.activeSizing){
this._hideSizingLine();
}
this._updateSize();
this.isSizing=false;
if(this.persist){
this._saveState(this);
}
dojo.forEach(this._ownconnects,dojo.disconnect);
},movePoint:function(){
var p=this.lastPoint-this.screenToClientOffset;
var a=p-this.dragOffset;
a=this.legaliseSplitPoint(a);
p=a+this.dragOffset;
this.lastPoint=p+this.screenToClientOffset;
},legaliseSplitPoint:function(a){
a+=this.sizingSplitter.position;
this.isDraggingLeft=!!(a>0);
if(!this.activeSizing){
var min=this.paneBefore.position+this.paneBefore.sizeMin;
if(a<min){
a=min;
}
var max=this.paneAfter.position+(this.paneAfter.sizeActual-(this.sizerWidth+this.paneAfter.sizeMin));
if(a>max){
a=max;
}
}
a-=this.sizingSplitter.position;
this._checkSizes();
return a;
},_updateSize:function(){
var pos=this.lastPoint-this.dragOffset-this.originPos;
var _3b=this.paneBefore.position;
var _3c=this.paneAfter.position+this.paneAfter.sizeActual;
this.paneBefore.sizeActual=pos-_3b;
this.paneAfter.position=pos+this.sizerWidth;
this.paneAfter.sizeActual=_3c-this.paneAfter.position;
dojo.forEach(this.getChildren(),function(_3d){
_3d.sizeShare=_3d.sizeActual;
});
if(this._started){
this.layout();
}
},_showSizingLine:function(){
this._moveSizingLine();
dojo.marginBox(this.virtualSizer,this.isHorizontal?{w:this.sizerWidth,h:this.paneHeight}:{w:this.paneWidth,h:this.sizerWidth});
this.virtualSizer.style.display="block";
},_hideSizingLine:function(){
this.virtualSizer.style.display="none";
},_moveSizingLine:function(){
var pos=(this.lastPoint-this.startPoint)+this.sizingSplitter.position;
dojo.style(this.virtualSizer,(this.isHorizontal?"left":"top"),pos+"px");
},_getCookieName:function(i){
return this.id+"_"+i;
},_restoreState:function(){
dojo.forEach(this.getChildren(),function(_40,i){
var _42=this._getCookieName(i);
var _43=dojo.cookie(_42);
if(_43){
var pos=parseInt(_43);
if(typeof pos=="number"){
_40.sizeShare=pos;
}
}
},this);
},_saveState:function(){
if(!this.persist){
return;
}
dojo.forEach(this.getChildren(),function(_45,i){
dojo.cookie(this._getCookieName(i),_45.sizeShare,{expires:365});
},this);
}});
dojo.extend(dijit._Widget,{sizeMin:10,sizeShare:10});
}
