/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.timing.Sequence"]){
dojo._hasResource["dojox.timing.Sequence"]=true;
dojo.provide("dojox.timing.Sequence");
dojo.experimental("dojox.timing.Sequence");
dojo.declare("dojox.timing.Sequence",null,{_goOnPause:0,_running:false,constructor:function(){
this._defsResolved=[];
},go:function(_1,_2){
this._running=true;
dojo.forEach(_1,function(_3){
if(_3.repeat>1){
var _4=_3.repeat;
for(var j=0;j<_4;j++){
_3.repeat=1;
this._defsResolved.push(_3);
}
}else{
this._defsResolved.push(_3);
}
},this);
var _6=_1[_1.length-1];
if(_2){
this._defsResolved.push({func:_2});
}
this._defsResolved.push({func:[this.stop,this]});
this._curId=0;
this._go();
},_go:function(){
if(!this._running){
return;
}
var _7=this._defsResolved[this._curId];
this._curId+=1;
function _8(_9){
var _a=null;
if(dojo.isArray(_9)){
if(_9.length>2){
_a=_9[0].apply(_9[1],_9.slice(2));
}else{
_a=_9[0].apply(_9[1]);
}
}else{
_a=_9();
}
return _a;
};
if(this._curId>=this._defsResolved.length){
_8(_7.func);
return;
}
if(_7.pauseAfter){
if(_8(_7.func)!==false){
setTimeout(dojo.hitch(this,"_go"),_7.pauseAfter);
}else{
this._goOnPause=_7.pauseAfter;
}
}else{
if(_7.pauseBefore){
var x=dojo.hitch(this,function(){
if(_8(_7.func)!==false){
this._go();
}
});
setTimeout(x,_7.pauseBefore);
}else{
if(_8(_7.func)!==false){
this._go();
}
}
}
},goOn:function(){
if(this._goOnPause){
setTimeout(dojo.hitch(this,"_go"),this._goOnPause);
this._goOnPause=0;
}else{
this._go();
}
},stop:function(){
this._running=false;
}});
}
