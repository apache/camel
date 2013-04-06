/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.selection"]){
dojo._hasResource["dojox.grid.compat._grid.selection"]=true;
dojo.provide("dojox.grid.compat._grid.selection");
dojo.declare("dojox.grid.selection",null,{constructor:function(_1){
this.grid=_1;
this.selected=[];
},multiSelect:true,selected:null,updating:0,selectedIndex:-1,onCanSelect:function(_2){
return this.grid.onCanSelect(_2);
},onCanDeselect:function(_3){
return this.grid.onCanDeselect(_3);
},onSelected:function(_4){
return this.grid.onSelected(_4);
},onDeselected:function(_5){
return this.grid.onDeselected(_5);
},onChanging:function(){
},onChanged:function(){
return this.grid.onSelectionChanged();
},isSelected:function(_6){
return this.selected[_6];
},getFirstSelected:function(){
for(var i=0,l=this.selected.length;i<l;i++){
if(this.selected[i]){
return i;
}
}
return -1;
},getNextSelected:function(_9){
for(var i=_9+1,l=this.selected.length;i<l;i++){
if(this.selected[i]){
return i;
}
}
return -1;
},getSelected:function(){
var _c=[];
for(var i=0,l=this.selected.length;i<l;i++){
if(this.selected[i]){
_c.push(i);
}
}
return _c;
},getSelectedCount:function(){
var c=0;
for(var i=0;i<this.selected.length;i++){
if(this.selected[i]){
c++;
}
}
return c;
},beginUpdate:function(){
if(this.updating==0){
this.onChanging();
}
this.updating++;
},endUpdate:function(){
this.updating--;
if(this.updating==0){
this.onChanged();
}
},select:function(_11){
this.unselectAll(_11);
this.addToSelection(_11);
},addToSelection:function(_12){
_12=Number(_12);
if(this.selected[_12]){
this.selectedIndex=_12;
}else{
if(this.onCanSelect(_12)!==false){
this.selectedIndex=_12;
this.beginUpdate();
this.selected[_12]=true;
this.grid.onSelected(_12);
this.endUpdate();
}
}
},deselect:function(_13){
_13=Number(_13);
if(this.selectedIndex==_13){
this.selectedIndex=-1;
}
if(this.selected[_13]){
if(this.onCanDeselect(_13)===false){
return;
}
this.beginUpdate();
delete this.selected[_13];
this.grid.onDeselected(_13);
this.endUpdate();
}
},setSelected:function(_14,_15){
this[(_15?"addToSelection":"deselect")](_14);
},toggleSelect:function(_16){
this.setSelected(_16,!this.selected[_16]);
},insert:function(_17){
this.selected.splice(_17,0,false);
if(this.selectedIndex>=_17){
this.selectedIndex++;
}
},remove:function(_18){
this.selected.splice(_18,1);
if(this.selectedIndex>=_18){
this.selectedIndex--;
}
},unselectAll:function(_19){
for(var i in this.selected){
if((i!=_19)&&(this.selected[i]===true)){
this.deselect(i);
}
}
},shiftSelect:function(_1b,_1c){
var s=(_1b>=0?_1b:_1c),e=_1c;
if(s>e){
e=s;
s=_1c;
}
for(var i=s;i<=e;i++){
this.addToSelection(i);
}
},clickSelect:function(_20,_21,_22){
this.beginUpdate();
if(!this.multiSelect){
this.select(_20);
}else{
var _23=this.selectedIndex;
if(!_21){
this.unselectAll(_20);
}
if(_22){
this.shiftSelect(_23,_20);
}else{
if(_21){
this.toggleSelect(_20);
}else{
this.addToSelection(_20);
}
}
}
this.endUpdate();
},clickSelectEvent:function(e){
this.clickSelect(e.rowIndex,dojo.dnd.getCopyKeyState(e),e.shiftKey);
},clear:function(){
this.beginUpdate();
this.unselectAll();
this.endUpdate();
}});
}
