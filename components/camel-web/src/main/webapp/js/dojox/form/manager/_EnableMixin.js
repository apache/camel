/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.manager._EnableMixin"]){
dojo._hasResource["dojox.form.manager._EnableMixin"]=true;
dojo.provide("dojox.form.manager._EnableMixin");
dojo.require("dojox.form.manager._Mixin");
(function(){
var fm=dojox.form.manager,aa=fm.actionAdapter,ia=fm.inspectorAdapter;
dojo.declare("dojox.form.manager._EnableMixin",null,{gatherEnableState:function(_4){
var _5=this.inspectFormWidgets(ia(function(_6,_7){
return !_7.attr("disabled");
}),_4);
if(this.inspectFormNodes){
dojo.mixin(_5,this.inspectFormNodes(ia(function(_8,_9){
return !dojo.attr(_9,"disabled");
}),_4));
}
return _5;
},enable:function(_a,_b){
if(arguments.length<2||_b===undefined){
_b=true;
}
this.inspectFormWidgets(aa(function(_c,_d,_e){
_d.attr("disabled",!_e);
}),_a,_b);
if(this.inspectFormNodes){
this.inspectFormNodes(aa(function(_f,_10,_11){
dojo.attr(_10,"disabled",!_11);
}),_a,_b);
}
return this;
},disable:function(_12){
var _13=this.gatherEnableState();
this.enable(_12,false);
return _13;
}});
})();
}
