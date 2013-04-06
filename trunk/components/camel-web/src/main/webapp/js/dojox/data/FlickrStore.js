/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.FlickrStore"]){
dojo._hasResource["dojox.data.FlickrStore"]=true;
dojo.provide("dojox.data.FlickrStore");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.io.script");
dojo.require("dojo.date.stamp");
dojo.require("dojo.AdapterRegistry");
(function(){
var d=dojo;
dojo.declare("dojox.data.FlickrStore",null,{constructor:function(_2){
if(_2&&_2.label){
this.label=_2.label;
}
},_storeRef:"_S",label:"title",_assertIsItem:function(_3){
if(!this.isItem(_3)){
throw new Error("dojox.data.FlickrStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_4){
if(typeof _4!=="string"){
throw new Error("dojox.data.FlickrStore: a function was passed an attribute argument that was not an attribute name string");
}
},getFeatures:function(){
return {"dojo.data.api.Read":true};
},getValue:function(_5,_6,_7){
var _8=this.getValues(_5,_6);
if(_8&&_8.length>0){
return _8[0];
}
return _7;
},getAttributes:function(_9){
return ["title","description","author","datePublished","dateTaken","imageUrl","imageUrlSmall","imageUrlMedium","tags","link"];
},hasAttribute:function(_a,_b){
if(this.getValue(_a,_b)){
return true;
}
return false;
},isItemLoaded:function(_c){
return this.isItem(_c);
},loadItem:function(_d){
},getLabel:function(_e){
return this.getValue(_e,this.label);
},getLabelAttributes:function(_f){
return [this.label];
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
var u=d.hitch(this,"_unescapeHtml");
var s=d.hitch(d.date.stamp,"fromISOString");
switch(_16){
case "title":
return [u(_15.title)];
case "author":
return [u(_15.author)];
case "datePublished":
return [s(_15.published)];
case "dateTaken":
return [s(_15.date_taken)];
case "imageUrlSmall":
return [_15.media.m.replace(/_m\./,"_s.")];
case "imageUrl":
return [_15.media.m.replace(/_m\./,".")];
case "imageUrlMedium":
return [_15.media.m];
case "link":
return [_15.link];
case "tags":
return _15.tags.split(" ");
case "description":
return [u(_15.description)];
default:
return [];
}
},isItem:function(_19){
if(_19&&_19[this._storeRef]===this){
return true;
}
return false;
},close:function(_1a){
},_fetchItems:function(_1b,_1c,_1d){
var rq=_1b.query=_1b.query||{};
var _1f={format:"json",tagmode:"any"};
d.forEach(["tags","tagmode","lang","id","ids"],function(i){
if(rq[i]){
_1f[i]=rq[i];
}
});
_1f.id=rq.id||rq.userid||rq.groupid;
if(rq.userids){
_1f.ids=rq.userids;
}
var _21=null;
var _22={url:dojox.data.FlickrStore.urlRegistry.match(_1b),preventCache:true,content:_1f};
var _23=d.hitch(this,function(_24){
if(!!_21){
d.disconnect(_21);
}
_1c(this._processFlickrData(_24),_1b);
});
_21=d.connect("jsonFlickrFeed",_23);
var _25=d.io.script.get(_22);
_25.addErrback(function(_26){
d.disconnect(_21);
_1d(_26,_1b);
});
},_processFlickrData:function(_27){
var _28=[];
if(_27.items){
_28=_27.items;
for(var i=0;i<_27.items.length;i++){
var _2a=_27.items[i];
_2a[this._storeRef]=this;
}
}
return _28;
},_unescapeHtml:function(str){
return str.replace(/&amp;/gm,"&").replace(/&lt;/gm,"<").replace(/&gt;/gm,">").replace(/&quot;/gm,"\"").replace(/&#39;/gm,"'");
}});
dojo.extend(dojox.data.FlickrStore,dojo.data.util.simpleFetch);
var _2c="http://api.flickr.com/services/feeds/";
var reg=dojox.data.FlickrStore.urlRegistry=new d.AdapterRegistry(true);
reg.register("group pool",function(_2e){
return !!_2e.query["groupid"];
},_2c+"groups_pool.gne");
reg.register("default",function(_2f){
return true;
},_2c+"photos_public.gne");
})();
if(!jsonFlickrFeed){
var jsonFlickrFeed=function(_30){
};
}
}
