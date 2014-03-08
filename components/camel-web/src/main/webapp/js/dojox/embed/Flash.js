/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.embed.Flash"]){
dojo._hasResource["dojox.embed.Flash"]=true;
dojo.provide("dojox.embed.Flash");
(function(){
var _1,_2;
var _3=9;
var _4="dojox-embed-flash-",_5=0;
var _6={expressInstall:false,width:320,height:240,swLiveConnect:"true",allowScriptAccess:"sameDomain",allowNetworking:"all",style:null,redirect:null};
function _7(_8){
_8=dojo.delegate(_6,_8);
if(!("path" in _8)){
console.error("dojox.embed.Flash(ctor):: no path reference to a Flash movie was provided.");
return null;
}
if(!("id" in _8)){
_8.id=(_4+_5++);
}
return _8;
};
if(dojo.isIE){
_1=function(_9){
_9=_7(_9);
if(!_9){
return null;
}
var p;
var _b=_9.path;
if(_9.vars){
var a=[];
for(p in _9.vars){
a.push(p+"="+_9.vars[p]);
}
_b+=((_b.indexOf("?")==-1)?"?":"&")+a.join("&");
}
var s="<object id=\""+_9.id+"\" "+"classid=\"clsid:D27CDB6E-AE6D-11cf-96B8-444553540000\" "+"width=\""+_9.width+"\" "+"height=\""+_9.height+"\""+((_9.style)?" style=\""+_9.style+"\"":"")+">"+"<param name=\"movie\" value=\""+_b+"\" />";
if(_9.params){
for(p in _9.params){
s+="<param name=\""+p+"\" value=\""+_9.params[p]+"\" />";
}
}
s+="</object>";
return {id:_9.id,markup:s};
};
_2=(function(){
var _e=10,_f=null;
while(!_f&&_e>7){
try{
_f=new ActiveXObject("ShockwaveFlash.ShockwaveFlash."+_e--);
}
catch(e){
}
}
if(_f){
var v=_f.GetVariable("$version").split(" ")[1].split(",");
return {major:(v[0]!=null)?parseInt(v[0]):0,minor:(v[1]!=null)?parseInt(v[1]):0,rev:(v[2]!=null)?parseInt(v[2]):0};
}
return {major:0,minor:0,rev:0};
})();
dojo.addOnUnload(function(){
var _12=function(){
};
var _13=dojo.query("object").reverse().style("display","none").forEach(function(i){
for(var p in i){
if((p!="FlashVars")&&dojo.isFunction(i[p])){
try{
i[p]=_12;
}
catch(e){
}
}
}
});
});
}else{
_1=function(_16){
_16=_7(_16);
if(!_16){
return null;
}
var p;
var _18=_16.path;
if(_16.vars){
var a=[];
for(p in _16.vars){
a.push(p+"="+_16.vars[p]);
}
_18+=((_18.indexOf("?")==-1)?"?":"&")+a.join("&");
}
var s="<embed type=\"application/x-shockwave-flash\" "+"src=\""+_18+"\" "+"id=\""+_16.id+"\" "+"width=\""+_16.width+"\" "+"height=\""+_16.height+"\""+((_16.style)?" style=\""+_16.style+"\" ":"")+"swLiveConnect=\""+_16.swLiveConnect+"\" "+"allowScriptAccess=\""+_16.allowScriptAccess+"\" "+"allowNetworking=\""+_16.allowNetworking+"\" "+"pluginspage=\""+window.location.protocol+"//www.adobe.com/go/getflashplayer\" ";
if(_16.params){
for(p in _16.params){
s+=" "+p+"=\""+_16.params[p]+"\"";
}
}
s+=" />";
return {id:_16.id,markup:s};
};
_2=(function(){
var _1b=navigator.plugins["Shockwave Flash"];
if(_1b&&_1b.description){
var v=_1b.description.replace(/([a-zA-Z]|\s)+/,"").replace(/(\s+r|\s+b[0-9]+)/,".").split(".");
return {major:(v[0]!=null)?parseInt(v[0]):0,minor:(v[1]!=null)?parseInt(v[1]):0,rev:(v[2]!=null)?parseInt(v[2]):0};
}
return {major:0,minor:0,rev:0};
})();
}
dojox.embed.Flash=function(_1d,_1e){
if(location.href.toLowerCase().indexOf("file://")>-1){
throw new Error("dojox.embed.Flash can't be run directly from a file. To instatiate the required SWF correctly it must be run from a server, like localHost.");
}
this.available=dojox.embed.Flash.available;
this.minimumVersion=_1d.minimumVersion||_3;
this.id=null;
this.movie=null;
this.domNode=null;
if(_1e){
_1e=dojo.byId(_1e);
}
setTimeout(dojo.hitch(this,function(){
if(this.available&&this.available>=this.minimumVersion){
if(_1d&&_1e){
this.init(_1d,_1e);
}
}else{
if(!this.available){
this.onError("Flash is not installed.");
}else{
this.onError("Flash version detected: "+this.available+" is out of date. Minimum required: "+this.minimumVersion);
}
}
}),100);
};
dojo.extend(dojox.embed.Flash,{onReady:function(_1f){
},onLoad:function(_20){
},onError:function(msg){
},_onload:function(){
clearInterval(this._poller);
delete this._poller;
delete this._pollCount;
delete this._pollMax;
this.onLoad(this.movie);
},init:function(_22,_23){
this.destroy();
_23=dojo.byId(_23||this.domNode);
if(!_23){
throw new Error("dojox.embed.Flash: no domNode reference has been passed.");
}
var p=0,_25=false;
this._poller=null;
this._pollCount=0;
this._pollMax=5;
this.pollTime=100;
if(dojox.embed.Flash.initialized){
this.id=dojox.embed.Flash.place(_22,_23);
this.domNode=_23;
setTimeout(dojo.hitch(this,function(){
this.movie=dojox.embed.Flash.byId(this.id);
this.onReady(this.movie);
this._poller=setInterval(dojo.hitch(this,function(){
try{
p=this.movie.PercentLoaded();
}
catch(e){
console.warn("this.movie.PercentLoaded() failed");
}
if(p==100){
this._onload();
}else{
if(p==0&&this._pollCount++>this._pollMax){
throw new Error("Building SWF failed.");
}
}
}),this.pollTime);
}),1);
}
},_destroy:function(){
try{
this.domNode.removeChild(this.movie);
}
catch(e){
}
this.id=this.movie=this.domNode=null;
},destroy:function(){
if(!this.movie){
return;
}
var _26=dojo.delegate({id:true,movie:true,domNode:true,onReady:true,onLoad:true});
for(var p in this){
if(!_26[p]){
delete this[p];
}
}
if(this._poller){
dojo.connect(this,"onLoad",this,"_destroy");
}else{
this._destroy();
}
}});
dojo.mixin(dojox.embed.Flash,{byId:function(_28){
if(document.embeds[_28]){
return document.embeds[_28];
}
if(window.document[_28]){
return window.document[_28];
}
if(window[_28]){
return window[_28];
}
if(document[_28]){
return document[_28];
}
return null;
}});
dojo.mixin(dojox.embed.Flash,{minSupported:8,available:_2.major,supported:(_2.major>=_2.required),minimumRequired:_2.required,version:_2,initialized:false,onInitialize:function(){
dojox.embed.Flash.initialized=true;
},__ie_markup__:function(_29){
return _1(_29);
},proxy:function(obj,_2b){
dojo.forEach((dojo.isArray(_2b)?_2b:[_2b]),function(_2c){
this[_2c]=dojo.hitch(this,function(){
return (function(){
return eval(this.movie.CallFunction("<invoke name=\""+_2c+"\" returntype=\"javascript\">"+"<arguments>"+dojo.map(arguments,function(_2d){
return __flash__toXML(_2d);
}).join("")+"</arguments>"+"</invoke>"));
}).apply(this,arguments||[]);
});
},obj);
}});
if(dojo.isIE){
if(dojo._initFired){
var e=document.createElement("script");
e.type="text/javascript";
e.src=dojo.moduleUrl("dojox","embed/IE/flash.js");
document.getElementsByTagName("head")[0].appendChild(e);
}else{
document.write("<scr"+"ipt type=\"text/javascript\" src=\""+dojo.moduleUrl("dojox","embed/IE/flash.js")+"\">"+"</scr"+"ipt>");
}
}else{
dojox.embed.Flash.place=function(_2e,_2f){
var o=_1(_2e);
_2f=dojo.byId(_2f);
if(!_2f){
_2f=dojo.doc.createElement("div");
_2f.id=o.id+"-container";
dojo.body().appendChild(_2f);
}
if(o){
_2f.innerHTML=o.markup;
return o.id;
}
return null;
};
dojox.embed.Flash.onInitialize();
}
})();
}
