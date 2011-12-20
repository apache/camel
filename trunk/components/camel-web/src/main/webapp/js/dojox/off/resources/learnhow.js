/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


window.onload=function(){
var _1=window.location.href;
var _2=_1.match(/appName=([a-z0-9 \%]*)/i);
var _3="Application";
if(_2&&_2.length>0){
_3=decodeURIComponent(_2[1]);
}
var _4=document.getElementById("dot-learn-how-app-name");
_4.innerHTML="";
_4.appendChild(document.createTextNode(_3));
_2=_1.match(/hasOfflineCache=(true|false)/);
var _5=false;
if(_2&&_2.length>0){
_5=_2[1];
_5=(_5=="true")?true:false;
}
if(_5==true){
var _6=document.getElementById("dot-download-step");
var _7=document.getElementById("dot-install-step");
_6.parentNode.removeChild(_6);
_7.parentNode.removeChild(_7);
}
_2=_1.match(/runLink=([^\&]*)\&runLinkText=([^\&]*)/);
if(_2&&_2.length>0){
var _8=decodeURIComponent(_2[1]);
var _9=document.getElementById("dot-learn-how-run-link");
_9.setAttribute("href",_8);
var _a=decodeURIComponent(_2[2]);
_9.innerHTML="";
_9.appendChild(document.createTextNode(_a));
}
};
