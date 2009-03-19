/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.NumberTextBox"]){
dojo._hasResource["dijit.form.NumberTextBox"]=true;
dojo.provide("dijit.form.NumberTextBox");
dojo.require("dijit.form.ValidationTextBox");
dojo.require("dojo.number");
dojo.declare("dijit.form.NumberTextBoxMixin",null,{regExpGen:dojo.number.regexp,editOptions:{pattern:"#.######"},_formatter:dojo.number.format,_onFocus:function(){
if(this.disabled){
return;
}
var _1=this.attr("value");
if(typeof _1=="number"&&!isNaN(_1)){
this.textbox.value=this.format(_1,this.constraints);
}
this.inherited(arguments);
},format:function(_2,_3){
if(typeof _2=="string"){
return _2;
}
if(isNaN(_2)){
return "";
}
if(this.editOptions&&this._focused){
_3=dojo.mixin(dojo.mixin({},this.editOptions),this.constraints);
}
return this._formatter(_2,_3);
},parse:dojo.number.parse,_getDisplayedValueAttr:function(){
var v=this.inherited(arguments);
return isNaN(v)?this.textbox.value:v;
},filter:function(_5){
return (_5===null||_5===""||_5===undefined)?NaN:this.inherited(arguments);
},serialize:function(_6,_7){
return (typeof _6!="number"||isNaN(_6))?"":this.inherited(arguments);
},_getValueAttr:function(){
var v=this.inherited(arguments);
return (isNaN(v)&&this.textbox.value!=="")?undefined:v;
},value:NaN});
dojo.declare("dijit.form.NumberTextBox",[dijit.form.RangeBoundTextBox,dijit.form.NumberTextBoxMixin],{});
}
