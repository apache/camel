/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.Lightbox"]){
dojo._hasResource["dojox.image.Lightbox"]=true;
dojo.provide("dojox.image.Lightbox");
dojo.experimental("dojox.image.Lightbox");
dojo.require("dijit.Dialog");
dojo.require("dojox.fx._base");
dojo.declare("dojox.image.Lightbox",dijit._Widget,{group:"",title:"",href:"",duration:500,_allowPassthru:false,_attachedDialog:null,startup:function(){
this.inherited(arguments);
var _1=dijit.byId("dojoxLightboxDialog");
if(_1){
this._attachedDialog=_1;
}else{
this._attachedDialog=new dojox.image.LightboxDialog({id:"dojoxLightboxDialog"});
this._attachedDialog.startup();
}
if(!this.store){
this._addSelf();
this.connect(this.domNode,"onclick","_handleClick");
}
},_addSelf:function(){
this._attachedDialog.addImage({href:this.href,title:this.title},this.group||null);
},_handleClick:function(e){
if(!this._allowPassthru){
e.preventDefault();
}else{
return;
}
this.show();
},show:function(){
this._attachedDialog.show(this);
},disable:function(){
this._allowPassthru=true;
},enable:function(){
this._allowPassthru=false;
}});
dojo.declare("dojox.image.LightboxDialog",dijit.Dialog,{title:"",inGroup:null,imgUrl:dijit._Widget.prototype._blankGif,errorMessage:"Image not found.",adjust:true,_groups:{XnoGroupX:[]},errorImg:dojo.moduleUrl("dojox.image","resources/images/warning.png"),templateString:"<div class=\"dojoxLightbox\" dojoAttachPoint=\"containerNode\">\n\t<div style=\"position:relative\">\n\t\t<div dojoAttachPoint=\"imageContainer\" class=\"dojoxLightboxContainer\">\n\t\t\t<img dojoAttachPoint=\"imgNode\" src=\"${imgUrl}\" class=\"dojoxLightboxImage\" alt=\"${title}\">\n\t\t\t<div class=\"dojoxLightboxFooter\" dojoAttachPoint=\"titleNode\">\n\t\t\t\t<div class=\"dijitInline LightboxClose\" dojoAttachPoint=\"closeNode\"></div>\n\t\t\t\t<div class=\"dijitInline LightboxNext\" dojoAttachPoint=\"nextNode\"></div>\t\n\t\t\t\t<div class=\"dijitInline LightboxPrev\" dojoAttachPoint=\"prevNode\"></div>\n\t\t\t\t<div class=\"dojoxLightboxText\"><span dojoAttachPoint=\"textNode\">${title}</span><span dojoAttachPoint=\"groupCount\" class=\"dojoxLightboxGroupText\"></span></div>\n\t\t\t</div>\n\t\t</div>\n\t</div>\n</div>\n",startup:function(){
this.inherited(arguments);
this._animConnects=[];
this._clone=dojo.clone(this.imgNode);
this.connect(this.nextNode,"onclick","_nextImage");
this.connect(this.prevNode,"onclick","_prevImage");
this.connect(this.closeNode,"onclick","hide");
this._makeAnims();
this._vp=dijit.getViewport();
return this;
},show:function(_3){
var _t=this;
if(!_t.open){
_t.inherited(arguments);
this._modalconnects.push(dojo.connect(dojo.global,"onscroll",this,"_position"),dojo.connect(dojo.global,"onresize",this,"_position"),dojo.connect(dojo.body(),"onkeypress",this,"_handleKey"));
}
if(this._wasStyled){
dojo.destroy(_t.imgNode);
_t.imgNode=dojo.clone(_t._clone);
dojo.place(_t.imgNode,_t.imageContainer,"first");
_t._makeAnims();
_t._wasStyled=false;
}
dojo.style(_t.imgNode,"opacity","0");
dojo.style(_t.titleNode,"opacity","0");
_t._imageReady=false;
_t.imgNode.src=_3.href;
if((_3.group&&_3!=="XnoGroupX")||_t.inGroup){
if(!_t.inGroup){
_t.inGroup=_t._groups[(_3.group)];
dojo.forEach(_t.inGroup,function(g,i){
if(g.href==_3.href){
_t._positionIndex=i;
}
},_t);
}
if(!_t._positionIndex){
_t._positionIndex=0;
_t.imgNode.src=_t.inGroup[_t._positionIndex].href;
}
_t.groupCount.innerHTML=" ("+(_t._positionIndex+1)+" of "+_t.inGroup.length+")";
_t.prevNode.style.visibility="visible";
_t.nextNode.style.visibility="visible";
}else{
_t.groupCount.innerHTML="";
_t.prevNode.style.visibility="hidden";
_t.nextNode.style.visibility="hidden";
}
_t.textNode.innerHTML=_3.title;
if(!_t._imageReady||_t.imgNode.complete===true){
_t._imgConnect=dojo.connect(_t.imgNode,"onload",_t,function(){
_t._imageReady=true;
_t.resizeTo({w:_t.imgNode.width,h:_t.imgNode.height,duration:_t.duration});
dojo.disconnect(_t._imgConnect);
if(_t._imgError){
dojo.disconnect(_t._imgError);
}
});
_t._imgError=dojo.connect(_t.imgNode,"onerror",_t,function(){
dojo.disconnect(_t._imgError);
_t.imgNode.src=_t.errorImg;
_t._imageReady=true;
_t.textNode.innerHTML=_t.errorMessage;
});
if(dojo.isIE){
_t.imgNode.src=_t.imgNode.src;
}
}else{
_t.resizeTo({w:_t.imgNode.width,h:_t.imgNode.height,duration:1});
}
},_nextImage:function(){
if(!this.inGroup){
return;
}
if(this._positionIndex+1<this.inGroup.length){
this._positionIndex++;
}else{
this._positionIndex=0;
}
this._loadImage();
},_prevImage:function(){
if(this.inGroup){
if(this._positionIndex==0){
this._positionIndex=this.inGroup.length-1;
}else{
this._positionIndex--;
}
this._loadImage();
}
},_loadImage:function(){
this._loadingAnim.play(1);
},_prepNodes:function(){
this._imageReady=false;
this.show({href:this.inGroup[this._positionIndex].href,title:this.inGroup[this._positionIndex].title});
},resizeTo:function(_7){
var _8=((dojo.boxModel=="border-box"))?22:0;
if(this.adjust&&(_7.h+_8+80>this._vp.h||_7.w+_8+50>this._vp.w)){
_7=this._scaleToFit(_7);
}
var _9=dojox.fx.sizeTo({node:this.containerNode,duration:_7.duration||this.duration,width:_7.w+_8,height:_7.h+30+_8});
this.connect(_9,"onEnd","_showImage");
_9.play(15);
},_showImage:function(){
this._showImageAnim.play(1);
},_showNav:function(){
this._showNavAnim.play(1);
},hide:function(){
dojo.fadeOut({node:this.titleNode,duration:200,onEnd:dojo.hitch(this,function(){
this.imgNode.src=this._blankGif;
})}).play(5);
this.inherited(arguments);
this.inGroup=null;
this._positionIndex=null;
},addImage:function(_a,_b){
var g=_b;
if(!_a.href){
return;
}
if(g){
if(!this._groups[g]){
this._groups[g]=[];
}
this._groups[g].push(_a);
}else{
this._groups["XnoGroupX"].push(_a);
}
},_handleKey:function(e){
if(!this.open){
return;
}
var dk=dojo.keys;
var _f=(e.charCode==dk.SPACE?dk.SPACE:e.keyCode);
switch(_f){
case dk.ESCAPE:
this.hide();
break;
case dk.DOWN_ARROW:
case dk.RIGHT_ARROW:
case 78:
this._nextImage();
break;
case dk.UP_ARROW:
case dk.LEFT_ARROW:
case 80:
this._prevImage();
break;
}
},_scaleToFit:function(_10){
var ns={};
if(this._vp.h>this._vp.w){
ns.w=this._vp.w-70;
ns.h=ns.w*(_10.h/_10.w);
}else{
ns.h=this._vp.h-80;
ns.w=ns.h*(_10.w/_10.h);
}
this._wasStyled=true;
var s=this.imgNode.style;
s.height=ns.h+"px";
s.width=ns.w+"px";
ns.duration=_10.duration;
return ns;
},_position:function(e){
this.inherited(arguments);
this._vp=dijit.getViewport();
},_makeAnims:function(){
dojo.forEach(this._animConnects,dojo.disconnect);
this._animConnects=[];
this._showImageAnim=dojo.fadeIn({node:this.imgNode,duration:this.duration});
this._animConnects.push(dojo.connect(this._showImageAnim,"onEnd",this,"_showNav"));
this._loadingAnim=dojo.fx.combine([dojo.fadeOut({node:this.imgNode,duration:175}),dojo.fadeOut({node:this.titleNode,duration:175})]);
this._animConnects.push(dojo.connect(this._loadingAnim,"onEnd",this,"_prepNodes"));
this._showNavAnim=dojo.fadeIn({node:this.titleNode,duration:225});
}});
}
