/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.SnapLogicStore"]){
dojo._hasResource["dojox.data.SnapLogicStore"]=true;
dojo.provide("dojox.data.SnapLogicStore");
dojo.require("dojo.io.script");
dojo.require("dojo.data.util.sorter");
dojo.declare("dojox.data.SnapLogicStore",null,{Parts:{DATA:"data",COUNT:"count"},url:"",constructor:function(_1){
if(_1.url){
this.url=_1.url;
}
this._parameters=_1.parameters;
},_assertIsItem:function(_2){
if(!this.isItem(_2)){
throw new Error("dojox.data.SnapLogicStore: a function was passed an item argument that was not an item");
}
},_assertIsAttribute:function(_3){
if(typeof _3!=="string"){
throw new Error("dojox.data.SnapLogicStore: a function was passed an attribute argument that was not an attribute name string");
}
},getFeatures:function(){
return {"dojo.data.api.Read":true};
},getValue:function(_4,_5,_6){
this._assertIsItem(_4);
this._assertIsAttribute(_5);
var i=dojo.indexOf(_4.attributes,_5);
if(i!==-1){
return _4.values[i];
}
return _6;
},getAttributes:function(_8){
this._assertIsItem(_8);
return _8.attributes;
},hasAttribute:function(_9,_a){
this._assertIsItem(_9);
this._assertIsAttribute(_a);
for(var i=0;i<_9.attributes.length;++i){
if(_a==_9.attributes[i]){
return true;
}
}
return false;
},isItemLoaded:function(_c){
return this.isItem(_c);
},loadItem:function(_d){
},getLabel:function(_e){
return undefined;
},getLabelAttributes:function(_f){
return null;
},containsValue:function(_10,_11,_12){
return this.getValue(_10,_11)===_12;
},getValues:function(_13,_14){
this._assertIsItem(_13);
this._assertIsAttribute(_14);
var i=dojo.indexOf(_13.attributes,_14);
if(i!==-1){
return [_13.values[i]];
}
return [];
},isItem:function(_16){
if(_16&&_16._store===this){
return true;
}
return false;
},close:function(_17){
},_fetchHandler:function(_18){
var _19=_18.scope||dojo.global;
if(_18.onBegin){
_18.onBegin.call(_19,_18._countResponse[0],_18);
}
if(_18.onItem||_18.onComplete){
var _1a=_18._dataResponse;
if(!_1a.length){
_18.onError.call(_19,new Error("dojox.data.SnapLogicStore: invalid response of length 0"),_18);
return;
}else{
if(_18.query!="record count"){
var _1b=_1a.shift();
var _1c=[];
for(var i=0;i<_1a.length;++i){
if(_18._aborted){
break;
}
_1c.push({attributes:_1b,values:_1a[i],_store:this});
}
if(_18.sort&&!_18._aborted){
_1c.sort(dojo.data.util.sorter.createSortFunction(_18.sort,self));
}
}else{
_1c=[({attributes:["count"],values:_1a,_store:this})];
}
}
if(_18.onItem){
for(var i=0;i<_1c.length;++i){
if(_18._aborted){
break;
}
_18.onItem.call(_19,_1c[i],_18);
}
_1c=null;
}
if(_18.onComplete&&!_18._aborted){
_18.onComplete.call(_19,_1c,_18);
}
}
},_partHandler:function(_1e,_1f,_20){
if(_20 instanceof Error){
if(_1f==this.Parts.DATA){
_1e._dataHandle=null;
}else{
_1e._countHandle=null;
}
_1e._aborted=true;
if(_1e.onError){
_1e.onError.call(_1e.scope,_20,_1e);
}
}else{
if(_1e._aborted){
return;
}
if(_1f==this.Parts.DATA){
_1e._dataResponse=_20;
}else{
_1e._countResponse=_20;
}
if((!_1e._dataHandle||_1e._dataResponse!==null)&&(!_1e._countHandle||_1e._countResponse!==null)){
this._fetchHandler(_1e);
}
}
},fetch:function(_21){
_21._countResponse=null;
_21._dataResponse=null;
_21._aborted=false;
_21.abort=function(){
if(!_21._aborted){
_21._aborted=true;
if(_21._dataHandle&&_21._dataHandle.cancel){
_21._dataHandle.cancel();
}
if(_21._countHandle&&_21._countHandle.cancel){
_21._countHandle.cancel();
}
}
};
if(_21.onItem||_21.onComplete){
var _22=this._parameters||{};
if(_21.start){
if(_21.start<0){
throw new Error("dojox.data.SnapLogicStore: request start value must be 0 or greater");
}
_22["sn.start"]=_21.start+1;
}
if(_21.count){
if(_21.count<0){
throw new Error("dojox.data.SnapLogicStore: request count value 0 or greater");
}
_22["sn.limit"]=_21.count;
}
_22["sn.content_type"]="application/javascript";
var _23=this;
var _24=function(_25,_26){
if(_25 instanceof Error){
_23._fetchHandler(_25,_21);
}
};
var _27={url:this.url,content:_22,timeout:60000,callbackParamName:"sn.stream_header",handle:dojo.hitch(this,"_partHandler",_21,this.Parts.DATA)};
_21._dataHandle=dojo.io.script.get(_27);
}
if(_21.onBegin){
var _22={};
_22["sn.count"]="records";
_22["sn.content_type"]="application/javascript";
var _27={url:this.url,content:_22,timeout:60000,callbackParamName:"sn.stream_header",handle:dojo.hitch(this,"_partHandler",_21,this.Parts.COUNT)};
_21._countHandle=dojo.io.script.get(_27);
}
return _21;
}});
}
