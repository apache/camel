/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.io.OAuth"]){
dojo._hasResource["dojox.io.OAuth"]=true;
dojo.provide("dojox.io.OAuth");
dojo.require("dojox.encoding.digests.SHA1");
dojox.io.OAuth=new (function(){
var _1=this.encode=function(s){
if(!s){
return "";
}
return encodeURIComponent(s).replace(/\!/g,"%21").replace(/\*/g,"%2A").replace(/\'/g,"%27").replace(/\(/g,"%28").replace(/\)/g,"%29");
};
var _3=this.decode=function(_4){
var a=[],_6=_4.split("&");
for(var i=0,l=_6.length;i<l;i++){
var _9=_6[i];
if(_6[i]==""){
continue;
}
if(_6[i].indexOf("=")>-1){
var _a=_6[i].split("=");
a.push([decodeURIComponent(_a[0]),decodeURIComponent(_a[1])]);
}else{
a.push([decodeURIComponent(_6[i]),null]);
}
}
return a;
};
function _b(_c){
var _d=["source","protocol","authority","userInfo","user","password","host","port","relative","path","directory","file","query","anchor"],_e=/^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,_f=_e.exec(_c),map={},i=_d.length;
while(i--){
map[_d[i]]=_f[i]||"";
}
var p=map.protocol.toLowerCase(),a=map.authority.toLowerCase(),b=(p=="http"&&map.port==80)||(p=="https"&&map.port==443);
if(b){
if(a.lastIndexOf(":")>-1){
a=a.substring(0,a.lastIndexOf(":"));
}
}
var _15=map.path||"/";
map.url=p+"://"+a+_15;
return map;
};
var tab="0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
function _17(_18){
var s="",tl=tab.length;
for(var i=0;i<_18;i++){
s+=tab.charAt(Math.floor(Math.random()*tl));
}
return s;
};
function _1c(){
return Math.floor(new Date().valueOf()/1000)-2;
};
function _1d(_1e,key,_20){
if(_20&&_20!="PLAINTEXT"&&_20!="HMAC-SHA1"){
throw new Error("dojox.io.OAuth: the only supported signature encodings are PLAINTEXT and HMAC-SHA1.");
}
if(_20=="PLAINTEXT"){
return key;
}else{
return dojox.encoding.digests.SHA1._hmac(_1e,key);
}
};
function key(_22){
return _1(_22.consumer.secret)+"&"+(_22.token&&_22.token.secret?_1(_22.token.secret):"");
};
function _23(_24,oaa){
var o={oauth_consumer_key:oaa.consumer.key,oauth_nonce:_17(16),oauth_signature_method:oaa.sig_method||"HMAC-SHA1",oauth_timestamp:_1c(),oauth_version:"1.0"};
if(oaa.token){
o.oauth_token=oaa.token.key;
}
_24.content=dojo.mixin(_24.content||{},o);
};
function _27(_28){
var _29=[{}],_2a;
if(_28.form){
if(!_28.content){
_28.content={};
}
var _2b=dojo.byId(_28.form);
var _2c=_2b.getAttributeNode("action");
_28.url=_28.url||(_2c?_2c.value:null);
_2a=dojo.formToObject(_2b);
delete _28.form;
}
if(_2a){
_29.push(_2a);
}
if(_28.content){
_29.push(_28.content);
}
var map=_b(_28.url);
if(map.query){
var tmp=dojo.queryToObject(map.query);
for(var p in tmp){
tmp[p]=encodeURIComponent(tmp[p]);
}
_29.push(tmp);
}
_28._url=map.url;
var a=[];
for(var i=0,l=_29.length;i<l;i++){
var _33=_29[i];
for(var p in _33){
if(dojo.isArray(_33[p])){
for(var j=0,jl=_33.length;j<jl;j++){
a.push([p,_33[j]]);
}
}else{
a.push([p,_33[p]]);
}
}
}
_28._parameters=a;
return _28;
};
function _36(_37,_38,oaa){
_23(_38,oaa);
_27(_38);
var a=_38._parameters;
a.sort(function(a,b){
if(a[0]>b[0]){
return 1;
}
if(a[0]<b[0]){
return -1;
}
if(a[1]>b[1]){
return 1;
}
if(a[1]<b[1]){
return -1;
}
return 0;
});
var s=dojo.map(a,function(_3e){
return _1(_3e[0])+"%3D"+_1(_3e[1]||"");
}).join("%26");
var _3f=_37.toUpperCase()+"&"+_1(_38._url)+"&"+s;
return _3f;
};
function _40(_41,_42,oaa){
var k=key(oaa),_45=_36(_41,_42,oaa),s=_1d(_45,k,oaa.sig_method||"HMAC-SHA1");
_42.content["oauth_signature"]=s;
return _42;
};
this.sign=function(_47,_48,oaa){
return _40(_47,_48,oaa);
};
this.xhr=function(_4a,_4b,oaa,_4d){
_40(_4a,_4b,oaa);
return dojo.xhr(_4a,_4b,_4d);
};
this.xhrGet=function(_4e,oaa){
return this.xhr("GET",_4e,oaa);
};
this.xhrPost=this.xhrRawPost=function(_50,oaa){
return this.xhr("POST",_50,oaa,true);
};
this.xhrPut=this.xhrRawPut=function(_52,oaa){
return this.xhr("PUT",_52,oaa,true);
};
this.xhrDelete=function(_54,oaa){
return this.xhr("DELETE",_54,oaa);
};
})();
}
