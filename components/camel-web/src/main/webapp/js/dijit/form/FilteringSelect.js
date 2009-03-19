/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.FilteringSelect"]){
dojo._hasResource["dijit.form.FilteringSelect"]=true;
dojo.provide("dijit.form.FilteringSelect");
dojo.require("dijit.form.ComboBox");
dojo.declare("dijit.form.FilteringSelect",[dijit.form.MappedTextBox,dijit.form.ComboBoxMixin],{_isvalid:true,required:true,_lastDisplayedValue:"",isValid:function(){
return this._isvalid||(!this.required&&this.attr("displayedValue")=="");
},_callbackSetLabel:function(_1,_2,_3){
if((_2&&_2.query[this.searchAttr]!=this._lastQuery)||(!_2&&_1.length&&this.store.getIdentity(_1[0])!=this._lastQuery)){
return;
}
if(!_1.length){
if(_3||!this._focused){
this.valueNode.value="";
}
dijit.form.TextBox.superclass._setValueAttr.call(this,"",_3||!this._focused);
this._isvalid=false;
this.validate(this._focused);
this.item=null;
}else{
this._setValueFromItem(_1[0],_3);
}
},_openResultList:function(_4,_5){
if(_5.query[this.searchAttr]!=this._lastQuery){
return;
}
this._isvalid=_4.length!=0;
this.validate(true);
dijit.form.ComboBoxMixin.prototype._openResultList.apply(this,arguments);
},_getValueAttr:function(){
return this.valueNode.value;
},_getValueField:function(){
return "value";
},_setValue:function(_6,_7,_8){
this.valueNode.value=_6;
dijit.form.FilteringSelect.superclass._setValueAttr.call(this,_6,_8,_7);
this._lastDisplayedValue=_7;
},_setValueAttr:function(_9,_a){
if(!this._onChangeActive){
_a=null;
}
this._lastQuery=_9;
if(_9===null||_9===""){
this._setDisplayedValueAttr("",_a);
return;
}
var _b=this;
var _c=function(_d,_e){
if(_d){
if(_b.store.isItemLoaded(_d)){
_b._callbackSetLabel([_d],undefined,_e);
}else{
_b.store.loadItem({item:_d,onItem:function(_f,_10){
_b._callbackSetLabel(_f,_10,_e);
}});
}
}else{
_b._isvalid=false;
_b.validate(false);
}
};
this.store.fetchItemByIdentity({identity:_9,onItem:function(_11){
_c(_11,_a);
}});
},_setValueFromItem:function(_12,_13){
this._isvalid=true;
this.item=_12;
this._setValue(this.store.getIdentity(_12),this.labelFunc(_12,this.store),_13);
},labelFunc:function(_14,_15){
return _15.getValue(_14,this.searchAttr);
},_doSelect:function(tgt){
this._setValueFromItem(tgt.item,true);
},_setDisplayedValueAttr:function(_17,_18){
if(!this._created){
_18=false;
}
if(this.store){
var _19=dojo.clone(this.query);
this._lastQuery=_19[this.searchAttr]=_17.replace(/([\\\*\?])/g,"\\$1");
this.textbox.value=_17;
this._lastDisplayedValue=_17;
var _1a=this;
var _1b={query:_19,queryOptions:{ignoreCase:this.ignoreCase,deep:true},onComplete:function(_1c,_1d){
dojo.hitch(_1a,"_callbackSetLabel")(_1c,_1d,_18);
},onError:function(_1e){
console.error("dijit.form.FilteringSelect: "+_1e);
dojo.hitch(_1a,"_setValue")("",_17,false);
}};
dojo.mixin(_1b,this.fetchProperties);
this.store.fetch(_1b);
}
},postMixInProperties:function(){
this.inherited(arguments);
this._isvalid=!this.required;
},undo:function(){
this.attr("displayedValue",this._lastDisplayedValue);
}});
}
