/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.observable"]){
dojo._hasResource["dojox.lang.observable"]=true;
dojo.provide("dojox.lang.observable");
dojo.experimental("dojox.lang.observable");
dojox.lang.observable=function(_1,_2,_3,_4){
return dojox.lang.makeObservable(_2,_3,_4)(_1);
};
dojox.lang.makeObservable=function(_5,_6,_7,_8){
_8=_8||{};
_7=_7||function(_9,_a,_b,_c){
return _a[_b].apply(_9,_c);
};
function _d(_e,_f,i){
return function(){
return _7(_e,_f,i,arguments);
};
};
if(dojox.lang.lettableWin){
var _11=dojox.lang.makeObservable;
_11.inc=(_11.inc||0)+1;
var _12="gettable_"+_11.inc;
dojox.lang.lettableWin[_12]=_5;
var _13="settable_"+_11.inc;
dojox.lang.lettableWin[_13]=_6;
var _14={};
return function(_15){
if(_15.__observable){
return _15.__observable;
}
if(_15.data__){
throw new Error("Can wrap an object that is already wrapped");
}
var _16=[],i,l;
for(i in _8){
_16.push(i);
}
var _19={type:1,event:1};
for(i in _15){
if(i.match(/^[a-zA-Z][\w\$_]*$/)&&!(i in _8)&&!(i in _19)){
_16.push(i);
}
}
var _1a=_16.join(",");
var _1b,_1c=_14[_1a];
if(!_1c){
var _1d="dj_lettable_"+(_11.inc++);
var _1e=_1d+"_dj_getter";
var _1f=["Class "+_1d,"\tPublic data__"];
for(i=0,l=_16.length;i<l;i++){
_1b=_16[i];
var _20=typeof _15[_1b];
if(_20=="function"||_8[_1b]){
_1f.push("  Public "+_1b);
}else{
if(_20!="object"){
_1f.push("\tPublic Property Let "+_1b+"(val)","\t\tCall "+_13+"(me.data__,\""+_1b+"\",val)","\tEnd Property","\tPublic Property Get "+_1b,"\t\t"+_1b+" = "+_12+"(me.data__,\""+_1b+"\")","\tEnd Property");
}
}
}
_1f.push("End Class");
_1f.push("Function "+_1e+"()","\tDim tmp","\tSet tmp = New "+_1d,"\tSet "+_1e+" = tmp","End Function");
dojox.lang.lettableWin.vbEval(_1f.join("\n"));
_14[_1a]=_1c=function(){
return dojox.lang.lettableWin.construct(_1e);
};
}

var _21=_1c();
_21.data__=_15;

try{
_15.__observable=_21;
}
catch(e){
}
for(i=0,l=_16.length;i<l;i++){
_1b=_16[i];
try{
var val=_15[_1b];
}
catch(e){

}
if(typeof val=="function"||_8[_1b]){
_21[_1b]=_d(_21,_15,_1b);
}
}
return _21;
};
}else{
return function(_23){
if(_23.__observable){
return _23.__observable;
}
var _24=_23 instanceof Array?[]:{};
_24.data__=_23;
for(var i in _23){
if(i.charAt(0)!="_"){
if(typeof _23[i]=="function"){
_24[i]=_d(_24,_23,i);
}else{
if(typeof _23[i]!="object"){
(function(i){
_24.__defineGetter__(i,function(){
return _5(_23,i);
});
_24.__defineSetter__(i,function(_27){
return _6(_23,i,_27);
});
})(i);
}
}
}
}
for(i in _8){
_24[i]=_d(_24,_23,i);
}
_23.__observable=_24;
return _24;
};
}
};
if(!{}.__defineGetter__){
if(dojo.isIE){
var frame;
if(document.body){
frame=document.createElement("iframe");
document.body.appendChild(frame);
}else{
document.write("<iframe id='dj_vb_eval_frame'></iframe>");
frame=document.getElementById("dj_vb_eval_frame");
}
frame.style.display="none";
var doc=frame.contentWindow.document;
dojox.lang.lettableWin=frame.contentWindow;
doc.write("<html><head><script language=\"VBScript\" type=\"text/VBScript\">"+"Function vb_global_eval(code)"+"ExecuteGlobal(code)"+"End Function"+"</script>"+"<script type=\"text/javascript\">"+"function vbEval(code){ \n"+"return vb_global_eval(code);"+"}"+"function construct(name){ \n"+"return window[name]();"+"}"+"</script>"+"</head><body>vb-eval</body></html>");
doc.close();
}else{
throw new Error("This browser does not support getters and setters");
}
}
dojox.lang.ReadOnlyProxy=dojox.lang.makeObservable(function(obj,i){
return obj[i];
},function(obj,i,_2c){
});
}
