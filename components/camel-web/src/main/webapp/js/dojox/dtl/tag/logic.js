/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.tag.logic"]){
dojo._hasResource["dojox.dtl.tag.logic"]=true;
dojo.provide("dojox.dtl.tag.logic");
dojo.require("dojox.dtl._base");
(function(){
var dd=dojox.dtl;
var _2=dd.text;
var _3=dd.tag.logic;
_3.IfNode=dojo.extend(function(_4,_5,_6,_7){
this.bools=_4;
this.trues=_5;
this.falses=_6;
this.type=_7;
},{render:function(_8,_9){
var i,_b,_c,_d,_e;
if(this.type=="or"){
for(i=0;_b=this.bools[i];i++){
_c=_b[0];
_d=_b[1];
_e=_d.resolve(_8);
if((_e&&!_c)||(_c&&!_e)){
if(this.falses){
_9=this.falses.unrender(_8,_9);
}
return (this.trues)?this.trues.render(_8,_9,this):_9;
}
}
if(this.trues){
_9=this.trues.unrender(_8,_9);
}
return (this.falses)?this.falses.render(_8,_9,this):_9;
}else{
for(i=0;_b=this.bools[i];i++){
_c=_b[0];
_d=_b[1];
_e=_d.resolve(_8);
if(_e==_c){
if(this.trues){
_9=this.trues.unrender(_8,_9);
}
return (this.falses)?this.falses.render(_8,_9,this):_9;
}
}
if(this.falses){
_9=this.falses.unrender(_8,_9);
}
return (this.trues)?this.trues.render(_8,_9,this):_9;
}
return _9;
},unrender:function(_f,_10){
_10=(this.trues)?this.trues.unrender(_f,_10):_10;
_10=(this.falses)?this.falses.unrender(_f,_10):_10;
return _10;
},clone:function(_11){
var _12=(this.trues)?this.trues.clone(_11):null;
var _13=(this.falses)?this.falses.clone(_11):null;
return new this.constructor(this.bools,_12,_13,this.type);
}});
_3.IfEqualNode=dojo.extend(function(_14,_15,_16,_17,_18){
this.var1=new dd._Filter(_14);
this.var2=new dd._Filter(_15);
this.trues=_16;
this.falses=_17;
this.negate=_18;
},{render:function(_19,_1a){
var _1b=this.var1.resolve(_19);
var _1c=this.var2.resolve(_19);
_1b=(typeof _1b!="undefined")?_1b:"";
_1c=(typeof _1b!="undefined")?_1c:"";
if((this.negate&&_1b!=_1c)||(!this.negate&&_1b==_1c)){
if(this.falses){
_1a=this.falses.unrender(_19,_1a,this);
}
return (this.trues)?this.trues.render(_19,_1a,this):_1a;
}
if(this.trues){
_1a=this.trues.unrender(_19,_1a,this);
}
return (this.falses)?this.falses.render(_19,_1a,this):_1a;
},unrender:function(_1d,_1e){
return _3.IfNode.prototype.unrender.call(this,_1d,_1e);
},clone:function(_1f){
var _20=this.trues?this.trues.clone(_1f):null;
var _21=this.falses?this.falses.clone(_1f):null;
return new this.constructor(this.var1.getExpression(),this.var2.getExpression(),_20,_21,this.negate);
}});
_3.ForNode=dojo.extend(function(_22,_23,_24,_25){
this.assign=_22;
this.loop=new dd._Filter(_23);
this.reversed=_24;
this.nodelist=_25;
this.pool=[];
},{render:function(_26,_27){
var i,j,k;
var _2b=false;
var _2c=this.assign;
for(k=0;k<_2c.length;k++){
if(typeof _26[_2c[k]]!="undefined"){
_2b=true;
_26=_26.push();
break;
}
}
if(!_2b&&_26.forloop){
_2b=true;
_26=_26.push();
}
var _2d=this.loop.resolve(_26)||[];
for(i=_2d.length;i<this.pool.length;i++){
this.pool[i].unrender(_26,_27,this);
}
if(this.reversed){
_2d=_2d.slice(0).reverse();
}
var _2e=dojo.isObject(_2d)&&!dojo.isArrayLike(_2d);
var _2f=[];
if(_2e){
for(var key in _2d){
_2f.push(_2d[key]);
}
}else{
_2f=_2d;
}
var _31=_26.forloop={parentloop:_26.get("forloop",{})};
var j=0;
for(i=0;i<_2f.length;i++){
var _32=_2f[i];
_31.counter0=j;
_31.counter=j+1;
_31.revcounter0=_2f.length-j-1;
_31.revcounter=_2f.length-j;
_31.first=!j;
_31.last=(j==_2f.length-1);
if(_2c.length>1&&dojo.isArrayLike(_32)){
if(!_2b){
_2b=true;
_26=_26.push();
}
var _33={};
for(k=0;k<_32.length&&k<_2c.length;k++){
_33[_2c[k]]=_32[k];
}
dojo.mixin(_26,_33);
}else{
_26[_2c[0]]=_32;
}
if(j+1>this.pool.length){
this.pool.push(this.nodelist.clone(_27));
}
_27=this.pool[j++].render(_26,_27,this);
}
delete _26.forloop;
if(_2b){
_26=_26.pop();
}else{
for(k=0;k<_2c.length;k++){
delete _26[_2c[k]];
}
}
return _27;
},unrender:function(_34,_35){
for(var i=0,_37;_37=this.pool[i];i++){
_35=_37.unrender(_34,_35);
}
return _35;
},clone:function(_38){
return new this.constructor(this.assign,this.loop.getExpression(),this.reversed,this.nodelist.clone(_38));
}});
dojo.mixin(_3,{if_:function(_39,_3a){
var i,_3c,_3d,_3e=[],_3f=_3a.contents.split();
_3f.shift();
_3a=_3f.join(" ");
_3f=_3a.split(" and ");
if(_3f.length==1){
_3d="or";
_3f=_3a.split(" or ");
}else{
_3d="and";
for(i=0;i<_3f.length;i++){
if(_3f[i].indexOf(" or ")!=-1){
throw new Error("'if' tags can't mix 'and' and 'or'");
}
}
}
for(i=0;_3c=_3f[i];i++){
var not=false;
if(_3c.indexOf("not ")==0){
_3c=_3c.slice(4);
not=true;
}
_3e.push([not,new dd._Filter(_3c)]);
}
var _41=_39.parse(["else","endif"]);
var _42=false;
var _3a=_39.next_token();
if(_3a.contents=="else"){
_42=_39.parse(["endif"]);
_39.next_token();
}
return new _3.IfNode(_3e,_41,_42,_3d);
},_ifequal:function(_43,_44,_45){
var _46=_44.split_contents();
if(_46.length!=3){
throw new Error(_46[0]+" takes two arguments");
}
var end="end"+_46[0];
var _48=_43.parse(["else",end]);
var _49=false;
var _44=_43.next_token();
if(_44.contents=="else"){
_49=_43.parse([end]);
_43.next_token();
}
return new _3.IfEqualNode(_46[1],_46[2],_48,_49,_45);
},ifequal:function(_4a,_4b){
return _3._ifequal(_4a,_4b);
},ifnotequal:function(_4c,_4d){
return _3._ifequal(_4c,_4d,true);
},for_:function(_4e,_4f){
var _50=_4f.contents.split();
if(_50.length<4){
throw new Error("'for' statements should have at least four words: "+_4f.contents);
}
var _51=_50[_50.length-1]=="reversed";
var _52=(_51)?-3:-2;
if(_50[_50.length+_52]!="in"){
throw new Error("'for' tag received an invalid argument: "+_4f.contents);
}
var _53=_50.slice(1,_52).join(" ").split(/ *, */);
for(var i=0;i<_53.length;i++){
if(!_53[i]||_53[i].indexOf(" ")!=-1){
throw new Error("'for' tag received an invalid argument: "+_4f.contents);
}
}
var _55=_4e.parse(["endfor"]);
_4e.next_token();
return new _3.ForNode(_53,_50[_50.length+_52+1],_51,_55);
}});
})();
}
