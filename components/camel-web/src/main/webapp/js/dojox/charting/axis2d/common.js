/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.axis2d.common"]){
dojo._hasResource["dojox.charting.axis2d.common"]=true;
dojo.provide("dojox.charting.axis2d.common");
dojo.require("dojox.gfx");
(function(){
var g=dojox.gfx;
function _2(s){
s.marginLeft="0px";
s.marginTop="0px";
s.marginRight="0px";
s.marginBottom="0px";
s.paddingLeft="0px";
s.paddingTop="0px";
s.paddingRight="0px";
s.paddingBottom="0px";
s.borderLeftWidth="0px";
s.borderTopWidth="0px";
s.borderRightWidth="0px";
s.borderBottomWidth="0px";
};
dojo.mixin(dojox.charting.axis2d.common,{createText:{gfx:function(_4,_5,x,y,_8,_9,_a,_b){
return _5.createText({x:x,y:y,text:_9,align:_8}).setFont(_a).setFill(_b);
},html:function(_c,_d,x,y,_10,_11,_12,_13){
var p=dojo.doc.createElement("div"),s=p.style;
_2(s);
s.font=_12;
p.innerHTML=String(_11).replace(/\s/g,"&nbsp;");
s.color=_13;
s.position="absolute";
s.left="-10000px";
dojo.body().appendChild(p);
var _16=g.normalizedLength(g.splitFontString(_12).size),box=dojo.marginBox(p);
dojo.body().removeChild(p);
s.position="relative";
switch(_10){
case "middle":
s.left=Math.floor(x-box.w/2)+"px";
break;
case "end":
s.left=Math.floor(x-box.w)+"px";
break;
default:
s.left=Math.floor(x)+"px";
break;
}
s.top=Math.floor(y-_16)+"px";
var _18=dojo.doc.createElement("div"),w=_18.style;
_2(w);
w.width="0px";
w.height="0px";
_18.appendChild(p);
_c.node.insertBefore(_18,_c.node.firstChild);
return _18;
}}});
})();
}
