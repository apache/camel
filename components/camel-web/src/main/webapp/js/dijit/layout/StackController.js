/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.layout.StackController"]){
dojo._hasResource["dijit.layout.StackController"]=true;
dojo.provide("dijit.layout.StackController");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._Container");
dojo.require("dijit.form.ToggleButton");
dojo.require("dijit.Menu");
dojo.requireLocalization("dijit","common",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.layout.StackController",[dijit._Widget,dijit._Templated,dijit._Container],{templateString:"<span wairole='tablist' dojoAttachEvent='onkeypress' class='dijitStackController'></span>",containerId:"",buttonWidget:"dijit.layout._StackButton",postCreate:function(){
dijit.setWaiRole(this.domNode,"tablist");
this.pane2button={};
this.pane2handles={};
this.pane2menu={};
this._subscriptions=[dojo.subscribe(this.containerId+"-startup",this,"onStartup"),dojo.subscribe(this.containerId+"-addChild",this,"onAddChild"),dojo.subscribe(this.containerId+"-removeChild",this,"onRemoveChild"),dojo.subscribe(this.containerId+"-selectChild",this,"onSelectChild"),dojo.subscribe(this.containerId+"-containerKeyPress",this,"onContainerKeyPress")];
},onStartup:function(_1){
dojo.forEach(_1.children,this.onAddChild,this);
this.onSelectChild(_1.selected);
},destroy:function(){
for(var _2 in this.pane2button){
this.onRemoveChild(_2);
}
dojo.forEach(this._subscriptions,dojo.unsubscribe);
this.inherited(arguments);
},onAddChild:function(_3,_4){
var _5=dojo.doc.createElement("span");
this.domNode.appendChild(_5);
var _6=dojo.getObject(this.buttonWidget);
var _7=new _6({label:_3.title,closeButton:_3.closable},_5);
this.addChild(_7,_4);
this.pane2button[_3]=_7;
_3.controlButton=_7;
var _8=[];
_8.push(dojo.connect(_7,"onClick",dojo.hitch(this,"onButtonClick",_3)));
if(_3.closable){
_8.push(dojo.connect(_7,"onClickCloseButton",dojo.hitch(this,"onCloseButtonClick",_3)));
var _9=dojo.i18n.getLocalization("dijit","common");
var _a=new dijit.Menu({targetNodeIds:[_7.id],id:_7.id+"_Menu"});
var _b=new dijit.MenuItem({label:_9.itemClose});
_8.push(dojo.connect(_b,"onClick",dojo.hitch(this,"onCloseButtonClick",_3)));
_a.addChild(_b);
this.pane2menu[_3]=_a;
}
this.pane2handles[_3]=_8;
if(!this._currentChild){
_7.focusNode.setAttribute("tabIndex","0");
this._currentChild=_3;
}
if(!this.isLeftToRight()&&dojo.isIE&&this._rectifyRtlTabList){
this._rectifyRtlTabList();
}
},onRemoveChild:function(_c){
if(this._currentChild===_c){
this._currentChild=null;
}
dojo.forEach(this.pane2handles[_c],dojo.disconnect);
delete this.pane2handles[_c];
var _d=this.pane2menu[_c];
if(_d){
_d.destroyRecursive();
delete this.pane2menu[_c];
}
var _e=this.pane2button[_c];
if(_e){
_e.destroy();
delete this.pane2button[_c];
}
},onSelectChild:function(_f){
if(!_f){
return;
}
if(this._currentChild){
var _10=this.pane2button[this._currentChild];
_10.attr("checked",false);
_10.focusNode.setAttribute("tabIndex","-1");
}
var _11=this.pane2button[_f];
_11.attr("checked",true);
this._currentChild=_f;
_11.focusNode.setAttribute("tabIndex","0");
var _12=dijit.byId(this.containerId);
dijit.setWaiState(_12.containerNode,"labelledby",_11.id);
},onButtonClick:function(_13){
var _14=dijit.byId(this.containerId);
_14.selectChild(_13);
},onCloseButtonClick:function(_15){
var _16=dijit.byId(this.containerId);
_16.closeChild(_15);
var b=this.pane2button[this._currentChild];
if(b){
dijit.focus(b.focusNode||b.domNode);
}
},adjacent:function(_18){
if(!this.isLeftToRight()&&(!this.tabPosition||/top|bottom/.test(this.tabPosition))){
_18=!_18;
}
var _19=this.getChildren();
var _1a=dojo.indexOf(_19,this.pane2button[this._currentChild]);
var _1b=_18?1:_19.length-1;
return _19[(_1a+_1b)%_19.length];
},onkeypress:function(e){
if(this.disabled||e.altKey){
return;
}
var _1d=null;
if(e.ctrlKey||!e._djpage){
var k=dojo.keys;
switch(e.charOrCode){
case k.LEFT_ARROW:
case k.UP_ARROW:
if(!e._djpage){
_1d=false;
}
break;
case k.PAGE_UP:
if(e.ctrlKey){
_1d=false;
}
break;
case k.RIGHT_ARROW:
case k.DOWN_ARROW:
if(!e._djpage){
_1d=true;
}
break;
case k.PAGE_DOWN:
if(e.ctrlKey){
_1d=true;
}
break;
case k.DELETE:
if(this._currentChild.closable){
this.onCloseButtonClick(this._currentChild);
}
dojo.stopEvent(e);
break;
default:
if(e.ctrlKey){
if(e.charOrCode===k.TAB){
this.adjacent(!e.shiftKey).onClick();
dojo.stopEvent(e);
}else{
if(e.charOrCode=="w"){
if(this._currentChild.closable){
this.onCloseButtonClick(this._currentChild);
}
dojo.stopEvent(e);
}
}
}
}
if(_1d!==null){
this.adjacent(_1d).onClick();
dojo.stopEvent(e);
}
}
},onContainerKeyPress:function(_1f){
_1f.e._djpage=_1f.page;
this.onkeypress(_1f.e);
}});
dojo.declare("dijit.layout._StackButton",dijit.form.ToggleButton,{tabIndex:"-1",postCreate:function(evt){
dijit.setWaiRole((this.focusNode||this.domNode),"tab");
this.inherited(arguments);
},onClick:function(evt){
dijit.focus(this.focusNode);
},onClickCloseButton:function(evt){
evt.stopPropagation();
}});
}
