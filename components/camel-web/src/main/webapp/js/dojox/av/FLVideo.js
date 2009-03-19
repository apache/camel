/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.av.FLVideo"]){
dojo._hasResource["dojox.av.FLVideo"]=true;
dojo.provide("dojox.av.FLVideo");
dojo.experimental("dojox.av.FLVideo");
dojo.require("dijit._Widget");
dojo.require("dojox.embed.Flash");
dojo.require("dojox.av._Media");
dojo.declare("dojox.av.FLVideo",[dijit._Widget,dojox.av._Media],{_swfPath:dojo.moduleUrl("dojox.av","resources/video.swf"),postCreate:function(){
this._subs=[];
this._cons=[];
this.mediaUrl=this._normalizeUrl(this.mediaUrl);
this.initialVolume=this._normalizeVolume(this.initialVolume);
var _1={path:this._swfPath.uri,width:"100%",height:"100%",minimumVersion:9,expressInstall:true,params:{allowFullScreen:true,wmode:"transparent"},vars:{videoUrl:this.mediaUrl,id:this.id,autoPlay:this.autoPlay,volume:this.initialVolume,isDebug:this.isDebug}};
this._sub("stageClick","onClick");
this._sub("stageSized","onSwfSized");
this._sub("mediaStatus","onPlayerStatus");
this._sub("mediaMeta","onMetaData");
this._sub("mediaError","onError");
this._sub("mediaStart","onStart");
this._sub("mediaEnd","onEnd");
this._flashObject=new dojox.embed.Flash(_1,this.domNode);
this._flashObject.onError=function(_2){
console.warn("Flash Error:",_2);
alert(_2);
};
this._flashObject.onLoad=dojo.hitch(this,function(_3){
this.flashMedia=_3;
this.isPlaying=this.autoPlay;
this.isStopped=!this.autoPlay;
this.onLoad(this.flashMedia);
this._initStatus();
this._update();
});
},play:function(_4){
this.isPlaying=true;
this.isStopped=false;
this.flashMedia.doPlay(this._normalizeUrl(_4));
},pause:function(){
this.isPlaying=false;
this.isStopped=false;
this.flashMedia.pause();
},seek:function(_5){
this.flashMedia.seek(_5);
},volume:function(_6){
if(_6){
if(!this.flashMedia){
this.initialVolume=_6;
}
this.flashMedia.setVolume(this._normalizeVolume(_6));
}
if(!this.flashMedia||!this.flashMedia.doGetVolume){
return this.initialVolume;
}
return this.flashMedia.getVolume();
},_checkBuffer:function(_7,_8){
if(this.percentDownloaded==100){
if(this.isBuffering){
this.onBuffer(false);
this.flashMedia.doPlay();
}
return;
}
if(!this.isBuffering&&_8<0.1){
this.onBuffer(true);
this.flashMedia.pause();
return;
}
var _9=this.percentDownloaded*0.01*this.duration;
if(!this.isBuffering&&_7+this.minBufferTime*0.001>_9){
this.onBuffer(true);
this.flashMedia.pause();
}else{
if(this.isBuffering&&_7+this.bufferTime*0.001<=_9){
this.onBuffer(false);
this.flashMedia.doPlay();
}
}
},_update:function(){
var _a=Math.min(this.getTime()||0,this.duration);
var _b=this.flashMedia.getLoaded();
this.percentDownloaded=Math.ceil(_b.bytesLoaded/_b.bytesTotal*100);
this.onDownloaded(this.percentDownloaded);
this.onPosition(_a);
if(this.duration){
this._checkBuffer(_a,_b.buffer);
}
setTimeout(dojo.hitch(this,"_update"),this.updateTime);
}});
}
