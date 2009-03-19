/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.editor.plugins.UploadImage"]){
dojo._hasResource["dojox.editor.plugins.UploadImage"]=true;
dojo.provide("dojox.editor.plugins.UploadImage");
dojo.require("dijit._editor._Plugin");
dojo.require("dojox.form.FileUploader");
dojo.experimental("dojox.editor.plugins.UploadImage");
dojo.declare("dojox.editor.plugins.UploadImage",dijit._editor._Plugin,{tempImageUrl:"",iconClassPrefix:"editorIcon",useDefaultCommand:false,uploadUrl:"",fileInput:null,label:"Mike",_initButton:function(){
this.command="uploadImage";
this.editor.commands[this.command]="Upload Image";
this.inherited("_initButton",arguments);
delete this.command;
setTimeout(dojo.hitch(this,"createFileInput"),200);
},createFileInput:function(){
var _1=[["Jpeg File","*.jpg;*.jpeg"],["GIF File","*.gif"],["PNG File","*.png"],["All Images","*.jpg;*.jpeg;*.gif;*.png"]];
console.warn("downloadPath:",this.downloadPath);
this.fileInput=new dojox.form.FileUploader({isDebug:true,button:this.button,uploadUrl:this.uploadUrl,uploadOnChange:true,selectMultipleFiles:false,fileMask:_1});
dojo.connect(this.fileInput,"onChange",this,"insertTempImage");
dojo.connect(this.fileInput,"onComplete",this,"onComplete");
},onComplete:function(_2,_3,_4){
_2=_2[0];
var _5=dojo.withGlobal(this.editor.window,"byId",dojo,[this.currentImageId]);
var _6;
if(this.downloadPath){
_6=this.downloadPath+_2.name;
}else{
_6=_2.file;
}
_5.src=_6;
if(_2.width){
_5.width=_2.width;
_5.height=_2.height;
}
},insertTempImage:function(){
this.currentImageId="img_"+(new Date().getTime());
var _7="<img id=\""+this.currentImageId+"\" src=\""+this.tempImageUrl+"\" width=\"32\" height=\"32\"/>";
this.editor.execCommand("inserthtml",_7);
}});
dojo.subscribe(dijit._scopeName+".Editor.getPlugin",null,function(o){
if(o.plugin){
return;
}
switch(o.args.name){
case "uploadImage":
o.plugin=new dojox.editor.plugins.UploadImage({url:o.args.url});
}
});
}
