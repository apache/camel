/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.layout._LayoutWidget"]){
dojo._hasResource["dijit.layout._LayoutWidget"]=true;
dojo.provide("dijit.layout._LayoutWidget");
dojo.require("dijit._Widget");
dojo.require("dijit._Container");
dojo.require("dijit._Contained");
dojo.declare("dijit.layout._LayoutWidget",[dijit._Widget,dijit._Container,dijit._Contained],{baseClass:"dijitLayoutContainer",isLayoutContainer:true,postCreate:function(){
dojo.addClass(this.domNode,"dijitContainer");
dojo.addClass(this.domNode,this.baseClass);
},startup:function(){
if(this._started){
return;
}
dojo.forEach(this.getChildren(),function(_1){
_1.startup();
});
if(!this.getParent||!this.getParent()){
this.resize();
this.connect(dojo.global,"onresize",dojo.hitch(this,"resize"));
}
this.inherited(arguments);
},resize:function(_2,_3){
var _4=this.domNode;
if(_2){
dojo.marginBox(_4,_2);
if(_2.t){
_4.style.top=_2.t+"px";
}
if(_2.l){
_4.style.left=_2.l+"px";
}
}
var mb=_3||{};
dojo.mixin(mb,_2||{});
if(!("h" in mb)||!("w" in mb)){
mb=dojo.mixin(dojo.marginBox(_4),mb);
}
var cs=dojo.getComputedStyle(_4);
var me=dojo._getMarginExtents(_4,cs);
var be=dojo._getBorderExtents(_4,cs);
var bb=this._borderBox={w:mb.w-(me.w+be.w),h:mb.h-(me.h+be.h)};
var pe=dojo._getPadExtents(_4,cs);
this._contentBox={l:dojo._toPixelValue(_4,cs.paddingLeft),t:dojo._toPixelValue(_4,cs.paddingTop),w:bb.w-pe.w,h:bb.h-pe.h};
this.layout();
},layout:function(){
},_setupChild:function(_b){
dojo.addClass(_b.domNode,this.baseClass+"-child");
if(_b.baseClass){
dojo.addClass(_b.domNode,this.baseClass+"-"+_b.baseClass);
}
},addChild:function(_c,_d){
this.inherited(arguments);
if(this._started){
this._setupChild(_c);
}
},removeChild:function(_e){
dojo.removeClass(_e.domNode,this.baseClass+"-child");
if(_e.baseClass){
dojo.removeClass(_e.domNode,this.baseClass+"-"+_e.baseClass);
}
this.inherited(arguments);
}});
dijit.layout.marginBox2contentBox=function(_f,mb){
var cs=dojo.getComputedStyle(_f);
var me=dojo._getMarginExtents(_f,cs);
var pb=dojo._getPadBorderExtents(_f,cs);
return {l:dojo._toPixelValue(_f,cs.paddingLeft),t:dojo._toPixelValue(_f,cs.paddingTop),w:mb.w-(me.w+pb.w),h:mb.h-(me.h+pb.h)};
};
(function(){
var _14=function(_15){
return _15.substring(0,1).toUpperCase()+_15.substring(1);
};
var _16=function(_17,dim){
_17.resize?_17.resize(dim):dojo.marginBox(_17.domNode,dim);
dojo.mixin(_17,dojo.marginBox(_17.domNode));
dojo.mixin(_17,dim);
};
dijit.layout.layoutChildren=function(_19,dim,_1b){
dim=dojo.mixin({},dim);
dojo.addClass(_19,"dijitLayoutContainer");
_1b=dojo.filter(_1b,function(_1c){
return _1c.layoutAlign!="client";
}).concat(dojo.filter(_1b,function(_1d){
return _1d.layoutAlign=="client";
}));
dojo.forEach(_1b,function(_1e){
var elm=_1e.domNode,pos=_1e.layoutAlign;
var _21=elm.style;
_21.left=dim.l+"px";
_21.top=dim.t+"px";
_21.bottom=_21.right="auto";
dojo.addClass(elm,"dijitAlign"+_14(pos));
if(pos=="top"||pos=="bottom"){
_16(_1e,{w:dim.w});
dim.h-=_1e.h;
if(pos=="top"){
dim.t+=_1e.h;
}else{
_21.top=dim.t+dim.h+"px";
}
}else{
if(pos=="left"||pos=="right"){
_16(_1e,{h:dim.h});
dim.w-=_1e.w;
if(pos=="left"){
dim.l+=_1e.w;
}else{
_21.left=dim.l+dim.w+"px";
}
}else{
if(pos=="client"){
_16(_1e,dim);
}
}
}
});
};
})();
}
