/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor._Plugin"]){
dojo._hasResource["dijit._editor._Plugin"]=true;
dojo.provide("dijit._editor._Plugin");
dojo.require("dijit._Widget");
dojo.require("dijit.Editor");
dojo.require("dijit.form.Button");
dojo.declare("dijit._editor._Plugin",null,{constructor:function(_1,_2){
if(_1){
dojo.mixin(this,_1);
}
this._connects=[];
},editor:null,iconClassPrefix:"dijitEditorIcon",button:null,queryCommand:null,command:"",commandArg:null,useDefaultCommand:true,buttonClass:dijit.form.Button,getLabel:function(_3){
return this.editor.commands[_3];
},_initButton:function(_4){
if(this.command.length){
var _5=this.getLabel(this.command);
var _6=this.iconClassPrefix+" "+this.iconClassPrefix+this.command.charAt(0).toUpperCase()+this.command.substr(1);
if(!this.button){
_4=dojo.mixin({label:_5,showLabel:false,iconClass:_6,dropDown:this.dropDown,tabIndex:"-1"},_4||{});
this.button=new this.buttonClass(_4);
}
}
},destroy:function(f){
dojo.forEach(this._connects,dojo.disconnect);
if(this.dropDown){
this.dropDown.destroyRecursive();
}
},connect:function(o,f,tf){
this._connects.push(dojo.connect(o,f,this,tf));
},updateState:function(){
var _e=this.editor;
var _c=this.command;
if(!_e){
return;
}
if(!_e.isLoaded){
return;
}
if(!_c.length){
return;
}
if(this.button){
try{
var _d=_e.queryCommandEnabled(_c);
if(this.enabled!==_d){
this.enabled=_d;
this.button.attr("disabled",!_d);
}
if(typeof this.button.checked=="boolean"){
var _e=_e.queryCommandState(_c);
if(this.checked!==_e){
this.checked=_e;
this.button.attr("checked",_e.queryCommandState(_c));
}
}
}
catch(e){

}
}
},setEditor:function(_f){
this.editor=_f;
this._initButton();
if(this.command.length&&!this.editor.queryCommandAvailable(this.command)){
if(this.button){
this.button.domNode.style.display="none";
}
}
if(this.button&&this.useDefaultCommand){
this.connect(this.button,"onClick",dojo.hitch(this.editor,"execCommand",this.command,this.commandArg));
}
this.connect(this.editor,"onNormalizedDisplayChanged","updateState");
},setToolbar:function(_10){
if(this.button){
_10.addChild(this.button);
}
}});
}
