/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.popup"]){
dojo._hasResource["dijit._base.popup"]=true;
dojo.provide("dijit._base.popup");
dojo.require("dijit._base.focus");
dojo.require("dijit._base.place");
dojo.require("dijit._base.window");
dijit.popup=new function(){
var _1=[],_2=1000,_3=1;
this.prepare=function(_4){
var s=_4.style;
s.visibility="hidden";
s.position="absolute";
s.top="-9999px";
if(s.display=="none"){
s.display="";
}
dojo.body().appendChild(_4);
};
this.open=function(_6){
var _7=_6.popup,_8=_6.orient||{"BL":"TL","TL":"BL"},_9=_6.around,id=(_6.around&&_6.around.id)?(_6.around.id+"_dropdown"):("popup_"+_3++);
var _b=dojo.create("div",{id:id,"class":"dijitPopup",style:{zIndex:_2+_1.length,visibility:"hidden"}},dojo.body());
dijit.setWaiRole(_b,"presentation");
_b.style.left=_b.style.top="0px";
if(_6.parent){
_b.dijitPopupParent=_6.parent.id;
}
var s=_7.domNode.style;
s.display="";
s.visibility="";
s.position="";
s.top="0px";
_b.appendChild(_7.domNode);
var _d=new dijit.BackgroundIframe(_b);
var _e=_9?dijit.placeOnScreenAroundElement(_b,_9,_8,_7.orient?dojo.hitch(_7,"orient"):null):dijit.placeOnScreen(_b,_6,_8=="R"?["TR","BR","TL","BL"]:["TL","BL","TR","BR"],_6.padding);
_b.style.visibility="visible";
var _f=[];
var _10=function(){
for(var pi=_1.length-1;pi>0&&_1[pi].parent===_1[pi-1].widget;pi--){
}
return _1[pi];
};
_f.push(dojo.connect(_b,"onkeypress",this,function(evt){
if(evt.charOrCode==dojo.keys.ESCAPE&&_6.onCancel){
dojo.stopEvent(evt);
_6.onCancel();
}else{
if(evt.charOrCode===dojo.keys.TAB){
dojo.stopEvent(evt);
var _13=_10();
if(_13&&_13.onCancel){
_13.onCancel();
}
}
}
}));
if(_7.onCancel){
_f.push(dojo.connect(_7,"onCancel",null,_6.onCancel));
}
_f.push(dojo.connect(_7,_7.onExecute?"onExecute":"onChange",null,function(){
var _14=_10();
if(_14&&_14.onExecute){
_14.onExecute();
}
}));
_1.push({wrapper:_b,iframe:_d,widget:_7,parent:_6.parent,onExecute:_6.onExecute,onCancel:_6.onCancel,onClose:_6.onClose,handlers:_f});
if(_7.onOpen){
_7.onOpen(_e);
}
return _e;
};
this.close=function(_15){
while(dojo.some(_1,function(_16){
return _16.widget==_15;
})){
var top=_1.pop(),_18=top.wrapper,_19=top.iframe,_1a=top.widget,_1b=top.onClose;
if(_1a.onClose){
_1a.onClose();
}
dojo.forEach(top.handlers,dojo.disconnect);
if(!_1a||!_1a.domNode){
return;
}
this.prepare(_1a.domNode);
_19.destroy();
dojo.destroy(_18);
if(_1b){
_1b();
}
}
};
}();
dijit._frames=new function(){
var _1c=[];
this.pop=function(){
var _1d;
if(_1c.length){
_1d=_1c.pop();
_1d.style.display="";
}else{
if(dojo.isIE){
var _1e=dojo.config["dojoBlankHtmlUrl"]||(dojo.moduleUrl("dojo","resources/blank.html")+"")||"javascript:\"\"";
var _1f="<iframe src='"+_1e+"'"+" style='position: absolute; left: 0px; top: 0px;"+"z-index: -1; filter:Alpha(Opacity=\"0\");'>";
_1d=dojo.doc.createElement(_1f);
}else{
_1d=dojo.create("iframe");
_1d.src="javascript:\"\"";
_1d.className="dijitBackgroundIframe";
}
_1d.tabIndex=-1;
dojo.body().appendChild(_1d);
}
return _1d;
};
this.push=function(_20){
_20.style.display="none";
if(dojo.isIE){
_20.style.removeExpression("width");
_20.style.removeExpression("height");
}
_1c.push(_20);
};
}();
dijit.BackgroundIframe=function(_21){
if(!_21.id){
throw new Error("no id");
}
if(dojo.isIE<7||(dojo.isFF<3&&dojo.hasClass(dojo.body(),"dijit_a11y"))){
var _22=dijit._frames.pop();
_21.appendChild(_22);
if(dojo.isIE){
_22.style.setExpression("width",dojo._scopeName+".doc.getElementById('"+_21.id+"').offsetWidth");
_22.style.setExpression("height",dojo._scopeName+".doc.getElementById('"+_21.id+"').offsetHeight");
}
this.iframe=_22;
}
};
dojo.extend(dijit.BackgroundIframe,{destroy:function(){
if(this.iframe){
dijit._frames.push(this.iframe);
delete this.iframe;
}
}});
}
