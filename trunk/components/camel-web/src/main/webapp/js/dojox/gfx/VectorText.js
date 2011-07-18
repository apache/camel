/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.VectorText"]){
dojo._hasResource["dojox.gfx.VectorText"]=true;
dojo.provide("dojox.gfx.VectorText");
dojo.require("dojox.gfx");
dojo.require("dojox.xml.DomParser");
dojo.require("dojox.html.metrics");
(function(){
dojo.mixin(dojox.gfx,{vectorFontFitting:{NONE:0,FLOW:1,FIT:2},defaultVectorText:{type:"vectortext",x:0,y:0,width:null,height:null,text:"",align:"start",decoration:"none",fitting:0,leading:1.5},defaultVectorFont:{type:"vectorfont",size:"10pt",family:null},_vectorFontCache:{},_svgFontCache:{},getVectorFont:function(_1){
if(dojox.gfx._vectorFontCache[_1]){
return dojox.gfx._vectorFontCache[_1];
}
return new dojox.gfx.VectorFont(_1);
}});
dojo.declare("dojox.gfx.VectorFont",null,{_entityRe:/&(quot|apos|lt|gt|amp|#x[^;]+|#\d+);/g,_decodeEntitySequence:function(_2){
if(!_2.match(this._entityRe)){
return;
}
var _3={amp:"&",apos:"'",quot:"\"",lt:"<",gt:">"};
var r,_5="";
while((r=this._entityRe.exec(_2))!==null){
if(r[1].charAt(1)=="x"){
_5+=String.fromCharCode(r[1].slice(2),16);
}else{
if(!isNaN(parseInt(r[1].slice(1),10))){
_5+=String.fromCharCode(r[1].slice(1));
}else{
_5+=_3(r[1]);
}
}
}
return _5;
},_parse:function(_6,_7){
var _8=dojox.gfx._svgFontCache[_7]||dojox.xml.DomParser.parse(_6);
var f=_8.documentElement.byName("font")[0],_a=_8.documentElement.byName("font-face")[0];
var _b=parseFloat(_a.getAttribute("units-per-em")||1000,10);
var _c={x:parseFloat(f.getAttribute("horiz-adv-x"),10),y:parseFloat(f.getAttribute("vert-adv-y")||0,10)};
if(!_c.y){
_c.y=_b;
}
var _d={horiz:{x:parseFloat(f.getAttribute("horiz-origin-x")||0,10),y:parseFloat(f.getAttribute("horiz-origin-y")||0,10)},vert:{x:parseFloat(f.getAttribute("vert-origin-x")||0,10),y:parseFloat(f.getAttribute("vert-origin-y")||0,10)}};
var _e=_a.getAttribute("font-family"),_f=_a.getAttribute("font-style")||"all",_10=_a.getAttribute("font-variant")||"normal",_11=_a.getAttribute("font-weight")||"all",_12=_a.getAttribute("font-stretch")||"normal",_13=_a.getAttribute("unicode-range")||"U+0-10FFFF",_14=_a.getAttribute("panose-1")||"0 0 0 0 0 0 0 0 0 0",_15=_a.getAttribute("cap-height"),_16=parseFloat(_a.getAttribute("ascent")||(_b-_d.vert.y),10),_17=parseFloat(_a.getAttribute("descent")||_d.vert.y,10),_18={};
var _19=_e;
if(_a.byName("font-face-name")[0]){
_19=_a.byName("font-face-name")[0].getAttribute("name");
}
if(dojox.gfx._vectorFontCache[_19]){
return;
}
dojo.forEach(["alphabetic","ideographic","mathematical","hanging"],function(_1a){
var a=_a.getAttribute(_1a);
if(a!==null){
_18[_1a]=parseFloat(a,10);
}
});
var _1c=parseFloat(_8.documentElement.byName("missing-glyph")[0].getAttribute("horiz-adv-x")||_c.x,10);
var _1d={},_1e={},g=_8.documentElement.byName("glyph");
dojo.forEach(g,function(_20){
var _21=_20.getAttribute("unicode"),_19=_20.getAttribute("glyph-name"),_22=parseFloat(_20.getAttribute("horiz-adv-x")||_c.x,10),_23=_20.getAttribute("d");
if(_21.match(this._entityRe)){
_21=this._decodeEntitySequence(_21);
}
var o={code:_21,name:_19,xAdvance:_22,path:_23};
_1d[_21]=o;
_1e[_19]=o;
},this);
var _25=_8.documentElement.byName("hkern");
dojo.forEach(_25,function(_26,i){
var k=-parseInt(_26.getAttribute("k"),10);
var u1=_26.getAttribute("u1"),g1=_26.getAttribute("g1"),u2=_26.getAttribute("u2"),g2=_26.getAttribute("g2"),gl;
if(u1){
u1=this._decodeEntitySequence(u1);
if(_1d[u1]){
gl=_1d[u1];
}
}else{
if(_1e[g1]){
gl=_1e[g1];
}
}
if(gl){
if(!gl.kern){
gl.kern={};
}
if(u2){
u2=this._decodeEntitySequence(u2);
gl.kern[u2]={x:k};
}else{
if(_1e[g2]){
gl.kern[_1e[g2].code]={x:k};
}
}
}
},this);
dojo.mixin(this,{family:_e,name:_19,style:_f,variant:_10,weight:_11,stretch:_12,range:_13,viewbox:{width:_b,height:_b},origin:_d,advance:dojo.mixin(_c,{missing:{x:_1c,y:_1c}}),ascent:_16,descent:_17,baseline:_18,glyphs:_1d});
dojox.gfx._vectorFontCache[_19]=this;
dojox.gfx._vectorFontCache[_7]=this;
if(_19!=_e&&!dojox.gfx._vectorFontCache[_e]){
dojox.gfx._vectorFontCache[_e]=this;
}
if(!dojox.gfx._svgFontCache[_7]){
dojox.gfx._svgFontCache[_7]=_8;
}
},_clean:function(){
var _2e=this.name,_2f=this.family;
dojo.forEach(["family","name","style","variant","weight","stretch","range","viewbox","origin","advance","ascent","descent","baseline","glyphs"],function(_30){
try{
delete this[_30];
}
catch(e){
}
},this);
if(dojox.gfx._vectorFontCache[_2e]){
delete dojox.gfx._vectorFontCache[_2e];
}
if(dojox.gfx._vectorFontCache[_2f]){
delete dojox.gfx._vectorFontCache[_2f];
}
return this;
},constructor:function(url){
this._defaultLeading=1.5;
if(url!==undefined){
this.load(url);
}
},load:function(url){
this.onLoadBegin(url.toString());
this._parse(dojox.gfx._svgFontCache[url.toString()]||dojo._getText(url.toString()),url.toString());
this.onLoad(this);
return this;
},initialized:function(){
return (this.glyphs!==null);
},_round:function(n){
return Math.round(1000*n)/1000;
},_leading:function(_34){
return this.viewbox.height*(_34||this._defaultLeading);
},_normalize:function(str){
return str.replace(/\s+/g,String.fromCharCode(32));
},_getWidth:function(_36){
var w=0,_38=0,_39=null;
dojo.forEach(_36,function(_3a,i){
_38=_3a.xAdvance;
if(_36[i]&&_3a.kern&&_3a.kern[_36[i].code]){
_38+=_3a.kern[_36[i].code].x;
}
w+=_38;
_39=_3a;
});
if(_39&&_39.code==" "){
w-=_39.xAdvance;
}
return this._round(w);
},_getLongestLine:function(_3c){
var _3d=0,idx=0;
dojo.forEach(_3c,function(_3f,i){
var max=Math.max(_3d,this._getWidth(_3f));
if(max>_3d){
_3d=max;
idx=i;
}
},this);
return {width:_3d,index:idx,line:_3c[idx]};
},_trim:function(_42){
var fn=function(arr){
if(!arr.length){
return;
}
if(arr[arr.length-1].code==" "){
arr.splice(arr.length-1,1);
}
if(!arr.length){
return;
}
if(arr[0].code==" "){
arr.splice(0,1);
}
};
if(dojo.isArray(_42[0])){
dojo.forEach(_42,fn);
}else{
fn(_42);
}
return _42;
},_split:function(_45,_46){
var w=this._getWidth(_45),_48=Math.floor(w/_46),_49=[],cw=0,c=[],_4c=false;
for(var i=0,l=_45.length;i<l;i++){
if(_45[i].code==" "){
_4c=true;
}
cw+=_45[i].xAdvance;
if(i+1<l&&_45[i].kern&&_45[i].kern[_45[i+1].code]){
cw+=_45[i].kern[_45[i+1].code].x;
}
if(cw>=_48){
var chr=_45[i];
while(_4c&&chr.code!=" "&&i>=0){
chr=c.pop();
i--;
}
_49.push(c);
c=[];
cw=0;
_4c=false;
}
c.push(_45[i]);
}
if(c.length){
_49.push(c);
}
return this._trim(_49);
},_getSizeFactor:function(_50){
_50+="";
var _51=dojox.html.metrics.getCachedFontMeasurements(),_52=this.viewbox.height,f=_51["1em"],_54=parseFloat(_50,10);
if(_50.indexOf("em")>-1){
return this._round((_51["1em"]*_54)/_52);
}else{
if(_50.indexOf("ex")>-1){
return this._round((_51["1ex"]*_54)/_52);
}else{
if(_50.indexOf("pt")>-1){
return this._round(((_51["12pt"]/12)*_54)/_52);
}else{
if(_50.indexOf("px")>-1){
return this._round(((_51["16px"]/16)*_54)/_52);
}else{
if(_50.indexOf("%")>-1){
return this._round((_51["1em"]*(_54/100))/_52);
}else{
f=_51[_50]||_51.medium;
return this._round(f/_52);
}
}
}
}
}
},_getFitFactor:function(_55,w,h,l){
if(!h){
return this._round(w/this._getWidth(_55));
}else{
var _59=this._getLongestLine(_55).width,_5a=(_55.length*(this.viewbox.height*l))-((this.viewbox.height*l)-this.viewbox.height);
return this._round(Math.min(w/_59,h/_5a));
}
},_getBestFit:function(_5b,w,h,_5e){
var _5f=32,_60=0,_61=_5f;
while(_5f>0){
var f=this._getFitFactor(this._split(_5b,_5f),w,h,_5e);
if(f>_60){
_60=f;
_61=_5f;
}
_5f--;
}
return {scale:_60,lines:this._split(_5b,_61)};
},_getBestFlow:function(_63,w,_65){
var _66=[],cw=0,c=[],_69=false;
for(var i=0,l=_63.length;i<l;i++){
if(_63[i].code==" "){
_69=true;
}
var tw=_63[i].xAdvance;
if(i+1<l&&_63[i].kern&&_63[i].kern[_63[i+1].code]){
tw+=_63[i].kern[_63[i+1].code].x;
}
cw+=_65*tw;
if(cw>=w){
var chr=_63[i];
while(_69&&chr.code!=" "&&i>=0){
chr=c.pop();
i--;
}
_66.push(c);
c=[];
cw=0;
_69=false;
}
c.push(_63[i]);
}
if(c.length){
_66.push(c);
}
return this._trim(_66);
},getWidth:function(_6e,_6f){
return this._getWidth(dojo.map(this._normalize(_6e).split(""),function(chr){
return this.glyphs[chr]||{xAdvance:this.advance.missing.x};
},this))*(_6f||1);
},getLineHeight:function(_71){
return this.viewbox.height*(_71||1);
},getCenterline:function(_72){
return (_72||1)*(this.viewbox.height/2);
},getBaseline:function(_73){
return (_73||1)*(this.viewbox.height+this.descent);
},draw:function(_74,_75,_76,_77,_78){
if(!this.initialized()){
throw new Error("dojox.gfx.VectorFont.draw(): we have not been initialized yet.");
}
var g=_74.createGroup();
if(_75.x||_75.y){
_74.applyTransform({dx:_75.x||0,dy:_75.y||0});
}
var _7a=dojo.map(this._normalize(_75.text).split(""),function(chr){
return this.glyphs[chr]||{path:null,xAdvance:this.advance.missing.x};
},this);
var _7c=_76.size,_7d=_75.fitting,_7e=_75.width,_7f=_75.height,_80=_75.align,_81=_75.leading||this._defaultLeading;
if(_7d){
if((_7d==dojox.gfx.vectorFontFitting.FLOW&&!_7e)||(_7d==dojox.gfx.vectorFontFitting.FIT&&(!_7e||!_7f))){
_7d=dojox.gfx.vectorFontFitting.NONE;
}
}
var _82,_83;
switch(_7d){
case dojox.gfx.vectorFontFitting.FIT:
var o=this._getBestFit(_7a,_7e,_7f,_81);
_83=o.scale;
_82=o.lines;
break;
case dojox.gfx.vectorFontFitting.FLOW:
_83=this._getSizeFactor(_7c);
_82=this._getBestFlow(_7a,_7e,_83);
break;
default:
_83=this._getSizeFactor(_7c);
_82=[_7a];
}
_82=dojo.filter(_82,function(_85){
return _85.length>0;
});
var cy=0,_87=this._getLongestLine(_82).width;
for(var i=0,l=_82.length;i<l;i++){
var cx=0,_8b=_82[i],_8c=this._getWidth(_8b),lg=g.createGroup();
for(var j=0;j<_8b.length;j++){
var _8f=_8b[j];
if(_8f.path!==null){
var p=lg.createPath(_8f.path).setFill(_77);
if(_78){
p.setStroke(_78);
}
p.setTransform([dojox.gfx.matrix.flipY,dojox.gfx.matrix.translate(cx,-this.viewbox.height-this.descent)]);
}
cx+=_8f.xAdvance;
if(j+1<_8b.length&&_8f.kern&&_8f.kern[_8b[j+1].code]){
cx+=_8f.kern[_8b[j+1].code].x;
}
}
var dx=0;
if(_80=="middle"){
dx=_87/2-_8c/2;
}else{
if(_80=="end"){
dx=_87-_8c;
}
}
lg.setTransform({dx:dx,dy:cy});
cy+=this.viewbox.height*_81;
}
g.setTransform(dojox.gfx.matrix.scale(_83));
return g;
},onLoadBegin:function(url){
},onLoad:function(_93){
}});
})();
}
