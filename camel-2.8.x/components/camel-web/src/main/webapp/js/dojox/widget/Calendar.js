/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.Calendar"]){
dojo._hasResource["dojox.widget.Calendar"]=true;
dojo.provide("dojox.widget.Calendar");
dojo.experimental("dojox.widget.Calendar");
dojo.require("dijit._Calendar");
dojo.require("dijit._Container");
dojo.declare("dojox.widget._CalendarBase",[dijit._Widget,dijit._Templated,dijit._Container],{templateString:"<div class=\"dojoxCalendar\">\n    <div tabindex=\"0\" class=\"dojoxCalendarContainer\" style=\"visibility: visible; width: 180px; heightL 138px;\" dojoAttachPoint=\"container\">\n\t\t<div style=\"display:none\">\n\t\t\t<div dojoAttachPoint=\"previousYearLabelNode\"></div>\n\t\t\t<div dojoAttachPoint=\"nextYearLabelNode\"></div>\n\t\t\t<div dojoAttachPoint=\"monthLabelSpacer\"></div>\n\t\t</div>\n        <div class=\"dojoxCalendarHeader\">\n            <div>\n                <div class=\"dojoxCalendarDecrease\" dojoAttachPoint=\"decrementMonth\"></div>\n            </div>\n            <div class=\"\">\n                <div class=\"dojoxCalendarIncrease\" dojoAttachPoint=\"incrementMonth\"></div>\n            </div>\n            <div class=\"dojoxCalendarTitle\" dojoAttachPoint=\"header\" dojoAttachEvent=\"onclick: onHeaderClick\">\n            </div>\n        </div>\n        <div class=\"dojoxCalendarBody\" dojoAttachPoint=\"containerNode\"></div>\n        <div class=\"\">\n            <div class=\"dojoxCalendarFooter\" dojoAttachPoint=\"footer\">                        \n            </div>\n        </div>\n    </div>\n</div>\n",_views:null,useFx:true,widgetsInTemplate:true,value:new Date(),constraints:null,footerFormat:"medium",constructor:function(){
this._views=[];
},postMixInProperties:function(){
var c=this.constraints;
if(c){
var _2=dojo.date.stamp.fromISOString;
if(typeof c.min=="string"){
c.min=_2(c.min);
}
if(typeof c.max=="string"){
c.max=_2(c.max);
}
}
},postCreate:function(){
this._height=dojo.style(this.containerNode,"height");
this.displayMonth=new Date(this.attr("value"));
var _3={parent:this,_getValueAttr:dojo.hitch(this,function(){
return new Date(this.displayMonth);
}),_getConstraintsAttr:dojo.hitch(this,function(){
return this.constraints;
}),getLang:dojo.hitch(this,function(){
return this.lang;
}),isDisabledDate:dojo.hitch(this,this.isDisabledDate),getClassForDate:dojo.hitch(this,this.getClassForDate),addFx:this.useFx?dojo.hitch(this,this.addFx):function(){
}};
dojo.forEach(this._views,function(_4){
var _5=new _4(_3,dojo.create("div"));
this.addChild(_5);
var _6=_5.getHeader();
if(_6){
this.header.appendChild(_6);
dojo.style(_6,"display","none");
}
dojo.style(_5.domNode,"visibility","hidden");
dojo.connect(_5,"onValueSelected",this,"_onDateSelected");
_5.attr("value",this.attr("value"));
},this);
if(this._views.length<2){
dojo.style(this.header,"cursor","auto");
}
this.inherited(arguments);
this._children=this.getChildren();
this._currentChild=0;
var _7=new Date();
this.footer.innerHTML="Today: "+dojo.date.locale.format(_7,{formatLength:this.footerFormat,selector:"date",locale:this.lang});
dojo.connect(this.footer,"onclick",this,"goToToday");
var _8=this._children[0];
dojo.style(_8.domNode,"top","0px");
dojo.style(_8.domNode,"visibility","visible");
var _9=_8.getHeader();
if(_9){
dojo.style(_8.getHeader(),"display","");
}
dojo[_8.useHeader?"removeClass":"addClass"](this.container,"no-header");
_8.onDisplay();
var _a=this;
var _b=function(_c,_d,_e){
dijit.typematic.addMouseListener(_a[_c],_a,function(_f){
if(_f>=0){
_a._adjustDisplay(_d,_e);
}
},0.8,500);
};
_b("incrementMonth","month",1);
_b("decrementMonth","month",-1);
this._updateTitleStyle();
},addFx:function(_10,_11){
},_setValueAttr:function(_12){
if(!_12["getFullYear"]){
_12=dojo.date.stamp.fromISOString(_12+"");
}
if(!this.value||dojo.date.compare(_12,this.value)){
_12=new Date(_12);
this.displayMonth=new Date(_12);
if(!this.isDisabledDate(_12,this.lang)){
this.value=_12;
this.onChange(_12);
}
this._children[this._currentChild].attr("value",this.value);
return true;
}
return false;
},isDisabledDate:function(_13,_14){
var c=this.constraints;
var _16=dojo.date.compare;
return c&&(c.min&&(_16(c.min,_13,"date")>0)||(c.max&&_16(c.max,_13,"date")<0));
},onValueSelected:function(_17){
},_onDateSelected:function(_18,_19,_1a){
this.displayMonth=_18;
this.attr("value",_18);
if(!this._transitionVert(-1)){
if(!_19&&_19!==0){
_19=this.attr("value");
}
this.onValueSelected(_19);
}
},onChange:function(_1b){
},onHeaderClick:function(e){
this._transitionVert(1);
},goToToday:function(){
this.attr("value",new Date());
this.onValueSelected(this.attr("value"));
},_transitionVert:function(_1d){
var _1e=this._children[this._currentChild];
var _1f=this._children[this._currentChild+_1d];
if(!_1f){
return false;
}
dojo.style(_1f.domNode,"visibility","visible");
var _20=dojo.style(this.containerNode,"height");
_1f.attr("value",this.displayMonth);
if(_1e.header){
dojo.style(_1e.header,"display","none");
}
if(_1f.header){
dojo.style(_1f.header,"display","");
}
dojo.style(_1f.domNode,"top",(_20*-1)+"px");
dojo.style(_1f.domNode,"visibility","visible");
this._currentChild+=_1d;
var _21=_20*_1d;
var _22=0;
dojo.style(_1f.domNode,"top",(_21*-1)+"px");
var _23=dojo.animateProperty({node:_1e.domNode,properties:{top:_21},onEnd:function(){
dojo.style(_1e.domNode,"visibility","hidden");
}});
var _24=dojo.animateProperty({node:_1f.domNode,properties:{top:_22},onEnd:function(){
_1f.onDisplay();
}});
dojo[_1f.useHeader?"removeClass":"addClass"](this.container,"no-header");
_23.play();
_24.play();
_1e.onBeforeUnDisplay();
_1f.onBeforeDisplay();
this._updateTitleStyle();
return true;
},_updateTitleStyle:function(){
dojo[this._currentChild<this._children.length-1?"addClass":"removeClass"](this.header,"navToPanel");
},_slideTable:function(_25,_26,_27){
var _28=_25.domNode;
var _29=_28.cloneNode(true);
var _2a=dojo.style(_28,"width");
_28.parentNode.appendChild(_29);
dojo.style(_28,"left",(_2a*_26)+"px");
_27();
var _2b=dojo.animateProperty({node:_29,properties:{left:_2a*_26*-1},duration:500,onEnd:function(){
_29.parentNode.removeChild(_29);
}});
var _2c=dojo.animateProperty({node:_28,properties:{left:0},duration:500});
_2b.play();
_2c.play();
},_addView:function(_2d){
this._views.push(_2d);
},getClassForDate:function(_2e,_2f){
},_adjustDisplay:function(_30,_31,_32){
var _33=this._children[this._currentChild];
var _34=this.displayMonth=_33.adjustDate(this.displayMonth,_31);
this._slideTable(_33,_31,function(){
_33.attr("value",_34);
});
}});
dojo.declare("dojox.widget._CalendarView",dijit._Widget,{headerClass:"",useHeader:true,cloneClass:function(_35,n,_37){
var _38=dojo.query(_35,this.domNode)[0];
if(!_37){
for(var i=0;i<n;i++){
_38.parentNode.appendChild(_38.cloneNode(true));
}
}else{
var _3a=dojo.query(_35,this.domNode)[0];
for(var i=0;i<n;i++){
_38.parentNode.insertBefore(_38.cloneNode(true),_3a);
}
}
},_setText:function(_3b,_3c){
while(_3b.firstChild){
_3b.removeChild(_3b.firstChild);
}
_3b.appendChild(dojo.doc.createTextNode(_3c));
},getHeader:function(){
return this.header||(this.header=this.header=dojo.create("span",{"class":this.headerClass}));
},onValueSelected:function(_3d){
},adjustDate:function(_3e,_3f){
return dojo.date.add(_3e,this.datePart,_3f);
},onDisplay:function(){
},onBeforeDisplay:function(){
},onBeforeUnDisplay:function(){
}});
dojo.declare("dojox.widget._CalendarDay",null,{parent:null,constructor:function(){
this._addView(dojox.widget._CalendarDayView);
}});
dojo.declare("dojox.widget._CalendarDayView",[dojox.widget._CalendarView,dijit._Templated],{templateString:"<div class=\"dijitCalendarDayLabels\" style=\"left: 0px;\" dojoAttachPoint=\"dayContainer\">\n\t<div dojoAttachPoint=\"header\">\n\t\t<div dojoAttachPoint=\"monthAndYearHeader\">\n\t\t\t<span dojoAttachPoint=\"monthLabelNode\" class=\"dojoxCalendarMonthLabelNode\"></span>\n\t\t\t<span dojoAttachPoint=\"headerComma\" class=\"dojoxCalendarComma\">,</span>\n\t\t\t<span dojoAttachPoint=\"yearLabelNode\" class=\"dojoxCalendarDayYearLabel\"></span>\n\t\t</div>\n\t</div>\n\t<table cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin: auto;\">\n\t\t<thead>\n\t\t\t<tr>\n\t\t\t\t<td class=\"dijitCalendarDayLabelTemplate\"><div class=\"dijitCalendarDayLabel\"></div></td>\n\t\t\t</tr>\n\t\t</thead>\n\t\t<tbody dojoAttachEvent=\"onclick: _onDayClick\">\n\t\t\t<tr class=\"dijitCalendarWeekTemplate\">\n\t\t\t\t<td class=\"dojoxCalendarNextMonth dijitCalendarDateTemplate\">\n\t\t\t\t\t<div class=\"dijitCalendarDateLabel\"></div>\n\t\t\t\t</td>\n\t\t\t</tr>\n\t\t</tbody>\n\t</table>\n</div>\n",datePart:"month",dayWidth:"narrow",postCreate:function(){
this.cloneClass(".dijitCalendarDayLabelTemplate",6);
this.cloneClass(".dijitCalendarDateTemplate",6);
this.cloneClass(".dijitCalendarWeekTemplate",5);
var _40=dojo.date.locale.getNames("days",this.dayWidth,"standAlone",this.getLang());
var _41=dojo.cldr.supplemental.getFirstDayOfWeek(this.getLang());
dojo.query(".dijitCalendarDayLabel",this.domNode).forEach(function(_42,i){
this._setText(_42,_40[(i+_41)%7]);
},this);
},onDisplay:function(){
if(!this._addedFx){
this._addedFx=true;
this.addFx(".dijitCalendarDateTemplate div",this.domNode);
}
},_onDayClick:function(e){
var _45=new Date(this.attr("value"));
var p=e.target.parentNode;
var c="dijitCalendar";
var d=dojo.hasClass(p,c+"PreviousMonth")?-1:(dojo.hasClass(p,c+"NextMonth")?1:0);
if(d){
_45=dojo.date.add(_45,"month",d);
}
_45.setDate(e.target._date);
if(this.isDisabledDate(_45)){
dojo.stopEvent(e);
return;
}
this.attr("value",_45);
this.parent._onDateSelected(_45);
},_setValueAttr:function(_49){
this._populateDays();
},_populateDays:function(){
var _4a=this.attr("value");
_4a.setDate(1);
var _4b=_4a.getDay();
var _4c=dojo.date.getDaysInMonth(_4a);
var _4d=dojo.date.getDaysInMonth(dojo.date.add(_4a,"month",-1));
var _4e=new Date();
var _4f=this.attr("value");
var _50=dojo.cldr.supplemental.getFirstDayOfWeek(this.getLang());
if(_50>_4b){
_50-=7;
}
dojo.query(".dijitCalendarDateTemplate",this.domNode).forEach(function(_51,i){
i+=_50;
var _53=new Date(_4a);
var _54,_55="dijitCalendar",adj=0;
if(i<_4b){
_54=_4d-_4b+i+1;
adj=-1;
_55+="Previous";
}else{
if(i>=(_4b+_4c)){
_54=i-_4b-_4c+1;
adj=1;
_55+="Next";
}else{
_54=i-_4b+1;
_55+="Current";
}
}
if(adj){
_53=dojo.date.add(_53,"month",adj);
}
_53.setDate(_54);
if(!dojo.date.compare(_53,_4e,"date")){
_55="dijitCalendarCurrentDate "+_55;
}
if(!dojo.date.compare(_53,_4f,"date")){
_55="dijitCalendarSelectedDate "+_55;
}
if(this.isDisabledDate(_53,this.getLang())){
_55=" dijitCalendarDisabledDate "+_55;
}
var _57=this.getClassForDate(_53,this.getLang());
if(_57){
_55+=_57+" "+_55;
}
_51.className=_55+"Month dijitCalendarDateTemplate";
_51.dijitDateValue=_53.valueOf();
var _58=dojo.query(".dijitCalendarDateLabel",_51)[0];
this._setText(_58,_53.getDate());
_58._date=_58.parentNode._date=_53.getDate();
},this);
var _59=dojo.date.locale.getNames("months","wide","standAlone",this.getLang());
this._setText(this.monthLabelNode,_59[_4a.getMonth()]);
this._setText(this.yearLabelNode,_4a.getFullYear());
}});
dojo.declare("dojox.widget._CalendarMonthYear",null,{constructor:function(){
this._addView(dojox.widget._CalendarMonthYearView);
}});
dojo.declare("dojox.widget._CalendarMonthYearView",[dojox.widget._CalendarView,dijit._Templated],{templateString:"<div class=\"dojoxCal-MY-labels\" style=\"left: 0px;\"\t\n\tdojoAttachPoint=\"myContainer\" dojoAttachEvent=\"onclick: onClick\">\n\t\t<table cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin: auto;\">\n\t\t\t\t<tbody>\n\t\t\t\t\t\t<tr class=\"dojoxCal-MY-G-Template\">\n\t\t\t\t\t\t\t\t<td class=\"dojoxCal-MY-M-Template\">\n\t\t\t\t\t\t\t\t\t\t<div class=\"dojoxCalendarMonthLabel\"></div>\n\t\t\t\t\t\t\t\t</td>\n\t\t\t\t\t\t\t\t<td class=\"dojoxCal-MY-M-Template\">\n\t\t\t\t\t\t\t\t\t\t<div class=\"dojoxCalendarMonthLabel\"></div>\n\t\t\t\t\t\t\t\t</td>\n\t\t\t\t\t\t\t\t<td class=\"dojoxCal-MY-Y-Template\">\n\t\t\t\t\t\t\t\t\t\t<div class=\"dojoxCalendarYearLabel\"></div>\n\t\t\t\t\t\t\t\t</td>\n\t\t\t\t\t\t\t\t<td class=\"dojoxCal-MY-Y-Template\">\n\t\t\t\t\t\t\t\t\t\t<div class=\"dojoxCalendarYearLabel\"></div>\n\t\t\t\t\t\t\t\t</td>\n\t\t\t\t\t\t </tr>\n\t\t\t\t\t\t <tr class=\"dojoxCal-MY-btns\">\n\t\t\t\t\t\t \t <td class=\"dojoxCal-MY-btns\" colspan=\"4\">\n\t\t\t\t\t\t \t\t <span class=\"dijitReset dijitInline dijitButtonNode ok-btn\" dojoAttachEvent=\"onclick: onOk\" dojoAttachPoint=\"okBtn\">\n\t\t\t\t\t\t \t \t \t <button\tclass=\"dijitReset dijitStretch dijitButtonContents\">OK</button>\n\t\t\t\t\t\t\t\t </span>\n\t\t\t\t\t\t\t\t <span class=\"dijitReset dijitInline dijitButtonNode cancel-btn\" dojoAttachEvent=\"onclick: onCancel\" dojoAttachPoint=\"cancelBtn\">\n\t\t\t\t\t\t \t \t\t <button\tclass=\"dijitReset dijitStretch dijitButtonContents\">Cancel</button>\n\t\t\t\t\t\t\t\t </span>\n\t\t\t\t\t\t \t </td>\n\t\t\t\t\t\t </tr>\n\t\t\t\t</tbody>\n\t\t</table>\n</div>\n",datePart:"year",displayedYears:10,useHeader:false,postCreate:function(){
this.cloneClass(".dojoxCal-MY-G-Template",5,".dojoxCal-MY-btns");
this.monthContainer=this.yearContainer=this.myContainer;
var _5a="dojoxCalendarYearLabel";
var _5b="dojoxCalendarDecrease";
var _5c="dojoxCalendarIncrease";
dojo.query("."+_5a,this.myContainer).forEach(function(_5d,idx){
var _5f=_5c;
switch(idx){
case 0:
_5f=_5b;
case 1:
dojo.removeClass(_5d,_5a);
dojo.addClass(_5d,_5f);
break;
}
});
this._decBtn=dojo.query("."+_5b,this.myContainer)[0];
this._incBtn=dojo.query("."+_5c,this.myContainer)[0];
dojo.query(".dojoxCal-MY-M-Template",this.domNode).filter(function(_60){
return _60.cellIndex==1;
}).addClass("dojoxCal-MY-M-last");
dojo.connect(this,"onBeforeDisplay",dojo.hitch(this,function(){
this._cachedDate=new Date(this.attr("value").getTime());
this._populateYears(this._cachedDate.getFullYear());
this._populateMonths();
this._updateSelectedMonth();
this._updateSelectedYear();
}));
dojo.connect(this,"_populateYears",dojo.hitch(this,function(){
this._updateSelectedYear();
}));
dojo.connect(this,"_populateMonths",dojo.hitch(this,function(){
this._updateSelectedMonth();
}));
this._cachedDate=this.attr("value");
this._populateYears();
this._populateMonths();
this.addFx(".dojoxCalendarMonthLabel,.dojoxCalendarYearLabel ",this.myContainer);
},_setValueAttr:function(_61){
this._populateYears(_61.getFullYear());
},getHeader:function(){
return null;
},_getMonthNames:function(_62){
this._monthNames=this._monthNames||dojo.date.locale.getNames("months",_62,"standAlone",this.getLang());
return this._monthNames;
},_populateMonths:function(){
var _63=this._getMonthNames("abbr");
dojo.query(".dojoxCalendarMonthLabel",this.monthContainer).forEach(dojo.hitch(this,function(_64,cnt){
this._setText(_64,_63[cnt]);
}));
var _66=this.attr("constraints");
if(_66){
var _67=new Date();
_67.setFullYear(this._year);
var min=-1,max=12;
if(_66.min){
var _6a=_66.min.getFullYear();
if(_6a>this._year){
min=12;
}else{
if(_6a==this._year){
min=_66.min.getMonth();
}
}
}
if(_66.max){
var _6b=_66.max.getFullYear();
if(_6b<this._year){
max=-1;
}else{
if(_6b==this._year){
max=_66.max.getMonth();
}
}
}
dojo.query(".dojoxCalendarMonthLabel",this.monthContainer).forEach(dojo.hitch(this,function(_6c,cnt){
dojo[(cnt<min||cnt>max)?"addClass":"removeClass"](_6c,"dijitCalendarDisabledDate");
}));
}
var h=this.getHeader();
if(h){
this._setText(this.getHeader(),this.attr("value").getFullYear());
}
},_populateYears:function(_6f){
var _70=this.attr("constraints");
var _71=_6f||this.attr("value").getFullYear();
var _72=_71-Math.floor(this.displayedYears/2);
var min=_70&&_70.min?_70.min.getFullYear():_72-10000;
_72=Math.max(min,_72);
this._displayedYear=_71;
var _74=dojo.query(".dojoxCalendarYearLabel",this.yearContainer);
var max=_70&&_70.max?_70.max.getFullYear()-_72:_74.length;
var _76="dijitCalendarDisabledDate";
_74.forEach(dojo.hitch(this,function(_77,cnt){
if(cnt<=max){
this._setText(_77,_72+cnt);
dojo.removeClass(_77,_76);
}else{
dojo.addClass(_77,_76);
}
}));
if(this._incBtn){
dojo[max<_74.length?"addClass":"removeClass"](this._incBtn,_76);
}
if(this._decBtn){
dojo[min>=_72?"addClass":"removeClass"](this._decBtn,_76);
}
var h=this.getHeader();
if(h){
this._setText(this.getHeader(),_72+" - "+(_72+11));
}
},_updateSelectedYear:function(){
this._year=String((this._cachedDate||this.attr("value")).getFullYear());
this._updateSelectedNode(".dojoxCalendarYearLabel",dojo.hitch(this,function(_7a,idx){
return this._year!==null&&_7a.innerHTML==this._year;
}));
},_updateSelectedMonth:function(){
var _7c=(this._cachedDate||this.attr("value")).getMonth();
this._month=_7c;
this._updateSelectedNode(".dojoxCalendarMonthLabel",function(_7d,idx){
return idx==_7c;
});
},_updateSelectedNode:function(_7f,_80){
var sel="dijitCalendarSelectedDate";
dojo.query(_7f,this.domNode).forEach(function(_82,idx,_84){
dojo[_80(_82,idx,_84)?"addClass":"removeClass"](_82.parentNode,sel);
});
var _85=dojo.query(".dojoxCal-MY-M-Template div",this.myContainer).filter(function(_86){
return dojo.hasClass(_86.parentNode,sel);
})[0];
if(!_85){
return;
}
var _87=dojo.hasClass(_85,"dijitCalendarDisabledDate");
dojo[_87?"addClass":"removeClass"](this.okBtn,"dijitDisabled");
},onClick:function(evt){
var _89;
var _8a=this;
var sel="dijitCalendarSelectedDate";
function hc(c){
return dojo.hasClass(evt.target,c);
};
if(hc("dijitCalendarDisabledDate")){
dojo.stopEvent(evt);
return;
}
if(hc("dojoxCalendarMonthLabel")){
_89="dojoxCal-MY-M-Template";
this._month=evt.target.parentNode.cellIndex+(evt.target.parentNode.parentNode.rowIndex*2);
this._cachedDate.setMonth(this._month);
this._updateSelectedMonth();
}else{
if(hc("dojoxCalendarYearLabel")){
_89="dojoxCal-MY-Y-Template";
this._year=Number(evt.target.innerHTML);
this._cachedDate.setYear(this._year);
this._populateMonths();
this._updateSelectedYear();
}else{
if(hc("dojoxCalendarDecrease")){
this._populateYears(this._displayedYear-10);
return;
}else{
if(hc("dojoxCalendarIncrease")){
this._populateYears(this._displayedYear+10);
return;
}else{
return true;
}
}
}
}
dojo.stopEvent(evt);
return false;
},onOk:function(evt){
dojo.stopEvent(evt);
if(dojo.hasClass(this.okBtn,"dijitDisabled")){
return false;
}
this.onValueSelected(this._cachedDate);
return false;
},onCancel:function(evt){
dojo.stopEvent(evt);
this.onValueSelected(this.attr("value"));
return false;
}});
dojo.declare("dojox.widget.Calendar2Pane",[dojox.widget._CalendarBase,dojox.widget._CalendarDay,dojox.widget._CalendarMonthYear],{});
dojo.declare("dojox.widget.Calendar",[dojox.widget._CalendarBase,dojox.widget._CalendarDay,dojox.widget._CalendarMonthYear],{});
dojo.declare("dojox.widget.DailyCalendar",[dojox.widget._CalendarBase,dojox.widget._CalendarDay],{});
dojo.declare("dojox.widget.MonthAndYearlyCalendar",[dojox.widget._CalendarBase,dojox.widget._CalendarMonthYear],{});
}
