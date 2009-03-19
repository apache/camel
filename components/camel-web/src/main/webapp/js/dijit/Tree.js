/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.Tree"]){
dojo._hasResource["dijit.Tree"]=true;
dojo.provide("dijit.Tree");
dojo.require("dojo.fx");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._Container");
dojo.require("dijit._Contained");
dojo.require("dojo.cookie");
dojo.declare("dijit._TreeNode",[dijit._Widget,dijit._Templated,dijit._Container,dijit._Contained],{item:null,isTreeNode:true,label:"",isExpandable:null,isExpanded:false,state:"UNCHECKED",templateString:"<div class=\"dijitTreeNode\" waiRole=\"presentation\"\n\t><div dojoAttachPoint=\"rowNode\" class=\"dijitTreeRow\" waiRole=\"presentation\" dojoAttachEvent=\"onmouseenter:_onMouseEnter, onmouseleave:_onMouseLeave\"\n\t\t><img src=\"${_blankGif}\" alt=\"\" dojoAttachPoint=\"expandoNode\" class=\"dijitTreeExpando\" waiRole=\"presentation\"\n\t\t><span dojoAttachPoint=\"expandoNodeText\" class=\"dijitExpandoText\" waiRole=\"presentation\"\n\t\t></span\n\t\t><span dojoAttachPoint=\"contentNode\"\n\t\t\tclass=\"dijitTreeContent\" waiRole=\"presentation\">\n\t\t\t<img src=\"${_blankGif}\" alt=\"\" dojoAttachPoint=\"iconNode\" class=\"dijitTreeIcon\" waiRole=\"presentation\"\n\t\t\t><span dojoAttachPoint=\"labelNode\" class=\"dijitTreeLabel\" wairole=\"treeitem\" tabindex=\"-1\" waiState=\"selected-false\" dojoAttachEvent=\"onfocus:_onLabelFocus, onblur:_onLabelBlur\"></span>\n\t\t</span\n\t></div>\n\t<div dojoAttachPoint=\"containerNode\" class=\"dijitTreeContainer\" waiRole=\"presentation\" style=\"display: none;\"></div>\n</div>\n",postCreate:function(){
this.setLabelNode(this.label);
this._setExpando();
this._updateItemClasses(this.item);
if(this.isExpandable){
dijit.setWaiState(this.labelNode,"expanded",this.isExpanded);
if(this==this.tree.rootNode){
dijit.setWaitState(this.tree.domNode,"expanded",this.isExpanded);
}
}
},_setIndentAttr:function(_1){
this.indent=_1;
var _2=(Math.max(_1,0)*19)+"px";
dojo.style(this.domNode,"backgroundPosition",_2+" 0px");
dojo.style(this.rowNode,dojo._isBodyLtr()?"paddingLeft":"paddingRight",_2);
dojo.forEach(this.getChildren(),function(_3){
_3.attr("indent",_1+1);
});
},markProcessing:function(){
this.state="LOADING";
this._setExpando(true);
},unmarkProcessing:function(){
this._setExpando(false);
},_updateItemClasses:function(_4){
var _5=this.tree,_6=_5.model;
if(_5._v10Compat&&_4===_6.root){
_4=null;
}
if(this._iconClass){
dojo.removeClass(this.iconNode,this._iconClass);
}
this._iconClass=_5.getIconClass(_4,this.isExpanded);
if(this._iconClass){
dojo.addClass(this.iconNode,this._iconClass);
}
dojo.style(this.iconNode,_5.getIconStyle(_4,this.isExpanded)||{});
if(this._labelClass){
dojo.removeClass(this.labelNode,this._labelClass);
}
this._labelClass=_5.getLabelClass(_4,this.isExpanded);
if(this._labelClass){
dojo.addClass(this.labelNode,this._labelClass);
}
dojo.style(this.labelNode,_5.getLabelStyle(_4,this.isExpanded)||{});
},_updateLayout:function(){
var _7=this.getParent();
if(!_7||_7.rowNode.style.display=="none"){
dojo.addClass(this.domNode,"dijitTreeIsRoot");
}else{
dojo.toggleClass(this.domNode,"dijitTreeIsLast",!this.getNextSibling());
}
},_setExpando:function(_8){
var _9=["dijitTreeExpandoLoading","dijitTreeExpandoOpened","dijitTreeExpandoClosed","dijitTreeExpandoLeaf"];
var _a=["*","-","+","*"];
var _b=_8?0:(this.isExpandable?(this.isExpanded?1:2):3);
dojo.forEach(_9,function(s){
dojo.removeClass(this.expandoNode,s);
},this);
dojo.addClass(this.expandoNode,_9[_b]);
this.expandoNodeText.innerHTML=_a[_b];
},expand:function(){
if(this.isExpanded){
return;
}
this._wipeOut&&this._wipeOut.stop();
this.isExpanded=true;
dijit.setWaiState(this.labelNode,"expanded","true");
dijit.setWaiRole(this.containerNode,"group");
dojo.addClass(this.contentNode,"dijitTreeContentExpanded");
this._setExpando();
this._updateItemClasses(this.item);
if(this==this.tree.rootNode){
dijit.setWaiState(this.tree.domNode,"expanded","true");
}
if(!this._wipeIn){
this._wipeIn=dojo.fx.wipeIn({node:this.containerNode,duration:dijit.defaultDuration});
}
this._wipeIn.play();
},collapse:function(){
if(!this.isExpanded){
return;
}
this._wipeIn&&this._wipeIn.stop();
this.isExpanded=false;
dijit.setWaiState(this.labelNode,"expanded","false");
if(this==this.tree.rootNode){
dijit.setWaiState(this.tree.domNode,"expanded","false");
}
dojo.removeClass(this.contentNode,"dijitTreeContentExpanded");
this._setExpando();
this._updateItemClasses(this.item);
if(!this._wipeOut){
this._wipeOut=dojo.fx.wipeOut({node:this.containerNode,duration:dijit.defaultDuration});
}
this._wipeOut.play();
},setLabelNode:function(_d){
this.labelNode.innerHTML="";
this.labelNode.appendChild(dojo.doc.createTextNode(_d));
},indent:0,setChildItems:function(_e){
var _f=this.tree,_10=_f.model;
this.getChildren().forEach(function(_11){
dijit._Container.prototype.removeChild.call(this,_11);
},this);
this.state="LOADED";
if(_e&&_e.length>0){
this.isExpandable=true;
dojo.forEach(_e,function(_12){
var id=_10.getIdentity(_12),_14=_f._itemNodeMap[id],_15=(_14&&!_14.getParent())?_14:this.tree._createTreeNode({item:_12,tree:_f,isExpandable:_10.mayHaveChildren(_12),label:_f.getLabel(_12),indent:this.indent+1});
if(_14){
_14.attr("indent",this.indent+1);
}
this.addChild(_15);
_f._itemNodeMap[id]=_15;
if(this.tree._state(_12)){
_f._expandNode(_15);
}
},this);
dojo.forEach(this.getChildren(),function(_16,idx){
_16._updateLayout();
});
}else{
this.isExpandable=false;
}
if(this._setExpando){
this._setExpando(false);
}
if(this==_f.rootNode){
var fc=this.tree.showRoot?this:this.getChildren()[0];
if(fc){
fc.setSelected(true);
_f.lastFocused=fc;
}else{
_f.domNode.setAttribute("tabIndex","0");
}
}
},removeChild:function(_19){
this.inherited(arguments);
var _1a=this.getChildren();
if(_1a.length==0){
this.isExpandable=false;
this.collapse();
}
dojo.forEach(_1a,function(_1b){
_1b._updateLayout();
});
},makeExpandable:function(){
this.isExpandable=true;
this._setExpando(false);
},_onLabelFocus:function(evt){
dojo.addClass(this.labelNode,"dijitTreeLabelFocused");
this.tree._onNodeFocus(this);
},_onLabelBlur:function(evt){
dojo.removeClass(this.labelNode,"dijitTreeLabelFocused");
},setSelected:function(_1e){
var _1f=this.labelNode;
_1f.setAttribute("tabIndex",_1e?"0":"-1");
dijit.setWaiState(_1f,"selected",_1e);
dojo.toggleClass(this.rowNode,"dijitTreeNodeSelected",_1e);
},_onMouseEnter:function(evt){
dojo.addClass(this.rowNode,"dijitTreeNodeHover");
this.tree._onNodeMouseEnter(this,evt);
},_onMouseLeave:function(evt){
dojo.removeClass(this.rowNode,"dijitTreeNodeHover");
this.tree._onNodeMouseLeave(this,evt);
}});
dojo.declare("dijit.Tree",[dijit._Widget,dijit._Templated],{store:null,model:null,query:null,label:"",showRoot:true,childrenAttr:["children"],openOnClick:false,openOnDblClick:false,templateString:"<div class=\"dijitTreeContainer\" waiRole=\"tree\"\n\tdojoAttachEvent=\"onclick:_onClick,onkeypress:_onKeyPress,ondblclick:_onDblClick\">\n</div>\n",isExpandable:true,isTree:true,persist:true,dndController:null,dndParams:["onDndDrop","itemCreator","onDndCancel","checkAcceptance","checkItemAcceptance","dragThreshold","betweenThreshold"],onDndDrop:null,itemCreator:null,onDndCancel:null,checkAcceptance:null,checkItemAcceptance:null,dragThreshold:0,betweenThreshold:0,_publish:function(_22,_23){
dojo.publish(this.id,[dojo.mixin({tree:this,event:_22},_23||{})]);
},postMixInProperties:function(){
this.tree=this;
this._itemNodeMap={};
if(!this.cookieName){
this.cookieName=this.id+"SaveStateCookie";
}
},postCreate:function(){
this._initState();
if(!this.model){
this._store2model();
}
this.connect(this.model,"onChange","_onItemChange");
this.connect(this.model,"onChildrenChange","_onItemChildrenChange");
this.connect(this.model,"onDelete","_onItemDelete");
this._load();
this.inherited(arguments);
if(this.dndController){
if(dojo.isString(this.dndController)){
this.dndController=dojo.getObject(this.dndController);
}
var _24={};
for(var i=0;i<this.dndParams.length;i++){
if(this[this.dndParams[i]]){
_24[this.dndParams[i]]=this[this.dndParams[i]];
}
}
this.dndController=new this.dndController(this,_24);
}
},_store2model:function(){
this._v10Compat=true;
dojo.deprecated("Tree: from version 2.0, should specify a model object rather than a store/query");
var _26={id:this.id+"_ForestStoreModel",store:this.store,query:this.query,childrenAttrs:this.childrenAttr};
if(this.params.mayHaveChildren){
_26.mayHaveChildren=dojo.hitch(this,"mayHaveChildren");
}
if(this.params.getItemChildren){
_26.getChildren=dojo.hitch(this,function(_27,_28,_29){
this.getItemChildren((this._v10Compat&&_27===this.model.root)?null:_27,_28,_29);
});
}
this.model=new dijit.tree.ForestStoreModel(_26);
this.showRoot=Boolean(this.label);
},_load:function(){
this.model.getRoot(dojo.hitch(this,function(_2a){
var rn=this.rootNode=this.tree._createTreeNode({item:_2a,tree:this,isExpandable:true,label:this.label||this.getLabel(_2a),indent:this.showRoot?0:-1});
if(!this.showRoot){
rn.rowNode.style.display="none";
}
this.domNode.appendChild(rn.domNode);
this._itemNodeMap[this.model.getIdentity(_2a)]=rn;
rn._updateLayout();
this._expandNode(rn);
}),function(err){
console.error(this,": error loading root: ",err);
});
},mayHaveChildren:function(_2d){
},getItemChildren:function(_2e,_2f){
},getLabel:function(_30){
return this.model.getLabel(_30);
},getIconClass:function(_31,_32){
return (!_31||this.model.mayHaveChildren(_31))?(_32?"dijitFolderOpened":"dijitFolderClosed"):"dijitLeaf";
},getLabelClass:function(_33,_34){
},getIconStyle:function(_35,_36){
},getLabelStyle:function(_37,_38){
},_onKeyPress:function(e){
if(e.altKey){
return;
}
var dk=dojo.keys;
var _3b=dijit.getEnclosingWidget(e.target);
if(!_3b){
return;
}
var key=e.charOrCode;
if(typeof key=="string"){
if(!e.altKey&&!e.ctrlKey&&!e.shiftKey&&!e.metaKey){
this._onLetterKeyNav({node:_3b,key:key.toLowerCase()});
dojo.stopEvent(e);
}
}else{
var map=this._keyHandlerMap;
if(!map){
map={};
map[dk.ENTER]="_onEnterKey";
map[this.isLeftToRight()?dk.LEFT_ARROW:dk.RIGHT_ARROW]="_onLeftArrow";
map[this.isLeftToRight()?dk.RIGHT_ARROW:dk.LEFT_ARROW]="_onRightArrow";
map[dk.UP_ARROW]="_onUpArrow";
map[dk.DOWN_ARROW]="_onDownArrow";
map[dk.HOME]="_onHomeKey";
map[dk.END]="_onEndKey";
this._keyHandlerMap=map;
}
if(this._keyHandlerMap[key]){
this[this._keyHandlerMap[key]]({node:_3b,item:_3b.item});
dojo.stopEvent(e);
}
}
},_onEnterKey:function(_3e){
this._publish("execute",{item:_3e.item,node:_3e.node});
this.onClick(_3e.item,_3e.node);
},_onDownArrow:function(_3f){
var _40=this._getNextNode(_3f.node);
if(_40&&_40.isTreeNode){
this.focusNode(_40);
}
},_onUpArrow:function(_41){
var _42=_41.node;
var _43=_42.getPreviousSibling();
if(_43){
_42=_43;
while(_42.isExpandable&&_42.isExpanded&&_42.hasChildren()){
var _44=_42.getChildren();
_42=_44[_44.length-1];
}
}else{
var _45=_42.getParent();
if(!(!this.showRoot&&_45===this.rootNode)){
_42=_45;
}
}
if(_42&&_42.isTreeNode){
this.focusNode(_42);
}
},_onRightArrow:function(_46){
var _47=_46.node;
if(_47.isExpandable&&!_47.isExpanded){
this._expandNode(_47);
}else{
if(_47.hasChildren()){
_47=_47.getChildren()[0];
if(_47&&_47.isTreeNode){
this.focusNode(_47);
}
}
}
},_onLeftArrow:function(_48){
var _49=_48.node;
if(_49.isExpandable&&_49.isExpanded){
this._collapseNode(_49);
}else{
var _4a=_49.getParent();
if(_4a&&_4a.isTreeNode&&!(!this.showRoot&&_4a===this.rootNode)){
this.focusNode(_4a);
}
}
},_onHomeKey:function(){
var _4b=this._getRootOrFirstNode();
if(_4b){
this.focusNode(_4b);
}
},_onEndKey:function(_4c){
var _4d=this.rootNode;
while(_4d.isExpanded){
var c=_4d.getChildren();
_4d=c[c.length-1];
}
if(_4d&&_4d.isTreeNode){
this.focusNode(_4d);
}
},_onLetterKeyNav:function(_4f){
var _50=_4f.node,_51=_50,key=_4f.key;
do{
_50=this._getNextNode(_50);
if(!_50){
_50=this._getRootOrFirstNode();
}
}while(_50!==_51&&(_50.label.charAt(0).toLowerCase()!=key));
if(_50&&_50.isTreeNode){
if(_50!==_51){
this.focusNode(_50);
}
}
},_onClick:function(e){
var _54=e.target;
var _55=dijit.getEnclosingWidget(_54);
if(!_55||!_55.isTreeNode){
return;
}
if((this.openOnClick&&_55.isExpandable)||(_54==_55.expandoNode||_54==_55.expandoNodeText)){
if(_55.isExpandable){
this._onExpandoClick({node:_55});
}
}else{
this._publish("execute",{item:_55.item,node:_55});
this.onClick(_55.item,_55);
this.focusNode(_55);
}
dojo.stopEvent(e);
},_onDblClick:function(e){
var _57=e.target;
var _58=dijit.getEnclosingWidget(_57);
if(!_58||!_58.isTreeNode){
return;
}
if((this.openOnDblClick&&_58.isExpandable)||(_57==_58.expandoNode||_57==_58.expandoNodeText)){
if(_58.isExpandable){
this._onExpandoClick({node:_58});
}
}else{
this._publish("execute",{item:_58.item,node:_58});
this.onDblClick(_58.item,_58);
this.focusNode(_58);
}
dojo.stopEvent(e);
},_onExpandoClick:function(_59){
var _5a=_59.node;
this.focusNode(_5a);
if(_5a.isExpanded){
this._collapseNode(_5a);
}else{
this._expandNode(_5a);
}
},onClick:function(_5b,_5c){
},onDblClick:function(_5d,_5e){
},onOpen:function(_5f,_60){
},onClose:function(_61,_62){
},_getNextNode:function(_63){
if(_63.isExpandable&&_63.isExpanded&&_63.hasChildren()){
return _63.getChildren()[0];
}else{
while(_63&&_63.isTreeNode){
var _64=_63.getNextSibling();
if(_64){
return _64;
}
_63=_63.getParent();
}
return null;
}
},_getRootOrFirstNode:function(){
return this.showRoot?this.rootNode:this.rootNode.getChildren()[0];
},_collapseNode:function(_65){
if(_65.isExpandable){
if(_65.state=="LOADING"){
return;
}
_65.collapse();
this.onClose(_65.item,_65);
if(_65.item){
this._state(_65.item,false);
this._saveState();
}
}
},_expandNode:function(_66){
if(!_66.isExpandable){
return;
}
var _67=this.model,_68=_66.item;
switch(_66.state){
case "LOADING":
return;
case "UNCHECKED":
_66.markProcessing();
var _69=this;
_67.getChildren(_68,function(_6a){
_66.unmarkProcessing();
_66.setChildItems(_6a);
_69._expandNode(_66);
},function(err){
console.error(_69,": error loading root children: ",err);
});
break;
default:
_66.expand();
this.onOpen(_66.item,_66);
if(_68){
this._state(_68,true);
this._saveState();
}
}
},focusNode:function(_6c){
_6c.labelNode.focus();
},_onNodeFocus:function(_6d){
if(_6d){
if(_6d!=this.lastFocused){
this.lastFocused.setSelected(false);
}
_6d.setSelected(true);
this.lastFocused=_6d;
}
},_onNodeMouseEnter:function(_6e){
},_onNodeMouseLeave:function(_6f){
},_onItemChange:function(_70){
var _71=this.model,_72=_71.getIdentity(_70),_73=this._itemNodeMap[_72];
if(_73){
_73.setLabelNode(this.getLabel(_70));
_73._updateItemClasses(_70);
}
},_onItemChildrenChange:function(_74,_75){
var _76=this.model,_77=_76.getIdentity(_74),_78=this._itemNodeMap[_77];
if(_78){
_78.setChildItems(_75);
}
},_onItemDelete:function(_79){
var _7a=this.model,_7b=_7a.getIdentity(_79),_7c=this._itemNodeMap[_7b];
if(_7c){
var _7d=_7c.getParent();
if(_7d){
_7d.removeChild(_7c);
}
_7c.destroyRecursive();
delete this._itemNodeMap[_7b];
}
},_initState:function(){
if(this.persist){
var _7e=dojo.cookie(this.cookieName);
this._openedItemIds={};
if(_7e){
dojo.forEach(_7e.split(","),function(_7f){
this._openedItemIds[_7f]=true;
},this);
}
}
},_state:function(_80,_81){
if(!this.persist){
return false;
}
var id=this.model.getIdentity(_80);
if(arguments.length===1){
return this._openedItemIds[id];
}
if(_81){
this._openedItemIds[id]=true;
}else{
delete this._openedItemIds[id];
}
},_saveState:function(){
if(!this.persist){
return;
}
var ary=[];
for(var id in this._openedItemIds){
ary.push(id);
}
dojo.cookie(this.cookieName,ary.join(","),{expires:365});
},destroy:function(){
if(this.rootNode){
this.rootNode.destroyRecursive();
}
if(this.dndController&&!dojo.isString(this.dndController)){
this.dndController.destroy();
}
this.rootNode=null;
this.inherited(arguments);
},destroyRecursive:function(){
this.destroy();
},_createTreeNode:function(_85){
return new dijit._TreeNode(_85);
}});
dojo.require("dijit.tree.TreeStoreModel");
dojo.require("dijit.tree.ForestStoreModel");
}
