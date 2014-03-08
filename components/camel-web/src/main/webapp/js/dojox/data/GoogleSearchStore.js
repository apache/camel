/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.GoogleSearchStore"]){
dojo._hasResource["dojox.data.GoogleSearchStore"]=true;
dojo.provide("dojox.data.GoogleSearchStore");
dojo.provide("dojox.data.GoogleWebSearchStore");
dojo.provide("dojox.data.GoogleBlogSearchStore");
dojo.provide("dojox.data.GoogleLocalSearchStore");
dojo.provide("dojox.data.GoogleVideoSearchStore");
dojo.provide("dojox.data.GoogleNewsSearchStore");
dojo.provide("dojox.data.GoogleBookSearchStore");
dojo.provide("dojox.data.GoogleImageSearchStore");
dojo.require("dojo.io.script");
dojo.experimental("dojox.data.GoogleSearchStore");
dojo.declare("dojox.data.GoogleSearchStore",null,{constructor:function(_1){
if(_1){
if(_1.label){
this.label=_1.label;
}
if(_1.key){
this._key=_1.key;
}
if(_1.lang){
this._lang=_1.lang;
}
}
this._id=dojox.data.GoogleSearchStore.prototype._id++;
},_id:0,_requestCount:0,_googleUrl:"http://ajax.googleapis.com/ajax/services/search/",_storeRef:"_S",_attributes:["unescapedUrl","url","visibleUrl","cacheUrl","title","titleNoFormatting","content"],label:"titleNoFormatting",_type:"web",_queryAttr:"text",_assertIsItem:function(_2){
if(!this.isItem(_2)){
throw new Error("dojox.data.GoogleSearchStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_3){
if(typeof _3!=="string"){
throw new Error("dojox.data.GoogleSearchStore: a function was passed an attribute argument that was not an attribute name string");
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
return this._attributes;
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
var val=_14[_15];
if(dojo.isArray(val)){
return val;
}else{
if(val!==undefined){
return [val];
}else{
return [];
}
}
},isItem:function(_17){
if(_17&&_17[this._storeRef]===this){
return true;
}
return false;
},close:function(_18){
},_format:function(_19,_1a){
return _19;
},fetch:function(_1b){
_1b=_1b||{};
var _1c=_1b.scope||dojo.global;
if(!_1b.query||!_1b.query[this._queryAttr]){
if(_1b.onError){
_1b.onError.call(_1c,new Error(this.declaredClass+": A query must be specified, with a '"+[this._queryAttr]+"' parameter."));
return;
}
}
var _1d=_1b.query[this._queryAttr];
_1b={query:{},onComplete:_1b.onComplete,onError:_1b.onError,onItem:_1b.onItem,onBegin:_1b.onBegin,start:_1b.start,count:_1b.count};
_1b.query[this._queryAttr]=_1d;
var _1e=8;
var _1f="GoogleSearchStoreCallback_"+this._id+"_"+(++this._requestCount);
var _20=this._createContent(_1d,_1f,_1b);
var _21;
if(typeof (_1b.start)==="undefined"||_1b.start===null){
_1b.start=0;
}
if(!_1b.count){
_1b.count=_1e;
}
_21={start:_1b.start-_1b.start%_1e};
var _22=this;
var _23=null;
var _24=this._googleUrl+this._type;
var _25={url:_24,preventCache:true,content:_20};
var _26=[];
var _27=0;
var _28=false;
var _29=_1b.start-1;
var _2a=0;
function _2b(req){
_2a++;
_25.content.context=_25.content.start=req.start;
var _2d=dojo.io.script.get(_25);
_2d.addErrback(function(_2e){
if(_1b.onError){
_1b.onError.call(_1c,_2e,_1b);
}
});
};
var _2f=function(_30,_31){
if(_28){
return;
}
var _32=_22._getItems(_31);
var _33=_31?_31["cursor"]:null;
if(_32){
for(var i=0;i<_32.length&&i+_30<_1b.count+_1b.start;i++){
_22._processItem(_32[i],_31);
_26[i+_30]=_32[i];
}
_27++;
if(_27==1){
var _35=_33?_33.pages:null;
var _36=_35?Number(_35[_35.length-1].start):0;
if(_1b.onBegin){
var est=_33?_33.estimatedResultCount:_32.length;
var _38=est?Math.min(est,_36+_32.length):_36+_32.length;
_1b.onBegin.call(_1c,_38,_1b);
}
var _39=(_1b.start-_1b.start%_1e)+_1e;
var _3a=1;
while(_35){
if(!_35[_3a]||Number(_35[_3a].start)>=_1b.start+_1b.count){
break;
}
if(Number(_35[_3a].start)>=_39){
_2b({start:_35[_3a].start});
}
_3a++;
}
}
if(_1b.onItem&&_26[_29+1]){
do{
_29++;
_1b.onItem.call(_1c,_26[_29],_1b);
}while(_26[_29+1]&&_29<_1b.start+_1b.count);
}
if(_27==_2a){
_28=true;
dojo.global[_1f]=null;
if(_1b.onItem){
_1b.onComplete.call(_1c,null,_1b);
}else{
_26=_26.slice(_1b.start,_1b.start+_1b.count);
_1b.onComplete.call(_1c,_26,_1b);
}
}
}
};
var _3b=[];
var _3c=_21.start-1;
var _3d=function(a,b){
if(a.start<b.start){
return -1;
}
if(b.start<a.start){
return 1;
}
return 0;
};
dojo.global[_1f]=function(_40,_41,_42,_43){
try{
if(_42!=200){
if(_1b.onError){
_1b.onError.call(_1c,new Error("Response from Google was: "+_42),_1b);
}
dojo.global[_1f]=function(){
};
return;
}
if(_40==_3c+1){
_2f(Number(_40),_41);
_3c+=_1e;
if(_3b.length>0){
_3b.sort(_3d);
while(_3b.length>0&&_3b[0].start==_3c+1){
_2f(Number(_3b[0].start),_3b[0].data);
_3b.splice(0,1);
_3c+=_1e;
}
}
}else{
_3b.push({start:_40,data:_41});
}
}
catch(e){
_1b.onError.call(_1c,e,_1b);
}
};
_2b(_21);
},_processItem:function(_44,_45){
_44[this._storeRef]=this;
},_getItems:function(_46){
return _46["results"]||_46;
},_createContent:function(_47,_48,_49){
return {q:_47,v:"1.0",rsz:"large",callback:_48,key:this._key,hl:this._lang};
}});
dojo.declare("dojox.data.GoogleWebSearchStore",dojox.data.GoogleSearchStore,{});
dojo.declare("dojox.data.GoogleBlogSearchStore",dojox.data.GoogleSearchStore,{_type:"blogs",_attributes:["blogUrl","postUrl","title","titleNoFormatting","content","author","publishedDate"]});
dojo.declare("dojox.data.GoogleLocalSearchStore",dojox.data.GoogleSearchStore,{_type:"local",_attributes:["title","titleNoFormatting","url","lat","lng","streetAddress","city","region","country","phoneNumbers","ddUrl","ddUrlToHere","ddUrlFromHere","staticMapUrl"]});
dojo.declare("dojox.data.GoogleVideoSearchStore",dojox.data.GoogleSearchStore,{_type:"video",_attributes:["title","titleNoFormatting","content","url","published","publisher","duration","tbWidth","tbHeight","tbUrl","playUrl"]});
dojo.declare("dojox.data.GoogleNewsSearchStore",dojox.data.GoogleSearchStore,{_type:"news",_attributes:["title","titleNoFormatting","content","url","unescapedUrl","publisher","clusterUrl","location","publishedDate","relatedStories"]});
dojo.declare("dojox.data.GoogleBookSearchStore",dojox.data.GoogleSearchStore,{_type:"books",_attributes:["title","titleNoFormatting","authors","url","unescapedUrl","bookId","pageCount","publishedYear"]});
dojo.declare("dojox.data.GoogleImageSearchStore",dojox.data.GoogleSearchStore,{_type:"images",_attributes:["title","titleNoFormatting","visibleUrl","url","unescapedUrl","originalContextUrl","width","height","tbWidth","tbHeight","tbUrl","content","contentNoFormatting"]});
}
