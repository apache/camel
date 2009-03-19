/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.wai"]){
dojo._hasResource["dijit._base.wai"]=true;
dojo.provide("dijit._base.wai");
dijit.wai={onload:function(){
var _1=dojo.create("div",{id:"a11yTestNode",style:{cssText:"border: 1px solid;"+"border-color:red green;"+"position: absolute;"+"height: 5px;"+"top: -999px;"+"background-image: url(\""+(dojo.config.blankGif||dojo.moduleUrl("dojo","resources/blank.gif"))+"\");"}},dojo.body());
var cs=dojo.getComputedStyle(_1);
if(cs){
var _3=cs.backgroundImage;
var _4=(cs.borderTopColor==cs.borderRightColor)||(_3!=null&&(_3=="none"||_3=="url(invalid-url:)"));
dojo[_4?"addClass":"removeClass"](dojo.body(),"dijit_a11y");
if(dojo.isIE){
_1.outerHTML="";
}else{
dojo.body().removeChild(_1);
}
}
}};
if(dojo.isIE||dojo.isMoz){
dojo._loaders.unshift(dijit.wai.onload);
}
dojo.mixin(dijit,{_XhtmlRoles:/banner|contentinfo|definition|main|navigation|search|note|secondary|seealso/,hasWaiRole:function(_5,_6){
var _7=this.getWaiRole(_5);
return _6?(_7.indexOf(_6)>-1):(_7.length>0);
},getWaiRole:function(_8){
return dojo.trim((dojo.attr(_8,"role")||"").replace(this._XhtmlRoles,"").replace("wairole:",""));
},setWaiRole:function(_9,_a){
var _b=dojo.attr(_9,"role")||"";
if(dojo.isFF<3||!this._XhtmlRoles.test(_b)){
dojo.attr(_9,"role",dojo.isFF<3?"wairole:"+_a:_a);
}else{
if((" "+_b+" ").indexOf(" "+_a+" ")<0){
var _c=dojo.trim(_b.replace(this._XhtmlRoles,""));
var _d=dojo.trim(_b.replace(_c,""));
dojo.attr(_9,"role",_d+(_d?" ":"")+_a);
}
}
},removeWaiRole:function(_e,_f){
var _10=dojo.attr(_e,"role");
if(!_10){
return;
}
if(_f){
var _11=dojo.isFF<3?"wairole:"+_f:_f;
var t=dojo.trim((" "+_10+" ").replace(" "+_11+" "," "));
dojo.attr(_e,"role",t);
}else{
_e.removeAttribute("role");
}
},hasWaiState:function(_13,_14){
if(dojo.isFF<3){
return _13.hasAttributeNS("http://www.w3.org/2005/07/aaa",_14);
}
return _13.hasAttribute?_13.hasAttribute("aria-"+_14):!!_13.getAttribute("aria-"+_14);
},getWaiState:function(_15,_16){
if(dojo.isFF<3){
return _15.getAttributeNS("http://www.w3.org/2005/07/aaa",_16);
}
return _15.getAttribute("aria-"+_16)||"";
},setWaiState:function(_17,_18,_19){
if(dojo.isFF<3){
_17.setAttributeNS("http://www.w3.org/2005/07/aaa","aaa:"+_18,_19);
}else{
_17.setAttribute("aria-"+_18,_19);
}
},removeWaiState:function(_1a,_1b){
if(dojo.isFF<3){
_1a.removeAttributeNS("http://www.w3.org/2005/07/aaa",_1b);
}else{
_1a.removeAttribute("aria-"+_1b);
}
}});
}
