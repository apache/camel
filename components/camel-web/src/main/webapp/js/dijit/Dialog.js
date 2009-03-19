/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.Dialog"]){
dojo._hasResource["dijit.Dialog"]=true;
dojo.provide("dijit.Dialog");
dojo.require("dojo.dnd.move");
dojo.require("dojo.dnd.TimedMoveable");
dojo.require("dojo.fx");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit.form._FormMixin");
dojo.require("dijit._DialogMixin");
dojo.require("dijit.DialogUnderlay");
dojo.require("dijit.layout.ContentPane");
dojo.requireLocalization("dijit","common",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.Dialog",[dijit.layout.ContentPane,dijit._Templated,dijit.form._FormMixin,dijit._DialogMixin],{templateString:null,templateString:"<div class=\"dijitDialog\" tabindex=\"-1\" waiRole=\"dialog\" waiState=\"labelledby-${id}_title\">\n\t<div dojoAttachPoint=\"titleBar\" class=\"dijitDialogTitleBar\">\n\t<span dojoAttachPoint=\"titleNode\" class=\"dijitDialogTitle\" id=\"${id}_title\"></span>\n\t<span dojoAttachPoint=\"closeButtonNode\" class=\"dijitDialogCloseIcon\" dojoAttachEvent=\"onclick: onCancel, onmouseenter: _onCloseEnter, onmouseleave: _onCloseLeave\" title=\"${buttonCancel}\">\n\t\t<span dojoAttachPoint=\"closeText\" class=\"closeText\" title=\"${buttonCancel}\">x</span>\n\t</span>\n\t</div>\n\t\t<div dojoAttachPoint=\"containerNode\" class=\"dijitDialogPaneContent\"></div>\n</div>\n",attributeMap:dojo.delegate(dijit._Widget.prototype.attributeMap,{title:[{node:"titleNode",type:"innerHTML"},{node:"titleBar",type:"attribute"}]}),open:false,duration:dijit.defaultDuration,refocus:true,autofocus:true,_firstFocusItem:null,_lastFocusItem:null,doLayout:false,draggable:true,postMixInProperties:function(){
var _1=dojo.i18n.getLocalization("dijit","common");
dojo.mixin(this,_1);
this.inherited(arguments);
},postCreate:function(){
dojo.style(this.domNode,{visibility:"hidden",position:"absolute",display:"",top:"-9999px"});
dojo.body().appendChild(this.domNode);
this.inherited(arguments);
this.connect(this,"onExecute","hide");
this.connect(this,"onCancel","hide");
this._modalconnects=[];
},onLoad:function(){
this._position();
this.inherited(arguments);
},_endDrag:function(e){
if(e&&e.node&&e.node===this.domNode){
var vp=dijit.getViewport();
var p=e._leftTop||dojo.coords(e.node,true);
this._relativePosition={t:p.t-vp.t,l:p.l-vp.l};
}
},_setup:function(){
var _5=this.domNode;
if(this.titleBar&&this.draggable){
this._moveable=(dojo.isIE==6)?new dojo.dnd.TimedMoveable(_5,{handle:this.titleBar}):new dojo.dnd.Moveable(_5,{handle:this.titleBar,timeout:0});
dojo.subscribe("/dnd/move/stop",this,"_endDrag");
}else{
dojo.addClass(_5,"dijitDialogFixed");
}
var _6={dialogId:this.id,"class":dojo.map(this["class"].split(/\s/),function(s){
return s+"_underlay";
}).join(" ")};
var _8=dijit._underlay;
if(!_8){
_8=dijit._underlay=new dijit.DialogUnderlay(_6);
}
this._fadeIn=dojo.fadeIn({node:_5,duration:this.duration,beforeBegin:function(){
_8.attr(_6);
_8.show();
},onEnd:dojo.hitch(this,function(){
if(this.autofocus){
this._getFocusItems(this.domNode);
dijit.focus(this._firstFocusItem);
}
})});
this._fadeOut=dojo.fadeOut({node:_5,duration:this.duration,onEnd:function(){
_5.style.visibility="hidden";
_5.style.top="-9999px";
dijit._underlay.hide();
}});
},uninitialize:function(){
if(this._fadeIn&&this._fadeIn.status()=="playing"){
this._fadeIn.stop();
}
if(this._fadeOut&&this._fadeOut.status()=="playing"){
this._fadeOut.stop();
}
if(this._moveable){
this._moveable.destroy();
}
},_size:function(){
var mb=dojo.marginBox(this.domNode);
var _a=dijit.getViewport();
if(mb.w>=_a.w||mb.h>=_a.h){
dojo.style(this.containerNode,{width:Math.min(mb.w,Math.floor(_a.w*0.75))+"px",height:Math.min(mb.h,Math.floor(_a.h*0.75))+"px",overflow:"auto",position:"relative"});
}
},_position:function(){
if(!dojo.hasClass(dojo.body(),"dojoMove")){
var _b=this.domNode;
var _c=dijit.getViewport();
var p=this._relativePosition;
var mb=p?null:dojo.marginBox(_b);
dojo.style(_b,{left:Math.floor(_c.l+(p?p.l:(_c.w-mb.w)/2))+"px",top:Math.floor(_c.t+(p?p.t:(_c.h-mb.h)/2))+"px"});
}
},_onKey:function(_f){
if(_f.charOrCode){
var dk=dojo.keys;
var _11=_f.target;
if(_f.charOrCode===dk.TAB){
this._getFocusItems(this.domNode);
}
var _12=(this._firstFocusItem==this._lastFocusItem);
if(_11==this._firstFocusItem&&_f.shiftKey&&_f.charOrCode===dk.TAB){
if(!_12){
dijit.focus(this._lastFocusItem);
}
dojo.stopEvent(_f);
}else{
if(_11==this._lastFocusItem&&_f.charOrCode===dk.TAB&&!_f.shiftKey){
if(!_12){
dijit.focus(this._firstFocusItem);
}
dojo.stopEvent(_f);
}else{
while(_11){
if(_11==this.domNode){
if(_f.charOrCode==dk.ESCAPE){
this.onCancel();
}else{
return;
}
}
_11=_11.parentNode;
}
if(_f.charOrCode!==dk.TAB){
dojo.stopEvent(_f);
}else{
if(!dojo.isOpera){
try{
this._firstFocusItem.focus();
}
catch(e){
}
}
}
}
}
}
},show:function(){
if(this.open){
return;
}
if(!this._alreadyInitialized){
this._setup();
this._alreadyInitialized=true;
}
if(this._fadeOut.status()=="playing"){
this._fadeOut.stop();
}
this._modalconnects.push(dojo.connect(window,"onscroll",this,"layout"));
this._modalconnects.push(dojo.connect(window,"onresize",this,"layout"));
this._modalconnects.push(dojo.connect(dojo.doc.documentElement,"onkeypress",this,"_onKey"));
dojo.style(this.domNode,{opacity:0,visibility:""});
this.open=true;
this._onShow();
this._size();
this._position();
this._fadeIn.play();
this._savedFocus=dijit.getFocus(this);
},hide:function(){
if(!this._alreadyInitialized){
return;
}
if(this._fadeIn.status()=="playing"){
this._fadeIn.stop();
}
this._fadeOut.play();
if(this._scrollConnected){
this._scrollConnected=false;
}
dojo.forEach(this._modalconnects,dojo.disconnect);
this._modalconnects=[];
if(this.refocus){
this.connect(this._fadeOut,"onEnd",dojo.hitch(dijit,"focus",this._savedFocus));
}
if(this._relativePosition){
delete this._relativePosition;
}
this.open=false;
},layout:function(){
if(this.domNode.style.visibility!="hidden"){
dijit._underlay.layout();
this._position();
}
},destroy:function(){
dojo.forEach(this._modalconnects,dojo.disconnect);
if(this.refocus&&this.open){
setTimeout(dojo.hitch(dijit,"focus",this._savedFocus),25);
}
this.inherited(arguments);
},_onCloseEnter:function(){
dojo.addClass(this.closeButtonNode,"dijitDialogCloseIcon-hover");
},_onCloseLeave:function(){
dojo.removeClass(this.closeButtonNode,"dijitDialogCloseIcon-hover");
}});
dojo.require("dijit.TooltipDialog");
}
