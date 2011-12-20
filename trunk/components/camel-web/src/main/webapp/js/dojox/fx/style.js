/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx.style"]){
dojo._hasResource["dojox.fx.style"]=true;
dojo.provide("dojox.fx.style");
dojo.experimental("dojox.fx.style");
dojo.require("dojo.fx");
(function(){
var d=dojo;
var _2=function(_3){
return d.map(dojox.fx._allowedProperties,function(_4){
return _3[_4];
});
};
var _5=function(_6,_7){
var _8=_6.node=d.byId(_6.node);
var cs=d.getComputedStyle(_8);
var _a=_2(cs);
d[(_7?"addClass":"removeClass")](_8,_6.cssClass);
var _b=_2(cs);
d[(_7?"removeClass":"addClass")](_8,_6.cssClass);
var _c={},i=0;
d.forEach(dojox.fx._allowedProperties,function(_e){
if(_a[i]!=_b[i]){
_c[_e]=parseInt(_b[i]);
}
i++;
});
return _c;
};
d.mixin(dojox.fx,{addClass:function(_f){
var _10=_f.node=d.byId(_f.node);
var _11=(function(n){
return function(){
d.addClass(n,_f.cssClass);
n.style.cssText=_13;
};
})(_10);
var _14=_5(_f,true);
var _13=_10.style.cssText;
var _15=d.animateProperty(d.mixin({properties:_14},_f));
d.connect(_15,"onEnd",_15,_11);
return _15;
},removeClass:function(_16){
var _17=(_16.node=dojo.byId(_16.node));
var _18=(function(n){
return function(){
d.removeClass(n,_16.cssClass);
n.style.cssText=_1a;
};
})(_17);
var _1b=_5(_16,false);
var _1a=_17.style.cssText;
var _1c=d.animateProperty(d.mixin({properties:_1b},_16));
d.connect(_1c,"onEnd",_1c,_18);
return _1c;
},toggleClass:function(_1d,_1e,_1f){
if(typeof _1f=="undefined"){
_1f=!d.hasClass(_1d,_1e);
}
return dojox.fx[(_1f?"addClass":"removeClass")]({node:_1d,cssClass:_1e});
},_allowedProperties:["width","height","left","top","backgroundColor","color","borderBottomWidth","borderTopWidth","borderLeftWidth","borderRightWidth","paddingLeft","paddingRight","paddingTop","paddingBottom","marginLeft","marginTop","marginRight","marginBottom","lineHeight","letterSpacing","fontSize"]});
})();
}
