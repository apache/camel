/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl._base"]){
dojo._hasResource["dojox.dtl._base"]=true;
dojo.provide("dojox.dtl._base");
dojo.require("dojox.string.Builder");
dojo.require("dojox.string.tokenize");
dojo.experimental("dojox.dtl");
(function(){
var dd=dojox.dtl;
dd.TOKEN_BLOCK=-1;
dd.TOKEN_VAR=-2;
dd.TOKEN_COMMENT=-3;
dd.TOKEN_TEXT=3;
dd._Context=dojo.extend(function(_2){
dojo._mixin(this,_2||{});
this._dicts=[];
},{push:function(){
var _3=this;
var _4=dojo.delegate(this);
_4.pop=function(){
return _3;
};
return _4;
},pop:function(){
throw new Error("pop() called on empty Context");
},get:function(_5,_6){
if(typeof this[_5]!="undefined"){
return this._normalize(this[_5]);
}
for(var i=0,_8;_8=this._dicts[i];i++){
if(typeof _8[_5]!="undefined"){
return this._normalize(_8[_5]);
}
}
return _6;
},_normalize:function(_9){
if(_9 instanceof Date){
_9.year=_9.getFullYear();
_9.month=_9.getMonth()+1;
_9.day=_9.getDate();
_9.date=_9.year+"-"+("0"+_9.month).slice(-2)+"-"+("0"+_9.day).slice(-2);
_9.hour=_9.getHours();
_9.minute=_9.getMinutes();
_9.second=_9.getSeconds();
_9.microsecond=_9.getMilliseconds();
}
return _9;
},update:function(_a){
var _b=this.push();
if(_a){
dojo._mixin(this,_a);
}
return _b;
}});
var _c=/("(?:[^"\\]*(?:\\.[^"\\]*)*)"|'(?:[^'\\]*(?:\\.[^'\\]*)*)'|[^\s]+)/g;
var _d=/\s+/g;
var _e=function(_f,_10){
_f=_f||_d;
if(!(_f instanceof RegExp)){
_f=new RegExp(_f,"g");
}
if(!_f.global){
throw new Error("You must use a globally flagged RegExp with split "+_f);
}
_f.exec("");
var _11,_12=[],_13=0,i=0;
while(_11=_f.exec(this)){
_12.push(this.slice(_13,_f.lastIndex-_11[0].length));
_13=_f.lastIndex;
if(_10&&(++i>_10-1)){
break;
}
}
_12.push(this.slice(_13));
return _12;
};
dd.Token=function(_15,_16){
this.token_type=_15;
this.contents=new String(dojo.trim(_16));
this.contents.split=_e;
this.split=function(){
return String.prototype.split.apply(this.contents,arguments);
};
};
dd.Token.prototype.split_contents=function(_17){
var bit,_19=[],i=0;
_17=_17||999;
while(i++<_17&&(bit=_c.exec(this.contents))){
bit=bit[0];
if(bit.charAt(0)=="\""&&bit.slice(-1)=="\""){
_19.push("\""+bit.slice(1,-1).replace("\\\"","\"").replace("\\\\","\\")+"\"");
}else{
if(bit.charAt(0)=="'"&&bit.slice(-1)=="'"){
_19.push("'"+bit.slice(1,-1).replace("\\'","'").replace("\\\\","\\")+"'");
}else{
_19.push(bit);
}
}
}
return _19;
};
var ddt=dd.text={_get:function(_1c,_1d,_1e){
var _1f=dd.register.get(_1c,_1d.toLowerCase(),_1e);
if(!_1f){
if(!_1e){
throw new Error("No tag found for "+_1d);
}
return null;
}
var fn=_1f[1];
var _21=_1f[2];
var _22;
if(fn.indexOf(":")!=-1){
_22=fn.split(":");
fn=_22.pop();
}
dojo["require"](_21);
var _23=dojo.getObject(_21);
return _23[fn||_1d]||_23[_1d+"_"]||_23[fn+"_"];
},getTag:function(_24,_25){
return ddt._get("tag",_24,_25);
},getFilter:function(_26,_27){
return ddt._get("filter",_26,_27);
},getTemplate:function(_28){
return new dd.Template(ddt.getTemplateString(_28));
},getTemplateString:function(_29){
return dojo._getText(_29.toString())||"";
},_resolveLazy:function(_2a,_2b,_2c){
if(_2b){
if(_2c){
return dojo.fromJson(dojo._getText(_2a))||{};
}else{
return dd.text.getTemplateString(_2a);
}
}else{
return dojo.xhrGet({handleAs:(_2c)?"json":"text",url:_2a});
}
},_resolveTemplateArg:function(arg,_2e){
if(ddt._isTemplate(arg)){
if(!_2e){
var d=new dojo.Deferred();
d.callback(arg);
return d;
}
return arg;
}
return ddt._resolveLazy(arg,_2e);
},_isTemplate:function(arg){
return (typeof arg=="undefined")||(typeof arg=="string"&&(arg.match(/^\s*[<{]/)||arg.indexOf(" ")!=-1));
},_resolveContextArg:function(arg,_32){
if(arg.constructor==Object){
if(!_32){
var d=new dojo.Deferred;
d.callback(arg);
return d;
}
return arg;
}
return ddt._resolveLazy(arg,_32,true);
},_re:/(?:\{\{\s*(.+?)\s*\}\}|\{%\s*(load\s*)?(.+?)\s*%\})/g,tokenize:function(str){
return dojox.string.tokenize(str,ddt._re,ddt._parseDelims);
},_parseDelims:function(_35,_36,tag){
if(_35){
return [dd.TOKEN_VAR,_35];
}else{
if(_36){
var _38=dojo.trim(tag).split(/\s+/g);
for(var i=0,_3a;_3a=_38[i];i++){
dojo["require"](_3a);
}
}else{
return [dd.TOKEN_BLOCK,tag];
}
}
}};
dd.Template=dojo.extend(function(_3b,_3c){
var str=_3c?_3b:ddt._resolveTemplateArg(_3b,true)||"";
var _3e=ddt.tokenize(str);
var _3f=new dd._Parser(_3e);
this.nodelist=_3f.parse();
},{update:function(_40,_41){
return ddt._resolveContextArg(_41).addCallback(this,function(_42){
var _43=this.render(new dd._Context(_42));
if(_40.forEach){
_40.forEach(function(_44){
_44.innerHTML=_43;
});
}else{
dojo.byId(_40).innerHTML=_43;
}
return this;
});
},render:function(_45,_46){
_46=_46||this.getBuffer();
_45=_45||new dd._Context({});
return this.nodelist.render(_45,_46)+"";
},getBuffer:function(){
dojo.require("dojox.string.Builder");
return new dojox.string.Builder();
}});
var _47=/\{\{\s*(.+?)\s*\}\}/g;
dd.quickFilter=function(str){
if(!str){
return new dd._NodeList();
}
if(str.indexOf("{%")==-1){
return new dd._QuickNodeList(dojox.string.tokenize(str,_47,function(_49){
return new dd._Filter(_49);
}));
}
};
dd._QuickNodeList=dojo.extend(function(_4a){
this.contents=_4a;
},{render:function(_4b,_4c){
for(var i=0,l=this.contents.length;i<l;i++){
if(this.contents[i].resolve){
_4c=_4c.concat(this.contents[i].resolve(_4b));
}else{
_4c=_4c.concat(this.contents[i]);
}
}
return _4c;
},dummyRender:function(_4f){
return this.render(_4f,dd.Template.prototype.getBuffer()).toString();
},clone:function(_50){
return this;
}});
dd._Filter=dojo.extend(function(_51){
if(!_51){
throw new Error("Filter must be called with variable name");
}
this.contents=_51;
var _52=this._cache[_51];
if(_52){
this.key=_52[0];
this.filters=_52[1];
}else{
this.filters=[];
dojox.string.tokenize(_51,this._re,this._tokenize,this);
this._cache[_51]=[this.key,this.filters];
}
},{_cache:{},_re:/(?:^_\("([^\\"]*(?:\\.[^\\"])*)"\)|^"([^\\"]*(?:\\.[^\\"]*)*)"|^([a-zA-Z0-9_.]+)|\|(\w+)(?::(?:_\("([^\\"]*(?:\\.[^\\"])*)"\)|"([^\\"]*(?:\\.[^\\"]*)*)"|([a-zA-Z0-9_.]+)|'([^\\']*(?:\\.[^\\']*)*)'))?|^'([^\\']*(?:\\.[^\\']*)*)')/g,_values:{0:"\"",1:"\"",2:"",8:"\""},_args:{4:"\"",5:"\"",6:"",7:"'"},_tokenize:function(){
var pos,arg;
for(var i=0,has=[];i<arguments.length;i++){
has[i]=(typeof arguments[i]!="undefined"&&typeof arguments[i]=="string"&&arguments[i]);
}
if(!this.key){
for(pos in this._values){
if(has[pos]){
this.key=this._values[pos]+arguments[pos]+this._values[pos];
break;
}
}
}else{
for(pos in this._args){
if(has[pos]){
var _57=arguments[pos];
if(this._args[pos]=="'"){
_57=_57.replace(/\\'/g,"'");
}else{
if(this._args[pos]=="\""){
_57=_57.replace(/\\"/g,"\"");
}
}
arg=[!this._args[pos],_57];
break;
}
}
var fn=ddt.getFilter(arguments[3]);
if(!dojo.isFunction(fn)){
throw new Error(arguments[3]+" is not registered as a filter");
}
this.filters.push([fn,arg]);
}
},getExpression:function(){
return this.contents;
},resolve:function(_59){
if(typeof this.key=="undefined"){
return "";
}
var str=this.resolvePath(this.key,_59);
for(var i=0,_5c;_5c=this.filters[i];i++){
if(_5c[1]){
if(_5c[1][0]){
str=_5c[0](str,this.resolvePath(_5c[1][1],_59));
}else{
str=_5c[0](str,_5c[1][1]);
}
}else{
str=_5c[0](str);
}
}
return str;
},resolvePath:function(_5d,_5e){
var _5f,_60;
var _61=_5d.charAt(0);
var _62=_5d.slice(-1);
if(!isNaN(parseInt(_61))){
_5f=(_5d.indexOf(".")==-1)?parseInt(_5d):parseFloat(_5d);
}else{
if(_61=="\""&&_61==_62){
_5f=_5d.slice(1,-1);
}else{
if(_5d=="true"){
return true;
}
if(_5d=="false"){
return false;
}
if(_5d=="null"||_5d=="None"){
return null;
}
_60=_5d.split(".");
_5f=_5e.get(_60[0]);
if(dojo.isFunction(_5f)){
var _63=_5e.getThis&&_5e.getThis();
if(_5f.alters_data){
_5f="";
}else{
if(_63){
_5f=_5f.call(_63);
}else{
_5f="";
}
}
}
for(var i=1;i<_60.length;i++){
var _65=_60[i];
if(_5f){
var _66=_5f;
if(dojo.isObject(_5f)&&_65=="items"&&typeof _5f[_65]=="undefined"){
var _67=[];
for(var key in _5f){
_67.push([key,_5f[key]]);
}
_5f=_67;
continue;
}
if(_5f.get&&dojo.isFunction(_5f.get)&&_5f.get.safe){
_5f=_5f.get(_65);
}else{
if(typeof _5f[_65]=="undefined"){
_5f=_5f[_65];
break;
}else{
_5f=_5f[_65];
}
}
if(dojo.isFunction(_5f)){
if(_5f.alters_data){
_5f="";
}else{
_5f=_5f.call(_66);
}
}else{
if(_5f instanceof Date){
_5f=dd._Context.prototype._normalize(_5f);
}
}
}else{
return "";
}
}
}
}
return _5f;
}});
dd._TextNode=dd._Node=dojo.extend(function(obj){
this.contents=obj;
},{set:function(_6a){
this.contents=_6a;
return this;
},render:function(_6b,_6c){
return _6c.concat(this.contents);
},isEmpty:function(){
return !dojo.trim(this.contents);
},clone:function(){
return this;
}});
dd._NodeList=dojo.extend(function(_6d){
this.contents=_6d||[];
this.last="";
},{push:function(_6e){
this.contents.push(_6e);
return this;
},concat:function(_6f){
this.contents=this.contents.concat(_6f);
return this;
},render:function(_70,_71){
for(var i=0;i<this.contents.length;i++){
_71=this.contents[i].render(_70,_71);
if(!_71){
throw new Error("Template must return buffer");
}
}
return _71;
},dummyRender:function(_73){
return this.render(_73,dd.Template.prototype.getBuffer()).toString();
},unrender:function(){
return arguments[1];
},clone:function(){
return this;
},rtrim:function(){
while(1){
i=this.contents.length-1;
if(this.contents[i] instanceof dd._TextNode&&this.contents[i].isEmpty()){
this.contents.pop();
}else{
break;
}
}
return this;
}});
dd._VarNode=dojo.extend(function(str){
this.contents=new dd._Filter(str);
},{render:function(_75,_76){
var str=this.contents.resolve(_75);
if(!str.safe){
str=dd._base.escape(""+str);
}
return _76.concat(str);
}});
dd._noOpNode=new function(){
this.render=this.unrender=function(){
return arguments[1];
};
this.clone=function(){
return this;
};
};
dd._Parser=dojo.extend(function(_78){
this.contents=_78;
},{i:0,parse:function(_79){
var _7a={};
_79=_79||[];
for(var i=0;i<_79.length;i++){
_7a[_79[i]]=true;
}
var _7c=new dd._NodeList();
while(this.i<this.contents.length){
token=this.contents[this.i++];
if(typeof token=="string"){
_7c.push(new dd._TextNode(token));
}else{
var _7d=token[0];
var _7e=token[1];
if(_7d==dd.TOKEN_VAR){
_7c.push(new dd._VarNode(_7e));
}else{
if(_7d==dd.TOKEN_BLOCK){
if(_7a[_7e]){
--this.i;
return _7c;
}
var cmd=_7e.split(/\s+/g);
if(cmd.length){
cmd=cmd[0];
var fn=ddt.getTag(cmd);
if(fn){
_7c.push(fn(this,new dd.Token(_7d,_7e)));
}
}
}
}
}
}
if(_79.length){
throw new Error("Could not find closing tag(s): "+_79.toString());
}
this.contents.length=0;
return _7c;
},next_token:function(){
var _81=this.contents[this.i++];
return new dd.Token(_81[0],_81[1]);
},delete_first_token:function(){
this.i++;
},skip_past:function(_82){
while(this.i<this.contents.length){
var _83=this.contents[this.i++];
if(_83[0]==dd.TOKEN_BLOCK&&_83[1]==_82){
return;
}
}
throw new Error("Unclosed tag found when looking for "+_82);
},create_variable_node:function(_84){
return new dd._VarNode(_84);
},create_text_node:function(_85){
return new dd._TextNode(_85||"");
},getTemplate:function(_86){
return new dd.Template(_86);
}});
dd.register={_registry:{attributes:[],tags:[],filters:[]},get:function(_87,_88){
var _89=dd.register._registry[_87+"s"];
for(var i=0,_8b;_8b=_89[i];i++){
if(typeof _8b[0]=="string"){
if(_8b[0]==_88){
return _8b;
}
}else{
if(_88.match(_8b[0])){
return _8b;
}
}
}
},getAttributeTags:function(){
var _8c=[];
var _8d=dd.register._registry.attributes;
for(var i=0,_8f;_8f=_8d[i];i++){
if(_8f.length==3){
_8c.push(_8f);
}else{
var fn=dojo.getObject(_8f[1]);
if(fn&&dojo.isFunction(fn)){
_8f.push(fn);
_8c.push(_8f);
}
}
}
return _8c;
},_any:function(_91,_92,_93){
for(var _94 in _93){
for(var i=0,fn;fn=_93[_94][i];i++){
var key=fn;
if(dojo.isArray(fn)){
key=fn[0];
fn=fn[1];
}
if(typeof key=="string"){
if(key.substr(0,5)=="attr:"){
var _98=fn.toLowerCase();
if(_98.substr(0,5)=="attr:"){
_98=_98.slice(5);
}
dd.register._registry.attributes.push([_98,_92+"."+_94+"."+_98]);
}
key=key.toLowerCase();
}
dd.register._registry[_91].push([key,fn,_92+"."+_94]);
}
}
},tags:function(_99,_9a){
dd.register._any("tags",_99,_9a);
},filters:function(_9b,_9c){
dd.register._any("filters",_9b,_9c);
}};
var _9d=/&/g;
var _9e=/</g;
var _9f=/>/g;
var _a0=/'/g;
var _a1=/"/g;
dd._base.escape=function(_a2){
return dd.mark_safe(_a2.replace(_9d,"&amp;").replace(_9e,"&lt;").replace(_9f,"&gt;").replace(_a1,"&quot;").replace(_a0,"&#39;"));
};
dd._base.safe=function(_a3){
if(typeof _a3=="string"){
_a3=new String(_a3);
}
if(typeof _a3=="object"){
_a3.safe=true;
}
return _a3;
};
dd.mark_safe=dd._base.safe;
dd.register.tags("dojox.dtl.tag",{"date":["now"],"logic":["if","for","ifequal","ifnotequal"],"loader":["extends","block","include","load","ssi"],"misc":["comment","debug","filter","firstof","spaceless","templatetag","widthratio","with"],"loop":["cycle","ifchanged","regroup"]});
dd.register.filters("dojox.dtl.filter",{"dates":["date","time","timesince","timeuntil"],"htmlstrings":["linebreaks","linebreaksbr","removetags","striptags"],"integers":["add","get_digit"],"lists":["dictsort","dictsortreversed","first","join","length","length_is","random","slice","unordered_list"],"logic":["default","default_if_none","divisibleby","yesno"],"misc":["filesizeformat","pluralize","phone2numeric","pprint"],"strings":["addslashes","capfirst","center","cut","fix_ampersands","floatformat","iriencode","linenumbers","ljust","lower","make_list","rjust","slugify","stringformat","title","truncatewords","truncatewords_html","upper","urlencode","urlize","urlizetrunc","wordcount","wordwrap"]});
dd.register.filters("dojox.dtl",{"_base":["escape","safe"]});
})();
}
