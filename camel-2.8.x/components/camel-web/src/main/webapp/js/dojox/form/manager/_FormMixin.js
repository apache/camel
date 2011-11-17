/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.manager._FormMixin"]){
dojo._hasResource["dojox.form.manager._FormMixin"]=true;
dojo.provide("dojox.form.manager._FormMixin");
dojo.require("dojox.form.manager._Mixin");
(function(){
var fm=dojox.form.manager,aa=fm.actionAdapter;
dojo.declare("dojox.form.manager._FormMixin",null,{name:"",action:"",method:"",encType:"","accept-charset":"",accept:"",target:"",startup:function(){
this.isForm=this.domNode.tagName.toLowerCase()=="form";
if(this.isForm){
this.connect(this.domNode,"onreset","_onReset");
this.connect(this.domNode,"onsubmit","_onSubmit");
}
this.inherited(arguments);
},_onReset:function(_3){
var _4={returnValue:true,preventDefault:function(){
this.returnValue=false;
},stopPropagation:function(){
},currentTarget:_3.currentTarget,target:_3.target};
if(!(this.onReset(_4)===false)&&_4.returnValue){
this.reset();
}
dojo.stopEvent(_3);
return false;
},onReset:function(){
return true;
},reset:function(){
this.inspectFormWidgets(aa(function(_,_6){
if(_6.reset){
_6.reset();
}
}));
if(this.isForm){
this.domNode.reset();
}
return this;
},_onSubmit:function(_7){
if(this.onSubmit(_7)===false){
dojo.stopEvent(_7);
}
},onSubmit:function(){
return this.isValid();
},submit:function(){
if(this.isForm){
if(!(this.onSubmit()===false)){
this.domNode.submit();
}
}
},isValid:function(){
for(var _8 in this.formWidgets){
var _9=false;
aa(function(_,_b){
if(!_b.attr("disabled")&&_b.isValid&&!_b.isValid()){
_9=true;
}
}).call(this,null,this.formWidgets[_8].widget);
if(_9){
return false;
}
}
return true;
}});
})();
}
