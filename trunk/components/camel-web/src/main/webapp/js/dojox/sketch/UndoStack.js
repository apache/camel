/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sketch.UndoStack"]){
dojo._hasResource["dojox.sketch.UndoStack"]=true;
dojo.provide("dojox.sketch.UndoStack");
dojo.require("dojox.xml.DomParser");
(function(){
var ta=dojox.sketch;
ta.CommandTypes={Create:"Create",Move:"Move",Modify:"Modify",Delete:"Delete",Convert:"Convert"};
dojo.declare("dojox.sketch.UndoStack",null,{constructor:function(_2){
this.figure=_2;
this._steps=[];
this._undoedSteps=[];
},apply:function(_3,_4,to){
if(!_4&&!to&&_3.fullText){
this.figure.setValue(_3.fullText);
return;
}
var _6=_4.shapeText;
var _7=to.shapeText;
if(_6.length==0&&_7.length==0){
return;
}
if(_6.length==0){
var o=dojox.xml.DomParser.parse(_7).documentElement;
var a=this.figure._loadAnnotation(o);
if(a){
this.figure._add(a);
}
return;
}
if(_7.length==0){
var _a=this.figure.get(_4.shapeId);
this.figure._delete([_a],true);
return;
}
var _b=this.figure.get(to.shapeId);
var no=dojox.xml.DomParser.parse(_7).documentElement;
_b.draw(no);
this.figure.select(_b);
return;
},add:function(_d,_e,_f){
var id=_e?_e.id:"";
var _11=_e?_e.serialize():"";
if(_d==ta.CommandTypes.Delete){
_11="";
}
var _12={cmdname:_d,before:{shapeId:id,shapeText:_f||""},after:{shapeId:id,shapeText:_11}};
this._steps.push(_12);
this._undoedSteps=[];
},destroy:function(){
},undo:function(){
var _13=this._steps.pop();
if(_13){
this._undoedSteps.push(_13);
this.apply(_13,_13.after,_13.before);
}
},redo:function(){
var _14=this._undoedSteps.pop();
if(_14){
this._steps.push(_14);
this.apply(_14,_14.before,_14.after);
}
}});
})();
}
