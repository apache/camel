/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.layout.BorderContainer"]){
dojo._hasResource["dijit.layout.BorderContainer"]=true;
dojo.provide("dijit.layout.BorderContainer");
dojo.require("dijit.layout._LayoutWidget");
dojo.require("dojo.cookie");
dojo.declare("dijit.layout.BorderContainer",dijit.layout._LayoutWidget,{design:"headline",gutters:true,liveSplitters:true,persist:false,baseClass:"dijitBorderContainer",_splitterClass:"dijit.layout._Splitter",postMixInProperties:function(){
if(!this.gutters){
this.baseClass+="NoGutter";
}
this.inherited(arguments);
},postCreate:function(){
this.inherited(arguments);
this._splitters={};
this._splitterThickness={};
},startup:function(){
if(this._started){
return;
}
dojo.forEach(this.getChildren(),this._setupChild,this);
this.inherited(arguments);
},_setupChild:function(_1){
var _2=_1.region;
if(_2){
this.inherited(arguments);
dojo.addClass(_1.domNode,this.baseClass+"Pane");
var _3=this.isLeftToRight();
if(_2=="leading"){
_2=_3?"left":"right";
}
if(_2=="trailing"){
_2=_3?"right":"left";
}
this["_"+_2]=_1.domNode;
this["_"+_2+"Widget"]=_1;
if((_1.splitter||this.gutters)&&!this._splitters[_2]){
var _4=dojo.getObject(_1.splitter?this._splitterClass:"dijit.layout._Gutter");
var _5={left:"right",right:"left",top:"bottom",bottom:"top",leading:"trailing",trailing:"leading"};
var _6=new _4({container:this,child:_1,region:_2,oppNode:this["_"+_5[_1.region]],live:this.liveSplitters});
_6.isSplitter=true;
this._splitters[_2]=_6.domNode;
dojo.place(this._splitters[_2],_1.domNode,"after");
_6.startup();
}
_1.region=_2;
}
},_computeSplitterThickness:function(_7){
this._splitterThickness[_7]=this._splitterThickness[_7]||dojo.marginBox(this._splitters[_7])[(/top|bottom/.test(_7)?"h":"w")];
},layout:function(){
for(var _8 in this._splitters){
this._computeSplitterThickness(_8);
}
this._layoutChildren();
},addChild:function(_9,_a){
this.inherited(arguments);
if(this._started){
this._layoutChildren();
}
},removeChild:function(_b){
var _c=_b.region;
var _d=this._splitters[_c];
if(_d){
dijit.byNode(_d).destroy();
delete this._splitters[_c];
delete this._splitterThickness[_c];
}
this.inherited(arguments);
delete this["_"+_c];
delete this["_"+_c+"Widget"];
if(this._started){
this._layoutChildren(_b.region);
}
dojo.removeClass(_b.domNode,this.baseClass+"Pane");
},getChildren:function(){
return dojo.filter(this.inherited(arguments),function(_e){
return !_e.isSplitter;
});
},getSplitter:function(_f){
var _10=this._splitters[_f];
return _10?dijit.byNode(_10):null;
},resize:function(_11,_12){
if(!this.cs||!this.pe){
var _13=this.domNode;
this.cs=dojo.getComputedStyle(_13);
this.pe=dojo._getPadExtents(_13,this.cs);
this.pe.r=dojo._toPixelValue(_13,this.cs.paddingRight);
this.pe.b=dojo._toPixelValue(_13,this.cs.paddingBottom);
dojo.style(_13,"padding","0px");
}
this.inherited(arguments);
},_layoutChildren:function(_14){
if(!this._borderBox||!this._borderBox.h){
return;
}
var _15=(this.design=="sidebar");
var _16=0,_17=0,_18=0,_19=0;
var _1a={},_1b={},_1c={},_1d={},_1e=(this._center&&this._center.style)||{};
var _1f=/left|right/.test(_14);
var _20=!_14||(!_1f&&!_15);
var _21=!_14||(_1f&&_15);
if(this._top){
_1a=_21&&this._top.style;
_16=dojo.marginBox(this._top).h;
}
if(this._left){
_1b=_20&&this._left.style;
_18=dojo.marginBox(this._left).w;
}
if(this._right){
_1c=_20&&this._right.style;
_19=dojo.marginBox(this._right).w;
}
if(this._bottom){
_1d=_21&&this._bottom.style;
_17=dojo.marginBox(this._bottom).h;
}
var _22=this._splitters;
var _23=_22.top,_24=_22.bottom,_25=_22.left,_26=_22.right;
var _27=this._splitterThickness;
var _28=_27.top||0,_29=_27.left||0,_2a=_27.right||0,_2b=_27.bottom||0;
if(_29>50||_2a>50){
setTimeout(dojo.hitch(this,function(){
this._splitterThickness={};
for(var _2c in this._splitters){
this._computeSplitterThickness(_2c);
}
this._layoutChildren();
}),50);
return false;
}
var pe=this.pe;
var _2e={left:(_15?_18+_29:0)+pe.l+"px",right:(_15?_19+_2a:0)+pe.r+"px"};
if(_23){
dojo.mixin(_23.style,_2e);
_23.style.top=_16+pe.t+"px";
}
if(_24){
dojo.mixin(_24.style,_2e);
_24.style.bottom=_17+pe.b+"px";
}
_2e={top:(_15?0:_16+_28)+pe.t+"px",bottom:(_15?0:_17+_2b)+pe.b+"px"};
if(_25){
dojo.mixin(_25.style,_2e);
_25.style.left=_18+pe.l+"px";
}
if(_26){
dojo.mixin(_26.style,_2e);
_26.style.right=_19+pe.r+"px";
}
dojo.mixin(_1e,{top:pe.t+_16+_28+"px",left:pe.l+_18+_29+"px",right:pe.r+_19+_2a+"px",bottom:pe.b+_17+_2b+"px"});
var _2f={top:_15?pe.t+"px":_1e.top,bottom:_15?pe.b+"px":_1e.bottom};
dojo.mixin(_1b,_2f);
dojo.mixin(_1c,_2f);
_1b.left=pe.l+"px";
_1c.right=pe.r+"px";
_1a.top=pe.t+"px";
_1d.bottom=pe.b+"px";
if(_15){
_1a.left=_1d.left=_18+_29+pe.l+"px";
_1a.right=_1d.right=_19+_2a+pe.r+"px";
}else{
_1a.left=_1d.left=pe.l+"px";
_1a.right=_1d.right=pe.r+"px";
}
var _30=this._borderBox.h-pe.t-pe.b,_31=_30-(_16+_28+_17+_2b),_32=_15?_30:_31;
var _33=this._borderBox.w-pe.l-pe.r,_34=_33-(_18+_29+_19+_2a),_35=_15?_34:_33;
var dim={top:{w:_35,h:_16},bottom:{w:_35,h:_17},left:{w:_18,h:_32},right:{w:_19,h:_32},center:{h:_31,w:_34}};
var _37=dojo.isIE<8||(dojo.isIE&&dojo.isQuirks)||dojo.some(this.getChildren(),function(_38){
return _38.domNode.tagName=="TEXTAREA"||_38.domNode.tagName=="INPUT";
});
if(_37){
var _39=function(_3a,_3b,_3c){
if(_3a){
(_3a.resize?_3a.resize(_3b,_3c):dojo.marginBox(_3a.domNode,_3b));
}
};
if(_25){
_25.style.height=_32;
}
if(_26){
_26.style.height=_32;
}
_39(this._leftWidget,{h:_32},dim.left);
_39(this._rightWidget,{h:_32},dim.right);
if(_23){
_23.style.width=_35;
}
if(_24){
_24.style.width=_35;
}
_39(this._topWidget,{w:_35},dim.top);
_39(this._bottomWidget,{w:_35},dim.bottom);
_39(this._centerWidget,dim.center);
}else{
var _3d={};
if(_14){
_3d[_14]=_3d.center=true;
if(/top|bottom/.test(_14)&&this.design!="sidebar"){
_3d.left=_3d.right=true;
}else{
if(/left|right/.test(_14)&&this.design=="sidebar"){
_3d.top=_3d.bottom=true;
}
}
}
dojo.forEach(this.getChildren(),function(_3e){
if(_3e.resize&&(!_14||_3e.region in _3d)){
_3e.resize(null,dim[_3e.region]);
}
},this);
}
},destroy:function(){
for(var _3f in this._splitters){
var _40=this._splitters[_3f];
dijit.byNode(_40).destroy();
dojo.destroy(_40);
}
delete this._splitters;
delete this._splitterThickness;
this.inherited(arguments);
}});
dojo.extend(dijit._Widget,{region:"",splitter:false,minSize:0,maxSize:Infinity});
dojo.require("dijit._Templated");
dojo.declare("dijit.layout._Splitter",[dijit._Widget,dijit._Templated],{live:true,templateString:"<div class=\"dijitSplitter\" dojoAttachEvent=\"onkeypress:_onKeyPress,onmousedown:_startDrag\" tabIndex=\"0\" waiRole=\"separator\"><div class=\"dijitSplitterThumb\"></div></div>",postCreate:function(){
this.inherited(arguments);
this.horizontal=/top|bottom/.test(this.region);
dojo.addClass(this.domNode,"dijitSplitter"+(this.horizontal?"H":"V"));
this._factor=/top|left/.test(this.region)?1:-1;
this._minSize=this.child.minSize;
this.child.domNode._recalc=true;
this.connect(this.container,"resize",function(){
this.child.domNode._recalc=true;
});
this._cookieName=this.container.id+"_"+this.region;
if(this.container.persist){
var _41=dojo.cookie(this._cookieName);
if(_41){
this.child.domNode.style[this.horizontal?"height":"width"]=_41;
}
}
},_computeMaxSize:function(){
var dim=this.horizontal?"h":"w",_43=this.container._splitterThickness[this.region];
var _44=dojo.contentBox(this.container.domNode)[dim]-(this.oppNode?dojo.marginBox(this.oppNode)[dim]:0)-20-_43*2;
this._maxSize=Math.min(this.child.maxSize,_44);
},_startDrag:function(e){
if(this.child.domNode._recalc){
this._computeMaxSize();
this.child.domNode._recalc=false;
}
if(!this.cover){
this.cover=dojo.doc.createElement("div");
dojo.addClass(this.cover,"dijitSplitterCover");
dojo.place(this.cover,this.child.domNode,"after");
}
dojo.addClass(this.cover,"dijitSplitterCoverActive");
if(this.fake){
dojo.destroy(this.fake);
}
if(!(this._resize=this.live)){
(this.fake=this.domNode.cloneNode(true)).removeAttribute("id");
dojo.addClass(this.domNode,"dijitSplitterShadow");
dojo.place(this.fake,this.domNode,"after");
}
dojo.addClass(this.domNode,"dijitSplitterActive");
var _46=this._factor,max=this._maxSize,min=this._minSize||20,_49=this.horizontal,_4a=_49?"pageY":"pageX",_4b=e[_4a],_4c=this.domNode.style,dim=_49?"h":"w",_4e=dojo.marginBox(this.child.domNode)[dim],_4f=this.region,_50=parseInt(this.domNode.style[_4f],10),_51=this._resize,mb={},_53=this.child.domNode,_54=dojo.hitch(this.container,this.container._layoutChildren),de=dojo.doc.body;
this._handlers=(this._handlers||[]).concat([dojo.connect(de,"onmousemove",this._drag=function(e,_57){
var _58=e[_4a]-_4b,_59=_46*_58+_4e,_5a=Math.max(Math.min(_59,max),min);
if(_51||_57){
mb[dim]=_5a;
dojo.marginBox(_53,mb);
_54(_4f);
}
_4c[_4f]=_46*_58+_50+(_5a-_59)+"px";
}),dojo.connect(dojo.doc,"ondragstart",dojo.stopEvent),dojo.connect(dojo.body(),"onselectstart",dojo.stopEvent),dojo.connect(de,"onmouseup",this,"_stopDrag")]);
dojo.stopEvent(e);
},_stopDrag:function(e){
try{
if(this.cover){
dojo.removeClass(this.cover,"dijitSplitterCoverActive");
}
if(this.fake){
dojo.destroy(this.fake);
}
dojo.removeClass(this.domNode,"dijitSplitterActive");
dojo.removeClass(this.domNode,"dijitSplitterShadow");
this._drag(e);
this._drag(e,true);
}
finally{
this._cleanupHandlers();
if(this.oppNode){
this.oppNode._recalc=true;
}
delete this._drag;
}
if(this.container.persist){
dojo.cookie(this._cookieName,this.child.domNode.style[this.horizontal?"height":"width"],{expires:365});
}
},_cleanupHandlers:function(){
dojo.forEach(this._handlers,dojo.disconnect);
delete this._handlers;
},_onKeyPress:function(e){
if(this.child.domNode._recalc){
this._computeMaxSize();
this.child.domNode._recalc=false;
}
this._resize=true;
var _5d=this.horizontal;
var _5e=1;
var dk=dojo.keys;
switch(e.charOrCode){
case _5d?dk.UP_ARROW:dk.LEFT_ARROW:
_5e*=-1;
case _5d?dk.DOWN_ARROW:dk.RIGHT_ARROW:
break;
default:
return;
}
var _60=dojo.marginBox(this.child.domNode)[_5d?"h":"w"]+this._factor*_5e;
var mb={};
mb[this.horizontal?"h":"w"]=Math.max(Math.min(_60,this._maxSize),this._minSize);
dojo.marginBox(this.child.domNode,mb);
if(this.oppNode){
this.oppNode._recalc=true;
}
this.container._layoutChildren(this.region);
dojo.stopEvent(e);
},destroy:function(){
this._cleanupHandlers();
delete this.child;
delete this.container;
delete this.cover;
delete this.fake;
this.inherited(arguments);
}});
dojo.declare("dijit.layout._Gutter",[dijit._Widget,dijit._Templated],{templateString:"<div class=\"dijitGutter\" waiRole=\"presentation\"></div>",postCreate:function(){
this.horizontal=/top|bottom/.test(this.region);
dojo.addClass(this.domNode,"dijitGutter"+(this.horizontal?"H":"V"));
}});
}
