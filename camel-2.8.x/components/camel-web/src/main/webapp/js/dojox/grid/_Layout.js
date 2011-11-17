/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid._Layout"]){
dojo._hasResource["dojox.grid._Layout"]=true;
dojo.provide("dojox.grid._Layout");
dojo.require("dojox.grid.cells");
dojo.require("dojox.grid._RowSelector");
dojo.declare("dojox.grid._Layout",null,{constructor:function(_1){
this.grid=_1;
},cells:[],structure:null,defaultWidth:"6em",moveColumn:function(_2,_3,_4,_5,_6){
var _7=this.structure[_2].cells[0];
var _8=this.structure[_3].cells[0];
var _9=null;
var _a=0;
var _b=0;
for(var i=0,c;c=_7[i];i++){
if(c.index==_4){
_a=i;
break;
}
}
_9=_7.splice(_a,1)[0];
_9.view=this.grid.views.views[_3];
for(i=0,c=null;c=_8[i];i++){
if(c.index==_5){
_b=i;
break;
}
}
if(!_6){
_b+=1;
}
_8.splice(_b,0,_9);
var _e=this.grid.getCell(this.grid.getSortIndex());
if(_e){
_e._currentlySorted=this.grid.getSortAsc();
}
this.cells=[];
var _4=0;
for(var i=0,v;v=this.structure[i];i++){
for(var j=0,cs;cs=v.cells[j];j++){
for(var k=0,c;c=cs[k];k++){
c.index=_4;
this.cells.push(c);
if("_currentlySorted" in c){
var si=_4+1;
si*=c._currentlySorted?1:-1;
this.grid.sortInfo=si;
delete c._currentlySorted;
}
_4++;
}
}
}
this.grid.setupHeaderMenu();
},setColumnVisibility:function(_14,_15){
var _16=this.cells[_14];
if(_16.hidden==_15){
_16.hidden=!_15;
var v=_16.view,w=v.viewWidth;
if(w&&w!="auto"){
v._togglingColumn=dojo.marginBox(_16.getHeaderNode()).w||0;
}
v.update();
return true;
}else{
return false;
}
},addCellDef:function(_19,_1a,_1b){
var _1c=this;
var _1d=function(_1e){
var w=0;
if(_1e.colSpan>1){
w=0;
}else{
w=_1e.width||_1c._defaultCellProps.width||_1c.defaultWidth;
if(!isNaN(w)){
w=w+"em";
}
}
return w;
};
var _20={grid:this.grid,subrow:_19,layoutIndex:_1a,index:this.cells.length};
if(_1b&&_1b instanceof dojox.grid.cells._Base){
var _21=dojo.clone(_1b);
_20.unitWidth=_1d(_21._props);
_21=dojo.mixin(_21,this._defaultCellProps,_1b._props,_20);
return _21;
}
var _22=_1b.type||this._defaultCellProps.type||dojox.grid.cells.Cell;
_20.unitWidth=_1d(_1b);
return new _22(dojo.mixin({},this._defaultCellProps,_1b,_20));
},addRowDef:function(_23,_24){
var _25=[];
var _26=0,_27=0,_28=true;
for(var i=0,def,_2b;(def=_24[i]);i++){
_2b=this.addCellDef(_23,i,def);
_25.push(_2b);
this.cells.push(_2b);
if(_28&&_2b.relWidth){
_26+=_2b.relWidth;
}else{
if(_2b.width){
var w=_2b.width;
if(typeof w=="string"&&w.slice(-1)=="%"){
_27+=window.parseInt(w,10);
}else{
if(w=="auto"){
_28=false;
}
}
}
}
}
if(_26&&_28){
dojo.forEach(_25,function(_2d){
if(_2d.relWidth){
_2d.width=_2d.unitWidth=((_2d.relWidth/_26)*(100-_27))+"%";
}
});
}
return _25;
},addRowsDef:function(_2e){
var _2f=[];
if(dojo.isArray(_2e)){
if(dojo.isArray(_2e[0])){
for(var i=0,row;_2e&&(row=_2e[i]);i++){
_2f.push(this.addRowDef(i,row));
}
}else{
_2f.push(this.addRowDef(0,_2e));
}
}
return _2f;
},addViewDef:function(_32){
this._defaultCellProps=_32.defaultCell||{};
if(_32.width&&_32.width=="auto"){
delete _32.width;
}
return dojo.mixin({},_32,{cells:this.addRowsDef(_32.rows||_32.cells)});
},setStructure:function(_33){
this.fieldIndex=0;
this.cells=[];
var s=this.structure=[];
if(this.grid.rowSelector){
var sel={type:dojox._scopeName+".grid._RowSelector"};
if(dojo.isString(this.grid.rowSelector)){
var _36=this.grid.rowSelector;
if(_36=="false"){
sel=null;
}else{
if(_36!="true"){
sel["width"]=_36;
}
}
}else{
if(!this.grid.rowSelector){
sel=null;
}
}
if(sel){
s.push(this.addViewDef(sel));
}
}
var _37=function(def){
return ("name" in def||"field" in def||"get" in def);
};
var _39=function(def){
if(dojo.isArray(def)){
if(dojo.isArray(def[0])||_37(def[0])){
return true;
}
}
return false;
};
var _3b=function(def){
return (def!=null&&dojo.isObject(def)&&("cells" in def||"rows" in def||("type" in def&&!_37(def))));
};
if(dojo.isArray(_33)){
var _3d=false;
for(var i=0,st;(st=_33[i]);i++){
if(_3b(st)){
_3d=true;
break;
}
}
if(!_3d){
s.push(this.addViewDef({cells:_33}));
}else{
for(var i=0,st;(st=_33[i]);i++){
if(_39(st)){
s.push(this.addViewDef({cells:st}));
}else{
if(_3b(st)){
s.push(this.addViewDef(st));
}
}
}
}
}else{
if(_3b(_33)){
s.push(this.addViewDef(_33));
}
}
this.cellCount=this.cells.length;
this.grid.setupHeaderMenu();
}});
}
