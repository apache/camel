/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.XmlStore"]){
dojo._hasResource["dojox.data.XmlStore"]=true;
dojo.provide("dojox.data.XmlStore");
dojo.provide("dojox.data.XmlItem");
dojo.require("dojo.data.util.simpleFetch");
dojo.require("dojo.data.util.filter");
dojo.require("dojox.xml.parser");
dojo.declare("dojox.data.XmlStore",null,{constructor:function(_1){
if(_1){
this.url=_1.url;
this.rootItem=(_1.rootItem||_1.rootitem||this.rootItem);
this.keyAttribute=(_1.keyAttribute||_1.keyattribute||this.keyAttribute);
this._attributeMap=(_1.attributeMap||_1.attributemap);
this.label=_1.label||this.label;
this.sendQuery=(_1.sendQuery||_1.sendquery||this.sendQuery);
}
this._newItems=[];
this._deletedItems=[];
this._modifiedItems=[];
},url:"",rootItem:"",keyAttribute:"",label:"",sendQuery:false,attributeMap:null,getValue:function(_2,_3,_4){
var _5=_2.element;
var i;
var _7;
if(_3==="tagName"){
return _5.nodeName;
}else{
if(_3==="childNodes"){
for(i=0;i<_5.childNodes.length;i++){
_7=_5.childNodes[i];
if(_7.nodeType===1){
return this._getItem(_7);
}
}
return _4;
}else{
if(_3==="text()"){
for(i=0;i<_5.childNodes.length;i++){
_7=_5.childNodes[i];
if(_7.nodeType===3||_7.nodeType===4){
return _7.nodeValue;
}
}
return _4;
}else{
_3=this._getAttribute(_5.nodeName,_3);
if(_3.charAt(0)==="@"){
var _8=_3.substring(1);
var _9=_5.getAttribute(_8);
return (_9!==undefined)?_9:_4;
}else{
for(i=0;i<_5.childNodes.length;i++){
_7=_5.childNodes[i];
if(_7.nodeType===1&&_7.nodeName===_3){
return this._getItem(_7);
}
}
return _4;
}
}
}
}
},getValues:function(_a,_b){
var _c=_a.element;
var _d=[];
var i;
var _f;
if(_b==="tagName"){
return [_c.nodeName];
}else{
if(_b==="childNodes"){
for(i=0;i<_c.childNodes.length;i++){
_f=_c.childNodes[i];
if(_f.nodeType===1){
_d.push(this._getItem(_f));
}
}
return _d;
}else{
if(_b==="text()"){
var ec=_c.childNodes;
for(i=0;i<ec.length;i++){
_f=ec[i];
if(_f.nodeType===3||_f.nodeType===4){
_d.push(_f.nodeValue);
}
}
return _d;
}else{
_b=this._getAttribute(_c.nodeName,_b);
if(_b.charAt(0)==="@"){
var _11=_b.substring(1);
var _12=_c.getAttribute(_11);
return (_12!==undefined)?[_12]:[];
}else{
for(i=0;i<_c.childNodes.length;i++){
_f=_c.childNodes[i];
if(_f.nodeType===1&&_f.nodeName===_b){
_d.push(this._getItem(_f));
}
}
return _d;
}
}
}
}
},getAttributes:function(_13){
var _14=_13.element;
var _15=[];
var i;
_15.push("tagName");
if(_14.childNodes.length>0){
var _17={};
var _18=true;
var _19=false;
for(i=0;i<_14.childNodes.length;i++){
var _1a=_14.childNodes[i];
if(_1a.nodeType===1){
var _1b=_1a.nodeName;
if(!_17[_1b]){
_15.push(_1b);
_17[_1b]=_1b;
}
_18=true;
}else{
if(_1a.nodeType===3){
_19=true;
}
}
}
if(_18){
_15.push("childNodes");
}
if(_19){
_15.push("text()");
}
}
for(i=0;i<_14.attributes.length;i++){
_15.push("@"+_14.attributes[i].nodeName);
}
if(this._attributeMap){
for(var key in this._attributeMap){
i=key.indexOf(".");
if(i>0){
var _1d=key.substring(0,i);
if(_1d===_14.nodeName){
_15.push(key.substring(i+1));
}
}else{
_15.push(key);
}
}
}
return _15;
},hasAttribute:function(_1e,_1f){
return (this.getValue(_1e,_1f)!==undefined);
},containsValue:function(_20,_21,_22){
var _23=this.getValues(_20,_21);
for(var i=0;i<_23.length;i++){
if((typeof _22==="string")){
if(_23[i].toString&&_23[i].toString()===_22){
return true;
}
}else{
if(_23[i]===_22){
return true;
}
}
}
return false;
},isItem:function(_25){
if(_25&&_25.element&&_25.store&&_25.store===this){
return true;
}
return false;
},isItemLoaded:function(_26){
return this.isItem(_26);
},loadItem:function(_27){
},getFeatures:function(){
var _28={"dojo.data.api.Read":true,"dojo.data.api.Write":true};
if(!this.sendQuery||this.keyAttribute!==""){
_28["dojo.data.api.Identity"]=true;
}
return _28;
},getLabel:function(_29){
if((this.label!=="")&&this.isItem(_29)){
var _2a=this.getValue(_29,this.label);
if(_2a){
return _2a.toString();
}
}
return undefined;
},getLabelAttributes:function(_2b){
if(this.label!==""){
return [this.label];
}
return null;
},_fetchItems:function(_2c,_2d,_2e){
var url=this._getFetchUrl(_2c);

if(!url){
_2e(new Error("No URL specified."));
return;
}
var _30=(!this.sendQuery?_2c:null);
var _31=this;
var _32={url:url,handleAs:"xml",preventCache:true};
var _33=dojo.xhrGet(_32);
_33.addCallback(function(_34){
var _35=_31._getItems(_34,_30);

if(_35&&_35.length>0){
_2d(_35,_2c);
}else{
_2d([],_2c);
}
});
_33.addErrback(function(_36){
_2e(_36,_2c);
});
},_getFetchUrl:function(_37){
if(!this.sendQuery){
return this.url;
}
var _38=_37.query;
if(!_38){
return this.url;
}
if(dojo.isString(_38)){
return this.url+_38;
}
var _39="";
for(var _3a in _38){
var _3b=_38[_3a];
if(_3b){
if(_39){
_39+="&";
}
_39+=(_3a+"="+_3b);
}
}
if(!_39){
return this.url;
}
var _3c=this.url;
if(_3c.indexOf("?")<0){
_3c+="?";
}else{
_3c+="&";
}
return _3c+_39;
},_getItems:function(_3d,_3e){
var _3f=null;
if(_3e){
_3f=_3e.query;
}
var _40=[];
var _41=null;
if(this.rootItem!==""){
_41=dojo.query(this.rootItem,_3d);
}else{
_41=_3d.documentElement.childNodes;
}
var _42=_3e.queryOptions?_3e.queryOptions.deep:false;
if(_42){
_41=this._flattenNodes(_41);
}
for(var i=0;i<_41.length;i++){
var _44=_41[i];
if(_44.nodeType!=1){
continue;
}
var _45=this._getItem(_44);
if(_3f){
var _46=true;
var _47=_3e.queryOptions?_3e.queryOptions.ignoreCase:false;
var _48;
var _49={};
for(var key in _3f){
_48=_3f[key];
if(typeof _48==="string"){
_49[key]=dojo.data.util.filter.patternToRegExp(_48,_47);
}
}
for(var _4b in _3f){
_48=this.getValue(_45,_4b);
if(_48){
var _4c=_3f[_4b];
if((typeof _48)==="string"&&(_49[_4b])){
if((_48.match(_49[_4b]))!==null){
continue;
}
}else{
if((typeof _48)==="object"){
if(_48.toString&&(_49[_4b])){
var _4d=_48.toString();
if((_4d.match(_49[_4b]))!==null){
continue;
}
}else{
if(_4c==="*"||_4c===_48){
continue;
}
}
}
}
}
_46=false;
break;
}
if(!_46){
continue;
}
}
_40.push(_45);
}
dojo.forEach(_40,function(_4e){
_4e.element.parentNode.removeChild(_4e.element);
},this);
return _40;
},_flattenNodes:function(_4f){
var _50=[];
if(_4f){
var i;
for(i=0;i<_4f.length;i++){
var _52=_4f[i];
_50.push(_52);
if(_52.childNodes&&_52.childNodes.length>0){
_50=_50.concat(this._flattenNodes(_52.childNodes));
}
}
}
return _50;
},close:function(_53){
},newItem:function(_54,_55){

_54=(_54||{});
var _56=_54.tagName;
if(!_56){
_56=this.rootItem;
if(_56===""){
return null;
}
}
var _57=this._getDocument();
var _58=_57.createElement(_56);
for(var _59 in _54){
var _5a;
if(_59==="tagName"){
continue;
}else{
if(_59==="text()"){
_5a=_57.createTextNode(_54[_59]);
_58.appendChild(_5a);
}else{
_59=this._getAttribute(_56,_59);
if(_59.charAt(0)==="@"){
var _5b=_59.substring(1);
_58.setAttribute(_5b,_54[_59]);
}else{
var _5c=_57.createElement(_59);
_5a=_57.createTextNode(_54[_59]);
_5c.appendChild(_5a);
_58.appendChild(_5c);
}
}
}
}
var _5d=this._getItem(_58);
this._newItems.push(_5d);
var _5e=null;
if(_55&&_55.parent&&_55.attribute){
_5e={item:_55.parent,attribute:_55.attribute,oldValue:undefined};
var _5f=this.getValues(_55.parent,_55.attribute);
if(_5f&&_5f.length>0){
var _60=_5f.slice(0,_5f.length);
if(_5f.length===1){
_5e.oldValue=_5f[0];
}else{
_5e.oldValue=_5f.slice(0,_5f.length);
}
_60.push(_5d);
this.setValues(_55.parent,_55.attribute,_60);
_5e.newValue=this.getValues(_55.parent,_55.attribute);
}else{
this.setValues(_55.parent,_55.attribute,_5d);
_5e.newValue=_5d;
}
}
return _5d;
},deleteItem:function(_61){

var _62=_61.element;
if(_62.parentNode){
this._backupItem(_61);
_62.parentNode.removeChild(_62);
return true;
}
this._forgetItem(_61);
this._deletedItems.push(_61);
return true;
},setValue:function(_63,_64,_65){
if(_64==="tagName"){
return false;
}
this._backupItem(_63);
var _66=_63.element;
var _67;
var _68;
if(_64==="childNodes"){
_67=_65.element;
_66.appendChild(_67);
}else{
if(_64==="text()"){
while(_66.firstChild){
_66.removeChild(_66.firstChild);
}
_68=this._getDocument(_66).createTextNode(_65);
_66.appendChild(_68);
}else{
_64=this._getAttribute(_66.nodeName,_64);
if(_64.charAt(0)==="@"){
var _69=_64.substring(1);
_66.setAttribute(_69,_65);
}else{
for(var i=0;i<_66.childNodes.length;i++){
var _6b=_66.childNodes[i];
if(_6b.nodeType===1&&_6b.nodeName===_64){
_67=_6b;
break;
}
}
var _6c=this._getDocument(_66);
if(_67){
while(_67.firstChild){
_67.removeChild(_67.firstChild);
}
}else{
_67=_6c.createElement(_64);
_66.appendChild(_67);
}
_68=_6c.createTextNode(_65);
_67.appendChild(_68);
}
}
}
return true;
},setValues:function(_6d,_6e,_6f){
if(_6e==="tagName"){
return false;
}
this._backupItem(_6d);
var _70=_6d.element;
var i;
var _72;
var _73;
if(_6e==="childNodes"){
while(_70.firstChild){
_70.removeChild(_70.firstChild);
}
for(i=0;i<_6f.length;i++){
_72=_6f[i].element;
_70.appendChild(_72);
}
}else{
if(_6e==="text()"){
while(_70.firstChild){
_70.removeChild(_70.firstChild);
}
var _74="";
for(i=0;i<_6f.length;i++){
_74+=_6f[i];
}
_73=this._getDocument(_70).createTextNode(_74);
_70.appendChild(_73);
}else{
_6e=this._getAttribute(_70.nodeName,_6e);
if(_6e.charAt(0)==="@"){
var _75=_6e.substring(1);
_70.setAttribute(_75,_6f[0]);
}else{
for(i=_70.childNodes.length-1;i>=0;i--){
var _76=_70.childNodes[i];
if(_76.nodeType===1&&_76.nodeName===_6e){
_70.removeChild(_76);
}
}
var _77=this._getDocument(_70);
for(i=0;i<_6f.length;i++){
_72=_77.createElement(_6e);
_73=_77.createTextNode(_6f[i]);
_72.appendChild(_73);
_70.appendChild(_72);
}
}
}
}
return true;
},unsetAttribute:function(_78,_79){
if(_79==="tagName"){
return false;
}
this._backupItem(_78);
var _7a=_78.element;
if(_79==="childNodes"||_79==="text()"){
while(_7a.firstChild){
_7a.removeChild(_7a.firstChild);
}
}else{
_79=this._getAttribute(_7a.nodeName,_79);
if(_79.charAt(0)==="@"){
var _7b=_79.substring(1);
_7a.removeAttribute(_7b);
}else{
for(var i=_7a.childNodes.length-1;i>=0;i--){
var _7d=_7a.childNodes[i];
if(_7d.nodeType===1&&_7d.nodeName===_79){
_7a.removeChild(_7d);
}
}
}
}
return true;
},save:function(_7e){
if(!_7e){
_7e={};
}
var i;
for(i=0;i<this._modifiedItems.length;i++){
this._saveItem(this._modifiedItems[i],_7e,"PUT");
}
for(i=0;i<this._newItems.length;i++){
var _80=this._newItems[i];
if(_80.element.parentNode){
this._newItems.splice(i,1);
i--;
continue;
}
this._saveItem(this._newItems[i],_7e,"POST");
}
for(i=0;i<this._deletedItems.length;i++){
this._saveItem(this._deletedItems[i],_7e,"DELETE");
}
},revert:function(){



this._newItems=[];
this._restoreItems(this._deletedItems);
this._deletedItems=[];
this._restoreItems(this._modifiedItems);
this._modifiedItems=[];
return true;
},isDirty:function(_81){
if(_81){
var _82=this._getRootElement(_81.element);
return (this._getItemIndex(this._newItems,_82)>=0||this._getItemIndex(this._deletedItems,_82)>=0||this._getItemIndex(this._modifiedItems,_82)>=0);
}else{
return (this._newItems.length>0||this._deletedItems.length>0||this._modifiedItems.length>0);
}
},_saveItem:function(_83,_84,_85){
var url;
var _87;
if(_85==="PUT"){
url=this._getPutUrl(_83);
}else{
if(_85==="DELETE"){
url=this._getDeleteUrl(_83);
}else{
url=this._getPostUrl(_83);
}
}
if(!url){
if(_84.onError){
_87=_84.scope||dojo.global;
_84.onError.call(_87,new Error("No URL for saving content: "+this._getPostContent(_83)));
}
return;
}
var _88={url:url,method:(_85||"POST"),contentType:"text/xml",handleAs:"xml"};
var _89;
if(_85==="PUT"){
_88.putData=this._getPutContent(_83);
_89=dojo.rawXhrPut(_88);
}else{
if(_85==="DELETE"){
_89=dojo.xhrDelete(_88);
}else{
_88.postData=this._getPostContent(_83);
_89=dojo.rawXhrPost(_88);
}
}
_87=(_84.scope||dojo.global);
var _8a=this;
_89.addCallback(function(_8b){
_8a._forgetItem(_83);
if(_84.onComplete){
_84.onComplete.call(_87);
}
});
_89.addErrback(function(_8c){
if(_84.onError){
_84.onError.call(_87,_8c);
}
});
},_getPostUrl:function(_8d){
return this.url;
},_getPutUrl:function(_8e){
return this.url;
},_getDeleteUrl:function(_8f){
var url=this.url;
if(_8f&&this.keyAttribute!==""){
var _91=this.getValue(_8f,this.keyAttribute);
if(_91){
var key=this.keyAttribute.charAt(0)==="@"?this.keyAttribute.substring(1):this.keyAttribute;
url+=url.indexOf("?")<0?"?":"&";
url+=key+"="+_91;
}
}
return url;
},_getPostContent:function(_93){
var _94=_93.element;
var _95="<?xml version=\"1.0\"?>";
return _95+dojox.xml.parser.innerXML(_94);
},_getPutContent:function(_96){
var _97=_96.element;
var _98="<?xml version=\"1.0\"?>";
return _98+dojox.xml.parser.innerXML(_97);
},_getAttribute:function(_99,_9a){
if(this._attributeMap){
var key=_99+"."+_9a;
var _9c=this._attributeMap[key];
if(_9c){
_9a=_9c;
}else{
_9c=this._attributeMap[_9a];
if(_9c){
_9a=_9c;
}
}
}
return _9a;
},_getItem:function(_9d){
try{
var q=null;
if(this.keyAttribute===""){
q=this._getXPath(_9d);
}
return new dojox.data.XmlItem(_9d,this,q);
}
catch(e){

}
return null;
},_getItemIndex:function(_9f,_a0){
for(var i=0;i<_9f.length;i++){
if(_9f[i].element===_a0){
return i;
}
}
return -1;
},_backupItem:function(_a2){
var _a3=this._getRootElement(_a2.element);
if(this._getItemIndex(this._newItems,_a3)>=0||this._getItemIndex(this._modifiedItems,_a3)>=0){
return;
}
if(_a3!=_a2.element){
_a2=this._getItem(_a3);
}
_a2._backup=_a3.cloneNode(true);
this._modifiedItems.push(_a2);
},_restoreItems:function(_a4){
dojo.forEach(_a4,function(_a5){
if(_a5._backup){
_a5.element=_a5._backup;
_a5._backup=null;
}
},this);
},_forgetItem:function(_a6){
var _a7=_a6.element;
var _a8=this._getItemIndex(this._newItems,_a7);
if(_a8>=0){
this._newItems.splice(_a8,1);
}
_a8=this._getItemIndex(this._deletedItems,_a7);
if(_a8>=0){
this._deletedItems.splice(_a8,1);
}
_a8=this._getItemIndex(this._modifiedItems,_a7);
if(_a8>=0){
this._modifiedItems.splice(_a8,1);
}
},_getDocument:function(_a9){
if(_a9){
return _a9.ownerDocument;
}else{
if(!this._document){
return dojox.xml.parser.parse();
}
}
return null;
},_getRootElement:function(_aa){
while(_aa.parentNode){
_aa=_aa.parentNode;
}
return _aa;
},_getXPath:function(_ab){
var _ac=null;
if(!this.sendQuery){
var _ad=_ab;
_ac="";
while(_ad&&_ad!=_ab.ownerDocument){
var pos=0;
var _af=_ad;
var _b0=_ad.nodeName;
while(_af){
_af=_af.previousSibling;
if(_af&&_af.nodeName===_b0){
pos++;
}
}
var _b1="/"+_b0+"["+pos+"]";
if(_ac){
_ac=_b1+_ac;
}else{
_ac=_b1;
}
_ad=_ad.parentNode;
}
}
return _ac;
},getIdentity:function(_b2){
if(!this.isItem(_b2)){
throw new Error("dojox.data.XmlStore: Object supplied to getIdentity is not an item");
}else{
var id=null;
if(this.sendQuery&&this.keyAttribute!==""){
id=this.getValue(_b2,this.keyAttribute).toString();
}else{
if(!this.serverQuery){
if(this.keyAttribute!==""){
id=this.getValue(_b2,this.keyAttribute).toString();
}else{
id=_b2.q;
}
}
}
return id;
}
},getIdentityAttributes:function(_b4){
if(!this.isItem(_b4)){
throw new Error("dojox.data.XmlStore: Object supplied to getIdentity is not an item");
}else{
if(this.keyAttribute!==""){
return [this.keyAttribute];
}else{
return null;
}
}
},fetchItemByIdentity:function(_b5){
var _b6=null;
var _b7=null;
var _b8=this;
var url=null;
var _ba=null;
var _bb=null;
if(!_b8.sendQuery){
_b6=function(_bc){
if(_bc){
if(_b8.keyAttribute!==""){
var _bd={};
_bd.query={};
_bd.query[_b8.keyAttribute]=_b5.identity;
var _be=_b8._getItems(_bc,_bd);
_b7=_b5.scope||dojo.global;
if(_be.length===1){
if(_b5.onItem){
_b5.onItem.call(_b7,_be[0]);
}
}else{
if(_be.length===0){
if(_b5.onItem){
_b5.onItem.call(_b7,null);
}
}else{
if(_b5.onError){
_b5.onError.call(_b7,new Error("Items array size for identity lookup greater than 1, invalid keyAttribute."));
}
}
}
}else{
var _bf=_b5.identity.split("/");
var i;
var _c1=_bc;
for(i=0;i<_bf.length;i++){
if(_bf[i]&&_bf[i]!==""){
var _c2=_bf[i];
_c2=_c2.substring(0,_c2.length-1);
var _c3=_c2.split("[");
var tag=_c3[0];
var _c5=parseInt(_c3[1],10);
var pos=0;
if(_c1){
var _c7=_c1.childNodes;
if(_c7){
var j;
var _c9=null;
for(j=0;j<_c7.length;j++){
var _ca=_c7[j];
if(_ca.nodeName===tag){
if(pos<_c5){
pos++;
}else{
_c9=_ca;
break;
}
}
}
if(_c9){
_c1=_c9;
}else{
_c1=null;
}
}else{
_c1=null;
}
}else{
break;
}
}
}
var _cb=null;
if(_c1){
_cb=_b8._getItem(_c1);
_cb.element.parentNode.removeChild(_cb.element);
}
if(_b5.onItem){
_b7=_b5.scope||dojo.global;
_b5.onItem.call(_b7,_cb);
}
}
}
};
url=this._getFetchUrl(null);
_ba={url:url,handleAs:"xml",preventCache:true};
_bb=dojo.xhrGet(_ba);
_bb.addCallback(_b6);
if(_b5.onError){
_bb.addErrback(function(_cc){
var s=_b5.scope||dojo.global;
_b5.onError.call(s,_cc);
});
}
}else{
if(_b8.keyAttribute!==""){
var _ce={query:{}};
_ce.query[_b8.keyAttribute]=_b5.identity;
url=this._getFetchUrl(_ce);
_b6=function(_cf){
var _d0=null;
if(_cf){
var _d1=_b8._getItems(_d1,null);
if(_d1.length===1){
_d0=_d1[0];
}else{
if(_b5.onError){
var _d2=_b5.scope||dojo.global;
_b5.onError.call(_d2,new Error("More than one item was returned from the server for the denoted identity"));
}
}
}
if(_b5.onItem){
_d2=_b5.scope||dojo.global;
_b5.onItem.call(_d2,_d0);
}
};
_ba={url:url,handleAs:"xml",preventCache:true};
_bb=dojo.xhrGet(_ba);
_bb.addCallback(_b6);
if(_b5.onError){
_bb.addErrback(function(_d3){
var s=_b5.scope||dojo.global;
_b5.onError.call(s,_d3);
});
}
}else{
if(_b5.onError){
var s=_b5.scope||dojo.global;
_b5.onError.call(s,new Error("XmlStore is not told that the server to provides identity support.  No keyAttribute specified."));
}
}
}
}});
dojo.declare("dojox.data.XmlItem",null,{constructor:function(_d6,_d7,_d8){
this.element=_d6;
this.store=_d7;
this.q=_d8;
},toString:function(){
var str="";
if(this.element){
for(var i=0;i<this.element.childNodes.length;i++){
var _db=this.element.childNodes[i];
if(_db.nodeType===3||_db.nodeType===4){
str+=_db.nodeValue;
}
}
}
return str;
}});
dojo.extend(dojox.data.XmlStore,dojo.data.util.simpleFetch);
}
