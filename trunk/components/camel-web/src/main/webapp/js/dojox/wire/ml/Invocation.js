/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.wire.ml.Invocation"]){
dojo._hasResource["dojox.wire.ml.Invocation"]=true;
dojo.provide("dojox.wire.ml.Invocation");
dojo.require("dojox.wire.ml.Action");
dojo.declare("dojox.wire.ml.Invocation",dojox.wire.ml.Action,{object:"",method:"",topic:"",parameters:"",result:"",error:"",_run:function(){
if(this.topic){
var _1=this._getParameters(arguments);
try{
dojo.publish(this.topic,_1);
this.onComplete();
}
catch(e){
this.onError(e);
}
}else{
if(this.method){
var _2=(this.object?dojox.wire.ml._getValue(this.object):dojo.global);
if(!_2){
return;
}
var _1=this._getParameters(arguments);
var _3=_2[this.method];
if(!_3){
_3=_2.callMethod;
if(!_3){
return;
}
_1=[this.method,_1];
}
try{
var _4=false;
if(_2.getFeatures){
var _5=_2.getFeatures();
if((this.method=="fetch"&&_5["dojo.data.api.Read"])||(this.method=="save"&&_5["dojo.data.api.Write"])){
var _6=_1[0];
if(!_6.onComplete){
_6.onComplete=function(){
};
}
this.connect(_6,"onComplete","onComplete");
if(!_6.onError){
_6.onError=function(){
};
}
this.connect(_6,"onError","onError");
_4=true;
}
}
var r=_3.apply(_2,_1);
if(!_4){
if(r&&(r instanceof dojo.Deferred)){
var _8=this;
r.addCallbacks(function(_9){
_8.onComplete(_9);
},function(_a){
_8.onError(_a);
});
}else{
this.onComplete(r);
}
}
}
catch(e){
this.onError(e);
}
}
}
},onComplete:function(_b){
if(this.result){
dojox.wire.ml._setValue(this.result,_b);
}
if(this.error){
dojox.wire.ml._setValue(this.error,"");
}
},onError:function(_c){
if(this.error){
if(_c&&_c.message){
_c=_c.message;
}
dojox.wire.ml._setValue(this.error,_c);
}
},_getParameters:function(_d){
if(!this.parameters){
return _d;
}
var _e=[];
var _f=this.parameters.split(",");
if(_f.length==1){
var _10=dojox.wire.ml._getValue(dojo.trim(_f[0]),_d);
if(dojo.isArray(_10)){
_e=_10;
}else{
_e.push(_10);
}
}else{
for(var i in _f){
_e.push(dojox.wire.ml._getValue(dojo.trim(_f[i]),_d));
}
}
return _e;
}});
}
