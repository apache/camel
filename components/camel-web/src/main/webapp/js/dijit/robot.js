/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.robot"]){
dojo._hasResource["dijit.robot"]=true;
dojo.provide("dijit.robot");
dojo.require("dojo.robot");
dojo.require("dijit._base.scroll");
dojo.mixin(doh.robot,{_scrollIntoView:function(_1){
if(typeof _1=="function"){
_1=_1();
}
dijit.scrollIntoView(_1);
}});
}
