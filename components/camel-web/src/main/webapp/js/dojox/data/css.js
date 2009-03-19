/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.css"]){
dojo._hasResource["dojox.data.css"]=true;
dojo.provide("dojox.data.css");
dojo.provide("dojox.data.css.rules");
dojox.data.css.rules.forEach=function(fn,_2,_3){
if(_3){
var _4=function(_5){
dojo.forEach(_5[_5.cssRules?"cssRules":"rules"],function(_6){
if(!_6.type||_6.type!==3){
var _7="";
if(_5&&_5.href){
_7=_5.href;
}
fn.call(_2?_2:this,_6,_5,_7);
}
});
};
dojo.forEach(_3,_4);
}
};
dojox.data.css.findStyleSheets=function(_8){
var _9=[];
var _a=function(_b){
var s=dojox.data.css.findStyleSheet(_b);
if(s){
dojo.forEach(s,function(_d){
if(dojo.indexOf(_9,_d)===-1){
_9.push(_d);
}
});
}
};
dojo.forEach(_8,_a);
return _9;
};
dojox.data.css.findStyleSheet=function(_e){
var _f=[];
if(_e.charAt(0)==="."){
_e=_e.substring(1);
}
var _10=function(_11){
if(_11.href&&_11.href.match(_e)){
_f.push(_11);
return true;
}
if(_11.imports){
return dojo.some(_11.imports,function(_12){
return _10(_12);
});
}
return dojo.some(_11[_11.cssRules?"cssRules":"rules"],function(_13){
if(_13.type&&_13.type===3&&_10(_13.styleSheet)){
return true;
}
return false;
});
};
dojo.some(document.styleSheets,_10);
return _f;
};
dojox.data.css.determineContext=function(_14){
var ret=[];
if(_14&&_14.length>0){
_14=dojox.data.css.findStyleSheets(_14);
}else{
_14=document.styleSheets;
}
var _16=function(_17){
ret.push(_17);
if(_17.imports){
dojo.forEach(_17.imports,function(_18){
_16(_18);
});
}
dojo.forEach(_17[_17.cssRules?"cssRules":"rules"],function(_19){
if(_19.type&&_19.type===3){
_16(_19.styleSheet);
}
});
};
dojo.forEach(_14,_16);
return ret;
};
}
