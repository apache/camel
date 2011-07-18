/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.dtl.filter.lists"]){
dojo._hasResource["dojox.dtl.filter.lists"]=true;
dojo.provide("dojox.dtl.filter.lists");
dojo.require("dojox.dtl._base");
dojo.mixin(dojox.dtl.filter.lists,{_dictsort:function(a,b){
if(a[0]==b[0]){
return 0;
}
return (a[0]<b[0])?-1:1;
},dictsort:function(_3,_4){
if(!_4){
return _3;
}
var i,_6,_7=[];
if(!dojo.isArray(_3)){
var _8=_3,_3=[];
for(var _9 in _8){
_3.push(_8[_9]);
}
}
for(i=0;i<_3.length;i++){
_7.push([new dojox.dtl._Filter("var."+_4).resolve(new dojox.dtl._Context({"var":_3[i]})),_3[i]]);
}
_7.sort(dojox.dtl.filter.lists._dictsort);
var _a=[];
for(i=0;_6=_7[i];i++){
_a.push(_6[1]);
}
return _a;
},dictsortreversed:function(_b,_c){
if(!_c){
return _b;
}
var _d=dojox.dtl.filter.lists.dictsort(_b,_c);
return _d.reverse();
},first:function(_e){
return (_e.length)?_e[0]:"";
},join:function(_f,arg){
return _f.join(arg||",");
},length:function(_11){
return (isNaN(_11.length))?(_11+"").length:_11.length;
},length_is:function(_12,arg){
return _12.length==parseInt(arg);
},random:function(_14){
return _14[Math.floor(Math.random()*_14.length)];
},slice:function(_15,arg){
arg=arg||"";
var _17=arg.split(":");
var _18=[];
for(var i=0;i<_17.length;i++){
if(!_17[i].length){
_18.push(null);
}else{
_18.push(parseInt(_17[i]));
}
}
if(_18[0]===null){
_18[0]=0;
}
if(_18[0]<0){
_18[0]=_15.length+_18[0];
}
if(_18.length<2||_18[1]===null){
_18[1]=_15.length;
}
if(_18[1]<0){
_18[1]=_15.length+_18[1];
}
return _15.slice(_18[0],_18[1]);
},_unordered_list:function(_1a,_1b){
var ddl=dojox.dtl.filter.lists;
var i,_1e="";
for(i=0;i<_1b;i++){
_1e+="\t";
}
if(_1a[1]&&_1a[1].length){
var _1f=[];
for(i=0;i<_1a[1].length;i++){
_1f.push(ddl._unordered_list(_1a[1][i],_1b+1));
}
return _1e+"<li>"+_1a[0]+"\n"+_1e+"<ul>\n"+_1f.join("\n")+"\n"+_1e+"</ul>\n"+_1e+"</li>";
}else{
return _1e+"<li>"+_1a[0]+"</li>";
}
},unordered_list:function(_20){
return dojox.dtl.filter.lists._unordered_list(_20,1);
}});
}
