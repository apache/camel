/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.HtmlTableStore"]){
dojo._hasResource["dojox.data.HtmlTableStore"]=true;
dojo.provide("dojox.data.HtmlTableStore");
dojo.require("dojox.xml.parser");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.data.util.filter");
dojo.declare("dojox.data.HtmlTableStore",null,{constructor:function(_1){
dojo.deprecated("dojox.data.HtmlTableStore","Please use dojox.data.HtmlStore");
if(_1.url){
if(!_1.tableId){
throw new Error("dojo.data.HtmlTableStore: Cannot instantiate using url without an id!");
}
this.url=_1.url;
this.tableId=_1.tableId;
}else{
if(_1.tableId){
this._rootNode=dojo.byId(_1.tableId);
this.tableId=this._rootNode.id;
}else{
this._rootNode=dojo.byId(this.tableId);
}
this._getHeadings();
for(var i=0;i<this._rootNode.rows.length;i++){
this._rootNode.rows[i].store=this;
}
}
},url:"",tableId:"",_getHeadings:function(){
this._headings=[];
dojo.forEach(this._rootNode.tHead.rows[0].cells,dojo.hitch(this,function(th){
this._headings.push(dojox.xml.parser.textContent(th));
}));
},_getAllItems:function(){
var _4=[];
for(var i=1;i<this._rootNode.rows.length;i++){
_4.push(this._rootNode.rows[i]);
}
return _4;
},_assertIsItem:function(_6){
if(!this.isItem(_6)){
throw new Error("dojo.data.HtmlTableStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_7){
if(typeof _7!=="string"){
throw new Error("dojo.data.HtmlTableStore: a function was passed an attribute argument that was not an attribute name string");
return -1;
}
return dojo.indexOf(this._headings,_7);
},getValue:function(_8,_9,_a){
var _b=this.getValues(_8,_9);
return (_b.length>0)?_b[0]:_a;
},getValues:function(_c,_d){
this._assertIsItem(_c);
var _e=this._assertIsAttribute(_d);
if(_e>-1){
return [dojox.xml.parser.textContent(_c.cells[_e])];
}
return [];
},getAttributes:function(_f){
this._assertIsItem(_f);
var _10=[];
for(var i=0;i<this._headings.length;i++){
if(this.hasAttribute(_f,this._headings[i])){
_10.push(this._headings[i]);
}
}
return _10;
},hasAttribute:function(_12,_13){
return this.getValues(_12,_13).length>0;
},containsValue:function(_14,_15,_16){
var _17=undefined;
if(typeof _16==="string"){
_17=dojo.data.util.filter.patternToRegExp(_16,false);
}
return this._containsValue(_14,_15,_16,_17);
},_containsValue:function(_18,_19,_1a,_1b){
var _1c=this.getValues(_18,_19);
for(var i=0;i<_1c.length;++i){
var _1e=_1c[i];
if(typeof _1e==="string"&&_1b){
return (_1e.match(_1b)!==null);
}else{
if(_1a===_1e){
return true;
}
}
}
return false;
},isItem:function(_1f){
if(_1f&&_1f.store&&_1f.store===this){
return true;
}
return false;
},isItemLoaded:function(_20){
return this.isItem(_20);
},loadItem:function(_21){
this._assertIsItem(_21.item);
},_fetchItems:function(_22,_23,_24){
if(this._rootNode){
this._finishFetchItems(_22,_23,_24);
}else{
if(!this.url){
this._rootNode=dojo.byId(this.tableId);
this._getHeadings();
for(var i=0;i<this._rootNode.rows.length;i++){
this._rootNode.rows[i].store=this;
}
}else{
var _26={url:this.url,handleAs:"text"};
var _27=this;
var _28=dojo.xhrGet(_26);
_28.addCallback(function(_29){
var _2a=function(_2b,id){
if(_2b.id==id){
return _2b;
}
if(_2b.childNodes){
for(var i=0;i<_2b.childNodes.length;i++){
var _2e=_2a(_2b.childNodes[i],id);
if(_2e){
return _2e;
}
}
}
return null;
};
var d=document.createElement("div");
d.innerHTML=_29;
_27._rootNode=_2a(d,_27.tableId);
_27._getHeadings.call(_27);
for(var i=0;i<_27._rootNode.rows.length;i++){
_27._rootNode.rows[i].store=_27;
}
_27._finishFetchItems(_22,_23,_24);
});
_28.addErrback(function(_31){
_24(_31,_22);
});
}
}
},_finishFetchItems:function(_32,_33,_34){
var _35=null;
var _36=this._getAllItems();
if(_32.query){
var _37=_32.queryOptions?_32.queryOptions.ignoreCase:false;
_35=[];
var _38={};
var _39;
var key;
for(key in _32.query){
_39=_32.query[key]+"";
if(typeof _39==="string"){
_38[key]=dojo.data.util.filter.patternToRegExp(_39,_37);
}
}
for(var i=0;i<_36.length;++i){
var _3c=true;
var _3d=_36[i];
for(key in _32.query){
_39=_32.query[key]+"";
if(!this._containsValue(_3d,key,_39,_38[key])){
_3c=false;
}
}
if(_3c){
_35.push(_3d);
}
}
_33(_35,_32);
}else{
if(_36.length>0){
_35=_36.slice(0,_36.length);
}
_33(_35,_32);
}
},getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
},close:function(_3e){
},getLabel:function(_3f){
if(this.isItem(_3f)){
return "Table Row #"+this.getIdentity(_3f);
}
return undefined;
},getLabelAttributes:function(_40){
return null;
},getIdentity:function(_41){
this._assertIsItem(_41);
if(!dojo.isOpera){
return _41.sectionRowIndex;
}else{
return (dojo.indexOf(this._rootNode.rows,_41)-1);
}
},getIdentityAttributes:function(_42){
return null;
},fetchItemByIdentity:function(_43){
var _44=_43.identity;
var _45=this;
var _46=null;
var _47=null;
if(!this._rootNode){
if(!this.url){
this._rootNode=dojo.byId(this.tableId);
this._getHeadings();
for(var i=0;i<this._rootNode.rows.length;i++){
this._rootNode.rows[i].store=this;
}
_46=this._rootNode.rows[_44+1];
if(_43.onItem){
_47=_43.scope?_43.scope:dojo.global;
_43.onItem.call(_47,_46);
}
}else{
var _49={url:this.url,handleAs:"text"};
var _4a=dojo.xhrGet(_49);
_4a.addCallback(function(_4b){
var _4c=function(_4d,id){
if(_4d.id==id){
return _4d;
}
if(_4d.childNodes){
for(var i=0;i<_4d.childNodes.length;i++){
var _50=_4c(_4d.childNodes[i],id);
if(_50){
return _50;
}
}
}
return null;
};
var d=document.createElement("div");
d.innerHTML=_4b;
_45._rootNode=_4c(d,_45.tableId);
_45._getHeadings.call(_45);
for(var i=0;i<_45._rootNode.rows.length;i++){
_45._rootNode.rows[i].store=_45;
}
_46=_45._rootNode.rows[_44+1];
if(_43.onItem){
_47=_43.scope?_43.scope:dojo.global;
_43.onItem.call(_47,_46);
}
});
_4a.addErrback(function(_53){
if(_43.onError){
_47=_43.scope?_43.scope:dojo.global;
_43.onError.call(_47,_53);
}
});
}
}else{
if(this._rootNode.rows[_44+1]){
_46=this._rootNode.rows[_44+1];
if(_43.onItem){
_47=_43.scope?_43.scope:dojo.global;
_43.onItem.call(_47,_46);
}
}
}
}});
dojo.extend(dojox.data.HtmlTableStore,dojo.data.util.simpleFetch);
}
