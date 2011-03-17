/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.tag.loader"]){
dojo._hasResource["dojox.dtl.tag.loader"]=true;
dojo.provide("dojox.dtl.tag.loader");
dojo.require("dojox.dtl._base");
(function(){
var dd=dojox.dtl;
var _2=dd.tag.loader;
_2.BlockNode=dojo.extend(function(_3,_4){
this.name=_3;
this.nodelist=_4;
},{"super":function(){
if(this.parent){
var _5=this.parent.nodelist.dummyRender(this.context,null,true);
if(typeof _5=="string"){
_5=new String(_5);
}
_5.safe=true;
return _5;
}
return "";
},render:function(_6,_7){
var _8=this.name;
var _9=this.nodelist;
var _a;
if(_7.blocks){
var _b=_7.blocks[_8];
if(_b){
_a=_b.parent;
_9=_b.nodelist;
_b.used=true;
}
}
this.rendered=_9;
_6=_6.push();
this.context=_6;
this.parent=null;
if(_9!=this.nodelist){
this.parent=this;
}
_6.block=this;
if(_7.getParent){
var _c=_7.getParent();
var _d=dojo.connect(_7,"onSetParent",function(_e,up,_10){
if(up&&_10){
_7.setParent(_c);
}
});
}
_7=_9.render(_6,_7,this);
_d&&dojo.disconnect(_d);
_6=_6.pop();
return _7;
},unrender:function(_11,_12){
return this.rendered.unrender(_11,_12);
},clone:function(_13){
return new this.constructor(this.name,this.nodelist.clone(_13));
},toString:function(){
return "dojox.dtl.tag.loader.BlockNode";
}});
_2.ExtendsNode=dojo.extend(function(_14,_15,_16,_17,key){
this.getTemplate=_14;
this.nodelist=_15;
this.shared=_16;
this.parent=_17;
this.key=key;
},{parents:{},getParent:function(_19){
var _1a=this.parent;
if(!_1a){
var _1b;
_1a=this.parent=_19.get(this.key,false);
if(!_1a){
throw new Error("extends tag used a variable that did not resolve");
}
if(typeof _1a=="object"){
var url=_1a.url||_1a.templatePath;
if(_1a.shared){
this.shared=true;
}
if(url){
_1a=this.parent=url.toString();
}else{
if(_1a.templateString){
_1b=_1a.templateString;
_1a=this.parent=" ";
}else{
_1a=this.parent=this.parent.toString();
}
}
}
if(_1a&&_1a.indexOf("shared:")===0){
this.shared=true;
_1a=this.parent=_1a.substring(7,_1a.length);
}
}
if(!_1a){
throw new Error("Invalid template name in 'extends' tag.");
}
if(_1a.render){
return _1a;
}
if(this.parents[_1a]){
return this.parents[_1a];
}
this.parent=this.getTemplate(_1b||dojox.dtl.text.getTemplateString(_1a));
if(this.shared){
this.parents[_1a]=this.parent;
}
return this.parent;
},render:function(_1d,_1e){
var _1f=this.getParent(_1d);
_1f.blocks=_1f.blocks||{};
_1e.blocks=_1e.blocks||{};
for(var i=0,_21;_21=this.nodelist.contents[i];i++){
if(_21 instanceof dojox.dtl.tag.loader.BlockNode){
var old=_1f.blocks[_21.name];
if(old&&old.nodelist!=_21.nodelist){
_1e=old.nodelist.unrender(_1d,_1e);
}
_1f.blocks[_21.name]=_1e.blocks[_21.name]={shared:this.shared,nodelist:_21.nodelist,used:false};
}
}
this.rendered=_1f;
return _1f.nodelist.render(_1d,_1e,this);
},unrender:function(_23,_24){
return this.rendered.unrender(_23,_24,this);
},toString:function(){
return "dojox.dtl.block.ExtendsNode";
}});
_2.IncludeNode=dojo.extend(function(_25,_26,_27,_28,_29){
this._path=_25;
this.constant=_26;
this.path=(_26)?_25:new dd._Filter(_25);
this.getTemplate=_27;
this.text=_28;
this.parsed=(arguments.length==5)?_29:true;
},{_cache:[{},{}],render:function(_2a,_2b){
var _2c=((this.constant)?this.path:this.path.resolve(_2a)).toString();
var _2d=Number(this.parsed);
var _2e=false;
if(_2c!=this.last){
_2e=true;
if(this.last){
_2b=this.unrender(_2a,_2b);
}
this.last=_2c;
}
var _2f=this._cache[_2d];
if(_2d){
if(!_2f[_2c]){
_2f[_2c]=dd.text._resolveTemplateArg(_2c,true);
}
if(_2e){
var _30=this.getTemplate(_2f[_2c]);
this.rendered=_30.nodelist;
}
return this.rendered.render(_2a,_2b,this);
}else{
if(this.text instanceof dd._TextNode){
if(_2e){
this.rendered=this.text;
this.rendered.set(dd.text._resolveTemplateArg(_2c,true));
}
return this.rendered.render(_2a,_2b);
}else{
if(!_2f[_2c]){
var _31=[];
var div=document.createElement("div");
div.innerHTML=dd.text._resolveTemplateArg(_2c,true);
var _33=div.childNodes;
while(_33.length){
var _34=div.removeChild(_33[0]);
_31.push(_34);
}
_2f[_2c]=_31;
}
if(_2e){
this.nodelist=[];
var _35=true;
for(var i=0,_37;_37=_2f[_2c][i];i++){
this.nodelist.push(_37.cloneNode(true));
}
}
for(var i=0,_38;_38=this.nodelist[i];i++){
_2b=_2b.concat(_38);
}
}
}
return _2b;
},unrender:function(_39,_3a){
if(this.rendered){
_3a=this.rendered.unrender(_39,_3a);
}
if(this.nodelist){
for(var i=0,_3c;_3c=this.nodelist[i];i++){
_3a=_3a.remove(_3c);
}
}
return _3a;
},clone:function(_3d){
return new this.constructor(this._path,this.constant,this.getTemplate,this.text.clone(_3d),this.parsed);
}});
dojo.mixin(_2,{block:function(_3e,_3f){
var _40=_3f.contents.split();
var _41=_40[1];
_3e._blocks=_3e._blocks||{};
_3e._blocks[_41]=_3e._blocks[_41]||[];
_3e._blocks[_41].push(_41);
var _42=_3e.parse(["endblock","endblock "+_41]).rtrim();
_3e.next_token();
return new dojox.dtl.tag.loader.BlockNode(_41,_42);
},extends_:function(_43,_44){
var _45=_44.contents.split();
var _46=false;
var _47=null;
var key=null;
if(_45[1].charAt(0)=="\""||_45[1].charAt(0)=="'"){
_47=_45[1].substring(1,_45[1].length-1);
}else{
key=_45[1];
}
if(_47&&_47.indexOf("shared:")==0){
_46=true;
_47=_47.substring(7,_47.length);
}
var _49=_43.parse();
return new dojox.dtl.tag.loader.ExtendsNode(_43.getTemplate,_49,_46,_47,key);
},include:function(_4a,_4b){
var _4c=_4b.contents.split();
if(_4c.length!=2){
throw new Error(_4c[0]+" tag takes one argument: the name of the template to be included");
}
var _4d=_4c[1];
var _4e=false;
if((_4d.charAt(0)=="\""||_4d.slice(-1)=="'")&&_4d.charAt(0)==_4d.slice(-1)){
_4d=_4d.slice(1,-1);
_4e=true;
}
return new _2.IncludeNode(_4d,_4e,_4a.getTemplate,_4a.create_text_node());
},ssi:function(_4f,_50){
var _51=_50.contents.split();
var _52=false;
if(_51.length==3){
_52=(_51.pop()=="parsed");
if(!_52){
throw new Error("Second (optional) argument to ssi tag must be 'parsed'");
}
}
var _53=_2.include(_4f,new dd.Token(_50.token_type,_51.join(" ")));
_53.parsed=_52;
return _53;
}});
})();
}
