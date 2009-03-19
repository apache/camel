/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._Templated"]){
dojo._hasResource["dijit._Templated"]=true;
dojo.provide("dijit._Templated");
dojo.require("dijit._Widget");
dojo.require("dojo.string");
dojo.require("dojo.parser");
dojo.declare("dijit._Templated",null,{templateString:null,templatePath:null,widgetsInTemplate:false,_skipNodeCache:false,_stringRepl:function(_1){
var _2=this.declaredClass,_3=this;
return dojo.string.substitute(_1,this,function(_4,_5){
if(_5.charAt(0)=="!"){
_4=dojo.getObject(_5.substr(1),_3);
}
if(typeof _4=="undefined"){
throw new Error(_2+" template:"+_5);
}
if(_4==null){
return "";
}
return _5.charAt(0)=="!"?_4:_4.toString().replace(/"/g,"&quot;");
},this);
},buildRendering:function(){
var _6=dijit._Templated.getCachedTemplate(this.templatePath,this.templateString,this._skipNodeCache);
var _7;
if(dojo.isString(_6)){
_7=dojo._toDom(this._stringRepl(_6));
}else{
_7=_6.cloneNode(true);
}
this.domNode=_7;
this._attachTemplateNodes(_7);
if(this.widgetsInTemplate){
var cw=(this._supportingWidgets=dojo.parser.parse(_7));
this._attachTemplateNodes(cw,function(n,p){
return n[p];
});
}
this._fillContent(this.srcNodeRef);
},_fillContent:function(_b){
var _c=this.containerNode;
if(_b&&_c){
while(_b.hasChildNodes()){
_c.appendChild(_b.firstChild);
}
}
},_attachTemplateNodes:function(_d,_e){
_e=_e||function(n,p){
return n.getAttribute(p);
};
var _11=dojo.isArray(_d)?_d:(_d.all||_d.getElementsByTagName("*"));
var x=dojo.isArray(_d)?0:-1;
for(;x<_11.length;x++){
var _13=(x==-1)?_d:_11[x];
if(this.widgetsInTemplate&&_e(_13,"dojoType")){
continue;
}
var _14=_e(_13,"dojoAttachPoint");
if(_14){
var _15,_16=_14.split(/\s*,\s*/);
while((_15=_16.shift())){
if(dojo.isArray(this[_15])){
this[_15].push(_13);
}else{
this[_15]=_13;
}
}
}
var _17=_e(_13,"dojoAttachEvent");
if(_17){
var _18,_19=_17.split(/\s*,\s*/);
var _1a=dojo.trim;
while((_18=_19.shift())){
if(_18){
var _1b=null;
if(_18.indexOf(":")!=-1){
var _1c=_18.split(":");
_18=_1a(_1c[0]);
_1b=_1a(_1c[1]);
}else{
_18=_1a(_18);
}
if(!_1b){
_1b=_18;
}
this.connect(_13,_18,_1b);
}
}
}
var _1d=_e(_13,"waiRole");
if(_1d){
dijit.setWaiRole(_13,_1d);
}
var _1e=_e(_13,"waiState");
if(_1e){
dojo.forEach(_1e.split(/\s*,\s*/),function(_1f){
if(_1f.indexOf("-")!=-1){
var _20=_1f.split("-");
dijit.setWaiState(_13,_20[0],_20[1]);
}
});
}
}
}});
dijit._Templated._templateCache={};
dijit._Templated.getCachedTemplate=function(_21,_22,_23){
var _24=dijit._Templated._templateCache;
var key=_22||_21;
var _26=_24[key];
if(_26){
if(!_26.ownerDocument||_26.ownerDocument==dojo.doc){
return _26;
}
dojo.destroy(_26);
}
if(!_22){
_22=dijit._Templated._sanitizeTemplateString(dojo.trim(dojo._getText(_21)));
}
_22=dojo.string.trim(_22);
if(_23||_22.match(/\$\{([^\}]+)\}/g)){
return (_24[key]=_22);
}else{
return (_24[key]=dojo._toDom(_22));
}
};
dijit._Templated._sanitizeTemplateString=function(_27){
if(_27){
_27=_27.replace(/^\s*<\?xml(\s)+version=[\'\"](\d)*.(\d)*[\'\"](\s)*\?>/im,"");
var _28=_27.match(/<body[^>]*>\s*([\s\S]+)\s*<\/body>/im);
if(_28){
_27=_28[1];
}
}else{
_27="";
}
return _27;
};
if(dojo.isIE){
dojo.addOnWindowUnload(function(){
var _29=dijit._Templated._templateCache;
for(var key in _29){
var _2b=_29[key];
if(!isNaN(_2b.nodeType)){
dojo.destroy(_2b);
}
delete _29[key];
}
});
}
dojo.extend(dijit._Widget,{dojoAttachEvent:"",dojoAttachPoint:"",waiRole:"",waiState:""});
}
