/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.cells.dijit"]){
dojo._hasResource["dojox.grid.cells.dijit"]=true;
dojo.provide("dojox.grid.cells.dijit");
dojo.require("dojox.grid.cells");
dojo.require("dijit.form.DateTextBox");
dojo.require("dijit.form.TimeTextBox");
dojo.require("dijit.form.ComboBox");
dojo.require("dojo.data.ItemFileReadStore");
dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form.TextBox");
dojo.require("dijit.form.NumberSpinner");
dojo.require("dijit.form.NumberTextBox");
dojo.require("dijit.form.CurrencyTextBox");
dojo.require("dijit.form.HorizontalSlider");
dojo.require("dijit.Editor");
(function(){
var _1=dojox.grid.cells;
dojo.declare("dojox.grid.cells._Widget",_1._Base,{widgetClass:dijit.form.TextBox,constructor:function(_2){
this.widget=null;
if(typeof this.widgetClass=="string"){
dojo.deprecated("Passing a string to widgetClass is deprecated","pass the widget class object instead","2.0");
this.widgetClass=dojo.getObject(this.widgetClass);
}
},formatEditing:function(_3,_4){
this.needFormatNode(_3,_4);
return "<div></div>";
},getValue:function(_5){
return this.widget.attr("value");
},setValue:function(_6,_7){
if(this.widget&&this.widget.attr){
if(this.widget.onLoadDeferred){
var _8=this;
this.widget.onLoadDeferred.addCallback(function(){
_8.widget.attr("value",_7==null?"":_7);
});
}else{
this.widget.attr("value",_7);
}
}else{
this.inherited(arguments);
}
},getWidgetProps:function(_9){
return dojo.mixin({},this.widgetProps||{},{constraints:dojo.mixin({},this.constraint)||{},value:_9});
},createWidget:function(_a,_b,_c){
return new this.widgetClass(this.getWidgetProps(_b),_a);
},attachWidget:function(_d,_e,_f){
_d.appendChild(this.widget.domNode);
this.setValue(_f,_e);
},formatNode:function(_10,_11,_12){
if(!this.widgetClass){
return _11;
}
if(!this.widget){
this.widget=this.createWidget.apply(this,arguments);
}else{
this.attachWidget.apply(this,arguments);
}
this.sizeWidget.apply(this,arguments);
this.grid.rowHeightChanged(_12);
this.focus();
},sizeWidget:function(_13,_14,_15){
var p=this.getNode(_15),box=dojo.contentBox(p);
dojo.marginBox(this.widget.domNode,{w:box.w});
},focus:function(_18,_19){
if(this.widget){
setTimeout(dojo.hitch(this.widget,function(){
dojox.grid.util.fire(this,"focus");
}),0);
}
},_finish:function(_1a){
this.inherited(arguments);
dojox.grid.util.removeNode(this.widget.domNode);
}});
_1._Widget.markupFactory=function(_1b,_1c){
_1._Base.markupFactory(_1b,_1c);
var d=dojo;
var _1e=d.trim(d.attr(_1b,"widgetProps")||"");
var _1f=d.trim(d.attr(_1b,"constraint")||"");
var _20=d.trim(d.attr(_1b,"widgetClass")||"");
if(_1e){
_1c.widgetProps=d.fromJson(_1e);
}
if(_1f){
_1c.constraint=d.fromJson(_1f);
}
if(_20){
_1c.widgetClass=d.getObject(_20);
}
};
dojo.declare("dojox.grid.cells.ComboBox",_1._Widget,{widgetClass:dijit.form.ComboBox,getWidgetProps:function(_21){
var _22=[];
dojo.forEach(this.options,function(o){
_22.push({name:o,value:o});
});
var _24=new dojo.data.ItemFileReadStore({data:{identifier:"name",items:_22}});
return dojo.mixin({},this.widgetProps||{},{value:_21,store:_24});
},getValue:function(){
var e=this.widget;
e.attr("displayedValue",e.attr("displayedValue"));
return e.attr("value");
}});
_1.ComboBox.markupFactory=function(_26,_27){
_1._Widget.markupFactory(_26,_27);
var d=dojo;
var _29=d.trim(d.attr(_26,"options")||"");
if(_29){
var o=_29.split(",");
if(o[0]!=_29){
_27.options=o;
}
}
};
dojo.declare("dojox.grid.cells.DateTextBox",_1._Widget,{widgetClass:dijit.form.DateTextBox,setValue:function(_2b,_2c){
if(this.widget){
this.widget.attr("value",new Date(_2c));
}else{
this.inherited(arguments);
}
},getWidgetProps:function(_2d){
return dojo.mixin(this.inherited(arguments),{value:new Date(_2d)});
}});
_1.DateTextBox.markupFactory=function(_2e,_2f){
_1._Widget.markupFactory(_2e,_2f);
};
dojo.declare("dojox.grid.cells.CheckBox",_1._Widget,{widgetClass:dijit.form.CheckBox,getValue:function(){
return this.widget.checked;
},setValue:function(_30,_31){
if(this.widget&&this.widget.attributeMap.checked){
this.widget.attr("checked",_31);
}else{
this.inherited(arguments);
}
},sizeWidget:function(_32,_33,_34){
return;
}});
_1.CheckBox.markupFactory=function(_35,_36){
_1._Widget.markupFactory(_35,_36);
};
dojo.declare("dojox.grid.cells.Editor",_1._Widget,{widgetClass:dijit.Editor,getWidgetProps:function(_37){
return dojo.mixin({},this.widgetProps||{},{height:this.widgetHeight||"100px"});
},createWidget:function(_38,_39,_3a){
var _3b=new this.widgetClass(this.getWidgetProps(_39),_38);
dojo.connect(_3b,"onLoad",dojo.hitch(this,"populateEditor"));
return _3b;
},formatNode:function(_3c,_3d,_3e){
this.content=_3d;
this.inherited(arguments);
if(dojo.isMoz){
var e=this.widget;
e.open();
if(this.widgetToolbar){
dojo.place(e.toolbar.domNode,e.editingArea,"before");
}
}
},populateEditor:function(){
this.widget.attr("value",this.content);
this.widget.placeCursorAtEnd();
}});
_1.Editor.markupFactory=function(_40,_41){
_1._Widget.markupFactory(_40,_41);
var d=dojo;
var h=dojo.trim(dojo.attr(_40,"widgetHeight")||"");
if(h){
if((h!="auto")&&(h.substr(-2)!="em")){
h=parseInt(h)+"px";
}
_41.widgetHeight=h;
}
};
})();
}
