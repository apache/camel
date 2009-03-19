/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.tag.loop"]){
dojo._hasResource["dojox.dtl.tag.loop"]=true;
dojo.provide("dojox.dtl.tag.loop");
dojo.require("dojox.dtl._base");
dojo.require("dojox.string.tokenize");
(function(){
var dd=dojox.dtl;
var _2=dd.tag.loop;
_2.CycleNode=dojo.extend(function(_3,_4,_5,_6){
this.cyclevars=_3;
this.name=_4;
this.contents=_5;
this.shared=_6||{counter:-1,map:{}};
},{render:function(_7,_8){
if(_7.forloop&&!_7.forloop.counter0){
this.shared.counter=-1;
}
++this.shared.counter;
var _9=this.cyclevars[this.shared.counter%this.cyclevars.length];
var _a=this.shared.map;
if(!_a[_9]){
_a[_9]=new dd._Filter(_9);
}
_9=_a[_9].resolve(_7,_8);
if(this.name){
_7[this.name]=_9;
}
this.contents.set(_9);
return this.contents.render(_7,_8);
},unrender:function(_b,_c){
return this.contents.unrender(_b,_c);
},clone:function(_d){
return new this.constructor(this.cyclevars,this.name,this.contents.clone(_d),this.shared);
}});
_2.IfChangedNode=dojo.extend(function(_e,_f,_10){
this.nodes=_e;
this._vars=_f;
this.shared=_10||{last:null,counter:0};
this.vars=dojo.map(_f,function(_11){
return new dojox.dtl._Filter(_11);
});
},{render:function(_12,_13){
if(_12.forloop){
if(_12.forloop.counter<=this.shared.counter){
this.shared.last=null;
}
this.shared.counter=_12.forloop.counter;
}
var _14;
if(this.vars.length){
_14=dojo.toJson(dojo.map(this.vars,function(_15){
return _15.resolve(_12);
}));
}else{
_14=this.nodes.dummyRender(_12,_13);
}
if(_14!=this.shared.last){
var _16=(this.shared.last===null);
this.shared.last=_14;
_12=_12.push();
_12.ifchanged={firstloop:_16};
_13=this.nodes.render(_12,_13);
_12=_12.pop();
}else{
_13=this.nodes.unrender(_12,_13);
}
return _13;
},unrender:function(_17,_18){
return this.nodes.unrender(_17,_18);
},clone:function(_19){
return new this.constructor(this.nodes.clone(_19),this._vars,this.shared);
}});
_2.RegroupNode=dojo.extend(function(_1a,key,_1c){
this._expression=_1a;
this.expression=new dd._Filter(_1a);
this.key=key;
this.alias=_1c;
},{_push:function(_1d,_1e,_1f){
if(_1f.length){
_1d.push({grouper:_1e,list:_1f});
}
},render:function(_20,_21){
_20[this.alias]=[];
var _22=this.expression.resolve(_20);
if(_22){
var _23=null;
var _24=[];
for(var i=0;i<_22.length;i++){
var id=_22[i][this.key];
if(_23!==id){
this._push(_20[this.alias],_23,_24);
_23=id;
_24=[_22[i]];
}else{
_24.push(_22[i]);
}
}
this._push(_20[this.alias],_23,_24);
}
return _21;
},unrender:function(_27,_28){
return _28;
},clone:function(_29,_2a){
return this;
}});
dojo.mixin(_2,{cycle:function(_2b,_2c){
var _2d=_2c.split_contents();
if(_2d.length<2){
throw new Error("'cycle' tag requires at least two arguments");
}
if(_2d[1].indexOf(",")!=-1){
var _2e=_2d[1].split(",");
_2d=[_2d[0]];
for(var i=0;i<_2e.length;i++){
_2d.push("\""+_2e[i]+"\"");
}
}
if(_2d.length==2){
var _30=_2d[_2d.length-1];
if(!_2b._namedCycleNodes){
throw new Error("No named cycles in template: '"+_30+"' is not defined");
}
if(!_2b._namedCycleNodes[_30]){
throw new Error("Named cycle '"+_30+"' does not exist");
}
return _2b._namedCycleNodes[_30];
}
if(_2d.length>4&&_2d[_2d.length-2]=="as"){
var _30=_2d[_2d.length-1];
var _31=new _2.CycleNode(_2d.slice(1,_2d.length-2),_30,_2b.create_text_node());
if(!_2b._namedCycleNodes){
_2b._namedCycleNodes={};
}
_2b._namedCycleNodes[_30]=_31;
}else{
_31=new _2.CycleNode(_2d.slice(1),null,_2b.create_text_node());
}
return _31;
},ifchanged:function(_32,_33){
var _34=_33.contents.split();
var _35=_32.parse(["endifchanged"]);
_32.delete_first_token();
return new _2.IfChangedNode(_35,_34.slice(1));
},regroup:function(_36,_37){
var _38=dojox.string.tokenize(_37.contents,/(\s+)/g,function(_39){
return _39;
});
if(_38.length<11||_38[_38.length-3]!="as"||_38[_38.length-7]!="by"){
throw new Error("Expected the format: regroup list by key as newList");
}
var _3a=_38.slice(2,-8).join("");
var key=_38[_38.length-5];
var _3c=_38[_38.length-1];
return new _2.RegroupNode(_3a,key,_3c);
}});
})();
}
