/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.json.ref"]){
dojo._hasResource["dojox.json.ref"]=true;
dojo.provide("dojox.json.ref");
dojo.require("dojo.date.stamp");
dojox.json.ref={resolveJson:function(_1,_2){
_2=_2||{};
var _3=_2.idAttribute||"id";
var _4=_2.idPrefix||"";
var _5=_2.assignAbsoluteIds;
var _6=_2.index||{};
var _7=_2.timeStamps;
var _8,_9=[];
var _a=/^(.*\/)?(\w+:\/\/)|[^\/\.]+\/\.\.\/|^.*\/(\/)/;
var _b=this._addProp;
var F=function(){
};
function _d(it,_f,_10,_11,_12){
var _13,val,id=_3 in it?it[_3]:_10;
if(id!==undefined){
id=(_4+id).replace(_a,"$2$3");
}
var _16=_12||it;
if(id!==undefined){
if(_5){
it.__id=id;
}
if(_2.schemas&&(!(it instanceof Array))&&(val=id.match(/^(.+\/)[^\.\[]*$/))){
_11=_2.schemas[val[1]];
}
if(_6[id]&&((it instanceof Array)==(_6[id] instanceof Array))){
_16=_6[id];
delete _16.$ref;
_13=true;
}else{
var _17=_11&&_11.prototype;
if(_17){
F.prototype=_17;
_16=new F();
}
}
_6[id]=_16;
if(_7){
_7[id]=_2.time;
}
}
var _18=_11&&_11.properties;
var _19=it.length;
for(var i in it){
if(i==_19){
break;
}
if(it.hasOwnProperty(i)){
val=it[i];
var _1b=_18&&_18[i];
if(_1b&&_1b.format=="date-time"&&typeof val=="string"){
val=dojo.date.stamp.fromISOString(val);
}else{
if((typeof val=="object")&&val&&!(val instanceof Date)){
_8=val.$ref;
if(_8){
delete it[i];
var _1c=_8.replace(/(#)([^\.\[])/,"$1.$2").match(/(^([^\[]*\/)?[^#\.\[]*)#?([\.\[].*)?/);
if((_8=(_1c[1]=="$"||_1c[1]=="this"||_1c[1]=="")?_1:_6[(_4+_1c[1]).replace(_a,"$2$3")])){
if(_1c[3]){
_1c[3].replace(/(\[([^\]]+)\])|(\.?([^\.\[]+))/g,function(t,a,b,c,d){
_8=_8&&_8[b?b.replace(/[\"\'\\]/,""):d];
});
}
}
if(_8){
val=_8;
}else{
if(!_f){
var _22;
if(!_22){
_9.push(_16);
}
_22=true;
}else{
val=_d(val,false,val.$ref,_1b);
val._loadObject=_2.loader;
}
}
}else{
if(!_f){
val=_d(val,_9==it,id&&_b(id,i),_1b,_16!=it&&typeof _16[i]=="object"&&_16[i]);
}
}
}
}
it[i]=val;
if(_16!=it&&!_16.__isDirty){
var old=_16[i];
_16[i]=val;
if(_13&&val!==old&&!_16._loadObject&&!(val instanceof Date&&old instanceof Date&&val.getTime()==old.getTime())&&!(typeof val=="function"&&typeof old=="function"&&val.toString()==old.toString())&&_6.onUpdate){
_6.onUpdate(_16,i,old,val);
}
}
}
}
if(_13){
for(i in _16){
if(!_16.__isDirty&&_16.hasOwnProperty(i)&&!it.hasOwnProperty(i)&&i!="__id"&&i!="__clientId"&&!(_16 instanceof Array&&isNaN(i))){
if(_6.onUpdate&&i!="_loadObject"&&i!="_idAttr"){
_6.onUpdate(_16,i,_16[i],undefined);
}
delete _16[i];
while(_16 instanceof Array&&_16.length&&_16[_16.length-1]===undefined){
_16.length--;
}
}
}
}else{
if(_6.onLoad){
_6.onLoad(_16);
}
}
return _16;
};
if(_1&&typeof _1=="object"){
_1=_d(_1,false,_2.defaultId);
_d(_9,false);
}
return _1;
},fromJson:function(str,_25){
function ref(_27){
return {$ref:_27};
};
try{
var _28=eval("("+str+")");
}
catch(e){
throw new SyntaxError("Invalid JSON string: "+e.message+" parsing: "+str);
}
if(_28){
return this.resolveJson(_28,_25);
}
return _28;
},toJson:function(it,_2a,_2b,_2c){
var _2d=this._useRefs;
var _2e=this._addProp;
_2b=_2b||"";
var _2f={};
var _30={};
function _31(it,_33,_34){
if(typeof it=="object"&&it){
var _35;
if(it instanceof Date){
return "\""+dojo.date.stamp.toISOString(it,{zulu:true})+"\"";
}
var id=it.__id;
if(id){
if(_33!="#"&&((_2d&&!id.match(/#/))||_2f[id])){
var ref=id;
if(id.charAt(0)!="#"){
if(it.__clientId==id){
ref="cid:"+id;
}else{
if(id.substring(0,_2b.length)==_2b){
ref=id.substring(_2b.length);
}else{
ref=id;
}
}
}
return _31({$ref:ref},"#");
}
_33=id;
}else{
it.__id=_33;
_30[_33]=it;
}
_2f[_33]=it;
_34=_34||"";
var _38=_2a?_34+dojo.toJsonIndentStr:"";
var _39=_2a?"\n":"";
var sep=_2a?" ":"";
if(it instanceof Array){
var res=dojo.map(it,function(obj,i){
var val=_31(obj,_2e(_33,i),_38);
if(typeof val!="string"){
val="undefined";
}
return _39+_38+val;
});
return "["+res.join(","+sep)+_39+_34+"]";
}
var _3f=[];
for(var i in it){
if(it.hasOwnProperty(i)){
var _41;
if(typeof i=="number"){
_41="\""+i+"\"";
}else{
if(typeof i=="string"&&(i.charAt(0)!="_"||i.charAt(1)!="_")){
_41=dojo._escapeString(i);
}else{
continue;
}
}
var val=_31(it[i],_2e(_33,i),_38);
if(typeof val!="string"){
continue;
}
_3f.push(_39+_38+_41+":"+sep+val);
}
}
return "{"+_3f.join(","+sep)+_39+_34+"}";
}else{
if(typeof it=="function"&&dojox.json.ref.serializeFunctions){
return it.toString();
}
}
return dojo.toJson(it);
};
var _43=_31(it,"#","");
if(!_2c){
for(var i in _30){
delete _30[i].__id;
}
}
return _43;
},_addProp:function(id,_46){
return id+(id.match(/#/)?id.length==1?"":".":"#")+_46;
},_useRefs:false,serializeFunctions:false};
}
