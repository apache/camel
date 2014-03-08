/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.AppStore"]){
dojo._hasResource["dojox.data.AppStore"]=true;
dojo.provide("dojox.data.AppStore");
dojo.require("dojox.atom.io.Connection");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.data.util.filter");
dojo.requireLocalization("dojox.data","AppStore",null,"ROOT,cs,de,es,fr,hu,it,ja,ko,pl,pt,ru,zh,zh-tw");
dojo.experimental("dojox.data.AppStore");
dojo.declare("dojox.data.AppStore",null,{url:"",urlPreventCache:false,xmethod:false,_atomIO:null,_feed:null,_requests:null,_processing:null,_updates:null,_adds:null,_deletes:null,constructor:function(_1){
if(_1&&_1.url){
this.url=_1.url;
}
if(_1&&_1.urlPreventCache){
this.urlPreventCache=_1.urlPreventCache;
}
if(!this.url){
var _2=dojo.i18n.getLocalization("dojox.data","AppStore");
throw new Error(_2.missingUrl);
}
},_setFeed:function(_3,_4){
this._feed=_3;
var i;
for(i=0;i<this._feed.entries.length;i++){
this._feed.entries[i].store=this;
}
if(this._requests){
for(i=0;i<this._requests.length;i++){
var _6=this._requests[i];
if(_6.request&&_6.fh&&_6.eh){
this._finishFetchItems(_6.request,_6.fh,_6.eh);
}else{
if(_6.clear){
this._feed=null;
}else{
if(_6.add){
this._feed.addEntry(_6.add);
}else{
if(_6.remove){
this._feed.removeEntry(_6.remove);
}
}
}
}
}
}
this._requests=null;
},_getAllItems:function(){
var _7=[];
for(var i=0;i<this._feed.entries.length;i++){
_7.push(this._feed.entries[i]);
}
return _7;
},_assertIsItem:function(_9){
if(!this.isItem(_9)){
var _a=dojo.i18n.getLocalization("dojox.data","AppStore");
throw new Error(_a.invalidItem);
}
},_assertIsAttribute:function(_b){
if(typeof _b!=="string"){
var _c=dojo.i18n.getLocalization("dojox.data","AppStore");
throw new Error(_c.invalidAttributeType);
}
for(var _d in dojox.atom.io.model._actions){
if(_d==_b){
return true;
}
}
return false;
},_addUpdate:function(_e){
if(!this._updates){
this._updates=[_e];
}else{
this._updates.push(_e);
}
},getValue:function(_f,_10,_11){
var _12=this.getValues(_f,_10);
return (_12.length>0)?_12[0]:_11;
},getValues:function(_13,_14){
this._assertIsItem(_13);
var _15=this._assertIsAttribute(_14);
if(_15){
if((_14==="author"||_14==="contributor"||_14==="link")&&_13[_14+"s"]){
return _13[_14+"s"];
}
if(_14==="category"&&_13.categories){
return _13.categories;
}
if(_13[_14]){
_13=_13[_14];
if(_13.declaredClass=="dojox.atom.io.model.Content"){
return [_13.value];
}
return [_13];
}
}
return [];
},getAttributes:function(_16){
this._assertIsItem(_16);
var _17=[];
for(var key in dojox.atom.io.model._actions){
if(this.hasAttribute(_16,key)){
_17.push(key);
}
}
return _17;
},hasAttribute:function(_19,_1a){
return this.getValues(_19,_1a).length>0;
},containsValue:function(_1b,_1c,_1d){
var _1e=undefined;
if(typeof _1d==="string"){
_1e=dojo.data.util.filter.patternToRegExp(_1d,false);
}
return this._containsValue(_1b,_1c,_1d,_1e);
},_containsValue:function(_1f,_20,_21,_22,_23){
var _24=this.getValues(_1f,_20);
for(var i=0;i<_24.length;++i){
var _26=_24[i];
if(typeof _26==="string"&&_22){
if(_23){
_26=_26.replace(new RegExp(/^\s+/),"");
_26=_26.replace(new RegExp(/\s+$/),"");
}
_26=_26.replace(/\r|\n|\r\n/g,"");
return (_26.match(_22)!==null);
}else{
if(_21===_26){
return true;
}
}
}
return false;
},isItem:function(_27){
if(_27&&_27.store&&_27.store===this){
return true;
}
return false;
},isItemLoaded:function(_28){
return this.isItem(_28);
},loadItem:function(_29){
this._assertIsItem(_29.item);
},_fetchItems:function(_2a,_2b,_2c){
if(this._feed){
this._finishFetchItems(_2a,_2b,_2c);
}else{
var _2d=false;
if(!this._requests){
this._requests=[];
_2d=true;
}
this._requests.push({request:_2a,fh:_2b,eh:_2c});
if(_2d){
this._atomIO=new dojox.atom.io.Connection(false,this.urlPreventCache);
this._atomIO.getFeed(this.url,this._setFeed,null,this);
}
}
},_finishFetchItems:function(_2e,_2f,_30){
var _31=null;
var _32=this._getAllItems();
if(_2e.query){
var _33=_2e.queryOptions?_2e.queryOptions.ignoreCase:false;
_31=[];
var _34={};
var key;
var _36;
for(key in _2e.query){
_36=_2e.query[key]+"";
if(typeof _36==="string"){
_34[key]=dojo.data.util.filter.patternToRegExp(_36,_33);
}
}
for(var i=0;i<_32.length;++i){
var _38=true;
var _39=_32[i];
for(key in _2e.query){
_36=_2e.query[key]+"";
if(!this._containsValue(_39,key,_36,_34[key],_2e.trim)){
_38=false;
}
}
if(_38){
_31.push(_39);
}
}
}else{
if(_32.length>0){
_31=_32.slice(0,_32.length);
}
}
try{
_2f(_31,_2e);
}
catch(e){
_30(e,_2e);
}
},getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Write":true,"dojo.data.api.Identity":true};
},close:function(_3a){
this._feed=null;
},getLabel:function(_3b){
if(this.isItem(_3b)){
return this.getValue(_3b,"title","No Title");
}
return undefined;
},getLabelAttributes:function(_3c){
return ["title"];
},getIdentity:function(_3d){
this._assertIsItem(_3d);
return this.getValue(_3d,"id");
},getIdentityAttributes:function(_3e){
return ["id"];
},fetchItemByIdentity:function(_3f){
this._fetchItems({query:{id:_3f.identity},onItem:_3f.onItem,scope:_3f.scope},function(_40,_41){
var _42=_41.scope;
if(!_42){
_42=dojo.global;
}
if(_40.length<1){
_41.onItem.call(_42,null);
}else{
_41.onItem.call(_42,_40[0]);
}
},_3f.onError);
},newItem:function(_43){
var _44=new dojox.atom.io.model.Entry();
var _45=null;
var _46=null;
var i;
for(var key in _43){
if(this._assertIsAttribute(key)){
_45=_43[key];
switch(key){
case "link":
for(i in _45){
_46=_45[i];
_44.addLink(_46.href,_46.rel,_46.hrefLang,_46.title,_46.type);
}
break;
case "author":
for(i in _45){
_46=_45[i];
_44.addAuthor(_46.name,_46.email,_46.uri);
}
break;
case "contributor":
for(i in _45){
_46=_45[i];
_44.addContributor(_46.name,_46.email,_46.uri);
}
break;
case "category":
for(i in _45){
_46=_45[i];
_44.addCategory(_46.scheme,_46.term,_46.label);
}
break;
case "icon":
case "id":
case "logo":
case "xmlBase":
case "rights":
_44[key]=_45;
break;
case "updated":
case "published":
case "issued":
case "modified":
_44[key]=dojox.atom.io.model.util.createDate(_45);
break;
case "content":
case "summary":
case "title":
case "subtitle":
_44[key]=new dojox.atom.io.model.Content(key);
_44[key].value=_45;
break;
default:
_44[key]=_45;
break;
}
}
}
_44.store=this;
_44.isDirty=true;
if(!this._adds){
this._adds=[_44];
}else{
this._adds.push(_44);
}
if(this._feed){
this._feed.addEntry(_44);
}else{
if(this._requests){
this._requests.push({add:_44});
}else{
this._requests=[{add:_44}];
this._atomIO=new dojox.atom.io.Connection(false,this.urlPreventCache);
this._atomIO.getFeed(this.url,dojo.hitch(this,this._setFeed));
}
}
return true;
},deleteItem:function(_49){
this._assertIsItem(_49);
if(!this._deletes){
this._deletes=[_49];
}else{
this._deletes.push(_49);
}
if(this._feed){
this._feed.removeEntry(_49);
}else{
if(this._requests){
this._requests.push({remove:_49});
}else{
this._requests=[{remove:_49}];
this._atomIO=new dojox.atom.io.Connection(false,this.urlPreventCache);
this._atomIO.getFeed(this.url,dojo.hitch(this,this._setFeed));
}
}
_49=null;
return true;
},setValue:function(_4a,_4b,_4c){
this._assertIsItem(_4a);
var _4d={item:_4a};
if(this._assertIsAttribute(_4b)){
switch(_4b){
case "link":
_4d.links=_4a.links;
this._addUpdate(_4d);
_4a.links=null;
_4a.addLink(_4c.href,_4c.rel,_4c.hrefLang,_4c.title,_4c.type);
_4a.isDirty=true;
return true;
case "author":
_4d.authors=_4a.authors;
this._addUpdate(_4d);
_4a.authors=null;
_4a.addAuthor(_4c.name,_4c.email,_4c.uri);
_4a.isDirty=true;
return true;
case "contributor":
_4d.contributors=_4a.contributors;
this._addUpdate(_4d);
_4a.contributors=null;
_4a.addContributor(_4c.name,_4c.email,_4c.uri);
_4a.isDirty=true;
return true;
case "category":
_4d.categories=_4a.categories;
this._addUpdate(_4d);
_4a.categories=null;
_4a.addCategory(_4c.scheme,_4c.term,_4c.label);
_4a.isDirty=true;
return true;
case "icon":
case "id":
case "logo":
case "xmlBase":
case "rights":
_4d[_4b]=_4a[_4b];
this._addUpdate(_4d);
_4a[_4b]=_4c;
_4a.isDirty=true;
return true;
case "updated":
case "published":
case "issued":
case "modified":
_4d[_4b]=_4a[_4b];
this._addUpdate(_4d);
_4a[_4b]=dojox.atom.io.model.util.createDate(_4c);
_4a.isDirty=true;
return true;
case "content":
case "summary":
case "title":
case "subtitle":
_4d[_4b]=_4a[_4b];
this._addUpdate(_4d);
_4a[_4b]=new dojox.atom.io.model.Content(_4b);
_4a[_4b].value=_4c;
_4a.isDirty=true;
return true;
default:
_4d[_4b]=_4a[_4b];
this._addUpdate(_4d);
_4a[_4b]=_4c;
_4a.isDirty=true;
return true;
}
}
return false;
},setValues:function(_4e,_4f,_50){
if(_50.length===0){
return this.unsetAttribute(_4e,_4f);
}
this._assertIsItem(_4e);
var _51={item:_4e};
var _52;
var i;
if(this._assertIsAttribute(_4f)){
switch(_4f){
case "link":
_51.links=_4e.links;
_4e.links=null;
for(i in _50){
_52=_50[i];
_4e.addLink(_52.href,_52.rel,_52.hrefLang,_52.title,_52.type);
}
_4e.isDirty=true;
return true;
case "author":
_51.authors=_4e.authors;
_4e.authors=null;
for(i in _50){
_52=_50[i];
_4e.addAuthor(_52.name,_52.email,_52.uri);
}
_4e.isDirty=true;
return true;
case "contributor":
_51.contributors=_4e.contributors;
_4e.contributors=null;
for(i in _50){
_52=_50[i];
_4e.addContributor(_52.name,_52.email,_52.uri);
}
_4e.isDirty=true;
return true;
case "categories":
_51.categories=_4e.categories;
_4e.categories=null;
for(i in _50){
_52=_50[i];
_4e.addCategory(_52.scheme,_52.term,_52.label);
}
_4e.isDirty=true;
return true;
case "icon":
case "id":
case "logo":
case "xmlBase":
case "rights":
_51[_4f]=_4e[_4f];
_4e[_4f]=_50[0];
_4e.isDirty=true;
return true;
case "updated":
case "published":
case "issued":
case "modified":
_51[_4f]=_4e[_4f];
_4e[_4f]=dojox.atom.io.model.util.createDate(_50[0]);
_4e.isDirty=true;
return true;
case "content":
case "summary":
case "title":
case "subtitle":
_51[_4f]=_4e[_4f];
_4e[_4f]=new dojox.atom.io.model.Content(_4f);
_4e[_4f].values[0]=_50[0];
_4e.isDirty=true;
return true;
default:
_51[_4f]=_4e[_4f];
_4e[_4f]=_50[0];
_4e.isDirty=true;
return true;
}
}
this._addUpdate(_51);
return false;
},unsetAttribute:function(_54,_55){
this._assertIsItem(_54);
if(this._assertIsAttribute(_55)){
if(_54[_55]!==null){
var _56={item:_54};
switch(_55){
case "author":
case "contributor":
case "link":
_56[_55+"s"]=_54[_55+"s"];
break;
case "category":
_56.categories=_54.categories;
break;
default:
_56[_55]=_54[_55];
break;
}
_54.isDirty=true;
_54[_55]=null;
this._addUpdate(_56);
return true;
}
}
return false;
},save:function(_57){
var i;
for(i in this._adds){
this._atomIO.addEntry(this._adds[i],null,function(){
},_57.onError,false,_57.scope);
}
this._adds=null;
for(i in this._updates){
this._atomIO.updateEntry(this._updates[i].item,function(){
},_57.onError,false,this.xmethod,_57.scope);
}
this._updates=null;
for(i in this._deletes){
this._atomIO.removeEntry(this._deletes[i],function(){
},_57.onError,this.xmethod,_57.scope);
}
this._deletes=null;
this._atomIO.getFeed(this.url,dojo.hitch(this,this._setFeed));
if(_57.onComplete){
var _59=_57.scope||dojo.global;
_57.onComplete.call(_59);
}
},revert:function(){
var i;
for(i in this._adds){
this._feed.removeEntry(this._adds[i]);
}
this._adds=null;
var _5b,_5c,key;
for(i in this._updates){
_5b=this._updates[i];
_5c=_5b.item;
for(key in _5b){
if(key!=="item"){
_5c[key]=_5b[key];
}
}
}
this._updates=null;
for(i in this._deletes){
this._feed.addEntry(this._deletes[i]);
}
this._deletes=null;
return true;
},isDirty:function(_5e){
if(_5e){
this._assertIsItem(_5e);
return _5e.isDirty?true:false;
}
return (this._adds!==null||this._updates!==null);
}});
dojo.extend(dojox.data.AppStore,dojo.data.util.simpleFetch);
}
