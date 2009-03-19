/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.sniff"]){
dojo._hasResource["dijit._base.sniff"]=true;
dojo.provide("dijit._base.sniff");
(function(){
var d=dojo,_2=d.doc.documentElement,ie=d.isIE,_4=d.isOpera,_5=Math.floor,ff=d.isFF,_7=d.boxModel.replace(/-/,""),_8={dj_ie:ie,dj_ie6:_5(ie)==6,dj_ie7:_5(ie)==7,dj_iequirks:ie&&d.isQuirks,dj_opera:_4,dj_opera8:_5(_4)==8,dj_opera9:_5(_4)==9,dj_khtml:d.isKhtml,dj_webkit:d.isWebKit,dj_safari:d.isSafari,dj_gecko:d.isMozilla,dj_ff2:_5(ff)==2,dj_ff3:_5(ff)==3};
_8["dj_"+_7]=true;
for(var p in _8){
if(_8[p]){
if(_2.className){
_2.className+=" "+p;
}else{
_2.className=p;
}
}
}
dojo._loaders.unshift(function(){
if(!dojo._isBodyLtr()){
_2.className+=" dijitRtl";
for(var p in _8){
if(_8[p]){
_2.className+=" "+p+"-rtl";
}
}
}
});
})();
}
