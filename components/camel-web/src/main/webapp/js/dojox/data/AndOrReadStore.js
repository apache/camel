/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.AndOrReadStore"]){
dojo._hasResource["dojox.data.AndOrReadStore"]=true;
dojo.provide("dojox.data.AndOrReadStore");
dojo.require("dojo.data.util.filter");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.date.stamp");
dojo.declare("dojox.data.AndOrReadStore",null,{constructor:function(_1){
this._arrayOfAllItems=[];
this._arrayOfTopLevelItems=[];
this._loadFinished=false;
this._jsonFileUrl=_1.url;
this._jsonData=_1.data;
this._datatypeMap=_1.typeMap||{};
if(!this._datatypeMap["Date"]){
this._datatypeMap["Date"]={type:Date,deserialize:function(_2){
return dojo.date.stamp.fromISOString(_2);
}};
}
this._features={"dojo.data.api.Read":true,"dojo.data.api.Identity":true};
this._itemsByIdentity=null;
this._storeRefPropName="_S";
this._itemNumPropName="_0";
this._rootItemPropName="_RI";
this._reverseRefMap="_RRM";
this._loadInProgress=false;
this._queuedFetches=[];
if(_1.urlPreventCache!==undefined){
this.urlPreventCache=_1.urlPreventCache?true:false;
}
if(_1.clearOnClose){
this.clearOnClose=true;
}
},url:"",data:null,typeMap:null,clearOnClose:false,urlPreventCache:false,_assertIsItem:function(_3){
if(!this.isItem(_3)){
throw new Error("dojox.data.AndOrReadStore: Invalid item argument.");
}
},_assertIsAttribute:function(_4){
if(typeof _4!=="string"){
throw new Error("dojox.data.AndOrReadStore: Invalid attribute argument.");
}
},getValue:function(_5,_6,_7){
var _8=this.getValues(_5,_6);
return (_8.length>0)?_8[0]:_7;
},getValues:function(_9,_a){
this._assertIsItem(_9);
this._assertIsAttribute(_a);
return _9[_a]||[];
},getAttributes:function(_b){
this._assertIsItem(_b);
var _c=[];
for(var _d in _b){
if((_d!==this._storeRefPropName)&&(_d!==this._itemNumPropName)&&(_d!==this._rootItemPropName)&&(_d!==this._reverseRefMap)){
_c.push(_d);
}
}
return _c;
},hasAttribute:function(_e,_f){
return this.getValues(_e,_f).length>0;
},containsValue:function(_10,_11,_12){
var _13=undefined;
if(typeof _12==="string"){
_13=dojo.data.util.filter.patternToRegExp(_12,false);
}
return this._containsValue(_10,_11,_12,_13);
},_containsValue:function(_14,_15,_16,_17){
return dojo.some(this.getValues(_14,_15),function(_18){
if(_18!==null&&!dojo.isObject(_18)&&_17){
if(_18.toString().match(_17)){
return true;
}
}else{
if(_16===_18){
return true;
}
}
});
},isItem:function(_19){
if(_19&&_19[this._storeRefPropName]===this){
if(this._arrayOfAllItems[_19[this._itemNumPropName]]===_19){
return true;
}
}
return false;
},isItemLoaded:function(_1a){
return this.isItem(_1a);
},loadItem:function(_1b){
this._assertIsItem(_1b.item);
},getFeatures:function(){
return this._features;
},getLabel:function(_1c){
if(this._labelAttr&&this.isItem(_1c)){
return this.getValue(_1c,this._labelAttr);
}
return undefined;
},getLabelAttributes:function(_1d){
if(this._labelAttr){
return [this._labelAttr];
}
return null;
},_fetchItems:function(_1e,_1f,_20){
var _21=this;
var _22=function(_23,_24){
var _25=[];
if(_23.query){
var _26=dojo.fromJson(dojo.toJson(_23.query));
if(typeof _26=="object"){
var _27=0;
var p;
for(p in _26){
_27++;
}
if(_27>1&&_26.complexQuery){
var cq=_26.complexQuery;
var _2a=false;
for(p in _26){
if(p!=="complexQuery"){
if(!_2a){
cq="( "+cq+" )";
_2a=true;
}
cq+=" AND "+p+":"+_23.query[p];
delete _26[p];
}
}
_26.complexQuery=cq;
}
}
var _2b=_23.queryOptions?_23.queryOptions.ignoreCase:false;
if(typeof _26!="string"){
_26=dojo.toJson(_26);
_26=_26.replace(/\\\\/g,"\\");
}
_26=_26.replace(/\\"/g,"\"");
var _2c=dojo.trim(_26.replace(/{|}/g,""));
var _2d,i;
if(_2c.match(/"? *complexQuery *"?:/)){
_2c=dojo.trim(_2c.replace(/"?\s*complexQuery\s*"?:/,""));
var _2f=["'","\""];
var _30,_31;
var _32=false;
for(i=0;i<_2f.length;i++){
_30=_2c.indexOf(_2f[i]);
_2d=_2c.indexOf(_2f[i],1);
_31=_2c.indexOf(":",1);
if(_30===0&&_2d!=-1&&_31<_2d){
_32=true;
break;
}
}
if(_32){
_2c=_2c.replace(/^\"|^\'|\"$|\'$/g,"");
}
}
var _33=_2c;
var _34=/^,|^NOT |^AND |^OR |^\(|^\)|^!|^&&|^\|\|/i;
var _35="";
var op="";
var val="";
var pos=-1;
var err=false;
var key="";
var _3b="";
var tok="";
_2d=-1;
for(i=0;i<_24.length;++i){
var _3d=true;
var _3e=_24[i];
if(_3e===null){
_3d=false;
}else{
_2c=_33;
_35="";
while(_2c.length>0&&!err){
op=_2c.match(_34);
while(op&&!err){
_2c=dojo.trim(_2c.replace(op[0],""));
op=dojo.trim(op[0]).toUpperCase();
op=op=="NOT"?"!":op=="AND"||op==","?"&&":op=="OR"?"||":op;
op=" "+op+" ";
_35+=op;
op=_2c.match(_34);
}
if(_2c.length>0){
pos=_2c.indexOf(":");
if(pos==-1){
err=true;
break;
}else{
key=dojo.trim(_2c.substring(0,pos).replace(/\"|\'/g,""));
_2c=dojo.trim(_2c.substring(pos+1));
tok=_2c.match(/^\'|^\"/);
if(tok){
tok=tok[0];
pos=_2c.indexOf(tok);
_2d=_2c.indexOf(tok,pos+1);
if(_2d==-1){
err=true;
break;
}
_3b=_2c.substring(pos+1,_2d);
if(_2d==_2c.length-1){
_2c="";
}else{
_2c=dojo.trim(_2c.substring(_2d+1));
}
_35+=_21._containsValue(_3e,key,_3b,dojo.data.util.filter.patternToRegExp(_3b,_2b));
}else{
tok=_2c.match(/\s|\)|,/);
if(tok){
var _3f=new Array(tok.length);
for(var j=0;j<tok.length;j++){
_3f[j]=_2c.indexOf(tok[j]);
}
pos=_3f[0];
if(_3f.length>1){
for(var j=1;j<_3f.length;j++){
pos=Math.min(pos,_3f[j]);
}
}
_3b=dojo.trim(_2c.substring(0,pos));
_2c=dojo.trim(_2c.substring(pos));
}else{
_3b=dojo.trim(_2c);
_2c="";
}
_35+=_21._containsValue(_3e,key,_3b,dojo.data.util.filter.patternToRegExp(_3b,_2b));
}
}
}
}
_3d=eval(_35);
}
if(_3d){
_25.push(_3e);
}
}
if(err){
_25=[];

}
_1f(_25,_23);
}else{
for(var i=0;i<_24.length;++i){
var _41=_24[i];
if(_41!==null){
_25.push(_41);
}
}
_1f(_25,_23);
}
};
if(this._loadFinished){
_22(_1e,this._getItemsArray(_1e.queryOptions));
}else{
if(this._jsonFileUrl){
if(this._loadInProgress){
this._queuedFetches.push({args:_1e,filter:_22});
}else{
this._loadInProgress=true;
var _42={url:_21._jsonFileUrl,handleAs:"json-comment-optional",preventCache:this.urlPreventCache};
var _43=dojo.xhrGet(_42);
_43.addCallback(function(_44){
try{
_21._getItemsFromLoadedData(_44);
_21._loadFinished=true;
_21._loadInProgress=false;
_22(_1e,_21._getItemsArray(_1e.queryOptions));
_21._handleQueuedFetches();
}
catch(e){
_21._loadFinished=true;
_21._loadInProgress=false;
_20(e,_1e);
}
});
_43.addErrback(function(_45){
_21._loadInProgress=false;
_20(_45,_1e);
});
var _46=null;
if(_1e.abort){
_46=_1e.abort;
}
_1e.abort=function(){
var df=_43;
if(df&&df.fired===-1){
df.cancel();
df=null;
}
if(_46){
_46.call(_1e);
}
};
}
}else{
if(this._jsonData){
try{
this._loadFinished=true;
this._getItemsFromLoadedData(this._jsonData);
this._jsonData=null;
_22(_1e,this._getItemsArray(_1e.queryOptions));
}
catch(e){
_20(e,_1e);
}
}else{
_20(new Error("dojox.data.AndOrReadStore: No JSON source data was provided as either URL or a nested Javascript object."),_1e);
}
}
}
},_handleQueuedFetches:function(){
if(this._queuedFetches.length>0){
for(var i=0;i<this._queuedFetches.length;i++){
var _49=this._queuedFetches[i];
var _4a=_49.args;
var _4b=_49.filter;
if(_4b){
_4b(_4a,this._getItemsArray(_4a.queryOptions));
}else{
this.fetchItemByIdentity(_4a);
}
}
this._queuedFetches=[];
}
},_getItemsArray:function(_4c){
if(_4c&&_4c.deep){
return this._arrayOfAllItems;
}
return this._arrayOfTopLevelItems;
},close:function(_4d){
if(this.clearOnClose&&(this._jsonFileUrl!=="")){
this._arrayOfAllItems=[];
this._arrayOfTopLevelItems=[];
this._loadFinished=false;
this._itemsByIdentity=null;
this._loadInProgress=false;
this._queuedFetches=[];
}
},_getItemsFromLoadedData:function(_4e){
function _4f(_50){
var _51=((_50!==null)&&(typeof _50==="object")&&(!dojo.isArray(_50))&&(!dojo.isFunction(_50))&&(_50.constructor==Object)&&(typeof _50._reference==="undefined")&&(typeof _50._type==="undefined")&&(typeof _50._value==="undefined"));
return _51;
};
var _52=this;
function _53(_54){
_52._arrayOfAllItems.push(_54);
for(var _55 in _54){
var _56=_54[_55];
if(_56){
if(dojo.isArray(_56)){
var _57=_56;
for(var k=0;k<_57.length;++k){
var _59=_57[k];
if(_4f(_59)){
_53(_59);
}
}
}else{
if(_4f(_56)){
_53(_56);
}
}
}
}
};
this._labelAttr=_4e.label;
var i;
var _5b;
this._arrayOfAllItems=[];
this._arrayOfTopLevelItems=_4e.items;
for(i=0;i<this._arrayOfTopLevelItems.length;++i){
_5b=this._arrayOfTopLevelItems[i];
_53(_5b);
_5b[this._rootItemPropName]=true;
}
var _5c={};
var key;
for(i=0;i<this._arrayOfAllItems.length;++i){
_5b=this._arrayOfAllItems[i];
for(key in _5b){
if(key!==this._rootItemPropName){
var _5e=_5b[key];
if(_5e!==null){
if(!dojo.isArray(_5e)){
_5b[key]=[_5e];
}
}else{
_5b[key]=[null];
}
}
_5c[key]=key;
}
}
while(_5c[this._storeRefPropName]){
this._storeRefPropName+="_";
}
while(_5c[this._itemNumPropName]){
this._itemNumPropName+="_";
}
while(_5c[this._reverseRefMap]){
this._reverseRefMap+="_";
}
var _5f;
var _60=_4e.identifier;
if(_60){
this._itemsByIdentity={};
this._features["dojo.data.api.Identity"]=_60;
for(i=0;i<this._arrayOfAllItems.length;++i){
_5b=this._arrayOfAllItems[i];
_5f=_5b[_60];
var _61=_5f[0];
if(!this._itemsByIdentity[_61]){
this._itemsByIdentity[_61]=_5b;
}else{
if(this._jsonFileUrl){
throw new Error("dojox.data.AndOrReadStore:  The json data as specified by: ["+this._jsonFileUrl+"] is malformed.  Items within the list have identifier: ["+_60+"].  Value collided: ["+_61+"]");
}else{
if(this._jsonData){
throw new Error("dojox.data.AndOrReadStore:  The json data provided by the creation arguments is malformed.  Items within the list have identifier: ["+_60+"].  Value collided: ["+_61+"]");
}
}
}
}
}else{
this._features["dojo.data.api.Identity"]=Number;
}
for(i=0;i<this._arrayOfAllItems.length;++i){
_5b=this._arrayOfAllItems[i];
_5b[this._storeRefPropName]=this;
_5b[this._itemNumPropName]=i;
}
for(i=0;i<this._arrayOfAllItems.length;++i){
_5b=this._arrayOfAllItems[i];
for(key in _5b){
_5f=_5b[key];
for(var j=0;j<_5f.length;++j){
_5e=_5f[j];
if(_5e!==null&&typeof _5e=="object"){
if(_5e._type&&_5e._value){
var _63=_5e._type;
var _64=this._datatypeMap[_63];
if(!_64){
throw new Error("dojox.data.AndOrReadStore: in the typeMap constructor arg, no object class was specified for the datatype '"+_63+"'");
}else{
if(dojo.isFunction(_64)){
_5f[j]=new _64(_5e._value);
}else{
if(dojo.isFunction(_64.deserialize)){
_5f[j]=_64.deserialize(_5e._value);
}else{
throw new Error("dojox.data.AndOrReadStore: Value provided in typeMap was neither a constructor, nor a an object with a deserialize function");
}
}
}
}
if(_5e._reference){
var _65=_5e._reference;
if(!dojo.isObject(_65)){
_5f[j]=this._itemsByIdentity[_65];
}else{
for(var k=0;k<this._arrayOfAllItems.length;++k){
var _67=this._arrayOfAllItems[k];
var _68=true;
for(var _69 in _65){
if(_67[_69]!=_65[_69]){
_68=false;
}
}
if(_68){
_5f[j]=_67;
}
}
}
if(this.referenceIntegrity){
var _6a=_5f[j];
if(this.isItem(_6a)){
this._addReferenceToMap(_6a,_5b,key);
}
}
}else{
if(this.isItem(_5e)){
if(this.referenceIntegrity){
this._addReferenceToMap(_5e,_5b,key);
}
}
}
}
}
}
}
},_addReferenceToMap:function(_6b,_6c,_6d){
},getIdentity:function(_6e){
var _6f=this._features["dojo.data.api.Identity"];
if(_6f===Number){
return _6e[this._itemNumPropName];
}else{
var _70=_6e[_6f];
if(_70){
return _70[0];
}
}
return null;
},fetchItemByIdentity:function(_71){
if(!this._loadFinished){
var _72=this;
if(this._jsonFileUrl){
if(this._loadInProgress){
this._queuedFetches.push({args:_71});
}else{
this._loadInProgress=true;
var _73={url:_72._jsonFileUrl,handleAs:"json-comment-optional",preventCache:this.urlPreventCache};
var _74=dojo.xhrGet(_73);
_74.addCallback(function(_75){
var _76=_71.scope?_71.scope:dojo.global;
try{
_72._getItemsFromLoadedData(_75);
_72._loadFinished=true;
_72._loadInProgress=false;
var _77=_72._getItemByIdentity(_71.identity);
if(_71.onItem){
_71.onItem.call(_76,_77);
}
_72._handleQueuedFetches();
}
catch(error){
_72._loadInProgress=false;
if(_71.onError){
_71.onError.call(_76,error);
}
}
});
_74.addErrback(function(_78){
_72._loadInProgress=false;
if(_71.onError){
var _79=_71.scope?_71.scope:dojo.global;
_71.onError.call(_79,_78);
}
});
}
}else{
if(this._jsonData){
_72._getItemsFromLoadedData(_72._jsonData);
_72._jsonData=null;
_72._loadFinished=true;
var _7a=_72._getItemByIdentity(_71.identity);
if(_71.onItem){
var _7b=_71.scope?_71.scope:dojo.global;
_71.onItem.call(_7b,_7a);
}
}
}
}else{
var _7a=this._getItemByIdentity(_71.identity);
if(_71.onItem){
var _7b=_71.scope?_71.scope:dojo.global;
_71.onItem.call(_7b,_7a);
}
}
},_getItemByIdentity:function(_7c){
var _7d=null;
if(this._itemsByIdentity){
_7d=this._itemsByIdentity[_7c];
}else{
_7d=this._arrayOfAllItems[_7c];
}
if(_7d===undefined){
_7d=null;
}
return _7d;
},getIdentityAttributes:function(_7e){
var _7f=this._features["dojo.data.api.Identity"];
if(_7f===Number){
return null;
}else{
return [_7f];
}
},_forceLoad:function(){
var _80=this;
if(this._jsonFileUrl){
var _81={url:_80._jsonFileUrl,handleAs:"json-comment-optional",preventCache:this.urlPreventCache,sync:true};
var _82=dojo.xhrGet(_81);
_82.addCallback(function(_83){
try{
if(_80._loadInProgress!==true&&!_80._loadFinished){
_80._getItemsFromLoadedData(_83);
_80._loadFinished=true;
}else{
if(_80._loadInProgress){
throw new Error("dojox.data.AndOrReadStore:  Unable to perform a synchronous load, an async load is in progress.");
}
}
}
catch(e){

throw e;
}
});
_82.addErrback(function(_84){
throw _84;
});
}else{
if(this._jsonData){
_80._getItemsFromLoadedData(_80._jsonData);
_80._jsonData=null;
_80._loadFinished=true;
}
}
}});
dojo.extend(dojox.data.AndOrReadStore,dojo.data.util.simpleFetch);
}
