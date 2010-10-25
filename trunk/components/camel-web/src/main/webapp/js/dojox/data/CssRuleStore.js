/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.CssRuleStore"]){
dojo._hasResource["dojox.data.CssRuleStore"]=true;
dojo.provide("dojox.data.CssRuleStore");
dojo.require("dojo.data.util.filter");
dojo.require("dojo.data.util.sorter");
dojo.require("dojox.data.css");
dojo.declare("dojox.data.CssRuleStore",null,{_storeRef:"_S",_labelAttribute:"selector",_cache:null,_browserMap:null,_cName:"dojox.data.CssRuleStore",constructor:function(_1){
if(_1){
dojo.mixin(this,_1);
}
this._cache={};
this._allItems=null;
this._waiting=[];
this.gatherHandle=null;
var _2=this;
function _3(){
try{
_2.context=dojox.data.css.determineContext(_2.context);
if(_2.gatherHandle){
clearInterval(_2.gatherHandle);
_2.gatherHandle=null;
}
while(_2._waiting.length){
var _4=_2._waiting.pop();
dojox.data.css.rules.forEach(_4.forFunc,null,_2.context);
_4.finishFunc();
}
}
catch(e){
}
};
this.gatherHandle=setInterval(_3,250);
},setContext:function(_5){
if(_5){
this.close();
this.context=dojox.data.css.determineContext(_5);
}
},getFeatures:function(){
return {"dojo.data.api.Read":true};
},isItem:function(_6){
if(_6&&_6[this._storeRef]==this){
return true;
}
return false;
},hasAttribute:function(_7,_8){
this._assertIsItem(_7);
this._assertIsAttribute(_8);
var _9=this.getAttributes(_7);
if(dojo.indexOf(_9,_8)!=-1){
return true;
}
return false;
},getAttributes:function(_a){
this._assertIsItem(_a);
var _b=["selector","classes","rule","style","cssText","styleSheet","parentStyleSheet","parentStyleSheetHref"];
var _c=_a.rule.style;
if(_c){
var _d;
for(_d in _c){
_b.push("style."+_d);
}
}
return _b;
},getValue:function(_e,_f,_10){
var _11=this.getValues(_e,_f);
var _12=_10;
if(_11&&_11.length>0){
return _11[0];
}
return _10;
},getValues:function(_13,_14){
this._assertIsItem(_13);
this._assertIsAttribute(_14);
var _15=null;
if(_14==="selector"){
_15=_13.rule["selectorText"];
if(_15&&dojo.isString(_15)){
_15=_15.split(",");
}
}else{
if(_14==="classes"){
_15=_13.classes;
}else{
if(_14==="rule"){
_15=_13.rule.rule;
}else{
if(_14==="style"){
_15=_13.rule.style;
}else{
if(_14==="cssText"){
if(dojo.isIE){
if(_13.rule.style){
_15=_13.rule.style.cssText;
if(_15){
_15="{ "+_15.toLowerCase()+" }";
}
}
}else{
_15=_13.rule.cssText;
if(_15){
_15=_15.substring(_15.indexOf("{"),_15.length);
}
}
}else{
if(_14==="styleSheet"){
_15=_13.rule.styleSheet;
}else{
if(_14==="parentStyleSheet"){
_15=_13.rule.parentStyleSheet;
}else{
if(_14==="parentStyleSheetHref"){
if(_13.href){
_15=_13.href;
}
}else{
if(_14.indexOf("style.")===0){
var _16=_14.substring(_14.indexOf("."),_14.length);
_15=_13.rule.style[_16];
}else{
_15=[];
}
}
}
}
}
}
}
}
}
if(_15!==undefined){
if(!dojo.isArray(_15)){
_15=[_15];
}
}
return _15;
},getLabel:function(_17){
this._assertIsItem(_17);
return this.getValue(_17,this._labelAttribute);
},getLabelAttributes:function(_18){
return [this._labelAttribute];
},containsValue:function(_19,_1a,_1b){
var _1c=undefined;
if(typeof _1b==="string"){
_1c=dojo.data.util.filter.patternToRegExp(_1b,false);
}
return this._containsValue(_19,_1a,_1b,_1c);
},isItemLoaded:function(_1d){
return this.isItem(_1d);
},loadItem:function(_1e){
this._assertIsItem(_1e.item);
},fetch:function(_1f){
_1f=_1f||{};
if(!_1f.store){
_1f.store=this;
}
var _20=_1f.scope||dojo.global;
if(this._pending&&this._pending.length>0){
this._pending.push({request:_1f,fetch:true});
}else{
this._pending=[{request:_1f,fetch:true}];
this._fetch(_1f);
}
return _1f;
},_fetch:function(_21){
var _22=_21.scope||dojo.global;
if(this._allItems===null){
this._allItems={};
try{
if(this.gatherHandle){
this._waiting.push({"forFunc":dojo.hitch(this,this._handleRule),"finishFunc":dojo.hitch(this,this._handleReturn)});
}else{
dojox.data.css.rules.forEach(dojo.hitch(this,this._handleRule),null,this.context);
this._handleReturn();
}
}
catch(e){
if(_21.onError){
_21.onError.call(_22,e,_21);
}
}
}else{
this._handleReturn();
}
},_handleRule:function(_23,_24,_25){
var _26=_23["selectorText"];
var s=_26.split(" ");
var _28=[];
for(var j=0;j<s.length;j++){
var tmp=s[j];
var _2b=tmp.indexOf(".");
if(tmp&&tmp.length>0&&_2b!==-1){
var _2c=tmp.indexOf(",")||tmp.indexOf("[");
tmp=tmp.substring(_2b,((_2c!==-1&&_2c>_2b)?_2c:tmp.length));
_28.push(tmp);
}
}
var _2d={};
_2d.rule=_23;
_2d.styleSheet=_24;
_2d.href=_25;
_2d.classes=_28;
_2d[this._storeRef]=this;
if(!this._allItems[_26]){
this._allItems[_26]=[];
}
this._allItems[_26].push(_2d);
},_handleReturn:function(){
var _2e=[];
var _2f=[];
var _30=null;
for(var i in this._allItems){
_30=this._allItems[i];
for(var j in _30){
_2f.push(_30[j]);
}
}
var _33;
while(this._pending.length){
_33=this._pending.pop();
_33.request._items=_2f;
_2e.push(_33);
}
while(_2e.length){
_33=_2e.pop();
this._handleFetchReturn(_33.request);
}
},_handleFetchReturn:function(_34){
var _35=_34.scope||dojo.global;
var _36=[];
var _37="all";
var i;
if(_34.query){
_37=dojo.toJson(_34.query);
}
if(this._cache[_37]){
_36=this._cache[_37];
}else{
if(_34.query){
for(i in _34._items){
var _39=_34._items[i];
var _3a=dojo.isWebKit?true:(_34.queryOptions?_34.queryOptions.ignoreCase:false);
var _3b={};
var key;
var _3d;
for(key in _34.query){
_3d=_34.query[key];
if(typeof _3d==="string"){
_3b[key]=dojo.data.util.filter.patternToRegExp(_3d,_3a);
}
}
var _3e=true;
for(key in _34.query){
_3d=_34.query[key];
if(!this._containsValue(_39,key,_3d,_3b[key])){
_3e=false;
}
}
if(_3e){
_36.push(_39);
}
}
this._cache[_37]=_36;
}else{
for(i in _34._items){
_36.push(_34._items[i]);
}
}
}
var _3f=_36.length;
if(_34.sort){
_36.sort(dojo.data.util.sorter.createSortFunction(_34.sort,this));
}
var _40=0;
var _41=_36.length;
if(_34.start>0&&_34.start<_36.length){
_40=_34.start;
}
if(_34.count&&_34.count){
_41=_34.count;
}
var _42=_40+_41;
if(_42>_36.length){
_42=_36.length;
}
_36=_36.slice(_40,_42);
if(_34.onBegin){
_34.onBegin.call(_35,_3f,_34);
}
if(_34.onItem){
if(dojo.isArray(_36)){
for(i=0;i<_36.length;i++){
_34.onItem.call(_35,_36[i],_34);
}
if(_34.onComplete){
_34.onComplete.call(_35,null,_34);
}
}
}else{
if(_34.onComplete){
_34.onComplete.call(_35,_36,_34);
}
}
return _34;
},close:function(){
this._cache={};
this._allItems=null;
},_assertIsItem:function(_43){
if(!this.isItem(_43)){
throw new Error(this._cName+": Invalid item argument.");
}
},_assertIsAttribute:function(_44){
if(typeof _44!=="string"){
throw new Error(this._cName+": Invalid attribute argument.");
}
},_containsValue:function(_45,_46,_47,_48){
return dojo.some(this.getValues(_45,_46),function(_49){
if(_49!==null&&!dojo.isObject(_49)&&_48){
if(_49.toString().match(_48)){
return true;
}
}else{
if(_47===_49){
return true;
}
}
return false;
});
}});
}
