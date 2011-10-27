/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.validate.check"]){
dojo._hasResource["dojox.validate.check"]=true;
dojo.provide("dojox.validate.check");
dojo.experimental;
dojo.require("dojox.validate._base");
dojox.validate.check=function(_1,_2){
var _3=[];
var _4=[];
var _5={isSuccessful:function(){
return (!this.hasInvalid()&&!this.hasMissing());
},hasMissing:function(){
return (_3.length>0);
},getMissing:function(){
return _3;
},isMissing:function(_6){
for(var i=0;i<_3.length;i++){
if(_6==_3[i]){
return true;
}
}
return false;
},hasInvalid:function(){
return (_4.length>0);
},getInvalid:function(){
return _4;
},isInvalid:function(_8){
for(var i=0;i<_4.length;i++){
if(_8==_4[i]){
return true;
}
}
return false;
}};
var _a=function(_b,_c){
return (typeof _c[_b]=="undefined");
};
if(_2.trim instanceof Array){
for(var i=0;i<_2.trim.length;i++){
var _e=_1[_2.trim[i]];
if(_a("type",_e)||_e.type!="text"&&_e.type!="textarea"&&_e.type!="password"){
continue;
}
_e.value=_e.value.replace(/(^\s*|\s*$)/g,"");
}
}
if(_2.uppercase instanceof Array){
for(var i=0;i<_2.uppercase.length;i++){
var _e=_1[_2.uppercase[i]];
if(_a("type",_e)||_e.type!="text"&&_e.type!="textarea"&&_e.type!="password"){
continue;
}
_e.value=_e.value.toUpperCase();
}
}
if(_2.lowercase instanceof Array){
for(var i=0;i<_2.lowercase.length;i++){
var _e=_1[_2.lowercase[i]];
if(_a("type",_e)||_e.type!="text"&&_e.type!="textarea"&&_e.type!="password"){
continue;
}
_e.value=_e.value.toLowerCase();
}
}
if(_2.ucfirst instanceof Array){
for(var i=0;i<_2.ucfirst.length;i++){
var _e=_1[_2.ucfirst[i]];
if(_a("type",_e)||_e.type!="text"&&_e.type!="textarea"&&_e.type!="password"){
continue;
}
_e.value=_e.value.replace(/\b\w+\b/g,function(_f){
return _f.substring(0,1).toUpperCase()+_f.substring(1).toLowerCase();
});
}
}
if(_2.digit instanceof Array){
for(var i=0;i<_2.digit.length;i++){
var _e=_1[_2.digit[i]];
if(_a("type",_e)||_e.type!="text"&&_e.type!="textarea"&&_e.type!="password"){
continue;
}
_e.value=_e.value.replace(/\D/g,"");
}
}
if(_2.required instanceof Array){
for(var i=0;i<_2.required.length;i++){
if(!dojo.isString(_2.required[i])){
continue;
}
var _e=_1[_2.required[i]];
if(!_a("type",_e)&&(_e.type=="text"||_e.type=="textarea"||_e.type=="password"||_e.type=="file")&&/^\s*$/.test(_e.value)){
_3[_3.length]=_e.name;
}else{
if(!_a("type",_e)&&(_e.type=="select-one"||_e.type=="select-multiple")&&(_e.selectedIndex==-1||/^\s*$/.test(_e.options[_e.selectedIndex].value))){
_3[_3.length]=_e.name;
}else{
if(_e instanceof Array){
var _10=false;
for(var j=0;j<_e.length;j++){
if(_e[j].checked){
_10=true;
}
}
if(!_10){
_3[_3.length]=_e[0].name;
}
}
}
}
}
}
if(_2.required instanceof Array){
for(var i=0;i<_2.required.length;i++){
if(!dojo.isObject(_2.required[i])){
continue;
}
var _e,_12;
for(var _13 in _2.required[i]){
_e=_1[_13];
_12=_2.required[i][_13];
}
if(_e instanceof Array){
var _10=0;
for(var j=0;j<_e.length;j++){
if(_e[j].checked){
_10++;
}
}
if(_10<_12){
_3[_3.length]=_e[0].name;
}
}else{
if(!_a("type",_e)&&_e.type=="select-multiple"){
var _14=0;
for(var j=0;j<_e.options.length;j++){
if(_e.options[j].selected&&!/^\s*$/.test(_e.options[j].value)){
_14++;
}
}
if(_14<_12){
_3[_3.length]=_e.name;
}
}
}
}
}
if(dojo.isObject(_2.dependencies)){
for(_13 in _2.dependencies){
var _e=_1[_13];
if(_a("type",_e)){
continue;
}
if(_e.type!="text"&&_e.type!="textarea"&&_e.type!="password"){
continue;
}
if(/\S+/.test(_e.value)){
continue;
}
if(_5.isMissing(_e.name)){
continue;
}
var _15=_1[_2.dependencies[_13]];
if(_15.type!="text"&&_15.type!="textarea"&&_15.type!="password"){
continue;
}
if(/^\s*$/.test(_15.value)){
continue;
}
_3[_3.length]=_e.name;
}
}
if(dojo.isObject(_2.constraints)){
for(_13 in _2.constraints){
var _e=_1[_13];
if(!_e){
continue;
}
if(!_a("tagName",_e)&&(_e.tagName.toLowerCase().indexOf("input")>=0||_e.tagName.toLowerCase().indexOf("textarea")>=0)&&/^\s*$/.test(_e.value)){
continue;
}
var _16=true;
if(dojo.isFunction(_2.constraints[_13])){
_16=_2.constraints[_13](_e.value);
}else{
if(dojo.isArray(_2.constraints[_13])){
if(dojo.isArray(_2.constraints[_13][0])){
for(var i=0;i<_2.constraints[_13].length;i++){
_16=dojox.validate.evaluateConstraint(_2,_2.constraints[_13][i],_13,_e);
if(!_16){
break;
}
}
}else{
_16=dojox.validate.evaluateConstraint(_2,_2.constraints[_13],_13,_e);
}
}
}
if(!_16){
_4[_4.length]=_e.name;
}
}
}
if(dojo.isObject(_2.confirm)){
for(_13 in _2.confirm){
var _e=_1[_13];
var _15=_1[_2.confirm[_13]];
if(_a("type",_e)||_a("type",_15)||(_e.type!="text"&&_e.type!="textarea"&&_e.type!="password")||(_15.type!=_e.type)||(_15.value==_e.value)||(_5.isInvalid(_e.name))||(/^\s*$/.test(_15.value))){
continue;
}
_4[_4.length]=_e.name;
}
}
return _5;
};
dojox.validate.evaluateConstraint=function(_17,_18,_19,_1a){
var _1b=_18[0];
var _1c=_18.slice(1);
_1c.unshift(_1a.value);
if(typeof _1b!="undefined"){
return _1b.apply(null,_1c);
}
return false;
};
}
