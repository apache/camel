/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.off.sync"]){
dojo._hasResource["dojox.off.sync"]=true;
dojo.provide("dojox.off.sync");
dojo.require("dojox.storage.GearsStorageProvider");
dojo.require("dojox.off._common");
dojo.require("dojox.off.files");
dojo.mixin(dojox.off.sync,{isSyncing:false,cancelled:false,successful:true,details:[],error:false,actions:null,autoSync:true,onSync:function(_1){
},synchronize:function(){
if(this.isSyncing||dojox.off.goingOnline||(!dojox.off.isOnline)){
return;
}
this.isSyncing=true;
this.successful=false;
this.details=[];
this.cancelled=false;
this.start();
},cancel:function(){
if(!this.isSyncing){
return;
}
this.cancelled=true;
if(dojox.off.files.refreshing){
dojox.off.files.abortRefresh();
}
this.onSync("cancel");
},finishedDownloading:function(_2,_3){
if(typeof _2=="undefined"){
_2=true;
}
if(!_2){
this.successful=false;
this.details.push(_3);
this.error=true;
}
this.finished();
},start:function(){
if(this.cancelled){
this.finished();
return;
}
this.onSync("start");
this.refreshFiles();
},refreshFiles:function(){
if(this.cancelled){
this.finished();
return;
}
this.onSync("refreshFiles");
dojox.off.files.refresh(dojo.hitch(this,function(_4,_5){
if(_4){
this.error=true;
this.successful=false;
for(var i=0;i<_5.length;i++){
this.details.push(_5[i]);
}
}
this.upload();
}));
},upload:function(){
if(this.cancelled){
this.finished();
return;
}
this.onSync("upload");
dojo.connect(this.actions,"onReplayFinished",this,this.download);
this.actions.replay();
},download:function(){
if(this.cancelled){
this.finished();
return;
}
this.onSync("download");
},finished:function(){
this.isSyncing=false;
this.successful=(!this.cancelled&&!this.error);
this.onSync("finished");
},_save:function(_7){
this.actions._save(function(){
_7();
});
},_load:function(_8){
this.actions._load(function(){
_8();
});
}});
dojo.declare("dojox.off.sync.ActionLog",null,{entries:[],reasonHalted:null,isReplaying:false,autoSave:true,add:function(_9){
if(this.isReplaying){
throw "Programming error: you can not call "+"dojox.off.sync.actions.add() while "+"we are replaying an action log";
}
this.entries.push(_9);
if(this.autoSave){
this._save();
}
},onReplay:function(_a,_b){
},length:function(){
return this.entries.length;
},haltReplay:function(_c){
if(!this.isReplaying){
return;
}
if(_c){
this.reasonHalted=_c.toString();
}
if(this.autoSave){
var _d=this;
this._save(function(){
_d.isReplaying=false;
_d.onReplayFinished();
});
}else{
this.isReplaying=false;
this.onReplayFinished();
}
},continueReplay:function(){
if(!this.isReplaying){
return;
}
this.entries.shift();
if(!this.entries.length){
if(this.autoSave){
var _e=this;
this._save(function(){
_e.isReplaying=false;
_e.onReplayFinished();
});
return;
}else{
this.isReplaying=false;
this.onReplayFinished();
return;
}
}
var _f=this.entries[0];
this.onReplay(_f,this);
},clear:function(){
if(this.isReplaying){
return;
}
this.entries=[];
if(this.autoSave){
this._save();
}
},replay:function(){
if(this.isReplaying){
return;
}
this.reasonHalted=null;
if(!this.entries.length){
this.onReplayFinished();
return;
}
this.isReplaying=true;
var _10=this.entries[0];
this.onReplay(_10,this);
},onReplayFinished:function(){
},toString:function(){
var _11="";
_11+="[";
for(var i=0;i<this.entries.length;i++){
_11+="{";
for(var j in this.entries[i]){
_11+=j+": \""+this.entries[i][j]+"\"";
_11+=", ";
}
_11+="}, ";
}
_11+="]";
return _11;
},_save:function(_14){
if(!_14){
_14=function(){
};
}
try{
var _15=this;
var _16=function(_17,key,_19){
if(_17==dojox.storage.FAILED){
dojox.off.onFrameworkEvent("save",{status:dojox.storage.FAILED,isCoreSave:true,key:key,value:_19,namespace:dojox.off.STORAGE_NAMESPACE});
_14();
}else{
if(_17==dojox.storage.SUCCESS){
_14();
}
}
};
dojox.storage.put("actionlog",this.entries,_16,dojox.off.STORAGE_NAMESPACE);
}
catch(exp){

dojox.off.onFrameworkEvent("save",{status:dojox.storage.FAILED,isCoreSave:true,key:"actionlog",value:this.entries,namespace:dojox.off.STORAGE_NAMESPACE});
_14();
}
},_load:function(_1a){
var _1b=dojox.storage.get("actionlog",dojox.off.STORAGE_NAMESPACE);
if(!_1b){
_1b=[];
}
this.entries=_1b;
_1a();
}});
dojox.off.sync.actions=new dojox.off.sync.ActionLog();
}
