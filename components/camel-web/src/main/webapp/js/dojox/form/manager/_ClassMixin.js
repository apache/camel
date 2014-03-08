/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.manager._ClassMixin"]){
dojo._hasResource["dojox.form.manager._ClassMixin"]=true;
dojo.provide("dojox.form.manager._ClassMixin");
dojo.require("dojox.form.manager._Mixin");
(function(){
var fm=dojox.form.manager,aa=fm.actionAdapter,ia=fm.inspectorAdapter;
dojo.declare("dojox.form.manager._ClassMixin",null,{gatherClassState:function(_4,_5){
var _6=this.inspect(ia(function(_7,_8){
return dojo.hasClass(_8,_4);
}),_5);
return _6;
},addClass:function(_9,_a){
this.inspect(aa(function(_b,_c){
dojo.addClass(_c,_9);
}),_a);
return this;
},removeClass:function(_d,_e){
this.inspect(aa(function(_f,_10){
dojo.removeClass(_10,_d);
}),_e);
return this;
}});
})();
}
