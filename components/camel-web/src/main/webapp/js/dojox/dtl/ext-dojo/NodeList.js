/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.ext-dojo.NodeList"]){
dojo._hasResource["dojox.dtl.ext-dojo.NodeList"]=true;
dojo.provide("dojox.dtl.ext-dojo.NodeList");
dojo.require("dojox.dtl._base");
dojo.extend(dojo.NodeList,{dtl:function(_1,_2){
var d=dojox.dtl;
var _4=this;
var _5=function(_6,_7){
var _8=_6.render(new d._Context(_7));
_4.forEach(function(_9){
_9.innerHTML=_8;
});
};
d.text._resolveTemplateArg(_1).addCallback(function(_a){
_1=new d.Template(_a);
d.text._resolveContextArg(_2).addCallback(function(_b){
_5(_1,_b);
});
});
return this;
}});
}
