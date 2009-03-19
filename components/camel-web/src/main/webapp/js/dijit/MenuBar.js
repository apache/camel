/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.MenuBar"]){
dojo._hasResource["dijit.MenuBar"]=true;
dojo.provide("dijit.MenuBar");
dojo.require("dijit.Menu");
dojo.declare("dijit.MenuBar",dijit._MenuBase,{templateString:"<div class=\"dijitMenuBar dijitMenuPassive\" dojoAttachPoint=\"containerNode\"  waiRole=\"menubar\" tabIndex=\"${tabIndex}\" dojoAttachEvent=\"onkeypress: _onKeyPress\"></div>\n",_isMenuBar:true,constructor:function(){
this._orient=this.isLeftToRight()?{BL:"TL"}:{BR:"TR"};
},postCreate:function(){
var k=dojo.keys,l=this.isLeftToRight();
this.connectKeyNavHandlers(l?[k.LEFT_ARROW]:[k.RIGHT_ARROW],l?[k.RIGHT_ARROW]:[k.LEFT_ARROW]);
},focusChild:function(_3){
var _4=this.focusedChild,_5=_4&&_4.popup&&_4.popup.isShowingNow;
this.inherited(arguments);
if(_5&&!_3.disabled){
this._openPopup();
}
},_onKeyPress:function(_6){
if(_6.ctrlKey||_6.altKey){
return;
}
switch(_6.charOrCode){
case dojo.keys.DOWN_ARROW:
this._moveToPopup(_6);
dojo.stopEvent(_6);
}
}});
}
