/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.SlideShow"]){
dojo._hasResource["dojox.image.SlideShow"]=true;
dojo.provide("dojox.image.SlideShow");
dojo.require("dojo.string");
dojo.require("dojo.fx");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dojox.image.SlideShow",[dijit._Widget,dijit._Templated],{imageHeight:375,imageWidth:500,title:"",titleTemplate:"${title} <span class=\"slideShowCounterText\">(${current} of ${total})</span>",noLink:false,loop:true,hasNav:true,images:[],pageSize:20,autoLoad:true,autoStart:false,fixedHeight:false,imageStore:null,linkAttr:"link",imageLargeAttr:"imageUrl",titleAttr:"title",slideshowInterval:3,templateString:"<div dojoAttachPoint=\"outerNode\" class=\"slideShowWrapper\">\n\t<div style=\"position:relative;\" dojoAttachPoint=\"innerWrapper\">\n\t\t<div class=\"slideShowNav\" dojoAttachEvent=\"onclick: _handleClick\">\n\t\t\t<div class=\"dijitInline slideShowTitle\" dojoAttachPoint=\"titleNode\">${title}</div>\n\t\t</div>\n\t\t<div dojoAttachPoint=\"navNode\" class=\"slideShowCtrl\" dojoAttachEvent=\"onclick: _handleClick\">\n\t\t\t<span dojoAttachPoint=\"navPrev\" class=\"slideShowCtrlPrev\"></span>\n\t\t\t<span dojoAttachPoint=\"navPlay\" class=\"slideShowCtrlPlay\"></span>\n\t\t\t<span dojoAttachPoint=\"navNext\" class=\"slideShowCtrlNext\"></span>\n\t\t</div>\n\t\t<div dojoAttachPoint=\"largeNode\" class=\"slideShowImageWrapper\"></div>\t\t\n\t\t<div dojoAttachPoint=\"hiddenNode\" class=\"slideShowHidden\"></div>\n\t</div>\n</div>\n",_imageCounter:0,_tmpImage:null,_request:null,postCreate:function(){
this.inherited(arguments);
var _1=document.createElement("img");
_1.setAttribute("width",this.imageWidth);
_1.setAttribute("height",this.imageHeight);
if(this.hasNav){
dojo.connect(this.outerNode,"onmouseover",this,function(_2){
try{
this._showNav();
}
catch(e){
}
});
dojo.connect(this.outerNode,"onmouseout",this,function(_3){
try{
this._hideNav(_3);
}
catch(e){
}
});
}
this.outerNode.style.width=this.imageWidth+"px";
_1.setAttribute("src",this._blankGif);
var _4=this;
this.largeNode.appendChild(_1);
this._tmpImage=this._currentImage=_1;
this._fitSize(true);
this._loadImage(0,dojo.hitch(this,"showImage",0));
this._calcNavDimensions();
},setDataStore:function(_5,_6,_7){
this.reset();
var _8=this;
this._request={query:{},start:_6.start||0,count:_6.count||this.pageSize,onBegin:function(_9,_a){
_8.maxPhotos=_9;
}};
if(_6.query){
dojo.mixin(this._request.query,_6.query);
}
if(_7){
dojo.forEach(["imageLargeAttr","linkAttr","titleAttr"],function(_b){
if(_7[_b]){
this[_b]=_7[_b];
}
},this);
}
var _c=function(_d){
_8.maxPhotos=_d.length;
_8.showImage(0);
_8._request.onComplete=null;
if(_8.autoStart){
_8.toggleSlideShow();
}
};
this.imageStore=_5;
this._request.onComplete=_c;
this._request.start=0;
this.imageStore.fetch(this._request);
},reset:function(){
while(this.largeNode.firstChild){
this.largeNode.removeChild(this.largeNode.firstChild);
}
this.largeNode.appendChild(this._tmpImage);
while(this.hiddenNode.firstChild){
this.hiddenNode.removeChild(this.hiddenNode.firstChild);
}
dojo.forEach(this.images,function(_e){
if(_e&&_e.parentNode){
_e.parentNode.removeChild(_e);
}
});
this.images=[];
this.isInitialized=false;
this._imageCounter=0;
},isImageLoaded:function(_f){
return this.images&&this.images.length>_f&&this.images[_f];
},moveImageLoadingPointer:function(_10){
this._imageCounter=_10;
},destroy:function(){
if(this._slideId){
this._stop();
}
this.inherited(arguments);
},showNextImage:function(_11,_12){
if(_11&&this._timerCancelled){
return false;
}
if(this.imageIndex+1>=this.maxPhotos){
if(_11&&(this.loop||_12)){
this.imageIndex=-1;
}else{
if(this._slideId){
this._stop();
}
return false;
}
}
this.showImage(this.imageIndex+1,dojo.hitch(this,function(){
if(_11){
this._startTimer();
}
}));
return true;
},toggleSlideShow:function(){
if(this._slideId){
this._stop();
}else{
dojo.toggleClass(this.domNode,"slideShowPaused");
this._timerCancelled=false;
if(this.images[this.imageIndex]&&this.images[this.imageIndex]._img.complete){
var _13=this.showNextImage(true,true);
if(!_13){
this._stop();
}
}else{
var idx=this.imageIndex;
var _15=dojo.subscribe(this.getShowTopicName(),dojo.hitch(this,function(_16){
setTimeout(dojo.hitch(this,function(){
if(_16.index==idx){
var _17=this.showNextImage(true,true);
if(!_17){
this._stop();
}
dojo.unsubscribe(_15);
}
}),this.slideshowInterval*1000);
}));
}
}
},getShowTopicName:function(){
return (this.widgetId||this.id)+"/imageShow";
},getLoadTopicName:function(){
return (this.widgetId?this.widgetId:this.id)+"/imageLoad";
},showImage:function(_18,_19){
if(!_19&&this._slideId){
this.toggleSlideShow();
}
var _1a=this;
var _1b=this.largeNode.getElementsByTagName("div");
this.imageIndex=_18;
var _1c=function(){
if(_1a.images[_18]){
while(_1a.largeNode.firstChild){
_1a.largeNode.removeChild(_1a.largeNode.firstChild);
}
dojo.style(_1a.images[_18],"opacity",0);
_1a.largeNode.appendChild(_1a.images[_18]);
_1a._currentImage=_1a.images[_18]._img;
_1a._fitSize();
var _1d=function(a,b,c){
var img=_1a.images[_18].firstChild;
if(img.tagName.toLowerCase()!="img"){
img=img.firstChild;
}
var _22=img.getAttribute("title")||"";
if(_1a._navShowing){
_1a._showNav(true);
}
dojo.publish(_1a.getShowTopicName(),[{index:_18,title:_22,url:img.getAttribute("src")}]);
if(_19){
_19(a,b,c);
}
_1a._setTitle(_22);
};
dojo.fadeIn({node:_1a.images[_18],duration:300,onEnd:_1d}).play();
}else{
_1a._loadImage(_18,function(){
dojo.publish(_1a.getLoadTopicName(),[_18]);
_1a.showImage(_18,_19);
});
}
};
if(_1b&&_1b.length>0){
dojo.fadeOut({node:_1b[0],duration:300,onEnd:function(){
_1a.hiddenNode.appendChild(_1b[0]);
_1c();
}}).play();
}else{
_1c();
}
},_fitSize:function(_23){
if(!this.fixedHeight||_23){
var _24=(this._currentImage.height+(this.hasNav?20:0));
dojo.style(this.innerWrapper,"height",_24+"px");
return;
}
dojo.style(this.largeNode,"paddingTop",this._getTopPadding()+"px");
},_getTopPadding:function(){
if(!this.fixedHeight){
return 0;
}
return (this.imageHeight-this._currentImage.height)/2;
},_loadNextImage:function(){
if(!this.autoLoad){
return;
}
while(this.images.length>=this._imageCounter&&this.images[this._imageCounter]){
this._imageCounter++;
}
this._loadImage(this._imageCounter);
},_loadImage:function(_25,_26){
if(this.images[_25]||!this._request){
return;
}
var _27=_25-(_25%this.pageSize);
this._request.start=_27;
this._request.onComplete=function(_28){
var _29=_25-_27;
if(_28&&_28.length>_29){
_2a(_28[_29]);
}else{
}
};
var _2b=this;
var _2a=function(_2c){
var url=_2b.imageStore.getValue(_2c,_2b.imageLargeAttr);
var img=new Image();
var div=document.createElement("div");
div._img=img;
var _30=_2b.imageStore.getValue(_2c,_2b.linkAttr);
if(!_30||_2b.noLink){
div.appendChild(img);
}else{
var a=document.createElement("a");
a.setAttribute("href",_30);
a.setAttribute("target","_blank");
div.appendChild(a);
a.appendChild(img);
}
div.setAttribute("id",_2b.id+"_imageDiv"+_25);
dojo.connect(img,"onload",function(){
_2b._fitImage(img);
div.setAttribute("width",_2b.imageWidth);
div.setAttribute("height",_2b.imageHeight);
dojo.publish(_2b.getLoadTopicName(),[_25]);
setTimeout(_2b._loadNextImage,1);
if(_26){
_26();
}
});
_2b.hiddenNode.appendChild(div);
var _32=document.createElement("div");
dojo.addClass(_32,"slideShowTitle");
div.appendChild(_32);
_2b.images[_25]=div;
img.setAttribute("src",url);
var _33=_2b.imageStore.getValue(_2c,_2b.titleAttr);
if(_33){
img.setAttribute("title",_33);
}
};
this.imageStore.fetch(this._request);
},_stop:function(){
if(this._slideId){
clearTimeout(this._slideId);
}
this._slideId=null;
this._timerCancelled=true;
dojo.removeClass(this.domNode,"slideShowPaused");
},_prev:function(){
if(this.imageIndex<1){
return;
}
this.showImage(this.imageIndex-1);
},_next:function(){
this.showNextImage();
},_startTimer:function(){
var id=this.id;
this._slideId=setTimeout(function(){
dijit.byId(id).showNextImage(true);
},this.slideshowInterval*1000);
},_calcNavDimensions:function(){
dojo.style(this.navNode,"position","absolute");
dojo.style(this.navNode,"top","-10000px");
dojo._setOpacity(this.navNode,99);
this.navPlay._size=dojo.marginBox(this.navPlay);
this.navPrev._size=dojo.marginBox(this.navPrev);
this.navNext._size=dojo.marginBox(this.navNext);
dojo._setOpacity(this.navNode,0);
dojo.style(this.navNode,"position","");
dojo.style(this.navNode,"top","");
},_setTitle:function(_35){
this.titleNode.innerHTML=dojo.string.substitute(this.titleTemplate,{title:_35,current:1+this.imageIndex,total:this.maxPhotos||""});
},_fitImage:function(img){
var _37=img.width;
var _38=img.height;
if(_37>this.imageWidth){
_38=Math.floor(_38*(this.imageWidth/_37));
img.height=_38;
img.width=this.imageWidth;
}
if(_38>this.imageHeight){
_37=Math.floor(_37*(this.imageHeight/_38));
img.height=this.imageHeight;
img.width=_37;
}
},_handleClick:function(e){
switch(e.target){
case this.navNext:
this._next();
break;
case this.navPrev:
this._prev();
break;
case this.navPlay:
this.toggleSlideShow();
break;
}
},_showNav:function(_3a){
if(this._navShowing&&!_3a){
return;
}
dojo.style(this.navNode,"marginTop","0px");
dojo.style(this.navPlay,"marginLeft","0px");
var _3b=dojo.marginBox(this.outerNode);
var _3c=this._currentImage.height-this.navPlay._size.h-10+this._getTopPadding();
if(_3c>this._currentImage.height){
_3c+=10;
}
dojo[this.imageIndex<1?"addClass":"removeClass"](this.navPrev,"slideShowCtrlHide");
dojo[this.imageIndex+1>=this.maxPhotos?"addClass":"removeClass"](this.navNext,"slideShowCtrlHide");
var _3d=this;
if(this._navAnim){
this._navAnim.stop();
}
if(this._navShowing){
return;
}
this._navAnim=dojo.fadeIn({node:this.navNode,duration:300,onEnd:function(){
_3d._navAnim=null;
}});
this._navAnim.play();
this._navShowing=true;
},_hideNav:function(e){
if(!e||!this._overElement(this.outerNode,e)){
var _3f=this;
if(this._navAnim){
this._navAnim.stop();
}
this._navAnim=dojo.fadeOut({node:this.navNode,duration:300,onEnd:function(){
_3f._navAnim=null;
}});
this._navAnim.play();
this._navShowing=false;
}
},_overElement:function(_40,e){
if(typeof (dojo)=="undefined"){
return false;
}
_40=dojo.byId(_40);
var m={x:e.pageX,y:e.pageY};
var bb=dojo._getBorderBox(_40);
var _44=dojo.coords(_40,true);
var _45=_44.x;
return (m.x>=_45&&m.x<=(_45+bb.w)&&m.y>=_44.y&&m.y<=(top+bb.h));
}});
}
