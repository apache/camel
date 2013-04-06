/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.AndOrWriteStore"]){
dojo._hasResource["dojox.data.AndOrWriteStore"]=true;
dojo.provide("dojox.data.AndOrWriteStore");
dojo.require("dojox.data.AndOrReadStore");
dojo.declare("dojox.data.AndOrWriteStore",dojox.data.AndOrReadStore,{constructor:function(_1){
this._features["dojo.data.api.Write"]=true;
this._features["dojo.data.api.Notification"]=true;
this._pending={_newItems:{},_modifiedItems:{},_deletedItems:{}};
if(!this._datatypeMap["Date"].serialize){
this._datatypeMap["Date"].serialize=function(_2){
return dojo.date.stamp.toISOString(_2,{zulu:true});
};
}
if(_1&&(_1.referenceIntegrity===false)){
this.referenceIntegrity=false;
}
this._saveInProgress=false;
},referenceIntegrity:true,_assert:function(_3){
if(!_3){
throw new Error("assertion failed in ItemFileWriteStore");
}
},_getIdentifierAttribute:function(){
var _4=this.getFeatures()["dojo.data.api.Identity"];
return _4;
},newItem:function(_5,_6){
this._assert(!this._saveInProgress);
if(!this._loadFinished){
this._forceLoad();
}
if(typeof _5!="object"&&typeof _5!="undefined"){
throw new Error("newItem() was passed something other than an object");
}
var _7=null;
var _8=this._getIdentifierAttribute();
if(_8===Number){
_7=this._arrayOfAllItems.length;
}else{
_7=_5[_8];
if(typeof _7==="undefined"){
throw new Error("newItem() was not passed an identity for the new item");
}
if(dojo.isArray(_7)){
throw new Error("newItem() was not passed an single-valued identity");
}
}
if(this._itemsByIdentity){
this._assert(typeof this._itemsByIdentity[_7]==="undefined");
}
this._assert(typeof this._pending._newItems[_7]==="undefined");
this._assert(typeof this._pending._deletedItems[_7]==="undefined");
var _9={};
_9[this._storeRefPropName]=this;
_9[this._itemNumPropName]=this._arrayOfAllItems.length;
if(this._itemsByIdentity){
this._itemsByIdentity[_7]=_9;
_9[_8]=[_7];
}
this._arrayOfAllItems.push(_9);
var _a=null;
if(_6&&_6.parent&&_6.attribute){
_a={item:_6.parent,attribute:_6.attribute,oldValue:undefined};
var _b=this.getValues(_6.parent,_6.attribute);
if(_b&&_b.length>0){
var _c=_b.slice(0,_b.length);
if(_b.length===1){
_a.oldValue=_b[0];
}else{
_a.oldValue=_b.slice(0,_b.length);
}
_c.push(_9);
this._setValueOrValues(_6.parent,_6.attribute,_c,false);
_a.newValue=this.getValues(_6.parent,_6.attribute);
}else{
this._setValueOrValues(_6.parent,_6.attribute,_9,false);
_a.newValue=_9;
}
}else{
_9[this._rootItemPropName]=true;
this._arrayOfTopLevelItems.push(_9);
}
this._pending._newItems[_7]=_9;
for(var _d in _5){
if(_d===this._storeRefPropName||_d===this._itemNumPropName){
throw new Error("encountered bug in ItemFileWriteStore.newItem");
}
var _e=_5[_d];
if(!dojo.isArray(_e)){
_e=[_e];
}
_9[_d]=_e;
if(this.referenceIntegrity){
for(var i=0;i<_e.length;i++){
var val=_e[i];
if(this.isItem(val)){
this._addReferenceToMap(val,_9,_d);
}
}
}
}
this.onNew(_9,_a);
return _9;
},_removeArrayElement:function(_11,_12){
var _13=dojo.indexOf(_11,_12);
if(_13!=-1){
_11.splice(_13,1);
return true;
}
return false;
},deleteItem:function(_14){
this._assert(!this._saveInProgress);
this._assertIsItem(_14);
var _15=_14[this._itemNumPropName];
var _16=this.getIdentity(_14);
if(this.referenceIntegrity){
var _17=this.getAttributes(_14);
if(_14[this._reverseRefMap]){
_14["backup_"+this._reverseRefMap]=dojo.clone(_14[this._reverseRefMap]);
}
dojo.forEach(_17,function(_18){
dojo.forEach(this.getValues(_14,_18),function(_19){
if(this.isItem(_19)){
if(!_14["backupRefs_"+this._reverseRefMap]){
_14["backupRefs_"+this._reverseRefMap]=[];
}
_14["backupRefs_"+this._reverseRefMap].push({id:this.getIdentity(_19),attr:_18});
this._removeReferenceFromMap(_19,_14,_18);
}
},this);
},this);
var _1a=_14[this._reverseRefMap];
if(_1a){
for(var _1b in _1a){
var _1c=null;
if(this._itemsByIdentity){
_1c=this._itemsByIdentity[_1b];
}else{
_1c=this._arrayOfAllItems[_1b];
}
if(_1c){
for(var _1d in _1a[_1b]){
var _1e=this.getValues(_1c,_1d)||[];
var _1f=dojo.filter(_1e,function(_20){
return !(this.isItem(_20)&&this.getIdentity(_20)==_16);
},this);
this._removeReferenceFromMap(_14,_1c,_1d);
if(_1f.length<_1e.length){
this._setValueOrValues(_1c,_1d,_1f);
}
}
}
}
}
}
this._arrayOfAllItems[_15]=null;
_14[this._storeRefPropName]=null;
if(this._itemsByIdentity){
delete this._itemsByIdentity[_16];
}
this._pending._deletedItems[_16]=_14;
if(_14[this._rootItemPropName]){
this._removeArrayElement(this._arrayOfTopLevelItems,_14);
}
this.onDelete(_14);
return true;
},setValue:function(_21,_22,_23){
return this._setValueOrValues(_21,_22,_23,true);
},setValues:function(_24,_25,_26){
return this._setValueOrValues(_24,_25,_26,true);
},unsetAttribute:function(_27,_28){
return this._setValueOrValues(_27,_28,[],true);
},_setValueOrValues:function(_29,_2a,_2b,_2c){
this._assert(!this._saveInProgress);
this._assertIsItem(_29);
this._assert(dojo.isString(_2a));
this._assert(typeof _2b!=="undefined");
var _2d=this._getIdentifierAttribute();
if(_2a==_2d){
throw new Error("ItemFileWriteStore does not have support for changing the value of an item's identifier.");
}
var _2e=this._getValueOrValues(_29,_2a);
var _2f=this.getIdentity(_29);
if(!this._pending._modifiedItems[_2f]){
var _30={};
for(var key in _29){
if((key===this._storeRefPropName)||(key===this._itemNumPropName)||(key===this._rootItemPropName)){
_30[key]=_29[key];
}else{
if(key===this._reverseRefMap){
_30[key]=dojo.clone(_29[key]);
}else{
_30[key]=_29[key].slice(0,_29[key].length);
}
}
}
this._pending._modifiedItems[_2f]=_30;
}
var _32=false;
if(dojo.isArray(_2b)&&_2b.length===0){
_32=delete _29[_2a];
_2b=undefined;
if(this.referenceIntegrity&&_2e){
var _33=_2e;
if(!dojo.isArray(_33)){
_33=[_33];
}
for(var i=0;i<_33.length;i++){
var _35=_33[i];
if(this.isItem(_35)){
this._removeReferenceFromMap(_35,_29,_2a);
}
}
}
}else{
var _36;
if(dojo.isArray(_2b)){
var _37=_2b;
_36=_2b.slice(0,_2b.length);
}else{
_36=[_2b];
}
if(this.referenceIntegrity){
if(_2e){
var _33=_2e;
if(!dojo.isArray(_33)){
_33=[_33];
}
var map={};
dojo.forEach(_33,function(_39){
if(this.isItem(_39)){
var id=this.getIdentity(_39);
map[id.toString()]=true;
}
},this);
dojo.forEach(_36,function(_3b){
if(this.isItem(_3b)){
var id=this.getIdentity(_3b);
if(map[id.toString()]){
delete map[id.toString()];
}else{
this._addReferenceToMap(_3b,_29,_2a);
}
}
},this);
for(var rId in map){
var _3e;
if(this._itemsByIdentity){
_3e=this._itemsByIdentity[rId];
}else{
_3e=this._arrayOfAllItems[rId];
}
this._removeReferenceFromMap(_3e,_29,_2a);
}
}else{
for(var i=0;i<_36.length;i++){
var _35=_36[i];
if(this.isItem(_35)){
this._addReferenceToMap(_35,_29,_2a);
}
}
}
}
_29[_2a]=_36;
_32=true;
}
if(_2c){
this.onSet(_29,_2a,_2e,_2b);
}
return _32;
},_addReferenceToMap:function(_3f,_40,_41){
var _42=this.getIdentity(_40);
var _43=_3f[this._reverseRefMap];
if(!_43){
_43=_3f[this._reverseRefMap]={};
}
var _44=_43[_42];
if(!_44){
_44=_43[_42]={};
}
_44[_41]=true;
},_removeReferenceFromMap:function(_45,_46,_47){
var _48=this.getIdentity(_46);
var _49=_45[this._reverseRefMap];
var _4a;
if(_49){
for(_4a in _49){
if(_4a==_48){
delete _49[_4a][_47];
if(this._isEmpty(_49[_4a])){
delete _49[_4a];
}
}
}
if(this._isEmpty(_49)){
delete _45[this._reverseRefMap];
}
}
},_dumpReferenceMap:function(){
var i;
for(i=0;i<this._arrayOfAllItems.length;i++){
var _4c=this._arrayOfAllItems[i];
if(_4c&&_4c[this._reverseRefMap]){

}
}
},_getValueOrValues:function(_4d,_4e){
var _4f=undefined;
if(this.hasAttribute(_4d,_4e)){
var _50=this.getValues(_4d,_4e);
if(_50.length==1){
_4f=_50[0];
}else{
_4f=_50;
}
}
return _4f;
},_flatten:function(_51){
if(this.isItem(_51)){
var _52=_51;
var _53=this.getIdentity(_52);
var _54={_reference:_53};
return _54;
}else{
if(typeof _51==="object"){
for(var _55 in this._datatypeMap){
var _56=this._datatypeMap[_55];
if(dojo.isObject(_56)&&!dojo.isFunction(_56)){
if(_51 instanceof _56.type){
if(!_56.serialize){
throw new Error("ItemFileWriteStore:  No serializer defined for type mapping: ["+_55+"]");
}
return {_type:_55,_value:_56.serialize(_51)};
}
}else{
if(_51 instanceof _56){
return {_type:_55,_value:_51.toString()};
}
}
}
}
return _51;
}
},_getNewFileContentString:function(){
var _57={};
var _58=this._getIdentifierAttribute();
if(_58!==Number){
_57.identifier=_58;
}
if(this._labelAttr){
_57.label=this._labelAttr;
}
_57.items=[];
for(var i=0;i<this._arrayOfAllItems.length;++i){
var _5a=this._arrayOfAllItems[i];
if(_5a!==null){
var _5b={};
for(var key in _5a){
if(key!==this._storeRefPropName&&key!==this._itemNumPropName&&key!==this._reverseRefMap&&key!==this._rootItemPropName){
var _5d=key;
var _5e=this.getValues(_5a,_5d);
if(_5e.length==1){
_5b[_5d]=this._flatten(_5e[0]);
}else{
var _5f=[];
for(var j=0;j<_5e.length;++j){
_5f.push(this._flatten(_5e[j]));
_5b[_5d]=_5f;
}
}
}
}
_57.items.push(_5b);
}
}
var _61=true;
return dojo.toJson(_57,_61);
},_isEmpty:function(_62){
var _63=true;
if(dojo.isObject(_62)){
var i;
for(i in _62){
_63=false;
break;
}
}else{
if(dojo.isArray(_62)){
if(_62.length>0){
_63=false;
}
}
}
return _63;
},save:function(_65){
this._assert(!this._saveInProgress);
this._saveInProgress=true;
var _66=this;
var _67=function(){
_66._pending={_newItems:{},_modifiedItems:{},_deletedItems:{}};
_66._saveInProgress=false;
if(_65&&_65.onComplete){
var _68=_65.scope||dojo.global;
_65.onComplete.call(_68);
}
};
var _69=function(){
_66._saveInProgress=false;
if(_65&&_65.onError){
var _6a=_65.scope||dojo.global;
_65.onError.call(_6a);
}
};
if(this._saveEverything){
var _6b=this._getNewFileContentString();
this._saveEverything(_67,_69,_6b);
}
if(this._saveCustom){
this._saveCustom(_67,_69);
}
if(!this._saveEverything&&!this._saveCustom){
_67();
}
},revert:function(){
this._assert(!this._saveInProgress);
var _6c;
for(_6c in this._pending._modifiedItems){
var _6d=this._pending._modifiedItems[_6c];
var _6e=null;
if(this._itemsByIdentity){
_6e=this._itemsByIdentity[_6c];
}else{
_6e=this._arrayOfAllItems[_6c];
}
_6d[this._storeRefPropName]=this;
_6e[this._storeRefPropName]=null;
var _6f=_6e[this._itemNumPropName];
this._arrayOfAllItems[_6f]=_6d;
if(_6e[this._rootItemPropName]){
var i;
for(i=0;i<this._arrayOfTopLevelItems.length;i++){
var _71=this._arrayOfTopLevelItems[i];
if(this.getIdentity(_71)==_6c){
this._arrayOfTopLevelItems[i]=_6d;
break;
}
}
}
if(this._itemsByIdentity){
this._itemsByIdentity[_6c]=_6d;
}
}
var _72;
for(_6c in this._pending._deletedItems){
_72=this._pending._deletedItems[_6c];
_72[this._storeRefPropName]=this;
var _73=_72[this._itemNumPropName];
if(_72["backup_"+this._reverseRefMap]){
_72[this._reverseRefMap]=_72["backup_"+this._reverseRefMap];
delete _72["backup_"+this._reverseRefMap];
}
this._arrayOfAllItems[_73]=_72;
if(this._itemsByIdentity){
this._itemsByIdentity[_6c]=_72;
}
if(_72[this._rootItemPropName]){
this._arrayOfTopLevelItems.push(_72);
}
}
for(_6c in this._pending._deletedItems){
_72=this._pending._deletedItems[_6c];
if(_72["backupRefs_"+this._reverseRefMap]){
dojo.forEach(_72["backupRefs_"+this._reverseRefMap],function(_74){
var _75;
if(this._itemsByIdentity){
_75=this._itemsByIdentity[_74.id];
}else{
_75=this._arrayOfAllItems[_74.id];
}
this._addReferenceToMap(_75,_72,_74.attr);
},this);
delete _72["backupRefs_"+this._reverseRefMap];
}
}
for(_6c in this._pending._newItems){
var _76=this._pending._newItems[_6c];
_76[this._storeRefPropName]=null;
this._arrayOfAllItems[_76[this._itemNumPropName]]=null;
if(_76[this._rootItemPropName]){
this._removeArrayElement(this._arrayOfTopLevelItems,_76);
}
if(this._itemsByIdentity){
delete this._itemsByIdentity[_6c];
}
}
this._pending={_newItems:{},_modifiedItems:{},_deletedItems:{}};
return true;
},isDirty:function(_77){
if(_77){
var _78=this.getIdentity(_77);
return new Boolean(this._pending._newItems[_78]||this._pending._modifiedItems[_78]||this._pending._deletedItems[_78]).valueOf();
}else{
if(!this._isEmpty(this._pending._newItems)||!this._isEmpty(this._pending._modifiedItems)||!this._isEmpty(this._pending._deletedItems)){
return true;
}
return false;
}
},onSet:function(_79,_7a,_7b,_7c){
},onNew:function(_7d,_7e){
},onDelete:function(_7f){
},close:function(_80){
if(this.clearOnClose){
if(!this.isDirty()){
this.inherited(arguments);
}else{
if(this._jsonFileUrl!==""){
throw new Error("dojox.data.AndOrWriteStore: There are unsaved changes present in the store.  Please save or revert the changes before invoking close.");
}
}
}
}});
}
