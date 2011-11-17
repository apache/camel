/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.ItemExplorer"]){
dojo._hasResource["dojox.data.ItemExplorer"]=true;
dojo.provide("dojox.data.ItemExplorer");
dojo.require("dijit.Tree");
dojo.require("dijit.Dialog");
dojo.require("dijit.Menu");
dojo.require("dijit.form.ValidationTextBox");
dojo.require("dijit.form.Textarea");
dojo.require("dijit.form.Button");
dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form.FilteringSelect");
(function(){
var _1=function(_2,_3,_4){
var _5=_2.getValues(_3,_4);
if(_5.length<2){
_5=_2.getValue(_3,_4);
}
return _5;
};
dojo.declare("dojox.data.ItemExplorer",dijit.Tree,{useSelect:false,refSelectSearchAttr:null,constructor:function(_6){
dojo.mixin(this,_6);
var _7=this;
var _8={};
var _9=this.rootModelNode={value:_8,id:"root"};
this._modelNodeIdMap={};
this._modelNodePropMap={};
var _a=1;
this.model={getRoot:function(_b){
_b(_9);
},mayHaveChildren:function(_c){
return _c.value&&typeof _c.value=="object"&&!(_c.value instanceof Date);
},getChildren:function(_d,_e,_f){
var _10,_11,_12=_d.value;
var _13=[];
if(_12==_8){
_e([]);
return;
}
var _14=_7.store&&_7.store.isItem(_12,true);
if(_14&&!_7.store.isItemLoaded(_12)){
_7.store.loadItem({item:_12,onItem:function(_15){
_12=_15;
_16();
}});
}else{
_16();
}
function _16(){
if(_14){
_10=_7.store.getAttributes(_12);
_11=_12;
}else{
if(_12&&typeof _12=="object"){
_11=_d.value;
_10=[];
for(var i in _12){
if(_12.hasOwnProperty(i)&&i!="__id"&&i!="__clientId"){
_10.push(i);
}
}
}
}
if(_10){
for(var key,k=0;key=_10[k++];){
_13.push({property:key,value:_14?_1(_7.store,_12,key):_12[key],parent:_11});
}
_13.push({addNew:true,parent:_11,parentNode:_d});
}
_e(_13);
};
},getIdentity:function(_1a){
if(!_1a.id){
if(_1a.addNew){
_1a.property="--addNew";
}
_1a.id=_a++;
if(_7.store){
if(_7.store.isItem(_1a.value)){
var _1b=_7.store.getIdentity(_1a.value);
(_7._modelNodeIdMap[_1b]=_7._modelNodeIdMap[_1b]||[]).push(_1a);
}
if(_1a.parent){
_1b=_7.store.getIdentity(_1a.parent)+"."+_1a.property;
(_7._modelNodePropMap[_1b]=_7._modelNodePropMap[_1b]||[]).push(_1a);
}
}
}
return _1a.id;
},getLabel:function(_1c){
return _1c===_9?"Object Properties":_1c.addNew?(_1c.parent instanceof Array?"Add new value":"Add new property"):_1c.property+": "+(_1c.value instanceof Array?"("+_1c.value.length+" elements)":_1c.value);
},onChildrenChange:function(_1d){
},onChange:function(_1e){
}};
},postCreate:function(){
this.inherited(arguments);
dojo.connect(this,"onClick",function(_1f,_20){
this.lastFocused=_20;
if(_1f.addNew){
this._addProperty();
}else{
this._editProperty();
}
});
var _21=new dijit.Menu({targetNodeIds:[this.rootNode.domNode],id:"contextMenu"});
dojo.connect(_21,"_openMyself",this,function(e){
var _23=dijit.getEnclosingWidget(e.target);
if(_23){
var _24=_23.item;
if(this.store.isItem(_24.value,true)&&!_24.parent){
_21.getChildren().forEach(function(_25){
_25.attr("disabled",(_25.label!="Add"));
});
this.lastFocused=_23;
}else{
if(_24.value&&typeof _24.value=="object"&&!(_24.value instanceof Date)){
_21.getChildren().forEach(function(_26){
_26.attr("disabled",(_26.label!="Add")&&(_26.label!="Delete"));
});
this.lastFocused=_23;
}else{
if(_24.property&&dojo.indexOf(this.store.getIdentityAttributes(),_24.property)>=0){
this.focusNode(_23);
alert("Cannot modify an Identifier node.");
}else{
if(_24.addNew){
this.focusNode(_23);
}else{
_21.getChildren().forEach(function(_27){
_27.attr("disabled",(_27.label!="Edit")&&(_27.label!="Delete"));
});
this.lastFocused=_23;
}
}
}
}
}
});
_21.addChild(new dijit.MenuItem({label:"Add",onClick:dojo.hitch(this,"_addProperty")}));
_21.addChild(new dijit.MenuItem({label:"Edit",onClick:dojo.hitch(this,"_editProperty")}));
_21.addChild(new dijit.MenuItem({label:"Delete",onClick:dojo.hitch(this,"_destroyProperty")}));
_21.startup();
},store:null,setStore:function(_28){
this.store=_28;
var _29=this;
if(this._editDialog){
this._editDialog.destroyRecursive();
delete this._editDialog;
}
dojo.connect(_28,"onSet",function(_2a,_2b,_2c,_2d){
var _2e,i,_30=_29.store.getIdentity(_2a);
_2e=_29._modelNodeIdMap[_30];
if(_2e&&(_2c===undefined||_2d===undefined||_2c instanceof Array||_2d instanceof Array||typeof _2c=="object"||typeof _2d=="object")){
for(i=0;i<_2e.length;i++){
(function(_31){
_29.model.getChildren(_31,function(_32){
_29.model.onChildrenChange(_31,_32);
});
})(_2e[i]);
}
}
_2e=_29._modelNodePropMap[_30+"."+_2b];
if(_2e){
for(i=0;i<_2e.length;i++){
_2e[i].value=_2d;
_29.model.onChange(_2e[i]);
}
}
});
this.rootNode.setChildItems([]);
},setItem:function(_33){
(this._modelNodeIdMap={})[this.store.getIdentity(_33)]=[this.rootModelNode];
this._modelNodePropMap={};
this.rootModelNode.value=_33;
var _34=this;
this.model.getChildren(this.rootModelNode,function(_35){
_34.rootNode.setChildItems(_35);
});
},refreshItem:function(){
this.setItem(this.rootModelNode.value);
},_createEditDialog:function(){
this._editDialog=new dijit.Dialog({title:"Edit Property",execute:dojo.hitch(this,"_updateItem"),preload:true});
this._editDialog.placeAt(dojo.body());
this._editDialog.startup();
var _36=dojo.doc.createElement("div");
var _37=dojo.doc.createElement("label");
dojo.attr(_37,"for","property");
dojo.style(_37,"fontWeight","bold");
dojo.attr(_37,"innerHTML","Property:");
_36.appendChild(_37);
var _38=new dijit.form.ValidationTextBox({name:"property",value:"",required:true,disabled:true}).placeAt(_36);
_36.appendChild(dojo.doc.createElement("br"));
_36.appendChild(dojo.doc.createElement("br"));
var _39=new dijit.form.RadioButton({name:"itemType",value:"value",onClick:dojo.hitch(this,function(){
this._enableFields("value");
})}).placeAt(_36);
var _3a=dojo.doc.createElement("label");
dojo.attr(_3a,"for","value");
dojo.attr(_3a,"innerHTML","Value (JSON):");
_36.appendChild(_3a);
var _3b=dojo.doc.createElement("div");
dojo.addClass(_3b,"value");
var _3c=new dijit.form.Textarea({name:"jsonVal"}).placeAt(_3b);
_36.appendChild(_3b);
var _3d=new dijit.form.RadioButton({name:"itemType",value:"reference",onClick:dojo.hitch(this,function(){
this._enableFields("reference");
})}).placeAt(_36);
var _3e=dojo.doc.createElement("label");
dojo.attr(_3e,"for","_reference");
dojo.attr(_3e,"innerHTML","Reference (ID):");
_36.appendChild(_3e);
_36.appendChild(dojo.doc.createElement("br"));
var _3f=dojo.doc.createElement("div");
dojo.addClass(_3f,"reference");
if(this.useSelect){
var _40=new dijit.form.FilteringSelect({name:"_reference",store:this.store,searchAttr:this.refSelectSearchAttr||this.store.getIdentityAttributes()[0],required:false,value:null,pageSize:10}).placeAt(_3f);
}else{
var _41=new dijit.form.ValidationTextBox({name:"_reference",value:"",promptMessage:"Enter the ID of the item to reference",isValid:dojo.hitch(this,function(_42){
return true;
})}).placeAt(_3f);
}
_36.appendChild(_3f);
_36.appendChild(dojo.doc.createElement("br"));
_36.appendChild(dojo.doc.createElement("br"));
var _43=document.createElement("div");
_43.setAttribute("dir","rtl");
var _44=new dijit.form.Button({type:"reset",label:"Cancel"}).placeAt(_43);
_44.onClick=dojo.hitch(this._editDialog,"onCancel");
var _45=new dijit.form.Button({type:"submit",label:"OK"}).placeAt(_43);
_36.appendChild(_43);
this._editDialog.attr("content",_36);
},_enableFields:function(_46){
switch(_46){
case "reference":
dojo.query(".value [widgetId]",this._editDialog.containerNode).forEach(function(_47){
dijit.getEnclosingWidget(_47).attr("disabled",true);
});
dojo.query(".reference [widgetId]",this._editDialog.containerNode).forEach(function(_48){
dijit.getEnclosingWidget(_48).attr("disabled",false);
});
break;
case "value":
dojo.query(".value [widgetId]",this._editDialog.containerNode).forEach(function(_49){
dijit.getEnclosingWidget(_49).attr("disabled",false);
});
dojo.query(".reference [widgetId]",this._editDialog.containerNode).forEach(function(_4a){
dijit.getEnclosingWidget(_4a).attr("disabled",true);
});
break;
}
},_updateItem:function(_4b){
var _4c,_4d,val,_4f,_50=this._editDialog.attr("title")=="Edit Property";
var _51=this._editDialog;
var _52=this.store;
function _53(){
try{
var _54,_55=[];
var _56=_4b.property;
if(_50){
while(!_52.isItem(_4d.parent,true)){
_4c=_4c.getParent();
_55.push(_4d.property);
_4d=_4c.item;
}
if(_55.length==0){
_52.setValue(_4d.parent,_4d.property,val);
}else{
_4f=_1(_52,_4d.parent,_4d.property);
if(_4f instanceof Array){
_4f=_4f.concat();
}
_54=_4f;
while(_55.length>1){
_54=_54[_55.pop()];
}
_54[_55]=val;
_52.setValue(_4d.parent,_4d.property,_4f);
}
}else{
if(_52.isItem(_57,true)){
if(!_52.isItemLoaded(_57)){
_52.loadItem({item:_57,onItem:function(_58){
if(_58 instanceof Array){
_56=_58.length;
}
_52.setValue(_58,_56,val);
}});
}else{
if(_57 instanceof Array){
_56=_57.length;
}
_52.setValue(_57,_56,val);
}
}else{
if(_4d.value instanceof Array){
_55.push(_4d.value.length);
}else{
_55.push(_4b.property);
}
while(!_52.isItem(_4d.parent,true)){
_4c=_4c.getParent();
_55.push(_4d.property);
_4d=_4c.item;
}
_4f=_1(_52,_4d.parent,_4d.property);
_54=_4f;
while(_55.length>1){
_54=_54[_55.pop()];
}
_54[_55]=val;
_52.setValue(_4d.parent,_4d.property,_4f);
}
}
}
catch(e){
alert(e);
}
};
if(_51.validate()){
_4c=this.lastFocused;
_4d=_4c.item;
var _57=_4d.value;
if(_4d.addNew){
_57=_4c.item.parent;
_4c=_4c.getParent();
_4d=_4c.item;
}
val=null;
switch(_4b.itemType){
case "reference":
this.store.fetchItemByIdentity({identity:_4b._reference,onItem:function(_59){
val=_59;
_53();
},onError:function(){
alert("The id could not be found");
}});
break;
case "value":
var _5a=_4b.jsonVal;
val=dojo.fromJson(_5a);
if(typeof val=="function"){
val.toString=function(){
return _5a;
};
}
_53();
break;
}
}else{
_51.show();
}
},_editProperty:function(){
var _5b=dojo.mixin({},this.lastFocused.item);
if(!this._editDialog){
this._createEditDialog();
}else{
this._editDialog.reset();
}
if(dojo.indexOf(this.store.getIdentityAttributes(),_5b.property)>=0){
alert("Cannot Edit an Identifier!");
}else{
this._editDialog.attr("title","Edit Property");
dijit.getEnclosingWidget(dojo.query("input",this._editDialog.containerNode)[0]).attr("disabled",true);
if(this.store.isItem(_5b.value,true)){
if(_5b.parent){
_5b.itemType="reference";
this._enableFields(_5b.itemType);
_5b._reference=this.store.getIdentity(_5b.value);
this._editDialog.attr("value",_5b);
this._editDialog.show();
}
}else{
if(_5b.value&&typeof _5b.value=="object"&&!(_5b.value instanceof Date)){
}else{
_5b.itemType="value";
this._enableFields(_5b.itemType);
_5b.jsonVal=typeof _5b.value=="function"?_5b.value.toString():_5b.value instanceof Date?"new Date(\""+_5b.value+"\")":dojo.toJson(_5b.value);
this._editDialog.attr("value",_5b);
this._editDialog.show();
}
}
}
},_destroyProperty:function(){
var _5c=this.lastFocused;
var _5d=_5c.item;
var _5e=[];
while(!this.store.isItem(_5d.parent,true)||_5d.parent instanceof Array){
_5c=_5c.getParent();
_5e.push(_5d.property);
_5d=_5c.item;
}
if(dojo.indexOf(this.store.getIdentityAttributes(),_5d.property)>=0){
alert("Cannot Delete an Identifier!");
}else{
try{
if(_5e.length>0){
var _5f,_60=_1(this.store,_5d.parent,_5d.property);
_5f=_60;
while(_5e.length>1){
_5f=_5f[_5e.pop()];
}
if(dojo.isArray(_5f)){
_5f.splice(_5e,1);
}else{
delete _5f[_5e];
}
this.store.setValue(_5d.parent,_5d.property,_60);
}else{
this.store.unsetAttribute(_5d.parent,_5d.property);
}
}
catch(e){
alert(e);
}
}
},_addProperty:function(){
var _61=this.lastFocused.item;
var _62=_61.value;
var _63=dojo.hitch(this,function(){
var _64=null;
if(!this._editDialog){
this._createEditDialog();
}else{
this._editDialog.reset();
}
if(_62 instanceof Array){
_64=_62.length;
dijit.getEnclosingWidget(dojo.query("input",this._editDialog.containerNode)[0]).attr("disabled",true);
}else{
dijit.getEnclosingWidget(dojo.query("input",this._editDialog.containerNode)[0]).attr("disabled",false);
}
this._editDialog.attr("title","Add Property");
this._enableFields("value");
this._editDialog.attr("value",{itemType:"value",property:_64});
this._editDialog.show();
});
if(_61.addNew){
_61=this.lastFocused.getParent().item;
_62=this.lastFocused.item.parent;
}
if(_61.property&&dojo.indexOf(this.store.getIdentityAttributes(),_61.property)>=0){
alert("Cannot add properties to an ID node!");
}else{
if(this.store.isItem(_62,true)&&!this.store.isItemLoaded(_62)){
this.store.loadItem({item:_62,onItem:function(_65){
_62=_65;
_63();
}});
}else{
_63();
}
}
}});
})();
}
