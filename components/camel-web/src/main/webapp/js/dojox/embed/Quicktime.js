/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.embed.Quicktime"]){
dojo._hasResource["dojox.embed.Quicktime"]=true;
dojo.provide("dojox.embed.Quicktime");
(function(){
var _1,_2,_3,_4={width:320,height:240,redirect:null};
var _5="dojox-embed-quicktime-",_6=0;
var _7=dojo.moduleUrl("dojox","embed/resources/version.mov");
function _8(_9){
_9=dojo.mixin(dojo.clone(_4),_9||{});
if(!("path" in _9)){
console.error("dojox.embed.Quicktime(ctor):: no path reference to a QuickTime movie was provided.");
return null;
}
if(!("id" in _9)){
_9.id=(_5+_6++);
}
return _9;
};
var _a="This content requires the <a href=\"http://www.apple.com/quicktime/download/\" title=\"Download and install QuickTime.\">QuickTime plugin</a>.";
if(dojo.isIE){
_2=0;
_3=(function(){
try{
var o=new ActiveXObject("QuickTimeCheckObject.QuickTimeCheck.1");
if(o!==undefined){
var v=o.QuickTimeVersion.toString(16);
_2={major:parseInt(v.substring(0,1),10)||0,minor:parseInt(v.substring(1,2),10)||0,rev:parseInt(v.substring(2,3),10)||0};
return o.IsQuickTimeAvailable(0);
}
}
catch(e){
}
return false;
})();
_1=function(_d){
if(!_3){
return {id:null,markup:_a};
}
_d=_8(_d);
if(!_d){
return null;
}
var s="<object classid=\"clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B\" "+"codebase=\"http://www.apple.com/qtactivex/qtplugin.cab#version=6,0,2,0\" "+"id=\""+_d.id+"\" "+"width=\""+_d.width+"\" "+"height=\""+_d.height+"\">"+"<param name=\"src\" value=\""+_d.path+"\" />";
if(_d.params){
for(var p in _d.params){
s+="<param name=\""+p+"\" value=\""+_d.params[p]+"\" />";
}
}
s+="</object>";
return {id:_d.id,markup:s};
};
}else{
_3=(function(){
for(var i=0,l=navigator.plugins.length;i<l;i++){
if(navigator.plugins[i].name.indexOf("QuickTime")>-1){
return true;
}
}
return false;
})();
_1=function(_12){
if(!_3){
return {id:null,markup:_a};
}
_12=_8(_12);
if(!_12){
return null;
}
var s="<embed type=\"video/quicktime\" src=\""+_12.path+"\" "+"id=\""+_12.id+"\" "+"name=\""+_12.id+"\" "+"pluginspage=\"www.apple.com/quicktime/download\" "+"enablejavascript=\"true\" "+"width=\""+_12.width+"\" "+"height=\""+_12.height+"\"";
if(_12.params){
for(var p in _12.params){
s+=" "+p+"=\""+_12.params[p]+"\"";
}
}
s+="></embed>";
return {id:_12.id,markup:s};
};
}
dojox.embed.Quicktime=function(_15,_16){
return dojox.embed.Quicktime.place(_15,_16);
};
dojo.mixin(dojox.embed.Quicktime,{minSupported:6,available:_3,supported:_3,version:_2,initialized:false,onInitialize:function(){
dojox.embed.Quicktime.initialized=true;
},place:function(_17,_18){
var o=_1(_17);
_18=dojo.byId(_18);
if(!_18){
_18=dojo.doc.createElement("div");
_18.id=o.id+"-container";
dojo.body().appendChild(_18);
}
if(o){
_18.innerHTML=o.markup;
if(o.id){
return (dojo.isIE)?dojo.byId(o.id):document[o.id];
}
}
return null;
}});
if(!dojo.isIE){
_2=dojox.embed.Quicktime.version={major:0,minor:0,rev:0};
var o=_1({path:_7,width:4,height:4});
function _1b(){
if(!dojo._initFired){
var s="<div style=\"top:0;left:0;width:1px;height:1px;;overflow:hidden;position:absolute;\" id=\"-qt-version-test\">"+o.markup+"</div>";
document.write(s);
}else{
var n=document.createElement("div");
n.id="-qt-version-test";
n.style.cssText="top:0;left:0;width:1px;height:1px;overflow:hidden;position:absolute;";
dojo.body().appendChild(n);
n.innerHTML=o.markup;
}
};
function _1e(mv){
var qt,n,v=[0,0,0];
if(mv){
qt=mv,n=qt.parentNode;
}else{
if(o.id){
_1b();
if(!dojo.isOpera){
setTimeout(function(){
_1e(document[o.id]);
},50);
}else{
var fn=function(){
setTimeout(function(){
_1e(document[o.id]);
},50);
};
if(!dojo._initFired){
dojo.addOnLoad(fn);
}else{
dojo.connect(document[o.id],"onload",fn);
}
}
}
return;
}
if(qt){
try{
v=qt.GetQuickTimeVersion().split(".");
_2={major:parseInt(v[0]||0),minor:parseInt(v[1]||0),rev:parseInt(v[2]||0)};
}
catch(e){
_2={major:0,minor:0,rev:0};
}
}
dojox.embed.Quicktime.supported=v[0];
dojox.embed.Quicktime.version=_2;
if(dojox.embed.Quicktime.supported){
dojox.embed.Quicktime.onInitialize();
}else{

}
try{
if(!mv){
dojo.body().removeChild(n);
}
}
catch(e){
}
};
_1e();
}else{
if(dojo.isIE&&_3){
dojox.embed.Quicktime.onInitialize();
}
}
})();
}
