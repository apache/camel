/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.Rating"]){
dojo._hasResource["dojox.form.Rating"]=true;
dojo.provide("dojox.form.Rating");
dojo.require("dijit.form._FormWidget");
dojo.declare("dojox.form.Rating",dijit.form._FormWidget,{templateString:null,numStars:3,value:0,constructor:function(_1){
dojo.mixin(this,_1);
var _2="<div dojoAttachPoint=\"domNode\" class=\"dojoxRating dijitInline\">"+"<input type=\"hidden\" value=\"0\" dojoAttachPoint=\"focusNode\" /><ul>${stars}</ul>"+"</div>";
var _3="<li class=\"dojoxRatingStar dijitInline\" dojoAttachEvent=\"onclick:onStarClick,onmouseover:_onMouse,onmouseout:_onMouse\" value=\"${value}\"></li>";
var _4="";
for(var i=0;i<this.numStars;i++){
_4+=dojo.string.substitute(_3,{value:i+1});
}
this.templateString=dojo.string.substitute(_2,{stars:_4});
},postCreate:function(){
this.inherited(arguments);
this._renderStars(this.value);
},_onMouse:function(_6){
this.inherited(arguments);
if(this._hovering){
var _7=+dojo.attr(_6.target,"value");
this.onMouseOver(_6,_7);
this._renderStars(_7,true);
}else{
this._renderStars(this.value);
}
},_renderStars:function(_8,_9){
dojo.query(".dojoxRatingStar",this.domNode).forEach(function(_a,i){
if(i+1>_8){
dojo.removeClass(_a,"dojoxRatingStarHover");
dojo.removeClass(_a,"dojoxRatingStarChecked");
}else{
dojo.removeClass(_a,"dojoxRatingStar"+(_9?"Checked":"Hover"));
dojo.addClass(_a,"dojoxRatingStar"+(_9?"Hover":"Checked"));
}
});
},onStarClick:function(_c){
var _d=+dojo.attr(_c.target,"value");
this.setAttribute("value",_d==this.value?0:_d);
this._renderStars(this.value);
this.onChange(this.value);
},onMouseOver:function(){
},setAttribute:function(_e,_f){
this.inherited("setAttribute",arguments);
if(_e=="value"){
this._renderStars(this.value);
this.onChange(this.value);
}
}});
}
