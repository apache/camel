/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image.Badge"]){
dojo._hasResource["dojox.image.Badge"]=true;
dojo.provide("dojox.image.Badge");
dojo.experimental("dojox.image.Badge");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojo.fx.easing");
dojo.declare("dojox.image.Badge",[dijit._Widget,dijit._Templated],{baseClass:"dojoxBadge",templateString:"<div class=\"dojoxBadge\" dojoAttachPoint=\"containerNode\"></div>",children:"div.dojoxBadgeImage",rows:4,cols:5,cellSize:50,delay:2000,threads:1,easing:"dojo.fx.easing.backOut",startup:function(){
if(this._started){
return;
}
if(dojo.isString(this.easing)){
this.easing=dojo.getObject(this.easing);
}
this.inherited(arguments);
this._init();
},_init:function(){
var _1=0,_w=this.cellSize;
dojo.style(this.domNode,{width:_w*this.cols+"px",height:_w*this.rows+"px"});
this._nl=dojo.query(this.children,this.containerNode).forEach(function(n,_4){
var _5=_4%this.cols,t=_1*_w,l=_5*_w;
dojo.style(n,{top:t+"px",left:l+"px",width:_w-2+"px",height:_w-2+"px"});
if(_5==this.cols-1){
_1++;
}
dojo.addClass(n,this.baseClass+"Image");
},this);
var l=this._nl.length;
while(this.threads--){
var s=Math.floor(Math.random()*l);
setTimeout(dojo.hitch(this,"_enbiggen",{target:this._nl[s]}),this.delay*this.threads);
}
},_getCell:function(n){
var _b=this._nl.indexOf(n);
if(_b>=0){
var _c=_b%this.cols;
var _d=Math.floor(_b/this.cols);
return {x:_c,y:_d,n:this._nl[_b],io:_b};
}else{
return undefined;
}
},_getImage:function(){
return "url('')";
},_enbiggen:function(e){
var _f=this._getCell(e.target||e);
if(_f){
var _cc=(this.cellSize*2)-2;
var _11={height:_cc,width:_cc};
var _12=function(){
return Math.round(Math.random());
};
if(_f.x==this.cols-1||(_f.x>0&&_12())){
_11.left=this.cellSize*(_f.x-1);
}
if(_f.y==this.rows-1||(_f.y>0&&_12())){
_11.top=this.cellSize*(_f.y-1);
}
var bc=this.baseClass;
dojo.addClass(_f.n,bc+"Top");
dojo.addClass(_f.n,bc+"Seen");
dojo.animateProperty({node:_f.n,properties:_11,onEnd:dojo.hitch(this,"_loadUnder",_f,_11),easing:this.easing}).play();
}
},_loadUnder:function(_14,_15){
var idx=_14.io;
var _17=[];
var _18=(_15.left>=0);
var _19=(_15.top>=0);
var c=this.cols,e=idx+(_18?-1:1),f=idx+(_19?-c:c),g=(_19?(_18?e-c:f+1):(_18?f-1:e+c)),bc=this.baseClass;
dojo.forEach([e,f,g],function(x){
var n=this._nl[x];
if(n){
if(dojo.hasClass(n,bc+"Seen")){
dojo.removeClass(n,bc+"Seen");
}
}
},this);
setTimeout(dojo.hitch(this,"_disenbiggen",_14,_15),this.delay*1.25);
},_disenbiggen:function(_21,_22){
if(_22.top>=0){
_22.top+=this.cellSize;
}
if(_22.left>=0){
_22.left+=this.cellSize;
}
var _cc=this.cellSize-2;
dojo.animateProperty({node:_21.n,properties:dojo.mixin(_22,{width:_cc,height:_cc}),onEnd:dojo.hitch(this,"_cycle",_21,_22)}).play(5);
},_cycle:function(_24,_25){
var bc=this.baseClass;
dojo.removeClass(_24.n,bc+"Top");
var ns=this._nl.filter(function(n){
return !dojo.hasClass(n,bc+"Seen");
});
var c=ns[Math.floor(Math.random()*ns.length)];
setTimeout(dojo.hitch(this,"_enbiggen",{target:c}),this.delay/2);
}});
}
