/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.FlickrRestStore"]){
dojo._hasResource["dojox.data.FlickrRestStore"]=true;
dojo.provide("dojox.data.FlickrRestStore");
dojo.require("dojox.data.FlickrStore");
dojo.declare("dojox.data.FlickrRestStore",dojox.data.FlickrStore,{constructor:function(_1){
if(_1){
if(_1.label){
this.label=_1.label;
}
if(_1.apikey){
this._apikey=_1.apikey;
}
}
this._cache=[];
this._prevRequests={};
this._handlers={};
this._prevRequestRanges=[];
this._maxPhotosPerUser={};
this._id=dojox.data.FlickrRestStore.prototype._id++;
},_id:0,_requestCount:0,_flickrRestUrl:"http://www.flickr.com/services/rest/",_apikey:null,_storeRef:"_S",_cache:null,_prevRequests:null,_handlers:null,_sortAttributes:{"date-posted":true,"date-taken":true,"interestingness":true},_fetchItems:function(_2,_3,_4){
var _5={};
if(!_2.query){
_2.query=_5={};
}else{
dojo.mixin(_5,_2.query);
}
var _6=[];
var _7=[];
var _8={format:"json",method:"flickr.photos.search",api_key:this._apikey,extras:"owner_name,date_upload,date_taken"};
var _9=false;
if(_5.userid){
_9=true;
_8.user_id=_2.query.userid;
_6.push("userid"+_2.query.userid);
}
if(_5.apikey){
_9=true;
_8.api_key=_2.query.apikey;
_7.push("api"+_2.query.apikey);
}else{
if(_8.api_key){
_9=true;
_2.query.apikey=_8.api_key;
_7.push("api"+_8.api_key);
}else{
throw Error("dojox.data.FlickrRestStore: An API key must be specified.");
}
}
_2._curCount=_2.count;
if(_5.page){
_8.page=_2.query.page;
_7.push("page"+_8.page);
}else{
if(typeof (_2.start)!="undefined"&&_2.start!=null){
if(!_2.count){
_2.count=20;
}
var _a=_2.start%_2.count;
var _b=_2.start,_c=_2.count;
if(_a!=0){
if(_b<_c/2){
_c=_b+_c;
_b=0;
}else{
var _d=20,_e=2;
for(var i=_d;i>0;i--){
if(_b%i==0&&(_b/i)>=_c){
_e=i;
break;
}
}
_c=_b/_e;
}
_2._realStart=_2.start;
_2._realCount=_2.count;
_2._curStart=_b;
_2._curCount=_c;
}else{
_2._realStart=_2._realCount=null;
_2._curStart=_2.start;
_2._curCount=_2.count;
}
_8.page=(_b/_c)+1;
_7.push("page"+_8.page);
}
}
if(_2._curCount){
_8.per_page=_2._curCount;
_7.push("count"+_2._curCount);
}
if(_5.lang){
_8.lang=_2.query.lang;
_6.push("lang"+_2.lang);
}
var url=this._flickrRestUrl;
if(_5.setid){
_8.method="flickr.photosets.getPhotos";
_8.photoset_id=_2.query.setid;
_6.push("set"+_2.query.setid);
}
if(_5.tags){
if(_5.tags instanceof Array){
_8.tags=_5.tags.join(",");
}else{
_8.tags=_5.tags;
}
_6.push("tags"+_8.tags);
if(_5["tag_mode"]&&(_5.tag_mode.toLowerCase()=="any"||_5.tag_mode.toLowerCase()=="all")){
_8.tag_mode=_5.tag_mode;
}
}
if(_5.text){
_8.text=_5.text;
_6.push("text:"+_5.text);
}
if(_5.sort&&_5.sort.length>0){
if(!_5.sort[0].attribute){
_5.sort[0].attribute="date-posted";
}
if(this._sortAttributes[_5.sort[0].attribute]){
if(_5.sort[0].descending){
_8.sort=_5.sort[0].attribute+"-desc";
}else{
_8.sort=_5.sort[0].attribute+"-asc";
}
}
}else{
_8.sort="date-posted-asc";
}
_6.push("sort:"+_8.sort);
_6=_6.join(".");
_7=_7.length>0?"."+_7.join("."):"";
var _11=_6+_7;
_2={query:_5,count:_2._curCount,start:_2._curStart,_realCount:_2._realCount,_realStart:_2._realStart,onBegin:_2.onBegin,onComplete:_2.onComplete,onItem:_2.onItem};
var _12={request:_2,fetchHandler:_3,errorHandler:_4};
if(this._handlers[_11]){
this._handlers[_11].push(_12);
return;
}
this._handlers[_11]=[_12];
var _13=null;
var _14={url:this._flickrRestUrl,preventCache:true,content:_8,callbackParamName:"jsoncallback"};
var _15=dojo.hitch(this,function(_16,_17,_18){
var _19=_18.request.onBegin;
_18.request.onBegin=null;
var _1a;
var req=_18.request;
if(typeof (req._realStart)!=undefined&&req._realStart!=null){
req.start=req._realStart;
req.count=req._realCount;
req._realStart=req._realCount=null;
}
if(_19){
var _1c=null;
if(_17){
_1c=(_17.photoset?_17.photoset:_17.photos);
}
if(_1c&&typeof (_1c.perpage)!="undefined"&&typeof (_1c.pages)!="undefined"){
if(_1c.perpage*_1c.pages<=_18.request.start+_18.request.count){
_1a=_18.request.start+_1c.photo.length;
}else{
_1a=_1c.perpage*_1c.pages;
}
this._maxPhotosPerUser[_6]=_1a;
_19(_1a,_18.request);
}else{
if(this._maxPhotosPerUser[_6]){
_19(this._maxPhotosPerUser[_6],_18.request);
}
}
}
_18.fetchHandler(_16,_18.request);
if(_19){
_18.request.onBegin=_19;
}
});
var _1d=dojo.hitch(this,function(_1e){
if(_1e.stat!="ok"){
_4(null,_2);
}else{
var _1f=this._handlers[_11];
if(!_1f){

return;
}
this._handlers[_11]=null;
this._prevRequests[_11]=_1e;
var _20=this._processFlickrData(_1e,_2,_6);
if(!this._prevRequestRanges[_6]){
this._prevRequestRanges[_6]=[];
}
this._prevRequestRanges[_6].push({start:_2.start,end:_2.start+(_1e.photoset?_1e.photoset.photo.length:_1e.photos.photo.length)});
dojo.forEach(_1f,function(i){
_15(_20,_1e,i);
});
}
});
var _22=this._prevRequests[_11];
if(_22){
this._handlers[_11]=null;
_15(this._cache[_6],_22,_12);
return;
}else{
if(this._checkPrevRanges(_6,_2.start,_2.count)){
this._handlers[_11]=null;
_15(this._cache[_6],null,_12);
return;
}
}
var _23=dojo.io.script.get(_14);
_23.addCallback(_1d);
_23.addErrback(function(_24){
dojo.disconnect(_13);
_4(_24,_2);
});
},getAttributes:function(_25){
return ["title","author","imageUrl","imageUrlSmall","imageUrlMedium","imageUrlThumb","link","dateTaken","datePublished"];
},getValues:function(_26,_27){
this._assertIsItem(_26);
this._assertIsAttribute(_27);
switch(_27){
case "title":
return [this._unescapeHtml(_26.title)];
case "author":
return [_26.ownername];
case "imageUrlSmall":
return [_26.media.s];
case "imageUrl":
return [_26.media.l];
case "imageUrlMedium":
return [_26.media.m];
case "imageUrlThumb":
return [_26.media.t];
case "link":
return ["http://www.flickr.com/photos/"+_26.owner+"/"+_26.id];
case "dateTaken":
return [_26.datetaken];
case "datePublished":
return [_26.datepublished];
default:
return undefined;
}
},_processFlickrData:function(_28,_29,_2a){
if(_28.items){
return dojox.data.FlickrStore.prototype._processFlickrData.apply(this,arguments);
}
var _2b=["http://farm",null,".static.flickr.com/",null,"/",null,"_",null];
var _2c=[];
var _2d=(_28.photoset?_28.photoset:_28.photos);
if(_28.stat=="ok"&&_2d&&_2d.photo){
_2c=_2d.photo;
for(var i=0;i<_2c.length;i++){
var _2f=_2c[i];
_2f[this._storeRef]=this;
_2b[1]=_2f.farm;
_2b[3]=_2f.server;
_2b[5]=_2f.id;
_2b[7]=_2f.secret;
var _30=_2b.join("");
_2f.media={s:_30+"_s.jpg",m:_30+"_m.jpg",l:_30+".jpg",t:_30+"_t.jpg"};
if(!_2f.owner&&_28.photoset){
_2f.owner=_28.photoset.owner;
}
}
}
var _31=_29.start?_29.start:0;
var arr=this._cache[_2a];
if(!arr){
this._cache[_2a]=arr=[];
}
dojo.forEach(_2c,function(i,idx){
arr[idx+_31]=i;
});
return arr;
},_checkPrevRanges:function(_35,_36,_37){
var end=_36+_37;
var arr=this._prevRequestRanges[_35];
return (!!arr)&&dojo.some(arr,function(_3a){
return ((_36>=_3a.start)&&(end<=_3a.end));
});
}});
}
