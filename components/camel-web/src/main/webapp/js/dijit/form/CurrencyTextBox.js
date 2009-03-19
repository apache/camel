/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.CurrencyTextBox"]){
dojo._hasResource["dijit.form.CurrencyTextBox"]=true;
dojo.provide("dijit.form.CurrencyTextBox");
dojo.require("dojo.currency");
dojo.require("dijit.form.NumberTextBox");
dojo.declare("dijit.form.CurrencyTextBox",dijit.form.NumberTextBox,{currency:"",regExpGen:dojo.currency.regexp,_formatter:dojo.currency.format,parse:dojo.currency.parse,postMixInProperties:function(){
if(this.constraints===dijit.form.ValidationTextBox.prototype.constraints){
this.constraints={};
}
this.constraints.currency=this.currency;
dijit.form.CurrencyTextBox.superclass.postMixInProperties.apply(this,arguments);
}});
}
