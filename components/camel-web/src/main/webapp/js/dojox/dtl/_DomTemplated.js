/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl._DomTemplated"]){
dojo._hasResource["dojox.dtl._DomTemplated"]=true;
dojo.provide("dojox.dtl._DomTemplated");
dojo.require("dijit._Templated");
dojo.require("dojox.dtl.dom");
dojo.require("dojox.dtl.render.dom");
dojo.require("dojox.dtl.contrib.dijit");
dojox.dtl._DomTemplated={prototype:{_dijitTemplateCompat:false,buildRendering:function(){
this.domNode=this.srcNodeRef;
if(!this._render){
var _1=dojox.dtl.contrib.dijit;
var _2=_1.widgetsInTemplate;
_1.widgetsInTemplate=this.widgetsInTemplate;
this.template=this.template||this._getCachedTemplate(this.templatePath,this.templateString);
this._render=new dojox.dtl.render.dom.Render(this.domNode,this.template);
_1.widgetsInTemplate=_2;
}
this.render();
this.domNode=this.template.getRootNode();
if(this.srcNodeRef&&this.srcNodeRef.parentNode){
dojo.destroy(this.srcNodeRef);
delete this.srcNodeRef;
}
},setTemplate:function(_3,_4){
if(dojox.dtl.text._isTemplate(_3)){
this.template=this._getCachedTemplate(null,_3);
}else{
this.template=this._getCachedTemplate(_3);
}
this.render(_4);
},render:function(_5,_6){
if(_6){
this.template=_6;
}
this._render.render(this._getContext(_5),this.template);
},_getContext:function(_7){
if(!(_7 instanceof dojox.dtl.Context)){
_7=false;
}
_7=_7||new dojox.dtl.Context(this);
_7.setThis(this);
return _7;
},_getCachedTemplate:function(_8,_9){
if(!this._templates){
this._templates={};
}
var _a=_9||_8.toString();
var _b=this._templates;
if(_b[_a]){
return _b[_a];
}
return (_b[_a]=new dojox.dtl.DomTemplate(dijit._Templated.getCachedTemplate(_8,_9,true)));
}}};
}
