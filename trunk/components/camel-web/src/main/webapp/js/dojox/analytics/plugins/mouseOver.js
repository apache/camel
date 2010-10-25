/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.analytics.plugins.mouseOver"]){
dojo._hasResource["dojox.analytics.plugins.mouseOver"]=true;
dojo.provide("dojox.analytics.plugins.mouseOver");
dojox.analytics.plugins.mouseOver=new (function(){
this.watchMouse=dojo.config["watchMouseOver"]||true;
this.mouseSampleDelay=dojo.config["sampleDelay"]||2500;
this.addData=dojo.hitch(dojox.analytics,"addData","mouseOver");
this.targetProps=dojo.config["targetProps"]||["id","className","localName","href","spellcheck","lang","textContent","value"];
this.toggleWatchMouse=function(){
if(this._watchingMouse){
dojo.disconnect(this._watchingMouse);
delete this._watchingMouse;
return;
}
dojo.connect(dojo.doc,"onmousemove",this,"sampleMouse");
};
if(this.watchMouse){
dojo.connect(dojo.doc,"onmouseover",this,"toggleWatchMouse");
dojo.connect(dojo.doc,"onmouseout",this,"toggleWatchMouse");
}
this.sampleMouse=function(e){
if(!this._rateLimited){
this.addData("sample",this.trimMouseEvent(e));
this._rateLimited=true;
setTimeout(dojo.hitch(this,function(){
if(this._rateLimited){
this.trimMouseEvent(this._lastMouseEvent);
delete this._lastMouseEvent;
delete this._rateLimited;
}
}),this.mouseSampleDelay);
}
this._lastMouseEvent=e;
return e;
};
this.trimMouseEvent=function(e){
var t={};
for(var i in e){
switch(i){
case "target":
var _5=this.targetProps;
t[i]={};
for(var j=0;j<_5.length;j++){
if(dojo.isObject(e[i])&&_5[j] in e[i]){
if(_5[j]=="text"||_5[j]=="textContent"){
if(e[i]["localName"]&&(e[i]["localName"]!="HTML")&&(e[i]["localName"]!="BODY")){
t[i][_5[j]]=e[i][_5[j]].substr(0,50);
}
}else{
t[i][_5[j]]=e[i][_5[j]];
}
}
}
break;
case "x":
case "y":
if(e[i]){
var _7=e[i];
t[i]=_7+"";
}
break;
default:
break;
}
}
return t;
};
})();
}
