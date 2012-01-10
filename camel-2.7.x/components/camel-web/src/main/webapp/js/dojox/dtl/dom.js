/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.dom"]){
dojo._hasResource["dojox.dtl.dom"]=true;
dojo.provide("dojox.dtl.dom");
dojo.require("dojox.dtl._base");
dojo.require("dojox.dtl.Context");
(function(){
var dd=dojox.dtl;
dd.BOOLS={checked:1,disabled:1,readonly:1};
dd.TOKEN_CHANGE=-11;
dd.TOKEN_ATTR=-12;
dd.TOKEN_CUSTOM=-13;
dd.TOKEN_NODE=1;
var _2=dd.text;
var _3=dd.dom={_attributes:{},_uppers:{},_re4:/^function anonymous\(\)\s*{\s*(.*)\s*}$/,_reTrim:/(?:^[\n\s]*(\{%)?\s*|\s*(%\})?[\n\s]*$)/g,_reSplit:/\s*%\}[\n\s]*\{%\s*/g,getTemplate:function(_4){
if(typeof this._commentable=="undefined"){
this._commentable=false;
var _5=document.createElement("div");
_5.innerHTML="<!--Test comment handling, and long comments, using comments whenever possible.-->";
if(_5.childNodes.length&&_5.childNodes[0].nodeType==8&&_5.childNodes[0].data=="comment"){
this._commentable=true;
}
}
if(!this._commentable){
_4=_4.replace(/<!--({({|%).*?(%|})})-->/g,"$1");
}
if(dojo.isIE){
_4=_4.replace(/\b(checked|disabled|readonly|style)="/g,"t$1=\"");
}
_4=_4.replace(/\bstyle="/g,"tstyle=\"");
var _6;
var _7=dojo.isWebKit;
var _8=[[true,"select","option"],[_7,"tr","td|th"],[_7,"thead","tr","th"],[_7,"tbody","tr","td"],[_7,"table","tbody|thead|tr","tr","td"],];
var _9=[];
for(var i=0,_b;_b=_8[i];i++){
if(!_b[0]){
continue;
}
if(_4.indexOf("<"+_b[1])!=-1){
var _c=new RegExp("<"+_b[1]+"(?:.|\n)*?>((?:.|\n)+?)</"+_b[1]+">","ig");
tagLoop:
while(_6=_c.exec(_4)){
var _d=_b[2].split("|");
var _e=[];
for(var j=0,_10;_10=_d[j];j++){
_e.push("<"+_10+"(?:.|\n)*?>(?:.|\n)*?</"+_10+">");
}
var _11=[];
var _12=dojox.string.tokenize(_6[1],new RegExp("("+_e.join("|")+")","ig"),function(_13){
var tag=/<(\w+)/.exec(_13)[1];
if(!_11[tag]){
_11[tag]=true;
_11.push(tag);
}
return {data:_13};
});
if(_11.length){
var tag=(_11.length==1)?_11[0]:_b[2].split("|")[0];
var _16=[];
for(var j=0,jl=_12.length;j<jl;j++){
var _18=_12[j];
if(dojo.isObject(_18)){
_16.push(_18.data);
}else{
var _19=_18.replace(this._reTrim,"");
if(!_19){
continue;
}
_18=_19.split(this._reSplit);
for(var k=0,kl=_18.length;k<kl;k++){
var _1c="";
for(var p=2,pl=_b.length;p<pl;p++){
if(p==2){
_1c+="<"+tag+" dtlinstruction=\"{% "+_18[k].replace("\"","\\\"")+" %}\">";
}else{
if(tag==_b[p]){
continue;
}else{
_1c+="<"+_b[p]+">";
}
}
}
_1c+="DTL";
for(var p=_b.length-1;p>1;p--){
if(p==2){
_1c+="</"+tag+">";
}else{
if(tag==_b[p]){
continue;
}else{
_1c+="</"+_b[p]+">";
}
}
}
_16.push("ÿ"+_9.length);
_9.push(_1c);
}
}
}
_4=_4.replace(_6[1],_16.join(""));
}
}
}
}
for(var i=_9.length;i--;){
_4=_4.replace("ÿ"+i,_9[i]);
}
var re=/\b([a-zA-Z_:][a-zA-Z0-9_\-\.:]*)=['"]/g;
while(_6=re.exec(_4)){
var _20=_6[1].toLowerCase();
if(_20=="dtlinstruction"){
continue;
}
if(_20!=_6[1]){
this._uppers[_20]=_6[1];
}
this._attributes[_20]=true;
}
var _5=document.createElement("div");
_5.innerHTML=_4;
var _21={nodes:[]};
while(_5.childNodes.length){
_21.nodes.push(_5.removeChild(_5.childNodes[0]));
}
return _21;
},tokenize:function(_22){
var _23=[];
for(var i=0,_25;_25=_22[i++];){
if(_25.nodeType!=1){
this.__tokenize(_25,_23);
}else{
this._tokenize(_25,_23);
}
}
return _23;
},_swallowed:[],_tokenize:function(_26,_27){
var _28=false;
var _29=this._swallowed;
var i,j,tag,_2d;
if(!_27.first){
_28=_27.first=true;
var _2e=dd.register.getAttributeTags();
for(i=0;tag=_2e[i];i++){
try{
(tag[2])({swallowNode:function(){
throw 1;
}},new dd.Token(dd.TOKEN_ATTR,""));
}
catch(e){
_29.push(tag);
}
}
}
for(i=0;tag=_29[i];i++){
var _2f=_26.getAttribute(tag[0]);
if(_2f){
var _29=false;
var _30=(tag[2])({swallowNode:function(){
_29=true;
return _26;
}},new dd.Token(dd.TOKEN_ATTR,_2f));
if(_29){
if(_26.parentNode&&_26.parentNode.removeChild){
_26.parentNode.removeChild(_26);
}
_27.push([dd.TOKEN_CUSTOM,_30]);
return;
}
}
}
var _31=[];
if(dojo.isIE&&_26.tagName=="SCRIPT"){
_31.push({nodeType:3,data:_26.text});
_26.text="";
}else{
for(i=0;_2d=_26.childNodes[i];i++){
_31.push(_2d);
}
}
_27.push([dd.TOKEN_NODE,_26]);
var _32=false;
if(_31.length){
_27.push([dd.TOKEN_CHANGE,_26]);
_32=true;
}
for(var key in this._attributes){
var _34=false;
var _35="";
if(key=="class"){
_35=_26.className||_35;
}else{
if(key=="for"){
_35=_26.htmlFor||_35;
}else{
if(key=="value"&&_26.value==_26.innerHTML){
continue;
}else{
if(_26.getAttribute){
_35=_26.getAttribute(key,2)||_35;
if(key=="href"||key=="src"){
if(dojo.isIE){
var _36=location.href.lastIndexOf(location.hash);
var _37=location.href.substring(0,_36).split("/");
_37.pop();
_37=_37.join("/")+"/";
if(_35.indexOf(_37)==0){
_35=_35.replace(_37,"");
}
_35=decodeURIComponent(_35);
}
}else{
if(key=="tstyle"){
_34=key;
key="style";
}else{
if(dd.BOOLS[key.slice(1)]&&dojo.trim(_35)){
key=key.slice(1);
}else{
if(this._uppers[key]&&dojo.trim(_35)){
_34=this._uppers[key];
}
}
}
}
}
}
}
}
if(_34){
_26.setAttribute(_34,"");
_26.removeAttribute(_34);
}
if(typeof _35=="function"){
_35=_35.toString().replace(this._re4,"$1");
}
if(!_32){
_27.push([dd.TOKEN_CHANGE,_26]);
_32=true;
}
_27.push([dd.TOKEN_ATTR,_26,key,_35]);
}
for(i=0,_2d;_2d=_31[i];i++){
if(_2d.nodeType==1){
var _38=_2d.getAttribute("dtlinstruction");
if(_38){
_2d.parentNode.removeChild(_2d);
_2d={nodeType:8,data:_38};
}
}
this.__tokenize(_2d,_27);
}
if(!_28&&_26.parentNode&&_26.parentNode.tagName){
if(_32){
_27.push([dd.TOKEN_CHANGE,_26,true]);
}
_27.push([dd.TOKEN_CHANGE,_26.parentNode]);
_26.parentNode.removeChild(_26);
}else{
_27.push([dd.TOKEN_CHANGE,_26,true,true]);
}
},__tokenize:function(_39,_3a){
var _3b=_39.data;
switch(_39.nodeType){
case 1:
this._tokenize(_39,_3a);
return;
case 3:
if(_3b.match(/[^\s\n]/)&&(_3b.indexOf("{{")!=-1||_3b.indexOf("{%")!=-1)){
var _3c=_2.tokenize(_3b);
for(var j=0,_3e;_3e=_3c[j];j++){
if(typeof _3e=="string"){
_3a.push([dd.TOKEN_TEXT,_3e]);
}else{
_3a.push(_3e);
}
}
}else{
_3a.push([_39.nodeType,_39]);
}
if(_39.parentNode){
_39.parentNode.removeChild(_39);
}
return;
case 8:
if(_3b.indexOf("{%")==0){
var _3e=dojo.trim(_3b.slice(2,-2));
if(_3e.substr(0,5)=="load "){
var _3f=dojo.trim(_3e).split(/\s+/g);
for(var i=1,_41;_41=_3f[i];i++){
dojo["require"](_41);
}
}
_3a.push([dd.TOKEN_BLOCK,_3e]);
}
if(_3b.indexOf("{{")==0){
_3a.push([dd.TOKEN_VAR,dojo.trim(_3b.slice(2,-2))]);
}
if(_39.parentNode){
_39.parentNode.removeChild(_39);
}
return;
}
}};
dd.DomTemplate=dojo.extend(function(obj){
if(!obj.nodes){
var _43=dojo.byId(obj);
if(_43&&_43.nodeType==1){
dojo.forEach(["class","src","href","name","value"],function(_44){
_3._attributes[_44]=true;
});
obj={nodes:[_43]};
}else{
if(typeof obj=="object"){
obj=_2.getTemplateString(obj);
}
obj=_3.getTemplate(obj);
}
}
var _45=_3.tokenize(obj.nodes);
if(dd.tests){
this.tokens=_45.slice(0);
}
var _46=new dd._DomParser(_45);
this.nodelist=_46.parse();
},{_count:0,_re:/\bdojo:([a-zA-Z0-9_]+)\b/g,setClass:function(str){
this.getRootNode().className=str;
},getRootNode:function(){
return this.buffer.rootNode;
},getBuffer:function(){
return new dd.DomBuffer();
},render:function(_48,_49){
_49=this.buffer=_49||this.getBuffer();
this.rootNode=null;
var _4a=this.nodelist.render(_48||new dd.Context({}),_49);
for(var i=0,_4c;_4c=_49._cache[i];i++){
if(_4c._cache){
_4c._cache.length=0;
}
}
return _4a;
},unrender:function(_4d,_4e){
return this.nodelist.unrender(_4d,_4e);
}});
dd.DomBuffer=dojo.extend(function(_4f){
this._parent=_4f;
this._cache=[];
},{concat:function(_50){
var _51=this._parent;
if(_51&&_50.parentNode&&_50.parentNode===_51&&!_51._dirty){
return this;
}
if(_50.nodeType==1&&!this.rootNode){
this.rootNode=_50||true;
return this;
}
if(!_51){
if(_50.nodeType==3&&dojo.trim(_50.data)){
throw new Error("Text should not exist outside of the root node in template");
}
return this;
}
if(this._closed){
if(_50.nodeType==3&&!dojo.trim(_50.data)){
return this;
}else{
throw new Error("Content should not exist outside of the root node in template");
}
}
if(_51._dirty){
if(_50._drawn&&_50.parentNode==_51){
var _52=_51._cache;
if(_52){
for(var i=0,_54;_54=_52[i];i++){
this.onAddNode&&this.onAddNode(_54);
_51.insertBefore(_54,_50);
this.onAddNodeComplete&&this.onAddNodeComplete(_54);
}
_52.length=0;
}
}
_51._dirty=false;
}
if(!_51._cache){
_51._cache=[];
this._cache.push(_51);
}
_51._dirty=true;
_51._cache.push(_50);
return this;
},remove:function(obj){
if(typeof obj=="string"){
if(this._parent){
this._parent.removeAttribute(obj);
}
}else{
if(obj.nodeType==1&&!this.getRootNode()&&!this._removed){
this._removed=true;
return this;
}
if(obj.parentNode){
this.onRemoveNode&&this.onRemoveNode(obj);
if(obj.parentNode){
obj.parentNode.removeChild(obj);
}
}
}
return this;
},setAttribute:function(key,_57){
var old=dojo.attr(this._parent,key);
if(this.onChangeAttribute&&old!=_57){
this.onChangeAttribute(this._parent,key,old,_57);
}
if(key=="style"){

this._parent.style.cssText=_57;
}else{
dojo.attr(this._parent,key,_57);

}
return this;
},addEvent:function(_59,_5a,fn,_5c){
if(!_59.getThis()){
throw new Error("You must use Context.setObject(instance)");
}
this.onAddEvent&&this.onAddEvent(this.getParent(),_5a,fn);
var _5d=fn;
if(dojo.isArray(_5c)){
_5d=function(e){
this[fn].apply(this,[e].concat(_5c));
};
}
return dojo.connect(this.getParent(),_5a,_59.getThis(),_5d);
},setParent:function(_5f,up,_61){
if(!this._parent){
this._parent=this._first=_5f;
}
if(up&&_61&&_5f===this._first){
this._closed=true;
}
if(up){
var _62=this._parent;
var _63="";
var ie=dojo.isIE&&_62.tagName=="SCRIPT";
if(ie){
_62.text="";
}
if(_62._dirty){
var _65=_62._cache;
var _66=(_62.tagName=="SELECT"&&!_62.options.length);
for(var i=0,_68;_68=_65[i];i++){
if(_68!==_62){
this.onAddNode&&this.onAddNode(_68);
if(ie){
_63+=_68.data;
}else{
_62.appendChild(_68);
if(_66&&_68.defaultSelected&&i){
_66=i;
}
}
this.onAddNodeComplete&&this.onAddNodeComplete(_68);
}
}
if(_66){
_62.options.selectedIndex=(typeof _66=="number")?_66:0;
}
_65.length=0;
_62._dirty=false;
}
if(ie){
_62.text=_63;
}
}
this._parent=_5f;
this.onSetParent&&this.onSetParent(_5f,up,_61);
return this;
},getParent:function(){
return this._parent;
},getRootNode:function(){
return this.rootNode;
}});
dd._DomNode=dojo.extend(function(_69){
this.contents=_69;
},{render:function(_6a,_6b){
this._rendered=true;
return _6b.concat(this.contents);
},unrender:function(_6c,_6d){
if(!this._rendered){
return _6d;
}
this._rendered=false;
return _6d.remove(this.contents);
},clone:function(_6e){
return new this.constructor(this.contents);
}});
dd._DomNodeList=dojo.extend(function(_6f){
this.contents=_6f||[];
},{push:function(_70){
this.contents.push(_70);
},unshift:function(_71){
this.contents.unshift(_71);
},render:function(_72,_73,_74){
_73=_73||dd.DomTemplate.prototype.getBuffer();
if(_74){
var _75=_73.getParent();
}
for(var i=0;i<this.contents.length;i++){
_73=this.contents[i].render(_72,_73);
if(!_73){
throw new Error("Template node render functions must return their buffer");
}
}
if(_75){
_73.setParent(_75);
}
return _73;
},dummyRender:function(_77,_78,_79){
var div=document.createElement("div");
var _7b=_78.getParent();
var old=_7b._clone;
_7b._clone=div;
var _7d=this.clone(_78,div);
if(old){
_7b._clone=old;
}else{
_7b._clone=null;
}
_78=dd.DomTemplate.prototype.getBuffer();
_7d.unshift(new dd.ChangeNode(div));
_7d.unshift(new dd._DomNode(div));
_7d.push(new dd.ChangeNode(div,true));
_7d.render(_77,_78);
if(_79){
return _78.getRootNode();
}
var _7e=div.innerHTML;
return (dojo.isIE)?_7e.replace(/\s*_(dirty|clone)="[^"]*"/g,""):_7e;
},unrender:function(_7f,_80,_81){
if(_81){
var _82=_80.getParent();
}
for(var i=0;i<this.contents.length;i++){
_80=this.contents[i].unrender(_7f,_80);
if(!_80){
throw new Error("Template node render functions must return their buffer");
}
}
if(_82){
_80.setParent(_82);
}
return _80;
},clone:function(_84){
var _85=_84.getParent();
var _86=this.contents;
var _87=new dd._DomNodeList();
var _88=[];
for(var i=0;i<_86.length;i++){
var _8a=_86[i].clone(_84);
if(_8a instanceof dd.ChangeNode||_8a instanceof dd._DomNode){
var _8b=_8a.contents._clone;
if(_8b){
_8a.contents=_8b;
}else{
if(_85!=_8a.contents&&_8a instanceof dd._DomNode){
var _8c=_8a.contents;
_8a.contents=_8a.contents.cloneNode(false);
_84.onClone&&_84.onClone(_8c,_8a.contents);
_88.push(_8c);
_8c._clone=_8a.contents;
}
}
}
_87.push(_8a);
}
for(var i=0,_8a;_8a=_88[i];i++){
_8a._clone=null;
}
return _87;
},rtrim:function(){
while(1){
var i=this.contents.length-1;
if(this.contents[i] instanceof dd._DomTextNode&&this.contents[i].isEmpty()){
this.contents.pop();
}else{
break;
}
}
return this;
}});
dd._DomVarNode=dojo.extend(function(str){
this.contents=new dd._Filter(str);
},{render:function(_8f,_90){
var str=this.contents.resolve(_8f);
var _92="text";
if(str){
if(str.render&&str.getRootNode){
_92="injection";
}else{
if(str.safe){
if(str.nodeType){
_92="node";
}else{
if(str.toString){
str=str.toString();
_92="html";
}
}
}
}
}
if(this._type&&_92!=this._type){
this.unrender(_8f,_90);
}
this._type=_92;
switch(_92){
case "text":
this._rendered=true;
this._txt=this._txt||document.createTextNode(str);
if(this._txt.data!=str){
var old=this._txt.data;
this._txt.data=str;
_90.onChangeData&&_90.onChangeData(this._txt,old,this._txt.data);
}
return _90.concat(this._txt);
case "injection":
var _94=str.getRootNode();
if(this._rendered&&_94!=this._root){
_90=this.unrender(_8f,_90);
}
this._root=_94;
var _95=this._injected=new dd._DomNodeList();
_95.push(new dd.ChangeNode(_90.getParent()));
_95.push(new dd._DomNode(_94));
_95.push(str);
_95.push(new dd.ChangeNode(_90.getParent()));
this._rendered=true;
return _95.render(_8f,_90);
case "node":
this._rendered=true;
if(this._node&&this._node!=str&&this._node.parentNode&&this._node.parentNode===_90.getParent()){
this._node.parentNode.removeChild(this._node);
}
this._node=str;
return _90.concat(str);
case "html":
if(this._rendered&&this._src!=str){
_90=this.unrender(_8f,_90);
}
this._src=str;
if(!this._rendered){
this._rendered=true;
this._html=this._html||[];
var div=(this._div=this._div||document.createElement("div"));
div.innerHTML=str;
var _97=div.childNodes;
while(_97.length){
var _98=div.removeChild(_97[0]);
this._html.push(_98);
_90=_90.concat(_98);
}
}
return _90;
default:
return _90;
}
},unrender:function(_99,_9a){
if(!this._rendered){
return _9a;
}
this._rendered=false;
switch(this._type){
case "text":
return _9a.remove(this._txt);
case "injection":
return this._injection.unrender(_99,_9a);
case "node":
if(this._node.parentNode===_9a.getParent()){
return _9a.remove(this._node);
}
return _9a;
case "html":
for(var i=0,l=this._html.length;i<l;i++){
_9a=_9a.remove(this._html[i]);
}
return _9a;
default:
return _9a;
}
},clone:function(){
return new this.constructor(this.contents.getExpression());
}});
dd.ChangeNode=dojo.extend(function(_9d,up,_9f){
this.contents=_9d;
this.up=up;
this.root=_9f;
},{render:function(_a0,_a1){
return _a1.setParent(this.contents,this.up,this.root);
},unrender:function(_a2,_a3){
if(!_a3.getParent()){
return _a3;
}
return _a3.setParent(this.contents);
},clone:function(){
return new this.constructor(this.contents,this.up,this.root);
}});
dd.AttributeNode=dojo.extend(function(key,_a5){
this.key=key;
this.value=_a5;
this.contents=_a5;
if(this._pool[_a5]){
this.nodelist=this._pool[_a5];
}else{
if(!(this.nodelist=dd.quickFilter(_a5))){
this.nodelist=(new dd.Template(_a5,true)).nodelist;
}
this._pool[_a5]=this.nodelist;
}
this.contents="";
},{_pool:{},render:function(_a6,_a7){
var key=this.key;
var _a9=this.nodelist.dummyRender(_a6);
if(dd.BOOLS[key]){
_a9=!(_a9=="false"||_a9=="undefined"||!_a9);
}
if(_a9!==this.contents){
this.contents=_a9;
return _a7.setAttribute(key,_a9);
}
return _a7;
},unrender:function(_aa,_ab){
this.contents="";
return _ab.remove(this.key);
},clone:function(_ac){
return new this.constructor(this.key,this.value);
}});
dd._DomTextNode=dojo.extend(function(str){
this.contents=document.createTextNode(str);
this.upcoming=str;
},{set:function(_ae){
this.upcoming=_ae;
return this;
},render:function(_af,_b0){
if(this.contents.data!=this.upcoming){
var old=this.contents.data;
this.contents.data=this.upcoming;
_b0.onChangeData&&_b0.onChangeData(this.contents,old,this.upcoming);
}
return _b0.concat(this.contents);
},unrender:function(_b2,_b3){
return _b3.remove(this.contents);
},isEmpty:function(){
return !dojo.trim(this.contents.data);
},clone:function(){
return new this.constructor(this.contents.data);
}});
dd._DomParser=dojo.extend(function(_b4){
this.contents=_b4;
},{i:0,parse:function(_b5){
var _b6={};
var _b7=this.contents;
if(!_b5){
_b5=[];
}
for(var i=0;i<_b5.length;i++){
_b6[_b5[i]]=true;
}
var _b9=new dd._DomNodeList();
while(this.i<_b7.length){
var _ba=_b7[this.i++];
var _bb=_ba[0];
var _bc=_ba[1];
if(_bb==dd.TOKEN_CUSTOM){
_b9.push(_bc);
}else{
if(_bb==dd.TOKEN_CHANGE){
var _bd=new dd.ChangeNode(_bc,_ba[2],_ba[3]);
_bc[_bd.attr]=_bd;
_b9.push(_bd);
}else{
if(_bb==dd.TOKEN_ATTR){
var fn=_2.getTag("attr:"+_ba[2],true);
if(fn&&_ba[3]){
if(_ba[3].indexOf("{%")!=-1||_ba[3].indexOf("{{")!=-1){
_bc.setAttribute(_ba[2],"");
}
_b9.push(fn(null,new dd.Token(_bb,_ba[2]+" "+_ba[3])));
}else{
if(dojo.isString(_ba[3])){
if(_ba[2]=="style"||_ba[3].indexOf("{%")!=-1||_ba[3].indexOf("{{")!=-1){
_b9.push(new dd.AttributeNode(_ba[2],_ba[3]));
}else{
if(dojo.trim(_ba[3])){
try{
dojo.attr(_bc,_ba[2],_ba[3]);
}
catch(e){
}
}
}
}
}
}else{
if(_bb==dd.TOKEN_NODE){
var fn=_2.getTag("node:"+_bc.tagName.toLowerCase(),true);
if(fn){
_b9.push(fn(null,new dd.Token(_bb,_bc),_bc.tagName.toLowerCase()));
}
_b9.push(new dd._DomNode(_bc));
}else{
if(_bb==dd.TOKEN_VAR){
_b9.push(new dd._DomVarNode(_bc));
}else{
if(_bb==dd.TOKEN_TEXT){
_b9.push(new dd._DomTextNode(_bc.data||_bc));
}else{
if(_bb==dd.TOKEN_BLOCK){
if(_b6[_bc]){
--this.i;
return _b9;
}
var cmd=_bc.split(/\s+/g);
if(cmd.length){
cmd=cmd[0];
var fn=_2.getTag(cmd);
if(typeof fn!="function"){
throw new Error("Function not found for "+cmd);
}
var tpl=fn(this,new dd.Token(_bb,_bc));
if(tpl){
_b9.push(tpl);
}
}
}
}
}
}
}
}
}
}
if(_b5.length){
throw new Error("Could not find closing tag(s): "+_b5.toString());
}
return _b9;
},next_token:function(){
var _c1=this.contents[this.i++];
return new dd.Token(_c1[0],_c1[1]);
},delete_first_token:function(){
this.i++;
},skip_past:function(_c2){
return dd._Parser.prototype.skip_past.call(this,_c2);
},create_variable_node:function(_c3){
return new dd._DomVarNode(_c3);
},create_text_node:function(_c4){
return new dd._DomTextNode(_c4||"");
},getTemplate:function(loc){
return new dd.DomTemplate(_3.getTemplate(loc));
}});
})();
}
