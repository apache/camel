/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.tag.misc"]){
dojo._hasResource["dojox.dtl.tag.misc"]=true;
dojo.provide("dojox.dtl.tag.misc");
dojo.require("dojox.dtl._base");
(function(){
var dd=dojox.dtl;
var _2=dd.tag.misc;
_2.DebugNode=dojo.extend(function(_3){
this.text=_3;
},{render:function(_4,_5){
var _6=_4.getKeys();
var _7=[];
var _8={};
for(var i=0,_a;_a=_6[i];i++){
_8[_a]=_4[_a];
_7+="["+_a+": "+typeof _4[_a]+"]\n";
}

return this.text.set(_7).render(_4,_5,this);
},unrender:function(_b,_c){
return _c;
},clone:function(_d){
return new this.constructor(this.text.clone(_d));
},toString:function(){
return "ddtm.DebugNode";
}});
_2.FilterNode=dojo.extend(function(_e,_f){
this._varnode=_e;
this._nodelist=_f;
},{render:function(_10,_11){
var _12=this._nodelist.render(_10,new dojox.string.Builder());
_10=_10.update({"var":_12.toString()});
var _13=this._varnode.render(_10,_11);
_10=_10.pop();
return _11;
},unrender:function(_14,_15){
return _15;
},clone:function(_16){
return new this.constructor(this._expression,this._nodelist.clone(_16));
}});
_2.FirstOfNode=dojo.extend(function(_17,_18){
this._vars=_17;
this.vars=dojo.map(_17,function(_19){
return new dojox.dtl._Filter(_19);
});
this.contents=_18;
},{render:function(_1a,_1b){
for(var i=0,_1d;_1d=this.vars[i];i++){
var _1e=_1d.resolve(_1a);
if(typeof _1e!="undefined"){
if(_1e===null){
_1e="null";
}
this.contents.set(_1e);
return this.contents.render(_1a,_1b);
}
}
return this.contents.unrender(_1a,_1b);
},unrender:function(_1f,_20){
return this.contents.unrender(_1f,_20);
},clone:function(_21){
return new this.constructor(this._vars,this.contents.clone(_21));
}});
_2.SpacelessNode=dojo.extend(function(_22,_23){
this.nodelist=_22;
this.contents=_23;
},{render:function(_24,_25){
if(_25.getParent){
var _26=[dojo.connect(_25,"onAddNodeComplete",this,"_watch"),dojo.connect(_25,"onSetParent",this,"_watchParent")];
_25=this.nodelist.render(_24,_25);
dojo.disconnect(_26[0]);
dojo.disconnect(_26[1]);
}else{
var _27=this.nodelist.dummyRender(_24);
this.contents.set(_27.replace(/>\s+</g,"><"));
_25=this.contents.render(_24,_25);
}
return _25;
},unrender:function(_28,_29){
return this.nodelist.unrender(_28,_29);
},clone:function(_2a){
return new this.constructor(this.nodelist.clone(_2a),this.contents.clone(_2a));
},_isEmpty:function(_2b){
return (_2b.nodeType==3&&!_2b.data.match(/[^\s\n]/));
},_watch:function(_2c){
if(this._isEmpty(_2c)){
var _2d=false;
if(_2c.parentNode.firstChild==_2c){
_2c.parentNode.removeChild(_2c);
}
}else{
var _2e=_2c.parentNode.childNodes;
if(_2c.nodeType==1&&_2e.length>2){
for(var i=2,_30;_30=_2e[i];i++){
if(_2e[i-2].nodeType==1&&this._isEmpty(_2e[i-1])){
_2c.parentNode.removeChild(_2e[i-1]);
return;
}
}
}
}
},_watchParent:function(_31){
var _32=_31.childNodes;
if(_32.length){
while(_31.childNodes.length){
var _33=_31.childNodes[_31.childNodes.length-1];
if(!this._isEmpty(_33)){
return;
}
_31.removeChild(_33);
}
}
}});
_2.TemplateTagNode=dojo.extend(function(tag,_35){
this.tag=tag;
this.contents=_35;
},{mapping:{openblock:"{%",closeblock:"%}",openvariable:"{{",closevariable:"}}",openbrace:"{",closebrace:"}",opencomment:"{#",closecomment:"#}"},render:function(_36,_37){
this.contents.set(this.mapping[this.tag]);
return this.contents.render(_36,_37);
},unrender:function(_38,_39){
return this.contents.unrender(_38,_39);
},clone:function(_3a){
return new this.constructor(this.tag,this.contents.clone(_3a));
}});
_2.WidthRatioNode=dojo.extend(function(_3b,max,_3d,_3e){
this.current=new dd._Filter(_3b);
this.max=new dd._Filter(max);
this.width=_3d;
this.contents=_3e;
},{render:function(_3f,_40){
var _41=+this.current.resolve(_3f);
var max=+this.max.resolve(_3f);
if(typeof _41!="number"||typeof max!="number"||!max){
this.contents.set("");
}else{
this.contents.set(""+Math.round((_41/max)*this.width));
}
return this.contents.render(_3f,_40);
},unrender:function(_43,_44){
return this.contents.unrender(_43,_44);
},clone:function(_45){
return new this.constructor(this.current.getExpression(),this.max.getExpression(),this.width,this.contents.clone(_45));
}});
_2.WithNode=dojo.extend(function(_46,_47,_48){
this.target=new dd._Filter(_46);
this.alias=_47;
this.nodelist=_48;
},{render:function(_49,_4a){
var _4b=this.target.resolve(_49);
_49=_49.push();
_49[this.alias]=_4b;
_4a=this.nodelist.render(_49,_4a);
_49=_49.pop();
return _4a;
},unrender:function(_4c,_4d){
return _4d;
},clone:function(_4e){
return new this.constructor(this.target.getExpression(),this.alias,this.nodelist.clone(_4e));
}});
dojo.mixin(_2,{comment:function(_4f,_50){
_4f.skip_past("endcomment");
return dd._noOpNode;
},debug:function(_51,_52){
return new _2.DebugNode(_51.create_text_node());
},filter:function(_53,_54){
var _55=_54.contents.split(null,1)[1];
var _56=_53.create_variable_node("var|"+_55);
var _57=_53.parse(["endfilter"]);
_53.next_token();
return new _2.FilterNode(_56,_57);
},firstof:function(_58,_59){
var _5a=_59.split_contents().slice(1);
if(!_5a.length){
throw new Error("'firstof' statement requires at least one argument");
}
return new _2.FirstOfNode(_5a,_58.create_text_node());
},spaceless:function(_5b,_5c){
var _5d=_5b.parse(["endspaceless"]);
_5b.delete_first_token();
return new _2.SpacelessNode(_5d,_5b.create_text_node());
},templatetag:function(_5e,_5f){
var _60=_5f.contents.split();
if(_60.length!=2){
throw new Error("'templatetag' statement takes one argument");
}
var tag=_60[1];
var _62=_2.TemplateTagNode.prototype.mapping;
if(!_62[tag]){
var _63=[];
for(var key in _62){
_63.push(key);
}
throw new Error("Invalid templatetag argument: '"+tag+"'. Must be one of: "+_63.join(", "));
}
return new _2.TemplateTagNode(tag,_5e.create_text_node());
},widthratio:function(_65,_66){
var _67=_66.contents.split();
if(_67.length!=4){
throw new Error("widthratio takes three arguments");
}
var _68=+_67[3];
if(typeof _68!="number"){
throw new Error("widthratio final argument must be an integer");
}
return new _2.WidthRatioNode(_67[1],_67[2],_68,_65.create_text_node());
},with_:function(_69,_6a){
var _6b=_6a.split_contents();
if(_6b.length!=4||_6b[2]!="as"){
throw new Error("do_width expected format as 'with value as name'");
}
var _6c=_69.parse(["endwith"]);
_69.next_token();
return new _2.WithNode(_6b[1],_6b[3],_6c);
}});
})();
}
