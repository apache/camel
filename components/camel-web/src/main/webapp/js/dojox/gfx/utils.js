/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.utils"]){
dojo._hasResource["dojox.gfx.utils"]=true;
dojo.provide("dojox.gfx.utils");
dojo.require("dojox.gfx");
(function(){
var d=dojo,g=dojox.gfx,gu=g.utils;
dojo.mixin(gu,{forEach:function(_4,f,o){
o=o||d.global;
f.call(o,_4);
if(_4 instanceof g.Surface||_4 instanceof g.Group){
d.forEach(_4.children,function(_7){
gu.inspect(_7,f,o);
});
}
},serialize:function(_8){
var t={},v,_b=_8 instanceof g.Surface;
if(_b||_8 instanceof g.Group){
t.children=d.map(_8.children,gu.serialize);
if(_b){
return t.children;
}
}else{
t.shape=_8.getShape();
}
if(_8.getTransform){
v=_8.getTransform();
if(v){
t.transform=v;
}
}
if(_8.getStroke){
v=_8.getStroke();
if(v){
t.stroke=v;
}
}
if(_8.getFill){
v=_8.getFill();
if(v){
t.fill=v;
}
}
if(_8.getFont){
v=_8.getFont();
if(v){
t.font=v;
}
}
return t;
},toJson:function(_c,_d){
return d.toJson(gu.serialize(_c),_d);
},deserialize:function(_e,_f){
if(_f instanceof Array){
return d.map(_f,d.hitch(null,gu.serialize,_e));
}
var _10=("shape" in _f)?_e.createShape(_f.shape):_e.createGroup();
if("transform" in _f){
_10.setTransform(_f.transform);
}
if("stroke" in _f){
_10.setStroke(_f.stroke);
}
if("fill" in _f){
_10.setFill(_f.fill);
}
if("font" in _f){
_10.setFont(_f.font);
}
if("children" in _f){
d.forEach(_f.children,d.hitch(null,gu.deserialize,_10));
}
return _10;
},fromJson:function(_11,_12){
return gu.deserialize(_11,d.fromJson(_12));
}});
})();
}
