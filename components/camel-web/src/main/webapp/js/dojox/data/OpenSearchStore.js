/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.OpenSearchStore"]){
dojo._hasResource["dojox.data.OpenSearchStore"]=true;
dojo.provide("dojox.data.OpenSearchStore");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojox.xml.DomParser");
dojo.require("dojox.xml.parser");
dojo.experimental("dojox.data.OpenSearchStore");
dojo.declare("dojox.data.OpenSearchStore",null,{constructor:function(_1){
if(_1){
this.label=_1.label;
this.url=_1.url;
this.itemPath=_1.itemPath;
}
var _2=dojo.xhrGet({url:this.url,handleAs:"xml",sync:true});
_2.addCallback(this,"_processOsdd");
_2.addErrback(function(){
throw new Error("Unable to load OpenSearch Description document from ".args.url);
});
},url:"",itemPath:"",_storeRef:"_S",urlElement:null,iframeElement:null,ATOM_CONTENT_TYPE:3,ATOM_CONTENT_TYPE_STRING:"atom",RSS_CONTENT_TYPE:2,RSS_CONTENT_TYPE_STRING:"rss",XML_CONTENT_TYPE:1,XML_CONTENT_TYPE_STRING:"xml",_assertIsItem:function(_3){
if(!this.isItem(_3)){
throw new Error("dojox.data.OpenSearchStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_4){
if(typeof _4!=="string"){
throw new Error("dojox.data.OpenSearchStore: a function was passed an attribute argument that was not an attribute name string");
}
},getFeatures:function(){
return {"dojo.data.api.Read":true};
},getValue:function(_5,_6,_7){
var _8=this.getValues(_5,_6);
if(_8){
return _8[0];
}
return _7;
},getAttributes:function(_9){
return ["content"];
},hasAttribute:function(_a,_b){
if(this.getValue(_a,_b)){
return true;
}
return false;
},isItemLoaded:function(_c){
return this.isItem(_c);
},loadItem:function(_d){
},getLabel:function(_e){
return undefined;
},getLabelAttributes:function(_f){
return null;
},containsValue:function(_10,_11,_12){
var _13=this.getValues(_10,_11);
for(var i=0;i<_13.length;i++){
if(_13[i]===_12){
return true;
}
}
return false;
},getValues:function(_15,_16){
this._assertIsItem(_15);
this._assertIsAttribute(_16);
var _17=this.processItem(_15,_16);
if(_17){
return [_17];
}
return undefined;
},isItem:function(_18){
if(_18&&_18[this._storeRef]===this){
return true;
}
return false;
},close:function(_19){
},process:function(_1a){
return this["_processOSD"+this.contentType](_1a);
},processItem:function(_1b,_1c){
return this["_processItem"+this.contentType](_1b.node,_1c);
},_createSearchUrl:function(_1d){
var _1e=this.urlElement.attributes.getNamedItem("template").nodeValue;
var _1f=this.urlElement.attributes;
var _20=_1e.indexOf("{searchTerms}");
_1e=_1e.substring(0,_20)+_1d.query.searchTerms+_1e.substring(_20+13);
dojo.forEach([{"name":"count","test":_1d.count,"def":"10"},{"name":"startIndex","test":_1d.start,"def":this.urlElement.attributes.getNamedItem("indexOffset")?this.urlElement.attributes.getNamedItem("indexOffset").nodeValue:0},{"name":"startPage","test":_1d.startPage,"def":this.urlElement.attributes.getNamedItem("pageOffset")?this.urlElement.attributes.getNamedItem("pageOffset").nodeValue:0},{"name":"language","test":_1d.language,"def":"*"},{"name":"inputEncoding","test":_1d.inputEncoding,"def":"UTF-8"},{"name":"outputEncoding","test":_1d.outputEncoding,"def":"UTF-8"}],function(_21){
_1e=_1e.replace("{"+_21.name+"}",_21.test||_21.def);
_1e=_1e.replace("{"+_21.name+"?}",_21.test||_21.def);
});
return _1e;
},_fetchItems:function(_22,_23,_24){
if(!_22.query){
_22.query={};
}
var _25=this;
var url=this._createSearchUrl(_22);
var _27={url:url,preventCache:true};
var xhr=dojo.xhrGet(_27);
xhr.addErrback(function(_29){
_24(_29,_22);
});
xhr.addCallback(function(_2a){
var _2b=[];
if(_2a){
_2b=_25.process(_2a);
for(var i=0;i<_2b.length;i++){
_2b[i]={node:_2b[i]};
_2b[i][_25._storeRef]=_25;
}
}
_23(_2b,_22);
});
},_processOSDxml:function(_2d){
var div=dojo.doc.createElement("div");
div.innerHTML=_2d;
return dojo.query(this.itemPath,div);
},_processItemxml:function(_2f,_30){
if(_30==="content"){
return _2f.innerHTML;
}
return undefined;
},_processOSDatom:function(_31){
return this._processOSDfeed(_31,"entry");
},_processItematom:function(_32,_33){
return this._processItemfeed(_32,_33,"content");
},_processOSDrss:function(_34){
return this._processOSDfeed(_34,"item");
},_processItemrss:function(_35,_36){
return this._processItemfeed(_35,_36,"description");
},_processOSDfeed:function(_37,_38){
_37=dojox.xml.parser.parse(_37);
var _39=[];
var _3a=_37.getElementsByTagName(_38);
for(var i=0;i<_3a.length;i++){
_39.push(_3a.item(i));
}
return _39;
},_processItemfeed:function(_3c,_3d,_3e){
if(_3d==="content"){
var _3f=_3c.getElementsByTagName(_3e).item(0);
return this._getNodeXml(_3f,true);
}
return undefined;
},_getNodeXml:function(_40,_41){
var i;
switch(_40.nodeType){
case 1:
var xml=[];
if(!_41){
xml.push("<"+_40.tagName);
var _44;
for(i=0;i<_40.attributes.length;i++){
_44=_40.attributes.item(i);
xml.push(" "+_44.nodeName+"=\""+_44.nodeValue+"\"");
}
xml.push(">");
}
for(i=0;i<_40.childNodes.length;i++){
xml.push(this._getNodeXml(_40.childNodes.item(i)));
}
if(!_41){
xml.push("</"+_40.tagName+">\n");
}
return xml.join("");
case 3:
case 4:
return _40.nodeValue;
}
return undefined;
},_processOsdd:function(doc){
var _46=doc.getElementsByTagName("Url");
var _47=[];
var _48;
var i;
for(i=0;i<_46.length;i++){
_48=_46[i].attributes.getNamedItem("type").nodeValue;
switch(_48){
case "application/rss+xml":
_47[i]=this.RSS_CONTENT_TYPE;
break;
case "application/atom+xml":
_47[i]=this.ATOM_CONTENT_TYPE;
break;
default:
_47[i]=this.XML_CONTENT_TYPE;
break;
}
}
var _4a=0;
var _4b=_47[0];
for(i=1;i<_46.length;i++){
if(_47[i]>_4b){
_4a=i;
_4b=_47[i];
}
}
var _4c=_46[_4a].nodeName.toLowerCase();
if(_4c=="url"){
var _4d=_46[_4a].attributes;
this.urlElement=_46[_4a];
switch(_47[_4a]){
case this.ATOM_CONTENT_TYPE:
this.contentType=this.ATOM_CONTENT_TYPE_STRING;
break;
case this.RSS_CONTENT_TYPE:
this.contentType=this.RSS_CONTENT_TYPE_STRING;
break;
case this.XML_CONTENT_TYPE:
this.contentType=this.XML_CONTENT_TYPE_STRING;
break;
}
}
}});
dojo.extend(dojox.data.OpenSearchStore,dojo.data.util.simpleFetch);
}
