/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.io.scriptFrame"]){
dojo._hasResource["dojox.io.scriptFrame"]=true;
dojo.provide("dojox.io.scriptFrame");
dojo.require("dojo.io.script");
dojo.require("dojo.io.iframe");
(function(){
var _1=dojo.io.script;
dojox.io.scriptFrame={_waiters:{},_loadedIds:{},_getWaiters:function(_2){
return this._waiters[_2]||(this._waiters[_2]=[]);
},_fixAttachUrl:function(_3){
},_loaded:function(_4){
var _5=this._getWaiters(_4);
this._loadedIds[_4]=true;
this._waiters[_4]=null;
for(var i=0;i<_5.length;i++){
var _7=_5[i];
_7.frameDoc=dojo.io.iframe.doc(dojo.byId(_4));
_1.attach(_7.id,_7.url,_7.frameDoc);
}
}};
var _8=_1._canAttach;
var _9=dojox.io.scriptFrame;
_1._canAttach=function(_a){
var _b=_a.args.frameDoc;
if(_b&&dojo.isString(_b)){
var _c=dojo.byId(_b);
var _d=_9._getWaiters(_b);
if(!_c){
_d.push(_a);
dojo.io.iframe.create(_b,dojox._scopeName+".io.scriptFrame._loaded('"+_b+"');");
}else{
if(_9._loadedIds[_b]){
_a.frameDoc=dojo.io.iframe.doc(_c);
this.attach(_a.id,_a.url,_a.frameDoc);
}else{
_d.push(_a);
}
}
return false;
}else{
return _8.apply(this,arguments);
}
};
})();
}
