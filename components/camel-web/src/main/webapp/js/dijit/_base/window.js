/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.window"]){
dojo._hasResource["dijit._base.window"]=true;
dojo.provide("dijit._base.window");
dijit.getDocumentWindow=function(_1){
if(dojo.isIE&&window!==document.parentWindow&&!_1._parentWindow){
_1.parentWindow.execScript("document._parentWindow = window;","Javascript");
var _2=_1._parentWindow;
_1._parentWindow=null;
return _2;
}
return _1._parentWindow||_1.parentWindow||_1.defaultView;
};
}
