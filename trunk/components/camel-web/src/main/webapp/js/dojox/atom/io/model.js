/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.atom.io.model"]){
dojo._hasResource["dojox.atom.io.model"]=true;
dojo.provide("dojox.atom.io.model");
dojo.require("dojox.xml.parser");
dojo.require("dojo.string");
dojo.require("dojo.date.stamp");
dojo.requireLocalization("dojox.atom.io","messages",null,"");
dojox.atom.io.model._Constants={"ATOM_URI":"http://www.w3.org/2005/Atom","ATOM_NS":"http://www.w3.org/2005/Atom","PURL_NS":"http://purl.org/atom/app#","APP_NS":"http://www.w3.org/2007/app"};
dojox.atom.io.model._actions={"link":function(_1,_2){
if(_1.links===null){
_1.links=[];
}
var _3=new dojox.atom.io.model.Link();
_3.buildFromDom(_2);
_1.links.push(_3);
},"author":function(_4,_5){
if(_4.authors===null){
_4.authors=[];
}
var _6=new dojox.atom.io.model.Person("author");
_6.buildFromDom(_5);
_4.authors.push(_6);
},"contributor":function(_7,_8){
if(_7.contributors===null){
_7.contributors=[];
}
var _9=new dojox.atom.io.model.Person("contributor");
_9.buildFromDom(_8);
_7.contributors.push(_9);
},"category":function(_a,_b){
if(_a.categories===null){
_a.categories=[];
}
var _c=new dojox.atom.io.model.Category();
_c.buildFromDom(_b);
_a.categories.push(_c);
},"icon":function(_d,_e){
_d.icon=dojox.xml.parser.textContent(_e);
},"id":function(_f,_10){
_f.id=dojox.xml.parser.textContent(_10);
},"rights":function(obj,_12){
obj.rights=dojox.xml.parser.textContent(_12);
},"subtitle":function(obj,_14){
var cnt=new dojox.atom.io.model.Content("subtitle");
cnt.buildFromDom(_14);
obj.subtitle=cnt;
},"title":function(obj,_17){
var cnt=new dojox.atom.io.model.Content("title");
cnt.buildFromDom(_17);
obj.title=cnt;
},"updated":function(obj,_1a){
obj.updated=dojox.atom.io.model.util.createDate(_1a);
},"issued":function(obj,_1c){
obj.issued=dojox.atom.io.model.util.createDate(_1c);
},"modified":function(obj,_1e){
obj.modified=dojox.atom.io.model.util.createDate(_1e);
},"published":function(obj,_20){
obj.published=dojox.atom.io.model.util.createDate(_20);
},"entry":function(obj,_22){
if(obj.entries===null){
obj.entries=[];
}
var _23=obj.createEntry?obj.createEntry():new dojox.atom.io.model.Entry();
_23.buildFromDom(_22);
obj.entries.push(_23);
},"content":function(obj,_25){
var cnt=new dojox.atom.io.model.Content("content");
cnt.buildFromDom(_25);
obj.content=cnt;
},"summary":function(obj,_28){
var _29=new dojox.atom.io.model.Content("summary");
_29.buildFromDom(_28);
obj.summary=_29;
},"name":function(obj,_2b){
obj.name=dojox.xml.parser.textContent(_2b);
},"email":function(obj,_2d){
obj.email=dojox.xml.parser.textContent(_2d);
},"uri":function(obj,_2f){
obj.uri=dojox.xml.parser.textContent(_2f);
},"generator":function(obj,_31){
obj.generator=new dojox.atom.io.model.Generator();
obj.generator.buildFromDom(_31);
}};
dojox.atom.io.model.util={createDate:function(_32){
var _33=dojox.xml.parser.textContent(_32);
if(_33){
return dojo.date.stamp.fromISOString(dojo.trim(_33));
}
return null;
},escapeHtml:function(str){
str=str.replace(/&/gm,"&amp;").replace(/</gm,"&lt;").replace(/>/gm,"&gt;").replace(/"/gm,"&quot;");
str=str.replace(/'/gm,"&#39;");
return str;
},unEscapeHtml:function(str){
str=str.replace(/&amp;/gm,"&").replace(/&lt;/gm,"<").replace(/&gt;/gm,">").replace(/&quot;/gm,"\"");
str=str.replace(/&#39;/gm,"'");
return str;
},getNodename:function(_36){
var _37=null;
if(_36!==null){
_37=_36.localName?_36.localName:_36.nodeName;
if(_37!==null){
var _38=_37.indexOf(":");
if(_38!==-1){
_37=_37.substring((_38+1),_37.length);
}
}
}
return _37;
}};
dojo.declare("dojox.atom.io.model.Node",null,{constructor:function(_39,_3a,_3b,_3c,_3d){
this.name_space=_39;
this.name=_3a;
this.attributes=[];
if(_3b){
this.attributes=_3b;
}
this.content=[];
this.rawNodes=[];
this.textContent=null;
if(_3c){
this.content.push(_3c);
}
this.shortNs=_3d;
this._objName="Node";
},buildFromDom:function(_3e){
this._saveAttributes(_3e);
this.name_space=_3e.namespaceURI;
this.shortNs=_3e.prefix;
this.name=dojox.atom.io.model.util.getNodename(_3e);
for(var x=0;x<_3e.childNodes.length;x++){
var c=_3e.childNodes[x];
if(dojox.atom.io.model.util.getNodename(c)!="#text"){
this.rawNodes.push(c);
var n=new dojox.atom.io.model.Node();
n.buildFromDom(c,true);
this.content.push(n);
}else{
this.content.push(c.nodeValue);
}
}
this.textContent=dojox.xml.parser.textContent(_3e);
},_saveAttributes:function(_42){
if(!this.attributes){
this.attributes=[];
}
var _43=function(_44){
var _45=_44.attributes;
if(_45===null){
return false;
}
return (_45.length!==0);
};
if(_43(_42)&&this._getAttributeNames){
var _46=this._getAttributeNames(_42);
if(_46&&_46.length>0){
for(var x in _46){
var _48=_42.getAttribute(_46[x]);
if(_48){
this.attributes[_46[x]]=_48;
}
}
}
}
},addAttribute:function(_49,_4a){
this.attributes[_49]=_4a;
},getAttribute:function(_4b){
return this.attributes[_4b];
},_getAttributeNames:function(_4c){
var _4d=[];
for(var i=0;i<_4c.attributes.length;i++){
_4d.push(_4c.attributes[i].nodeName);
}
return _4d;
},toString:function(){
var xml=[];
var x;
var _51=(this.shortNs?this.shortNs+":":"")+this.name;
var _52=(this.name=="#cdata-section");
if(_52){
xml.push("<![CDATA[");
xml.push(this.textContent);
xml.push("]]>");
}else{
xml.push("<");
xml.push(_51);
if(this.name_space){
xml.push(" xmlns='"+this.name_space+"'");
}
if(this.attributes){
for(x in this.attributes){
xml.push(" "+x+"='"+this.attributes[x]+"'");
}
}
if(this.content){
xml.push(">");
for(x in this.content){
xml.push(this.content[x]);
}
xml.push("</"+_51+">\n");
}else{
xml.push("/>\n");
}
}
return xml.join("");
},addContent:function(_53){
this.content.push(_53);
}});
dojo.declare("dojox.atom.io.model.AtomItem",dojox.atom.io.model.Node,{constructor:function(_54){
this.ATOM_URI=dojox.atom.io.model._Constants.ATOM_URI;
this.links=null;
this.authors=null;
this.categories=null;
this.contributors=null;
this.icon=this.id=this.logo=this.xmlBase=this.rights=null;
this.subtitle=this.title=null;
this.updated=this.published=null;
this.issued=this.modified=null;
this.content=null;
this.extensions=null;
this.entries=null;
this.name_spaces={};
this._objName="AtomItem";
},_getAttributeNames:function(){
return null;
},_accepts:{},accept:function(tag){
return Boolean(this._accepts[tag]);
},_postBuild:function(){
},buildFromDom:function(_56){
var i,c,n;
for(i=0;i<_56.attributes.length;i++){
c=_56.attributes.item(i);
n=dojox.atom.io.model.util.getNodename(c);
if(c.prefix=="xmlns"&&c.prefix!=n){
this.addNamespace(c.nodeValue,n);
}
}
c=_56.childNodes;
for(i=0;i<c.length;i++){
if(c[i].nodeType==1){
var _5a=dojox.atom.io.model.util.getNodename(c[i]);
if(!_5a){
continue;
}
if(c[i].namespaceURI!=dojox.atom.io.model._Constants.ATOM_NS&&_5a!="#text"){
if(!this.extensions){
this.extensions=[];
}
var _5b=new dojox.atom.io.model.Node();
_5b.buildFromDom(c[i]);
this.extensions.push(_5b);
}
if(!this.accept(_5a.toLowerCase())){
continue;
}
var fn=dojox.atom.io.model._actions[_5a];
if(fn){
fn(this,c[i]);
}
}
}
this._saveAttributes(_56);
if(this._postBuild){
this._postBuild();
}
},addNamespace:function(_5d,_5e){
if(_5d&&_5e){
this.name_spaces[_5e]=_5d;
}
},addAuthor:function(_5f,_60,uri){
if(!this.authors){
this.authors=[];
}
this.authors.push(new dojox.atom.io.model.Person("author",_5f,_60,uri));
},addContributor:function(_62,_63,uri){
if(!this.contributors){
this.contributors=[];
}
this.contributors.push(new dojox.atom.io.model.Person("contributor",_62,_63,uri));
},addLink:function(_65,rel,_67,_68,_69){
if(!this.links){
this.links=[];
}
this.links.push(new dojox.atom.io.model.Link(_65,rel,_67,_68,_69));
},removeLink:function(_6a,rel){
if(!this.links||!dojo.isArray(this.links)){
return;
}
var _6c=0;
for(var i=0;i<this.links.length;i++){
if((!_6a||this.links[i].href===_6a)&&(!rel||this.links[i].rel===rel)){
this.links.splice(i,1);
_6c++;
}
}
return _6c;
},removeBasicLinks:function(){
if(!this.links){
return;
}
var _6e=0;
for(var i=0;i<this.links.length;i++){
if(!this.links[i].rel){
this.links.splice(i,1);
_6e++;
i--;
}
}
return _6e;
},addCategory:function(_70,_71,_72){
if(!this.categories){
this.categories=[];
}
this.categories.push(new dojox.atom.io.model.Category(_70,_71,_72));
},getCategories:function(_73){
if(!_73){
return this.categories;
}
var arr=[];
for(var x in this.categories){
if(this.categories[x].scheme===_73){
arr.push(this.categories[x]);
}
}
return arr;
},removeCategories:function(_76,_77){
if(!this.categories){
return;
}
var _78=0;
for(var i=0;i<this.categories.length;i++){
if((!_76||this.categories[i].scheme===_76)&&(!_77||this.categories[i].term===_77)){
this.categories.splice(i,1);
_78++;
i--;
}
}
return _78;
},setTitle:function(str,_7b){
if(!str){
return;
}
this.title=new dojox.atom.io.model.Content("title");
this.title.value=str;
if(_7b){
this.title.type=_7b;
}
},addExtension:function(_7c,_7d,_7e,_7f,_80){
if(!this.extensions){
this.extensions=[];
}
this.extensions.push(new dojox.atom.io.model.Node(_7c,_7d,_7e,_7f,_80||"ns"+this.extensions.length));
},getExtensions:function(_81,_82){
var arr=[];
if(!this.extensions){
return arr;
}
for(var x in this.extensions){
if((this.extensions[x].name_space===_81||this.extensions[x].shortNs===_81)&&(!_82||this.extensions[x].name===_82)){
arr.push(this.extensions[x]);
}
}
return arr;
},removeExtensions:function(_85,_86){
if(!this.extensions){
return;
}
for(var i=0;i<this.extensions.length;i++){
if((this.extensions[i].name_space==_85||this.extensions[i].shortNs===_85)&&this.extensions[i].name===_86){
this.extensions.splice(i,1);
i--;
}
}
},destroy:function(){
this.links=null;
this.authors=null;
this.categories=null;
this.contributors=null;
this.icon=this.id=this.logo=this.xmlBase=this.rights=null;
this.subtitle=this.title=null;
this.updated=this.published=null;
this.issued=this.modified=null;
this.content=null;
this.extensions=null;
this.entries=null;
}});
dojo.declare("dojox.atom.io.model.Category",dojox.atom.io.model.Node,{constructor:function(_88,_89,_8a){
this.scheme=_88;
this.term=_89;
this.label=_8a;
this._objName="Category";
},_postBuild:function(){
},_getAttributeNames:function(){
return ["label","scheme","term"];
},toString:function(){
var s=[];
s.push("<category ");
if(this.label){
s.push(" label=\""+this.label+"\" ");
}
if(this.scheme){
s.push(" scheme=\""+this.scheme+"\" ");
}
if(this.term){
s.push(" term=\""+this.term+"\" ");
}
s.push("/>\n");
return s.join("");
},buildFromDom:function(_8c){
this._saveAttributes(_8c);
this.label=this.attributes.label;
this.scheme=this.attributes.scheme;
this.term=this.attributes.term;
if(this._postBuild){
this._postBuild();
}
}});
dojo.declare("dojox.atom.io.model.Content",dojox.atom.io.model.Node,{constructor:function(_8d,_8e,src,_90,_91){
this.tagName=_8d;
this.value=_8e;
this.src=src;
this.type=_90;
this.xmlLang=_91;
this.HTML="html";
this.TEXT="text";
this.XHTML="xhtml";
this.XML="xml";
this._useTextContent="true";
},_getAttributeNames:function(){
return ["type","src"];
},_postBuild:function(){
},buildFromDom:function(_92){
if(_92.innerHTML){
this.value=_92.innerHTML;
}else{
this.value=dojox.xml.parser.textContent(_92);
}
this._saveAttributes(_92);
if(this.attributes){
this.type=this.attributes.type;
this.scheme=this.attributes.scheme;
this.term=this.attributes.term;
}
if(!this.type){
this.type="text";
}
var _93=this.type.toLowerCase();
if(_93==="html"||_93==="text/html"||_93==="xhtml"||_93==="text/xhtml"){
this.value=dojox.atom.io.model.util.unEscapeHtml(this.value);
}
if(this._postBuild){
this._postBuild();
}
},toString:function(){
var s=[];
s.push("<"+this.tagName+" ");
if(!this.type){
this.type="text";
}
if(this.type){
s.push(" type=\""+this.type+"\" ");
}
if(this.xmlLang){
s.push(" xml:lang=\""+this.xmlLang+"\" ");
}
if(this.xmlBase){
s.push(" xml:base=\""+this.xmlBase+"\" ");
}
if(this.type.toLowerCase()==this.HTML){
s.push(">"+dojox.atom.io.model.util.escapeHtml(this.value)+"</"+this.tagName+">\n");
}else{
s.push(">"+this.value+"</"+this.tagName+">\n");
}
var ret=s.join("");
return ret;
}});
dojo.declare("dojox.atom.io.model.Link",dojox.atom.io.model.Node,{constructor:function(_96,rel,_98,_99,_9a){
this.href=_96;
this.hrefLang=_98;
this.rel=rel;
this.title=_99;
this.type=_9a;
},_getAttributeNames:function(){
return ["href","jrefLang","rel","title","type"];
},_postBuild:function(){
},buildFromDom:function(_9b){
this._saveAttributes(_9b);
this.href=this.attributes.href;
this.hrefLang=this.attributes.hreflang;
this.rel=this.attributes.rel;
this.title=this.attributes.title;
this.type=this.attributes.type;
if(this._postBuild){
this._postBuild();
}
},toString:function(){
var s=[];
s.push("<link ");
if(this.href){
s.push(" href=\""+this.href+"\" ");
}
if(this.hrefLang){
s.push(" hrefLang=\""+this.hrefLang+"\" ");
}
if(this.rel){
s.push(" rel=\""+this.rel+"\" ");
}
if(this.title){
s.push(" title=\""+this.title+"\" ");
}
if(this.type){
s.push(" type = \""+this.type+"\" ");
}
s.push("/>\n");
return s.join("");
}});
dojo.declare("dojox.atom.io.model.Person",dojox.atom.io.model.Node,{constructor:function(_9d,_9e,_9f,uri){
this.author="author";
this.contributor="contributor";
if(!_9d){
_9d=this.author;
}
this.personType=_9d;
this.name=_9e||"";
this.email=_9f||"";
this.uri=uri||"";
this._objName="Person";
},_getAttributeNames:function(){
return null;
},_postBuild:function(){
},accept:function(tag){
return Boolean(this._accepts[tag]);
},buildFromDom:function(_a2){
var c=_a2.childNodes;
for(var i=0;i<c.length;i++){
var _a5=dojox.atom.io.model.util.getNodename(c[i]);
if(!_a5){
continue;
}
if(c[i].namespaceURI!=dojox.atom.io.model._Constants.ATOM_NS&&_a5!="#text"){
if(!this.extensions){
this.extensions=[];
}
var _a6=new dojox.atom.io.model.Node();
_a6.buildFromDom(c[i]);
this.extensions.push(_a6);
}
if(!this.accept(_a5.toLowerCase())){
continue;
}
var fn=dojox.atom.io.model._actions[_a5];
if(fn){
fn(this,c[i]);
}
}
this._saveAttributes(_a2);
if(this._postBuild){
this._postBuild();
}
},_accepts:{"name":true,"uri":true,"email":true},toString:function(){
var s=[];
s.push("<"+this.personType+">\n");
if(this.name){
s.push("\t<name>"+this.name+"</name>\n");
}
if(this.email){
s.push("\t<email>"+this.email+"</email>\n");
}
if(this.uri){
s.push("\t<uri>"+this.uri+"</uri>\n");
}
s.push("</"+this.personType+">\n");
return s.join("");
}});
dojo.declare("dojox.atom.io.model.Generator",dojox.atom.io.model.Node,{constructor:function(uri,_aa,_ab){
this.uri=uri;
this.version=_aa;
this.value=_ab;
},_postBuild:function(){
},buildFromDom:function(_ac){
this.value=dojox.xml.parser.textContent(_ac);
this._saveAttributes(_ac);
this.uri=this.attributes.uri;
this.version=this.attributes.version;
if(this._postBuild){
this._postBuild();
}
},toString:function(){
var s=[];
s.push("<generator ");
if(this.uri){
s.push(" uri=\""+this.uri+"\" ");
}
if(this.version){
s.push(" version=\""+this.version+"\" ");
}
s.push(">"+this.value+"</generator>\n");
var ret=s.join("");
return ret;
}});
dojo.declare("dojox.atom.io.model.Entry",dojox.atom.io.model.AtomItem,{constructor:function(id){
this.id=id;
this._objName="Entry";
this.feedUrl=null;
},_getAttributeNames:function(){
return null;
},_accepts:{"author":true,"content":true,"category":true,"contributor":true,"created":true,"id":true,"link":true,"published":true,"rights":true,"summary":true,"title":true,"updated":true,"xmlbase":true,"issued":true,"modified":true},toString:function(_b0){
var s=[];
var i;
if(_b0){
s.push("<?xml version='1.0' encoding='UTF-8'?>");
s.push("<entry xmlns='"+dojox.atom.io.model._Constants.ATOM_URI+"'");
}else{
s.push("<entry");
}
if(this.xmlBase){
s.push(" xml:base=\""+this.xmlBase+"\" ");
}
for(i in this.name_spaces){
s.push(" xmlns:"+i+"=\""+this.name_spaces[i]+"\"");
}
s.push(">\n");
s.push("<id>"+(this.id?this.id:"")+"</id>\n");
if(this.issued&&!this.published){
this.published=this.issued;
}
if(this.published){
s.push("<published>"+dojo.date.stamp.toISOString(this.published)+"</published>\n");
}
if(this.created){
s.push("<created>"+dojo.date.stamp.toISOString(this.created)+"</created>\n");
}
if(this.issued){
s.push("<issued>"+dojo.date.stamp.toISOString(this.issued)+"</issued>\n");
}
if(this.modified){
s.push("<modified>"+dojo.date.stamp.toISOString(this.modified)+"</modified>\n");
}
if(this.modified&&!this.updated){
this.updated=this.modified;
}
if(this.updated){
s.push("<updated>"+dojo.date.stamp.toISOString(this.updated)+"</updated>\n");
}
if(this.rights){
s.push("<rights>"+this.rights+"</rights>\n");
}
if(this.title){
s.push(this.title.toString());
}
if(this.summary){
s.push(this.summary.toString());
}
var _b3=[this.authors,this.categories,this.links,this.contributors,this.extensions];
for(var x in _b3){
if(_b3[x]){
for(var y in _b3[x]){
s.push(_b3[x][y]);
}
}
}
if(this.content){
s.push(this.content.toString());
}
s.push("</entry>\n");
return s.join("");
},getEditHref:function(){
if(this.links===null||this.links.length===0){
return null;
}
for(var x in this.links){
if(this.links[x].rel&&this.links[x].rel=="edit"){
return this.links[x].href;
}
}
return null;
},setEditHref:function(url){
if(this.links===null){
this.links=[];
}
for(var x in this.links){
if(this.links[x].rel&&this.links[x].rel=="edit"){
this.links[x].href=url;
return;
}
}
this.addLink(url,"edit");
}});
dojo.declare("dojox.atom.io.model.Feed",dojox.atom.io.model.AtomItem,{_accepts:{"author":true,"content":true,"category":true,"contributor":true,"created":true,"id":true,"link":true,"published":true,"rights":true,"summary":true,"title":true,"updated":true,"xmlbase":true,"entry":true,"logo":true,"issued":true,"modified":true,"icon":true,"subtitle":true},addEntry:function(_b9){
if(!_b9.id){
var _ba=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_ba.noId);
}
if(!this.entries){
this.entries=[];
}
_b9.feedUrl=this.getSelfHref();
this.entries.push(_b9);
},getFirstEntry:function(){
if(!this.entries||this.entries.length===0){
return null;
}
return this.entries[0];
},getEntry:function(_bb){
if(!this.entries){
return null;
}
for(var x in this.entries){
if(this.entries[x].id==_bb){
return this.entries[x];
}
}
return null;
},removeEntry:function(_bd){
if(!this.entries){
return;
}
var _be=0;
for(var i=0;i<this.entries.length;i++){
if(this.entries[i]===_bd){
this.entries.splice(i,1);
_be++;
}
}
return _be;
},setEntries:function(_c0){
for(var x in _c0){
this.addEntry(_c0[x]);
}
},toString:function(){
var s=[];
var i;
s.push("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
s.push("<feed xmlns=\""+dojox.atom.io.model._Constants.ATOM_URI+"\"");
if(this.xmlBase){
s.push(" xml:base=\""+this.xmlBase+"\"");
}
for(i in this.name_spaces){
s.push(" xmlns:"+i+"=\""+this.name_spaces[i]+"\"");
}
s.push(">\n");
s.push("<id>"+(this.id?this.id:"")+"</id>\n");
if(this.title){
s.push(this.title);
}
if(this.copyright&&!this.rights){
this.rights=this.copyright;
}
if(this.rights){
s.push("<rights>"+this.rights+"</rights>\n");
}
if(this.issued){
s.push("<issued>"+dojo.date.stamp.toISOString(this.issued)+"</issued>\n");
}
if(this.modified){
s.push("<modified>"+dojo.date.stamp.toISOString(this.modified)+"</modified>\n");
}
if(this.modified&&!this.updated){
this.updated=this.modified;
}
if(this.updated){
s.push("<updated>"+dojo.date.stamp.toISOString(this.updated)+"</updated>\n");
}
if(this.published){
s.push("<published>"+dojo.date.stamp.toISOString(this.published)+"</published>\n");
}
if(this.icon){
s.push("<icon>"+this.icon+"</icon>\n");
}
if(this.language){
s.push("<language>"+this.language+"</language>\n");
}
if(this.logo){
s.push("<logo>"+this.logo+"</logo>\n");
}
if(this.subtitle){
s.push(this.subtitle.toString());
}
if(this.tagline){
s.push(this.tagline.toString());
}
var _c4=[this.alternateLinks,this.authors,this.categories,this.contributors,this.otherLinks,this.extensions,this.entries];
for(i in _c4){
if(_c4[i]){
for(var x in _c4[i]){
s.push(_c4[i][x]);
}
}
}
s.push("</feed>");
return s.join("");
},createEntry:function(){
var _c6=new dojox.atom.io.model.Entry();
_c6.feedUrl=this.getSelfHref();
return _c6;
},getSelfHref:function(){
if(this.links===null||this.links.length===0){
return null;
}
for(var x in this.links){
if(this.links[x].rel&&this.links[x].rel=="self"){
return this.links[x].href;
}
}
return null;
}});
dojo.declare("dojox.atom.io.model.Service",dojox.atom.io.model.AtomItem,{constructor:function(_c8){
this.href=_c8;
},buildFromDom:function(_c9){
var _ca;
var i;
var len=_c9.childNodes?_c9.childNodes.length:0;
this.workspaces=[];
if(_c9.tagName!="service"){
return;
}
if(_c9.namespaceURI!=dojox.atom.io.model._Constants.PURL_NS&&_c9.namespaceURI!=dojox.atom.io.model._Constants.APP_NS){
return;
}
var ns=_c9.namespaceURI;
this.name_space=_c9.namespaceURI;
var _ce;
if(typeof (_c9.getElementsByTagNameNS)!="undefined"){
_ce=_c9.getElementsByTagNameNS(ns,"workspace");
}else{
_ce=[];
var _cf=_c9.getElementsByTagName("workspace");
for(i=0;i<_cf.length;i++){
if(_cf[i].namespaceURI==ns){
_ce.push(_cf[i]);
}
}
}
if(_ce&&_ce.length>0){
var _d0=0;
var _d1;
for(i=0;i<_ce.length;i++){
_d1=(typeof (_ce.item)==="undefined"?_ce[i]:_ce.item(i));
var _d2=new dojox.atom.io.model.Workspace();
_d2.buildFromDom(_d1);
this.workspaces[_d0++]=_d2;
}
}
},getCollection:function(url){
for(var i=0;i<this.workspaces.length;i++){
var _d5=this.workspaces[i].collections;
for(var j=0;j<_d5.length;j++){
if(_d5[j].href==url){
return _d5;
}
}
}
return null;
}});
dojo.declare("dojox.atom.io.model.Workspace",dojox.atom.io.model.AtomItem,{constructor:function(_d7){
this.title=_d7;
this.collections=[];
},buildFromDom:function(_d8){
var _d9=dojox.atom.io.model.util.getNodename(_d8);
if(_d9!="workspace"){
return;
}
var c=_d8.childNodes;
var len=0;
for(var i=0;i<c.length;i++){
var _dd=c[i];
if(_dd.nodeType===1){
_d9=dojox.atom.io.model.util.getNodename(_dd);
if(_dd.namespaceURI==dojox.atom.io.model._Constants.PURL_NS||_dd.namespaceURI==dojox.atom.io.model._Constants.APP_NS){
if(_d9==="collection"){
var _de=new dojox.atom.io.model.Collection();
_de.buildFromDom(_dd);
this.collections[len++]=_de;
}
}else{
if(_dd.namespaceURI===dojox.atom.io.model._Constants.ATOM_NS){
if(_d9==="title"){
this.title=dojox.xml.parser.textContent(_dd);
}
}else{
var _df=dojo.i18n.getLocalization("dojox.atom.io","messages");
throw new Error(_df.badNS);
}
}
}
}
}});
dojo.declare("dojox.atom.io.model.Collection",dojox.atom.io.model.AtomItem,{constructor:function(_e0,_e1){
this.href=_e0;
this.title=_e1;
this.attributes=[];
this.features=[];
this.children=[];
this.memberType=null;
this.id=null;
},buildFromDom:function(_e2){
this.href=_e2.getAttribute("href");
var c=_e2.childNodes;
for(var i=0;i<c.length;i++){
var _e5=c[i];
if(_e5.nodeType===1){
var _e6=dojox.atom.io.model.util.getNodename(_e5);
if(_e5.namespaceURI==dojox.atom.io.model._Constants.PURL_NS||_e5.namespaceURI==dojox.atom.io.model._Constants.APP_NS){
if(_e6==="member-type"){
this.memberType=dojox.xml.parser.textContent(_e5);
}else{
if(_e6=="feature"){
if(_e5.getAttribute("id")){
this.features.push(_e5.getAttribute("id"));
}
}else{
var _e7=new dojox.atom.io.model.Node();
_e7.buildFromDom(_e5);
this.children.push(_e7);
}
}
}else{
if(_e5.namespaceURI===dojox.atom.io.model._Constants.ATOM_NS){
if(_e6==="id"){
this.id=dojox.xml.parser.textContent(_e5);
}else{
if(_e6==="title"){
this.title=dojox.xml.parser.textContent(_e5);
}
}
}
}
}
}
}});
}
