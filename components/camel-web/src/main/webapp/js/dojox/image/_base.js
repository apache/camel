/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.image._base"]){
dojo._hasResource["dojox.image._base"]=true;
dojo.provide("dojox.image._base");
(function(d){
var _2;
dojox.image.preload=function(_3){
if(!_2){
_2=d.create("div",{style:{position:"absolute",top:"-9999px",display:"none"}},d.body());
}
d.forEach(_3,function(_4){
d.create("img",{src:_4},_2);
});
};
if(d.config.preloadImages){
d.addOnLoad(function(){
dojox.image.preload(d.config.preloadImages);
});
}
})(dojo);
}
