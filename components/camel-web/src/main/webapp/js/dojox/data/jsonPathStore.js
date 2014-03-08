/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.jsonPathStore"]){
dojo._hasResource["dojox.data.jsonPathStore"]=true;
dojo.provide("dojox.data.jsonPathStore");
dojo.require("dojox.jsonPath");
dojo.require("dojo.date");
dojo.require("dojo.date.locale");
dojo.require("dojo.date.stamp");
dojox.data.ASYNC_MODE=0;
dojox.data.SYNC_MODE=1;
dojo.declare("dojox.data.jsonPathStore",null,{mode:dojox.data.ASYNC_MODE,metaLabel:"_meta",hideMetaAttributes:false,autoIdPrefix:"_auto_",autoIdentity:true,idAttribute:"_id",indexOnLoad:true,labelAttribute:"",url:"",_replaceRegex:/\'\]/gi,noRevert:false,constructor:function(_1){
this.byId=this.fetchItemByIdentity;
if(_1){
dojo.mixin(this,_1);
}
this._dirtyItems=[];
this._autoId=0;
this._referenceId=0;
this._references={};
this._fetchQueue=[];
this.index={};
var _2="("+this.metaLabel+"'])";
this.metaRegex=new RegExp(_2);
if(!this.data&&!this.url){
this.setData({});
}
if(this.data&&!this.url){
this.setData(this.data);
delete this.data;
}
if(this.url){
dojo.xhrGet({url:_1.url,handleAs:"json",load:dojo.hitch(this,"setData"),sync:this.mode});
}
},_loadData:function(_3){
if(this._data){
delete this._data;
}
if(dojo.isString(_3)){
this._data=dojo.fromJson(_3);
}else{
this._data=_3;
}
if(this.indexOnLoad){
this.buildIndex();
}
this._updateMeta(this._data,{path:"$"});
this.onLoadData(this._data);
},onLoadData:function(_4){
while(this._fetchQueue.length>0){
var _5=this._fetchQueue.shift();
this.fetch(_5);
}
},setData:function(_6){
this._loadData(_6);
},buildIndex:function(_7,_8){
if(!this.idAttribute){
throw new Error("buildIndex requires idAttribute for the store");
}
_8=_8||this._data;
var _9=_7;
_7=_7||"$";
_7+="[*]";
var _a=this.fetch({query:_7,mode:dojox.data.SYNC_MODE});
for(var i=0;i<_a.length;i++){
var _c,_d;
if(dojo.isObject(_a[i])){
var _e=_a[i][this.metaLabel]["path"];
if(_9){
_c=_9.split("['");
_d=_c[_c.length-1].replace(this._replaceRegex,"");
if(!dojo.isArray(_a[i])){
this._addReference(_a[i],{parent:_8,attribute:_d});
this.buildIndex(_e,_a[i]);
}else{
this.buildIndex(_e,_8);
}
}else{
_c=_e.split("['");
_d=_c[_c.length-1].replace(this._replaceRegex,"");
this._addReference(_a[i],{parent:this._data,attribute:_d});
this.buildIndex(_e,_a[i]);
}
}
}
},_correctReference:function(_f){
if(this.index[_f[this.idAttribute]]&&(this.index[_f[this.idAttribute]][this.metaLabel]===_f[this.metaLabel])){
return this.index[_f[this.idAttribute]];
}
return _f;
},getValue:function(_10,_11){
_10=this._correctReference(_10);
return _10[_11];
},getValues:function(_12,_13){
_12=this._correctReference(_12);
return dojo.isArray(_12[_13])?_12[_13]:[_12[_13]];
},getAttributes:function(_14){
_14=this._correctReference(_14);
var res=[];
for(var i in _14){
if(this.hideMetaAttributes&&(i==this.metaLabel)){
continue;
}
res.push(i);
}
return res;
},hasAttribute:function(_17,_18){
_17=this._correctReference(_17);
if(_18 in _17){
return true;
}
return false;
},containsValue:function(_19,_1a,_1b){
_19=this._correctReference(_19);
if(_19[_1a]&&_19[_1a]==_1b){
return true;
}
if(dojo.isObject(_19[_1a])||dojo.isObject(_1b)){
if(this._shallowCompare(_19[_1a],_1b)){
return true;
}
}
return false;
},_shallowCompare:function(a,b){
if((dojo.isObject(a)&&!dojo.isObject(b))||(dojo.isObject(b)&&!dojo.isObject(a))){
return false;
}
if(a["getFullYear"]||b["getFullYear"]){
if((a["getFullYear"]&&!b["getFullYear"])||(b["getFullYear"]&&!a["getFullYear"])){
return false;
}else{
if(!dojo.date.compare(a,b)){
return true;
}
return false;
}
}
for(var i in b){
if(dojo.isObject(b[i])){
if(!a[i]||!dojo.isObject(a[i])){
return false;
}
if(b[i]["getFullYear"]){
if(!a[i]["getFullYear"]){
return false;
}
if(dojo.date.compare(a,b)){
return false;
}
}else{
if(!this._shallowCompare(a[i],b[i])){
return false;
}
}
}else{
if(!b[i]||(a[i]!=b[i])){
return false;
}
}
}
for(i in a){
if(!b[i]){
return false;
}
}
return true;
},isItem:function(_1f){
if(!dojo.isObject(_1f)||!_1f[this.metaLabel]){
return false;
}
if(this.requireId&&this._hasId&&!_1f[this._id]){
return false;
}
return true;
},isItemLoaded:function(_20){
_20=this._correctReference(_20);
return this.isItem(_20);
},loadItem:function(_21){
return true;
},_updateMeta:function(_22,_23){
if(_22&&_22[this.metaLabel]){
dojo.mixin(_22[this.metaLabel],_23);
return;
}
_22[this.metaLabel]=_23;
},cleanMeta:function(_24,_25){
_24=_24||this._data;
if(_24[this.metaLabel]){
if(_24[this.metaLabel].autoId){
delete _24[this.idAttribute];
}
delete _24[this.metaLabel];
}
if(dojo.isArray(_24)){
for(var i=0;i<_24.length;i++){
if(dojo.isObject(_24[i])||dojo.isArray(_24[i])){
this.cleanMeta(_24[i]);
}
}
}else{
if(dojo.isObject(_24)){
for(i in _24){
if(dojo.isObject(_24[i])){
this.cleanMeta(_24[i]);
}
}
}
}
},fetch:function(_27){
if(!this._data){
this._fetchQueue.push(_27);
return _27;
}
if(dojo.isString(_27)){
_28=_27;
_27={query:_28,mode:dojox.data.SYNC_MODE};
}
var _28;
if(!_27||!_27.query){
if(!_27){
var _27={};
}
if(!_27.query){
_27.query="$..*";
_28=_27.query;
}
}
if(dojo.isObject(_27.query)){
if(_27.query.query){
_28=_27.query.query;
}else{
_28=_27.query="$..*";
}
if(_27.query.queryOptions){
_27.queryOptions=_27.query.queryOptions;
}
}else{
_28=_27.query;
}
if(!_27.mode){
_27.mode=this.mode;
}
if(!_27.queryOptions){
_27.queryOptions={};
}
_27.queryOptions.resultType="BOTH";
var _29=dojox.jsonPath.query(this._data,_28,_27.queryOptions);
var tmp=[];
var _2b=0;
for(var i=0;i<_29.length;i++){
if(_27.start&&i<_27.start){
continue;
}
if(_27.count&&(_2b>=_27.count)){
continue;
}
var _2d=_29[i]["value"];
var _2e=_29[i]["path"];
if(!dojo.isObject(_2d)){
continue;
}
if(this.metaRegex.exec(_2e)){
continue;
}
this._updateMeta(_2d,{path:_29[i].path});
if(this.autoIdentity&&!_2d[this.idAttribute]){
var _2f=this.autoIdPrefix+this._autoId++;
_2d[this.idAttribute]=_2f;
_2d[this.metaLabel].autoId=true;
}
if(_2d[this.idAttribute]){
this.index[_2d[this.idAttribute]]=_2d;
}
_2b++;
tmp.push(_2d);
}
_29=tmp;
var _30=_27.scope||dojo.global;
if("sort" in _27){

}
if(_27.mode==dojox.data.SYNC_MODE){
return _29;
}
if(_27.onBegin){
_27["onBegin"].call(_30,_29.length,_27);
}
if(_27.onItem){
for(var i=0;i<_29.length;i++){
_27["onItem"].call(_30,_29[i],_27);
}
}
if(_27.onComplete){
_27["onComplete"].call(_30,_29,_27);
}
return _27;
},dump:function(_31){
var _31=_31||{};
var d=_31.data||this._data;
if(!_31.suppressExportMeta&&_31.clone){
_33=dojo.clone(d);
if(_33[this.metaLabel]){
_33[this.metaLabel]["clone"]=true;
}
}else{
var _33=d;
}
if(!_31.suppressExportMeta&&_33[this.metaLabel]){
_33[this.metaLabel]["last_export"]=new Date().toString();
}
if(_31.cleanMeta){
this.cleanMeta(_33);
}
switch(_31.type){
case "raw":
return _33;
case "json":
default:
return dojo.toJson(_33,_31.pretty||false);
}
},getFeatures:function(){
return {"dojo.data.api.Read":true,"dojo.data.api.Identity":true,"dojo.data.api.Write":true,"dojo.data.api.Notification":true};
},getLabel:function(_34){
_34=this._correctReference(_34);
var _35="";
if(dojo.isFunction(this.createLabel)){
return this.createLabel(_34);
}
if(this.labelAttribute){
if(dojo.isArray(this.labelAttribute)){
for(var i=0;i<this.labelAttribute.length;i++){
if(i>0){
_35+=" ";
}
_35+=_34[this.labelAttribute[i]];
}
return _35;
}else{
return _34[this.labelAttribute];
}
}
return _34.toString();
},getLabelAttributes:function(_37){
_37=this._correctReference(_37);
return dojo.isArray(this.labelAttribute)?this.labelAttribute:[this.labelAttribute];
},sort:function(a,b){

},getIdentity:function(_3a){
if(this.isItem(_3a)){
return _3a[this.idAttribute];
}
throw new Error("Id not found for item");
},getIdentityAttributes:function(_3b){
return [this.idAttribute];
},fetchItemByIdentity:function(_3c){
var id;
if(dojo.isString(_3c)){
id=_3c;
_3c={identity:id,mode:dojox.data.SYNC_MODE};
}else{
if(_3c){
id=_3c["identity"];
}
if(!_3c.mode){
_3c.mode=this.mode;
}
}
if(this.index&&(this.index[id]||this.index["identity"])){
if(_3c.mode==dojox.data.SYNC_MODE){
return this.index[id];
}
if(_3c.onItem){
_3c["onItem"].call(_3c.scope||dojo.global,this.index[id],_3c);
}
return _3c;
}else{
if(_3c.mode==dojox.data.SYNC_MODE){
return false;
}
}
if(_3c.onError){
_3c["onItem"].call(_3c.scope||dojo.global,new Error("Item Not Found: "+id),_3c);
}
return _3c;
},_makeItAnItem:function(_3e,_3f){
var _40={};
if(this.idAttribute&&!_3e[this.idAttribute]){
if(this.requireId){
throw new Error("requireId is enabled, new items must have an id defined to be added");
}
if(this.autoIdentity){
var _41=this.autoIdPrefix+this._autoId++;
_3e[this.idAttribute]=_41;
_40.autoId=true;
}
}
if(!_3f&&!_3f.attribute&&!this.idAttribute&&!_3e[this.idAttribute]){
throw new Error("Adding a new item requires, at a minimum, either the pInfo information, including the pInfo.attribute, or an id on the item in the field identified by idAttribute");
}
if(!_3f.attribute){
_3f.attribute=_3e[this.idAttribute];
}
if(_3e[this.idAttribute]){
this.index[_3e[this.idAttribute]]=_3e;
}
this._updateMeta(_3e,_40);
this._addReference(_3e,{parent:_3f.item,attribute:_3f.attribute});
this._setDirty(_3e);
if(_3e[_3f.attribute]&&dojo.isArray(_3e[_3f.attribute])){
for(var i=0;i<_3e[_3f.attribute].length;i++){
this._makeItAnItem(_3e[_3f.attribute][i],{item:_3e,attribute:_3f.attribute});
}
}
return _3e;
},newItem:function(_43,_44){
var _45={item:this._data};
if(_44){
if(_44.parent){
_44.item=_44.parent;
}
dojo.mixin(_45,_44);
}
this._makeItAnItem(_43,_45);
_45.oldValue=this._trimItem(_45.item[_45.attribute]);
this._setDirty(_45.item);
if(dojo.isArray(_45.item[_45.attribute])){
_45.item[_45.attribute].push(_43);
}else{
_45.item[_45.attribute]=_43;
}
_45.newValue=_45.item[_45.attribute];
this.onNew(_43,_45);
if(_43[_45.attribute]&&dojo.isArray(_43[_45.attribute])){
for(var i=0;i<_43[_45.attribute].length;i++){
this.onNew(_43[_45.attribute][i],{item:_43,attribute:_45.attribute});
}
}
return _43;
},_addReference:function(_47,_48){
var rid="_ref_"+this._referenceId++;
if(!_47[this.metaLabel]["referenceIds"]){
_47[this.metaLabel]["referenceIds"]=[];
}
_47[this.metaLabel]["referenceIds"].push(rid);
this._references[rid]=_48;
},deleteItem:function(_4a){
_4a=this._correctReference(_4a);

if(this.isItem(_4a)){
while(_4a[this.metaLabel]["referenceIds"].length>0){


var rid=_4a[this.metaLabel]["referenceIds"].pop();
var _4c=this._references[rid];

var _4d=_4c.parent;
var _4e=_4c.attribute;
if(_4d&&_4d[_4e]&&!dojo.isArray(_4d[_4e])){
this._setDirty(_4d);
this.unsetAttribute(_4d,_4e);
delete _4d[_4e];
}
if(dojo.isArray(_4d[_4e])){

var _4f=this._trimItem(_4d[_4e]);
var _50=false;
for(var i=0;i<_4d[_4e].length&&!_50;i++){
if(_4d[_4e][i][this.metaLabel]===_4a[this.metaLabel]){
_50=true;
}
}
if(_50){
this._setDirty(_4d);
var del=_4d[_4e].splice(i-1,1);
delete del;
}
var _53=this._trimItem(_4d[_4e]);
this.onSet(_4d,_4e,_4f,_53);
}
delete this._references[rid];
}
this.onDelete(_4a);
delete _4a;
this.index[id]=null;
delete this.index[id];
}
},_setDirty:function(_54){
if(this.noRevert){
return;
}
for(var i=0;i<this._dirtyItems.length;i++){
if(_54[this.idAttribute]==this._dirtyItems[i][this.idAttribute]){
return;
}
}
this._dirtyItems.push({item:_54,old:this._trimItem(_54)});
this._updateMeta(_54,{isDirty:true});
},setValue:function(_56,_57,_58){
_56=this._correctReference(_56);
this._setDirty(_56);
var old=_56[_57]|undefined;
_56[_57]=_58;
this.onSet(_56,_57,old,_58);
},setValues:function(_5a,_5b,_5c){
_5a=this._correctReference(_5a);
if(!dojo.isArray(_5c)){
throw new Error("setValues expects to be passed an Array object as its value");
}
this._setDirty(_5a);
var old=_5a[_5b]||null;
_5a[_5b]=_5c;
this.onSet(_5a,_5b,old,_5c);
},unsetAttribute:function(_5e,_5f){
_5e=this._correctReference(_5e);
this._setDirty(_5e);
var old=_5e[_5f];
delete _5e[_5f];
this.onSet(_5e,_5f,old,null);
},save:function(_61){
var _62=[];
if(!_61){
_61={};
}
while(this._dirtyItems.length>0){
var _63=this._dirtyItems.pop()["item"];
var t=this._trimItem(_63);
var d;
switch(_61.format){
case "json":
d=dojo.toJson(t);
break;
case "raw":
default:
d=t;
}
_62.push(d);
this._markClean(_63);
}
this.onSave(_62);
},_markClean:function(_66){
if(_66&&_66[this.metaLabel]&&_66[this.metaLabel]["isDirty"]){
delete _66[this.metaLabel]["isDirty"];
}
},revert:function(){
while(this._dirtyItems.length>0){
var d=this._dirtyItems.pop();
this._mixin(d.item,d.old);
}
this.onRevert();
},_mixin:function(_68,_69){
var mix;
if(dojo.isObject(_69)){
if(dojo.isArray(_69)){
while(_68.length>0){
_68.pop();
}
for(var i=0;i<_69.length;i++){
if(dojo.isObject(_69[i])){
if(dojo.isArray(_69[i])){
mix=[];
}else{
mix={};
if(_69[i][this.metaLabel]&&_69[i][this.metaLabel]["type"]&&_69[i][this.metaLabel]["type"]=="reference"){
_68[i]=this.index[_69[i][this.idAttribute]];
continue;
}
}
this._mixin(mix,_69[i]);
_68.push(mix);
}else{
_68.push(_69[i]);
}
}
}else{
for(var i in _68){
if(i in _69){
continue;
}
delete _68[i];
}
for(var i in _69){
if(dojo.isObject(_69[i])){
if(dojo.isArray(_69[i])){
mix=[];
}else{
if(_69[i][this.metaLabel]&&_69[i][this.metaLabel]["type"]&&_69[i][this.metaLabel]["type"]=="reference"){
_68[i]=this.index[_69[i][this.idAttribute]];
continue;
}
mix={};
}
this._mixin(mix,_69[i]);
_68[i]=mix;
}else{
_68[i]=_69[i];
}
}
}
}
},isDirty:function(_6c){
_6c=this._correctReference(_6c);
return _6c&&_6c[this.metaLabel]&&_6c[this.metaLabel]["isDirty"];
},_createReference:function(_6d){
var obj={};
obj[this.metaLabel]={type:"reference"};
obj[this.idAttribute]=_6d[this.idAttribute];
return obj;
},_trimItem:function(_6f){
var _70;
if(dojo.isArray(_6f)){
_70=[];
for(var i=0;i<_6f.length;i++){
if(dojo.isArray(_6f[i])){
_70.push(this._trimItem(_6f[i]));
}else{
if(dojo.isObject(_6f[i])){
if(_6f[i]["getFullYear"]){
_70.push(dojo.date.stamp.toISOString(_6f[i]));
}else{
if(_6f[i][this.idAttribute]){
_70.push(this._createReference(_6f[i]));
}else{
_70.push(this._trimItem(_6f[i]));
}
}
}else{
_70.push(_6f[i]);
}
}
}
return _70;
}
if(dojo.isObject(_6f)){
_70={};
for(var _72 in _6f){
if(!_6f[_72]){
_70[_72]=undefined;
continue;
}
if(dojo.isArray(_6f[_72])){
_70[_72]=this._trimItem(_6f[_72]);
}else{
if(dojo.isObject(_6f[_72])){
if(_6f[_72]["getFullYear"]){
_70[_72]=dojo.date.stamp.toISOString(_6f[_72]);
}else{
if(_6f[_72][this.idAttribute]){
_70[_72]=this._createReference(_6f[_72]);
}else{
_70[_72]=this._trimItem(_6f[_72]);
}
}
}else{
_70[_72]=_6f[_72];
}
}
}
return _70;
}
},onSet:function(_73,_74,_75,_76){
},onNew:function(_77,_78){
},onDelete:function(_79){
},onSave:function(_7a){
},onRevert:function(){
}});
dojox.data.jsonPathStore.byId=dojox.data.jsonPathStore.fetchItemByIdentity;
}
