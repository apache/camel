/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.fx"]){
dojo._hasResource["dojox.gfx.fx"]=true;
dojo.provide("dojox.gfx.fx");
dojo.require("dojox.gfx.matrix");
(function(){
var d=dojo,g=dojox.gfx,m=g.matrix;
var _4=function(_5,_6){
this.start=_5,this.end=_6;
};
d.extend(_4,{getValue:function(r){
return (this.end-this.start)*r+this.start;
}});
var _8=function(_9,_a,_b){
this.start=_9,this.end=_a;
this.unit=_b;
};
d.extend(_8,{getValue:function(r){
return (this.end-this.start)*r+this.start+this.unit;
}});
var _d=function(_e,_f){
this.start=_e,this.end=_f;
this.temp=new dojo.Color();
};
d.extend(_d,{getValue:function(r){
return d.blendColors(this.start,this.end,r,this.temp);
}});
var _11=function(_12){
this.values=_12;
this.length=_12.length;
};
d.extend(_11,{getValue:function(r){
return this.values[Math.min(Math.floor(r*this.length),this.length-1)];
}});
var _14=function(_15,def){
this.values=_15;
this.def=def?def:{};
};
d.extend(_14,{getValue:function(r){
var ret=dojo.clone(this.def);
for(var i in this.values){
ret[i]=this.values[i].getValue(r);
}
return ret;
}});
var _1a=function(_1b,_1c){
this.stack=_1b;
this.original=_1c;
};
d.extend(_1a,{getValue:function(r){
var ret=[];
dojo.forEach(this.stack,function(t){
if(t instanceof m.Matrix2D){
ret.push(t);
return;
}
if(t.name=="original"&&this.original){
ret.push(this.original);
return;
}
if(!(t.name in m)){
return;
}
var f=m[t.name];
if(typeof f!="function"){
ret.push(f);
return;
}
var val=dojo.map(t.start,function(v,i){
return (t.end[i]-v)*r+v;
}),_24=f.apply(m,val);
if(_24 instanceof m.Matrix2D){
ret.push(_24);
}
},this);
return ret;
}});
var _25=new d.Color(0,0,0,0);
var _26=function(_27,obj,_29,def){
if(_27.values){
return new _11(_27.values);
}
var _2b,_2c,end;
if(_27.start){
_2c=g.normalizeColor(_27.start);
}else{
_2c=_2b=obj?(_29?obj[_29]:obj):def;
}
if(_27.end){
end=g.normalizeColor(_27.end);
}else{
if(!_2b){
_2b=obj?(_29?obj[_29]:obj):def;
}
end=_2b;
}
return new _d(_2c,end);
};
var _2e=function(_2f,obj,_31,def){
if(_2f.values){
return new _11(_2f.values);
}
var _33,_34,end;
if(_2f.start){
_34=_2f.start;
}else{
_34=_33=obj?obj[_31]:def;
}
if(_2f.end){
end=_2f.end;
}else{
if(typeof _33!="number"){
_33=obj?obj[_31]:def;
}
end=_33;
}
return new _4(_34,end);
};
g.fx.animateStroke=function(_36){
if(!_36.easing){
_36.easing=d._defaultEasing;
}
var _37=new d._Animation(_36),_38=_36.shape,_39;
d.connect(_37,"beforeBegin",_37,function(){
_39=_38.getStroke();
var _3a=_36.color,_3b={},_3c,_3d,end;
if(_3a){
_3b.color=_26(_3a,_39,"color",_25);
}
_3a=_36.style;
if(_3a&&_3a.values){
_3b.style=new _11(_3a.values);
}
_3a=_36.width;
if(_3a){
_3b.width=_2e(_3a,_39,"width",1);
}
_3a=_36.cap;
if(_3a&&_3a.values){
_3b.cap=new _11(_3a.values);
}
_3a=_36.join;
if(_3a){
if(_3a.values){
_3b.join=new _11(_3a.values);
}else{
_3d=_3a.start?_3a.start:(_39&&_39.join||0);
end=_3a.end?_3a.end:(_39&&_39.join||0);
if(typeof _3d=="number"&&typeof end=="number"){
_3b.join=new _4(_3d,end);
}
}
}
this.curve=new _14(_3b,_39);
});
d.connect(_37,"onAnimate",_38,"setStroke");
return _37;
};
g.fx.animateFill=function(_3f){
if(!_3f.easing){
_3f.easing=d._defaultEasing;
}
var _40=new d._Animation(_3f),_41=_3f.shape,_42;
d.connect(_40,"beforeBegin",_40,function(){
_42=_41.getFill();
var _43=_3f.color,_44={};
if(_43){
this.curve=_26(_43,_42,"",_25);
}
});
d.connect(_40,"onAnimate",_41,"setFill");
return _40;
};
g.fx.animateFont=function(_45){
if(!_45.easing){
_45.easing=d._defaultEasing;
}
var _46=new d._Animation(_45),_47=_45.shape,_48;
d.connect(_46,"beforeBegin",_46,function(){
_48=_47.getFont();
var _49=_45.style,_4a={},_4b,_4c,end;
if(_49&&_49.values){
_4a.style=new _11(_49.values);
}
_49=_45.variant;
if(_49&&_49.values){
_4a.variant=new _11(_49.values);
}
_49=_45.weight;
if(_49&&_49.values){
_4a.weight=new _11(_49.values);
}
_49=_45.family;
if(_49&&_49.values){
_4a.family=new _11(_49.values);
}
_49=_45.size;
if(_49&&_49.unit){
_4c=parseFloat(_49.start?_49.start:(_47.font&&_47.font.size||"0"));
end=parseFloat(_49.end?_49.end:(_47.font&&_47.font.size||"0"));
_4a.size=new _8(_4c,end,_49.unit);
}
this.curve=new _14(_4a,_48);
});
d.connect(_46,"onAnimate",_47,"setFont");
return _46;
};
g.fx.animateTransform=function(_4e){
if(!_4e.easing){
_4e.easing=d._defaultEasing;
}
var _4f=new d._Animation(_4e),_50=_4e.shape,_51;
d.connect(_4f,"beforeBegin",_4f,function(){
_51=_50.getTransform();
this.curve=new _1a(_4e.transform,_51);
});
d.connect(_4f,"onAnimate",_50,"setTransform");
return _4f;
};
})();
}
