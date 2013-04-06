/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form._SelectStackMixin"]){
dojo._hasResource["dojox.form._SelectStackMixin"]=true;
dojo.provide("dojox.form._SelectStackMixin");
dojo.declare("dojox.form._SelectStackMixin",null,{stackId:"",stackPrefix:"",_paneIdFromOption:function(_1){
return (this.stackPrefix||"")+_1;
},_optionValFromPane:function(id){
var sp=this.stackPrefix;
if(sp&&id.indexOf(sp)===0){
return id.substring(sp.length);
}
return id;
},_togglePane:function(_4,_5){
if(_4._shown!=undefined&&_4._shown==_5){
return;
}
var _6=dojo.filter(_4.getDescendants(),"return item.name;");
if(!_5){
_7={};
dojo.forEach(_6,function(w){
_7[w.id]=w.disabled;
w.attr("disabled",true);
});
_4._savedStates=_7;
}else{
var _7=_4._savedStates||{};
dojo.forEach(_6,function(w){
var _a=_7[w.id];
if(_a==undefined){
_a=false;
}
w.attr("disabled",_a);
});
delete _4._savedStates;
}
_4._shown=_5;
},onAddChild:function(_b,_c){
if(!this._panes[_b.id]){
this._panes[_b.id]=_b;
this.addOption({value:this._optionValFromPane(_b.id),label:_b.title});
}
if(!_b.onShow||!_b.onHide||_b._shown==undefined){
_b.onShow=dojo.hitch(this,"_togglePane",_b,true);
_b.onHide=dojo.hitch(this,"_togglePane",_b,false);
_b.onHide();
}
},onRemoveChild:function(_d){
if(this._panes[_d.id]){
delete this._panes[_d.id];
this.removeOption(this._optionValFromPane(_d.id));
}
},onSelectChild:function(_e){
this._setValueAttr(this._optionValFromPane(_e.id));
},onStartup:function(_f){
var _10=_f.selected;
dojo.forEach(_f.children,function(c){
this.onAddChild(c);
if(this._savedValue&&this._optionValFromPane(c.id)){
_10=c;
}
},this);
delete this._savedValue;
this.onSelectChild(_10);
if(!_10._shown){
this._togglePane(_10,true);
}
},postMixInProperties:function(){
this._savedValue=this.value;
this.inherited(arguments);
},postCreate:function(){
this.inherited(arguments);
this._panes={};
this._subscriptions=[dojo.subscribe(this.stackId+"-startup",this,"onStartup"),dojo.subscribe(this.stackId+"-addChild",this,"onAddChild"),dojo.subscribe(this.stackId+"-removeChild",this,"onRemoveChild"),dojo.subscribe(this.stackId+"-selectChild",this,"onSelectChild")];
var _12=dijit.byId(this.stackId);
if(_12&&_12._started){
this.onStartup({children:_12.getChildren(),selected:_12.selectedChildWidget});
}
},destroy:function(){
dojo.forEach(this._subscriptions,dojo.unsubscribe);
delete this._panes;
this.inherited("destroy",arguments);
},onChange:function(val){
var _14=this._panes[this._paneIdFromOption(val)];
if(_14){
dijit.byId(this.stackId).selectChild(_14);
}
}});
}
