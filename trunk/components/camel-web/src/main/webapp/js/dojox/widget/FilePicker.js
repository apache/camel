/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.FilePicker"]){
dojo._hasResource["dojox.widget.FilePicker"]=true;
dojo.provide("dojox.widget.FilePicker");
dojo.require("dojox.widget.RollingList");
dojo.require("dojo.i18n");
dojo.requireLocalization("dojox.widget","FilePicker",null,"ROOT");
dojo.declare("dojox.widget._FileInfoPane",[dojox.widget._RollingListPane],{templateString:"",templateString:"<div class=\"dojoxFileInfoPane\">\n\t<table>\n\t\t<tbody>\n\t\t\t<tr>\n\t\t\t\t<td class=\"dojoxFileInfoLabel dojoxFileInfoNameLabel\">${_messages.name}</td>\n\t\t\t\t<td class=\"dojoxFileInfoName\" dojoAttachPoint=\"nameNode\"></td>\n\t\t\t</tr>\n\t\t\t<tr>\n\t\t\t\t<td class=\"dojoxFileInfoLabel dojoxFileInfoPathLabel\">${_messages.path}</td>\n\t\t\t\t<td class=\"dojoxFileInfoPath\" dojoAttachPoint=\"pathNode\"></td>\n\t\t\t</tr>\n\t\t\t<tr>\n\t\t\t\t<td class=\"dojoxFileInfoLabel dojoxFileInfoSizeLabel\">${_messages.size}</td>\n\t\t\t\t<td class=\"dojoxFileInfoSize\" dojoAttachPoint=\"sizeNode\"></td>\n\t\t\t</tr>\n\t\t</tbody>\n\t</table>\n\t<div dojoAttachPoint=\"containerNode\" style=\"display:none;\"></div>\n</div>\n",postMixInProperties:function(){
this._messages=dojo.i18n.getLocalization("dojox.widget","FilePicker",this.lang);
this.inherited(arguments);
},onItems:function(){
var _1=this.store,_2=this.items[0];
if(!_2){
this._onError("Load",new Error("No item defined"));
}else{
this.nameNode.innerHTML=_1.getLabel(_2);
this.pathNode.innerHTML=_1.getIdentity(_2);
this.sizeNode.innerHTML=_1.getValue(_2,"size");
this.parentWidget.scrollIntoView(this);
this.inherited(arguments);
}
}});
dojo.declare("dojox.widget.FilePicker",dojox.widget.RollingList,{className:"dojoxFilePicker",pathSeparator:"",topDir:"",parentAttr:"parentDir",pathAttr:"path",preloadItems:50,selectDirectories:true,selectFiles:true,_itemsMatch:function(_3,_4){
if(!_3&&!_4){
return true;
}else{
if(!_3||!_4){
return false;
}else{
if(_3==_4){
return true;
}else{
if(this._isIdentity){
var _5=[this.store.getIdentity(_3),this.store.getIdentity(_4)];
dojo.forEach(_5,function(i,_7){
if(i.lastIndexOf(this.pathSeparator)==(i.length-1)){
_5[_7]=i.substring(0,i.length-1);
}else{
}
},this);
return (_5[0]==_5[1]);
}
}
}
}
return false;
},startup:function(){
if(this._started){
return;
}
this.inherited(arguments);
var _8,_9=this.getChildren()[0];
var _a=dojo.hitch(this,function(){
if(_8){
this.disconnect(_8);
}
delete _8;
var _b=_9.items[0];
if(_b){
var _c=this.store;
var _d=_c.getValue(_b,this.parentAttr);
var _e=_c.getValue(_b,this.pathAttr);
this.pathSeparator=this.pathSeparator||_c.pathSeparator;
if(!this.pathSeparator){
this.pathSeparator=_e.substring(_d.length,_d.length+1);
}
if(!this.topDir){
this.topDir=_d;
if(this.topDir.lastIndexOf(this.pathSeparator)!=(this.topDir.length-1)){
this.topDir+=this.pathSeparator;
}
}
}
});
if(!this.pathSeparator||!this.topDir){
if(!_9.items){
_8=this.connect(_9,"onItems",_a);
}else{
_a();
}
}
},getChildItems:function(_f){
var ret=this.inherited(arguments);
if(!ret&&this.store.getValue(_f,"directory")){
ret=[];
}
return ret;
},getMenuItemForItem:function(_11,_12,_13){
var _14={iconClass:"dojoxDirectoryItemIcon"};
if(!this.store.getValue(_11,"directory")){
_14.iconClass="dojoxFileItemIcon";
var l=this.store.getLabel(_11),idx=l.lastIndexOf(".");
if(idx>=0){
_14.iconClass+=" dojoxFileItemIcon_"+l.substring(idx+1);
}
if(!this.selectFiles){
_14.disabled=true;
}
}
var ret=new dijit.MenuItem(_14);
return ret;
},getPaneForItem:function(_18,_19,_1a){
var ret=null;
if(!_18||(this.store.isItem(_18)&&this.store.getValue(_18,"directory"))){
ret=new dojox.widget._RollingListGroupPane({});
}else{
if(this.store.isItem(_18)&&!this.store.getValue(_18,"directory")){
ret=new dojox.widget._FileInfoPane({});
}
}
return ret;
},_setPathValueAttr:function(_1c,_1d,_1e){
if(!_1c){
this.attr("value",null);
return;
}
if(_1c.lastIndexOf(this.pathSeparator)==(_1c.length-1)){
_1c=_1c.substring(0,_1c.length-1);
}
this.store.fetchItemByIdentity({identity:_1c,onItem:function(v){
if(_1d){
this._lastExecutedValue=v;
}
this.attr("value",v);
if(_1e){
_1e();
}
},scope:this});
},_getPathValueAttr:function(val){
if(!val){
val=this.value;
}
if(val&&this.store.isItem(val)){
return this.store.getValue(val,this.pathAttr);
}else{
return "";
}
},_setValue:function(_21){
delete this._setInProgress;
var _22=this.store;
if(_21&&_22.isItem(_21)){
var _23=this.store.getValue(_21,"directory");
if((_23&&!this.selectDirectories)||(!_23&&!this.selectFiles)){
return;
}
}else{
_21=null;
}
if(!this._itemsMatch(this.value,_21)){
this.value=_21;
this._onChange(_21);
}
}});
}
