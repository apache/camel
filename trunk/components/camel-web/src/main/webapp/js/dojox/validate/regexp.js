/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.validate.regexp"]){
dojo._hasResource["dojox.validate.regexp"]=true;
dojo.provide("dojox.validate.regexp");
dojo.require("dojo.regexp");
dojo.mixin(dojox.validate.regexp,{ipAddress:function(_1){
_1=(typeof _1=="object")?_1:{};
if(typeof _1.allowDottedDecimal!="boolean"){
_1.allowDottedDecimal=true;
}
if(typeof _1.allowDottedHex!="boolean"){
_1.allowDottedHex=true;
}
if(typeof _1.allowDottedOctal!="boolean"){
_1.allowDottedOctal=true;
}
if(typeof _1.allowDecimal!="boolean"){
_1.allowDecimal=true;
}
if(typeof _1.allowHex!="boolean"){
_1.allowHex=true;
}
if(typeof _1.allowIPv6!="boolean"){
_1.allowIPv6=true;
}
if(typeof _1.allowHybrid!="boolean"){
_1.allowHybrid=true;
}
var _2="((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])";
var _3="(0[xX]0*[\\da-fA-F]?[\\da-fA-F]\\.){3}0[xX]0*[\\da-fA-F]?[\\da-fA-F]";
var _4="(0+[0-3][0-7][0-7]\\.){3}0+[0-3][0-7][0-7]";
var _5="(0|[1-9]\\d{0,8}|[1-3]\\d{9}|4[01]\\d{8}|42[0-8]\\d{7}|429[0-3]\\d{6}|"+"4294[0-8]\\d{5}|42949[0-5]\\d{4}|429496[0-6]\\d{3}|4294967[01]\\d{2}|42949672[0-8]\\d|429496729[0-5])";
var _6="0[xX]0*[\\da-fA-F]{1,8}";
var _7="([\\da-fA-F]{1,4}\\:){7}[\\da-fA-F]{1,4}";
var _8="([\\da-fA-F]{1,4}\\:){6}"+"((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])";
var a=[];
if(_1.allowDottedDecimal){
a.push(_2);
}
if(_1.allowDottedHex){
a.push(_3);
}
if(_1.allowDottedOctal){
a.push(_4);
}
if(_1.allowDecimal){
a.push(_5);
}
if(_1.allowHex){
a.push(_6);
}
if(_1.allowIPv6){
a.push(_7);
}
if(_1.allowHybrid){
a.push(_8);
}
var _a="";
if(a.length>0){
_a="("+a.join("|")+")";
}
return _a;
},host:function(_b){
_b=(typeof _b=="object")?_b:{};
if(typeof _b.allowIP!="boolean"){
_b.allowIP=true;
}
if(typeof _b.allowLocal!="boolean"){
_b.allowLocal=false;
}
if(typeof _b.allowPort!="boolean"){
_b.allowPort=true;
}
if(typeof _b.allowNamed!="boolean"){
_b.allowNamed=false;
}
var _c="(?:[\\da-zA-Z](?:[-\\da-zA-Z]{0,61}[\\da-zA-Z])?)";
var _d="(?:[a-zA-Z](?:[-\\da-zA-Z]{0,6}[\\da-zA-Z])?)";
var _e=_b.allowPort?"(\\:\\d+)?":"";
var _f="((?:"+_c+"\\.)*"+_d+"\\.?)";
if(_b.allowIP){
_f+="|"+dojox.validate.regexp.ipAddress(_b);
}
if(_b.allowLocal){
_f+="|localhost";
}
if(_b.allowNamed){
_f+="|^[^-][a-zA-Z0-9_-]*";
}
return "("+_f+")"+_e;
},url:function(_10){
_10=(typeof _10=="object")?_10:{};
if(!("scheme" in _10)){
_10.scheme=[true,false];
}
var _11=dojo.regexp.buildGroupRE(_10.scheme,function(q){
if(q){
return "(https?|ftps?)\\://";
}
return "";
});
var _13="(/(?:[^?#\\s/]+/)*(?:[^?#\\s/]+(?:\\?[^?#\\s/]*)?(?:#[A-Za-z][\\w.:-]*)?)?)?";
return _11+dojox.validate.regexp.host(_10)+_13;
},emailAddress:function(_14){
_14=(typeof _14=="object")?_14:{};
if(typeof _14.allowCruft!="boolean"){
_14.allowCruft=false;
}
_14.allowPort=false;
var _15="([\\da-zA-Z]+[-._+&'])*[\\da-zA-Z]+";
var _16=_15+"@"+dojox.validate.regexp.host(_14);
if(_14.allowCruft){
_16="<?(mailto\\:)?"+_16+">?";
}
return _16;
},emailAddressList:function(_17){
_17=(typeof _17=="object")?_17:{};
if(typeof _17.listSeparator!="string"){
_17.listSeparator="\\s;,";
}
var _18=dojox.validate.regexp.emailAddress(_17);
var _19="("+_18+"\\s*["+_17.listSeparator+"]\\s*)*"+_18+"\\s*["+_17.listSeparator+"]?\\s*";
return _19;
},numberFormat:function(_1a){
_1a=(typeof _1a=="object")?_1a:{};
if(typeof _1a.format=="undefined"){
_1a.format="###-###-####";
}
var _1b=function(_1c){
return dojo.regexp.escapeString(_1c,"?").replace(/\?/g,"\\d?").replace(/#/g,"\\d");
};
return dojo.regexp.buildGroupRE(_1a.format,_1b);
}});
dojox.validate.regexp.ca={postalCode:function(){
return "([A-Z][0-9][A-Z] [0-9][A-Z][0-9])";
},province:function(){
return "(AB|BC|MB|NB|NL|NS|NT|NU|ON|PE|QC|SK|YT)";
}};
dojox.validate.regexp.us={state:function(_1d){
_1d=(typeof _1d=="object")?_1d:{};
if(typeof _1d.allowTerritories!="boolean"){
_1d.allowTerritories=true;
}
if(typeof _1d.allowMilitary!="boolean"){
_1d.allowMilitary=true;
}
var _1e="AL|AK|AZ|AR|CA|CO|CT|DE|DC|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|"+"NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY";
var _1f="AS|FM|GU|MH|MP|PW|PR|VI";
var _20="AA|AE|AP";
if(_1d.allowTerritories){
_1e+="|"+_1f;
}
if(_1d.allowMilitary){
_1e+="|"+_20;
}
return "("+_1e+")";
}};
}
