/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.off.ui"]){
dojo._hasResource["dojox.off.ui"]=true;
dojo.provide("dojox.off.ui");
dojo.require("dojox.storage.Provider");
dojo.require("dojox.storage.manager");
dojo.require("dojox.storage.GearsStorageProvider");
dojo.mixin(dojox.off.ui,{appName:"setme",autoEmbed:true,autoEmbedID:"dot-widget",runLink:window.location.href,runLinkTitle:"Run Application",learnHowPath:dojo.moduleUrl("dojox","off/resources/learnhow.html"),customLearnHowPath:false,htmlTemplatePath:dojo.moduleUrl("dojox","off/resources/offline-widget.html").uri,cssTemplatePath:dojo.moduleUrl("dojox","off/resources/offline-widget.css").uri,onlineImagePath:dojo.moduleUrl("dojox","off/resources/greenball.png").uri,offlineImagePath:dojo.moduleUrl("dojox","off/resources/redball.png").uri,rollerImagePath:dojo.moduleUrl("dojox","off/resources/roller.gif").uri,checkmarkImagePath:dojo.moduleUrl("dojox","off/resources/checkmark.png").uri,learnHowJSPath:dojo.moduleUrl("dojox","off/resources/learnhow.js").uri,_initialized:false,onLoad:function(){
},_initialize:function(){
if(this._validateAppName(this.appName)==false){
alert("You must set dojox.off.ui.appName; it can only contain "+"letters, numbers, and spaces; right now it "+"is incorrectly set to '"+dojox.off.ui.appName+"'");
dojox.off.enabled=false;
return;
}
this.runLinkText="Run "+this.appName;
dojo.connect(dojox.off,"onNetwork",this,"_onNetwork");
dojo.connect(dojox.off.sync,"onSync",this,"_onSync");
dojox.off.files.cache([this.htmlTemplatePath,this.cssTemplatePath,this.onlineImagePath,this.offlineImagePath,this.rollerImagePath,this.checkmarkImagePath]);
if(this.autoEmbed){
this._doAutoEmbed();
}
},_doAutoEmbed:function(){
dojo.xhrGet({url:this.htmlTemplatePath,handleAs:"text",error:function(_1){
dojox.off.enabled=false;
_1=_1.message||_1;
alert("Error loading the Dojo Offline Widget from "+this.htmlTemplatePath+": "+_1);
},load:dojo.hitch(this,this._templateLoaded)});
},_templateLoaded:function(_2){
var _3=dojo.byId(this.autoEmbedID);
if(_3){
_3.innerHTML=_2;
}
this._initImages();
this._updateNetIndicator();
this._initLearnHow();
this._initialized=true;
if(!dojox.off.hasOfflineCache){
this._showNeedsOfflineCache();
return;
}
if(dojox.off.hasOfflineCache&&dojox.off.browserRestart){
this._needsBrowserRestart();
return;
}else{
var _4=dojo.byId("dot-widget-browser-restart");
if(_4){
_4.style.display="none";
}
}
this._updateSyncUI();
this._initMainEvtHandlers();
this._setOfflineEnabled(dojox.off.enabled);
this._onNetwork(dojox.off.isOnline?"online":"offline");
this._testNet();
},_testNet:function(){
dojox.off.goOnline(dojo.hitch(this,function(_5){
this._onNetwork(_5?"online":"offline");
this.onLoad();
}));
},_updateNetIndicator:function(){
var _6=dojo.byId("dot-widget-network-indicator-online");
var _7=dojo.byId("dot-widget-network-indicator-offline");
var _8=dojo.byId("dot-widget-title-text");
if(_6&&_7){
if(dojox.off.isOnline==true){
_6.style.display="inline";
_7.style.display="none";
}else{
_6.style.display="none";
_7.style.display="inline";
}
}
if(_8){
if(dojox.off.isOnline){
_8.innerHTML="Online";
}else{
_8.innerHTML="Offline";
}
}
},_initLearnHow:function(){
var _9=dojo.byId("dot-widget-learn-how-link");
if(!_9){
return;
}
if(!this.customLearnHowPath){
var _a=dojo.config.baseRelativePath;
this.learnHowPath+="?appName="+encodeURIComponent(this.appName)+"&hasOfflineCache="+dojox.off.hasOfflineCache+"&runLink="+encodeURIComponent(this.runLink)+"&runLinkText="+encodeURIComponent(this.runLinkText)+"&baseRelativePath="+encodeURIComponent(_a);
dojox.off.files.cache(this.learnHowJSPath);
dojox.off.files.cache(this.learnHowPath);
}
_9.setAttribute("href",this.learnHowPath);
var _b=dojo.byId("dot-widget-learn-how-app-name");
if(!_b){
return;
}
_b.innerHTML="";
_b.appendChild(document.createTextNode(this.appName));
},_validateAppName:function(_c){
if(!_c){
return false;
}
return (/^[a-z0-9 ]*$/i.test(_c));
},_updateSyncUI:function(){
var _d=dojo.byId("dot-roller");
var _e=dojo.byId("dot-success-checkmark");
var _f=dojo.byId("dot-sync-messages");
var _10=dojo.byId("dot-sync-details");
var _11=dojo.byId("dot-sync-cancel");
if(dojox.off.sync.isSyncing){
this._clearSyncMessage();
if(_d){
_d.style.display="inline";
}
if(_e){
_e.style.display="none";
}
if(_f){
dojo.removeClass(_f,"dot-sync-error");
}
if(_10){
_10.style.display="none";
}
if(_11){
_11.style.display="inline";
}
}else{
if(_d){
_d.style.display="none";
}
if(_11){
_11.style.display="none";
}
if(_f){
dojo.removeClass(_f,"dot-sync-error");
}
}
},_setSyncMessage:function(_12){
var _13=dojo.byId("dot-sync-messages");
if(_13){
while(_13.firstChild){
_13.removeChild(_13.firstChild);
}
_13.appendChild(document.createTextNode(_12));
}
},_clearSyncMessage:function(){
this._setSyncMessage("");
},_initImages:function(){
var _14=dojo.byId("dot-widget-network-indicator-online");
if(_14){
_14.setAttribute("src",this.onlineImagePath);
}
var _15=dojo.byId("dot-widget-network-indicator-offline");
if(_15){
_15.setAttribute("src",this.offlineImagePath);
}
var _16=dojo.byId("dot-roller");
if(_16){
_16.setAttribute("src",this.rollerImagePath);
}
var _17=dojo.byId("dot-success-checkmark");
if(_17){
_17.setAttribute("src",this.checkmarkImagePath);
}
},_showDetails:function(evt){
evt.preventDefault();
evt.stopPropagation();
if(!dojox.off.sync.details.length){
return;
}
var _19="";
_19+="<html><head><title>Sync Details</title><head><body>";
_19+="<h1>Sync Details</h1>\n";
_19+="<ul>\n";
for(var i=0;i<dojox.off.sync.details.length;i++){
_19+="<li>";
_19+=dojox.off.sync.details[i];
_19+="</li>";
}
_19+="</ul>\n";
_19+="<a href='javascript:window.close()' "+"style='text-align: right; padding-right: 2em;'>"+"Close Window"+"</a>\n";
_19+="</body></html>";
var _1b="height=400,width=600,resizable=true,"+"scrollbars=true,toolbar=no,menubar=no,"+"location=no,directories=no,dependent=yes";
var _1c=window.open("","SyncDetails",_1b);
if(!_1c){
alert("Please allow popup windows for this domain; can't display sync details window");
return;
}
_1c.document.open();
_1c.document.write(_19);
_1c.document.close();
if(_1c.focus){
_1c.focus();
}
},_cancel:function(evt){
evt.preventDefault();
evt.stopPropagation();
dojox.off.sync.cancel();
},_needsBrowserRestart:function(){
var _1e=dojo.byId("dot-widget-browser-restart");
if(_1e){
dojo.addClass(_1e,"dot-needs-browser-restart");
}
var _1f=dojo.byId("dot-widget-browser-restart-app-name");
if(_1f){
_1f.innerHTML="";
_1f.appendChild(document.createTextNode(this.appName));
}
var _20=dojo.byId("dot-sync-status");
if(_20){
_20.style.display="none";
}
},_showNeedsOfflineCache:function(){
var _21=dojo.byId("dot-widget-container");
if(_21){
dojo.addClass(_21,"dot-needs-offline-cache");
}
},_hideNeedsOfflineCache:function(){
var _22=dojo.byId("dot-widget-container");
if(_22){
dojo.removeClass(_22,"dot-needs-offline-cache");
}
},_initMainEvtHandlers:function(){
var _23=dojo.byId("dot-sync-details-button");
if(_23){
dojo.connect(_23,"onclick",this,this._showDetails);
}
var _24=dojo.byId("dot-sync-cancel-button");
if(_24){
dojo.connect(_24,"onclick",this,this._cancel);
}
},_setOfflineEnabled:function(_25){
var _26=[];
_26.push(dojo.byId("dot-sync-status"));
for(var i=0;i<_26.length;i++){
if(_26[i]){
_26[i].style.visibility=(_25?"visible":"hidden");
}
}
},_syncFinished:function(){
this._updateSyncUI();
var _28=dojo.byId("dot-success-checkmark");
var _29=dojo.byId("dot-sync-details");
if(dojox.off.sync.successful==true){
this._setSyncMessage("Sync Successful");
if(_28){
_28.style.display="inline";
}
}else{
if(dojox.off.sync.cancelled==true){
this._setSyncMessage("Sync Cancelled");
if(_28){
_28.style.display="none";
}
}else{
this._setSyncMessage("Sync Error");
var _2a=dojo.byId("dot-sync-messages");
if(_2a){
dojo.addClass(_2a,"dot-sync-error");
}
if(_28){
_28.style.display="none";
}
}
}
if(dojox.off.sync.details.length&&_29){
_29.style.display="inline";
}
},_onFrameworkEvent:function(_2b,_2c){
if(_2b=="save"){
if(_2c.status==dojox.storage.FAILED&&!_2c.isCoreSave){
alert("Please increase the amount of local storage available "+"to this application");
if(dojox.storage.hasSettingsUI()){
dojox.storage.showSettingsUI();
}
}
}else{
if(_2b=="coreOperationFailed"){

if(!this._userInformed){
alert("This application will not work if Google Gears is not allowed to run");
this._userInformed=true;
}
}else{
if(_2b=="offlineCacheInstalled"){
this._hideNeedsOfflineCache();
if(dojox.off.hasOfflineCache==true&&dojox.off.browserRestart==true){
this._needsBrowserRestart();
return;
}else{
var _2d=dojo.byId("dot-widget-browser-restart");
if(_2d){
_2d.style.display="none";
}
}
this._updateSyncUI();
this._initMainEvtHandlers();
this._setOfflineEnabled(dojox.off.enabled);
this._testNet();
}
}
}
},_onSync:function(_2e){
switch(_2e){
case "start":
this._updateSyncUI();
break;
case "refreshFiles":
this._setSyncMessage("Downloading UI...");
break;
case "upload":
this._setSyncMessage("Uploading new data...");
break;
case "download":
this._setSyncMessage("Downloading new data...");
break;
case "finished":
this._syncFinished();
break;
case "cancel":
this._setSyncMessage("Canceling Sync...");
break;
default:
dojo.warn("Programming error: "+"Unknown sync type in dojox.off.ui: "+_2e);
break;
}
},_onNetwork:function(_2f){
if(!this._initialized){
return;
}
this._updateNetIndicator();
if(_2f=="offline"){
this._setSyncMessage("You are working offline");
var _30=dojo.byId("dot-sync-details");
if(_30){
_30.style.display="none";
}
this._updateSyncUI();
}else{
if(dojox.off.sync.autoSync){
if(dojo.isAIR){
window.setTimeout(function(){
dojox.off.sync.synchronize();
},1000);
}else{
window.setTimeout(dojox._scopeName+".off.sync.synchronize()",1000);
}
}
}
}});
dojo.connect(dojox.off,"onFrameworkEvent",dojox.off.ui,"_onFrameworkEvent");
dojo.connect(dojox.off,"onLoad",dojox.off.ui,dojox.off.ui._initialize);
}
