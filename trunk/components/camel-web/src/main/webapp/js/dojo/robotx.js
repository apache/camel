/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojo.robotx"]){
dojo._hasResource["dojo.robotx"]=true;
dojo.provide("dojo.robotx");
dojo.require("dojo.robot");
dojo.experimental("dojo.robotx");
(function(){
doh.robot._runsemaphore.lock.push("dojo.robotx.lock");
var _1=document.getElementById("robotapplication");
var _2=dojo.connect(doh,"_groupStarted",function(){
dojo.disconnect(_2);
if(!document.getElementById("robotconsole").childNodes.length){
document.body.removeChild(document.getElementById("robotconsole"));
_1.style.height="100%";
}
_1.style.visibility="visible";
});
var _3=function(){
doh.robot._updateDocument();
_3=null;
doh.run();
};
var _4=function(){
if(_3){
_3();
}
var _5=dojo.connect(dojo.body(),"onunload",function(){
dojo.global=window;
dojo.doc=document;
dojo.disconnect(_5);
});
};
dojo.config.debugContainerId="robotconsole";
document.write("<div id=\"robotconsole\" style=\"position:absolute;left:0px;top:75%;width:100%; height:25%;\"></div>");
_1=document.createElement("iframe");
_1.setAttribute("ALLOWTRANSPARENCY","true");
dojo.style(_1,{visibility:"hidden",border:"0px none",padding:"0px",margin:"0px",position:"absolute",left:"0px",top:"0px",width:"100%",height:"75%",zIndex:"1"});
if(_1["attachEvent"]!==undefined){
_1.attachEvent("onload",_4);
}else{
dojo.connect(_1,"onload",_4);
}
dojo.mixin(doh.robot,{_updateDocument:function(){
dojo.setContext(_1.contentWindow,_1.contentWindow.document);
var _6=dojo.global;
if(_6["dojo"]){
dojo._topics=_6.dojo._topics;
}
},initRobot:function(_7){
_1.src=_7;
dojo.addOnLoad(function(){
dojo.style(document.body,{width:"100%",height:"100%"});
document.body.appendChild(_1);
var _8=document.createElement("base");
_8.href=_7;
document.getElementsByTagName("head")[0].appendChild(_8);
});
},waitForPageToLoad:function(_9){
var d=new doh.Deferred();
_3=function(){
_3=null;
doh.robot._updateDocument();
d.callback(true);
};
_9();
return d;
}});
})();
}
