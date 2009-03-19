/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.rpc.Service"]){
dojo._hasResource["dojox.rpc.Service"]=true;
dojo.provide("dojox.rpc.Service");
dojo.require("dojo.AdapterRegistry");
dojo.declare("dojox.rpc.Service",null,{constructor:function(_1,_2){
var _3;
var _4=this;
function _5(_6){
_6._baseUrl=new dojo._Url(location.href,_3||".")+"";
_4._smd=_6;
for(var _7 in _4._smd.services){
var _8=_7.split(".");
var _9=_4;
for(var i=0;i<_8.length-1;i++){
_9=_9[_8[i]]||(_9[_8[i]]={});
}
_9[_8[_8.length-1]]=_4._generateService(_7,_4._smd.services[_7]);
}
};
if(_1){
if((dojo.isString(_1))||(_1 instanceof dojo._Url)){
if(_1 instanceof dojo._Url){
_3=_1+"";
}else{
_3=_1;
}
var _b=dojo._getText(_3);
if(!_b){
throw new Error("Unable to load SMD from "+_1);
}else{
_5(dojo.fromJson(_b));
}
}else{
_5(_1);
}
}
this._options=(_2?_2:{});
this._requestId=0;
},_generateService:function(_c,_d){
if(this[_d]){
throw new Error("WARNING: "+_c+" already exists for service. Unable to generate function");
}
_d.name=_c;
var _e=dojo.hitch(this,"_executeMethod",_d);
var _f=dojox.rpc.transportRegistry.match(_d.transport||this._smd.transport);
if(_f.getExecutor){
_e=_f.getExecutor(_e,_d,this);
}
var _10=_d.returns||(_d._schema={});
var _11="/"+_c+"/";
_10._service=_e;
_e.servicePath=_11;
_e._schema=_10;
_e.id=dojox.rpc.Service._nextId++;
return _e;
},_getRequest:function(_12,_13){
var smd=this._smd;
var _15=dojox.rpc.envelopeRegistry.match(_12.envelope||smd.envelope||"NONE");
if(_15.namedParams){
if((_13.length==1)&&dojo.isObject(_13[0])){
_13=_13[0];
}else{
var _16={};
for(var i=0;i<_12.parameters.length;i++){
if(typeof _13[i]!="undefined"||!_12.parameters[i].optional){
_16[_12.parameters[i].name]=_13[i];
}
}
_13=_16;
}
var _18=(_12.parameters||[]).concat(smd.parameters||[]);
if(_12.strictParameters||smd.strictParameters){
for(i in _13){
var _19=false;
for(var j=0;j<_18.length;j++){
if(_18[i].name==i){
_19=true;
}
}
if(!_19){
delete _13[i];
}
}
}
for(i=0;i<_18.length;i++){
var _1b=_18[i];
if(!_1b.optional&&_1b.name&&!_13[_1b.name]){
if(_1b["default"]){
_13[_1b.name]=_1b["default"];
}else{
if(!(_1b.name in _13)){
throw new Error("Required parameter "+_1b.name+" was omitted");
}
}
}
}
}else{
if(_12.parameters&&_12.parameters[0]&&_12.parameters[0].name&&(_13.length==1)&&dojo.isObject(_13[0])){
if(_15.namedParams===false){
_13=dojox.rpc.toOrdered(_12,_13);
}else{
_13=_13[0];
}
}
}
if(dojo.isObject(this._options)){
_13=dojo.mixin(_13,this._options);
}
var _1c=_12._schema||_12.returns;
var _1d=_15.serialize.apply(this,[smd,_12,_13]);
_1d._envDef=_15;
var _1e=(_12.contentType||smd.contentType||_1d.contentType);
return dojo.mixin(_1d,{sync:dojox.rpc._sync,contentType:_1e,headers:{},target:_1d.target||dojox.rpc.getTarget(smd,_12),transport:_12.transport||smd.transport||_1d.transport,envelope:_12.envelope||smd.envelope||_1d.envelope,timeout:_12.timeout||smd.timeout,callbackParamName:_12.callbackParamName||smd.callbackParamName,schema:_1c,handleAs:_1d.handleAs||"auto",preventCache:_12.preventCache||smd.preventCache,frameDoc:this._options.frameDoc||undefined});
},_executeMethod:function(_1f){
var _20=[];
var i;
for(i=1;i<arguments.length;i++){
_20.push(arguments[i]);
}
var _22=this._getRequest(_1f,_20);
var _23=dojox.rpc.transportRegistry.match(_22.transport).fire(_22);
_23.addBoth(function(_24){
return _22._envDef.deserialize.call(this,_24);
});
return _23;
}});
dojox.rpc.getTarget=function(smd,_26){
var _27=smd._baseUrl;
if(smd.target){
_27=new dojo._Url(_27,smd.target)+"";
}
if(_26.target){
_27=new dojo._Url(_27,_26.target)+"";
}
return _27;
};
dojox.rpc.toOrdered=function(_28,_29){
if(dojo.isArray(_29)){
return _29;
}
var _2a=[];
for(var i=0;i<_28.parameters.length;i++){
_2a.push(_29[_28.parameters[i].name]);
}
return _2a;
};
dojox.rpc.transportRegistry=new dojo.AdapterRegistry(true);
dojox.rpc.envelopeRegistry=new dojo.AdapterRegistry(true);
dojox.rpc.envelopeRegistry.register("URL",function(str){
return str=="URL";
},{serialize:function(smd,_2e,_2f){
var d=dojo.objectToQuery(_2f);
return {data:d,transport:"POST"};
},deserialize:function(_31){
return _31;
},namedParams:true});
dojox.rpc.envelopeRegistry.register("JSON",function(str){
return str=="JSON";
},{serialize:function(smd,_34,_35){
var d=dojo.toJson(_35);
return {data:d,handleAs:"json",contentType:"application/json"};
},deserialize:function(_37){
return _37;
}});
dojox.rpc.envelopeRegistry.register("PATH",function(str){
return str=="PATH";
},{serialize:function(smd,_3a,_3b){
var i;
var _3d=dojox.rpc.getTarget(smd,_3a);
if(dojo.isArray(_3b)){
for(i=0;i<_3b.length;i++){
_3d+="/"+_3b[i];
}
}else{
for(i in _3b){
_3d+="/"+i+"/"+_3b[i];
}
}
return {data:"",target:_3d};
},deserialize:function(_3e){
return _3e;
}});
dojox.rpc.transportRegistry.register("POST",function(str){
return str=="POST";
},{fire:function(r){
r.url=r.target;
r.postData=r.data;
return dojo.rawXhrPost(r);
}});
dojox.rpc.transportRegistry.register("GET",function(str){
return str=="GET";
},{fire:function(r){
r.url=r.target+(r.data?"?"+r.data:"");
return dojo.xhrGet(r);
}});
dojox.rpc.transportRegistry.register("JSONP",function(str){
return str=="JSONP";
},{fire:function(r){
r.url=r.target+((r.target.indexOf("?")==-1)?"?":"&")+r.data;
r.callbackParamName=r.callbackParamName||"callback";
return dojo.io.script.get(r);
}});
dojox.rpc.Service._nextId=1;
dojo._contentHandlers.auto=function(xhr){
var _46=dojo._contentHandlers;
var _47=xhr.getResponseHeader("Content-Type");
var _48=!_47?_46.text(xhr):_47.match(/\/.*json/)?_46.json(xhr):_47.match(/\/javascript/)?_46.javascript(xhr):_47.match(/\/xml/)?_46.xml(xhr):_46.text(xhr);
return _48;
};
}
