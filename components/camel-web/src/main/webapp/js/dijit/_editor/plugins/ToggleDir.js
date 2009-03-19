/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.ToggleDir"]){
dojo._hasResource["dijit._editor.plugins.ToggleDir"]=true;
dojo.provide("dijit._editor.plugins.ToggleDir");
dojo.experimental("dijit._editor.plugins.ToggleDir");
dojo.require("dijit._editor._Plugin");
dojo.declare("dijit._editor.plugins.ToggleDir",dijit._editor._Plugin,{useDefaultCommand:false,command:"toggleDir",_initButton:function(){
this.inherited("_initButton",arguments);
this.connect(this.button,"onClick",this._toggleDir);
},updateState:function(){
},_toggleDir:function(){
var _1=this.editor.editorObject.contentWindow.document.documentElement;
var _2=dojo.getComputedStyle(_1).direction=="ltr";
_1.dir=_2?"rtl":"ltr";
}});
dojo.subscribe(dijit._scopeName+".Editor.getPlugin",null,function(o){
if(o.plugin){
return;
}
switch(o.args.name){
case "toggleDir":
o.plugin=new dijit._editor.plugins.ToggleDir({command:o.args.name});
}
});
}
