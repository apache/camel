/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.Tooltip"]){
dojo._hasResource["dijit.Tooltip"]=true;
dojo.provide("dijit.Tooltip");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dijit._MasterTooltip",[dijit._Widget,dijit._Templated],{duration:dijit.defaultDuration,templateString:"<div class=\"dijitTooltip dijitTooltipLeft\" id=\"dojoTooltip\">\n\t<div class=\"dijitTooltipContainer dijitTooltipContents\" dojoAttachPoint=\"containerNode\" waiRole='alert'></div>\n\t<div class=\"dijitTooltipConnector\"></div>\n</div>\n",postCreate:function(){
dojo.body().appendChild(this.domNode);
this.bgIframe=new dijit.BackgroundIframe(this.domNode);
this.fadeIn=dojo.fadeIn({node:this.domNode,duration:this.duration,onEnd:dojo.hitch(this,"_onShow")});
this.fadeOut=dojo.fadeOut({node:this.domNode,duration:this.duration,onEnd:dojo.hitch(this,"_onHide")});
},show:function(_1,_2,_3){
if(this.aroundNode&&this.aroundNode===_2){
return;
}
if(this.fadeOut.status()=="playing"){
this._onDeck=arguments;
return;
}
this.containerNode.innerHTML=_1;
this.domNode.style.top=(this.domNode.offsetTop+1)+"px";
var _4={};
var _5=this.isLeftToRight();
dojo.forEach((_3&&_3.length)?_3:dijit.Tooltip.defaultPosition,function(_6){
switch(_6){
case "after":
_4[_5?"BR":"BL"]=_5?"BL":"BR";
break;
case "before":
_4[_5?"BL":"BR"]=_5?"BR":"BL";
break;
case "below":
_4[_5?"BL":"BR"]=_5?"TL":"TR";
_4[_5?"BR":"BL"]=_5?"TR":"TL";
break;
case "above":
default:
_4[_5?"TL":"TR"]=_5?"BL":"BR";
_4[_5?"TR":"TL"]=_5?"BR":"BL";
break;
}
});
var _7=dijit.placeOnScreenAroundElement(this.domNode,_2,_4,dojo.hitch(this,"orient"));
dojo.style(this.domNode,"opacity",0);
this.fadeIn.play();
this.isShowingNow=true;
this.aroundNode=_2;
},orient:function(_8,_9,_a){
_8.className="dijitTooltip "+{"BL-TL":"dijitTooltipBelow dijitTooltipABLeft","TL-BL":"dijitTooltipAbove dijitTooltipABLeft","BR-TR":"dijitTooltipBelow dijitTooltipABRight","TR-BR":"dijitTooltipAbove dijitTooltipABRight","BR-BL":"dijitTooltipRight","BL-BR":"dijitTooltipLeft"}[_9+"-"+_a];
},_onShow:function(){
if(dojo.isIE){
this.domNode.style.filter="";
}
},hide:function(_b){
if(this._onDeck&&this._onDeck[1]==_b){
this._onDeck=null;
}else{
if(this.aroundNode===_b){
this.fadeIn.stop();
this.isShowingNow=false;
this.aroundNode=null;
this.fadeOut.play();
}else{
}
}
},_onHide:function(){
this.domNode.style.cssText="";
if(this._onDeck){
this.show.apply(this,this._onDeck);
this._onDeck=null;
}
}});
dijit.showTooltip=function(_c,_d,_e){
if(!dijit._masterTT){
dijit._masterTT=new dijit._MasterTooltip();
}
return dijit._masterTT.show(_c,_d,_e);
};
dijit.hideTooltip=function(_f){
if(!dijit._masterTT){
dijit._masterTT=new dijit._MasterTooltip();
}
return dijit._masterTT.hide(_f);
};
dojo.declare("dijit.Tooltip",dijit._Widget,{label:"",showDelay:400,connectId:[],position:[],_setConnectIdAttr:function(ids){
this._connectNodes=[];
this.connectId=dojo.isArrayLike(ids)?ids:[ids];
dojo.forEach(this.connectId,function(id){
var _12=dojo.byId(id);
if(_12){
this._connectNodes.push(_12);
dojo.forEach(["onMouseEnter","onMouseLeave","onFocus","onBlur"],function(_13){
this.connect(_12,_13.toLowerCase(),"_"+_13);
},this);
if(dojo.isIE){
_12.style.zoom=1;
}
}
},this);
},postCreate:function(){
dojo.addClass(this.domNode,"dijitTooltipData");
},_onMouseEnter:function(e){
this._onHover(e);
},_onMouseLeave:function(e){
this._onUnHover(e);
},_onFocus:function(e){
this._focus=true;
this._onHover(e);
this.inherited(arguments);
},_onBlur:function(e){
this._focus=false;
this._onUnHover(e);
this.inherited(arguments);
},_onHover:function(e){
if(!this._showTimer){
var _19=e.target;
this._showTimer=setTimeout(dojo.hitch(this,function(){
this.open(_19);
}),this.showDelay);
}
},_onUnHover:function(e){
if(this._focus){
return;
}
if(this._showTimer){
clearTimeout(this._showTimer);
delete this._showTimer;
}
this.close();
},open:function(_1b){
_1b=_1b||this._connectNodes[0];
if(!_1b){
return;
}
if(this._showTimer){
clearTimeout(this._showTimer);
delete this._showTimer;
}
dijit.showTooltip(this.label||this.domNode.innerHTML,_1b,this.position);
this._connectNode=_1b;
},close:function(){
if(this._connectNode){
dijit.hideTooltip(this._connectNode);
delete this._connectNode;
}
if(this._showTimer){
clearTimeout(this._showTimer);
delete this._showTimer;
}
},uninitialize:function(){
this.close();
}});
dijit.Tooltip.defaultPosition=["after","before"];
}
