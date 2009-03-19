/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._Container"]){
dojo._hasResource["dijit._Container"]=true;
dojo.provide("dijit._Container");
dojo.declare("dijit._Container",null,{isContainer:true,buildRendering:function(){
this.inherited(arguments);
if(!this.containerNode){
this.containerNode=this.domNode;
}
},addChild:function(_1,_2){
var _3=this.containerNode;
if(_2&&typeof _2=="number"){
var _4=this.getChildren();
if(_4&&_4.length>=_2){
_3=_4[_2-1].domNode;
_2="after";
}
}
dojo.place(_1.domNode,_3,_2);
if(this._started&&!_1._started){
_1.startup();
}
},removeChild:function(_5){
if(typeof _5=="number"&&_5>0){
_5=this.getChildren()[_5];
}
if(!_5||!_5.domNode){
return;
}
var _6=_5.domNode;
_6.parentNode.removeChild(_6);
},_nextElement:function(_7){
do{
_7=_7.nextSibling;
}while(_7&&_7.nodeType!=1);
return _7;
},_firstElement:function(_8){
_8=_8.firstChild;
if(_8&&_8.nodeType!=1){
_8=this._nextElement(_8);
}
return _8;
},getChildren:function(){
return dojo.query("> [widgetId]",this.containerNode).map(dijit.byNode);
},hasChildren:function(){
return !!this._firstElement(this.containerNode);
},destroyDescendants:function(_9){
dojo.forEach(this.getChildren(),function(_a){
_a.destroyRecursive(_9);
});
},_getSiblingOfChild:function(_b,_c){
var _d=_b.domNode;
var _e=(_c>0?"nextSibling":"previousSibling");
do{
_d=_d[_e];
}while(_d&&(_d.nodeType!=1||!dijit.byNode(_d)));
return _d?dijit.byNode(_d):null;
},getIndexOfChild:function(_f){
var _10=this.getChildren();
for(var i=0,c;c=_10[i];i++){
if(c==_f){
return i;
}
}
return -1;
}});
}
