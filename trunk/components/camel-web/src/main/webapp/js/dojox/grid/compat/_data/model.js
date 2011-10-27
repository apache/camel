/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._data.model"]){
dojo._hasResource["dojox.grid.compat._data.model"]=true;
dojo.provide("dojox.grid.compat._data.model");
dojo.require("dojox.grid.compat._data.fields");
dojo.declare("dojox.grid.data.Model",null,{constructor:function(_1,_2){
this.observers=[];
this.fields=new dojox.grid.data.Fields();
if(_1){
this.fields.set(_1);
}
this.setData(_2);
},count:0,updating:0,observer:function(_3,_4){
this.observers.push({o:_3,p:_4||"model"});
},notObserver:function(_5){
for(var i=0,m,o;(o=this.observers[i]);i++){
if(o.o==_5){
this.observers.splice(i,1);
return;
}
}
},notify:function(_9,_a){
var a=_a||[];
for(var i=0,m,o;(o=this.observers[i]);i++){
m=o.p+_9;
o=o.o;
(m in o)&&(o[m].apply(o,a));
}
},clear:function(){
this.fields.clear();
this.clearData();
},beginUpdate:function(){
this.notify("BeginUpdate",arguments);
},endUpdate:function(){
this.notify("EndUpdate",arguments);
},clearData:function(){
this.setData(null);
},change:function(){
this.notify("Change",arguments);
},insertion:function(){
this.notify("Insertion",arguments);
this.notify("Change",arguments);
},removal:function(){
this.notify("Removal",arguments);
this.notify("Change",arguments);
},insert:function(_f){
if(!this._insert.apply(this,arguments)){
return false;
}
this.insertion.apply(this,dojo._toArray(arguments,1));
return true;
},remove:function(_10){
if(!this._remove.apply(this,arguments)){
return false;
}
this.removal.apply(this,arguments);
return true;
},canSort:function(){
return this.sort!=null;
},generateComparator:function(_11,_12,_13,_14){
return function(a,b){
var _17=_11(a[_12],b[_12]);
return _17?(_13?_17:-_17):_14&&_14(a,b);
};
},makeComparator:function(_18){
var idx,col,_1b,_1c=null;
for(var i=_18.length-1;i>=0;i--){
idx=_18[i];
col=Math.abs(idx)-1;
if(col>=0){
_1b=this.fields.get(col);
_1c=this.generateComparator(_1b.compare,_1b.key,idx>0,_1c);
}
}
return _1c;
},sort:null,dummy:0});
dojo.declare("dojox.grid.data.Rows",dojox.grid.data.Model,{allChange:function(){
this.notify("AllChange",arguments);
this.notify("Change",arguments);
},rowChange:function(){
this.notify("RowChange",arguments);
},datumChange:function(){
this.notify("DatumChange",arguments);
},beginModifyRow:function(_1e){
if(!this.cache[_1e]){
this.cache[_1e]=this.copyRow(_1e);
}
},endModifyRow:function(_1f){
var _20=this.cache[_1f];
if(_20){
var _21=this.getRow(_1f);
if(!dojox.grid.arrayCompare(_20,_21)){
this.update(_20,_21,_1f);
}
delete this.cache[_1f];
}
},cancelModifyRow:function(_22){
var _23=this.cache[_22];
if(_23){
this.setRow(_23,_22);
delete this.cache[_22];
}
}});
dojo.declare("dojox.grid.data.Table",dojox.grid.data.Rows,{constructor:function(){
this.cache=[];
},colCount:0,data:null,cache:null,measure:function(){
this.count=this.getRowCount();
this.colCount=this.getColCount();
this.allChange();
},getRowCount:function(){
return (this.data?this.data.length:0);
},getColCount:function(){
return (this.data&&this.data.length?this.data[0].length:this.fields.count());
},badIndex:function(_24,_25){
console.error("dojox.grid.data.Table: badIndex");
},isGoodIndex:function(_26,_27){
return (_26>=0&&_26<this.count&&(arguments.length<2||(_27>=0&&_27<this.colCount)));
},getRow:function(_28){
return this.data[_28];
},copyRow:function(_29){
return this.getRow(_29).slice(0);
},getDatum:function(_2a,_2b){
return this.data[_2a][_2b];
},get:function(){
throw ("Plain \"get\" no longer supported. Use \"getRow\" or \"getDatum\".");
},setData:function(_2c){
this.data=(_2c||[]);
this.allChange();
},setRow:function(_2d,_2e){
this.data[_2e]=_2d;
this.rowChange(_2d,_2e);
this.change();
},setDatum:function(_2f,_30,_31){
this.data[_30][_31]=_2f;
this.datumChange(_2f,_30,_31);
},set:function(){
throw ("Plain \"set\" no longer supported. Use \"setData\", \"setRow\", or \"setDatum\".");
},setRows:function(_32,_33){
for(var i=0,l=_32.length,r=_33;i<l;i++,r++){
this.setRow(_32[i],r);
}
},update:function(_37,_38,_39){
return true;
},_insert:function(_3a,_3b){
dojox.grid.arrayInsert(this.data,_3b,_3a);
this.count++;
return true;
},_remove:function(_3c){
for(var i=_3c.length-1;i>=0;i--){
dojox.grid.arrayRemove(this.data,_3c[i]);
}
this.count-=_3c.length;
return true;
},sort:function(){
this.data.sort(this.makeComparator(arguments));
},swap:function(_3e,_3f){
dojox.grid.arraySwap(this.data,_3e,_3f);
this.rowChange(this.getRow(_3e),_3e);
this.rowChange(this.getRow(_3f),_3f);
this.change();
},dummy:0});
dojo.declare("dojox.grid.data.Objects",dojox.grid.data.Table,{constructor:function(_40,_41,_42){
if(!_40){
this.autoAssignFields();
}
},allChange:function(){
this.notify("FieldsChange");
this.inherited(arguments);
},autoAssignFields:function(){
var d=this.data[0],i=0,_45;
for(var f in d){
_45=this.fields.get(i++);
if(!dojo.isString(_45.key)){
_45.key=f;
}
}
},setData:function(_47){
this.data=(_47||[]);
this.autoAssignFields();
this.allChange();
},getDatum:function(_48,_49){
return this.data[_48][this.fields.get(_49).key];
}});
dojo.declare("dojox.grid.data.Dynamic",dojox.grid.data.Table,{constructor:function(){
this.page=[];
this.pages=[];
},page:null,pages:null,rowsPerPage:100,requests:0,bop:-1,eop:-1,clearData:function(){
this.pages=[];
this.bop=this.eop=-1;
this.setData([]);
},getRowCount:function(){
return this.count;
},getColCount:function(){
return this.fields.count();
},setRowCount:function(_4a){
this.count=_4a;
this.change();
},requestsPending:function(_4b){
},rowToPage:function(_4c){
return (this.rowsPerPage?Math.floor(_4c/this.rowsPerPage):_4c);
},pageToRow:function(_4d){
return (this.rowsPerPage?this.rowsPerPage*_4d:_4d);
},requestRows:function(_4e,_4f){
},rowsProvided:function(_50,_51){
this.requests--;
if(this.requests==0){
this.requestsPending(false);
}
},requestPage:function(_52){
var row=this.pageToRow(_52);
var _54=Math.min(this.rowsPerPage,this.count-row);
if(_54>0){
this.requests++;
this.requestsPending(true);
setTimeout(dojo.hitch(this,"requestRows",row,_54),1);
}
},needPage:function(_55){
if(!this.pages[_55]){
this.pages[_55]=true;
this.requestPage(_55);
}
},preparePage:function(_56,_57){
if(_56<this.bop||_56>=this.eop){
var _58=this.rowToPage(_56);
this.needPage(_58);
this.bop=_58*this.rowsPerPage;
this.eop=this.bop+(this.rowsPerPage||this.count);
}
},isRowLoaded:function(_59){
return Boolean(this.data[_59]);
},removePages:function(_5a){
for(var i=0,r;((r=_5a[i])!=undefined);i++){
this.pages[this.rowToPage(r)]=false;
}
this.bop=this.eop=-1;
},remove:function(_5d){
this.removePages(_5d);
dojox.grid.data.Table.prototype.remove.apply(this,arguments);
},getRow:function(_5e){
var row=this.data[_5e];
if(!row){
this.preparePage(_5e);
}
return row;
},getDatum:function(_60,_61){
var row=this.getRow(_60);
return (row?row[_61]:this.fields.get(_61).na);
},setDatum:function(_63,_64,_65){
var row=this.getRow(_64);
if(row){
row[_65]=_63;
this.datumChange(_63,_64,_65);
}else{
console.error("["+this.declaredClass+"] dojox.grid.data.dynamic.set: cannot set data on a non-loaded row");
}
},canSort:function(){
return false;
}});
dojox.grid.data.table=dojox.grid.data.Table;
dojox.grid.data.dynamic=dojox.grid.data.Dynamic;
dojo.declare("dojox.grid.data.DojoData",dojox.grid.data.Dynamic,{constructor:function(_67,_68,_69){
this.count=1;
this._rowIdentities={};
this._currentlyProcessing=[];
if(_69){
dojo.mixin(this,_69);
}
if(this.store){
var f=this.store.getFeatures();
this._canNotify=f["dojo.data.api.Notification"];
this._canWrite=f["dojo.data.api.Write"];
this._canIdentify=f["dojo.data.api.Identity"];
if(this._canNotify){
dojo.connect(this.store,"onSet",this,"_storeDatumChange");
dojo.connect(this.store,"onDelete",this,"_storeDatumDelete");
dojo.connect(this.store,"onNew",this,"_storeDatumNew");
}
if(this._canWrite){
dojo.connect(this.store,"revert",this,"refresh");
}
}
},markupFactory:function(_6b,_6c){
return new dojox.grid.data.DojoData(null,null,_6b);
},query:{name:"*"},store:null,_currentlyProcessing:null,_canNotify:false,_canWrite:false,_canIdentify:false,_rowIdentities:{},clientSort:false,sortFields:null,queryOptions:null,setData:function(_6d){
this.store=_6d;
this.data=[];
this.allChange();
},setRowCount:function(_6e){
this.count=_6e;
this.allChange();
},beginReturn:function(_6f){
if(this.count!=_6f){
this.setRowCount(_6f);
}
},_setupFields:function(_70){
if(this.fields._nameMaps){
return;
}
var m={};
var _72=dojo.map(this.store.getAttributes(_70),function(_73,idx){
m[_73]=idx;
m[idx+".idx"]=_73;
return {name:_73,key:_73};
},this);
this.fields._nameMaps=m;
this.fields.set(_72);
this.notify("FieldsChange");
},_getRowFromItem:function(_75){
},_createRow:function(_76){
var row={};
row.__dojo_data_item=_76;
dojo.forEach(this.fields.values,function(a){
var _79=this.store.getValue(_76,a.name);
row[a.name]=(_79===undefined||_79===null)?"":_79;
},this);
return row;
},processRows:function(_7a,_7b){
if(!_7a||_7a.length==0){
return;
}
this._setupFields(_7a[0]);
dojo.forEach(_7a,function(_7c,idx){
var row=this._createRow(_7c);
this._setRowId(_7c,_7b.start,idx);
this.setRow(row,_7b.start+idx);
},this);
this.endUpdate();
},requestRows:function(_7f,_80){
this.beginUpdate();
var row=_7f||0;
var _82={start:row,count:this.rowsPerPage,query:this.query,sort:this.sortFields,queryOptions:this.queryOptions,onBegin:dojo.hitch(this,"beginReturn"),onComplete:dojo.hitch(this,"processRows"),onError:dojo.hitch(this,"processError")};
this.store.fetch(_82);
},getDatum:function(_83,_84){
var row=this.getRow(_83);
var _86=this.fields.values[_84];
return row&&_86?row[_86.name]:_86?_86.na:"?";
},setDatum:function(_87,_88,_89){
var n=this.fields._nameMaps[_89+".idx"];
if(n){
this.data[_88][n]=_87;
this.datumChange(_87,_88,_89);
}
},copyRow:function(_8b){
var row={};
var _8d={};
var src=this.getRow(_8b);
for(var x in src){
if(src[x]!=_8d[x]){
row[x]=src[x];
}
}
return row;
},_attrCompare:function(_90,_91){
dojo.forEach(this.fields.values,function(a){
if(_90[a.name]!=_91[a.name]){
return false;
}
},this);
return true;
},endModifyRow:function(_93){
var _94=this.cache[_93];
if(_94){
var _95=this.getRow(_93);
if(!this._attrCompare(_94,_95)){
this.update(_94,_95,_93);
}
delete this.cache[_93];
}
},cancelModifyRow:function(_96){
var _97=this.cache[_96];
if(_97){
this.setRow(_97,_96);
delete this.cache[_96];
}
},_setRowId:function(_98,_99,idx){
if(this._canIdentify){
this._rowIdentities[this.store.getIdentity(_98)]={rowId:_99+idx,item:_98};
}else{
var _9b=dojo.toJson(this.query)+":start:"+_99+":idx:"+idx+":sort:"+dojo.toJson(this.sortFields);
this._rowIdentities[_9b]={rowId:_99+idx,item:_98};
}
},_getRowId:function(_9c,_9d){
var _9e=null;
if(this._canIdentify&&!_9d){
var _9f=this._rowIdentities[this.store.getIdentity(_9c)];
if(_9f){
_9e=_9f.rowId;
}
}else{
var id;
for(id in this._rowIdentities){
if(this._rowIdentities[id].item===_9c){
_9e=this._rowIdentities[id].rowId;
break;
}
}
}
return _9e;
},_storeDatumChange:function(_a1,_a2,_a3,_a4){
var _a5=this._getRowId(_a1);
var row=this.getRow(_a5);
if(row){
row[_a2]=_a4;
var _a7=this.fields._nameMaps[_a2];
this.notify("DatumChange",[_a4,_a5,_a7]);
}
},_storeDatumDelete:function(_a8){
if(dojo.indexOf(this._currentlyProcessing,_a8)!=-1){
return;
}
var _a9=this._getRowId(_a8,true);
if(_a9!=null){
this._removeItems([_a9]);
}
},_storeDatumNew:function(_aa){
if(this._disableNew){
return;
}
this._insertItem(_aa,this.data.length);
},insert:function(_ab,_ac){
this._disableNew=true;
var i=this.store.newItem(_ab);
this._disableNew=false;
this._insertItem(i,_ac);
},_insertItem:function(_ae,_af){
if(!this.fields._nameMaps){
this._setupFields(_ae);
}
var row=this._createRow(_ae);
for(var i in this._rowIdentities){
var _b2=this._rowIdentities[i];
if(_b2.rowId>=_af){
_b2.rowId++;
}
}
this._setRowId(_ae,0,_af);
dojox.grid.data.Dynamic.prototype.insert.apply(this,[row,_af]);
},datumChange:function(_b3,_b4,_b5){
if(this._canWrite){
var row=this.getRow(_b4);
var _b7=this.fields._nameMaps[_b5+".idx"];
this.store.setValue(row.__dojo_data_item,_b7,_b3);
}else{
this.notify("DatumChange",arguments);
}
},insertion:function(){
this.notify("Insertion",arguments);
this.notify("Change",arguments);
},removal:function(){
this.notify("Removal",arguments);
this.notify("Change",arguments);
},remove:function(_b8){
for(var i=_b8.length-1;i>=0;i--){
var _ba=this.data[_b8[i]].__dojo_data_item;
this._currentlyProcessing.push(_ba);
this.store.deleteItem(_ba);
}
this._removeItems(_b8);
this._currentlyProcessing=[];
},_removeItems:function(_bb){
dojox.grid.data.Dynamic.prototype.remove.apply(this,arguments);
this._rowIdentities={};
for(var i=0;i<this.data.length;i++){
this._setRowId(this.data[i].__dojo_data_item,0,i);
}
},canSort:function(){
return true;
},sort:function(_bd){
var col=Math.abs(_bd)-1;
this.sortFields=[{"attribute":this.fields.values[col].name,"descending":(_bd>0)}];
this.refresh();
},refresh:function(){
this.clearData(true);
this.requestRows();
},clearData:function(_bf){
this._rowIdentities={};
this.pages=[];
this.bop=this.eop=-1;
this.count=0;
this.setData((_bf?this.store:[]));
},processError:function(_c0,_c1){

}});
}
