/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.av.FLAudio"]){
dojo._hasResource["dojox.av.FLAudio"]=true;
dojo.provide("dojox.av.FLAudio");
dojo.experimental("dojox.av.FLAudio");
dojo.require("dijit._Widget");
dojo.require("dojox.embed.Flash");
dojo.require("dojox.av._Media");
dojo.require("dojox.timing.doLater");
dojo.declare("dojox.av.FLAudio",null,{id:"",initialVolume:0.7,initialPan:0,isDebug:false,statusInterval:200,_swfPath:dojo.moduleUrl("dojox.av","resources/audio.swf"),constructor:function(_1){
dojo.mixin(this,_1||{});
if(!this.id){
this.id="flaudio_"+new Date().getTime();
}
this.domNode=dojo.doc.createElement("div");
dojo.style(this.domNode,{postion:"relative",width:"1px",height:"1px",top:"1px",left:"1px"});
dojo.body().appendChild(this.domNode);
this.init();
},init:function(){
this._subs=[];
this.initialVolume=this._normalizeVolume(this.initialVolume);
var _2={path:this._swfPath.uri,width:"1px",height:"1px",minimumVersion:9,expressInstall:true,params:{wmode:"transparent"},vars:{id:this.id,autoPlay:this.autoPlay,initialVolume:this.initialVolume,initialPan:this.initialPan,statusInterval:this.statusInterval,isDebug:this.isDebug}};
this._sub("mediaError","onError");
this._sub("filesProgress","onLoadStatus");
this._sub("filesAllLoaded","onAllLoaded");
this._sub("mediaPosition","onPlayStatus");
this._sub("mediaMeta","onID3");
this._flashObject=new dojox.embed.Flash(_2,this.domNode);
this._flashObject.onError=function(_3){
console.warn("Flash Error:",_3);
alert(_3);
};
this._flashObject.onLoad=dojo.hitch(this,function(_4){
this.flashMedia=_4;
this.isPlaying=this.autoPlay;
this.isStopped=!this.autoPlay;
this.onLoad(this.flashMedia);
});
},load:function(_5){
if(dojox.timing.doLater(this.flashMedia,this)){
return false;
}
if(!_5.url){
throw new Error("An url is required for loading media");
return false;
}else{
_5.url=this._normalizeUrl(_5.url);
}
this.flashMedia.load(_5);
return _5.url;
},doPlay:function(_6){
this.flashMedia.doPlay(_6);
},pause:function(_7){
this.flashMedia.pause(_7);
},stop:function(_8){
this.flashMedia.doStop(_8);
},setVolume:function(_9){
this.flashMedia.setVolume(_9);
},setPan:function(_a){
this.flashMedia.setPan(_a);
},getVolume:function(_b){
return this.flashMedia.getVolume(_b);
},getPan:function(_c){
return this.flashMedia.getPan(_c);
},onError:function(_d){
console.warn("SWF ERROR:",_d);
},onLoadStatus:function(_e){
},onAllLoaded:function(){
},onPlayStatus:function(_f){
},onLoad:function(){
},onID3:function(evt){
},destroy:function(){
if(!this.flashMedia){
this._cons.push(dojo.connect(this,"onLoad",this,"destroy"));
return;
}
dojo.forEach(this._subs,function(s){
dojo.unsubscribe(s);
});
dojo.forEach(this._cons,function(c){
dojo.disconnect(c);
});
this._flashObject.destroy();
},_sub:function(_13,_14){
dojo.subscribe(this.id+"/"+_13,this,_14);
},_normalizeVolume:function(vol){
if(vol>1){
while(vol>1){
vol*=0.1;
}
}
return vol;
},_normalizeUrl:function(_16){
if(_16&&_16.toLowerCase().indexOf("http")<0){
var loc=window.location.href.split("/");
loc.pop();
loc=loc.join("/")+"/";
_16=loc+_16;
}
return _16;
}});
}
