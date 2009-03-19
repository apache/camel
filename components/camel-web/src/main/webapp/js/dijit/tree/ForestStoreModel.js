/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.tree.ForestStoreModel"]){
dojo._hasResource["dijit.tree.ForestStoreModel"]=true;
dojo.provide("dijit.tree.ForestStoreModel");
dojo.require("dijit.tree.TreeStoreModel");
dojo.declare("dijit.tree.ForestStoreModel",dijit.tree.TreeStoreModel,{rootId:"$root$",rootLabel:"ROOT",query:null,constructor:function(_1){
this.root={store:this,root:true,id:_1.rootId,label:_1.rootLabel,children:_1.rootChildren};
},mayHaveChildren:function(_2){
return _2===this.root||this.inherited(arguments);
},getChildren:function(_3,_4,_5){
if(_3===this.root){
if(this.root.children){
_4(this.root.children);
}else{
this.store.fetch({query:this.query,onComplete:dojo.hitch(this,function(_6){
this.root.children=_6;
_4(_6);
}),onError:_5});
}
}else{
this.inherited(arguments);
}
},getIdentity:function(_7){
return (_7===this.root)?this.root.id:this.inherited(arguments);
},getLabel:function(_8){
return (_8===this.root)?this.root.label:this.inherited(arguments);
},newItem:function(_9,_a){
if(_a===this.root){
this.onNewRootItem(_9);
return this.store.newItem(_9);
}else{
return this.inherited(arguments);
}
},onNewRootItem:function(_b){
},pasteItem:function(_c,_d,_e,_f,_10){
if(_d===this.root){
if(!_f){
this.onLeaveRoot(_c);
}
}
dijit.tree.TreeStoreModel.prototype.pasteItem.call(this,_c,_d===this.root?null:_d,_e===this.root?null:_e,_f,_10);
if(_e===this.root){
this.onAddToRoot(_c);
}
},onAddToRoot:function(_11){

},onLeaveRoot:function(_12){

},_requeryTop:function(){
var _13=this.root.children||[];
this.store.fetch({query:this.query,onComplete:dojo.hitch(this,function(_14){
this.root.children=_14;
if(_13.length!=_14.length||dojo.some(_13,function(_15,idx){
return _14[idx]!=_15;
})){
this.onChildrenChange(this.root,_14);
}
})});
},_onNewItem:function(_17,_18){
this._requeryTop();
this.inherited(arguments);
},_onDeleteItem:function(_19){
if(dojo.indexOf(this.root.children,_19)!=-1){
this._requeryTop();
}
this.inherited(arguments);
}});
}
