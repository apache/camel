/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.av.widget.Status"]){
dojo._hasResource["dojox.av.widget.Status"]=true;
dojo.provide("dojox.av.widget.Status");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dojox.av.widget.Status",[dijit._Widget,dijit._Templated],{templateString:"<table class=\"Status\">\n    <tr>\n        <td class=\"Time\"><span dojoAttachPoint=\"timeNode\">0.00</span></td>\n        <td class=\"Status\"><div dojoAttachPoint=\"titleNode\">Loading...</div></td>\n        <td class=\"Duration\"><span dojoAttachPoint=\"durNode\">0.00</span></td>\n    </tr>\n</table>\n",postCreate:function(){
this.titleNode=dojo.query(".Status",this.domNode);
this.durNode=dojo.query(".Duration",this.domNode);
this.timeNode=dojo.query(".Time",this.domNode);



},setMedia:function(_1){
this.media=_1;
dojo.connect(this.media,"onMetaData",this,function(_2){
this.duration=_2.duration;
this.durNode.innerHTML=this.toSeconds(this.duration);
});
dojo.connect(this.media,"onPosition",this,function(_3){
});
var _4=["onMetaData","onPosition","onStart","onBuffer","onPlay","onPause","onStop","onEnd","onError","onLoad"];
dojo.forEach(_4,function(c){
dojo.connect(this.media,c,this,c);
},this);
},onMetaData:function(_6){
this.duration=_6.duration;
this.durNode.innerHTML=this.toSeconds(this.duration);
if(this.media.title){
this.title=this.media.title;
}else{
var a=this.media.mediaUrl.split("/");
var b=a[a.length-1].split(".")[0];
this.title=b;
}
},onBuffer:function(_9){
this.isBuffering=_9;
console.warn("status onBuffer",this.isBuffering);
if(this.isBuffering){
this.setStatus("buffering...");
}else{
this.setStatus("Playing");
}
},onPosition:function(_a){
},onStart:function(){
this.setStatus("Starting");
},onPlay:function(){
this.setStatus("Playing");
},onPause:function(){
this.setStatus("Paused");
},onStop:function(){
this.setStatus("Stopped");
},onEnd:function(){
this.setStatus("Stopped");
},onError:function(_b){

var _c=_b.info.code;
if(_c=="NetStream.Play.StreamNotFound"){
_c="Stream Not Found";
}
this.setStatus("ERROR: "+_c,true);
},onLoad:function(){
this.setStatus("Loading...");
},setStatus:function(_d,_e){
if(_e){
dojo.addClass(this.titleNode,"statusError");
}else{
dojo.removeClass(this.titleNode,"statusError");
if(this.isBuffering){
_d="buffering...";
}
}
this.titleNode.innerHTML="<span class=\"statusTitle\">"+this.title+"</span> <span class=\"statusInfo\">"+_d+"</span>";
},toSeconds:function(_f){
var ts=_f.toString();
if(ts.indexOf(".")<0){
ts+=".00";
}else{
if(ts.length-ts.indexOf(".")==2){
ts+="0";
}else{
if(ts.length-ts.indexOf(".")>2){
ts=ts.substring(0,ts.indexOf(".")+3);
}
}
}
return ts;
}});
}
