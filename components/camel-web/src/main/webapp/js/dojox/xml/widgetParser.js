/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.xml.widgetParser"]){
dojo._hasResource["dojox.xml.widgetParser"]=true;
dojo.provide("dojox.xml.widgetParser");
dojo.require("dojox.xml.parser");
dojo.require("dojo.parser");
dojox.xml.widgetParser=new function(){
var d=dojo;
this.parseNode=function(_2){
var _3=[];
d.query("script[type='text/xml']",_2).forEach(function(_4){
_3.push.apply(_3,this._processScript(_4));
},this).orphan();
return d.parser.instantiate(_3);
};
this._processScript=function(_5){
var _6=_5.src?d._getText(_5.src):_5.innerHTML||_5.firstChild.nodeValue;
var _7=this.toHTML(dojox.xml.parser.parse(_6).firstChild);
var _8=d.query("[dojoType]",_7);
dojo.query(">",_7).place(_5,"before");
_5.parentNode.removeChild(_5);
return _8;
};
this.toHTML=function(_9){
var _a;
var _b=_9.nodeName;
var dd=dojo.doc;
var _d=_9.nodeType;
if(_d>=3){
return dd.createTextNode((_d==3||_d==4)?_9.nodeValue:"");
}
var _e=_9.localName||_b.split(":").pop();
var _f=_9.namespaceURI||(_9.getNamespaceUri?_9.getNamespaceUri():"");
if(_f=="html"){
_a=dd.createElement(_e);
}else{
var _10=_f+"."+_e;
_a=_a||dd.createElement((_10=="dijit.form.ComboBox")?"select":"div");
_a.setAttribute("dojoType",_10);
}
d.forEach(_9.attributes,function(_11){
var _12=_11.name||_11.nodeName;
var _13=_11.value||_11.nodeValue;
if(_12.indexOf("xmlns")!=0){
if(dojo.isIE&&_12=="style"){
_a.style.setAttribute("cssText",_13);
}else{
_a.setAttribute(_12,_13);
}
}
});
d.forEach(_9.childNodes,function(cn){
var _15=this.toHTML(cn);
if(_e=="script"){
_a.text+=_15.nodeValue;
}else{
_a.appendChild(_15);
}
},this);
return _a;
};
}();
}
