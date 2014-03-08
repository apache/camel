/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.rpc.JsonRest"]){
dojo._hasResource["dojox.rpc.JsonRest"]=true;
dojo.provide("dojox.rpc.JsonRest");
dojo.require("dojox.json.ref");
dojo.require("dojox.rpc.Rest");
(function(){
var _1=[];
var _2=dojox.rpc.Rest;
var jr;
function _4(_5,_6,_7,_8){
var _9=_6.ioArgs&&_6.ioArgs.xhr&&_6.ioArgs.xhr.getResponseHeader("Last-Modified");
if(_9&&_2._timeStamps){
_2._timeStamps[_8]=_9;
}
return _7&&dojox.json.ref.resolveJson(_7,{defaultId:_8,index:_2._index,timeStamps:_9&&_2._timeStamps,time:_9,idPrefix:_5.servicePath,idAttribute:jr.getIdAttribute(_5),schemas:jr.schemas,loader:jr._loader,assignAbsoluteIds:true});
};
jr=dojox.rpc.JsonRest={conflictDateHeader:"If-Unmodified-Since",commit:function(_a){
_a=_a||{};
var _b=[];
var _c={};
var _d=[];
for(var i=0;i<_1.length;i++){
var _f=_1[i];
var _10=_f.object;
var old=_f.old;
var _12=false;
if(!(_a.service&&(_10||old)&&(_10||old).__id.indexOf(_a.service.servicePath))&&_f.save){
delete _10.__isDirty;
if(_10){
if(old){
var _13;
if((_13=_10.__id.match(/(.*)#.*/))){
_10=_2._index[_13[1]];
}
if(!(_10.__id in _c)){
_c[_10.__id]=_10;
_b.push({method:"put",target:_10,content:_10});
}
}else{
_b.push({method:"post",target:{__id:jr.getServiceAndId(_10.__id).service.servicePath},content:_10});
}
}else{
if(old){
_b.push({method:"delete",target:old});
}
}
_d.push(_f);
_1.splice(i--,1);
}
}
dojo.connect(_a,"onError",function(){
var _14=_1;
_1=_d;
var _15=0;
jr.revert();
_1=_14;
});
jr.sendToServer(_b,_a);
return _b;
},sendToServer:function(_16,_17){
var _18;
var _19=dojo.xhr;
var _1a=_16.length;
var _1b;
var _1c;
var _1d=this.conflictDateHeader;
dojo.xhr=function(_1e,_1f){
_1f.headers=_1f.headers||{};
_1f.headers["Transaction"]=_16.length-1==i?"commit":"open";
if(_1d&&_1c){
_1f.headers[_1d]=_1c;
}
if(_1b){
_1f.headers["Content-ID"]="<"+_1b+">";
}
return _19.apply(dojo,arguments);
};
for(i=0;i<_16.length;i++){
var _20=_16[i];
dojox.rpc.JsonRest._contentId=_20.content&&_20.content.__id;
var _21=_20.method=="post";
_1c=_20.method=="put"&&_2._timeStamps[_20.content.__id];
if(_1c){
_2._timeStamps[_20.content.__id]=(new Date())+"";
}
_1b=_21&&dojox.rpc.JsonRest._contentId;
var _22=jr.getServiceAndId(_20.target.__id);
var _23=_22.service;
var dfd=_20.deferred=_23[_20.method](_22.id.replace(/#/,""),dojox.json.ref.toJson(_20.content,false,_23.servicePath,true));
(function(_25,dfd,_27){
dfd.addCallback(function(_28){
try{
var _29=dfd.ioArgs.xhr&&dfd.ioArgs.xhr.getResponseHeader("Location");
if(_29){
var _2a=_29.match(/(^\w+:\/\/)/)&&_29.indexOf(_27.servicePath);
_29=_2a>0?_29.substring(_2a):(_27.servicePath+_29).replace(/^(.*\/)?(\w+:\/\/)|[^\/\.]+\/\.\.\/|^.*\/(\/)/,"$2$3");
_25.__id=_29;
_2._index[_29]=_25;
}
_28=_4(_27,dfd,_28,_25&&_25.__id);
}
catch(e){
}
if(!(--_1a)){
if(_17.onComplete){
_17.onComplete.call(_17.scope);
}
}
return _28;
});
})(_20.content,dfd,_23);
dfd.addErrback(function(_2b){
_1a=-1;
_17.onError.call(_17.scope,_2b);
});
}
dojo.xhr=_19;
},getDirtyObjects:function(){
return _1;
},revert:function(_2c){
for(var i=_1.length;i>0;){
i--;
var _2e=_1[i];
var _2f=_2e.object;
var old=_2e.old;
if(!(_2c&&(_2f||old)&&(_2f||old).__id.indexOf(kwArgs.service.servicePath))){
if(_2f&&old){
for(var j in old){
if(old.hasOwnProperty(j)){
_2f[j]=old[j];
}
}
for(j in _2f){
if(!old.hasOwnProperty(j)){
delete _2f[j];
}
}
}
_1.splice(i,1);
}
}
},changing:function(_32,_33){
if(!_32.__id){
return;
}
_32.__isDirty=true;
for(var i=0;i<_1.length;i++){
var _35=_1[i];
if(_32==_35.object){
if(_33){
_35.object=false;
if(!this._saveNotNeeded){
_35.save=true;
}
}
return;
}
}
var old=_32 instanceof Array?[]:{};
for(i in _32){
if(_32.hasOwnProperty(i)){
old[i]=_32[i];
}
}
_1.push({object:!_33&&_32,old:old,save:!this._saveNotNeeded});
},deleteObject:function(_37){
this.changing(_37,true);
},getConstructor:function(_38,_39){
if(typeof _38=="string"){
var _3a=_38;
_38=new dojox.rpc.Rest(_38,true);
this.registerService(_38,_3a,_39);
}
if(_38._constructor){
return _38._constructor;
}
_38._constructor=function(_3b){
var _3c=this;
var _3d=arguments;
var _3e;
function _3f(_40){
if(_40){
_3f(_40["extends"]);
_3e=_40.properties;
for(var i in _3e){
var _42=_3e[i];
if(_42&&(typeof _42=="object")&&("default" in _42)){
_3c[i]=_42["default"];
}
}
}
if(_3b){
dojo.mixin(_3c,_3b);
}
if(_40&&_40.prototype&&_40.prototype.initialize){
_40.prototype.initialize.apply(_3c,_3d);
}
};
_3f(_38._schema);
var _43=jr.getIdAttribute(_38);
_2._index[this.__id=this.__clientId=_38.servicePath+(this[_43]||Math.random().toString(16).substring(2,14)+"@"+((dojox.rpc.Client&&dojox.rpc.Client.clientId)||"client"))]=this;
if(dojox.json.schema&&_3e){
dojox.json.schema.mustBeValid(dojox.json.schema.validate(this,_38._schema));
}
_1.push({object:this,save:true});
};
return dojo.mixin(_38._constructor,_38._schema,{load:_38});
},fetch:function(_44){
var _45=jr.getServiceAndId(_44);
return this.byId(_45.service,_45.id);
},getIdAttribute:function(_46){
var _47=_46._schema;
var _48;
if(_47){
if(!(_48=_47._idAttr)){
for(var i in _47.properties){
if(_47.properties[i].identity){
_47._idAttr=_48=i;
}
}
}
}
return _48||"id";
},getServiceAndId:function(_4a){
var _4b=_4a.match(/^(.*\/)([^\/]*)$/);
var svc=jr.services[_4b[1]]||new dojox.rpc.Rest(_4b[1],true);
return {service:svc,id:_4b[2]};
},services:{},schemas:{},registerService:function(_4d,_4e,_4f){
_4e=_4e||_4d.servicePath;
_4e=_4d.servicePath=_4e.match(/\/$/)?_4e:(_4e+"/");
_4d._schema=jr.schemas[_4e]=_4f||_4d._schema||{};
jr.services[_4e]=_4d;
},byId:function(_50,id){
var _52,_53=_2._index[(_50.servicePath||"")+id];
if(_53&&!_53._loadObject){
_52=new dojo.Deferred();
_52.callback(_53);
return _52;
}
return this.query(_50,id);
},query:function(_54,id,_56){
var _57=_54(id,_56);
_57.addCallback(function(_58){
if(_58.nodeType&&_58.cloneNode){
return _58;
}
return _4(_54,_57,_58,typeof id!="string"||(_56&&(_56.start||_56.count))?undefined:id);
});
return _57;
},_loader:function(_59){
var _5a=jr.getServiceAndId(this.__id);
var _5b=this;
jr.query(_5a.service,_5a.id).addBoth(function(_5c){
if(_5c==_5b){
delete _5c.$ref;
delete _5c._loadObject;
}else{
_5b._loadObject=function(_5d){
_5d(_5c);
};
}
_59(_5c);
});
},isDirty:function(_5e){
if(!_5e){
return !!_1.length;
}
return _5e.__isDirty;
}};
})();
}
