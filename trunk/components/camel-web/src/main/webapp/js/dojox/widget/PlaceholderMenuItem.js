/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.PlaceholderMenuItem"]){
dojo._hasResource["dojox.widget.PlaceholderMenuItem"]=true;
dojo.provide("dojox.widget.PlaceholderMenuItem");
dojo.require("dijit.Menu");
dojo.declare("dojox.widget.PlaceholderMenuItem",dijit.MenuItem,{_replaced:false,_replacedWith:null,_isPlaceholder:true,postCreate:function(){
this.domNode.style.display="none";
this._replacedWith=[];
if(!this.label){
this.label=this.containerNode.innerHTML;
}
this.inherited(arguments);
},replace:function(_1){
if(this._replaced){
return false;
}
var _2=this.getIndexInParent();
if(_2<0){
return false;
}
var p=this.getParent();
dojo.forEach(_1,function(_4){
p.addChild(_4,_2++);
});
this._replacedWith=_1;
this._replaced=true;
return true;
},unReplace:function(_5){
if(!this._replaced){
return [];
}
var p=this.getParent();
if(!p){
return [];
}
var r=this._replacedWith;
dojo.forEach(this._replacedWith,function(_8){
p.removeChild(_8);
if(_5){
_8.destroy();
}
});
this._replacedWith=[];
this._replaced=false;
return r;
}});
dojo.extend(dijit.Menu,{getPlaceholders:function(_9){
var r=[];
var _b=this.getChildren();
_b.forEach(function(_c){
if(_c._isPlaceholder&&(!_9||_c.label==_9)){
r.push(_c);
}else{
if(_c._started&&_c.popup&&_c.popup.getPlaceholders){
r=r.concat(_c.popup.getPlaceholders(_9));
}else{
if(!_c._started&&_c.dropDownContainer){
var _d=dojo.query("[widgetId]",_c.dropDownContainer)[0];
var _e=dijit.byNode(_d);
if(_e.getPlaceholders){
r=r.concat(_e.getPlaceholders(_9));
}
}
}
}
},this);
return r;
}});
}
