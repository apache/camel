/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.html.styles"]){
dojo._hasResource["dojox.html.styles"]=true;
dojo.provide("dojox.html.styles");
(function(){
var _1={};
var _2={};
var _3=[];
var _4=[];
dojox.html.insertCssRule=function(_5,_6,_7){
var ss=dojox.html.getDynamicStyleSheet(_7);
var _9=_5+" {"+_6+"}";

if(dojo.isIE){
ss.cssText+=_9;

}else{
if(ss.sheet){
ss.sheet.insertRule(_9,ss._indicies.length);
}else{
ss.appendChild(dojo.doc.createTextNode(_9));
}
}
ss._indicies.push(_5+" "+_6);
return _5;
};
dojox.html.removeCssRule=function(_a,_b,_c){
var ss;
var _e=-1;
for(var nm in _1){
if(_c&&_c!=nm){
continue;
}
ss=_1[nm];
for(var i=0;i<ss._indicies.length;i++){
if(_a+" "+_b==ss._indicies[i]){
_e=i;
break;
}
}
if(_e>-1){
break;
}
}
if(!ss){

return false;
}
if(_e==-1){

return false;
}
ss._indicies.splice(_e,1);
if(dojo.isIE){
ss.removeRule(_e);
}else{
if(ss.sheet){
ss.sheet.deleteRule(_e);
}else{
if(document.styleSheets[0]){

}
}
}
return true;
};
dojox.html.getStyleSheet=function(_11){
if(_1[_11||"default"]){
return _1[_11||"default"];
}
if(!_11){
return false;
}
var _12=dojox.html.getStyleSheets();
if(_12[_11]){
return dojox.html.getStyleSheets()[_11];
}
for(var nm in _12){
if(_12[nm].href&&_12[nm].href.indexOf(_11)>-1){
return _12[nm];
}
}
return false;
};
dojox.html.getDynamicStyleSheet=function(_14){
if(!_14){
_14="default";
}
if(!_1[_14]){
if(dojo.doc.createStyleSheet){
_1[_14]=dojo.doc.createStyleSheet();
_1[_14].title=_14;
}else{
_1[_14]=dojo.doc.createElement("style");
_1[_14].setAttribute("type","text/css");
dojo.doc.getElementsByTagName("head")[0].appendChild(_1[_14]);

}
_1[_14]._indicies=[];
}
return _1[_14];
};
dojox.html.enableStyleSheet=function(_15){
var ss=dojox.html.getStyleSheet(_15);
if(ss){
if(ss.sheet){
ss.sheet.disabled=false;
}else{
ss.disabled=false;
}
}
};
dojox.html.disableStyleSheet=function(_17){
var ss=dojox.html.getStyleSheet(_17);
if(ss){
if(ss.sheet){
ss.sheet.disabled=true;
}else{
ss.disabled=true;
}
}
};
dojox.html.activeStyleSheet=function(_19){
var _1a=dojox.html.getToggledStyleSheets();
if(arguments.length==1){
dojo.forEach(_1a,function(s){
s.disabled=(s.title==_19)?false:true;
});
}else{
for(var i=0;i<_1a.length;i++){
if(_1a[i].disabled==false){
return _1a[i];
}
}
}
return true;
};
dojox.html.getPreferredStyleSheet=function(){
};
dojox.html.getToggledStyleSheets=function(){
if(!_3.length){
var _1d=dojox.html.getStyleSheets();
for(var nm in _1d){
if(_1d[nm].title){
_3.push(_1d[nm]);
}
}
}
return _3;
};
dojox.html.getStyleSheets=function(){
if(_2.collected){
return _2;
}
var _1f=dojo.doc.styleSheets;
dojo.forEach(_1f,function(n){
var s=(n.sheet)?n.sheet:n;
var _22=s.title||s.href;
if(dojo.isIE){
if(s.cssText.indexOf("#default#VML")==-1){
if(s.href){
_2[_22]=s;
}else{
if(s.imports.length){
dojo.forEach(s.imports,function(si){
_2[si.title||si.href]=si;
});
}else{
_2[_22]=s;
}
}
}
}else{
_2[_22]=s;
_2[_22].id=s.ownerNode.id;
dojo.forEach(s.cssRules,function(r){
if(r.href){
_2[r.href]=r.styleSheet;
_2[r.href].id=s.ownerNode.id;
}
});
}
});
_2.collected=true;
return _2;
};
})();
}
