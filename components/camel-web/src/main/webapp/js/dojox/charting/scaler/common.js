/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.scaler.common"]){
dojo._hasResource["dojox.charting.scaler.common"]=true;
dojo.provide("dojox.charting.scaler.common");
(function(){
var eq=function(a,b){
return Math.abs(a-b)<=0.000001*(Math.abs(a)+Math.abs(b));
};
dojo.mixin(dojox.charting.scaler.common,{findString:function(_4,_5){
_4=_4.toLowerCase();
for(var i=0;i<_5.length;++i){
if(_4==_5[i]){
return true;
}
}
return false;
},getNumericLabel:function(_7,_8,_9){
var _a=_9.fixed?_7.toFixed(_8<0?-_8:0):_7.toString();
if(_9.labelFunc){
var r=_9.labelFunc(_a,_7,_8);
if(r){
return r;
}
}
if(_9.labels){
var l=_9.labels,lo=0,hi=l.length;
while(lo<hi){
var _f=Math.floor((lo+hi)/2),val=l[_f].value;
if(val<_7){
lo=_f+1;
}else{
hi=_f;
}
}
if(lo<l.length&&eq(l[lo].value,_7)){
return l[lo].text;
}
--lo;
if(lo>=0&&lo<l.length&&eq(l[lo].value,_7)){
return l[lo].text;
}
lo+=2;
if(lo<l.length&&eq(l[lo].value,_7)){
return l[lo].text;
}
}
return _a;
}});
})();
}
