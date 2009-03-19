/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.html.metrics"]){
dojo._hasResource["dojox.html.metrics"]=true;
dojo.provide("dojox.html.metrics");
(function(){
var _1=dojox.html.metrics;
_1.getFontMeasurements=function(){
var _2={"1em":0,"1ex":0,"100%":0,"12pt":0,"16px":0,"xx-small":0,"x-small":0,"small":0,"medium":0,"large":0,"x-large":0,"xx-large":0};
if(dojo.isIE){
dojo.doc.documentElement.style.fontSize="100%";
}
var _3=dojo.doc.createElement("div");
var ds=_3.style;
ds.position="absolute";
ds.left="-100px";
ds.top="0";
ds.width="30px";
ds.height="1000em";
ds.border="0";
ds.margin="0";
ds.padding="0";
ds.outline="0";
ds.lineHeight="1";
ds.overflow="hidden";
dojo.body().appendChild(_3);
for(var p in _2){
ds.fontSize=p;
_2[p]=Math.round(_3.offsetHeight*12/16)*16/12/1000;
}
dojo.body().removeChild(_3);
_3=null;
return _2;
};
var _6=null;
_1.getCachedFontMeasurements=function(_7){
if(_7||!_6){
_6=_1.getFontMeasurements();
}
return _6;
};
var _8=null,_9={};
_1.getTextBox=function(_a,_b,_c){
var m;
if(!_8){
m=_8=dojo.doc.createElement("div");
m.style.position="absolute";
m.style.left="-10000px";
m.style.top="0";
dojo.body().appendChild(m);
}else{
m=_8;
}
m.className="";
m.style.border="0";
m.style.margin="0";
m.style.padding="0";
m.style.outline="0";
if(arguments.length>1&&_b){
for(var i in _b){
if(i in _9){
continue;
}
m.style[i]=_b[i];
}
}
if(arguments.length>2&&_c){
m.className=_c;
}
m.innerHTML=_a;
return dojo.marginBox(m);
};
var _f={w:16,h:16};
_1.getScrollbar=function(){
return {w:_f.w,h:_f.h};
};
_1._fontResizeNode=null;
_1.initOnFontResize=function(_10){
var f=_1._fontResizeNode=dojo.doc.createElement("iframe");
var fs=f.style;
fs.position="absolute";
fs.width="5em";
fs.height="10em";
fs.top="-10000px";
f.src=dojo.config["dojoBlankHtmlUrl"]||dojo.moduleUrl("dojo","resources/blank.html");
dojo.body().appendChild(f);
if(dojo.isIE){
f.onreadystatechange=function(){
if(f.contentWindow.document.readyState=="complete"){
f.onresize=Function("window.parent."+dojox._scopeName+".html.metrics._fontresize()");
}
};
}else{
f.onload=function(){
f.contentWindow.onresize=Function("window.parent."+dojox._scopeName+".html.metrics._fontresize()");
};
}
_1.initOnFontResize=function(){
};
};
_1.onFontResize=function(){
};
_1._fontresize=function(){
_1.onFontResize();
};
dojo.addOnUnload(function(){
var f=_1._fontResizeNode;
if(f){
if(dojo.isIE&&f.onresize){
f.onresize=null;
}else{
if(f.contentWindow&&f.contentWindow.onresize){
f.contentWindow.onresize=null;
}
}
_1._fontResizeNode=null;
}
});
dojo.addOnLoad(function(){
try{
var n=dojo.doc.createElement("div");
n.style.cssText="top:0;left:0;width:100px;height:100px;overflow:scroll;position:absolute;visibility:hidden;";
dojo.body().appendChild(n);
_f.w=n.offsetWidth-n.clientWidth;
_f.h=n.offsetHeight-n.clientHeight;
dojo.body().removeChild(n);
delete n;
}
catch(e){
}
if("fontSizeWatch" in dojo.config&&!!dojo.config.fontSizeWatch){
_1.initOnFontResize();
}
});
})();
}
