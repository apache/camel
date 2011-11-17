/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.RollingList"]){
dojo._hasResource["dojox.widget.RollingList"]=true;
dojo.provide("dojox.widget.RollingList");
dojo.experimental("dojox.widget.RollingList");
dojo.require("dijit._Templated");
dojo.require("dijit.layout.ContentPane");
dojo.require("dijit.layout._LayoutWidget");
dojo.require("dijit.Menu");
dojo.require("dojox.html.metrics");
dojo.require("dijit.form.Button");
dojo.require("dojo.i18n");
dojo.requireLocalization("dojox.widget","RollingList",null,"ROOT");
dojo.requireLocalization("dijit","common",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dojox.widget._RollingListPane",[dijit.layout.ContentPane,dijit._Templated],{templateString:"<div class=\"dojoxRollingListPane\"><table><tbody><tr><td dojoAttachPoint=\"containerNode\"></td></tr></tbody></div>",parentWidget:null,parentPane:null,store:null,items:null,query:null,queryOptions:null,_focusByNode:true,minWidth:0,_setContentAndScroll:function(_1,_2){
this._setContent(_1,_2);
this.parentWidget.scrollIntoView(this);
},_updateNodeWidth:function(n,_4){
n.style.width="";
var _5=dojo.marginBox(n).w;
if(_5<_4){
dojo.marginBox(n,{w:_4});
}
},_onMinWidthChange:function(v){
this._updateNodeWidth(this.domNode,v);
},_setMinWidthAttr:function(v){
if(v!==this.minWidth){
this.minWidth=v;
this._onMinWidthChange(v);
}
},startup:function(){
if(this._started){
return;
}
if(this.store&&this.store.getFeatures()["dojo.data.api.Notification"]){
window.setTimeout(dojo.hitch(this,function(){
this.connect(this.store,"onSet","_onSetItem");
this.connect(this.store,"onNew","_onNewItem");
this.connect(this.store,"onDelete","_onDeleteItem");
}),1);
}
this.connect(this.focusNode||this.domNode,"onkeypress","_focusKey");
this.parentWidget._updateClass(this.domNode,"Pane");
this.inherited(arguments);
this._onMinWidthChange(this.minWidth);
},_focusKey:function(e){
if(e.charOrCode==dojo.keys.BACKSPACE){
dojo.stopEvent(e);
return;
}else{
if(e.charOrCode==dojo.keys.LEFT_ARROW&&this.parentPane){
this.parentPane.focus();
this.parentWidget.scrollIntoView(this.parentPane);
}else{
if(e.charOrCode==dojo.keys.ENTER){
this.parentWidget._onExecute();
}
}
}
},focus:function(_9){
if(this.parentWidget._focusedPane!=this){
this.parentWidget._focusedPane=this;
this.parentWidget.scrollIntoView(this);
if(this._focusByNode&&(!this.parentWidget._savedFocus||_9)){
try{
(this.focusNode||this.domNode).focus();
}
catch(e){
}
}
}
},_loadCheck:function(){
if(!this._started){
var c=this.connect(this,"startup",function(){
this.disconnect(c);
this._loadCheck();
});
}
var _b=this.domNode&&this._isShown();
if((this.store||this.items)&&((this.refreshOnShow&&_b)||(!this.isLoaded&&_b))){
this._loadQuery();
}
},_loadQuery:function(){
this.isLoaded=false;
if(this.items){
this._setContentAndScroll(this.onLoadStart(),true);
window.setTimeout(dojo.hitch(this,"_doQuery"),1);
}else{
this._doQuery();
}
},_doLoadItems:function(_c,_d){
var _e=0,_f=this.store;
dojo.forEach(_c,function(_10){
if(!_f.isItemLoaded(_10)){
_e++;
}
});
if(_e===0){
_d();
}else{
var _11=function(_12){
_e--;
if((_e)===0){
_d();
}
};
dojo.forEach(_c,function(_13){
if(!_f.isItemLoaded(_13)){
_f.loadItem({item:_13,onItem:_11});
}
});
}
},_doQuery:function(){
var _14=this.parentWidget.preloadItems;
_14=(_14===true||(this.items&&this.items.length<=Number(_14)));
if(this.items&&_14){
this._doLoadItems(this.items,dojo.hitch(this,"onItems"));
}else{
if(this.items){
this.onItems();
}else{
this._setContentAndScroll(this.onFetchStart(),true);
this.store.fetch({query:this.query,onComplete:function(_15){
this.items=_15;
this.onItems();
},onError:function(e){
this._onError("Fetch",e);
},scope:this});
}
}
},_hasItem:function(_17){
var _18=this.items||[];
for(var i=0,_1a;(_1a=_18[i]);i++){
if(this.parentWidget._itemsMatch(_1a,_17)){
return true;
}
}
return false;
},_onSetItem:function(_1b,_1c,_1d,_1e){
if(this._hasItem(_1b)){
this._loadCheck(true);
}
},_onNewItem:function(_1f,_20){
var sel;
if((!_20&&!this.parentPane)||(_20&&this.parentPane&&this.parentPane._hasItem(_20.item)&&(sel=this.parentPane._getSelected())&&this.parentWidget._itemsMatch(sel.item,_20.item))){
this.items.push(_1f);
this._loadCheck(true);
}else{
if(_20&&this.parentPane&&this._hasItem(_20.item)){
this._loadCheck(true);
}
}
},_onDeleteItem:function(_22){
if(this._hasItem(_22)){
this.items=dojo.filter(this.items,function(i){
return (i!=_22);
});
this._loadCheck(true);
}
},onFetchStart:function(){
return this.loadingMessage;
},onFetchError:function(_24){
return this.errorMessage;
},onLoadStart:function(){
return this.loadingMessage;
},onLoadError:function(_25){
return this.errorMessage;
},onItems:function(){
this._onLoadHandler();
}});
dojo.declare("dojox.widget._RollingListGroupPane",[dojox.widget._RollingListPane],{templateString:"<div><div dojoAttachPoint=\"containerNode\"></div>"+"<div dojoAttachPoint=\"menuContainer\">"+"<div dojoAttachPoint=\"menuNode\"></div>"+"</div></div>",_menu:null,_loadCheck:function(){
var _26=this._isShown();
if((this.store||this.items)&&((this.refreshOnShow&&_26)||(!this.isLoaded&&_26))){
this._loadQuery();
}
},_setContent:function(_27){
if(!this._menu){
this.inherited(arguments);
}
},_onMinWidthChange:function(v){
if(!this._menu){
return;
}
var _29=dojo.marginBox(this.domNode).w;
var _2a=dojo.marginBox(this._menu.domNode).w;
this._updateNodeWidth(this._menu.domNode,v-(_29-_2a));
},onItems:function(){
var _2b,_2c=false;
if(this._menu){
_2b=this._getSelected();
this._menu.destroyRecursive();
}
this._menu=this._getMenu();
var _2d,_2e;
if(this.items.length){
dojo.forEach(this.items,function(_2f){
_2d=this.parentWidget._getMenuItemForItem(_2f,this);
if(_2d){
if(_2b&&this.parentWidget._itemsMatch(_2d.item,_2b.item)){
_2e=_2d;
}
this._menu.addChild(_2d);
}
},this);
}else{
_2d=this.parentWidget._getMenuItemForItem(null,this);
if(_2d){
this._menu.addChild(_2d);
}
}
if(_2e){
this._setSelected(_2e);
if((_2b&&!_2b.children&&_2e.children)||(_2b&&_2b.children&&!_2e.children)){
var _30=this.parentWidget._getPaneForItem(_2e.item,this,_2e.children);
if(_30){
this.parentWidget.addChild(_30,this.getIndexInParent()+1);
}else{
this.parentWidget._removeAfter(this);
this.parentWidget._onItemClick(null,this,_2e.item,_2e.children);
}
}
}else{
if(_2b){
this.parentWidget._removeAfter(this);
}
}
this.containerNode.innerHTML="";
this.containerNode.appendChild(this._menu.domNode);
this.parentWidget.scrollIntoView(this);
this._checkScrollConnection(true);
this.inherited(arguments);
this._onMinWidthChange(this.minWidth);
},_checkScrollConnection:function(_31){
var _32=this.store;
if(this._scrollConn){
this.disconnect(this._scrollConn);
}
delete this._scrollConn;
if(!dojo.every(this.items,function(i){
return _32.isItemLoaded(i);
})){
if(_31){
this._loadVisibleItems();
}
this._scrollConn=this.connect(this.domNode,"onscroll","_onScrollPane");
}
},startup:function(){
this.inherited(arguments);
this.parentWidget._updateClass(this.domNode,"GroupPane");
},focus:function(_34){
if(this._menu){
if(this._pendingFocus){
this.disconnect(this._pendingFocus);
}
delete this._pendingFocus;
var _35=this._menu.focusedChild;
if(!_35){
var _36=dojo.query(".dojoxRollingListItemSelected",this.domNode)[0];
if(_36){
_35=dijit.byNode(_36);
}
}
if(!_35){
_35=this._menu.getChildren()[0]||this._menu;
}
this._focusByNode=false;
if(_35.focusNode){
if(!this.parentWidget._savedFocus||_34){
try{
_35.focusNode.focus();
}
catch(e){
}
}
window.setTimeout(function(){
try{
dijit.scrollIntoView(_35.focusNode);
}
catch(e){
}
},1);
}else{
if(_35.focus){
if(!this.parentWidget._savedFocus||_34){
_35.focus();
}
}else{
this._focusByNode=true;
}
}
this.inherited(arguments);
}else{
if(!this._pendingFocus){
this._pendingFocus=this.connect(this,"onItems","focus");
}
}
},_getMenu:function(){
var _37=this;
var _38=new dijit.Menu({parentMenu:this.parentPane?this.parentPane._menu:null,onCancel:function(_39){
if(_37.parentPane){
_37.parentPane.focus(true);
}
},_moveToPopup:function(evt){
if(this.focusedChild&&!this.focusedChild.disabled){
this.focusedChild._onClick(evt);
}
}},this.menuNode);
this.connect(_38,"onItemClick",function(_3b,evt){
if(_3b.disabled){
return;
}
evt.alreadySelected=dojo.hasClass(_3b.domNode,"dojoxRollingListItemSelected");
if(evt.alreadySelected&&((evt.type=="keypress"&&evt.charOrCode!=dojo.keys.ENTER)||(evt.type=="internal"))){
var p=this.parentWidget.getChildren()[this.getIndexInParent()+1];
if(p){
p.focus(true);
this.parentWidget.scrollIntoView(p);
}
}else{
this._setSelected(_3b,_38);
this.parentWidget._onItemClick(evt,this,_3b.item,_3b.children);
if(evt.type=="keypress"&&evt.charOrCode==dojo.keys.ENTER){
this.parentWidget._onExecute();
}
}
});
if(!_38._started){
_38.startup();
}
return _38;
},_onScrollPane:function(){
if(this._visibleLoadPending){
window.clearTimeout(this._visibleLoadPending);
}
this._visibleLoadPending=window.setTimeout(dojo.hitch(this,"_loadVisibleItems"),500);
},_layoutHack:function(){
if(dojo.isFF==2&&!this._layoutHackHandle){
var _3e=this.domNode;
var old=_3e.style.opacity;
_3e.style.opacity="0.999";
this._layoutHackHandle=setTimeout(dojo.hitch(this,function(){
this._layoutHackHandle=null;
_3e.style.opacity=old;
}),0);
}
},_loadVisibleItems:function(){
delete this._visibleLoadPending;
var _40=this._menu;
if(!_40){
return;
}
var _41=_40.getChildren();
if(!_41||!_41.length){
return;
}
var _42=function(n,m,pb){
var s=dojo.getComputedStyle(n);
var r=0;
if(m){
r+=dojo._getMarginExtents(n,s).t;
}
if(pb){
r+=dojo._getPadBorderExtents(n,s).t;
}
return r;
};
var _48=_42(this.domNode,false,true)+_42(this.containerNode,true,true)+_42(_40.domNode,true,true)+_42(_41[0].domNode,true,false);
var h=dojo.contentBox(this.domNode).h;
var _4a=this.domNode.scrollTop-_48-(h/2);
var _4b=_4a+(3*h/2);
var _4c=dojo.filter(_41,function(c){
var cnt=c.domNode.offsetTop;
var s=c.store;
var i=c.item;
return (cnt>=_4a&&cnt<=_4b&&!s.isItemLoaded(i));
});
var _51=dojo.map(_4c,function(c){
return c.item;
});
var _53=dojo.hitch(this,function(){
var _54=this._getSelected();
var _55;
dojo.forEach(_51,function(_56,idx){
var _58=this.parentWidget._getMenuItemForItem(_56,this);
var _59=_4c[idx];
var _5a=_59.getIndexInParent();
_40.removeChild(_59);
if(_58){
if(_54&&this.parentWidget._itemsMatch(_58.item,_54.item)){
_55=_58;
}
_40.addChild(_58,_5a);
if(_40.focusedChild==_59){
_40.focusChild(_58);
}
}
_59.destroy();
},this);
this._checkScrollConnection(false);
this._layoutHack();
});
this._doLoadItems(_51,_53);
},_getSelected:function(_5b){
if(!_5b){
_5b=this._menu;
}
if(_5b){
var _5c=this._menu.getChildren();
for(var i=0,_5e;(_5e=_5c[i]);i++){
if(dojo.hasClass(_5e.domNode,"dojoxRollingListItemSelected")){
return _5e;
}
}
}
return null;
},_setSelected:function(_5f,_60){
if(!_60){
_60=this._menu;
}
if(_60){
dojo.forEach(_60.getChildren(),function(i){
this.parentWidget._updateClass(i.domNode,"Item",{"Selected":(_5f&&(i==_5f&&!i.disabled))});
},this);
}
},destroy:function(){
if(this._layoutHackHandle){
clearTimeout(this._layoutHackHandle);
}
this.inherited(arguments);
}});
dojo.declare("dojox.widget.RollingList",[dijit._Widget,dijit._Templated,dijit._Container],{templateString:"<div class=\"dojoxRollingList ${className}\"\n\t><div class=\"dojoxRollingListContainer\" dojoAttachPoint=\"containerNode\" dojoAttachEvent=\"onkeypress:_onKey\"\n\t></div\n\t><div class=\"dojoxRollingListButtons\" dojoAttachPoint=\"buttonsNode\"\n        ><button dojoType=\"dijit.form.Button\" dojoAttachPoint=\"okButton\"\n\t\t\t\tdojoAttachEvent=\"onClick:_onExecute\">${okButtonLabel}</button\n        ><button dojoType=\"dijit.form.Button\" dojoAttachPoint=\"cancelButton\"\n\t\t\t\tdojoAttachEvent=\"onClick:_onCancel\">${cancelButtonLabel}</button\n\t></div\n></div>\n",widgetsInTemplate:true,className:"",store:null,query:null,queryOptions:null,childrenAttrs:["children"],parentAttr:"",value:null,executeOnDblClick:true,preloadItems:false,showButtons:false,okButtonLabel:"",cancelButtonLabel:"",minPaneWidth:0,postMixInProperties:function(){
this.inherited(arguments);
var loc=dojo.i18n.getLocalization("dijit","common");
this.okButtonLabel=this.okButtonLabel||loc.buttonOk;
this.cancelButtonLabel=this.cancelButtonLabel||loc.buttonCancel;
},_setShowButtonsAttr:function(_63){
var _64=false;
if((this.showButtons!=_63&&this._started)||(this.showButtons==_63&&!this.started)){
_64=true;
}
dojo.toggleClass(this.domNode,"dojoxRollingListButtonsHidden",!_63);
this.showButtons=_63;
if(_64){
if(this._started){
this.layout();
}else{
window.setTimeout(dojo.hitch(this,"layout"),0);
}
}
},_itemsMatch:function(_65,_66){
if(!_65&&!_66){
return true;
}else{
if(!_65||!_66){
return false;
}
}
return (_65==_66||(this._isIdentity&&this.store.getIdentity(_65)==this.store.getIdentity(_66)));
},_removeAfter:function(idx){
if(typeof idx!="number"){
idx=this.getIndexOfChild(idx);
}
if(idx>=0){
dojo.forEach(this.getChildren(),function(c,i){
if(i>idx){
this.removeChild(c);
c.destroyRecursive();
}
},this);
}
var _6a=this.getChildren(),_6b=_6a[_6a.length-1];
var _6c=null;
while(_6b&&!_6c){
var val=_6b._getSelected?_6b._getSelected():null;
if(val){
_6c=val.item;
}
_6b=_6b.parentPane;
}
if(!this._setInProgress){
this._setValue(_6c);
}
},addChild:function(_6e,_6f){
if(_6f>0){
this._removeAfter(_6f-1);
}
this.inherited(arguments);
if(!_6e._started){
_6e.startup();
}
_6e.attr("minWidth",this.minPaneWidth);
this.layout();
if(!this._savedFocus){
_6e.focus();
}
},_setMinPaneWidthAttr:function(_70){
if(_70!==this.minPaneWidth){
this.minPaneWidth=_70;
dojo.forEach(this.getChildren(),function(c){
c.attr("minWidth",_70);
});
}
},_updateClass:function(_72,_73,_74){
if(!this._declaredClasses){
this._declaredClasses=("dojoxRollingList "+this.className).split(" ");
}
dojo.forEach(this._declaredClasses,function(c){
if(c){
dojo.addClass(_72,c+_73);
for(var k in _74||{}){
dojo.toggleClass(_72,c+_73+k,_74[k]);
}
dojo.toggleClass(_72,c+_73+"FocusSelected",(dojo.hasClass(_72,c+_73+"Focus")&&dojo.hasClass(_72,c+_73+"Selected")));
dojo.toggleClass(_72,c+_73+"HoverSelected",(dojo.hasClass(_72,c+_73+"Hover")&&dojo.hasClass(_72,c+_73+"Selected")));
}
});
},scrollIntoView:function(_77){
if(this._scrollingTimeout){
window.clearTimeout(this._scrollingTimeout);
}
delete this._scrollingTimeout;
this._scrollingTimeout=window.setTimeout(dojo.hitch(this,function(){
if(_77.domNode){
dijit.scrollIntoView(_77.domNode);
}
delete this._scrollingTimeout;
return;
}),1);
},resize:function(_78){
dijit.layout._LayoutWidget.prototype.resize.call(this,_78);
},layout:function(){
var _79=this.getChildren();
if(this._contentBox){
var bn=this.buttonsNode;
var _7b=this._contentBox.h-dojo.marginBox(bn).h-dojox.html.metrics.getScrollbar().h;
dojo.forEach(_79,function(c){
dojo.marginBox(c.domNode,{h:_7b});
});
}
if(this._focusedPane){
var foc=this._focusedPane;
delete this._focusedPane;
if(!this._savedFocus){
foc.focus();
}
}else{
if(_79&&_79.length){
if(!this._savedFocus){
_79[0].focus();
}
}
}
},_onChange:function(_7e){
this.onChange(_7e);
},_setValue:function(_7f){
delete this._setInProgress;
if(!this._itemsMatch(this.value,_7f)){
this.value=_7f;
this._onChange(_7f);
}
},_setValueAttr:function(_80){
if(this._itemsMatch(this.value,_80)&&!_80){
return;
}
if(this._setInProgress&&this._setInProgress===_80){
return;
}
this._setInProgress=_80;
if(!_80||!this.store.isItem(_80)){
var _81=this.getChildren()[0];
_81._setSelected(null);
this._onItemClick(null,_81,null,null);
return;
}
var _82=dojo.hitch(this,function(_83,_84){
var _85=this.store,id;
if(this.parentAttr&&_85.getFeatures()["dojo.data.api.Identity"]&&((id=this.store.getValue(_83,this.parentAttr))||id==="")){
var cb=function(i){
if(_85.getIdentity(i)==_85.getIdentity(_83)){
_84(null);
}else{
_84([i]);
}
};
if(id===""){
_84(null);
}else{
if(typeof id=="string"){
_85.fetchItemByIdentity({identity:id,onItem:cb});
}else{
if(_85.isItem(id)){
cb(id);
}
}
}
}else{
var _89=this.childrenAttrs.length;
var _8a=[];
dojo.forEach(this.childrenAttrs,function(_8b){
var q={};
q[_8b]=_83;
_85.fetch({query:q,scope:this,onComplete:function(_8d){
if(this._setInProgress!==_80){
return;
}
_8a=_8a.concat(_8d);
_89--;
if(_89===0){
_84(_8a);
}
}});
},this);
}
});
var _8e=dojo.hitch(this,function(_8f,idx){
var set=_8f[idx];
var _92=this.getChildren()[idx];
var _93;
if(set&&_92){
var fx=dojo.hitch(this,function(){
if(_93){
this.disconnect(_93);
}
delete _93;
if(this._setInProgress!==_80){
return;
}
var _95=dojo.filter(_92._menu.getChildren(),function(i){
return this._itemsMatch(i.item,set);
},this)[0];
if(_95){
idx++;
_92._menu.onItemClick(_95,{type:"internal",stopPropagation:function(){
},preventDefault:function(){
}});
if(_8f[idx]){
_8e(_8f,idx);
}else{
this._setValue(set);
this.onItemClick(set,_92,this.getChildItems(set));
}
}
});
if(!_92.isLoaded){
_93=this.connect(_92,"onLoad",fx);
}else{
fx();
}
}else{
if(idx===0){
this.attr("value",null);
}
}
});
var _97=[];
var _98=dojo.hitch(this,function(_99){
if(_99&&_99.length){
_97.push(_99[0]);
_82(_99[0],_98);
}else{
if(!_99){
_97.pop();
}
_97.reverse();
_8e(_97,0);
}
});
var ns=this.domNode.style;
if(ns.display=="none"||ns.visibility=="hidden"){
this._setValue(_80);
}else{
if(!this._itemsMatch(_80,this._visibleItem)){
_98([_80]);
}
}
},_onItemClick:function(evt,_9c,_9d,_9e){
if(evt){
var _9f=this._getPaneForItem(_9d,_9c,_9e);
var _a0=(evt.type=="click"&&evt.alreadySelected);
if(_a0&&_9f){
this._removeAfter(_9c.getIndexInParent()+1);
var _a1=_9c.getNextSibling();
if(_a1&&_a1._setSelected){
_a1._setSelected(null);
}
this.scrollIntoView(_a1);
}else{
if(_9f){
this.addChild(_9f,_9c.getIndexInParent()+1);
if(this._savedFocus){
_9f.focus(true);
}
}else{
this._removeAfter(_9c);
this.scrollIntoView(_9c);
}
}
}else{
if(_9c){
this._removeAfter(_9c);
this.scrollIntoView(_9c);
}
}
if(!evt||evt.type!="internal"){
this._setValue(_9d);
this.onItemClick(_9d,_9c,_9e);
}
this._visibleItem=_9d;
},_getPaneForItem:function(_a2,_a3,_a4){
var ret=this.getPaneForItem(_a2,_a3,_a4);
ret.store=this.store;
ret.parentWidget=this;
ret.parentPane=_a3||null;
if(!_a2){
ret.query=this.query;
ret.queryOptions=this.queryOptions;
}else{
if(_a4){
ret.items=_a4;
}else{
ret.items=[_a2];
}
}
return ret;
},_getMenuItemForItem:function(_a6,_a7){
var _a8=this.store;
if(!_a6||!_a8||!_a8.isItem(_a6)){
var i=new dijit.MenuItem({label:dojo.i18n.getLocalization("dojox.widget","RollingList",this.lang).empty,disabled:true,iconClass:"dojoxEmpty",focus:function(){
}});
this._updateClass(i.domNode,"Item");
return i;
}else{
var _aa=_a8.isItemLoaded(_a6);
var _ab=_aa?this.getChildItems(_a6):undefined;
var _ac;
if(_ab){
_ac=this.getMenuItemForItem(_a6,_a7,_ab);
_ac.children=_ab;
this._updateClass(_ac.domNode,"Item",{"Expanding":true});
if(!_ac._started){
var c=_ac.connect(_ac,"startup",function(){
this.disconnect(c);
dojo.style(this.arrowWrapper,"display","");
});
}else{
dojo.style(_ac.arrowWrapper,"display","");
}
}else{
_ac=this.getMenuItemForItem(_a6,_a7,null);
if(_aa){
this._updateClass(_ac.domNode,"Item",{"Single":true});
}else{
this._updateClass(_ac.domNode,"Item",{"Unloaded":true});
_ac.attr("disabled",true);
}
}
_ac.store=this.store;
_ac.item=_a6;
if(!_ac.label){
_ac.attr("label",this.store.getLabel(_a6));
}
if(_ac.focusNode){
var _ae=this;
_ac.focus=function(){
if(!this.disabled){
try{
this.focusNode.focus();
}
catch(e){
}
}
};
_ac.connect(_ac.focusNode,"onmouseenter",function(){
if(!this.disabled){
_ae._updateClass(this.domNode,"Item",{"Hover":true});
}
});
_ac.connect(_ac.focusNode,"onmouseleave",function(){
if(!this.disabled){
_ae._updateClass(this.domNode,"Item",{"Hover":false});
}
});
_ac.connect(_ac.focusNode,"blur",function(){
_ae._updateClass(this.domNode,"Item",{"Focus":false,"Hover":false});
});
_ac.connect(_ac.focusNode,"focus",function(){
_ae._updateClass(this.domNode,"Item",{"Focus":true});
_ae._focusedPane=_a7;
});
if(this.executeOnDblClick){
_ac.connect(_ac.focusNode,"ondblclick",function(){
_ae._onExecute();
});
}
}
return _ac;
}
},_setStore:function(_af){
if(_af===this.store&&this._started){
return;
}
this.store=_af;
this._isIdentity=_af.getFeatures()["dojo.data.api.Identity"];
var _b0=this._getPaneForItem();
this.addChild(_b0,0);
},_onKey:function(e){
if(e.charOrCode==dojo.keys.BACKSPACE){
dojo.stopEvent(e);
return;
}else{
if(e.charOrCode==dojo.keys.ESCAPE&&this._savedFocus){
try{
dijit.focus(this._savedFocus);
}
catch(e){
}
dojo.stopEvent(e);
return;
}else{
if(e.charOrCode==dojo.keys.LEFT_ARROW||e.charOrCode==dojo.keys.RIGHT_ARROW){
dojo.stopEvent(e);
return;
}
}
}
},_resetValue:function(){
this.attr("value",this._lastExecutedValue);
},_onCancel:function(){
this._resetValue();
this.onCancel();
},_onExecute:function(){
this._lastExecutedValue=this.attr("value");
this.onExecute();
},focus:function(){
var _b2=this._savedFocus;
this._savedFocus=dijit.getFocus(this);
if(!this._savedFocus.node){
delete this._savedFocus;
}
if(!this._focusedPane){
var _b3=this.getChildren()[0];
if(_b3&&!_b2){
_b3.focus(true);
}
}else{
this._savedFocus=dijit.getFocus(this);
var foc=this._focusedPane;
delete this._focusedPane;
if(!_b2){
foc.focus(true);
}
}
},handleKey:function(e){
if(e.charOrCode==dojo.keys.DOWN_ARROW){
delete this._savedFocus;
this.focus();
return false;
}else{
if(e.charOrCode==dojo.keys.ESCAPE){
this._onCancel();
return false;
}
}
return true;
},_updateChildClasses:function(){
var _b6=this.getChildren();
var _b7=_b6.length;
dojo.forEach(_b6,function(c,idx){
dojo.toggleClass(c.domNode,"dojoxRollingListPaneCurrentChild",(idx==(_b7-1)));
dojo.toggleClass(c.domNode,"dojoxRollingListPaneCurrentSelected",(idx==(_b7-2)));
});
},startup:function(){
if(this._started){
return;
}
if(!this.getParent||!this.getParent()){
this.resize();
this.connect(dojo.global,"onresize","resize");
}
this.connect(this,"addChild","_updateChildClasses");
this.connect(this,"removeChild","_updateChildClasses");
this._setStore(this.store);
this.attr("showButtons",this.showButtons);
this.inherited(arguments);
this._lastExecutedValue=this.attr("value");
},getChildItems:function(_ba){
var _bb,_bc=this.store;
dojo.forEach(this.childrenAttrs,function(_bd){
var _be=_bc.getValues(_ba,_bd);
if(_be&&_be.length){
_bb=(_bb||[]).concat(_be);
}
});
return _bb;
},getMenuItemForItem:function(_bf,_c0,_c1){
return new dijit.MenuItem({});
},getPaneForItem:function(_c2,_c3,_c4){
if(!_c2||_c4){
return new dojox.widget._RollingListGroupPane({});
}else{
return null;
}
},onItemClick:function(_c5,_c6,_c7){
},onExecute:function(){
},onCancel:function(){
},onChange:function(_c8){
}});
}
