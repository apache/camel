/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.testing.DocTest"]){
dojo._hasResource["dojox.testing.DocTest"]=true;
dojo.provide("dojox.testing.DocTest");
dojo.require("dojo.string");
dojo.declare("dojox.testing.DocTest",null,{errors:[],getTests:function(_1){
var _2=dojo.moduleUrl(_1).path;
var _3=_2.substring(0,_2.length-1)+".js";
var _4=dojo.xhrGet({url:_3,handleAs:"text"});
var _5=dojo._getText(_3);
return this._getTestsFromString(_5,true);
},getTestsFromString:function(_6){
return this._getTestsFromString(_6,false);
},_getTestsFromString:function(_7,_8){
var _9=dojo.hitch(dojo.string,"trim");
var _a=_7.split("\n");
var _b=_a.length;
var _c=[];
var _d={commands:[],expectedResult:[],line:null};
for(var i=0;i<_b+1;i++){
var l=_9(_a[i]||"");
if((_8&&l.match(/^\/\/\s+>>>\s.*/))||l.match(/^\s*>>>\s.*/)){
if(!_d.line){
_d.line=i+1;
}
if(_d.expectedResult.length>0){
_c.push({commands:_d.commands,expectedResult:_d.expectedResult.join("\n"),line:_d.line});
_d={commands:[],expectedResult:[],line:i+1};
}
l=_8?_9(l).substring(2,l.length):l;
l=_9(l).substring(3,l.length);
_d.commands.push(_9(l));
}else{
if((!_8||l.match(/^\/\/\s+.*/))&&_d.commands.length&&_d.expectedResult.length==0){
l=_8?_9(l).substring(3,l.length):l;
_d.expectedResult.push(_9(l));
}else{
if(_d.commands.length>0&&_d.expectedResult.length){
if(!_8||l.match(/^\/\/\s*$/)){
_c.push({commands:_d.commands,expectedResult:_d.expectedResult.join("\n"),line:_d.line});
}
if(_8&&!l.match(/^\/\//)){
_c.push({commands:_d.commands,expectedResult:_d.expectedResult.join("\n"),line:_d.line});
}
_d={commands:[],expectedResult:[],line:0};
}
}
}
}
return _c;
},run:function(_10){
this.errors=[];
var _11=this.getTests(_10);
if(_11){
this._run(_11);
}
},_run:function(_12){
var len=_12.length;
this.tests=len;
var oks=0;
for(var i=0;i<len;i++){
var t=_12[i];
var res=this.runTest(t.commands,t.expectedResult);
var msg="Test "+(i+1)+": ";
var _19=t.commands.join(" ");
_19=(_19.length>50?_19.substr(0,50)+"...":_19);
if(res.success){

oks+=1;
}else{
this.errors.push({commands:t.commands,actual:res.actualResult,expected:t.expectedResult});
console.error(msg+"Failed: "+_19,{commands:t.commands,actualResult:res.actualResult,expectedResult:t.expectedResult});
}
}

},runTest:function(_1a,_1b){
var ret={success:false,actualResult:null};
var _1d=_1a.join("\n");
ret.actualResult=eval(_1d);
if((String(ret.actualResult)==_1b)||(dojo.toJson(ret.actualResult)==_1b)||((_1b.charAt(0)=="\"")&&(_1b.charAt(_1b.length-1)=="\"")&&(String(ret.actualResult)==_1b.substring(1,_1b.length-1)))){
ret.success=true;
}
return ret;
}});
}
