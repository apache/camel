/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sketch.Toolbar"]){
dojo._hasResource["dojox.sketch.Toolbar"]=true;
dojo.provide("dojox.sketch.Toolbar");
dojo.require("dojox.sketch.Annotation");
dojo.require("dijit.Toolbar");
dojo.require("dijit.form.Button");
dojo.declare("dojox.sketch.ButtonGroup",null,{constructor:function(){
this._childMaps={};
this._children=[];
},add:function(_1){
this._childMaps[_1]=_1.connect(_1,"onActivate",dojo.hitch(this,"_resetGroup",_1));
this._children.push(_1);
},_resetGroup:function(p){
var cs=this._children;
dojo.forEach(cs,function(c){
if(p!=c&&c["attr"]){
c.attr("checked",false);
}
});
}});
dojo.declare("dojox.sketch.Toolbar",dijit.Toolbar,{figure:null,plugins:null,postCreate:function(){
this.inherited(arguments);
this.shapeGroup=new dojox.sketch.ButtonGroup;
this.connect(this.figure,"onLoad","reset");
if(!this.plugins){
this.plugins=["Slider","Lead","SingleArrow","DoubleArrow","Underline","Preexisting"];
}
this._plugins=[];
dojo.forEach(this.plugins,function(_5){
var _6=dojo.isString(_5)?_5:_5.name;
var p=new dojox.sketch.tools[_6](_5.args||{});
this._plugins.push(p);
p.setFigure(this.figure);
p.setToolbar(this);
if(!this._defaultTool&&p.button){
this._defaultTool=p;
}
},this);
},destroy:function(){
dojo.forEach(this._plugins,function(p){
p.destroy();
});
this.inherited(arguments);
delete this._defaultTool;
delete this._plugins;
},addGroupItem:function(_9,_a){
if(_a!="toolsGroup"){
console.error("not supported group "+_a);
return;
}
this.shapeGroup.add(_9);
},reset:function(){
this._defaultTool.activate();
},_setShape:function(s){
if(!this.figure.surface){
return;
}
if(this.figure.hasSelections()){
for(var i=0;i<this.figure.selected.length;i++){
var _d=this.figure.selected[i].serialize();
this.figure.convert(this.figure.selected[i],s);
this.figure.history.add(dojox.sketch.CommandTypes.Convert,this.figure.selected[i],_d);
}
}
}});
dojox.sketch.makeToolbar=function(_e,_f){
var _10=new dojox.sketch.Toolbar({"figure":_f});
_e.appendChild(_10.domNode);
return _10;
};
}
