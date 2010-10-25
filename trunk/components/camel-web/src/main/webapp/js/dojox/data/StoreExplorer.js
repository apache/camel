/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.StoreExplorer"]){
dojo._hasResource["dojox.data.StoreExplorer"]=true;
dojo.provide("dojox.data.StoreExplorer");
dojo.require("dojox.grid.DataGrid");
dojo.require("dojox.data.ItemExplorer");
dojo.require("dijit.layout.BorderContainer");
dojo.require("dijit.layout.ContentPane");
dojo.declare("dojox.data.StoreExplorer",dijit.layout.BorderContainer,{constructor:function(_1){
dojo.mixin(this,_1);
},store:null,stringQueries:false,postCreate:function(){
var _2=this;
this.inherited(arguments);
var _3=new dijit.layout.ContentPane({region:"top"}).placeAt(this);
function _4(_5,_6){
var _7=new dijit.form.Button({label:_5}).placeAt(_3);
_7.onClick=_6;
return _7;
};
var _8=_3.containerNode.appendChild(document.createElement("span"));
_8.innerHTML="Enter query: &nbsp;";
_8.id="queryText";
var _9=_3.containerNode.appendChild(document.createElement("input"));
_9.type="text";
_9.id="queryTextBox";
_4("Query",function(){
var _a=_9.value;
_2.setQuery(_2.stringQueries?_a:dojo.fromJson(_a));
});
_3.containerNode.appendChild(document.createElement("span")).innerHTML="&nbsp;&nbsp;&nbsp;";
var _b=_4("Create New",dojo.hitch(this,"createNew"));
var _c=_4("Delete",function(){
var _d=_e.selection.getSelected();
for(var i=0;i<_d.length;i++){
_2.store.deleteItem(_d[i]);
}
});
this.setItemName=function(_10){
_b.attr("label","<img style='width:12px; height:12px' src='"+dojo.moduleUrl("dijit.themes.tundra.images","dndCopy.png")+"' /> Create New "+_10);
_c.attr("label","Delete "+_10);
};
_4("Save",function(){
_2.store.save();
_2.tree.refreshItem();
});
_4("Revert",function(){
_2.store.revert();
});
_4("Add Column",function(){
var _11=prompt("Enter column name:","property");
if(_11){
_2.gridLayout.push({field:_11,name:_11,formatter:dojo.hitch(_2,"_formatCell"),editable:true});
_2.grid.attr("structure",_2.gridLayout);
}
});
var _12=new dijit.layout.ContentPane({region:"center"}).placeAt(this);
var _e=this.grid=new dojox.grid.DataGrid({store:this.store});
_12.attr("content",_e);
_e.canEdit=function(_13,_14){
var _15=this._copyAttr(_14,_13.field);
return !(_15&&typeof _15=="object")||_15 instanceof Date;
};
var _16=new dijit.layout.ContentPane({region:"trailing",splitter:true,style:"width: 300px",}).placeAt(this);
var _17=this.tree=new dojox.data.ItemExplorer({store:this.store});
_16.attr("content",_17);
dojo.connect(_e,"onCellClick",function(){
var _18=_e.selection.getSelected()[0];
_17.setItem(_18);
});
this.gridOnFetchComplete=_e._onFetchComplete;
this.setStore(this.store);
},setQuery:function(_19){
this.grid.setQuery(_19);
},_formatCell:function(_1a){
if(this.store.isItem(_1a)){
return this.store.getLabel(_1a)||this.store.getIdentity(_1a);
}
return _1a;
},setStore:function(_1b){
this.store=_1b;
var _1c=this;
var _1d=this.grid;
_1d._pending_requests[0]=false;
function _1e(_1f){
return _1c._formatCell(_1f);
};
var _20=this.gridOnFetchComplete;
_1d._onFetchComplete=function(_21,req){
var _23=_1c.gridLayout=[];
var _24,key,_26,i,j,k,_2a=_1b.getIdentityAttributes();
for(i=0;i<_2a.length;i++){
key=_2a[i];
_23.push({field:key,name:key,_score:100,formatter:_1e,editable:false});
}
for(i=0;_26=_21[i++];){
var _2b=_1b.getAttributes(_26);
for(k=0;key=_2b[k++];){
var _2c=false;
for(j=0;_24=_23[j++];){
if(_24.field==key){
_24._score++;
_2c=true;
break;
}
}
if(!_2c){
_23.push({field:key,name:key,_score:1,formatter:_1e,styles:"white-space:nowrap; ",editable:true});
}
}
}
_23=_23.sort(function(a,b){
return a._score>b._score?-1:1;
});
for(j=0;_24=_23[j];j++){
if(_24._score<_21.length/40*j){
_23.splice(j,_23.length-j);
break;
}
}
for(j=0;_24=_23[j++];){
_24.width=Math.round(100/_23.length)+"%";
}
_1d._onFetchComplete=_20;
_1d.attr("structure",_23);
var _2f=_20.apply(this,arguments);
};
_1d.setStore(_1b);
this.queryOptions={cache:true};
this.tree.setStore(_1b);
},createNew:function(){
var _30=prompt("Enter any properties to put in the new item (in JSON literal form):","{ }");
if(_30){
try{
this.store.newItem(dojo.fromJson(_30));
}
catch(e){
alert(e);
}
}
}});
}
