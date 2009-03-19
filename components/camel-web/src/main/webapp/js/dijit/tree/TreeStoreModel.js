/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.tree.TreeStoreModel"]){
dojo._hasResource["dijit.tree.TreeStoreModel"]=true;
dojo.provide("dijit.tree.TreeStoreModel");
dojo.declare("dijit.tree.TreeStoreModel",null,{store:null,childrenAttrs:["children"],labelAttr:"",root:null,query:null,constructor:function(_1){
dojo.mixin(this,_1);
this.connects=[];
var _2=this.store;
if(!_2.getFeatures()["dojo.data.api.Identity"]){
throw new Error("dijit.Tree: store must support dojo.data.Identity");
}
if(_2.getFeatures()["dojo.data.api.Notification"]){
this.connects=this.connects.concat([dojo.connect(_2,"onNew",this,"_onNewItem"),dojo.connect(_2,"onDelete",this,"_onDeleteItem"),dojo.connect(_2,"onSet",this,"_onSetItem")]);
}
},destroy:function(){
dojo.forEach(this.connects,dojo.disconnect);
},getRoot:function(_3,_4){
if(this.root){
_3(this.root);
}else{
this.store.fetch({query:this.query,onComplete:dojo.hitch(this,function(_5){
if(_5.length!=1){
throw new Error(this.declaredClass+": query "+dojo.toJson(this.query)+" returned "+_5.length+" items, but must return exactly one item");
}
this.root=_5[0];
_3(this.root);
}),onError:_4});
}
},mayHaveChildren:function(_6){
return dojo.some(this.childrenAttrs,function(_7){
return this.store.hasAttribute(_6,_7);
},this);
},getChildren:function(_8,_9,_a){
var _b=this.store;
var _c=[];
for(var i=0;i<this.childrenAttrs.length;i++){
var _e=_b.getValues(_8,this.childrenAttrs[i]);
_c=_c.concat(_e);
}
var _f=0;
dojo.forEach(_c,function(_10){
if(!_b.isItemLoaded(_10)){
_f++;
}
});
if(_f==0){
_9(_c);
}else{
var _11=function _11(_12){
if(--_f==0){
_9(_c);
}
};
dojo.forEach(_c,function(_13){
if(!_b.isItemLoaded(_13)){
_b.loadItem({item:_13,onItem:_11,onError:_a});
}
});
}
},getIdentity:function(_14){
return this.store.getIdentity(_14);
},getLabel:function(_15){
if(this.labelAttr){
return this.store.getValue(_15,this.labelAttr);
}else{
return this.store.getLabel(_15);
}
},newItem:function(_16,_17){
var _18={parent:_17,attribute:this.childrenAttrs[0]};
return this.store.newItem(_16,_18);
},pasteItem:function(_19,_1a,_1b,_1c,_1d){
var _1e=this.store,_1f=this.childrenAttrs[0];
if(_1a){
dojo.forEach(this.childrenAttrs,function(_20){
if(_1e.containsValue(_1a,_20,_19)){
if(!_1c){
var _21=dojo.filter(_1e.getValues(_1a,_20),function(x){
return x!=_19;
});
_1e.setValues(_1a,_20,_21);
}
_1f=_20;
}
});
}
if(_1b){
if(typeof _1d=="number"){
var _23=_1e.getValues(_1b,_1f);
_23.splice(_1d,0,_19);
_1e.setValues(_1b,_1f,_23);
}else{
_1e.setValues(_1b,_1f,_1e.getValues(_1b,_1f).concat(_19));
}
}
},onChange:function(_24){
},onChildrenChange:function(_25,_26){
},onDelete:function(_27,_28){
},_onNewItem:function(_29,_2a){
if(!_2a){
return;
}
this.getChildren(_2a.item,dojo.hitch(this,function(_2b){
this.onChildrenChange(_2a.item,_2b);
}));
},_onDeleteItem:function(_2c){
this.onDelete(_2c);
},_onSetItem:function(_2d,_2e,_2f,_30){
if(dojo.indexOf(this.childrenAttrs,_2e)!=-1){
this.getChildren(_2d,dojo.hitch(this,function(_31){
this.onChildrenChange(_2d,_31);
}));
}else{
this.onChange(_2d);
}
}});
}
