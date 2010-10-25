/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._grid.drag"]){
dojo._hasResource["dojox.grid.compat._grid.drag"]=true;
dojo.provide("dojox.grid.compat._grid.drag");
(function(){
var _1=dojox.grid.drag={};
_1.dragging=false;
_1.hysteresis=2;
_1.capture=function(_2){
if(_2.setCapture){
_2.setCapture();
}else{
document.addEventListener("mousemove",_2.onmousemove,true);
document.addEventListener("mouseup",_2.onmouseup,true);
document.addEventListener("click",_2.onclick,true);
}
};
_1.release=function(_3){
if(_3.releaseCapture){
_3.releaseCapture();
}else{
document.removeEventListener("click",_3.onclick,true);
document.removeEventListener("mouseup",_3.onmouseup,true);
document.removeEventListener("mousemove",_3.onmousemove,true);
}
};
_1.start=function(_4,_5,_6,_7,_8){
if(!_4||_1.dragging){

return;
}
_1.dragging=true;
_1.elt=_4;
_1.events={drag:_5||dojox.grid.nop,end:_6||dojox.grid.nop,start:_8||dojox.grid.nop,oldmove:_4.onmousemove,oldup:_4.onmouseup,oldclick:_4.onclick};
_1.positionX=(_7&&("screenX" in _7)?_7.screenX:false);
_1.positionY=(_7&&("screenY" in _7)?_7.screenY:false);
_1.started=(_1.position===false);
_4.onmousemove=_1.mousemove;
_4.onmouseup=_1.mouseup;
_4.onclick=_1.click;
_1.capture(_1.elt);
};
_1.end=function(){
_1.release(_1.elt);
_1.elt.onmousemove=_1.events.oldmove;
_1.elt.onmouseup=_1.events.oldup;
_1.elt.onclick=_1.events.oldclick;
_1.elt=null;
try{
if(_1.started){
_1.events.end();
}
}
finally{
_1.dragging=false;
}
};
_1.calcDelta=function(_9){
_9.deltaX=_9.screenX-_1.positionX;
_9.deltaY=_9.screenY-_1.positionY;
};
_1.hasMoved=function(_a){
return Math.abs(_a.deltaX)+Math.abs(_a.deltaY)>_1.hysteresis;
};
_1.mousemove=function(_b){
_b=dojo.fixEvent(_b);
dojo.stopEvent(_b);
_1.calcDelta(_b);
if((!_1.started)&&(_1.hasMoved(_b))){
_1.events.start(_b);
_1.started=true;
}
if(_1.started){
_1.events.drag(_b);
}
};
_1.mouseup=function(_c){
dojo.stopEvent(dojo.fixEvent(_c));
_1.end();
};
_1.click=function(_d){
dojo.stopEvent(dojo.fixEvent(_d));
};
})();
}
