/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.json.query"]){
dojo._hasResource["dojox.json.query"]=true;
dojo.provide("dojox.json.query");
(function(){
function _1(_2,_3,_4,_5){
var _6=_2.length,_7=[];
_4=_4||_6;
_3=(_3<0)?Math.max(0,_3+_6):Math.min(_6,_3);
_4=(_4<0)?Math.max(0,_4+_6):Math.min(_6,_4);
for(var i=_3;i<_4;i+=_5){
_7.push(_2[i]);
}
return _7;
};
function _9(_a,_b){
var _c=[];
function _d(_e){
if(_b){
if(_b===true&&!(_e instanceof Array)){
_c.push(_e);
}else{
if(_e[_b]){
_c.push(_e[_b]);
}
}
}
for(var i in _e){
var val=_e[i];
if(!_b){
_c.push(val);
}else{
if(val&&typeof val=="object"){
_d(val);
}
}
}
};
if(_b instanceof Array){
if(_b.length==1){
return _a[_b[0]];
}
for(var i=0;i<_b.length;i++){
_c.push(_a[_b[i]]);
}
}else{
_d(_a);
}
return _c;
};
function _12(_13,_14){
var _15=[];
var _16={};
for(var i=0,l=_13.length;i<l;++i){
var _19=_13[i];
if(_14(_19,i,_13)){
if((typeof _19=="object")&&_19){
if(!_19.__included){
_19.__included=true;
_15.push(_19);
}
}else{
if(!_16[_19+typeof _19]){
_16[_19+typeof _19]=true;
_15.push(_19);
}
}
}
}
for(i=0,l=_15.length;i<l;++i){
if(_15[i]){
delete _15[i].__included;
}
}
return _15;
};
dojox.json.query=function(_1a,obj){
var _1c=0;
var str=[];
_1a=_1a.replace(/"(\\.|[^"\\])*"|'(\\.|[^'\\])*'|[\[\]]/g,function(t){
_1c+=t=="["?1:t=="]"?-1:0;
return (t=="]"&&_1c>0)?"`]":(t.charAt(0)=="\""||t.charAt(0)=="'")?"`"+(str.push(t)-1):t;
});
var _1f="";
function _20(_21){
_1f=_21+"("+_1f;
};
function _22(t,a,b,c,d,e,f,g){
return str[g].match(/[\*\?]/)||f=="~"?"/^"+str[g].substring(1,str[g].length-1).replace(/\\([btnfr\\"'])|([^\w\*\?])/g,"\\$1$2").replace(/([\*\?])/g,".$1")+(f=="~"?"$/i":"$/")+".test("+a+")":t;
};
_1a.replace(/(\]|\)|push|pop|shift|splice|sort|reverse)\s*\(/,function(){
throw new Error("Unsafe function call");
});
_1a=_1a.replace(/([^=]=)([^=])/g,"$1=$2").replace(/@|(\.\s*)?[a-zA-Z\$_]+(\s*:)?/g,function(t){
return t.charAt(0)=="."?t:t=="@"?"$obj":(t.match(/:|^(\$|Math|true|false|null)$/)?"":"$obj.")+t;
}).replace(/\.?\.?\[(`\]|[^\]])*\]|\?.*|\.\.([\w\$_]+)|\.\*/g,function(t,a,b){
var _2f=t.match(/^\.?\.?(\[\s*\^?\?|\^?\?|\[\s*==)(.*?)\]?$/);
if(_2f){
var _30="";
if(t.match(/^\./)){
_20("expand");
_30=",true)";
}
_20(_2f[1].match(/\=/)?"dojo.map":_2f[1].match(/\^/)?"distinctFilter":"dojo.filter");
return _30+",function($obj){return "+_2f[2]+"})";
}
_2f=t.match(/^\[\s*([\/\\].*)\]/);
if(_2f){
return ".concat().sort(function(a,b){"+_2f[1].replace(/\s*,?\s*([\/\\])\s*([^,\\\/]+)/g,function(t,a,b){
return "var av= "+b.replace(/\$obj/,"a")+",bv= "+b.replace(/\$obj/,"b")+";if(av>bv||bv==null){return "+(a=="/"?1:-1)+";}\n"+"if(bv>av||av==null){return "+(a=="/"?-1:1)+";}\n";
})+"return 0;})";
}
_2f=t.match(/^\[(-?[0-9]*):(-?[0-9]*):?(-?[0-9]*)\]/);
if(_2f){
_20("slice");
return ","+(_2f[1]||0)+","+(_2f[2]||0)+","+(_2f[3]||1)+")";
}
if(t.match(/^\.\.|\.\*|\[\s*\*\s*\]|,/)){
_20("expand");
return (t.charAt(1)=="."?",'"+b+"'":t.match(/,/)?","+t:"")+")";
}
return t;
}).replace(/(\$obj\s*((\.\s*[\w_$]+\s*)|(\[\s*`([0-9]+)\s*`\]))*)(==|~)\s*`([0-9]+)/g,_22).replace(/`([0-9]+)\s*(==|~)\s*(\$obj\s*((\.\s*[\w_$]+)|(\[\s*`([0-9]+)\s*`\]))*)/g,function(t,a,b,c,d,e,f,g){
return _22(t,c,d,e,f,g,b,a);
});
_1a=_1f+(_1a.charAt(0)=="$"?"":"$")+_1a.replace(/`([0-9]+|\])/g,function(t,a){
return a=="]"?"]":str[a];
});
var _3e=eval("1&&function($,$1,$2,$3,$4,$5,$6,$7,$8,$9){var $obj=$;return "+_1a+"}");
for(var i=0;i<arguments.length-1;i++){
arguments[i]=arguments[i+1];
}
return obj?_3e.apply(this,arguments):_3e;
};
})();
}
