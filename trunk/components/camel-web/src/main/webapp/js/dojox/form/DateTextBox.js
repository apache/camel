/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.DateTextBox"]){
dojo._hasResource["dojox.form.DateTextBox"]=true;
dojo.provide("dojox.form.DateTextBox");
dojo.require("dojox.widget.Calendar");
dojo.require("dojox.widget.CalendarViews");
dojo.require("dijit.form._DateTimeTextBox");
dojo.declare("dojox.form.DateTextBox",dijit.form._DateTimeTextBox,{popupClass:"dojox.widget.Calendar",_selector:"date",_open:function(){
this.inherited(arguments);
dojo.style(this._picker.domNode.parentNode,"position","absolute");
}});
dojo.declare("dojox.form.DayTextBox",dojox.form.DateTextBox,{popupClass:"dojox.widget.DailyCalendar",format:function(_1){
return _1.getDate();
},validator:function(_2){
var _3=Number(_2);
var _4=/(^-?\d\d*$)/.test(String(_2));
return _2==""||_2==null||(_4&&_3>=1&&_3<=31);
},_open:function(){
this.inherited(arguments);
this._picker.onValueSelected=dojo.hitch(this,function(_5){
this.focus();
setTimeout(dojo.hitch(this,"_close"),1);
dijit.form.TextBox.prototype._setValueAttr.call(this,_5,true,String(_5.getDate()));
});
}});
dojo.declare("dojox.form.MonthTextBox",dojox.form.DateTextBox,{popupClass:"dojox.widget.MonthlyCalendar",format:function(_6){
return _6+1;
},validator:function(_7){
var _8=Number(_7);
var _9=/(^-?\d\d*$)/.test(String(_7));
return _7==""||_7==null||(_9&&_8>=1&&_8<=12);
},_open:function(){
this.inherited(arguments);
this._picker.onValueSelected=dojo.hitch(this,function(_a){
this.focus();
setTimeout(dojo.hitch(this,"_close"),1);
dijit.form.TextBox.prototype._setValueAttr.call(this,_a+1,true,_a+1);
});
}});
dojo.declare("dojox.form.YearTextBox",dojox.form.DateTextBox,{popupClass:"dojox.widget.YearlyCalendar",format:function(_b){
return _b;
},validator:function(_c){
return _c==""||_c==null||/(^-?\d\d*$)/.test(String(_c));
},_open:function(){
this.inherited(arguments);
this._picker.onValueSelected=dojo.hitch(this,function(_d){
this.focus();
setTimeout(dojo.hitch(this,"_close"),1);
dijit.form.TextBox.prototype._setValueAttr.call(this,_d,true,_d);
});
}});
}
