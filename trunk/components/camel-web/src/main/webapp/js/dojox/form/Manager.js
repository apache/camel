/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.Manager"]){
dojo._hasResource["dojox.form.Manager"]=true;
dojo.provide("dojox.form.Manager");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojox.form.manager._Mixin");
dojo.require("dojox.form.manager._NodeMixin");
dojo.require("dojox.form.manager._FormMixin");
dojo.require("dojox.form.manager._ValueMixin");
dojo.require("dojox.form.manager._EnableMixin");
dojo.require("dojox.form.manager._DisplayMixin");
dojo.require("dojox.form.manager._ClassMixin");
dojo.declare("dojox.form.Manager",[dijit._Widget,dijit._Templated,dojox.form.manager._Mixin,dojox.form.manager._NodeMixin,dojox.form.manager._FormMixin,dojox.form.manager._ValueMixin,dojox.form.manager._EnableMixin,dojox.form.manager._DisplayMixin,dojox.form.manager._ClassMixin],{widgetsInTemplate:true,buildRendering:function(){
var _1=this.domNode=this.srcNodeRef;
if(!this.containerNode){
this.containerNode=_1;
}
this._attachTemplateNodes(_1);
},startup:function(){
if(this._started){
return;
}
this._attachTemplateNodes(this.getDescendants(),function(n,p){
return n[p];
});
this.inherited(arguments);
}});
}
