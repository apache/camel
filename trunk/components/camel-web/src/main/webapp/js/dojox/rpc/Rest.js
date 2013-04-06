/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.rpc.Rest"]){
dojo._hasResource["dojox.rpc.Rest"]=true;
dojo.provide("dojox.rpc.Rest");
(function(){
if(dojox.rpc&&dojox.rpc.transportRegistry){
dojox.rpc.transportRegistry.register("REST",function(_1){
return _1=="REST";
},{getExecutor:function(_2,_3,_4){
return new dojox.rpc.Rest(_3.name,(_3.contentType||_4._smd.contentType||"").match(/json|javascript/),null,function(id,_6){
var _7=_4._getRequest(_3,[id]);
_7.url=_7.target+(_7.data?"?"+_7.data:"");
return _7;
});
}});
}
var _8;
function _9(_a,_b,_c,id){
_a.addCallback(function(_e){
if(_c){
_c=_a.ioArgs.xhr&&_a.ioArgs.xhr.getResponseHeader("Content-Range");
_a.fullLength=_c&&(_c=_c.match(/\/(.*)/))&&parseInt(_c[1]);
}
return _e;
});
return _a;
};
_8=dojox.rpc.Rest=function(_f,_10,_11,_12){
var _13;
_f=_f.match(/\/$/)?_f:(_f+"/");
_13=function(id,_15){
return _8._get(_13,id,_15);
};
_13.isJson=_10;
_13._schema=_11;
_13.cache={serialize:_10?((dojox.json&&dojox.json.ref)||dojo).toJson:function(_16){
return _16;
}};
_13._getRequest=_12||function(id,_18){
var _19={url:_f+(dojo.isObject(id)?"?"+dojo.objectToQuery(id):id==null?"":id),handleAs:_10?"json":"text",contentType:_10?"application/json":"text/plain",sync:dojox.rpc._sync,headers:{Accept:_10?"application/json,application/javascript":"*/*"}};
if(_18&&(_18.start>=0||_18.count>=0)){
_19.headers.Range="items="+(_18.start||"0")+"-"+((_18.count&&_18.count!=Infinity&&(_18.count+(_18.start||0)-1))||"");
}
dojox.rpc._sync=false;
return _19;
};
function _1a(_1b){
_13[_1b]=function(id,_1d){
return _8._change(_1b,_13,id,_1d);
};
};
_1a("put");
_1a("post");
_1a("delete");
_13.servicePath=_f;
return _13;
};
_8._index={};
_8._timeStamps={};
_8._change=function(_1e,_1f,id,_21){
var _22=_1f._getRequest(id);
_22[_1e+"Data"]=_21;
return _9(dojo.xhr(_1e.toUpperCase(),_22,true),_1f);
};
_8._get=function(_23,id,_25){
_25=_25||{};
return _9(dojo.xhrGet(_23._getRequest(id,_25)),_23,(_25.start>=0||_25.count>=0),id);
};
})();
}
