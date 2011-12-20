/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.FileUploader"]){
dojo._hasResource["dojox.form.FileUploader"]=true;
dojo.provide("dojox.form.FileUploader");
dojo.experimental("dojox.form.FileUploader");
var swfPath=dojo.config.uploaderPath||dojo.moduleUrl("dojox.form","resources/uploader.swf");
dojo.require("dojox.embed.Flash");
dojo.require("dojo.io.iframe");
dojo.require("dojox.html.styles");
dojo.declare("dojox.form.FileUploader",null,{isDebug:false,devMode:false,id:"",uploadUrl:"",button:null,uploadOnChange:false,selectMultipleFiles:true,htmlFieldName:"uploadedfile",flashFieldName:"flashUploadFiles",fileMask:[],force:"",postData:null,swfPath:swfPath,minFlashVersion:9,uploaderType:"",flashObject:null,flashMovie:null,flashDiv:null,domNode:null,constructor:function(_1){
this.init(_1);
},log:function(){
if(this.isDebug){
console.log.apply(console,arguments);
}
},init:function(_2){
dojo.mixin(this,_2);

this.id=this.id||dijit.getUniqueId("uploader");
dijit.registry.add(this);
this.log("init Flash:",(dojox.embed.Flash.available>=this.minFlashVersion||this.force=="flash"),dojox.embed.Flash.available>=this.minFlashVersion,this.force=="flash");
this.fileList=[];
this._subs=[];
this._cons=[];
if((dojox.embed.Flash.available>=this.minFlashVersion||this.force=="flash")&&this.force!="html"){
this.uploaderType="flash";
this.createFlashUploader();
}else{
this.uploaderType="html";
this.fileInputs=[];
this.fileCount=0;
if(dojo.isIE&&dojo.isIE<7){
setTimeout(dojo.hitch(this,"createHtmlUploader"),1);
}else{
this.createHtmlUploader();
}
}
},onMouseDown:function(_3){
},onMouseUp:function(_4){
},onMouseOver:function(_5){
if(this.button.domNode){
dojo.addClass(this.button.domNode,"dijitButtonHover dijitHover");
}
},onMouseOut:function(_6){
if(this.button.domNode){
dojo.removeClass(this.button.domNode,"dijitButtonHover dijitHover");
}
},onChange:function(_7){
},onProgress:function(_8){
},onComplete:function(_9){
},onCancel:function(){
this.log("Upload Canceled");
},onError:function(_a){
var _b=_a.type?_a.type.toUpperCase():"ERROR";
var _c=_a.msg?_a.msg:_a;
console.warn("FLASH/ERROR/"+_b,_c);
},upload:function(_d){
if(_d){
this.postData=_d;
}
this.log("upload type:",this.uploaderType," - postData:",this.postData);
if(this.uploaderType=="flash"){
try{
this.flashMovie.doUpload(this.postData);
}
catch(err){
throw new Error("Sorry, the SWF failed to initialize properly. The page will have to be refreshed. ERROR:"+err);
}
}else{
dojo.io.iframe.send({url:this.uploadUrl,form:this._formNode,handleAs:"json",handle:dojo.hitch(this,function(_e,_f,_10){
this._complete([_e]);
})});
}
},setPosition:function(){
if(this.uploaderType=="flash"){
this.setFlashPosition();
}else{
this.setHtmlPosition();
}
},hide:function(){
dojo.style(this.domNode,"display","none");
},show:function(){
dojo.style(this.domNode,"display","");
},disable:function(_11){
if(_11){
this.hide();
}else{
this.show();
}
},destroyAll:function(){
if(this.button.destroy){
this.button.destroy();
}else{
dojo.destroy(this.button);
}
this.destroy();
},destroy:function(){
if(this.uploaderType=="flash"&&!this.flashMovie){
this._cons.push(dojo.connect(this,"onLoad",this,"destroy"));
return;
}
dojo.forEach(this._subs,function(s){
dojo.unsubscribe(s);
});
dojo.forEach(this._cons,function(c){
dojo.disconnect(c);
});
if(this.uploaderType=="flash"){
this.flashObject.destroy();
dojo.destroy(this.flashDiv);
}
},createFlashUploader:function(){
this.log("FLASH");
var _14=this.uploadUrl.toLowerCase();
if(_14.indexOf("http")<0&&_14.indexOf("/")!=0){
var loc=window.location.href.split("/");
loc.pop();
loc=loc.join("/")+"/";
this.uploadUrl=loc+this.uploadUrl;
}else{
}
var dim=this.getFakeButtonSize();
var w="100%";
var h="100%";
var _19={expressInstall:true,path:this.swfPath.uri||this.swfPath,width:w,height:h,allowScriptAccess:"always",allowNetworking:"all",vars:{uploadDataFieldName:this.flashFieldName,uploadUrl:this.uploadUrl,uploadOnSelect:this.uploadOnChange,selectMultipleFiles:this.selectMultipleFiles,id:this.id,isDebug:this.isDebug,devMode:this.devMode},params:{wmode:"transparent"}};
if(_19.vars.isDebug&&window.console&&window.console.dir){
window.passthrough=function(){
console.log.apply(console,arguments);
};
window.passthrough("Flash trace enabled.");
}else{
window.passthrough=function(){
};
}
this.flashDiv=dojo.doc.createElement("div");
this.domNode=this.flashDiv;
dojo.body().appendChild(this.flashDiv);
this._connectFlash();
this.setPosition();
this.flashObject=new dojox.embed.Flash(_19,this.flashDiv);
this.flashObject.onError=function(msg){
console.warn("Flash Error:",msg);
};
this.flashObject.onLoad=dojo.hitch(this,function(mov){
this.log("ONLOAD",mov);
this.flashMovie=mov;
this.setFlashVars();
});
},setFlashVars:function(){
this.flashMovie.setFileMask(this.fileMask);
this.flashMovie.setPostData(this.postData);

return;
try{
this.flashMovie.setFileMask(this.fileMask);
if(this.postData){
this.flashMovie.setPostData(this.postData);
}
}
catch(e){
if(this.setvarTries===undefined){
this.setvarTries=0;
}
this.setvarTries++;
if(this.setvarTries<10){
setTimeout(dojo.hitch(this,"setFlashVars"),500);
}else{
console.warn("Tried to set Flash Vars and Post data but failed.");
}
}
},createHtmlUploader:function(){
if(!this.button.id){
this.button.id=dijit.getUniqueId("btn");
}
var _1c;
if(this.button.domNode){
_1c=dojo.byId(this.button.id).parentNode.parentNode;
_1c.parentNode.onmousedown=function(){
};
}else{
_1c=this.button.parentNode;
}
this._buildForm(_1c);
this._buildFileInput(_1c);
this.setPosition();
this._connectInput();
},setFlashPosition:function(){
var dim=this.getFakeButtonSize();
setTimeout(dojo.hitch(this,function(){
dojo.style(this.flashDiv,{position:"absolute",top:dim.y+"px",left:dim.x+"px",width:dim.w+"px",height:dim.h+"px",zIndex:2001});
this.log("this.flashDiv:",this.flashDiv);
}),100);
},setHtmlPosition:function(){
var _1e=this.getFakeButtonSize();
var _1f=dojo.marginBox(this._fileInput);
var _20="rect(0px "+_1f.w+"px "+_1e.h+"px "+(_1f.w-_1e.w)+"px)";
this._fileInput.style.clip=_20;
this._fileInput.style.left=(_1e.x+_1e.w-_1f.w)+"px";
this._fileInput.style.top=_1e.y+"px";
this._fileInput.style.zIndex=2001;
},_connectFlash:function(){
this._doSub("/filesSelected","_change");
this._doSub("/filesUploaded","_complete");
this._doSub("/filesProgress","_progress");
this._doSub("/filesError","_error");
this._doSub("/filesCanceled","onCancel");
this._doSub("/up","onMouseUp");
this._doSub("/down","onMouseDown");
this._doSub("/over","onMouseOver");
this._doSub("/out","onMouseOut");
this._connectCommon();
},_doSub:function(_21,_22){
this._subs.push(dojo.subscribe(this.id+_21,this,_22));
},_connectInput:function(){
this._disconnect();
this._cons.push(dojo.connect(this._fileInput,"mouseover",this,function(evt){
this.onMouseOver(evt);
}));
this._cons.push(dojo.connect(this._fileInput,"mouseout",this,function(evt){
this.onMouseOut(evt);
this._checkHtmlCancel("off");
}));
this._cons.push(dojo.connect(this._fileInput,"mousedown",this,function(evt){
this.onMouseDown(evt);
}));
this._cons.push(dojo.connect(this._fileInput,"mouseup",this,function(evt){
this.onMouseUp(evt);
this._checkHtmlCancel("up");
}));
this._cons.push(dojo.connect(this._fileInput,"change",this,function(){

this._checkHtmlCancel("change");
this._change([{name:this._fileInput.value,type:"",size:0}]);
}));
this._connectCommon();
},_connectCommon:function(){
this._cons.push(dojo.connect(window,"resize",this,"setPosition"));
if(this.button.domNode){
this._cons.push(dojo.connect(this.button,"onClick",this,"setPosition"));
}else{
this._cons.push(dojo.connect(this.button,"click",this,"setPosition"));
}
var _27=this._dialogParent();
if(_27){
this._cons.push(dojo.connect(_27,"show",this,function(){
this.show();
this.setPosition();
}));
this._cons.push(dojo.connect(_27,"hide",this,"hide"),dojo.connect(_27,"destroy",this,"destroy"));
this._subs.push(dojo.subscribe("/dnd/move/stop",this,"setPosition"));
}
if(this.button.domNode){
this._cons.push(dojo.connect(this.button,"_setDisabledAttr",this,"disable"));
}
setTimeout(dojo.hitch(this,"setPosition"),500);
},_checkHtmlCancel:function(_28){
if(_28=="change"){
this.dialogIsOpen=false;
}
if(_28=="up"){
this.dialogIsOpen=true;
}
if(_28=="off"){
this.dialogIsOpen=false;
this.onCancel();
}
},_error:function(evt){
this.onError(evt);
},_change:function(_2a){
this.fileList=this.fileList.concat(_2a);
this.onChange(_2a);
if(this.uploadOnChange){
this.upload();
}
},_complete:function(_2b){
this.log("_complete",_2b);
for(var i=0;i<this.fileList.length;i++){
this.fileList[i].percent=100;
}
this._progress(this.fileList);
this.fileList=[];
this.onComplete(_2b);
},_progress:function(_2d){
this.log("_progress",_2d);
for(var i=0;i<this.fileList.length;i++){
var f=this.fileList[i];
if(f.name==_2d.name){
f.bytesLoaded=_2d.bytesLoaded;
f.bytesTotal=_2d.bytesTotal;
f.percent=Math.ceil(f.bytesLoaded/f.bytesTotal*100);
}else{
if(!f.percent){
f.bytesLoaded=0;
f.bytesTotal=0;
f.percent=0;
}
}
}
this.onProgress(this.fileList);
},_dialogParent:function(){
var _30;
var _31=this.button.domNode||this.button;
for(var i=0;i<50;i++){
if(_31.tagName.toLowerCase()=="body"){
_31=null;
break;
}
if(_31.tagName&&_31.tagName.toLowerCase()=="div"&&(dojo.attr(_31,"widgetId")||dojo.attr(_31,"widgetid"))){
_30=dijit.byNode(_31);
if(_30.titleBar&&_30.titleNode){
break;
}else{
_30=null;
}
}
_31=_31.parentNode;
}
return _30;
},_disconnect:function(){
dojo.forEach(this._cons,function(c){
dojo.disconnect(c);
});
},_buildFileInput:function(_34){
if(this._fileInput){
this._disconnect();
dojo.style(this._fileInput,"display","none");
}
this._fileInput=document.createElement("input");
this.domNode=this._fileInput;
this._fileInput.setAttribute("type","file");
this.fileInputs.push(this._fileInput);
var nm=this.htmlFieldName;
var _id=this.id;
if(this.selectMultipleFiles){
nm+=this.fileCount;
_id+=this.fileCount;
this.fileCount++;
}
this.log("NAME:",nm,this.htmlFieldName,this.fileCount);
this._fileInput.setAttribute("id",this.id);
this._fileInput.setAttribute("name",nm);
dojo.addClass(this._fileInput,"dijitFileInputReal");
if(this.devMode){
dojo.style(this._fileInput,"opacity",1);
}
this._formNode.appendChild(this._fileInput);
},_removeFileInput:function(){
dojo.forEach(this.fileInputs,function(inp){
inp.parentNode.removeChild(inp);
});
this.fileInputs=[];
this.fileCount=0;
},_buildForm:function(_38){
if(this._formNode){
return;
}
if(dojo.isIE){
this._formNode=document.createElement("<form enctype=\"multipart/form-data\" method=\"post\">");
this._formNode.encoding="multipart/form-data";
}else{
this._formNode=document.createElement("form");
this._formNode.setAttribute("enctype","multipart/form-data");
}
this._formNode.id=dijit.getUniqueId("form");
if(_38&&dojo.style(_38,"display").indexOf("inline")>-1){
document.body.appendChild(this._formNode);
}else{
_38.appendChild(this._formNode);
}
this._setHtmlPostData();
this._setFormStyle();
},_setHtmlPostData:function(){
if(this.postData){
for(var nm in this.postData){
var f=document.createElement("input");
dojo.attr(f,"type","hidden");
dojo.attr(f,"name",nm);
dojo.attr(f,"value",this.postData[nm]);
this._formNode.appendChild(f);
}
}
},_setFormStyle:function(){
var _3b=this.getFakeButtonSize();
var _3c=Math.max(2,Math.max(Math.ceil(_3b.w/60),Math.ceil(_3b.h/15)));
dojox.html.insertCssRule("#"+this._formNode.id+" input","font-size:"+_3c+"em");
},getFakeButtonSize:function(){
var _3d=(this.button.domNode)?dojo.byId(this.button.id).parentNode:dojo.byId(this.button.id)||this.button;
if(_3d.tagName.toLowerCase()=="span"){
_3d=dojo.byId(this.button.id);
}
var _3e=dojo.coords(_3d,true);
_3e.w=(dojo.style(_3d,"display")=="block")?dojo.style(_3d,"width"):_3e.w;
var p=_3d.parentNode.parentNode;
if(p&&dojo.style(p,"position")=="relative"){
_3e.x=dojo.style(p,"left");
_3e.y=dojo.style(p,"top");
}
if(p&&dojo.style(p,"position")=="absolute"){
_3e.x=0;
_3e.y=0;
}
var s=3;
_3e.x-=s;
_3e.y-=s;
_3e.w+=s*2;
_3e.h+=s*2;
return _3e;
}});
}
