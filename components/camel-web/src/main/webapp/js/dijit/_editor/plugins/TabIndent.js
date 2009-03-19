/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.TabIndent"]){
dojo._hasResource["dijit._editor.plugins.TabIndent"]=true;
dojo.provide("dijit._editor.plugins.TabIndent");
dojo.experimental("dijit._editor.plugins.TabIndent");
dojo.require("dijit._editor._Plugin");
dojo.declare("dijit._editor.plugins.TabIndent",dijit._editor._Plugin,{useDefaultCommand:false,buttonClass:dijit.form.ToggleButton,command:"tabIndent",_initButton:function(){
this.inherited("_initButton",arguments);
this.connect(this.button,"onClick",this._tabIndent);
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
var _3=_e.isTabIndent;
if(typeof this.button.checked=="boolean"){
this.button.attr("checked",_3);
}
}
catch(e){

}
}
},_tabIndent:function(){
this.editor.isTabIndent=!this.editor.isTabIndent;
}});
dojo.subscribe(dijit._scopeName+".Editor.getPlugin",null,function(o){
if(o.plugin){
return;
}
switch(o.args.name){
case "tabIndent":
o.plugin=new dijit._editor.plugins.TabIndent({command:o.args.name});
}
});
}
