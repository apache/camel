/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.manager._NodeMixin"]){
dojo._hasResource["dojox.form.manager._NodeMixin"]=true;
dojo.provide("dojox.form.manager._NodeMixin");
dojo.require("dojox.form.manager._Mixin");
(function(){
var fm=dojox.form.manager,aa=fm.actionAdapter,_3=fm._keys,ce=fm.changeEvent=function(_5){
var _6="onclick";
switch(_5.tagName.toLowerCase()){
case "textarea":
_6="onkeyup";
break;
case "select":
_6="onchange";
break;
case "input":
switch(_5.type.toLowerCase()){
case "text":
case "password":
_6="onkeyup";
break;
}
break;
}
return _6;
},_7=function(_8,_9){
var _a=dojo.attr(_8,"name");
_9=_9||this.domNode;
if(_a&&!(_a in this.formWidgets)){
for(var n=_8;n&&n!==_9;n=n.parentNode){
if(dojo.attr(n,"widgetId")&&dijit.byNode(n) instanceof dijit.form._FormWidget){
return null;
}
}
if(_8.tagName.toLowerCase()=="input"&&_8.type.toLowerCase()=="radio"){
var a=this.formNodes[_a];
a=a&&a.node;
if(a&&dojo.isArray(a)){
a.push(_8);
}else{
this.formNodes[_a]={node:[_8],connections:[]};
}
}else{
this.formNodes[_a]={node:_8,connections:[]};
}
}else{
_a=null;
}
return _a;
},_d=function(_e){
var _f={};
aa(function(_,n){
var o=dojo.attr(n,"observer");
if(o&&typeof o=="string"){
dojo.forEach(o.split(","),function(o){
o=dojo.trim(o);
if(o&&dojo.isFunction(this[o])){
_f[o]=1;
}
},this);
}
}).call(this,null,this.formNodes[_e].node);
return _3(_f);
},_14=function(_15,_16){
var t=this.formNodes[_15],c=t.connections;
if(c.length){
dojo.forEach(c,dojo.disconnect);
c=t.connections=[];
}
aa(function(_,n){
var _1b=ce(n);
dojo.forEach(_16,function(o){
c.push(dojo.connect(n,_1b,this,function(evt){
if(this.watch){
this[o](this.formNodeValue(_15),_15,n,evt);
}
}));
},this);
}).call(this,null,t.node);
};
dojo.declare("dojox.form.manager._NodeMixin",null,{destroy:function(){
for(var _1e in this.formNodes){
dojo.forEach(this.formNodes[_1e].connections,dojo.disconnect);
}
this.formNodes={};
this.inherited(arguments);
},registerNode:function(_1f){
if(typeof _1f=="string"){
_1f=dojo.byId(_1f);
}
var _20=_7.call(this,_1f);
if(_20){
_14.call(this,_20,_d.call(this,_20));
}
return this;
},unregisterNode:function(_21){
if(_21 in this.formNodes){
dojo.forEach(this.formNodes[_21].connections,this.disconnect,this);
delete this.formNodes[_21];
}
return this;
},registerNodeDescendants:function(_22){
if(typeof _22=="string"){
_22=dojo.byId(_22);
}
dojo.query("input, select, textarea, button",_22).map(function(n){
return _7.call(this,n,_22);
},this).forEach(function(_24){
if(_24){
_14.call(this,_24,_d.call(this,_24));
}
},this);
return this;
},unregisterNodeDescendants:function(_25){
if(typeof _25=="string"){
_25=dojo.byId(_25);
}
dojo.query("input, select, textarea, button",_25).map(function(n){
return dojo.attr(_25,"name")||null;
}).forEach(function(_27){
if(_27){
this.unregisterNode(_27);
}
},this);
return this;
},formNodeValue:function(_28,_29){
var _2a=arguments.length==2&&_29!==undefined,_2b;
if(typeof _28=="string"){
_28=this.formNodes[_28];
if(_28){
_28=_28.node;
}
}
if(!_28){
return null;
}
if(dojo.isArray(_28)){
if(_2a){
dojo.forEach(_28,function(_2c){
_2c.checked="";
});
dojo.forEach(_28,function(_2d){
_2d.checked=_2d.value===_29?"checked":"";
});
return this;
}
dojo.some(_28,function(_2e){
if(_2e.checked){
_2b=_2e;
return true;
}
return false;
});
return _2b?_2b.value:"";
}
switch(_28.tagName.toLowerCase()){
case "select":
if(_28.multiple){
if(_2a){
if(dojo.isArray(_29)){
var _2f={};
dojo.forEach(_29,function(v){
_2f[v]=1;
});
dojo.query("> option",_28).forEach(function(opt){
opt.selected=opt.value in _2f;
});
return this;
}
dojo.query("> option",_28).forEach(function(opt){
opt.selected=opt.value===_29;
});
return this;
}
var _2b=dojo.query("> option",_28).filter(function(opt){
return opt.selected;
}).map(function(opt){
return opt.value;
});
return _2b.length==1?_2b[0]:_2b;
}
if(_2a){
dojo.query("> option",_28).forEach(function(opt){
opt.selected=opt.value===_29;
});
return this;
}
return _28.value||"";
case "button":
if(_2a){
_28.innerHTML=""+_29;
return this;
}
return _28.innerHTML;
case "input":
if(_28.type.toLowerCase()=="checkbox"){
if(_2a){
_28.checked=_29?"checked":"";
return this;
}
return Boolean(_28.checked);
}
}
if(_2a){
_28.value=""+_29;
return this;
}
return _28.value;
},inspectFormNodes:function(_36,_37,_38){
var _39,_3a={};
if(_37){
if(dojo.isArray(_37)){
dojo.forEach(_37,function(_3b){
if(_3b in this.formNodes){
_3a[_3b]=_36.call(this,_3b,this.formNodes[_3b].node,_38);
}
},this);
}else{
for(_39 in _37){
if(_39 in this.formNodes){
_3a[_39]=_36.call(this,_39,this.formNodes[_39].node,_37[_39]);
}
}
}
}else{
for(_39 in this.formNodes){
_3a[_39]=_36.call(this,_39,this.formNodes[_39].node,_38);
}
}
return _3a;
}});
})();
}
