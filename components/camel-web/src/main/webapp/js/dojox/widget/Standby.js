/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.Standby"]){
dojo._hasResource["dojox.widget.Standby"]=true;
dojo.provide("dojox.widget.Standby");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojo.fx");
dojo.experimental("dojox.widget.Standby");
dojo.declare("dojox.widget.Standby",[dijit._Widget,dijit._Templated],{templateString:"<div>\n\t<div class=\"standbyUnderlayNode\" dojoAttachPoint=\"_underlayNode\">\n\t</div>\n\t<img src=\"${image}\" class=\"standbyImageNode\" dojoAttachPoint=\"_imageNode\">\n</div>\n\n",_underlayNode:null,_imageNode:null,image:dojo.moduleUrl("dojox","widget/Standby/images/loading.gif").toString(),imageText:"Please Wait...",_displayed:false,_resizeCheck:null,target:"",color:"#C0C0C0",startup:function(_1){
if(typeof this.target==="string"){
var w=dijit.byId(this.target);
if(w){
this.target=w.domNode;
}else{
this.target=dojo.byId(this.target);
}
}
dojo.style(this._underlayNode,"display","none");
dojo.style(this._imageNode,"display","none");
dojo.style(this._underlayNode,"backgroundColor",this.color);
dojo.attr(this._imageNode,"src",this.image);
dojo.attr(this._imageNode,"alt",this.imageText);
this.connect(this._underlayNode,"onclick","_ignore");
if(this.domNode.parentNode&&this.domNode.parentNode!=dojo.body()){
dojo.body().appendChild(this.domNode);
}
},show:function(){
if(!this._displayed){
this._displayed=true;
this._size();
this._fadeIn();
}
},hide:function(){
if(this._displayed){
this._size();
this._fadeOut();
this._displayed=false;
if(this._resizeCheck!==null){
clearInterval(this._resizeCheck);
this._resizeCheck=null;
}
}
},_size:function(){
if(this._displayed){
var _3=dojo.style(this._imageNode,"display");
dojo.style(this._imageNode,"display","block");
var _4=dojo.coords(this.target);
var _5=dojo.marginBox(this._imageNode);
dojo.style(this._imageNode,"display",_3);
dojo.style(this._imageNode,"zIndex","10000");
var _6=dojo._docScroll();
if(!_6){
_6={x:0,y:0};
}
var _7=dojo.style(this.target,"marginLeft");
if(dojo.isWebKit&&_7){
_7=_7*2;
}
if(_7){
_4.w=_4.w-_7;
}
if(!dojo.isWebKit){
var _8=dojo.style(this.target,"marginRight");
if(_8){
_4.w=_4.w-_8;
}
}
var _9=dojo.style(this.target,"marginTop");
if(_9){
_4.h=_4.h-_9;
}
var _a=dojo.style(this.target,"marginBottom");
if(_a){
_4.h=_4.h-_a;
}
if(_4.h>0&&_4.w>0){
dojo.style(this._underlayNode,"width",_4.w+"px");
dojo.style(this._underlayNode,"height",_4.h+"px");
dojo.style(this._underlayNode,"top",(_4.y+_6.y)+"px");
dojo.style(this._underlayNode,"left",(_4.x+_6.x)+"px");
var _b=function(_c,_d){
dojo.forEach(_c,function(_e){
dojo.style(this._underlayNode,_e,dojo.style(this.target,_e));
},_d);
};
var _f=["borderRadius","borderTopLeftRadius","borderTopRightRadius","borderBottomLeftRadius","borderBottomRightRadius"];
_b(_f,this);
if(!dojo.isIE){
_f=["MozBorderRadius","MozBorderRadiusTopleft","MozBorderRadiusTopright","MozBorderRadiusBottomleft","MozBorderRadiusBottomright","WebkitBorderRadius","WebkitBorderTopLeftRadius","WebkitBorderTopRightRadius","WebkitBorderBottomLeftRadius","WebkitBorderBottomRightRadius"];
_b(_f,this);
}
var _10=(_4.h/2)-(_5.h/2);
var _11=(_4.w/2)-(_5.w/2);
dojo.style(this._imageNode,"top",(_10+_4.y+_6.y)+"px");
dojo.style(this._imageNode,"left",(_11+_4.x+_6.x)+"px");
dojo.style(this._underlayNode,"display","block");
dojo.style(this._imageNode,"display","block");
}else{
dojo.style(this._underlayNode,"display","none");
dojo.style(this._imageNode,"display","none");
}
if(this._resizeCheck===null){
var _12=this;
this._resizeCheck=setInterval(function(){
_12._size();
},100);
}
}
},_fadeIn:function(){
var _13=dojo.animateProperty({node:this._underlayNode,properties:{opacity:{start:0,end:0.75}}});
var _14=dojo.animateProperty({node:this._imageNode,properties:{opacity:{start:0,end:1}}});
var _15=dojo.fx.combine([_13,_14]);
_15.play();
},_fadeOut:function(){
var _16=this;
var _17=dojo.animateProperty({node:this._underlayNode,properties:{opacity:{start:0.75,end:0}},onEnd:function(){
dojo.style(_16._underlayNode,"display","none");
}});
var _18=dojo.animateProperty({node:this._imageNode,properties:{opacity:{start:1,end:0}},onEnd:function(){
dojo.style(_16._imageNode,"display","none");
}});
var _19=dojo.fx.combine([_17,_18]);
_19.play();
},_ignore:function(_1a){
if(_1a){
_1a.preventDefault();
_1a.stopPropagation();
}
},uninitialize:function(){
this.hide();
}});
}
