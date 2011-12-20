/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.Selection"]){
dojo._hasResource["dojox.grid.Selection"]=true;
dojo.provide("dojox.grid.Selection");
dojo.declare("dojox.grid.Selection",null,{constructor:function(_1){
this.grid=_1;
this.selected=[];
this.setMode(_1.selectionMode);
},mode:"extended",selected:null,updating:0,selectedIndex:-1,setMode:function(_2){
if(this.selected.length){
this.deselectAll();
}
if(_2!="extended"&&_2!="multiple"&&_2!="single"&&_2!="none"){
this.mode="extended";
}else{
this.mode=_2;
}
},onCanSelect:function(_3){
return this.grid.onCanSelect(_3);
},onCanDeselect:function(_4){
return this.grid.onCanDeselect(_4);
},onSelected:function(_5){
},onDeselected:function(_6){
},onChanging:function(){
},onChanged:function(){
},isSelected:function(_7){
if(this.mode=="none"){
return false;
}
return this.selected[_7];
},getFirstSelected:function(){
if(!this.selected.length||this.mode=="none"){
return -1;
}
for(var i=0,l=this.selected.length;i<l;i++){
if(this.selected[i]){
return i;
}
}
return -1;
},getNextSelected:function(_a){
if(this.mode=="none"){
return -1;
}
for(var i=_a+1,l=this.selected.length;i<l;i++){
if(this.selected[i]){
return i;
}
}
return -1;
},getSelected:function(){
var _d=[];
for(var i=0,l=this.selected.length;i<l;i++){
if(this.selected[i]){
_d.push(i);
}
}
return _d;
},getSelectedCount:function(){
var c=0;
for(var i=0;i<this.selected.length;i++){
if(this.selected[i]){
c++;
}
}
return c;
},_beginUpdate:function(){
if(this.updating==0){
this.onChanging();
}
this.updating++;
},_endUpdate:function(){
this.updating--;
if(this.updating==0){
this.onChanged();
}
},select:function(_12){
if(this.mode=="none"){
return;
}
if(this.mode!="multiple"){
this.deselectAll(_12);
this.addToSelection(_12);
}else{
this.toggleSelect(_12);
}
},addToSelection:function(_13){
if(this.mode=="none"){
return;
}
_13=Number(_13);
if(this.selected[_13]){
this.selectedIndex=_13;
}else{
if(this.onCanSelect(_13)!==false){
this.selectedIndex=_13;
this._beginUpdate();
this.selected[_13]=true;
this.onSelected(_13);
this._endUpdate();
}
}
},deselect:function(_14){
if(this.mode=="none"){
return;
}
_14=Number(_14);
if(this.selectedIndex==_14){
this.selectedIndex=-1;
}
if(this.selected[_14]){
if(this.onCanDeselect(_14)===false){
return;
}
this._beginUpdate();
delete this.selected[_14];
this.onDeselected(_14);
this._endUpdate();
}
},setSelected:function(_15,_16){
this[(_16?"addToSelection":"deselect")](_15);
},toggleSelect:function(_17){
this.setSelected(_17,!this.selected[_17]);
},_range:function(_18,_19,_1a){
var s=(_18>=0?_18:_19),e=_19;
if(s>e){
e=s;
s=_19;
}
for(var i=s;i<=e;i++){
_1a(i);
}
},selectRange:function(_1e,_1f){
this._range(_1e,_1f,dojo.hitch(this,"addToSelection"));
},deselectRange:function(_20,_21){
this._range(_20,_21,dojo.hitch(this,"deselect"));
},insert:function(_22){
this.selected.splice(_22,0,false);
if(this.selectedIndex>=_22){
this.selectedIndex++;
}
},remove:function(_23){
this.selected.splice(_23,1);
if(this.selectedIndex>=_23){
this.selectedIndex--;
}
},deselectAll:function(_24){
for(var i in this.selected){
if((i!=_24)&&(this.selected[i]===true)){
this.deselect(i);
}
}
},clickSelect:function(_26,_27,_28){
if(this.mode=="none"){
return;
}
this._beginUpdate();
if(this.mode!="extended"){
this.select(_26);
}else{
var _29=this.selectedIndex;
if(!_27){
this.deselectAll(_26);
}
if(_28){
this.selectRange(_29,_26);
}else{
if(_27){
this.toggleSelect(_26);
}else{
this.addToSelection(_26);
}
}
}
this._endUpdate();
},clickSelectEvent:function(e){
this.clickSelect(e.rowIndex,dojo.dnd.getCopyKeyState(e),e.shiftKey);
},clear:function(){
this._beginUpdate();
this.deselectAll();
this._endUpdate();
}});
}
