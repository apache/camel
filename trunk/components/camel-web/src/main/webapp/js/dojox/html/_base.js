/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.html._base"]){
dojo._hasResource["dojox.html._base"]=true;
dojo.provide("dojox.html._base");
dojo.require("dojo.html");
(function(){
if(dojo.isIE){
var _1=/(AlphaImageLoader\([^)]*?src=(['"]))(?![a-z]+:|\/)([^\r\n;}]+?)(\2[^)]*\)\s*[;}]?)/g;
}
var _2=/(?:(?:@import\s*(['"])(?![a-z]+:|\/)([^\r\n;{]+?)\1)|url\(\s*(['"]?)(?![a-z]+:|\/)([^\r\n;]+?)\3\s*\))([a-z, \s]*[;}]?)/g;
var _3=dojox.html._adjustCssPaths=function(_4,_5){
if(!_5||!_4){
return;
}
if(_1){
_5=_5.replace(_1,function(_6,_7,_8,_9,_a){
return _7+(new dojo._Url(_4,"./"+_9).toString())+_a;
});
}
return _5.replace(_2,function(_b,_c,_d,_e,_f,_10){
if(_d){
return "@import \""+(new dojo._Url(_4,"./"+_d).toString())+"\""+_10;
}else{
return "url("+(new dojo._Url(_4,"./"+_f).toString())+")"+_10;
}
});
};
var _11=/(<[a-z][a-z0-9]*\s[^>]*)(?:(href|src)=(['"]?)([^>]*?)\3|style=(['"]?)([^>]*?)\5)([^>]*>)/gi;
var _12=dojox.html._adjustHtmlPaths=function(_13,_14){
var url=_13||"./";
return _14.replace(_11,function(tag,_17,_18,_19,_1a,_1b,_1c,end){
return _17+(_18?(_18+"="+_19+(new dojo._Url(url,_1a).toString())+_19):("style="+_1b+_3(url,_1c)+_1b))+end;
});
};
var _1e=dojox.html._snarfStyles=function(_1f,_20,_21){
_21.attributes=[];
return _20.replace(/(?:<style([^>]*)>([\s\S]*?)<\/style>|<link\s+(?=[^>]*rel=['"]?stylesheet)([^>]*?href=(['"])([^>]*?)\4[^>\/]*)\/?>)/gi,function(_22,_23,_24,_25,_26,_27){
var i,_29=(_23||_25||"").replace(/^\s*([\s\S]*?)\s*$/i,"$1");
if(_24){
i=_21.push(_1f?_3(_1f,_24):_24);
}else{
i=_21.push("@import \""+_27+"\";");
_29=_29.replace(/\s*(?:rel|href)=(['"])?[^\s]*\1\s*/gi,"");
}
if(_29){
_29=_29.split(/\s+/);
var _2a={},tmp;
for(var j=0,e=_29.length;j<e;j++){
tmp=_29[j].split("=");
_2a[tmp[0]]=tmp[1].replace(/^\s*['"]?([\s\S]*?)['"]?\s*$/,"$1");
}
_21.attributes[i-1]=_2a;
}
return "";
});
};
var _2e=dojox.html._snarfScripts=function(_2f,_30){
_30.code="";
function _31(src){
if(_30.downloadRemote){
dojo.xhrGet({url:src,sync:true,load:function(_33){
_30.code+=_33+";";
},error:_30.errBack});
}
};
return _2f.replace(/<script\s*(?![^>]*type=['"]?dojo)(?:[^>]*?(?:src=(['"]?)([^>]*?)\1[^>]*)?)*>([\s\S]*?)<\/script>/gi,function(_34,_35,src,_37){
if(src){
_31(src);
}else{
_30.code+=_37;
}
return "";
});
};
var _38=dojox.html.evalInGlobal=function(_39,_3a){
_3a=_3a||dojo.doc.body;
var n=_3a.ownerDocument.createElement("script");
n.type="text/javascript";
_3a.appendChild(n);
n.text=_39;
};
dojo.declare("dojox.html._ContentSetter",[dojo.html._ContentSetter],{adjustPaths:false,referencePath:".",renderStyles:false,executeScripts:false,scriptHasHooks:false,scriptHookReplacement:null,_renderStyles:function(_3c){
this._styleNodes=[];
var st,att,_3f,doc=this.node.ownerDocument;
var _41=doc.getElementsByTagName("head")[0];
for(var i=0,e=_3c.length;i<e;i++){
_3f=_3c[i];
att=_3c.attributes[i];
st=doc.createElement("style");
st.setAttribute("type","text/css");
for(var x in att){
st.setAttribute(x,att[x]);
}
this._styleNodes.push(st);
_41.appendChild(st);
if(st.styleSheet){
st.styleSheet.cssText=_3f;
}else{
st.appendChild(doc.createTextNode(_3f));
}
}
},empty:function(){
this.inherited("empty",arguments);
this._styles=[];
},onBegin:function(){
this.inherited("onBegin",arguments);
var _45=this.content,_46=this.node;
var _47=this._styles;
if(dojo.isString(_45)){
if(this.adjustPaths&&this.referencePath){
_45=_12(this.referencePath,_45);
}
if(this.renderStyles||this.cleanContent){
_45=_1e(this.referencePath,_45,_47);
}
if(this.executeScripts){
var _t=this;
var _49={downloadRemote:true,errBack:function(e){
_t._onError.call(_t,"Exec","Error downloading remote script in \""+_t.id+"\"",e);
}};
_45=_2e(_45,_49);
this._code=_49.code;
}
}
this.content=_45;
},onEnd:function(){
var _4b=this._code,_4c=this._styles;
if(this._styleNodes&&this._styleNodes.length){
while(this._styleNodes.length){
dojo.destroy(this._styleNodes.pop());
}
}
if(this.renderStyles&&_4c&&_4c.length){
this._renderStyles(_4c);
}
if(this.executeScripts&&_4b){
if(this.cleanContent){
_4b=_4b.replace(/(<!--|(?:\/\/)?-->|<!\[CDATA\[|\]\]>)/g,"");
}
if(this.scriptHasHooks){
_4b=_4b.replace(/_container_(?!\s*=[^=])/g,this.scriptHookReplacement);
}
try{
_38(_4b,this.node);
}
catch(e){
this._onError("Exec","Error eval script in "+this.id+", "+e.message,e);
}
}
this.inherited("onEnd",arguments);
},tearDown:function(){
this.inherited(arguments);
delete this._styles;
if(this._styleNodes&&this._styleNodes.length){
while(this._styleNodes.length){
dojo.destroy(this._styleNodes.pop());
}
}
delete this._styleNodes;
dojo.mixin(this,dojo.getObject(this.declaredClass).prototype);
}});
dojox.html.set=function(_4d,_4e,_4f){
if(!_4f){
return dojo.html._setNodeContent(_4d,_4e,true);
}else{
var op=new dojox.html._ContentSetter(dojo.mixin(_4f,{content:_4e,node:_4d}));
return op.set();
}
};
})();
}
