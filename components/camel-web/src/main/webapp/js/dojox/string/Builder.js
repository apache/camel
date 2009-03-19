/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.string.Builder"]){
dojo._hasResource["dojox.string.Builder"]=true;
dojo.provide("dojox.string.Builder");
dojox.string.Builder=function(_1){
var b="";
this.length=0;
this.append=function(s){
if(arguments.length>1){
var _4="",l=arguments.length;
switch(l){
case 9:
_4=""+arguments[8]+_4;
case 8:
_4=""+arguments[7]+_4;
case 7:
_4=""+arguments[6]+_4;
case 6:
_4=""+arguments[5]+_4;
case 5:
_4=""+arguments[4]+_4;
case 4:
_4=""+arguments[3]+_4;
case 3:
_4=""+arguments[2]+_4;
case 2:
b+=""+arguments[0]+arguments[1]+_4;
break;
default:
var i=0;
while(i<arguments.length){
_4+=arguments[i++];
}
b+=_4;
}
}else{
b+=s;
}
this.length=b.length;
return this;
};
this.concat=function(s){
return this.append.apply(this,arguments);
};
this.appendArray=function(_8){
return this.append.apply(this,_8);
};
this.clear=function(){
b="";
this.length=0;
return this;
};
this.replace=function(_9,_a){
b=b.replace(_9,_a);
this.length=b.length;
return this;
};
this.remove=function(_b,_c){
if(_c===undefined){
_c=b.length;
}
if(_c==0){
return this;
}
b=b.substr(0,_b)+b.substr(_b+_c);
this.length=b.length;
return this;
};
this.insert=function(_d,_e){
if(_d==0){
b=_e+b;
}else{
b=b.slice(0,_d)+_e+b.slice(_d);
}
this.length=b.length;
return this;
};
this.toString=function(){
return b;
};
if(_1){
this.append(_1);
}
};
}
