/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.layout.TabController"]){
dojo._hasResource["dijit.layout.TabController"]=true;
dojo.provide("dijit.layout.TabController");
dojo.require("dijit.layout.StackController");
dojo.requireLocalization("dijit","common",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.layout.TabController",dijit.layout.StackController,{templateString:"<div wairole='tablist' dojoAttachEvent='onkeypress:onkeypress'></div>",tabPosition:"top",doLayout:true,buttonWidget:"dijit.layout._TabButton",_rectifyRtlTabList:function(){
if(0>=this.tabPosition.indexOf("-h")){
return;
}
if(!this.pane2button){
return;
}
var _1=0;
for(var _2 in this.pane2button){
var ow=this.pane2button[_2].innerDiv.scrollWidth;
_1=Math.max(_1,ow);
}
for(_2 in this.pane2button){
this.pane2button[_2].innerDiv.style.width=_1+"px";
}
}});
dojo.declare("dijit.layout._TabButton",dijit.layout._StackButton,{baseClass:"dijitTab",templateString:"<div waiRole=\"presentation\" dojoAttachEvent='onclick:onClick,onmouseenter:_onMouse,onmouseleave:_onMouse'>\n    <div waiRole=\"presentation\" class='dijitTabInnerDiv' dojoAttachPoint='innerDiv'>\n        <div waiRole=\"presentation\" class='dijitTabContent' dojoAttachPoint='tabContent'>\n\t        <span dojoAttachPoint='containerNode,focusNode' class='tabLabel'>${!label}</span><img class =\"dijitTabButtonSpacer\" src=\"${_blankGif}\" />\n\t        <span class=\"closeButton\" dojoAttachPoint='closeNode'\n\t        \t\tdojoAttachEvent='onclick: onClickCloseButton, onmouseenter: _onCloseButtonEnter, onmouseleave: _onCloseButtonLeave'>\n\t        \t<img src=\"${_blankGif}\" alt=\"\" dojoAttachPoint='closeIcon' class='closeImage' waiRole=\"presentation\"/>\n\t            <span dojoAttachPoint='closeText' class='closeText'>x</span>\n\t        </span>\n        </div>\n    </div>\n</div>\n",scrollOnFocus:false,postCreate:function(){
if(this.closeButton){
dojo.addClass(this.innerDiv,"dijitClosable");
var _4=dojo.i18n.getLocalization("dijit","common");
if(this.closeNode){
dojo.attr(this.closeNode,"title",_4.itemClose);
dojo.attr(this.closeIcon,"title",_4.itemClose);
}
}else{
this.closeNode.style.display="none";
}
this.inherited(arguments);
dojo.setSelectable(this.containerNode,false);
},_onCloseButtonEnter:function(){
dojo.addClass(this.closeNode,"closeButton-hover");
},_onCloseButtonLeave:function(){
dojo.removeClass(this.closeNode,"closeButton-hover");
}});
}
