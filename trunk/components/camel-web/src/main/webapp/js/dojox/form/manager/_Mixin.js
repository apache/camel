/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.manager._Mixin"]){
dojo._hasResource["dojox.form.manager._Mixin"]=true;
dojo.provide("dojox.form.manager._Mixin");
dojo.require("dijit._Widget");
(function(){
var fm=dojox.form.manager,aa=fm.actionAdapter=function(_3){
return function(_4,_5,_6){
if(dojo.isArray(_5)){
dojo.forEach(_5,function(_7){
_3.call(this,_4,_7,_6);
},this);
}else{
_3.apply(this,arguments);
}
};
},ia=fm.inspectorAdapter=function(_9){
return function(_a,_b,_c){
return _9.call(this,_a,dojo.isArray(_b)?_b[0]:_b,_c);
};
},_d={domNode:1,containerNode:1,srcNodeRef:1,bgIframe:1},_e=fm._keys=function(o){
var _10=[],key;
for(key in o){
if(o.hasOwnProperty(key)){
_10.push(key);
}
}
return _10;
},_12=function(_13){
var _14=_13.attr("name");
if(_14&&_13 instanceof dijit.form._FormWidget){
if(_14 in this.formWidgets){
var a=this.formWidgets[_14].widget;
if(dojo.isArray(a)){
a.push(_13);
}else{
this.formWidgets[_14].widget=[a,_13];
}
}else{
this.formWidgets[_14]={widget:_13,connections:[]};
}
}else{
_14=null;
}
return _14;
},_16=function(_17){
var _18={};
aa(function(_,w){
var o=w.attr("observer");
if(o&&typeof o=="string"){
dojo.forEach(o.split(","),function(o){
o=dojo.trim(o);
if(o&&dojo.isFunction(this[o])){
_18[o]=1;
}
},this);
}
}).call(this,null,this.formWidgets[_17].widget);
return _e(_18);
},_1d=function(_1e,_1f){
var t=this.formWidgets[_1e],w=t.widget,c=t.connections;
if(c.length){
dojo.forEach(c,dojo.disconnect);
c=t.connections=[];
}
if(dojo.isArray(w)){
dojo.forEach(w,function(w){
dojo.forEach(_1f,function(o){
c.push(dojo.connect(w,"onChange",this,function(evt){
if(this.watch&&dojo.attr(w.focusNode,"checked")){
this[o](w.attr("value"),_1e,w,evt);
}
}));
},this);
},this);
}else{
var _26=w.declaredClass=="dijit.form.Button"?"onClick":"onChange";
dojo.forEach(_1f,function(o){
c.push(dojo.connect(w,_26,this,function(evt){
if(this.watch){
this[o](w.attr("value"),_1e,w,evt);
}
}));
},this);
}
};
dojo.declare("dojox.form.manager._Mixin",null,{watch:true,startup:function(){
if(this._started){
return;
}
this.formWidgets={};
this.formNodes={};
this.registerWidgetDescendants(this);
this.inherited(arguments);
},destroy:function(){
for(var _29 in this.formWidgets){
dojo.forEach(this.formWidgets[_29].connections,dojo.disconnect);
}
this.formWidgets={};
this.inherited(arguments);
},registerWidget:function(_2a){
if(typeof _2a=="string"){
_2a=dijit.byId(_2a);
}else{
if(_2a.tagName&&_2a.cloneNode){
_2a=dijit.byNode(_2a);
}
}
var _2b=_12.call(this,_2a);
if(_2b){
_1d.call(this,_2b,_16.call(this,_2b));
}
return this;
},unregisterWidget:function(_2c){
if(_2c in this.formWidgets){
dojo.forEach(this.formWidgets[_2c].connections,this.disconnect,this);
delete this.formWidgets[_2c];
}
return this;
},registerWidgetDescendants:function(_2d){
if(typeof _2d=="string"){
_2d=dijit.byId(_2d);
}else{
if(_2d.tagName&&_2d.cloneNode){
_2d=dijit.byNode(_2d);
}
}
var _2e=dojo.map(_2d.getDescendants(),_12,this);
dojo.forEach(_2e,function(_2f){
if(_2f){
_1d.call(this,_2f,_16.call(this,_2f));
}
},this);
return this.registerNodeDescendants?this.registerNodeDescendants(_2d.domNode):this;
},unregisterWidgetDescendants:function(_30){
if(typeof _30=="string"){
_30=dijit.byId(_30);
}else{
if(_30.tagName&&_30.cloneNode){
_30=dijit.byNode(_30);
}
}
dojo.forEach(dojo.map(_30.getDescendants(),function(w){
return w instanceof dijit.form._FormWidget&&w.attr("name")||null;
}),function(_32){
if(_32){
this.unregisterNode(_32);
}
},this);
return this.unregisterNodeDescendants?this.unregisterNodeDescendants(_30.domNode):this;
},formWidgetValue:function(_33,_34){
var _35=arguments.length==2&&_34!==undefined,_36;
if(typeof _33=="string"){
_33=this.formWidgets[_33];
if(_33){
_33=_33.widget;
}
}
if(!_33){
return null;
}
if(dojo.isArray(_33)){
if(_35){
dojo.forEach(_33,function(_37){
_37.attr("checked",false);
});
dojo.forEach(_33,function(_38){
_38.attr("checked",_38.attr("value")===_34);
});
return this;
}
dojo.some(_33,function(_39){
if(dojo.attr(_39.focusNode,"checked")){
_36=_39;
return true;
}
return false;
});
return _36?_36.attr("value"):"";
}
if(_35){
_33.attr("value",_34);
return this;
}
return _33.attr("value");
},formPointValue:function(_3a,_3b){
if(_3a&&typeof _3a=="string"){
_3a=this[_3a];
}
if(!_3a||!_3a.tagName||!_3a.cloneNode){
return null;
}
if(!dojo.hasClass(_3a,"dojoFormValue")){
return null;
}
if(arguments.length==2&&_3b!==undefined){
_3a.innerHTML=_3b;
return this;
}
return _3a.innerHTML;
},inspectFormWidgets:function(_3c,_3d,_3e){
var _3f,_40={};
if(_3d){
if(dojo.isArray(_3d)){
dojo.forEach(_3d,function(_41){
if(_41 in this.formWidgets){
_40[_41]=_3c.call(this,_41,this.formWidgets[_41].widget,_3e);
}
},this);
}else{
for(_3f in _3d){
if(_3f in this.formWidgets){
_40[_3f]=_3c.call(this,_3f,this.formWidgets[_3f].widget,_3d[_3f]);
}
}
}
}else{
for(_3f in this.formWidgets){
_40[_3f]=_3c.call(this,_3f,this.formWidgets[_3f].widget,_3e);
}
}
return _40;
},inspectAttachedPoints:function(_42,_43,_44){
var _45,_46={};
if(_43){
if(dojo.isArray(_43)){
dojo.forEach(_43,function(_47){
var _48=this[_47];
if(_48&&_48.tagName&&_48.cloneNode){
_46[_47]=_42.call(this,_47,_48,_44);
}
},this);
}else{
for(_45 in _43){
var _49=this[_45];
if(_49&&_49.tagName&&_49.cloneNode){
_46[_45]=_42.call(this,_45,_49,_43[_45]);
}
}
}
}else{
for(_45 in this){
if(!(_45 in _d)){
var _49=this[_45];
if(_49&&_49.tagName&&_49.cloneNode){
_46[_45]=_42.call(this,_45,_49,_44);
}
}
}
}
return _46;
},inspect:function(_4a,_4b,_4c){
var _4d=this.inspectFormWidgets(function(_4e,_4f,_50){
if(dojo.isArray(_4f)){
return _4a.call(this,_4e,dojo.map(_4f,function(w){
return w.domNode;
}),_50);
}
return _4a.call(this,_4e,_4f.domNode,_50);
},_4b,_4c);
if(this.inspectFormNodes){
dojo.mixin(_4d,this.inspectFormNodes(_4a,_4b,_4c));
}
return dojo.mixin(_4d,this.inspectAttachedPoints(_4a,_4b,_4c));
}});
})();
dojo.extend(dijit._Widget,{observer:""});
}
