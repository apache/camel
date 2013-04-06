/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._data.dijitEditors"]){
dojo._hasResource["dojox.grid.compat._data.dijitEditors"]=true;
dojo.provide("dojox.grid.compat._data.dijitEditors");
dojo.require("dojox.grid.compat._data.editors");
dojo.require("dijit.form.DateTextBox");
dojo.require("dijit.form.TimeTextBox");
dojo.require("dijit.form.ComboBox");
dojo.require("dojo.data.ItemFileReadStore");
dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form.TextBox");
dojo.require("dijit.form.NumberSpinner");
dojo.require("dijit.form.NumberTextBox");
dojo.require("dijit.form.CurrencyTextBox");
dojo.require("dijit.form.Slider");
dojo.require("dijit.Editor");
dojo.declare("dojox.grid.editors.Dijit",dojox.grid.editors.base,{editorClass:"dijit.form.TextBox",constructor:function(_1){
this.editor=null;
this.editorClass=dojo.getObject(this.cell.editorClass||this.editorClass);
},format:function(_2,_3){
this.needFormatNode(_2,_3);
return "<div></div>";
},getValue:function(_4){
return this.editor.getValue();
},setValue:function(_5,_6){
if(this.editor&&this.editor.setValue){
if(this.editor.onLoadDeferred){
var _7=this;
this.editor.onLoadDeferred.addCallback(function(){
_7.editor.setValue(_6==null?"":_6);
});
}else{
this.editor.setValue(_6);
}
}else{
this.inherited(arguments);
}
},getEditorProps:function(_8){
return dojo.mixin({},this.cell.editorProps||{},{constraints:dojo.mixin({},this.cell.constraint)||{},value:_8});
},createEditor:function(_9,_a,_b){
return new this.editorClass(this.getEditorProps(_a),_9);
},attachEditor:function(_c,_d,_e){
_c.appendChild(this.editor.domNode);
this.setValue(_e,_d);
},formatNode:function(_f,_10,_11){
if(!this.editorClass){
return _10;
}
if(!this.editor){
this.editor=this.createEditor.apply(this,arguments);
}else{
this.attachEditor.apply(this,arguments);
}
this.sizeEditor.apply(this,arguments);
this.cell.grid.rowHeightChanged(_11);
this.focus();
},sizeEditor:function(_12,_13,_14){
var p=this.cell.getNode(_14),box=dojo.contentBox(p);
dojo.marginBox(this.editor.domNode,{w:box.w});
},focus:function(_17,_18){
if(this.editor){
setTimeout(dojo.hitch(this.editor,function(){
dojox.grid.fire(this,"focus");
}),0);
}
},_finish:function(_19){
this.inherited(arguments);
dojox.grid.removeNode(this.editor.domNode);
}});
dojo.declare("dojox.grid.editors.ComboBox",dojox.grid.editors.Dijit,{editorClass:"dijit.form.ComboBox",getEditorProps:function(_1a){
var _1b=[];
dojo.forEach(this.cell.options,function(o){
_1b.push({name:o,value:o});
});
var _1d=new dojo.data.ItemFileReadStore({data:{identifier:"name",items:_1b}});
return dojo.mixin({},this.cell.editorProps||{},{value:_1a,store:_1d});
},getValue:function(){
var e=this.editor;
e.setDisplayedValue(e.getDisplayedValue());
return e.getValue();
}});
dojo.declare("dojox.grid.editors.DateTextBox",dojox.grid.editors.Dijit,{editorClass:"dijit.form.DateTextBox",setValue:function(_1f,_20){
if(this.editor){
this.editor.setValue(new Date(_20));
}else{
this.inherited(arguments);
}
},getEditorProps:function(_21){
return dojo.mixin(this.inherited(arguments),{value:new Date(_21)});
}});
dojo.declare("dojox.grid.editors.CheckBox",dojox.grid.editors.Dijit,{editorClass:"dijit.form.CheckBox",getValue:function(){
return this.editor.checked;
},setValue:function(_22,_23){
if(this.editor&&this.editor.setAttribute){
this.editor.setAttribute("checked",_23);
}else{
this.inherited(arguments);
}
},sizeEditor:function(_24,_25,_26){
return;
}});
dojo.declare("dojox.grid.editors.Editor",dojox.grid.editors.Dijit,{editorClass:"dijit.Editor",getEditorProps:function(_27){
return dojo.mixin({},this.cell.editorProps||{},{height:this.cell.editorHeight||"100px"});
},createEditor:function(_28,_29,_2a){
var _2b=new this.editorClass(this.getEditorProps(_29),_28);
dojo.connect(_2b,"onLoad",dojo.hitch(this,"populateEditor"));
return _2b;
},formatNode:function(_2c,_2d,_2e){
this.content=_2d;
this.inherited(arguments);
if(dojo.isMoz){
var e=this.editor;
e.open();
if(this.cell.editorToolbar){
dojo.place(e.toolbar.domNode,e.editingArea,"before");
}
}
},populateEditor:function(){
this.editor.setValue(this.content);
this.editor.placeCursorAtEnd();
}});
}
