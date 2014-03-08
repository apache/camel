/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.Gallery"]){
dojo._hasResource["dojox.image.Gallery"]=true;
dojo.provide("dojox.image.Gallery");
dojo.experimental("dojox.image.Gallery");
dojo.require("dojo.fx");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojox.image.ThumbnailPicker");
dojo.require("dojox.image.SlideShow");
dojo.declare("dojox.image.Gallery",[dijit._Widget,dijit._Templated],{imageHeight:375,imageWidth:500,pageSize:dojox.image.SlideShow.prototype.pageSize,autoLoad:true,linkAttr:"link",imageThumbAttr:"imageUrlThumb",imageLargeAttr:"imageUrl",titleAttr:"title",slideshowInterval:3,templateString:"<div dojoAttachPoint=\"outerNode\" class=\"imageGalleryWrapper\">\n\t<div dojoAttachPoint=\"thumbPickerNode\"></div>\n\t<div dojoAttachPoint=\"slideShowNode\"></div>\n</div>\n",postCreate:function(){
this.widgetid=this.id;
this.inherited(arguments);
this.thumbPicker=new dojox.image.ThumbnailPicker({linkAttr:this.linkAttr,imageLargeAttr:this.imageLargeAttr,imageThumbAttr:this.imageThumbAttr,titleAttr:this.titleAttr,useLoadNotifier:true,size:this.imageWidth},this.thumbPickerNode);
this.slideShow=new dojox.image.SlideShow({imageHeight:this.imageHeight,imageWidth:this.imageWidth,autoLoad:this.autoLoad,linkAttr:this.linkAttr,imageLargeAttr:this.imageLargeAttr,titleAttr:this.titleAttr,slideshowInterval:this.slideshowInterval,pageSize:this.pageSize},this.slideShowNode);
var _1=this;
dojo.subscribe(this.slideShow.getShowTopicName(),function(_2){
_1.thumbPicker._showThumbs(_2.index);
});
dojo.subscribe(this.thumbPicker.getClickTopicName(),function(_3){
_1.slideShow.showImage(_3.index);
});
dojo.subscribe(this.thumbPicker.getShowTopicName(),function(_4){
_1.slideShow.moveImageLoadingPointer(_4.index);
});
dojo.subscribe(this.slideShow.getLoadTopicName(),function(_5){
_1.thumbPicker.markImageLoaded(_5);
});
this._centerChildren();
},setDataStore:function(_6,_7,_8){
this.thumbPicker.setDataStore(_6,_7,_8);
this.slideShow.setDataStore(_6,_7,_8);
},reset:function(){
this.slideShow.reset();
this.thumbPicker.reset();
},showNextImage:function(_9){
this.slideShow.showNextImage();
},toggleSlideshow:function(){
this.slideShow.toggleSlideshow();
},showImage:function(_a,_b){
this.slideShow.showImage(_a,_b);
},_centerChildren:function(){
var _c=dojo.marginBox(this.thumbPicker.outerNode);
var _d=dojo.marginBox(this.slideShow.outerNode);
var _e=(_c.w-_d.w)/2;
if(_e>0){
dojo.style(this.slideShow.outerNode,"marginLeft",_e+"px");
}else{
if(_e<0){
dojo.style(this.thumbPicker.outerNode,"marginLeft",(_e*-1)+"px");
}
}
}});
}
