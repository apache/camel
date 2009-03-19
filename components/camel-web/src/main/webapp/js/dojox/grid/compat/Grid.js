/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat.Grid"]){
dojo._hasResource["dojox.grid.compat.Grid"]=true;
dojo.provide("dojox.grid.compat.Grid");
dojo.require("dojox.grid.compat.VirtualGrid");
dojo.require("dojox.grid.compat._data.model");
dojo.require("dojox.grid.compat._data.editors");
dojo.require("dojox.grid.compat._data.dijitEditors");
dojo.declare("dojox.Grid",dojox.VirtualGrid,{model:"dojox.grid.data.Table",postCreate:function(){
if(this.model){
var m=this.model;
if(dojo.isString(m)){
m=dojo.getObject(m);
}
this.model=(dojo.isFunction(m))?new m():m;
this._setModel(this.model);
}
this.inherited(arguments);
},destroy:function(){
this.setModel(null);
this.inherited(arguments);
},_structureChanged:function(){
this.indexCellFields();
this.inherited(arguments);
},_setModel:function(_2){
this.model=_2;
if(this.model){
this.model.observer(this);
this.model.measure();
this.indexCellFields();
}
},setModel:function(_3){
if(this.model){
this.model.notObserver(this);
}
this._setModel(_3);
},get:function(_4){
return this.grid.model.getDatum(_4,this.fieldIndex);
},modelAllChange:function(){
this.rowCount=(this.model?this.model.getRowCount():0);
this.updateRowCount(this.rowCount);
},modelBeginUpdate:function(){
this.beginUpdate();
},modelEndUpdate:function(){
this.endUpdate();
},modelRowChange:function(_5,_6){
this.updateRow(_6);
},modelDatumChange:function(_7,_8,_9){
this.updateRow(_8);
},modelFieldsChange:function(){
this.indexCellFields();
this.render();
},modelInsertion:function(_a){
this.updateRowCount(this.model.getRowCount());
},modelRemoval:function(_b){
this.updateRowCount(this.model.getRowCount());
},getCellName:function(_c){
var v=this.model.fields.values,i=_c.fieldIndex;
return i>=0&&i<v.length&&v[i].name||this.inherited(arguments);
},indexCellFields:function(){
var _f=this.layout.cells;
for(var i=0,c;_f&&(c=_f[i]);i++){
if(dojo.isString(c.field)){
c.fieldIndex=this.model.fields.indexOf(c.field);
}
}
},refresh:function(){
this.edit.cancel();
this.model.measure();
},canSort:function(_12){
var f=this.getSortField(_12);
return f&&this.model.canSort(f);
},getSortField:function(_14){
var c=this.getCell(this.getSortIndex(_14));
return (c.fieldIndex+1)*(this.sortInfo>0?1:-1);
},sort:function(){
this.edit.apply();
this.model.sort(this.getSortField());
},addRow:function(_16,_17){
this.edit.apply();
var i=_17||-1;
if(i<0){
i=this.selection.getFirstSelected()||0;
}
if(i<0){
i=0;
}
this.model.insert(_16,i);
this.model.beginModifyRow(i);
for(var j=0,c;((c=this.getCell(j))&&!c.editor);j++){
}
if(c&&c.editor){
this.edit.setEditCell(c,i);
this.focus.setFocusCell(c,i);
}else{
this.focus.setFocusCell(this.getCell(0),i);
}
},removeSelectedRows:function(){
this.edit.apply();
var s=this.selection.getSelected();
if(s.length){
this.model.remove(s);
this.selection.clear();
}
},canEdit:function(_1c,_1d){
return (this.model.canModify?this.model.canModify(_1d):true);
},doStartEdit:function(_1e,_1f){
this.model.beginModifyRow(_1f);
this.onStartEdit(_1e,_1f);
},doApplyCellEdit:function(_20,_21,_22){
this.model.setDatum(_20,_21,_22);
this.onApplyCellEdit(_20,_21,_22);
},doCancelEdit:function(_23){
this.model.cancelModifyRow(_23);
this.onCancelEdit.apply(this,arguments);
},doApplyEdit:function(_24){
this.model.endModifyRow(_24);
this.onApplyEdit(_24);
},styleRowState:function(_25){
if(this.model.getState){
var _26=this.model.getState(_25.index),c="";
for(var i=0,ss=["inflight","error","inserting"],s;s=ss[i];i++){
if(_26[s]){
c=" dojoxGrid-row-"+s;
break;
}
}
_25.customClasses+=c;
}
},onStyleRow:function(_2b){
this.styleRowState(_2b);
this.inherited(arguments);
}});
dojox.Grid.markupFactory=function(_2c,_2d,_2e){
var d=dojo;
var _30=function(n){
var w=d.attr(n,"width")||"auto";
if((w!="auto")&&(w.substr(-2)!="em")){
w=parseInt(w)+"px";
}
return w;
};
if(!_2c.model&&d.hasAttr(_2d,"store")){
var _33=_2d.cloneNode(false);
d.attr(_33,{"jsId":null,"dojoType":d.attr(_2d,"dataModelClass")||"dojox.grid.data.DojoData"});
_2c.model=d.parser.instantiate([_33])[0];
}
if(!_2c.structure&&_2d.nodeName.toLowerCase()=="table"){
_2c.structure=d.query("> colgroup",_2d).map(function(cg){
var sv=d.attr(cg,"span");
var v={noscroll:(d.attr(cg,"noscroll")=="true")?true:false,__span:(!!sv?parseInt(sv):1),cells:[]};
if(d.hasAttr(cg,"width")){
v.width=_30(cg);
}
return v;
});
if(!_2c.structure.length){
_2c.structure.push({__span:Infinity,cells:[]});
}
d.query("thead > tr",_2d).forEach(function(tr,_38){
var _39=0;
var _3a=0;
var _3b;
var _3c=null;
d.query("> th",tr).map(function(th){
if(!_3c){
_3b=0;
_3c=_2c.structure[0];
}else{
if(_39>=(_3b+_3c.__span)){
_3a++;
_3b+=_3c.__span;
var _3e=_3c;
_3c=_2c.structure[_3a];
}
}
var _3f={name:d.trim(d.attr(th,"name")||th.innerHTML),field:d.trim(d.attr(th,"field")||""),colSpan:parseInt(d.attr(th,"colspan")||1)};
_39+=_3f.colSpan;
_3f.field=_3f.field||_3f.name;
_3f.width=_30(th);
if(!_3c.cells[_38]){
_3c.cells[_38]=[];
}
_3c.cells[_38].push(_3f);
});
});
}
return new dojox.Grid(_2c,_2d);
};
dojox.grid.Grid=dojox.Grid;
}
