/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.contrib.dom"]){
dojo._hasResource["dojox.dtl.contrib.dom"]=true;
dojo.provide("dojox.dtl.contrib.dom");
dojo.require("dojox.dtl.dom");
(function(){
var dd=dojox.dtl;
var _2=dd.contrib.dom;
var _3={render:function(){
return this.contents;
}};
_2.StyleNode=dojo.extend(function(_4){
this.contents={};
this._current={};
this._styles=_4;
for(var _5 in _4){
if(_4[_5].indexOf("{{")!=-1){
var _6=new dd.Template(_4[_5]);
}else{
var _6=dojo.delegate(_3);
_6.contents=_4[_5];
}
this.contents[_5]=_6;
}
},{render:function(_7,_8){
for(var _9 in this.contents){
var _a=this.contents[_9].render(_7);
if(this._current[_9]!=_a){
dojo.style(_8.getParent(),_9,this._current[_9]=_a);
}
}
return _8;
},unrender:function(_b,_c){
this._current={};
return _c;
},clone:function(_d){
return new this.constructor(this._styles);
}});
_2.BufferNode=dojo.extend(function(_e,_f){
this.nodelist=_e;
this.options=_f;
},{_swap:function(_10,_11){
if(!this.swapped&&this.parent.parentNode){
if(_10=="node"){
if((_11.nodeType==3&&!this.options.text)||(_11.nodeType==1&&!this.options.node)){
return;
}
}else{
if(_10=="class"){
if(_10!="class"){
return;
}
}
}
this.onAddNode&&dojo.disconnect(this.onAddNode);
this.onRemoveNode&&dojo.disconnect(this.onRemoveNode);
this.onChangeAttribute&&dojo.disconnect(this.onChangeAttribute);
this.onChangeData&&dojo.disconnect(this.onChangeData);
this.swapped=this.parent.cloneNode(true);
this.parent.parentNode.replaceChild(this.swapped,this.parent);
}
},render:function(_12,_13){
this.parent=_13.getParent();
if(this.options.node){
this.onAddNode=dojo.connect(_13,"onAddNode",dojo.hitch(this,"_swap","node"));
this.onRemoveNode=dojo.connect(_13,"onRemoveNode",dojo.hitch(this,"_swap","node"));
}
if(this.options.text){
this.onChangeData=dojo.connect(_13,"onChangeData",dojo.hitch(this,"_swap","node"));
}
if(this.options["class"]){
this.onChangeAttribute=dojo.connect(_13,"onChangeAttribute",dojo.hitch(this,"_swap","class"));
}
_13=this.nodelist.render(_12,_13);
if(this.swapped){
this.swapped.parentNode.replaceChild(this.parent,this.swapped);
dojo.destroy(this.swapped);
}else{
this.onAddNode&&dojo.disconnect(this.onAddNode);
this.onRemoveNode&&dojo.disconnect(this.onRemoveNode);
this.onChangeAttribute&&dojo.disconnect(this.onChangeAttribute);
this.onChangeData&&dojo.disconnect(this.onChangeData);
}
delete this.parent;
delete this.swapped;
return _13;
},unrender:function(_14,_15){
return this.nodelist.unrender(_14,_15);
},clone:function(_16){
return new this.constructor(this.nodelist.clone(_16),this.options);
}});
dojo.mixin(_2,{buffer:function(_17,_18){
var _19=_18.contents.split().slice(1);
var _1a={};
var _1b=false;
for(var i=_19.length;i--;){
_1b=true;
_1a[_19[i]]=true;
}
if(!_1b){
_1a.node=true;
}
var _1d=_17.parse(["endbuffer"]);
_17.next_token();
return new _2.BufferNode(_1d,_1a);
},html:function(_1e,_1f){
dojo.deprecated("{% html someVariable %}","Use {{ someVariable|safe }} instead");
return _1e.create_variable_node(_1f.contents.slice(5)+"|safe");
},style_:function(_20,_21){
var _22={};
_21=_21.contents.replace(/^style\s+/,"");
var _23=_21.split(/\s*;\s*/g);
for(var i=0,_25;_25=_23[i];i++){
var _26=_25.split(/\s*:\s*/g);
var key=_26[0];
var _28=dojo.trim(_26[1]);
if(_28){
_22[key]=_28;
}
}
return new _2.StyleNode(_22);
}});
dd.register.tags("dojox.dtl.contrib",{"dom":["html","attr:style","buffer"]});
})();
}
