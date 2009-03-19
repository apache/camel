/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.ProgressBar"]){
dojo._hasResource["dijit.ProgressBar"]=true;
dojo.provide("dijit.ProgressBar");
dojo.require("dojo.fx");
dojo.require("dojo.number");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dijit.ProgressBar",[dijit._Widget,dijit._Templated],{progress:"0",maximum:100,places:0,indeterminate:false,templateString:"<div class=\"dijitProgressBar dijitProgressBarEmpty\"\n\t><div waiRole=\"progressbar\" tabindex=\"0\" dojoAttachPoint=\"internalProgress\" class=\"dijitProgressBarFull\"\n\t\t><div class=\"dijitProgressBarTile\"></div\n\t\t><span style=\"visibility:hidden\">&nbsp;</span\n\t></div\n\t><div dojoAttachPoint=\"label\" class=\"dijitProgressBarLabel\" id=\"${id}_label\">&nbsp;</div\n\t><img dojoAttachPoint=\"indeterminateHighContrastImage\" class=\"dijitProgressBarIndeterminateHighContrastImage\"\n\t></img\n></div>\n",_indeterminateHighContrastImagePath:dojo.moduleUrl("dijit","themes/a11y/indeterminate_progress.gif"),postCreate:function(){
this.inherited(arguments);
this.indeterminateHighContrastImage.setAttribute("src",this._indeterminateHighContrastImagePath);
this.update();
},update:function(_1){
dojo.mixin(this,_1||{});
var _2=this.internalProgress;
var _3=1,_4;
if(this.indeterminate){
_4="addClass";
dijit.removeWaiState(_2,"valuenow");
dijit.removeWaiState(_2,"valuemin");
dijit.removeWaiState(_2,"valuemax");
}else{
_4="removeClass";
if(String(this.progress).indexOf("%")!=-1){
_3=Math.min(parseFloat(this.progress)/100,1);
this.progress=_3*this.maximum;
}else{
this.progress=Math.min(this.progress,this.maximum);
_3=this.progress/this.maximum;
}
var _5=this.report(_3);
this.label.firstChild.nodeValue=_5;
dijit.setWaiState(_2,"describedby",this.label.id);
dijit.setWaiState(_2,"valuenow",this.progress);
dijit.setWaiState(_2,"valuemin",0);
dijit.setWaiState(_2,"valuemax",this.maximum);
}
dojo[_4](this.domNode,"dijitProgressBarIndeterminate");
_2.style.width=(_3*100)+"%";
this.onChange();
},report:function(_6){
return dojo.number.format(_6,{type:"percent",places:this.places,locale:this.lang});
},onChange:function(){
}});
}
