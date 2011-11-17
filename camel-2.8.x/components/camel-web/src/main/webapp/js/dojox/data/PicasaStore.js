/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.PicasaStore"]){
dojo._hasResource["dojox.data.PicasaStore"]=true;
dojo.provide("dojox.data.PicasaStore");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.io.script");
dojo.require("dojo.date.stamp");
dojo.declare("dojox.data.PicasaStore",null,{constructor:function(_1){
if(_1&&_1.label){
this.label=_1.label;
}
},_picasaUrl:"http://picasaweb.google.com/data/feed/api/all",_storeRef:"_S",label:"title",_assertIsItem:function(_2){
if(!this.isItem(_2)){
throw new Error("dojox.data.PicasaStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_3){
if(typeof _3!=="string"){
throw new Error("dojox.data.PicasaStore: a function was passed an attribute argument that was not an attribute name string");
}
},getFeatures:function(){
return {"dojo.data.api.Read":true};
},getValue:function(_4,_5,_6){
var _7=this.getValues(_4,_5);
if(_7&&_7.length>0){
return _7[0];
}
return _6;
},getAttributes:function(_8){
return ["id","published","updated","category","title$type","title","summary$type","summary","rights$type","rights","link","author","gphoto$id","gphoto$name","location"];
},hasAttribute:function(_9,_a){
if(this.getValue(_9,_a)){
return true;
}
return false;
},isItemLoaded:function(_b){
return this.isItem(_b);
},loadItem:function(_c){
},getLabel:function(_d){
return this.getValue(_d,this.label);
},getLabelAttributes:function(_e){
return [this.label];
},containsValue:function(_f,_10,_11){
var _12=this.getValues(_f,_10);
for(var i=0;i<_12.length;i++){
if(_12[i]===_11){
return true;
}
}
return false;
},getValues:function(_14,_15){
this._assertIsItem(_14);
this._assertIsAttribute(_15);
if(_15==="title"){
return [this._unescapeHtml(_14.title)];
}else{
if(_15==="author"){
return [this._unescapeHtml(_14.author[0].name)];
}else{
if(_15==="datePublished"){
return [dojo.date.stamp.fromISOString(_14.published)];
}else{
if(_15==="dateTaken"){
return [dojo.date.stamp.fromISOString(_14.date_taken)];
}else{
if(_15==="imageUrlSmall"){
return [_14.media.thumbnail[1].url];
}else{
if(_15==="imageUrl"){
return [_14.content$src];
}else{
if(_15==="imageUrlMedium"){
return [_14.media.thumbnail[2].url];
}else{
if(_15==="link"){
return [_14.link[1]];
}else{
if(_15==="tags"){
return _14.tags.split(" ");
}else{
if(_15==="description"){
return [this._unescapeHtml(_14.summary)];
}
}
}
}
}
}
}
}
}
}
return [];
},isItem:function(_16){
if(_16&&_16[this._storeRef]===this){
return true;
}
return false;
},close:function(_17){
},_fetchItems:function(_18,_19,_1a){
if(!_18.query){
_18.query={};
}
var _1b={alt:"jsonm",pp:"1",psc:"G"};
_1b["start-index"]="1";
if(_18.query.start){
_1b["start-index"]=_18.query.start;
}
if(_18.query.tags){
_1b.q=_18.query.tags;
}
if(_18.query.userid){
_1b.uname=_18.query.userid;
}
if(_18.query.userids){
_1b.ids=_18.query.userids;
}
if(_18.query.lang){
_1b.hl=_18.query.lang;
}
if(_18.count){
_1b["max-results"]=_18.count;
}else{
_1b["max-results"]="20";
}
var _1c=this;
var _1d=null;
var _1e=function(_1f){
if(_1d!==null){
dojo.disconnect(_1d);
}
_19(_1c._processPicasaData(_1f),_18);
};
var _20={url:this._picasaUrl,content:_1b,callbackParamName:"callback",handle:_1e};
var _21=dojo.io.script.get(_20);
_21.addErrback(function(_22){
dojo.disconnect(_1d);
_1a(_22,_18);
});
},_processPicasaData:function(_23){
var _24=[];
if(_23.feed){
_24=_23.feed.entry;
for(var i=0;i<_24.length;i++){
var _26=_24[i];
_26[this._storeRef]=this;
}
}
return _24;
},_unescapeHtml:function(str){
str=str.replace(/&amp;/gm,"&").replace(/&lt;/gm,"<").replace(/&gt;/gm,">").replace(/&quot;/gm,"\"");
str=str.replace(/&#39;/gm,"'");
return str;
}});
dojo.extend(dojox.data.PicasaStore,dojo.data.util.simpleFetch);
}
