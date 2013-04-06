/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xml.parser"]){
dojo._hasResource["dojox.xml.parser"]=true;
dojo.provide("dojox.xml.parser");
dojox.xml.parser.parse=function(_1,_2){
var _3=dojo.doc;
var _4;
_2=_2||"text/xml";
if(_1&&dojo.trim(_1)&&"DOMParser" in dojo.global){
var _5=new DOMParser();
_4=_5.parseFromString(_1,_2);
var de=_4.documentElement;
var _7="http://www.mozilla.org/newlayout/xml/parsererror.xml";
if(de.nodeName=="parsererror"&&de.namespaceURI==_7){
var _8=de.getElementsByTagNameNS(_7,"sourcetext")[0];
if(!_8){
_8=_8.firstChild.data;
}
throw new Error("Error parsing text "+nativeDoc.documentElement.firstChild.data+" \n"+_8);
}
return _4;
}else{
if("ActiveXObject" in dojo.global){
var ms=function(n){
return "MSXML"+n+".DOMDocument";
};
var dp=["Microsoft.XMLDOM",ms(6),ms(4),ms(3),ms(2)];
dojo.some(dp,function(p){
try{
_4=new ActiveXObject(p);
}
catch(e){
return false;
}
return true;
});
if(_1&&_4){
_4.async=false;
_4.loadXML(_1);
var pe=_4.parseError;
if(pe.errorCode!==0){
throw new Error("Line: "+pe.line+"\n"+"Col: "+pe.linepos+"\n"+"Reason: "+pe.reason+"\n"+"Error Code: "+pe.errorCode+"\n"+"Source: "+pe.srcText);
}
}
if(_4){
return _4;
}
}else{
if(_3.implementation&&_3.implementation.createDocument){
if(_1&&dojo.trim(_1)&&_3.createElement){
var _e=_3.createElement("xml");
_e.innerHTML=_1;
var _f=_3.implementation.createDocument("foo","",null);
dojo.forEach(_e.childNodes,function(_10){
_f.importNode(_10,true);
});
return _f;
}else{
return _3.implementation.createDocument("","",null);
}
}
}
}
return null;
};
dojox.xml.parser.textContent=function(_11,_12){
if(arguments.length>1){
var _13=_11.ownerDocument||dojo.doc;
dojox.xml.parser.replaceChildren(_11,_13.createTextNode(_12));
return _12;
}else{
if(_11.textContent!==undefined){
return _11.textContent;
}
var _14="";
if(_11){
dojo.forEach(_11.childNodes,function(_15){
switch(_15.nodeType){
case 1:
case 5:
_14+=dojox.xml.parser.textContent(_15);
break;
case 3:
case 2:
case 4:
_14+=_15.nodeValue;
}
});
}
return _14;
}
};
dojox.xml.parser.replaceChildren=function(_16,_17){
var _18=[];
if(dojo.isIE){
dojo.forEach(_16.childNodes,function(_19){
_18.push(_19);
});
}
dojox.xml.parser.removeChildren(_16);
dojo.forEach(_18,dojo.destroy);
if(!dojo.isArray(_17)){
_16.appendChild(_17);
}else{
dojo.forEach(_17,function(_1a){
_16.appendChild(_1a);
});
}
};
dojox.xml.parser.removeChildren=function(_1b){
var _1c=_1b.childNodes.length;
while(_1b.hasChildNodes()){
_1b.removeChild(_1b.firstChild);
}
return _1c;
};
dojox.xml.parser.innerXML=function(_1d){
if(_1d.innerXML){
return _1d.innerXML;
}else{
if(_1d.xml){
return _1d.xml;
}else{
if(typeof XMLSerializer!="undefined"){
return (new XMLSerializer()).serializeToString(_1d);
}
}
}
return null;
};
}
