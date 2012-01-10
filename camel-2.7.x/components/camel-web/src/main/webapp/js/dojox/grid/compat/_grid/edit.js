/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.edit"]){
dojo._hasResource["dojox.grid.compat._grid.edit"]=true;
dojo.provide("dojox.grid.compat._grid.edit");
dojo.declare("dojox.grid.edit",null,{constructor:function(_1){
this.grid=_1;
this.connections=[];
if(dojo.isIE){
this.connections.push(dojo.connect(document.body,"onfocus",dojo.hitch(this,"_boomerangFocus")));
}
},info:{},destroy:function(){
dojo.forEach(this.connections,dojo.disconnect);
},cellFocus:function(_2,_3){
if(this.grid.singleClickEdit||this.isEditRow(_3)){
this.setEditCell(_2,_3);
}else{
this.apply();
}
if(this.isEditing()||(_2&&(_2.editor||0).alwaysOn)){
this._focusEditor(_2,_3);
}
},rowClick:function(e){
if(this.isEditing()&&!this.isEditRow(e.rowIndex)){
this.apply();
}
},styleRow:function(_5){
if(_5.index==this.info.rowIndex){
_5.customClasses+=" dojoxGrid-row-editing";
}
},dispatchEvent:function(e){
var c=e.cell,ed=c&&c.editor;
return ed&&ed.dispatchEvent(e.dispatch,e);
},isEditing:function(){
return this.info.rowIndex!==undefined;
},isEditCell:function(_9,_a){
return (this.info.rowIndex===_9)&&(this.info.cell.index==_a);
},isEditRow:function(_b){
return this.info.rowIndex===_b;
},setEditCell:function(_c,_d){
if(!this.isEditCell(_d,_c.index)&&this.grid.canEdit(_c,_d)){
this.start(_c,_d,this.isEditRow(_d)||_c.editor);
}
},_focusEditor:function(_e,_f){
dojox.grid.fire(_e.editor,"focus",[_f]);
},focusEditor:function(){
if(this.isEditing()){
this._focusEditor(this.info.cell,this.info.rowIndex);
}
},_boomerangWindow:500,_shouldCatchBoomerang:function(){
return this._catchBoomerang>new Date().getTime();
},_boomerangFocus:function(){
if(this._shouldCatchBoomerang()){
this.grid.focus.focusGrid();
this.focusEditor();
this._catchBoomerang=0;
}
},_doCatchBoomerang:function(){
if(dojo.isIE){
this._catchBoomerang=new Date().getTime()+this._boomerangWindow;
}
},start:function(_10,_11,_12){
this.grid.beginUpdate();
this.editorApply();
if(this.isEditing()&&!this.isEditRow(_11)){
this.applyRowEdit();
this.grid.updateRow(_11);
}
if(_12){
this.info={cell:_10,rowIndex:_11};
this.grid.doStartEdit(_10,_11);
this.grid.updateRow(_11);
}else{
this.info={};
}
this.grid.endUpdate();
this.grid.focus.focusGrid();
this._focusEditor(_10,_11);
this._doCatchBoomerang();
},_editorDo:function(_13){
var c=this.info.cell;
c&&c.editor&&c.editor[_13](this.info.rowIndex);
},editorApply:function(){
this._editorDo("apply");
},editorCancel:function(){
this._editorDo("cancel");
},applyCellEdit:function(_15,_16,_17){
if(this.grid.canEdit(_16,_17)){
this.grid.doApplyCellEdit(_15,_17,_16.fieldIndex);
}
},applyRowEdit:function(){
this.grid.doApplyEdit(this.info.rowIndex);
},apply:function(){
if(this.isEditing()){
this.grid.beginUpdate();
this.editorApply();
this.applyRowEdit();
this.info={};
this.grid.endUpdate();
this.grid.focus.focusGrid();
this._doCatchBoomerang();
}
},cancel:function(){
if(this.isEditing()){
this.grid.beginUpdate();
this.editorCancel();
this.info={};
this.grid.endUpdate();
this.grid.focus.focusGrid();
this._doCatchBoomerang();
}
},save:function(_18,_19){
var c=this.info.cell;
if(this.isEditRow(_18)&&(!_19||c.view==_19)&&c.editor){
c.editor.save(c,this.info.rowIndex);
}
},restore:function(_1b,_1c){
var c=this.info.cell;
if(this.isEditRow(_1c)&&c.view==_1b&&c.editor){
c.editor.restore(c,this.info.rowIndex);
}
}});
}
