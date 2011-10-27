/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.atom.io.Connection"]){
dojo._hasResource["dojox.atom.io.Connection"]=true;
dojo.provide("dojox.atom.io.Connection");
dojo.require("dojox.atom.io.model");
dojo.declare("dojox.atom.io.Connection",null,{constructor:function(_1,_2){
this.sync=_1;
this.preventCache=_2;
},preventCache:false,alertsEnabled:false,getFeed:function(_3,_4,_5,_6){
this._getXmlDoc(_3,"feed",new dojox.atom.io.model.Feed(),dojox.atom.io.model._Constants.ATOM_NS,_4,_5,_6);
},getService:function(_7,_8,_9,_a){
this._getXmlDoc(_7,"service",new dojox.atom.io.model.Service(_7),dojox.atom.io.model._Constants.APP_NS,_8,_9,_a);
},getEntry:function(_b,_c,_d,_e){
this._getXmlDoc(_b,"entry",new dojox.atom.io.model.Entry(),dojox.atom.io.model._Constants.ATOM_NS,_c,_d,_e);
},_getXmlDoc:function(_f,_10,_11,_12,_13,_14,_15){
if(!_15){
_15=dojo.global;
}
var ae=this.alertsEnabled;
var _17={url:_f,handleAs:"xml",sync:this.sync,preventCache:this.preventCache,load:function(_18,_19){
var _1a=null;
var _1b=_18;
var _1c;
if(_1b){
if(typeof (_1b.getElementsByTagNameNS)!="undefined"){
_1c=_1b.getElementsByTagNameNS(_12,_10);
if(_1c&&_1c.length>0){
_1a=_1c.item(0);
}else{
if(_1b.lastChild){
_1a=_1b.lastChild;
}
}
}else{
if(typeof (_1b.getElementsByTagName)!="undefined"){
_1c=_1b.getElementsByTagName(_10);
if(_1c&&_1c.length>0){
for(var i=0;i<_1c.length;i++){
if(_1c[i].namespaceURI==_12){
_1a=_1c[i];
break;
}
}
}else{
if(_1b.lastChild){
_1a=_1b.lastChild;
}
}
}else{
if(_1b.lastChild){
_1a=_1b.lastChild;
}else{
_13.call(_15,null,null,_19);
return;
}
}
}
_11.buildFromDom(_1a);
if(_13){
_13.call(_15,_11,_1b,_19);
}else{
if(ae){
var _1e=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_1e.noCallback);
}
}
}else{
_13.call(_15,null,null,_19);
}
}};
if(this.user&&this.user!==null){
_17.user=this.user;
}
if(this.password&&this.password!==null){
_17.password=this.password;
}
if(_14){
_17.error=function(_1f,_20){
_14.call(_15,_1f,_20);
};
}else{
_17.error=function(){
var _21=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_21.failedXhr);
};
}
dojo.xhrGet(_17);
},updateEntry:function(_22,_23,_24,_25,_26,_27){
if(!_27){
_27=dojo.global;
}
_22.updated=new Date();
var url=_22.getEditHref();
if(!url){
var _29=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_29.missingEditUrl);
}
var _2a=this;
var ae=this.alertsEnabled;
var _2c={url:url,handleAs:"text",contentType:"text/xml",sync:this.sync,preventCache:this.preventCache,load:function(_2d,_2e){
var _2f=null;
if(_25){
_2f=_2e.xhr.getResponseHeader("Location");
if(!_2f){
_2f=url;
}
var _30=function(_31,dom,_33){
if(_23){
_23.call(_27,_31,_2f,_33);
}else{
if(ae){
var _34=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_34.noCallback);
}
}
};
_2a.getEntry(_2f,_30);
}else{
if(_23){
_23.call(_27,_22,_2e.xhr.getResponseHeader("Location"),_2e);
}else{
if(ae){
var _35=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_35.noCallback);
}
}
}
return _2d;
}};
if(this.user&&this.user!==null){
_2c.user=this.user;
}
if(this.password&&this.password!==null){
_2c.password=this.password;
}
if(_24){
_2c.error=function(_36,_37){
_24.call(_27,_36,_37);
};
}else{
_2c.error=function(){
var _38=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_38.failedXhr);
};
}
if(_26){
_2c.postData=_22.toString(true);
_2c.headers={"X-Method-Override":"PUT"};
dojo.rawXhrPost(_2c);
}else{
_2c.putData=_22.toString(true);
var xhr=dojo.rawXhrPut(_2c);
}
},addEntry:function(_3a,url,_3c,_3d,_3e,_3f){
if(!_3f){
_3f=dojo.global;
}
_3a.published=new Date();
_3a.updated=new Date();
var _40=_3a.feedUrl;
var ae=this.alertsEnabled;
if(!url&&_40){
url=_40;
}
if(!url){
if(ae){
var _42=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_42.missingUrl);
}
return;
}
var _43=this;
var _44={url:url,handleAs:"text",contentType:"text/xml",sync:this.sync,preventCache:this.preventCache,postData:_3a.toString(true),load:function(_45,_46){
var _47=_46.xhr.getResponseHeader("Location");
if(!_47){
_47=url;
}
if(!_46.retrieveEntry){
if(_3c){
_3c.call(_3f,_3a,_47,_46);
}else{
if(ae){
var _48=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_48.noCallback);
}
}
}else{
var _49=function(_4a,dom,_4c){
if(_3c){
_3c.call(_3f,_4a,_47,_4c);
}else{
if(ae){
var _4d=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_4d.noCallback);
}
}
};
_43.getEntry(_47,_49);
}
return _45;
}};
if(this.user&&this.user!==null){
_44.user=this.user;
}
if(this.password&&this.password!==null){
_44.password=this.password;
}
if(_3d){
_44.error=function(_4e,_4f){
_3d.call(_3f,_4e,_4f);
};
}else{
_44.error=function(){
var _50=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_50.failedXhr);
};
}
dojo.rawXhrPost(_44);
},deleteEntry:function(_51,_52,_53,_54,_55){
if(!_55){
_55=dojo.global;
}
var url=null;
if(typeof (_51)=="string"){
url=_51;
}else{
url=_51.getEditHref();
}
if(!url){
var _57=dojo.i18n.getLocalization("dojox.atom.io","messages");
_52.call(_55,false,null);
throw new Error(_57.missingUrl);
}
var _58={url:url,handleAs:"text",sync:this.sync,preventCache:this.preventCache,load:function(_59,_5a){
_52.call(_55,_5a);
return _59;
}};
if(this.user&&this.user!==null){
_58.user=this.user;
}
if(this.password&&this.password!==null){
_58.password=this.password;
}
if(_53){
_58.error=function(_5b,_5c){
_53.call(_55,_5b,_5c);
};
}else{
_58.error=function(){
var _5d=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_5d.failedXhr);
};
}
if(_54){
_58.headers={"X-Method-Override":"DELETE"};
dojo.xhrPost(_58);
}else{
dojo.xhrDelete(_58);
}
}});
}
