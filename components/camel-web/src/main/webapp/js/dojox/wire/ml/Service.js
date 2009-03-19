/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.ml.Service"]){
dojo._hasResource["dojox.wire.ml.Service"]=true;
dojo.provide("dojox.wire.ml.Service");
dojo.provide("dojox.wire.ml.RestHandler");
dojo.provide("dojox.wire.ml.XmlHandler");
dojo.provide("dojox.wire.ml.JsonHandler");
dojo.require("dijit._Widget");
dojo.require("dojox.xml.parser");
dojo.require("dojox.wire._base");
dojo.require("dojox.wire.ml.util");
dojo.declare("dojox.wire.ml.Service",dijit._Widget,{url:"",serviceUrl:"",serviceType:"",handlerClass:"",preventCache:true,postCreate:function(){
this.handler=this._createHandler();
},_handlerClasses:{"TEXT":"dojox.wire.ml.RestHandler","XML":"dojox.wire.ml.XmlHandler","JSON":"dojox.wire.ml.JsonHandler","JSON-RPC":"dojo.rpc.JsonService"},_createHandler:function(){
if(this.url){
var _1=this;
var d=dojo.xhrGet({url:this.url,handleAs:"json",sync:true});
d.addCallback(function(_3){
_1.smd=_3;
});
if(this.smd&&!this.serviceUrl){
this.serviceUrl=(this.smd.serviceUrl||this.smd.serviceURL);
}
}
var _4=undefined;
if(this.handlerClass){
_4=dojox.wire._getClass(this.handlerClass);
}else{
if(this.serviceType){
_4=this._handlerClasses[this.serviceType];
if(_4&&dojo.isString(_4)){
_4=dojox.wire._getClass(_4);
this._handlerClasses[this.serviceType]=_4;
}
}else{
if(this.smd&&this.smd.serviceType){
_4=this._handlerClasses[this.smd.serviceType];
if(_4&&dojo.isString(_4)){
_4=dojox.wire._getClass(_4);
this._handlerClasses[this.smd.serviceType]=_4;
}
}
}
}
if(!_4){
return null;
}
return new _4();
},callMethod:function(_5,_6){
var _7=new dojo.Deferred();
this.handler.bind(_5,_6,_7,this.serviceUrl);
return _7;
}});
dojo.declare("dojox.wire.ml.RestHandler",null,{contentType:"text/plain",handleAs:"text",bind:function(_8,_9,_a,_b){
_8=_8.toUpperCase();
var _c=this;
var _d={url:this._getUrl(_8,_9,_b),contentType:this.contentType,handleAs:this.handleAs,headers:this.headers,preventCache:this.preventCache};
var d=null;
if(_8=="POST"){
_d.postData=this._getContent(_8,_9);
d=dojo.rawXhrPost(_d);
}else{
if(_8=="PUT"){
_d.putData=this._getContent(_8,_9);
d=dojo.rawXhrPut(_d);
}else{
if(_8=="DELETE"){
d=dojo.xhrDelete(_d);
}else{
d=dojo.xhrGet(_d);
}
}
}
d.addCallbacks(function(_f){
_a.callback(_c._getResult(_f));
},function(_10){
_a.errback(_10);
});
},_getUrl:function(_11,_12,url){
var _14;
if(_11=="GET"||_11=="DELETE"){
if(_12.length>0){
_14=_12[0];
}
}else{
if(_12.length>1){
_14=_12[1];
}
}
if(_14){
var _15="";
for(var _16 in _14){
var _17=_14[_16];
if(_17){
_17=encodeURIComponent(_17);
var _18="{"+_16+"}";
var _19=url.indexOf(_18);
if(_19>=0){
url=url.substring(0,_19)+_17+url.substring(_19+_18.length);
}else{
if(_15){
_15+="&";
}
_15+=(_16+"="+_17);
}
}
}
if(_15){
url+="?"+_15;
}
}
return url;
},_getContent:function(_1a,_1b){
if(_1a=="POST"||_1a=="PUT"){
return (_1b?_1b[0]:null);
}else{
return null;
}
},_getResult:function(_1c){
return _1c;
}});
dojo.declare("dojox.wire.ml.XmlHandler",dojox.wire.ml.RestHandler,{contentType:"text/xml",handleAs:"xml",_getContent:function(_1d,_1e){
var _1f=null;
if(_1d=="POST"||_1d=="PUT"){
var p=_1e[0];
if(p){
if(dojo.isString(p)){
_1f=p;
}else{
var _21=p;
if(_21 instanceof dojox.wire.ml.XmlElement){
_21=_21.element;
}else{
if(_21.nodeType===9){
_21=_21.documentElement;
}
}
var _22="<?xml version=\"1.0\"?>";
_1f=_22+dojox.xml.parser.innerXML(_21);
}
}
}
return _1f;
},_getResult:function(_23){
if(_23){
_23=new dojox.wire.ml.XmlElement(_23);
}
return _23;
}});
dojo.declare("dojox.wire.ml.JsonHandler",dojox.wire.ml.RestHandler,{contentType:"text/json",handleAs:"json",headers:{"Accept":"*/json"},_getContent:function(_24,_25){
var _26=null;
if(_24=="POST"||_24=="PUT"){
var p=(_25?_25[0]:undefined);
if(p){
if(dojo.isString(p)){
_26=p;
}else{
_26=dojo.toJson(p);
}
}
}
return _26;
}});
}
