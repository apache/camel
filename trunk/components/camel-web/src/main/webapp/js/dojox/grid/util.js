/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.util"]){
dojo._hasResource["dojox.grid.util"]=true;
dojo.provide("dojox.grid.util");
(function(){
var _1=dojox.grid.util;
_1.na="...";
_1.rowIndexTag="gridRowIndex";
_1.gridViewTag="gridView";
_1.fire=function(ob,ev,_4){
var fn=ob&&ev&&ob[ev];
return fn&&(_4?fn.apply(ob,_4):ob[ev]());
};
_1.setStyleHeightPx=function(_6,_7){
if(_7>=0){
var s=_6.style;
var v=_7+"px";
if(_6&&s["height"]!=v){
s["height"]=v;
}
}
};
_1.mouseEvents=["mouseover","mouseout","mousedown","mouseup","click","dblclick","contextmenu"];
_1.keyEvents=["keyup","keydown","keypress"];
_1.funnelEvents=function(_a,_b,_c,_d){
var _e=(_d?_d:_1.mouseEvents.concat(_1.keyEvents));
for(var i=0,l=_e.length;i<l;i++){
_b.connect(_a,"on"+_e[i],_c);
}
},_1.removeNode=function(_11){
_11=dojo.byId(_11);
_11&&_11.parentNode&&_11.parentNode.removeChild(_11);
return _11;
};
_1.arrayCompare=function(inA,inB){
for(var i=0,l=inA.length;i<l;i++){
if(inA[i]!=inB[i]){
return false;
}
}
return (inA.length==inB.length);
};
_1.arrayInsert=function(_16,_17,_18){
if(_16.length<=_17){
_16[_17]=_18;
}else{
_16.splice(_17,0,_18);
}
};
_1.arrayRemove=function(_19,_1a){
_19.splice(_1a,1);
};
_1.arraySwap=function(_1b,inI,inJ){
var _1e=_1b[inI];
_1b[inI]=_1b[inJ];
_1b[inJ]=_1e;
};
})();
}
