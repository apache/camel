/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.layout.FloatingPane"]){
dojo._hasResource["dojox.layout.FloatingPane"]=true;
dojo.provide("dojox.layout.FloatingPane");
dojo.experimental("dojox.layout.FloatingPane");
dojo.require("dojox.layout.ContentPane");
dojo.require("dijit._Templated");
dojo.require("dijit._Widget");
dojo.require("dojo.dnd.Moveable");
dojo.require("dojox.layout.ResizeHandle");
dojo.declare("dojox.layout.FloatingPane",[dojox.layout.ContentPane,dijit._Templated],{closable:true,dockable:true,resizable:false,maxable:false,resizeAxis:"xy",title:"",dockTo:"",duration:400,contentClass:"dojoxFloatingPaneContent",_showAnim:null,_hideAnim:null,_dockNode:null,_restoreState:{},_allFPs:[],_startZ:100,templateString:null,templateString:"<div class=\"dojoxFloatingPane\" id=\"${id}\">\n\t<div tabindex=\"0\" waiRole=\"button\" class=\"dojoxFloatingPaneTitle\" dojoAttachPoint=\"focusNode\">\n\t\t<span dojoAttachPoint=\"closeNode\" dojoAttachEvent=\"onclick: close\" class=\"dojoxFloatingCloseIcon\"></span>\n\t\t<span dojoAttachPoint=\"maxNode\" dojoAttachEvent=\"onclick: maximize\" class=\"dojoxFloatingMaximizeIcon\">&thinsp;</span>\n\t\t<span dojoAttachPoint=\"restoreNode\" dojoAttachEvent=\"onclick: _restore\" class=\"dojoxFloatingRestoreIcon\">&thinsp;</span>\t\n\t\t<span dojoAttachPoint=\"dockNode\" dojoAttachEvent=\"onclick: minimize\" class=\"dojoxFloatingMinimizeIcon\">&thinsp;</span>\n\t\t<span dojoAttachPoint=\"titleNode\" class=\"dijitInline dijitTitleNode\"></span>\n\t</div>\n\t<div dojoAttachPoint=\"canvas\" class=\"dojoxFloatingPaneCanvas\">\n\t\t<div dojoAttachPoint=\"containerNode\" waiRole=\"region\" tabindex=\"-1\" class=\"${contentClass}\">\n\t\t</div>\n\t\t<span dojoAttachPoint=\"resizeHandle\" class=\"dojoxFloatingResizeHandle\"></span>\n\t</div>\n</div>\n",postCreate:function(){
this.setTitle(this.title);
this.inherited(arguments);
var _1=new dojo.dnd.Moveable(this.domNode,{handle:this.focusNode});
if(!this.dockable){
this.dockNode.style.display="none";
}
if(!this.closable){
this.closeNode.style.display="none";
}
if(!this.maxable){
this.maxNode.style.display="none";
this.restoreNode.style.display="none";
}
if(!this.resizable){
this.resizeHandle.style.display="none";
}else{
var _2=dojo.marginBox(this.domNode);
this.domNode.style.width=_2.w+"px";
}
this._allFPs.push(this);
this.domNode.style.position="absolute";
this.bgIframe=new dijit.BackgroundIframe(this.domNode);
},startup:function(){
if(this._started){
return;
}
this.inherited(arguments);
if(this.resizable){
if(dojo.isIE){
this.canvas.style.overflow="auto";
}else{
this.containerNode.style.overflow="auto";
}
this._resizeHandle=new dojox.layout.ResizeHandle({targetId:this.id,resizeAxis:this.resizeAxis},this.resizeHandle);
}
if(this.dockable){
var _3=this.dockTo;
if(this.dockTo){
this.dockTo=dijit.byId(this.dockTo);
}else{
this.dockTo=dijit.byId("dojoxGlobalFloatingDock");
}
if(!this.dockTo){
var _4;
var _5;
if(_3){
_4=_3;
_5=dojo.byId(_3);
}else{
_5=document.createElement("div");
dojo.body().appendChild(_5);
dojo.addClass(_5,"dojoxFloatingDockDefault");
_4="dojoxGlobalFloatingDock";
}
this.dockTo=new dojox.layout.Dock({id:_4,autoPosition:"south"},_5);
this.dockTo.startup();
}
if((this.domNode.style.display=="none")||(this.domNode.style.visibility=="hidden")){
this.minimize();
}
}
this.connect(this.focusNode,"onmousedown","bringToTop");
this.connect(this.domNode,"onmousedown","bringToTop");
this.resize(dojo.coords(this.domNode));
this._started=true;
},setTitle:function(_6){
this.titleNode.innerHTML=_6;
this.title=_6;
},close:function(){
if(!this.closable){
return;
}
dojo.unsubscribe(this._listener);
this.hide(dojo.hitch(this,function(){
this.destroyRecursive();
}));
},hide:function(_7){
dojo.fadeOut({node:this.domNode,duration:this.duration,onEnd:dojo.hitch(this,function(){
this.domNode.style.display="none";
this.domNode.style.visibility="hidden";
if(this.dockTo&&this.dockable){
this.dockTo._positionDock(null);
}
if(_7){
_7();
}
})}).play();
},show:function(_8){
var _9=dojo.fadeIn({node:this.domNode,duration:this.duration,beforeBegin:dojo.hitch(this,function(){
this.domNode.style.display="";
this.domNode.style.visibility="visible";
if(this.dockTo&&this.dockable){
this.dockTo._positionDock(null);
}
if(typeof _8=="function"){
_8();
}
this._isDocked=false;
if(this._dockNode){
this._dockNode.destroy();
this._dockNode=null;
}
})}).play();
this.resize(dojo.coords(this.domNode));
},minimize:function(){
if(!this._isDocked){
this.hide(dojo.hitch(this,"_dock"));
}
},maximize:function(){
if(this._maximized){
return;
}
this._naturalState=dojo.coords(this.domNode);
if(this._isDocked){
this.show();
setTimeout(dojo.hitch(this,"maximize"),this.duration);
}
dojo.addClass(this.focusNode,"floatingPaneMaximized");
this.resize(dijit.getViewport());
this._maximized=true;
},_restore:function(){
if(this._maximized){
this.resize(this._naturalState);
dojo.removeClass(this.focusNode,"floatingPaneMaximized");
this._maximized=false;
}
},_dock:function(){
if(!this._isDocked&&this.dockable){
this._dockNode=this.dockTo.addNode(this);
this._isDocked=true;
}
},resize:function(_a){
this._currentState=_a;
var _b=this.domNode.style;
if(_a.t){
_b.top=_a.t+"px";
}
if(_a.l){
_b.left=_a.l+"px";
}
_b.width=_a.w+"px";
_b.height=_a.h+"px";
var _c={l:0,t:0,w:_a.w,h:(_a.h-this.focusNode.offsetHeight)};
dojo.marginBox(this.canvas,_c);
this._checkIfSingleChild();
if(this._singleChild&&this._singleChild.resize){
this._singleChild.resize(_c);
}
},bringToTop:function(){
var _d=dojo.filter(this._allFPs,function(i){
return i!==this;
},this);
_d.sort(function(a,b){
return a.domNode.style.zIndex-b.domNode.style.zIndex;
});
_d.push(this);
dojo.forEach(_d,function(w,x){
w.domNode.style.zIndex=this._startZ+(x*2);
dojo.removeClass(w.domNode,"dojoxFloatingPaneFg");
},this);
dojo.addClass(this.domNode,"dojoxFloatingPaneFg");
},destroy:function(){
this._allFPs.splice(dojo.indexOf(this._allFPs,this),1);
if(this._resizeHandle){
this._resizeHandle.destroy();
}
this.inherited(arguments);
}});
dojo.declare("dojox.layout.Dock",[dijit._Widget,dijit._Templated],{templateString:"<div class=\"dojoxDock\"><ul dojoAttachPoint=\"containerNode\" class=\"dojoxDockList\"></ul></div>",_docked:[],_inPositioning:false,autoPosition:false,addNode:function(_13){
var div=document.createElement("li");
this.containerNode.appendChild(div);
var _15=new dojox.layout._DockNode({title:_13.title,paneRef:_13},div);
_15.startup();
return _15;
},startup:function(){
if(this.id=="dojoxGlobalFloatingDock"||this.isFixedDock){
dojo.connect(window,"onresize",this,"_positionDock");
dojo.connect(window,"onscroll",this,"_positionDock");
if(dojo.isIE){
this.connect(this.domNode,"onresize","_positionDock");
}
}
this._positionDock(null);
this.inherited(arguments);
},_positionDock:function(e){
if(!this._inPositioning){
if(this.autoPosition=="south"){
setTimeout(dojo.hitch(this,function(){
this._inPositiononing=true;
var _17=dijit.getViewport();
var s=this.domNode.style;
s.left=_17.l+"px";
s.width=(_17.w-2)+"px";
s.top=(_17.h+_17.t)-this.domNode.offsetHeight+"px";
this._inPositioning=false;
}),125);
}
}
}});
dojo.declare("dojox.layout._DockNode",[dijit._Widget,dijit._Templated],{title:"",paneRef:null,templateString:"<li dojoAttachEvent=\"onclick: restore\" class=\"dojoxDockNode\">"+"<span dojoAttachPoint=\"restoreNode\" class=\"dojoxDockRestoreButton\" dojoAttachEvent=\"onclick: restore\"></span>"+"<span class=\"dojoxDockTitleNode\" dojoAttachPoint=\"titleNode\">${title}</span>"+"</li>",restore:function(){
this.paneRef.show();
this.paneRef.bringToTop();
this.destroy();
}});
}
