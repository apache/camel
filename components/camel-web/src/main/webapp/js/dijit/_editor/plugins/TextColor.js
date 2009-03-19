/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.TextColor"]){
dojo._hasResource["dijit._editor.plugins.TextColor"]=true;
dojo.provide("dijit._editor.plugins.TextColor");
dojo.require("dijit._editor._Plugin");
dojo.require("dijit.ColorPalette");
dojo.declare("dijit._editor.plugins.TextColor",dijit._editor._Plugin,{buttonClass:dijit.form.DropDownButton,constructor:function(){
this.dropDown=new dijit.ColorPalette();
this.connect(this.dropDown,"onChange",function(_1){
this.editor.execCommand(this.command,_1);
});
}});
dojo.subscribe(dijit._scopeName+".Editor.getPlugin",null,function(o){
if(o.plugin){
return;
}
switch(o.args.name){
case "foreColor":
case "hiliteColor":
o.plugin=new dijit._editor.plugins.TextColor({command:o.args.name});
}
});
}
