/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl._Templated"]){
dojo._hasResource["dojox.dtl._Templated"]=true;
dojo.provide("dojox.dtl._Templated");
dojo.require("dijit._Templated");
dojo.require("dojox.dtl._base");
dojo.declare("dojox.dtl._Templated",dijit._Templated,{_dijitTemplateCompat:false,buildRendering:function(){
var _1;
if(this.domNode&&!this._template){
return;
}
if(!this._template){
var t=this.getCachedTemplate(this.templatePath,this.templateString,this._skipNodeCache);
if(t instanceof dojox.dtl.Template){
this._template=t;
}else{
_1=t;
}
}
if(!_1){
var _3=dojo._toDom(this._template.render(new dojox.dtl._Context(this)));
if(_3.nodeType!==1&&_3.nodeType!==3){
for(var i=0,l=_3.childNodes.length;i<l;++i){
_1=_3.childNodes[i];
if(_1.nodeType==1){
break;
}
}
}else{
_1=_3;
}
}
this._attachTemplateNodes(_1);
if(this.widgetsInTemplate){
var _6=dojo.parser.parse(_1);
this._attachTemplateNodes(_6,function(n,p){
return n[p];
});
}
if(this.domNode){
dojo.place(_1,this.domNode,"before");
this.destroyDescendants();
dojo.destroy(this.domNode);
}
this.domNode=_1;
this._fillContent(this.srcNodeRef);
},_templateCache:{},getCachedTemplate:function(_9,_a,_b){
var _c=this._templateCache;
var _d=_a||_9;
if(_c[_d]){
return _c[_d];
}
_a=dojo.string.trim(_a||dijit._Templated._sanitizeTemplateString(dojo._getText(_9)));
if(this._dijitTemplateCompat&&(_b||_a.match(/\$\{([^\}]+)\}/g))){
_a=this._stringRepl(_a);
}
if(_b||!_a.match(/\{[{%]([^\}]+)[%}]\}/g)){
return _c[_d]=dojo._toDom(_a);
}else{
return _c[_d]=new dojox.dtl.Template(_a);
}
},render:function(){
this.buildRendering();
}});
}
