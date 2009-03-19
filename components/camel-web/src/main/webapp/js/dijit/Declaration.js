/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.Declaration"]){
dojo._hasResource["dijit.Declaration"]=true;
dojo.provide("dijit.Declaration");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.declare("dijit.Declaration",dijit._Widget,{_noScript:true,widgetClass:"",defaults:null,mixins:[],buildRendering:function(){
var _1=this.srcNodeRef.parentNode.removeChild(this.srcNodeRef),_2=dojo.query("> script[type^='dojo/method'][event]",_1).orphan(),_3=dojo.query("> script[type^='dojo/method']",_1).orphan(),_4=dojo.query("> script[type^='dojo/connect']",_1).orphan(),_5=_1.nodeName;
var _6=this.defaults||{};
dojo.forEach(_2,function(s){
var _8=s.getAttribute("event"),_9=dojo.parser._functionFromScript(s);
_6[_8]=_9;
});
this.mixins=this.mixins.length?dojo.map(this.mixins,function(_a){
return dojo.getObject(_a);
}):[dijit._Widget,dijit._Templated];
_6.widgetsInTemplate=true;
_6._skipNodeCache=true;
_6.templateString="<"+_5+" class='"+_1.className+"' dojoAttachPoint='"+(_1.getAttribute("dojoAttachPoint")||"")+"' dojoAttachEvent='"+(_1.getAttribute("dojoAttachEvent")||"")+"' >"+_1.innerHTML.replace(/\%7B/g,"{").replace(/\%7D/g,"}")+"</"+_5+">";
dojo.query("[dojoType]",_1).forEach(function(_b){
_b.removeAttribute("dojoType");
});
var wc=dojo.declare(this.widgetClass,this.mixins,_6);
var _d=_4.concat(_3);
dojo.forEach(_d,function(s){
var _f=s.getAttribute("event")||"postscript",_10=dojo.parser._functionFromScript(s);
dojo.connect(wc.prototype,_f,_10);
});
}});
}
