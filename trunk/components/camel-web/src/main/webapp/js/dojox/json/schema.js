/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.json.schema"]){
dojo._hasResource["dojox.json.schema"]=true;
dojo.provide("dojox.json.schema");
dojox.json.schema.validate=function(_1,_2){
return this._validate(_1,_2,false);
};
dojox.json.schema.checkPropertyChange=function(_3,_4){
return this._validate(_3,_4,true);
};
dojox.json.schema.mustBeValid=function(_5){
if(!_5.valid){
throw new Error(dojo.map(_5.errors,function(_6){
return _6.property+" "+_6.message;
}).join(","));
}
};
dojox.json.schema._validate=function(_7,_8,_9){
var _a=[];
function _b(_c,_d,_e,i){
if(typeof _d!="object"){
return null;
}
_e+=_e?typeof i=="number"?"["+i+"]":typeof i=="undefined"?"":"."+i:i;
function _10(_11){
_a.push({property:_e,message:_11});
};
if(_9&&_d.readonly){
_10("is a readonly field, it can not be changed");
}
if(_d instanceof Array){
if(!(_c instanceof Array)){
return [{property:_e,message:"An array tuple is required"}];
}
for(i=0;i<_d.length;i++){
_a.concat(_b(_c[i],_d[i],_e,i));
}
return _a;
}
if(_d["extends"]){
_b(_c,_d["extends"],_e,i);
}
function _12(_13,_14){
if(_13){
if(typeof _13=="string"&&_13!="any"&&(_13=="null"?_14!==null:typeof _14!=_13)&&!(_14 instanceof Array&&_13=="array")&&!(_13=="integer"&&!(_14%1))){
return [{property:_e,message:(typeof _14)+" value found, but a "+_13+" is required"}];
}
if(_13 instanceof Array){
var _15=[];
for(var j=0;j<_13.length;j++){
if(!(_15=_12(_13[j],_14)).length){
break;
}
}
if(_15.length){
return _15;
}
}else{
if(typeof _13=="object"){
_b(_14,_13,_e);
}
}
}
return [];
};
if(_c!==null){
if(_c===undefined){
if(!_d.optional){
_10("is missing and it is not optional");
}
}else{
_a=_a.concat(_12(_d.type,_c));
if(_d.disallow&&!_12(_d.disallow,_c).length){
_10(" disallowed value was matched");
}
if(_c instanceof Array){
if(_d.items){
for(i=0,l=_c.length;i<l;i++){
_a.concat(_b(_c[i],_d.items,_e,i));
}
}
if(_d.minItems&&_c.length<_d.minItems){
_10("There must be a minimum of "+_d.minItems+" in the array");
}
if(_d.maxItems&&_c.length>_d.maxItems){
_10("There must be a maximum of "+_d.maxItems+" in the array");
}
}else{
if(_d.properties&&typeof _c=="object"){
_a.concat(_17(_c,_d.properties,_e,_d.additionalProperties));
}
}
if(_d.pattern&&typeof _c=="string"&&!_c.match(_d.pattern)){
_10("does not match the regex pattern "+_d.pattern);
}
if(_d.maxLength&&typeof _c=="string"&&_c.length>_d.maxLength){
_10("may only be "+_d.maxLength+" characters long");
}
if(_d.minLength&&typeof _c=="string"&&_c.length<_d.minLength){
_10("must be at least "+_d.minLength+" characters long");
}
if(typeof _d.minimum!==undefined&&typeof _c==typeof _d.minimum&&_d.minimum>_c){
_10("must have a minimum value of "+_d.minimum);
}
if(typeof _d.maximum!==undefined&&typeof _c==typeof _d.maximum&&_d.maximum<_c){
_10("must have a maximum value of "+_d.maximum);
}
if(_d["enum"]){
var _18=_d["enum"];
l=_18.length;
var _19;
for(var j=0;j<l;j++){
if(_18[j]===_c){
_19=1;
break;
}
}
if(!_19){
_10("does not have a value in the enumeration "+_18.join(", "));
}
}
if(typeof _d.maxDecimal=="number"&&(_c*10^_d.maxDecimal)%1){
_10("may only have "+_d.maxDecimal+" digits of decimal places");
}
}
}
return null;
};
function _17(_1b,_1c,_1d,_1e){
if(typeof _1c=="object"){
if(typeof _1b!="object"||_1b instanceof Array){
_a.push({property:_1d,message:"an object is required"});
}
for(var i in _1c){
if(_1c.hasOwnProperty(i)){
var _20=_1b[i];
var _21=_1c[i];
_b(_20,_21,_1d,i);
}
}
}
for(i in _1b){
if(_1b.hasOwnProperty(i)&&(i.charAt(0)!="_"||i.charAt(0)!="_")&&_1c&&!_1c[i]&&_1e===false){
_a.push({property:_1d,message:(typeof _20)+"The property "+i+" is not defined in the schema and the schema does not allow additional properties"});
}
var _22=_1c&&_1c[i]&&_1c[i].requires;
if(_22&&!(_22 in _1b)){
_a.push({property:_1d,message:"the presence of the property "+i+" requires that "+_22+" also be present"});
}
_20=_1b[i];
if(_1c&&typeof _1c=="object"&&!(i in _1c)){
_b(_20,_1e,_1d,i);
}
if(!_9&&_20&&_20.$schema){
_a=_a.concat(_b(_20,_20.$schema,_1d,i));
}
}
return _a;
};
if(_8){
_b(_7,_8,"","");
}
if(!_9&&_7.$schema){
_b(_7,_7.$schema,"","");
}
return {valid:!_a.length,errors:_a};
};
}
