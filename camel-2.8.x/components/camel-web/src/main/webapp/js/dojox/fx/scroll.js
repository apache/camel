/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx.scroll"]){
dojo._hasResource["dojox.fx.scroll"]=true;
dojo.provide("dojox.fx.scroll");
dojo.experimental("dojox.fx.scroll");
dojo.require("dojox.fx._core");
dojox.fx.smoothScroll=function(_1){
if(!_1.target){
_1.target=dojo.coords(_1.node,true);
}
var _2=dojo[(dojo.isIE?"isObject":"isFunction")](_1["win"].scrollTo);
var _3=(_2)?(function(_4){
_1.win.scrollTo(_4[0],_4[1]);
}):(function(_5){
_1.win.scrollLeft=_5[0];
_1.win.scrollTop=_5[1];
});
var _6=new dojo._Animation(dojo.mixin({beforeBegin:function(){
if(this.curve){
delete this.curve;
}
var _7=_2?dojo._docScroll():{x:_1.win.scrollLeft,y:_1.win.scrollTop};
_6.curve=new dojox.fx._Line([_7.x,_7.y],[_1.target.x,_1.target.y]);
},onAnimate:_3},_1));
return _6;
};
}
