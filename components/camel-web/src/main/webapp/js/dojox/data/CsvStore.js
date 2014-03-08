/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.CsvStore"]){
dojo._hasResource["dojox.data.CsvStore"]=true;
dojo.provide("dojox.data.CsvStore");
dojo.require("dojo.data.util.filter");
dojo.require("dojo.data.util.simpleFetch");
dojo.declare("dojox.data.CsvStore",null,{constructor:function(_1){
this._attributes=[];
this._attributeIndexes={};
this._dataArray=[];
this._arrayOfAllItems=[];
this._loadFinished=false;
if(_1.url){
this.url=_1.url;
}
this._csvData=_1.data;
if(_1.label){
this.label=_1.label;
}else{
if(this.label===""){
this.label=undefined;
}
}
this._storeProp="_csvStore";
this._idProp="_csvId";
this._features={"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
this._loadInProgress=false;
this._queuedFetches=[];
this.identifier=_1.identifier;
if(this.identifier===""){
delete this.identifier;
}else{
this._idMap={};
}
},url:"",label:"",identifier:"",_assertIsItem:function(_2){
if(!this.isItem(_2)){
throw new Error(this.declaredClass+": a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_3){
if(!dojo.isString(_3)){
throw new Error(this.declaredClass+": a function was passed an attribute argument that was not an attribute object nor an attribute name string");
}
},_getIndex:function(_4){
var _5=this.getIdentity(_4);
if(this.identifier){
_5=this._idMap[_5];
}
return _5;
},getValue:function(_6,_7,_8){
this._assertIsItem(_6);
this._assertIsAttribute(_7);
var _9=_8;
if(this.hasAttribute(_6,_7)){
var _a=this._dataArray[this._getIndex(_6)];
_9=_a[this._attributeIndexes[_7]];
}
return _9;
},getValues:function(_b,_c){
var _d=this.getValue(_b,_c);
return (_d?[_d]:[]);
},getAttributes:function(_e){
this._assertIsItem(_e);
var _f=[];
var _10=this._dataArray[this._getIndex(_e)];
for(var i=0;i<_10.length;i++){
if(_10[i]!==""){
_f.push(this._attributes[i]);
}
}
return _f;
},hasAttribute:function(_12,_13){
this._assertIsItem(_12);
this._assertIsAttribute(_13);
var _14=this._attributeIndexes[_13];
var _15=this._dataArray[this._getIndex(_12)];
return (typeof _14!=="undefined"&&_14<_15.length&&_15[_14]!=="");
},containsValue:function(_16,_17,_18){
var _19=undefined;
if(typeof _18==="string"){
_19=dojo.data.util.filter.patternToRegExp(_18,false);
}
return this._containsValue(_16,_17,_18,_19);
},_containsValue:function(_1a,_1b,_1c,_1d){
var _1e=this.getValues(_1a,_1b);
for(var i=0;i<_1e.length;++i){
var _20=_1e[i];
if(typeof _20==="string"&&_1d){
return (_20.match(_1d)!==null);
}else{
if(_1c===_20){
return true;
}
}
}
return false;
},isItem:function(_21){
if(_21&&_21[this._storeProp]===this){
var _22=_21[this._idProp];
if(this.identifier){
var _23=this._dataArray[this._idMap[_22]];
if(_23){
return true;
}
}else{
if(_22>=0&&_22<this._dataArray.length){
return true;
}
}
}
return false;
},isItemLoaded:function(_24){
return this.isItem(_24);
},loadItem:function(_25){
},getFeatures:function(){
return this._features;
},getLabel:function(_26){
if(this.label&&this.isItem(_26)){
return this.getValue(_26,this.label);
}
return undefined;
},getLabelAttributes:function(_27){
if(this.label){
return [this.label];
}
return null;
},_fetchItems:function(_28,_29,_2a){
var _2b=this;
var _2c=function(_2d,_2e){
var _2f=null;
if(_2d.query){
var key,_31;
_2f=[];
var _32=_2d.queryOptions?_2d.queryOptions.ignoreCase:false;
var _33={};
for(key in _2d.query){
_31=_2d.query[key];
if(typeof _31==="string"){
_33[key]=dojo.data.util.filter.patternToRegExp(_31,_32);
}
}
for(var i=0;i<_2e.length;++i){
var _35=true;
var _36=_2e[i];
for(key in _2d.query){
_31=_2d.query[key];
if(!_2b._containsValue(_36,key,_31,_33[key])){
_35=false;
}
}
if(_35){
_2f.push(_36);
}
}
}else{
if(_2e.length>0){
_2f=_2e.slice(0,_2e.length);
}
}
_29(_2f,_2d);
};
if(this._loadFinished){
_2c(_28,this._arrayOfAllItems);
}else{
if(this.url!==""){
if(this._loadInProgress){
this._queuedFetches.push({args:_28,filter:_2c});
}else{
this._loadInProgress=true;
var _37={url:_2b.url,handleAs:"text"};
var _38=dojo.xhrGet(_37);
_38.addCallback(function(_39){
try{
_2b._processData(_39);
_2c(_28,_2b._arrayOfAllItems);
_2b._handleQueuedFetches();
}
catch(e){
_2a(e,_28);
}
});
_38.addErrback(function(_3a){
_2b._loadInProgress=false;
if(_2a){
_2a(_3a,_28);
}else{
throw _3a;
}
});
}
}else{
if(this._csvData){
try{
this._processData(this._csvData);
this._csvData=null;
_2c(_28,this._arrayOfAllItems);
}
catch(e){
_2a(e,_28);
}
}else{
var _3b=new Error(this.declaredClass+": No CSV source data was provided as either URL or String data input.");
if(_2a){
_2a(_3b,_28);
}else{
throw _3b;
}
}
}
}
},close:function(_3c){
},_getArrayOfArraysFromCsvFileContents:function(_3d){
if(dojo.isString(_3d)){
var _3e=new RegExp("\r\n|\n|\r");
var _3f=new RegExp("^\\s+","g");
var _40=new RegExp("\\s+$","g");
var _41=new RegExp("\"\"","g");
var _42=[];
var i;
var _44=this._splitLines(_3d);
for(i=0;i<_44.length;++i){
var _45=_44[i];
if(_45.length>0){
var _46=_45.split(",");
var j=0;
while(j<_46.length){
var _48=_46[j];
var _49=_48.replace(_3f,"");
var _4a=_49.replace(_40,"");
var _4b=_4a.charAt(0);
var _4c=_4a.charAt(_4a.length-1);
var _4d=_4a.charAt(_4a.length-2);
var _4e=_4a.charAt(_4a.length-3);
if(_4a.length===2&&_4a=="\"\""){
_46[j]="";
}else{
if((_4b=="\"")&&((_4c!="\"")||((_4c=="\"")&&(_4d=="\"")&&(_4e!="\"")))){
if(j+1===_46.length){
return null;
}
var _4f=_46[j+1];
_46[j]=_49+","+_4f;
_46.splice(j+1,1);
}else{
if((_4b=="\"")&&(_4c=="\"")){
_4a=_4a.slice(1,(_4a.length-1));
_4a=_4a.replace(_41,"\"");
}
_46[j]=_4a;
j+=1;
}
}
}
_42.push(_46);
}
}
this._attributes=_42.shift();
for(i=0;i<this._attributes.length;i++){
this._attributeIndexes[this._attributes[i]]=i;
}
this._dataArray=_42;
}
},_splitLines:function(_50){
var _51=[];
var i;
var _53="";
var _54=false;
for(i=0;i<_50.length;i++){
var c=_50.charAt(i);
switch(c){
case "\"":
_54=!_54;
_53+=c;
break;
case "\r":
if(_54){
_53+=c;
}else{
_51.push(_53);
_53="";
if(i<(_50.length-1)&&_50.charAt(i+1)=="\n"){
i++;
}
}
break;
case "\n":
if(_54){
_53+=c;
}else{
_51.push(_53);
_53="";
}
break;
default:
_53+=c;
}
}
if(_53!==""){
_51.push(_53);
}
return _51;
},_processData:function(_56){
this._getArrayOfArraysFromCsvFileContents(_56);
this._arrayOfAllItems=[];
if(this.identifier){
if(this._attributeIndexes[this.identifier]===undefined){
throw new Error(this.declaredClass+": Identity specified is not a column header in the data set.");
}
}
for(var i=0;i<this._dataArray.length;i++){
var id=i;
if(this.identifier){
var _59=this._dataArray[i];
id=_59[this._attributeIndexes[this.identifier]];
this._idMap[id]=i;
}
this._arrayOfAllItems.push(this._createItemFromIdentity(id));
}
this._loadFinished=true;
this._loadInProgress=false;
},_createItemFromIdentity:function(_5a){
var _5b={};
_5b[this._storeProp]=this;
_5b[this._idProp]=_5a;
return _5b;
},getIdentity:function(_5c){
if(this.isItem(_5c)){
return _5c[this._idProp];
}
return null;
},fetchItemByIdentity:function(_5d){
var _5e;
var _5f=_5d.scope?_5d.scope:dojo.global;
if(!this._loadFinished){
var _60=this;
if(this.url!==""){
if(this._loadInProgress){
this._queuedFetches.push({args:_5d});
}else{
this._loadInProgress=true;
var _61={url:_60.url,handleAs:"text"};
var _62=dojo.xhrGet(_61);
_62.addCallback(function(_63){
try{
_60._processData(_63);
var _64=_60._createItemFromIdentity(_5d.identity);
if(!_60.isItem(_64)){
_64=null;
}
if(_5d.onItem){
_5d.onItem.call(_5f,_64);
}
_60._handleQueuedFetches();
}
catch(error){
if(_5d.onError){
_5d.onError.call(_5f,error);
}
}
});
_62.addErrback(function(_65){
this._loadInProgress=false;
if(_5d.onError){
_5d.onError.call(_5f,_65);
}
});
}
}else{
if(this._csvData){
try{
_60._processData(_60._csvData);
_60._csvData=null;
_5e=_60._createItemFromIdentity(_5d.identity);
if(!_60.isItem(_5e)){
_5e=null;
}
if(_5d.onItem){
_5d.onItem.call(_5f,_5e);
}
}
catch(e){
if(_5d.onError){
_5d.onError.call(_5f,e);
}
}
}
}
}else{
_5e=this._createItemFromIdentity(_5d.identity);
if(!this.isItem(_5e)){
_5e=null;
}
if(_5d.onItem){
_5d.onItem.call(_5f,_5e);
}
}
},getIdentityAttributes:function(_66){
if(this.identifier){
return [this.identifier];
}else{
return null;
}
},_handleQueuedFetches:function(){
if(this._queuedFetches.length>0){
for(var i=0;i<this._queuedFetches.length;i++){
var _68=this._queuedFetches[i];
var _69=_68.filter;
var _6a=_68.args;
if(_69){
_69(_6a,this._arrayOfAllItems);
}else{
this.fetchItemByIdentity(_68.args);
}
}
this._queuedFetches=[];
}
}});
dojo.extend(dojox.data.CsvStore,dojo.data.util.simpleFetch);
}
