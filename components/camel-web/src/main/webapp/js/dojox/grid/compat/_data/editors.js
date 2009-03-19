/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.compat._data.editors"]){
dojo._hasResource["dojox.grid.compat._data.editors"]=true;
dojo.provide("dojox.grid.compat._data.editors");
dojo.provide("dojox.grid.compat.editors");
dojo.declare("dojox.grid.editors.Base",null,{constructor:function(_1){
this.cell=_1;
},_valueProp:"value",_formatPending:false,format:function(_2,_3){
},needFormatNode:function(_4,_5){
this._formatPending=true;
dojox.grid.whenIdle(this,"_formatNode",_4,_5);
},cancelFormatNode:function(){
this._formatPending=false;
},_formatNode:function(_6,_7){
if(this._formatPending){
this._formatPending=false;
dojo.setSelectable(this.cell.grid.domNode,true);
this.formatNode(this.getNode(_7),_6,_7);
}
},getNode:function(_8){
return (this.cell.getNode(_8)||0).firstChild||0;
},formatNode:function(_9,_a,_b){
if(dojo.isIE){
dojox.grid.whenIdle(this,"focus",_b,_9);
}else{
this.focus(_b,_9);
}
},dispatchEvent:function(m,e){
if(m in this){
return this[m](e);
}
},getValue:function(_e){
return this.getNode(_e)[this._valueProp];
},setValue:function(_f,_10){
var n=this.getNode(_f);
if(n){
n[this._valueProp]=_10;
}
},focus:function(_12,_13){
dojox.grid.focusSelectNode(_13||this.getNode(_12));
},save:function(_14){
this.value=this.value||this.getValue(_14);
},restore:function(_15){
this.setValue(_15,this.value);
},_finish:function(_16){
dojo.setSelectable(this.cell.grid.domNode,false);
this.cancelFormatNode(this.cell);
},apply:function(_17){
this.cell.applyEdit(this.getValue(_17),_17);
this._finish(_17);
},cancel:function(_18){
this.cell.cancelEdit(_18);
this._finish(_18);
}});
dojox.grid.editors.base=dojox.grid.editors.Base;
dojo.declare("dojox.grid.editors.Input",dojox.grid.editors.Base,{constructor:function(_19){
this.keyFilter=this.keyFilter||this.cell.keyFilter;
},keyFilter:null,format:function(_1a,_1b){
this.needFormatNode(_1a,_1b);
return "<input class=\"dojoxGrid-input\" type=\"text\" value=\""+_1a+"\">";
},formatNode:function(_1c,_1d,_1e){
this.inherited(arguments);
this.cell.registerOnBlur(_1c,_1e);
},doKey:function(e){
if(this.keyFilter){
var key=String.fromCharCode(e.charCode);
if(key.search(this.keyFilter)==-1){
dojo.stopEvent(e);
}
}
},_finish:function(_21){
this.inherited(arguments);
var n=this.getNode(_21);
try{
dojox.grid.fire(n,"blur");
}
catch(e){
}
}});
dojox.grid.editors.input=dojox.grid.editors.Input;
dojo.declare("dojox.grid.editors.Select",dojox.grid.editors.Input,{constructor:function(_23){
this.options=this.options||this.cell.options;
this.values=this.values||this.cell.values||this.options;
},format:function(_24,_25){
this.needFormatNode(_24,_25);
var h=["<select class=\"dojoxGrid-select\">"];
for(var i=0,o,v;((o=this.options[i])!==undefined)&&((v=this.values[i])!==undefined);i++){
h.push("<option",(_24==v?" selected":"")," value=\""+v+"\"",">",o,"</option>");
}
h.push("</select>");
return h.join("");
},getValue:function(_2a){
var n=this.getNode(_2a);
if(n){
var i=n.selectedIndex,o=n.options[i];
return this.cell.returnIndex?i:o.value||o.innerHTML;
}
}});
dojox.grid.editors.select=dojox.grid.editors.Select;
dojo.declare("dojox.grid.editors.AlwaysOn",dojox.grid.editors.Input,{alwaysOn:true,_formatNode:function(_2e,_2f){
this.formatNode(this.getNode(_2f),_2e,_2f);
},applyStaticValue:function(_30){
var e=this.cell.grid.edit;
e.applyCellEdit(this.getValue(_30),this.cell,_30);
e.start(this.cell,_30,true);
}});
dojox.grid.editors.alwaysOn=dojox.grid.editors.AlwaysOn;
dojo.declare("dojox.grid.editors.Bool",dojox.grid.editors.AlwaysOn,{_valueProp:"checked",format:function(_32,_33){
return "<input class=\"dojoxGrid-input\" type=\"checkbox\""+(_32?" checked=\"checked\"":"")+" style=\"width: auto\" />";
},doclick:function(e){
if(e.target.tagName=="INPUT"){
this.applyStaticValue(e.rowIndex);
}
}});
dojox.grid.editors.bool=dojox.grid.editors.Bool;
}
