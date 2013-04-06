/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.io.xhrMultiPart"]){
dojo._hasResource["dojox.io.xhrMultiPart"]=true;
dojo.provide("dojox.io.xhrMultiPart");
dojo.require("dojox.uuid.generateRandomUuid");
(function(){
function _1(_2,_3){
if(!_2["name"]&&!_2["content"]){
throw new Error("Each part of a multi-part request requires 'name' and 'content'.");
}
var _4=[];
_4.push("--"+_3,"Content-Disposition: form-data; name=\""+_2.name+"\""+(_2["filename"]?"; filename=\""+_2.filename+"\"":""));
if(_2["contentType"]){
var ct="Content-Type: "+_2.contentType;
if(_2["charset"]){
ct+="; Charset="+_2.charset;
}
_4.push(ct);
}
if(_2["contentTransferEncoding"]){
_4.push("Content-Transfer-Encoding: "+_2.contentTransferEncoding);
}
_4.push("",_2.content);
return _4;
};
function _6(_7,_8){
var o=dojo.formToObject(_7),_a=[];
for(var p in o){
if(dojo.isArray(o[p])){
dojo.forEach(o[p],function(_c){
_a=_a.concat(_1({name:p,content:_c},_8));
});
}else{
_a=_a.concat(_1({name:p,content:o[p]},_8));
}
}
return _a;
};
dojox.io.xhrMultiPart=function(_d){
if(!_d["file"]&&!_d["content"]&&!_d["form"]){
throw new Error("content, file or form must be provided to dojox.io.xhrMultiPart's arguments");
}
var _e=dojox.uuid.generateRandomUuid(),_f=[],out="";
if(_d["file"]||_d["content"]){
var v=_d["file"]||_d["content"];
dojo.forEach((dojo.isArray(v)?v:[v]),function(_12){
_f=_f.concat(_1(_12,_e));
});
}else{
if(_d["form"]){
if(dojo.query("input[type=file]",_d["form"]).length){
throw new Error("dojox.io.xhrMultiPart cannot post files that are values of an INPUT TYPE=FILE.  Use dojo.io.iframe.send() instead.");
}
_f=_6(_d["form"],_e);
}
}
if(_f.length){
_f.push("--"+_e+"--","");
out=_f.join("\r\n");
}

return dojo.rawXhrPost(dojo.mixin(_d,{contentType:"multipart/form-data; boundary="+_e,postData:out}));
};
})();
}
