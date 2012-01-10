/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx._base"]){
dojo._hasResource["dojox.gfx._base"]=true;
dojo.provide("dojox.gfx._base");
(function(){
var g=dojox.gfx,b=g._base;
g._hasClass=function(_3,_4){
var _5=_3.getAttribute("className");
return _5&&(" "+_5+" ").indexOf(" "+_4+" ")>=0;
};
g._addClass=function(_6,_7){
var _8=_6.getAttribute("className")||"";
if(!_8||(" "+_8+" ").indexOf(" "+_7+" ")<0){
_6.setAttribute("className",_8+(_8?" ":"")+_7);
}
};
g._removeClass=function(_9,_a){
var _b=_9.getAttribute("className");
if(_b){
_9.setAttribute("className",_b.replace(new RegExp("(^|\\s+)"+_a+"(\\s+|$)"),"$1$2"));
}
};
b._getFontMeasurements=function(){
var _c={"1em":0,"1ex":0,"100%":0,"12pt":0,"16px":0,"xx-small":0,"x-small":0,"small":0,"medium":0,"large":0,"x-large":0,"xx-large":0};
if(dojo.isIE){
dojo.doc.documentElement.style.fontSize="100%";
}
var _d=dojo.doc.createElement("div");
_d.style.position="absolute";
_d.style.left="-100px";
_d.style.top="0";
_d.style.width="30px";
_d.style.height="1000em";
_d.style.border="0";
_d.style.margin="0";
_d.style.padding="0";
_d.style.outline="0";
_d.style.lineHeight="1";
_d.style.overflow="hidden";
dojo.body().appendChild(_d);
for(var p in _c){
_d.style.fontSize=p;
_c[p]=Math.round(_d.offsetHeight*12/16)*16/12/1000;
}
dojo.body().removeChild(_d);
_d=null;
return _c;
};
var _f=null;
b._getCachedFontMeasurements=function(_10){
if(_10||!_f){
_f=b._getFontMeasurements();
}
return _f;
};
var _11=null,_12={};
b._getTextBox=function(_13,_14,_15){
var m;
if(!_11){
m=_11=dojo.doc.createElement("div");
m.style.position="absolute";
m.style.left="-10000px";
m.style.top="0";
dojo.body().appendChild(m);
}else{
m=_11;
}
m.className="";
m.style.border="0";
m.style.margin="0";
m.style.padding="0";
m.style.outline="0";
if(arguments.length>1&&_14){
for(var i in _14){
if(i in _12){
continue;
}
m.style[i]=_14[i];
}
}
if(arguments.length>2&&_15){
m.className=_15;
}
m.innerHTML=_13;
return dojo.marginBox(m);
};
var _18=0;
b._getUniqueId=function(){
var id;
do{
id=dojo._scopeName+"Unique"+(++_18);
}while(dojo.byId(id));
return id;
};
})();
dojo.mixin(dojox.gfx,{defaultPath:{type:"path",path:""},defaultPolyline:{type:"polyline",points:[]},defaultRect:{type:"rect",x:0,y:0,width:100,height:100,r:0},defaultEllipse:{type:"ellipse",cx:0,cy:0,rx:200,ry:100},defaultCircle:{type:"circle",cx:0,cy:0,r:100},defaultLine:{type:"line",x1:0,y1:0,x2:100,y2:100},defaultImage:{type:"image",x:0,y:0,width:0,height:0,src:""},defaultText:{type:"text",x:0,y:0,text:"",align:"start",decoration:"none",rotated:false,kerning:true},defaultTextPath:{type:"textpath",text:"",align:"start",decoration:"none",rotated:false,kerning:true},defaultStroke:{type:"stroke",color:"black",style:"solid",width:1,cap:"butt",join:4},defaultLinearGradient:{type:"linear",x1:0,y1:0,x2:100,y2:100,colors:[{offset:0,color:"black"},{offset:1,color:"white"}]},defaultRadialGradient:{type:"radial",cx:0,cy:0,r:100,colors:[{offset:0,color:"black"},{offset:1,color:"white"}]},defaultPattern:{type:"pattern",x:0,y:0,width:0,height:0,src:""},defaultFont:{type:"font",style:"normal",variant:"normal",weight:"normal",size:"10pt",family:"serif"},normalizeColor:function(_1a){
return (_1a instanceof dojo.Color)?_1a:new dojo.Color(_1a);
},normalizeParameters:function(_1b,_1c){
if(_1c){
var _1d={};
for(var x in _1b){
if(x in _1c&&!(x in _1d)){
_1b[x]=_1c[x];
}
}
}
return _1b;
},makeParameters:function(_1f,_20){
if(!_20){
return dojo.clone(_1f);
}
var _21={};
for(var i in _1f){
if(!(i in _21)){
_21[i]=dojo.clone((i in _20)?_20[i]:_1f[i]);
}
}
return _21;
},formatNumber:function(x,_24){
var val=x.toString();
if(val.indexOf("e")>=0){
val=x.toFixed(4);
}else{
var _26=val.indexOf(".");
if(_26>=0&&val.length-_26>5){
val=x.toFixed(4);
}
}
if(x<0){
return val;
}
return _24?" "+val:val;
},makeFontString:function(_27){
return _27.style+" "+_27.variant+" "+_27.weight+" "+_27.size+" "+_27.family;
},splitFontString:function(str){
var _29=dojo.clone(dojox.gfx.defaultFont);
var t=str.split(/\s+/);
do{
if(t.length<5){
break;
}
_29.style=t[0];
_29.varian=t[1];
_29.weight=t[2];
var i=t[3].indexOf("/");
_29.size=i<0?t[3]:t[3].substring(0,i);
var j=4;
if(i<0){
if(t[4]=="/"){
j=6;
break;
}
if(t[4].substr(0,1)=="/"){
j=5;
break;
}
}
if(j+3>t.length){
break;
}
_29.size=t[j];
_29.family=t[j+1];
}while(false);
return _29;
},cm_in_pt:72/2.54,mm_in_pt:7.2/2.54,px_in_pt:function(){
return dojox.gfx._base._getCachedFontMeasurements()["12pt"]/12;
},pt2px:function(len){
return len*dojox.gfx.px_in_pt();
},px2pt:function(len){
return len/dojox.gfx.px_in_pt();
},normalizedLength:function(len){
if(len.length==0){
return 0;
}
if(len.length>2){
var _30=dojox.gfx.px_in_pt();
var val=parseFloat(len);
switch(len.slice(-2)){
case "px":
return val;
case "pt":
return val*_30;
case "in":
return val*72*_30;
case "pc":
return val*12*_30;
case "mm":
return val*dojox.gfx.mm_in_pt*_30;
case "cm":
return val*dojox.gfx.cm_in_pt*_30;
}
}
return parseFloat(len);
},pathVmlRegExp:/([A-Za-z]+)|(\d+(\.\d+)?)|(\.\d+)|(-\d+(\.\d+)?)|(-\.\d+)/g,pathSvgRegExp:/([A-Za-z])|(\d+(\.\d+)?)|(\.\d+)|(-\d+(\.\d+)?)|(-\.\d+)/g,equalSources:function(a,b){
return a&&b&&a==b;
}});
}
