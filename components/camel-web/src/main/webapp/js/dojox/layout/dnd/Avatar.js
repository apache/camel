/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.layout.dnd.Avatar"]){
dojo._hasResource["dojox.layout.dnd.Avatar"]=true;
dojo.provide("dojox.layout.dnd.Avatar");
dojo.require("dojo.dnd.common");
dojox.layout.dnd.Avatar=function(_1,_2){
this.manager=_1;
this.construct(_2);
};
dojo.extend(dojox.layout.dnd.Avatar,{construct:function(_3){
var _4=this.manager.source;
var _5=(_4.creator)?_4._normalizedCreator(_4.getItem(this.manager.nodes[0].id).data,"avatar").node:this.manager.nodes[0].cloneNode(true);
_5.id=dojo.dnd.getUniqueId();
dojo.addClass(_5,"dojoDndAvatar");
_5.style.position="absolute";
_5.style.zIndex=1999;
_5.style.margin="0px";
_5.style.width=dojo.marginBox(_4.node).w+"px";
dojo.style(_5,"opacity",_3);
this.node=_5;
},destroy:function(){
dojo.destroy(this.node);
this.node=false;
},update:function(){
dojo[(this.manager.canDropFlag?"add":"remove")+"Class"](this.node,"dojoDndAvatarCanDrop");
},_generateText:function(){
}});
}
