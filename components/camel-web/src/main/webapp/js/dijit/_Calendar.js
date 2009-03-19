/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._Calendar"]){
dojo._hasResource["dijit._Calendar"]=true;
dojo.provide("dijit._Calendar");
dojo.require("dojo.cldr.supplemental");
dojo.require("dojo.date");
dojo.require("dojo.date.locale");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dijit._Calendar",[dijit._Widget,dijit._Templated],{templateString:"<table cellspacing=\"0\" cellpadding=\"0\" class=\"dijitCalendarContainer\">\n\t<thead>\n\t\t<tr class=\"dijitReset dijitCalendarMonthContainer\" valign=\"top\">\n\t\t\t<th class='dijitReset' dojoAttachPoint=\"decrementMonth\">\n\t\t\t\t<img src=\"${_blankGif}\" alt=\"\" class=\"dijitCalendarIncrementControl dijitCalendarDecrease\" waiRole=\"presentation\">\n\t\t\t\t<span dojoAttachPoint=\"decreaseArrowNode\" class=\"dijitA11ySideArrow\">-</span>\n\t\t\t</th>\n\t\t\t<th class='dijitReset' colspan=\"5\">\n\t\t\t\t<div dojoAttachPoint=\"monthLabelSpacer\" class=\"dijitCalendarMonthLabelSpacer\"></div>\n\t\t\t\t<div dojoAttachPoint=\"monthLabelNode\" class=\"dijitCalendarMonthLabel\"></div>\n\t\t\t</th>\n\t\t\t<th class='dijitReset' dojoAttachPoint=\"incrementMonth\">\n\t\t\t\t<img src=\"${_blankGif}\" alt=\"\" class=\"dijitCalendarIncrementControl dijitCalendarIncrease\" waiRole=\"presentation\">\n\t\t\t\t<span dojoAttachPoint=\"increaseArrowNode\" class=\"dijitA11ySideArrow\">+</span>\n\t\t\t</th>\n\t\t</tr>\n\t\t<tr>\n\t\t\t<th class=\"dijitReset dijitCalendarDayLabelTemplate\"><span class=\"dijitCalendarDayLabel\"></span></th>\n\t\t</tr>\n\t</thead>\n\t<tbody dojoAttachEvent=\"onclick: _onDayClick, onmouseover: _onDayMouseOver, onmouseout: _onDayMouseOut\" class=\"dijitReset dijitCalendarBodyContainer\">\n\t\t<tr class=\"dijitReset dijitCalendarWeekTemplate\">\n\t\t\t<td class=\"dijitReset dijitCalendarDateTemplate\"><span class=\"dijitCalendarDateLabel\"></span></td>\n\t\t</tr>\n\t</tbody>\n\t<tfoot class=\"dijitReset dijitCalendarYearContainer\">\n\t\t<tr>\n\t\t\t<td class='dijitReset' valign=\"top\" colspan=\"7\">\n\t\t\t\t<h3 class=\"dijitCalendarYearLabel\">\n\t\t\t\t\t<span dojoAttachPoint=\"previousYearLabelNode\" class=\"dijitInline dijitCalendarPreviousYear\"></span>\n\t\t\t\t\t<span dojoAttachPoint=\"currentYearLabelNode\" class=\"dijitInline dijitCalendarSelectedYear\"></span>\n\t\t\t\t\t<span dojoAttachPoint=\"nextYearLabelNode\" class=\"dijitInline dijitCalendarNextYear\"></span>\n\t\t\t\t</h3>\n\t\t\t</td>\n\t\t</tr>\n\t</tfoot>\n</table>\t\n",value:new Date(),dayWidth:"narrow",setValue:function(_1){
dojo.deprecated("dijit.Calendar:setValue() is deprecated.  Use attr('value', ...) instead.","","2.0");
this.attr("value",_1);
},_setValueAttr:function(_2){
if(!this.value||dojo.date.compare(_2,this.value)){
_2=new Date(_2);
_2.setHours(1);
this.displayMonth=new Date(_2);
if(!this.isDisabledDate(_2,this.lang)){
this.onChange(this.value=_2);
}
this._populateGrid();
}
},_setText:function(_3,_4){
while(_3.firstChild){
_3.removeChild(_3.firstChild);
}
_3.appendChild(dojo.doc.createTextNode(_4));
},_populateGrid:function(){
var _5=this.displayMonth;
_5.setDate(1);
var _6=_5.getDay();
var _7=dojo.date.getDaysInMonth(_5);
var _8=dojo.date.getDaysInMonth(dojo.date.add(_5,"month",-1));
var _9=new Date();
var _a=this.value;
var _b=dojo.cldr.supplemental.getFirstDayOfWeek(this.lang);
if(_b>_6){
_b-=7;
}
dojo.query(".dijitCalendarDateTemplate",this.domNode).forEach(function(_c,i){
i+=_b;
var _e=new Date(_5);
var _f,_10="dijitCalendar",adj=0;
if(i<_6){
_f=_8-_6+i+1;
adj=-1;
_10+="Previous";
}else{
if(i>=(_6+_7)){
_f=i-_6-_7+1;
adj=1;
_10+="Next";
}else{
_f=i-_6+1;
_10+="Current";
}
}
if(adj){
_e=dojo.date.add(_e,"month",adj);
}
_e.setDate(_f);
if(!dojo.date.compare(_e,_9,"date")){
_10="dijitCalendarCurrentDate "+_10;
}
if(!dojo.date.compare(_e,_a,"date")){
_10="dijitCalendarSelectedDate "+_10;
}
if(this.isDisabledDate(_e,this.lang)){
_10="dijitCalendarDisabledDate "+_10;
}
var _12=this.getClassForDate(_e,this.lang);
if(_12){
_10=_12+" "+_10;
}
_c.className=_10+"Month dijitCalendarDateTemplate";
_c.dijitDateValue=_e.valueOf();
var _13=dojo.query(".dijitCalendarDateLabel",_c)[0];
this._setText(_13,_e.getDate());
},this);
var _14=dojo.date.locale.getNames("months","wide","standAlone",this.lang);
this._setText(this.monthLabelNode,_14[_5.getMonth()]);
var y=_5.getFullYear()-1;
var d=new Date();
dojo.forEach(["previous","current","next"],function(_17){
d.setFullYear(y++);
this._setText(this[_17+"YearLabelNode"],dojo.date.locale.format(d,{selector:"year",locale:this.lang}));
},this);
var _18=this;
var _19=function(_1a,_1b,adj){
_18._connects.push(dijit.typematic.addMouseListener(_18[_1a],_18,function(_1d){
if(_1d>=0){
_18._adjustDisplay(_1b,adj);
}
},0.8,500));
};
_19("incrementMonth","month",1);
_19("decrementMonth","month",-1);
_19("nextYearLabelNode","year",1);
_19("previousYearLabelNode","year",-1);
},goToToday:function(){
this.attr("value",new Date());
},postCreate:function(){
this.inherited(arguments);
dojo.setSelectable(this.domNode,false);
var _1e=dojo.hitch(this,function(_1f,n){
var _21=dojo.query(_1f,this.domNode)[0];
for(var i=0;i<n;i++){
_21.parentNode.appendChild(_21.cloneNode(true));
}
});
_1e(".dijitCalendarDayLabelTemplate",6);
_1e(".dijitCalendarDateTemplate",6);
_1e(".dijitCalendarWeekTemplate",5);
var _23=dojo.date.locale.getNames("days",this.dayWidth,"standAlone",this.lang);
var _24=dojo.cldr.supplemental.getFirstDayOfWeek(this.lang);
dojo.query(".dijitCalendarDayLabel",this.domNode).forEach(function(_25,i){
this._setText(_25,_23[(i+_24)%7]);
},this);
var _27=dojo.date.locale.getNames("months","wide","standAlone",this.lang);
dojo.forEach(_27,function(_28){
var _29=dojo.create("div",null,this.monthLabelSpacer);
this._setText(_29,_28);
},this);
this.value=null;
this.attr("value",new Date());
},_adjustDisplay:function(_2a,_2b){
this.displayMonth=dojo.date.add(this.displayMonth,_2a,_2b);
this._populateGrid();
},_onDayClick:function(evt){
dojo.stopEvent(evt);
for(var _2d=evt.target;_2d&&!_2d.dijitDateValue;_2d=_2d.parentNode){
}
if(_2d&&!dojo.hasClass(_2d,"dijitCalendarDisabledDate")){
this.attr("value",_2d.dijitDateValue);
this.onValueSelected(this.value);
}
},_onDayMouseOver:function(evt){
var _2f=evt.target;
if(_2f&&(_2f.dijitDateValue||_2f==this.previousYearLabelNode||_2f==this.nextYearLabelNode)){
dojo.addClass(_2f,"dijitCalendarHoveredDate");
this._currentNode=_2f;
}
},_onDayMouseOut:function(evt){
if(!this._currentNode){
return;
}
for(var _31=evt.relatedTarget;_31;){
if(_31==this._currentNode){
return;
}
try{
_31=_31.parentNode;
}
catch(x){
_31=null;
}
}
dojo.removeClass(this._currentNode,"dijitCalendarHoveredDate");
this._currentNode=null;
},onValueSelected:function(_32){
},onChange:function(_33){
},isDisabledDate:function(_34,_35){
},getClassForDate:function(_36,_37){
}});
}
