/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.Toaster"]){
dojo._hasResource["dojox.widget.Toaster"]=true;
dojo.provide("dojox.widget.Toaster");
dojo.require("dojo.fx");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dojox.widget.Toaster",[dijit._Widget,dijit._Templated],{templateString:"<div dojoAttachPoint=\"clipNode\"><div dojoAttachPoint=\"containerNode\" dojoAttachEvent=\"onclick:onSelect\"><div dojoAttachPoint=\"contentNode\"></div></div></div>",messageTopic:"",messageTypes:{MESSAGE:"message",WARNING:"warning",ERROR:"error",FATAL:"fatal"},defaultType:"message",positionDirection:"br-up",positionDirectionTypes:["br-up","br-left","bl-up","bl-right","tr-down","tr-left","tl-down","tl-right"],duration:2000,slideDuration:500,separator:"<hr></hr>",postCreate:function(){
this.inherited(arguments);
this.hide();
dojo.body().appendChild(this.domNode);
this.clipNode.className="dijitToasterClip";
this.containerNode.className+=" dijitToasterContainer";
this.contentNode.className="dijitToasterContent";
if(this.messageTopic){
dojo.subscribe(this.messageTopic,this,"_handleMessage");
}
},_handleMessage:function(_1){
if(dojo.isString(_1)){
this.setContent(_1);
}else{
this.setContent(_1.message,_1.type,_1.duration);
}
},_capitalize:function(w){
return w.substring(0,1).toUpperCase()+w.substring(1);
},setContent:function(_3,_4,_5){
_5=_5||this.duration;
if(this.slideAnim){
if(this.slideAnim.status()!="playing"){
this.slideAnim.stop();
}
if(this.slideAnim.status()=="playing"||(this.fadeAnim&&this.fadeAnim.status()=="playing")){
setTimeout(dojo.hitch(this,function(){
this.setContent(_3,_4,_5);
}),50);
return;
}
}
for(var _6 in this.messageTypes){
dojo.removeClass(this.containerNode,"dijitToaster"+this._capitalize(this.messageTypes[_6]));
}
dojo.style(this.containerNode,"opacity",1);
this._setContent(_3);
dojo.addClass(this.containerNode,"dijitToaster"+this._capitalize(_4||this.defaultType));
this.show();
var _7=dojo.marginBox(this.containerNode);
this._cancelHideTimer();
if(this.isVisible){
this._placeClip();
if(!this._stickyMessage){
this._setHideTimer(_5);
}
}else{
var _8=this.containerNode.style;
var pd=this.positionDirection;
if(pd.indexOf("-up")>=0){
_8.left=0+"px";
_8.top=_7.h+10+"px";
}else{
if(pd.indexOf("-left")>=0){
_8.left=_7.w+10+"px";
_8.top=0+"px";
}else{
if(pd.indexOf("-right")>=0){
_8.left=0-_7.w-10+"px";
_8.top=0+"px";
}else{
if(pd.indexOf("-down")>=0){
_8.left=0+"px";
_8.top=0-_7.h-10+"px";
}else{
throw new Error(this.id+".positionDirection is invalid: "+pd);
}
}
}
}
this.slideAnim=dojo.fx.slideTo({node:this.containerNode,top:0,left:0,duration:this.slideDuration});
this.connect(this.slideAnim,"onEnd",function(_a,_b){
this.fadeAnim=dojo.fadeOut({node:this.containerNode,duration:1000});
this.connect(this.fadeAnim,"onEnd",function(_c){
this.isVisible=false;
this.hide();
});
this._setHideTimer(_5);
this.connect(this,"onSelect",function(_d){
this._cancelHideTimer();
this._stickyMessage=false;
this.fadeAnim.play();
});
this.isVisible=true;
});
this.slideAnim.play();
}
},_setContent:function(_e){
if(dojo.isFunction(_e)){
_e(this);
return;
}
if(_e&&this.isVisible){
_e=this.contentNode.innerHTML+this.separator+_e;
}
this.contentNode.innerHTML=_e;
},_cancelHideTimer:function(){
if(this._hideTimer){
clearTimeout(this._hideTimer);
this._hideTimer=null;
}
},_setHideTimer:function(_f){
this._cancelHideTimer();
if(_f>0){
this._cancelHideTimer();
this._hideTimer=setTimeout(dojo.hitch(this,function(evt){
if(this.bgIframe&&this.bgIframe.iframe){
this.bgIframe.iframe.style.display="none";
}
this._hideTimer=null;
this._stickyMessage=false;
this.fadeAnim.play();
}),_f);
}else{
this._stickyMessage=true;
}
},_placeClip:function(){
var _11=dijit.getViewport();
var _12=dojo.marginBox(this.containerNode);
var _13=this.clipNode.style;
_13.height=_12.h+"px";
_13.width=_12.w+"px";
var pd=this.positionDirection;
if(pd.match(/^t/)){
_13.top=_11.t+"px";
}else{
if(pd.match(/^b/)){
_13.top=(_11.h-_12.h-2+_11.t)+"px";
}
}
if(pd.match(/^[tb]r-/)){
_13.left=(_11.w-_12.w-1-_11.l)+"px";
}else{
if(pd.match(/^[tb]l-/)){
_13.left=0+"px";
}
}
_13.clip="rect(0px, "+_12.w+"px, "+_12.h+"px, 0px)";
if(dojo.isIE){
if(!this.bgIframe){
this.clipNode.id=dijit.getUniqueId("dojox_widget_Toaster_clipNode");
this.bgIframe=new dijit.BackgroundIframe(this.clipNode);
}
var _15=this.bgIframe.iframe;
if(_15){
_15.style.display="block";
}
}
},onSelect:function(e){
},show:function(){
dojo.style(this.domNode,"display","block");
this._placeClip();
if(!this._scrollConnected){
this._scrollConnected=dojo.connect(window,"onscroll",this,this._placeClip);
}
},hide:function(){
dojo.style(this.domNode,"display","none");
if(this._scrollConnected){
dojo.disconnect(this._scrollConnected);
this._scrollConnected=false;
}
dojo.style(this.containerNode,"opacity",1);
}});
}
