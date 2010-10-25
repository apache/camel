/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.flash._base"]){
dojo._hasResource["dojox.flash._base"]=true;
dojo.provide("dojox.flash._base");
dojo.experimental("dojox.flash");
dojo.require("dijit._base.place");
dojox.flash=function(){
};
dojox.flash={ready:false,url:null,_visible:true,_loadedListeners:[],_installingListeners:[],setSwf:function(_1,_2){
this.url=_1;
this._visible=true;
if(_2!==null&&_2!==undefined){
this._visible=_2;
}
this._initialize();
},addLoadedListener:function(_3){
this._loadedListeners.push(_3);
},addInstallingListener:function(_4){
this._installingListeners.push(_4);
},loaded:function(){
dojox.flash.ready=true;
if(dojox.flash._loadedListeners.length){
for(var i=0;i<dojox.flash._loadedListeners.length;i++){
dojox.flash._loadedListeners[i].call(null);
}
}
},installing:function(){
if(dojox.flash._installingListeners.length){
for(var i=0;i<dojox.flash._installingListeners.length;i++){
dojox.flash._installingListeners[i].call(null);
}
}
},_initialize:function(){
var _7=new dojox.flash.Install();
dojox.flash.installer=_7;
if(_7.needed()){
_7.install();
}else{
dojox.flash.obj=new dojox.flash.Embed(this._visible);
dojox.flash.obj.write();
dojox.flash.comm=new dojox.flash.Communicator();
}
}};
dojox.flash.Info=function(){
this._detectVersion();
};
dojox.flash.Info.prototype={version:-1,versionMajor:-1,versionMinor:-1,versionRevision:-1,capable:false,installing:false,isVersionOrAbove:function(_8,_9,_a){
_a=parseFloat("."+_a);
if(this.versionMajor>=_8&&this.versionMinor>=_9&&this.versionRevision>=_a){
return true;
}else{
return false;
}
},_detectVersion:function(){
var _b;
for(var _c=25;_c>0;_c--){
if(dojo.isIE){
var _d;
try{
if(_c>6){
_d=new ActiveXObject("ShockwaveFlash.ShockwaveFlash."+_c);
}else{
_d=new ActiveXObject("ShockwaveFlash.ShockwaveFlash");
}
if(typeof _d=="object"){
if(_c==6){
_d.AllowScriptAccess="always";
}
_b=_d.GetVariable("$version");
}
}
catch(e){
continue;
}
}else{
_b=this._JSFlashInfo(_c);
}
if(_b==-1){
this.capable=false;
return;
}else{
if(_b!=0){
var _e;
if(dojo.isIE){
var _f=_b.split(" ");
var _10=_f[1];
_e=_10.split(",");
}else{
_e=_b.split(".");
}
this.versionMajor=_e[0];
this.versionMinor=_e[1];
this.versionRevision=_e[2];
var _11=this.versionMajor+"."+this.versionRevision;
this.version=parseFloat(_11);
this.capable=true;
break;
}
}
}
},_JSFlashInfo:function(_12){
if(navigator.plugins!=null&&navigator.plugins.length>0){
if(navigator.plugins["Shockwave Flash 2.0"]||navigator.plugins["Shockwave Flash"]){
var _13=navigator.plugins["Shockwave Flash 2.0"]?" 2.0":"";
var _14=navigator.plugins["Shockwave Flash"+_13].description;
var _15=_14.split(" ");
var _16=_15[2].split(".");
var _17=_16[0];
var _18=_16[1];
var _19=(_15[3]||_15[4]).split("r");
var _1a=_19[1]>0?_19[1]:0;
var _1b=_17+"."+_18+"."+_1a;
return _1b;
}
}
return -1;
}};
dojox.flash.Embed=function(_1c){
this._visible=_1c;
};
dojox.flash.Embed.prototype={width:215,height:138,id:"flashObject",_visible:true,protocol:function(){
switch(window.location.protocol){
case "https:":
return "https";
break;
default:
return "http";
break;
}
},write:function(_1d){
var _1e;
var _1f=dojox.flash.url;
var _20=_1f;
var _21=_1f;
var _22=dojo.baseUrl;
var _23=document.location.protocol+"//"+document.location.host;
if(_1d){
var _24=escape(window.location);
document.title=document.title.slice(0,47)+" - Flash Player Installation";
var _25=escape(document.title);
_20+="?MMredirectURL="+_24+"&MMplayerType=ActiveX"+"&MMdoctitle="+_25+"&baseUrl="+escape(_22)+"&xdomain="+escape(_23);
_21+="?MMredirectURL="+_24+"&MMplayerType=PlugIn"+"&baseUrl="+escape(_22)+"&xdomain="+escape(_23);
}else{
_20+="?cachebust="+new Date().getTime();
_20+="&baseUrl="+escape(_22);
_20+="&xdomain="+escape(_23);
}
if(_21.indexOf("?")==-1){
_21+="?baseUrl="+escape(_22);
}else{
_21+="&baseUrl="+escape(_22);
}
_21+="&xdomain="+escape(_23);
_1e="<object classid=\"clsid:d27cdb6e-ae6d-11cf-96b8-444553540000\" "+"codebase=\""+this.protocol()+"://fpdownload.macromedia.com/pub/shockwave/cabs/flash/"+"swflash.cab#version=8,0,0,0\"\n "+"width=\""+this.width+"\"\n "+"height=\""+this.height+"\"\n "+"id=\""+this.id+"\"\n "+"name=\""+this.id+"\"\n "+"align=\"middle\">\n "+"<param name=\"allowScriptAccess\" value=\"always\"></param>\n "+"<param name=\"movie\" value=\""+_20+"\"></param>\n "+"<param name=\"quality\" value=\"high\"></param>\n "+"<param name=\"bgcolor\" value=\"#ffffff\"></param>\n "+"<embed src=\""+_21+"\" "+"quality=\"high\" "+"bgcolor=\"#ffffff\" "+"width=\""+this.width+"\" "+"height=\""+this.height+"\" "+"id=\""+this.id+"Embed"+"\" "+"name=\""+this.id+"\" "+"swLiveConnect=\"true\" "+"align=\"middle\" "+"allowScriptAccess=\"always\" "+"type=\"application/x-shockwave-flash\" "+"pluginspage=\""+this.protocol()+"://www.macromedia.com/go/getflashplayer\" "+"></embed>\n"+"</object>\n";
dojo.connect(dojo,"loaded",dojo.hitch(this,function(){
var _26=this.id+"Container";
if(dojo.byId(_26)){
return;
}
var div=document.createElement("div");
div.id=this.id+"Container";
div.style.width=this.width+"px";
div.style.height=this.height+"px";
if(!this._visible){
div.style.position="absolute";
div.style.zIndex="10000";
div.style.top="-1000px";
}
div.innerHTML=_1e;
var _28=document.getElementsByTagName("body");
if(!_28||!_28.length){
throw new Error("No body tag for this page");
}
_28=_28[0];
_28.appendChild(div);
}));
},get:function(){
if(dojo.isIE||dojo.isWebKit){
return dojo.byId(this.id);
}else{
return document[this.id+"Embed"];
}
},setVisible:function(_29){
var _2a=dojo.byId(this.id+"Container");
if(_29){
_2a.style.position="absolute";
_2a.style.visibility="visible";
}else{
_2a.style.position="absolute";
_2a.style.y="-1000px";
_2a.style.visibility="hidden";
}
},center:function(){
var _2b=this.width;
var _2c=this.height;
var _2d=dijit.getViewport();
var x=_2d.l+(_2d.w-_2b)/2;
var y=_2d.t+(_2d.h-_2c)/2;
var _30=dojo.byId(this.id+"Container");
_30.style.top=y+"px";
_30.style.left=x+"px";
}};
dojox.flash.Communicator=function(){
};
dojox.flash.Communicator.prototype={_addExternalInterfaceCallback:function(_31){
var _32=dojo.hitch(this,function(){
var _33=new Array(arguments.length);
for(var i=0;i<arguments.length;i++){
_33[i]=this._encodeData(arguments[i]);
}
var _35=this._execFlash(_31,_33);
_35=this._decodeData(_35);
return _35;
});
this[_31]=_32;
},_encodeData:function(_36){
if(!_36||typeof _36!="string"){
return _36;
}
_36=_36.replace("\\","&custom_backslash;");
_36=_36.replace(/\0/g,"&custom_null;");
return _36;
},_decodeData:function(_37){
if(_37&&_37.length&&typeof _37!="string"){
_37=_37[0];
}
if(!_37||typeof _37!="string"){
return _37;
}
_37=_37.replace(/\&custom_null\;/g,"\x00");
_37=_37.replace(/\&custom_lt\;/g,"<").replace(/\&custom_gt\;/g,">").replace(/\&custom_backslash\;/g,"\\");
return _37;
},_execFlash:function(_38,_39){
var _3a=dojox.flash.obj.get();
_39=(_39)?_39:[];
for(var i=0;i<_39;i++){
if(typeof _39[i]=="string"){
_39[i]=this._encodeData(_39[i]);
}
}
var _3c=function(){
return eval(_3a.CallFunction("<invoke name=\""+_38+"\" returntype=\"javascript\">"+__flash__argumentsToXML(_39,0)+"</invoke>"));
};
var _3d=_3c.call(_39);
if(typeof _3d=="string"){
_3d=this._decodeData(_3d);
}
return _3d;
}};
dojox.flash.Install=function(){
};
dojox.flash.Install.prototype={needed:function(){
if(!dojox.flash.info.capable){
return true;
}
if(!dojox.flash.info.isVersionOrAbove(8,0,0)){
return true;
}
return false;
},install:function(){
var _3e;
dojox.flash.info.installing=true;
dojox.flash.installing();
if(dojox.flash.info.capable==false){
_3e=new dojox.flash.Embed(false);
_3e.write();
}else{
if(dojox.flash.info.isVersionOrAbove(6,0,65)){
_3e=new dojox.flash.Embed(false);
_3e.write(true);
_3e.setVisible(true);
_3e.center();
}else{
alert("This content requires a more recent version of the Macromedia "+" Flash Player.");
window.location.href=+dojox.flash.Embed.protocol()+"://www.macromedia.com/go/getflashplayer";
}
}
},_onInstallStatus:function(msg){
if(msg=="Download.Complete"){
dojox.flash._initialize();
}else{
if(msg=="Download.Cancelled"){
alert("This content requires a more recent version of the Macromedia "+" Flash Player.");
window.location.href=dojox.flash.Embed.protocol()+"://www.macromedia.com/go/getflashplayer";
}else{
if(msg=="Download.Failed"){
alert("There was an error downloading the Flash Player update. "+"Please try again later, or visit macromedia.com to download "+"the latest version of the Flash plugin.");
}
}
}
}};
dojox.flash.info=new dojox.flash.Info();
}
