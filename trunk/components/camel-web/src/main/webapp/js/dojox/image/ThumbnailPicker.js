/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.ThumbnailPicker"]){
dojo._hasResource["dojox.image.ThumbnailPicker"]=true;
dojo.provide("dojox.image.ThumbnailPicker");
dojo.experimental("dojox.image.ThumbnailPicker");
dojo.require("dojox.fx.scroll");
dojo.require("dojo.fx.easing");
dojo.require("dojo.fx");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dojox.image.ThumbnailPicker",[dijit._Widget,dijit._Templated],{imageStore:null,request:null,size:500,thumbHeight:75,thumbWidth:100,useLoadNotifier:false,useHyperlink:false,hyperlinkTarget:"new",isClickable:true,isScrollable:true,isHorizontal:true,autoLoad:true,linkAttr:"link",imageThumbAttr:"imageUrlThumb",imageLargeAttr:"imageUrl",pageSize:20,titleAttr:"title",templateString:"<div dojoAttachPoint=\"outerNode\" class=\"thumbOuter\">\n\t<div dojoAttachPoint=\"navPrev\" class=\"thumbNav thumbClickable\">\n\t  <img src=\"\" dojoAttachPoint=\"navPrevImg\"/>    \n\t</div>\n\t<div dojoAttachPoint=\"thumbScroller\" class=\"thumbScroller\">\n\t  <div dojoAttachPoint=\"thumbsNode\" class=\"thumbWrapper\"></div>\n\t</div>\n\t<div dojoAttachPoint=\"navNext\" class=\"thumbNav thumbClickable\">\n\t  <img src=\"\" dojoAttachPoint=\"navNextImg\"/>  \n\t</div>\n</div>\n",_thumbs:[],_thumbIndex:0,_maxPhotos:0,_loadedImages:{},postCreate:function(){
this.widgetid=this.id;
this.inherited(arguments);
this.pageSize=Number(this.pageSize);
this._scrollerSize=this.size-(51*2);
var _1=this._sizeProperty=this.isHorizontal?"width":"height";
dojo.style(this.outerNode,"textAlign","center");
dojo.style(this.outerNode,_1,this.size+"px");
dojo.style(this.thumbScroller,_1,this._scrollerSize+"px");
if(this.useHyperlink){
dojo.subscribe(this.getClickTopicName(),this,function(_2){
var _3=_2.index;
var _4=this.imageStore.getValue(_2.data,this.linkAttr);
if(!_4){
return;
}
if(this.hyperlinkTarget=="new"){
window.open(_4);
}else{
window.location=_4;
}
});
}
if(this.isClickable){
dojo.addClass(this.thumbsNode,"thumbClickable");
}
this._totalSize=0;
this.init();
},init:function(){
if(this.isInitialized){
return false;
}
var _5=this.isHorizontal?"Horiz":"Vert";
dojo.addClass(this.navPrev,"prev"+_5);
dojo.addClass(this.navNext,"next"+_5);
dojo.addClass(this.thumbsNode,"thumb"+_5);
dojo.addClass(this.outerNode,"thumb"+_5);
this.navNextImg.setAttribute("src",this._blankGif);
this.navPrevImg.setAttribute("src",this._blankGif);
this.connect(this.navPrev,"onclick","_prev");
this.connect(this.navNext,"onclick","_next");
this.isInitialized=true;
if(this.isHorizontal){
this._offsetAttr="offsetLeft";
this._sizeAttr="offsetWidth";
this._scrollAttr="scrollLeft";
}else{
this._offsetAttr="offsetTop";
this._sizeAttr="offsetHeight";
this._scrollAttr="scrollTop";
}
this._updateNavControls();
if(this.imageStore&&this.request){
this._loadNextPage();
}
return true;
},getClickTopicName:function(){
return (this.widgetId||this.id)+"/select";
},getShowTopicName:function(){
return (this.widgetId||this.id)+"/show";
},setDataStore:function(_6,_7,_8){
this.reset();
this.request={query:{},start:_7.start||0,count:_7.count||10,onBegin:dojo.hitch(this,function(_9){
this._maxPhotos=_9;
})};
if(_7.query){
dojo.mixin(this.request.query,_7.query);
}
if(_8){
dojo.forEach(["imageThumbAttr","imageLargeAttr","linkAttr","titleAttr"],function(_a){
if(_8[_a]){
this[_a]=_8[_a];
}
},this);
}
this.request.start=0;
this.request.count=this.pageSize;
this.imageStore=_6;
if(!this.init()){
this._loadNextPage();
}
},reset:function(){
this._loadedImages={};
dojo.forEach(this._thumbs,function(_b){
if(_b){
if(_b.parentNode){
_b.parentNode.removeChild(_b);
}
}
});
this._thumbs=[];
this.isInitialized=false;
this._noImages=true;
},isVisible:function(_c){
var _d=this._thumbs[_c];
if(!_d){
return false;
}
var _e=this.isHorizontal?"offsetLeft":"offsetTop";
var _f=this.isHorizontal?"offsetWidth":"offsetHeight";
var _10=this.isHorizontal?"scrollLeft":"scrollTop";
var _11=_d[_e]-this.thumbsNode[_e];
return (_11>=this.thumbScroller[_10]&&_11+_d[_f]<=this.thumbScroller[_10]+this._scrollerSize);
},_next:function(){
var pos=this.isHorizontal?"offsetLeft":"offsetTop";
var _13=this.isHorizontal?"offsetWidth":"offsetHeight";
var _14=this.thumbsNode[pos];
var _15=this._thumbs[this._thumbIndex];
var _16=_15[pos]-_14;
var _17=-1,img;
for(var i=this._thumbIndex+1;i<this._thumbs.length;i++){
img=this._thumbs[i];
if(img[pos]-_14+img[_13]-_16>this._scrollerSize){
this._showThumbs(i);
return;
}
}
},_prev:function(){
if(this.thumbScroller[this.isHorizontal?"scrollLeft":"scrollTop"]==0){
return;
}
var pos=this.isHorizontal?"offsetLeft":"offsetTop";
var _1b=this.isHorizontal?"offsetWidth":"offsetHeight";
var _1c=this._thumbs[this._thumbIndex];
var _1d=_1c[pos]-this.thumbsNode[pos];
var _1e=-1,img;
for(var i=this._thumbIndex-1;i>-1;i--){
img=this._thumbs[i];
if(_1d-img[pos]>this._scrollerSize){
this._showThumbs(i+1);
return;
}
}
this._showThumbs(0);
},_checkLoad:function(img,_22){
dojo.publish(this.getShowTopicName(),[{index:_22}]);
this._updateNavControls();
this._loadingImages={};
this._thumbIndex=_22;
if(this.thumbsNode.offsetWidth-img.offsetLeft<(this._scrollerSize*2)){
this._loadNextPage();
}
},_showThumbs:function(_23){
_23=Math.min(Math.max(_23,0),this._maxPhotos);
if(_23>=this._maxPhotos){
return;
}
var img=this._thumbs[_23];
if(!img){
return;
}
var _25=img.offsetLeft-this.thumbsNode.offsetLeft;
var top=img.offsetTop-this.thumbsNode.offsetTop;
var _27=this.isHorizontal?_25:top;
if((_27>=this.thumbScroller[this._scrollAttr])&&(_27+img[this._sizeAttr]<=this.thumbScroller[this._scrollAttr]+this._scrollerSize)){
return;
}
if(this.isScrollable){
var _28=this.isHorizontal?{x:_25,y:0}:{x:0,y:top};
dojox.fx.smoothScroll({target:_28,win:this.thumbScroller,duration:300,easing:dojo.fx.easing.easeOut,onEnd:dojo.hitch(this,"_checkLoad",img,_23)}).play(10);
}else{
if(this.isHorizontal){
this.thumbScroller.scrollLeft=_25;
}else{
this.thumbScroller.scrollTop=top;
}
this._checkLoad(img,_23);
}
},markImageLoaded:function(_29){
var _2a=dojo.byId("loadingDiv_"+this.widgetid+"_"+_29);
if(_2a){
this._setThumbClass(_2a,"thumbLoaded");
}
this._loadedImages[_29]=true;
},_setThumbClass:function(_2b,_2c){
if(!this.autoLoad){
return;
}
dojo.addClass(_2b,_2c);
},_loadNextPage:function(){
if(this._loadInProgress){
return;
}
this._loadInProgress=true;
var _2d=this.request.start+(this._noImages?0:this.pageSize);
var pos=_2d;
while(pos<this._thumbs.length&&this._thumbs[pos]){
pos++;
}
var _2f=function(_30,_31){
if(_30&&_30.length){
var _32=0;
var _33=dojo.hitch(this,function(){
if(_32>=_30.length){
this._loadInProgress=false;
return;
}
var _34=_32++;
this._loadImage(_30[_34],pos+_34,_33);
});
_33();
this._updateNavControls();
}else{
this._loadInProgress=false;
}
};
var _35=function(){
this._loadInProgress=false;

};
this.request.onComplete=dojo.hitch(this,_2f);
this.request.onError=dojo.hitch(this,_35);
this.request.start=_2d;
this._noImages=false;
this.imageStore.fetch(this.request);
},_loadImage:function(_36,_37,_38){
var url=this.imageStore.getValue(_36,this.imageThumbAttr);
var img=document.createElement("img");
var _3b=document.createElement("div");
_3b.setAttribute("id","img_"+this.widgetid+"_"+_37);
_3b.appendChild(img);
img._index=_37;
img._data=_36;
this._thumbs[_37]=_3b;
var _3c;
if(this.useLoadNotifier){
_3c=document.createElement("div");
_3c.setAttribute("id","loadingDiv_"+this.widgetid+"_"+_37);
this._setThumbClass(_3c,this._loadedImages[_37]?"thumbLoaded":"thumbNotifier");
_3b.appendChild(_3c);
}
var _3d=dojo.marginBox(this.thumbsNode);
var _3e;
var _3f;
if(this.isHorizontal){
_3e=this.thumbWidth;
_3f="w";
}else{
_3e=this.thumbHeight;
_3f="h";
}
_3d=_3d[_3f];
var sl=this.thumbScroller.scrollLeft,st=this.thumbScroller.scrollTop;
dojo.style(this.thumbsNode,this._sizeProperty,(_3d+_3e+20)+"px");
this.thumbScroller.scrollLeft=sl;
this.thumbScroller.scrollTop=st;
this.thumbsNode.appendChild(_3b);
dojo.connect(img,"onload",this,function(){
var _42=dojo.marginBox(img)[_3f];
this._totalSize+=(Number(_42)+4);
dojo.style(this.thumbsNode,this._sizeProperty,this._totalSize+"px");
if(this.useLoadNotifier){
dojo.style(_3c,"width",(img.width-4)+"px");
}
dojo.style(_3b,"width",img.width+"px");
_38();
return false;
});
dojo.connect(img,"onclick",this,function(evt){
dojo.publish(this.getClickTopicName(),[{index:evt.target._index,data:evt.target._data,url:img.getAttribute("src"),largeUrl:this.imageStore.getValue(_36,this.imageLargeAttr),title:this.imageStore.getValue(_36,this.titleAttr),link:this.imageStore.getValue(_36,this.linkAttr)}]);
return false;
});
dojo.addClass(img,"imageGalleryThumb");
img.setAttribute("src",url);
var _44=this.imageStore.getValue(_36,this.titleAttr);
if(_44){
img.setAttribute("title",_44);
}
this._updateNavControls();
},_updateNavControls:function(){
var _45=[];
var _46=function(_47,add){
var fn=add?"addClass":"removeClass";
dojo[fn](_47,"enabled");
dojo[fn](_47,"thumbClickable");
};
var pos=this.isHorizontal?"scrollLeft":"scrollTop";
var _4b=this.isHorizontal?"offsetWidth":"offsetHeight";
_46(this.navPrev,(this.thumbScroller[pos]>0));
var _4c=this._thumbs[this._thumbs.length-1];
var _4d=(this.thumbScroller[pos]+this._scrollerSize<this.thumbsNode[_4b]);
_46(this.navNext,_4d);
}});
}
