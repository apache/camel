/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._base.manager"]){
dojo._hasResource["dijit._base.manager"]=true;
dojo.provide("dijit._base.manager");
dojo.declare("dijit.WidgetSet",null,{constructor:function(){
this._hash={};
},add:function(_1){
if(this._hash[_1.id]){
throw new Error("Tried to register widget with id=="+_1.id+" but that id is already registered");
}
this._hash[_1.id]=_1;
},remove:function(id){
delete this._hash[id];
},forEach:function(_3){
for(var id in this._hash){
_3(this._hash[id]);
}
},filter:function(_5){
var _6=new dijit.WidgetSet();
this.forEach(function(_7){
if(_5(_7)){
_6.add(_7);
}
});
return _6;
},byId:function(id){
return this._hash[id];
},byClass:function(_9){
return this.filter(function(_a){
return _a.declaredClass==_9;
});
}});
dijit.registry=new dijit.WidgetSet();
dijit._widgetTypeCtr={};
dijit.getUniqueId=function(_b){
var id;
do{
id=_b+"_"+(_b in dijit._widgetTypeCtr?++dijit._widgetTypeCtr[_b]:dijit._widgetTypeCtr[_b]=0);
}while(dijit.byId(id));
return id;
};
if(dojo.isIE){
dojo.addOnWindowUnload(function(){
dijit.registry.forEach(function(_d){
_d.destroy();
});
});
}
dijit.byId=function(id){
return (dojo.isString(id))?dijit.registry.byId(id):id;
};
dijit.byNode=function(_f){
return dijit.registry.byId(_f.getAttribute("widgetId"));
};
dijit.getEnclosingWidget=function(_10){
while(_10){
if(_10.getAttribute&&_10.getAttribute("widgetId")){
return dijit.registry.byId(_10.getAttribute("widgetId"));
}
_10=_10.parentNode;
}
return null;
};
dijit._tabElements={area:true,button:true,input:true,object:true,select:true,textarea:true};
dijit._isElementShown=function(_11){
var _12=dojo.style(_11);
return (_12.visibility!="hidden")&&(_12.visibility!="collapsed")&&(_12.display!="none")&&(dojo.attr(_11,"type")!="hidden");
};
dijit.isTabNavigable=function(_13){
if(dojo.hasAttr(_13,"disabled")){
return false;
}
var _14=dojo.hasAttr(_13,"tabindex");
var _15=dojo.attr(_13,"tabindex");
if(_14&&_15>=0){
return true;
}
var _16=_13.nodeName.toLowerCase();
if(((_16=="a"&&dojo.hasAttr(_13,"href"))||dijit._tabElements[_16])&&(!_14||_15>=0)){
return true;
}
return false;
};
dijit._getTabNavigable=function(_17){
var _18,_19,_1a,_1b,_1c,_1d;
var _1e=function(_1f){
dojo.query("> *",_1f).forEach(function(_20){
var _21=dijit._isElementShown(_20);
if(_21&&dijit.isTabNavigable(_20)){
var _22=dojo.attr(_20,"tabindex");
if(!dojo.hasAttr(_20,"tabindex")||_22==0){
if(!_18){
_18=_20;
}
_19=_20;
}else{
if(_22>0){
if(!_1a||_22<_1b){
_1b=_22;
_1a=_20;
}
if(!_1c||_22>=_1d){
_1d=_22;
_1c=_20;
}
}
}
}
if(_21&&_20.nodeName.toUpperCase()!="SELECT"){
_1e(_20);
}
});
};
if(dijit._isElementShown(_17)){
_1e(_17);
}
return {first:_18,last:_19,lowest:_1a,highest:_1c};
};
dijit.getFirstInTabbingOrder=function(_23){
var _24=dijit._getTabNavigable(dojo.byId(_23));
return _24.lowest?_24.lowest:_24.first;
};
dijit.getLastInTabbingOrder=function(_25){
var _26=dijit._getTabNavigable(dojo.byId(_25));
return _26.last?_26.last:_26.highest;
};
dijit.defaultDuration=dojo.config["defaultDuration"]||200;
}
