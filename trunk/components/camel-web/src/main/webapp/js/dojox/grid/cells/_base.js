/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.cells._base"]){
dojo._hasResource["dojox.grid.cells._base"]=true;
dojo.provide("dojox.grid.cells._base");
dojo.require("dojox.grid.util");
(function(){
var _1=function(_2){
try{
dojox.grid.util.fire(_2,"focus");
dojox.grid.util.fire(_2,"select");
}
catch(e){
}
};
var _3=function(){
setTimeout(dojo.hitch.apply(dojo,arguments),0);
};
var _4=dojox.grid.cells;
dojo.declare("dojox.grid.cells._Base",null,{styles:"",classes:"",editable:false,alwaysEditing:false,formatter:null,defaultValue:"...",value:null,hidden:false,noresize:false,_valueProp:"value",_formatPending:false,constructor:function(_5){
this._props=_5||{};
dojo.mixin(this,_5);
},format:function(_6,_7){
var f,i=this.grid.edit.info,d=this.get?this.get(_6,_7):(this.value||this.defaultValue);
if(this.editable&&(this.alwaysEditing||(i.rowIndex==_6&&i.cell==this))){
return this.formatEditing(d,_6);
}else{
var v=(d!=this.defaultValue&&(f=this.formatter))?f.call(this,d,_6):d;
return (typeof v=="undefined"?this.defaultValue:v);
}
},formatEditing:function(_c,_d){
},getNode:function(_e){
return this.view.getCellNode(_e,this.index);
},getHeaderNode:function(){
return this.view.getHeaderCellNode(this.index);
},getEditNode:function(_f){
return (this.getNode(_f)||0).firstChild||0;
},canResize:function(){
var uw=this.unitWidth;
return uw&&(uw=="auto");
},isFlex:function(){
var uw=this.unitWidth;
return uw&&dojo.isString(uw)&&(uw=="auto"||uw.slice(-1)=="%");
},applyEdit:function(_12,_13){
this.grid.edit.applyCellEdit(_12,this,_13);
},cancelEdit:function(_14){
this.grid.doCancelEdit(_14);
},_onEditBlur:function(_15){
if(this.grid.edit.isEditCell(_15,this.index)){
this.grid.edit.apply();
}
},registerOnBlur:function(_16,_17){
if(this.commitOnBlur){
dojo.connect(_16,"onblur",function(e){
setTimeout(dojo.hitch(this,"_onEditBlur",_17),250);
});
}
},needFormatNode:function(_19,_1a){
this._formatPending=true;
_3(this,"_formatNode",_19,_1a);
},cancelFormatNode:function(){
this._formatPending=false;
},_formatNode:function(_1b,_1c){
if(this._formatPending){
this._formatPending=false;
dojo.setSelectable(this.grid.domNode,true);
this.formatNode(this.getEditNode(_1c),_1b,_1c);
}
},formatNode:function(_1d,_1e,_1f){
if(dojo.isIE){
_3(this,"focus",_1f,_1d);
}else{
this.focus(_1f,_1d);
}
},dispatchEvent:function(m,e){
if(m in this){
return this[m](e);
}
},getValue:function(_22){
return this.getEditNode(_22)[this._valueProp];
},setValue:function(_23,_24){
var n=this.getEditNode(_23);
if(n){
n[this._valueProp]=_24;
}
},focus:function(_26,_27){
_1(_27||this.getEditNode(_26));
},save:function(_28){
this.value=this.value||this.getValue(_28);
},restore:function(_29){
this.setValue(_29,this.value);
},_finish:function(_2a){
dojo.setSelectable(this.grid.domNode,false);
this.cancelFormatNode();
},apply:function(_2b){
this.applyEdit(this.getValue(_2b),_2b);
this._finish(_2b);
},cancel:function(_2c){
this.cancelEdit(_2c);
this._finish(_2c);
}});
_4._Base.markupFactory=function(_2d,_2e){
var d=dojo;
var _30=d.trim(d.attr(_2d,"formatter")||"");
if(_30){
_2e.formatter=dojo.getObject(_30);
}
var get=d.trim(d.attr(_2d,"get")||"");
if(get){
_2e.get=dojo.getObject(get);
}
var _32=function(_33){
var _34=d.trim(d.attr(_2d,_33)||"");
return _34?!(_34.toLowerCase()=="false"):undefined;
};
_2e.sortDesc=_32("sortDesc");
_2e.editable=_32("editable");
_2e.alwaysEditing=_32("alwaysEditing");
_2e.noresize=_32("noresize");
var _35=d.trim(d.attr(_2d,"loadingText")||d.attr(_2d,"defaultValue")||"");
if(_35){
_2e.defaultValue=_35;
}
var _36=function(_37){
return d.trim(d.attr(_2d,_37)||"")||undefined;
};
_2e.styles=_36("styles");
_2e.headerStyles=_36("headerStyles");
_2e.cellStyles=_36("cellStyles");
_2e.classes=_36("classes");
_2e.headerClasses=_36("headerClasses");
_2e.cellClasses=_36("cellClasses");
};
dojo.declare("dojox.grid.cells.Cell",_4._Base,{constructor:function(){
this.keyFilter=this.keyFilter;
},keyFilter:null,formatEditing:function(_38,_39){
this.needFormatNode(_38,_39);
return "<input class=\"dojoxGridInput\" type=\"text\" value=\""+_38+"\">";
},formatNode:function(_3a,_3b,_3c){
this.inherited(arguments);
this.registerOnBlur(_3a,_3c);
},doKey:function(e){
if(this.keyFilter){
var key=String.fromCharCode(e.charCode);
if(key.search(this.keyFilter)==-1){
dojo.stopEvent(e);
}
}
},_finish:function(_3f){
this.inherited(arguments);
var n=this.getEditNode(_3f);
try{
dojox.grid.util.fire(n,"blur");
}
catch(e){
}
}});
_4.Cell.markupFactory=function(_41,_42){
_4._Base.markupFactory(_41,_42);
var d=dojo;
var _44=d.trim(d.attr(_41,"keyFilter")||"");
if(_44){
_42.keyFilter=new RegExp(_44);
}
};
dojo.declare("dojox.grid.cells.RowIndex",_4.Cell,{name:"Row",postscript:function(){
this.editable=false;
},get:function(_45){
return _45+1;
}});
_4.RowIndex.markupFactory=function(_46,_47){
_4.Cell.markupFactory(_46,_47);
};
dojo.declare("dojox.grid.cells.Select",_4.Cell,{options:null,values:null,returnIndex:-1,constructor:function(_48){
this.values=this.values||this.options;
},formatEditing:function(_49,_4a){
this.needFormatNode(_49,_4a);
var h=["<select class=\"dojoxGridSelect\">"];
for(var i=0,o,v;((o=this.options[i])!==undefined)&&((v=this.values[i])!==undefined);i++){
h.push("<option",(_49==v?" selected":"")," value=\""+v+"\"",">",o,"</option>");
}
h.push("</select>");
return h.join("");
},getValue:function(_4f){
var n=this.getEditNode(_4f);
if(n){
var i=n.selectedIndex,o=n.options[i];
return this.returnIndex>-1?i:o.value||o.innerHTML;
}
}});
_4.Select.markupFactory=function(_53,_54){
_4.Cell.markupFactory(_53,_54);
var d=dojo;
var _56=d.trim(d.attr(_53,"options")||"");
if(_56){
var o=_56.split(",");
if(o[0]!=_56){
_54.options=o;
}
}
var _58=d.trim(d.attr(_53,"values")||"");
if(_58){
var v=_58.split(",");
if(v[0]!=_58){
_54.values=v;
}
}
};
dojo.declare("dojox.grid.cells.AlwaysEdit",_4.Cell,{alwaysEditing:true,_formatNode:function(_5a,_5b){
this.formatNode(this.getEditNode(_5b),_5a,_5b);
},applyStaticValue:function(_5c){
var e=this.grid.edit;
e.applyCellEdit(this.getValue(_5c),this,_5c);
e.start(this,_5c,true);
}});
_4.AlwaysEdit.markupFactory=function(_5e,_5f){
_4.Cell.markupFactory(_5e,_5f);
};
dojo.declare("dojox.grid.cells.Bool",_4.AlwaysEdit,{_valueProp:"checked",formatEditing:function(_60,_61){
return "<input class=\"dojoxGridInput\" type=\"checkbox\""+(_60?" checked=\"checked\"":"")+" style=\"width: auto\" />";
},doclick:function(e){
if(e.target.tagName=="INPUT"){
this.applyStaticValue(e.rowIndex);
}
}});
_4.Bool.markupFactory=function(_63,_64){
_4.AlwaysEdit.markupFactory(_63,_64);
};
})();
}
