/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.HtmlStore"]){
dojo._hasResource["dojox.data.HtmlStore"]=true;
dojo.provide("dojox.data.HtmlStore");
dojo.require("dojox.xml.parser");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.data.util.filter");
dojo.declare("dojox.data.HtmlStore",null,{constructor:function(_1){
if(_1.url){
if(!_1.dataId){
throw new Error("dojo.data.HtmlStore: Cannot instantiate using url without an id!");
}
this.url=_1.url;
this.dataId=_1.dataId;
}else{
if(_1.dataId){
this._rootNode=dojo.byId(_1.dataId);
this.dataId=this._rootNode.id;
}else{
this._rootNode=dojo.byId(this.dataId);
}
this._indexItems();
}
},url:"",dataId:"",_indexItems:function(){
this._getHeadings();
if(this._rootNode.rows){
if(this._rootNode.tBodies&&this._rootNode.tBodies.length>0){
this._rootNode=this._rootNode.tBodies[0];
}
var i;
for(i=0;i<this._rootNode.rows.length;i++){
this._rootNode.rows[i].store=this;
this._rootNode.rows[i]._ident=i+1;
}
}else{
var c=1;
for(i=0;i<this._rootNode.childNodes.length;i++){
if(this._rootNode.childNodes[i].nodeType===1){
this._rootNode.childNodes[i].store=this;
this._rootNode.childNodes[i]._ident=c;
c++;
}
}
}
},_getHeadings:function(){
this._headings=[];
if(this._rootNode.tHead){
dojo.forEach(this._rootNode.tHead.rows[0].cells,dojo.hitch(this,function(th){
this._headings.push(dojox.xml.parser.textContent(th));
}));
}else{
this._headings=["name"];
}
},_getAllItems:function(){
var _5=[];
var i;
if(this._rootNode.rows){
for(i=0;i<this._rootNode.rows.length;i++){
_5.push(this._rootNode.rows[i]);
}
}else{
for(i=0;i<this._rootNode.childNodes.length;i++){
if(this._rootNode.childNodes[i].nodeType===1){
_5.push(this._rootNode.childNodes[i]);
}
}
}
return _5;
},_assertIsItem:function(_7){
if(!this.isItem(_7)){
throw new Error("dojo.data.HtmlStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_8){
if(typeof _8!=="string"){
throw new Error("dojo.data.HtmlStore: a function was passed an attribute argument that was not an attribute name string");
return -1;
}
return dojo.indexOf(this._headings,_8);
},getValue:function(_9,_a,_b){
var _c=this.getValues(_9,_a);
return (_c.length>0)?_c[0]:_b;
},getValues:function(_d,_e){
this._assertIsItem(_d);
var _f=this._assertIsAttribute(_e);
if(_f>-1){
if(_d.cells){
return [dojox.xml.parser.textContent(_d.cells[_f])];
}else{
return [dojox.xml.parser.textContent(_d)];
}
}
return [];
},getAttributes:function(_10){
this._assertIsItem(_10);
var _11=[];
for(var i=0;i<this._headings.length;i++){
if(this.hasAttribute(_10,this._headings[i])){
_11.push(this._headings[i]);
}
}
return _11;
},hasAttribute:function(_13,_14){
return this.getValues(_13,_14).length>0;
},containsValue:function(_15,_16,_17){
var _18=undefined;
if(typeof _17==="string"){
_18=dojo.data.util.filter.patternToRegExp(_17,false);
}
return this._containsValue(_15,_16,_17,_18);
},_containsValue:function(_19,_1a,_1b,_1c){
var _1d=this.getValues(_19,_1a);
for(var i=0;i<_1d.length;++i){
var _1f=_1d[i];
if(typeof _1f==="string"&&_1c){
return (_1f.match(_1c)!==null);
}else{
if(_1b===_1f){
return true;
}
}
}
return false;
},isItem:function(_20){
if(_20&&_20.store&&_20.store===this){
return true;
}
return false;
},isItemLoaded:function(_21){
return this.isItem(_21);
},loadItem:function(_22){
this._assertIsItem(_22.item);
},_fetchItems:function(_23,_24,_25){
if(this._rootNode){
this._finishFetchItems(_23,_24,_25);
}else{
if(!this.url){
this._rootNode=dojo.byId(this.dataId);
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
_27._rootNode=_2a(d,_27.dataId);
_27._indexItems();
_27._finishFetchItems(_23,_24,_25);
});
_28.addErrback(function(_30){
_25(_30,_23);
});
}
}
},_finishFetchItems:function(_31,_32,_33){
var _34=null;
var _35=this._getAllItems();
if(_31.query){
var _36=_31.queryOptions?_31.queryOptions.ignoreCase:false;
_34=[];
var _37={};
var key;
var _39;
for(key in _31.query){
_39=_31.query[key]+"";
if(typeof _39==="string"){
_37[key]=dojo.data.util.filter.patternToRegExp(_39,_36);
}
}
for(var i=0;i<_35.length;++i){
var _3b=true;
var _3c=_35[i];
for(key in _31.query){
_39=_31.query[key]+"";
if(!this._containsValue(_3c,key,_39,_37[key])){
_3b=false;
}
}
if(_3b){
_34.push(_3c);
}
}
_32(_34,_31);
}else{
if(_35.length>0){
_34=_35.slice(0,_35.length);
}
_32(_34,_31);
}
},getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
},close:function(_3d){
},getLabel:function(_3e){
if(this.isItem(_3e)){
if(_3e.cells){
return "Item #"+this.getIdentity(_3e);
}else{
return this.getValue(_3e,"name");
}
}
return undefined;
},getLabelAttributes:function(_3f){
if(_3f.cells){
return null;
}else{
return ["name"];
}
},getIdentity:function(_40){
this._assertIsItem(_40);
if(this.hasAttribute(_40,"name")){
return this.getValue(_40,"name");
}else{
return _40._ident;
}
},getIdentityAttributes:function(_41){
return null;
},fetchItemByIdentity:function(_42){
var _43=_42.identity;
var _44=this;
var _45=null;
var _46=null;
if(!this._rootNode){
if(!this.url){
this._rootNode=dojo.byId(this.dataId);
this._indexItems();
if(_44._rootNode.rows){
_45=this._rootNode.rows[_43+1];
}else{
for(var i=0;i<_44._rootNode.childNodes.length;i++){
if(_44._rootNode.childNodes[i].nodeType===1&&_43===dojox.xml.parser.textContent(_44._rootNode.childNodes[i])){
_45=_44._rootNode.childNodes[i];
}
}
}
if(_42.onItem){
_46=_42.scope?_42.scope:dojo.global;
_42.onItem.call(_46,_45);
}
}else{
var _48={url:this.url,handleAs:"text"};
var _49=dojo.xhrGet(_48);
_49.addCallback(function(_4a){
var _4b=function(_4c,id){
if(_4c.id==id){
return _4c;
}
if(_4c.childNodes){
for(var i=0;i<_4c.childNodes.length;i++){
var _4f=_4b(_4c.childNodes[i],id);
if(_4f){
return _4f;
}
}
}
return null;
};
var d=document.createElement("div");
d.innerHTML=_4a;
_44._rootNode=_4b(d,_44.dataId);
_44._indexItems();
if(_44._rootNode.rows&&_43<=_44._rootNode.rows.length){
_45=_44._rootNode.rows[_43-1];
}else{
for(var i=0;i<_44._rootNode.childNodes.length;i++){
if(_44._rootNode.childNodes[i].nodeType===1&&_43===dojox.xml.parser.textContent(_44._rootNode.childNodes[i])){
_45=_44._rootNode.childNodes[i];
break;
}
}
}
if(_42.onItem){
_46=_42.scope?_42.scope:dojo.global;
_42.onItem.call(_46,_45);
}
});
_49.addErrback(function(_52){
if(_42.onError){
_46=_42.scope?_42.scope:dojo.global;
_42.onError.call(_46,_52);
}
});
}
}else{
if(this._rootNode.rows[_43+1]){
_45=this._rootNode.rows[_43+1];
if(_42.onItem){
_46=_42.scope?_42.scope:dojo.global;
_42.onItem.call(_46,_45);
}
}
}
}});
dojo.extend(dojox.data.HtmlStore,dojo.data.util.simpleFetch);
}
