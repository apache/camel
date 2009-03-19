/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.ColorPalette"]){
dojo._hasResource["dijit.ColorPalette"]=true;
dojo.provide("dijit.ColorPalette");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojo.colors");
dojo.require("dojo.i18n");
dojo.requireLocalization("dojo","colors",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,ko,nb,nl,pl,pt,pt-pt,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.ColorPalette",[dijit._Widget,dijit._Templated],{defaultTimeout:500,timeoutChangeRate:0.9,palette:"7x10",value:null,_currentFocus:0,_xDim:null,_yDim:null,_palettes:{"7x10":[["white","seashell","cornsilk","lemonchiffon","lightyellow","palegreen","paleturquoise","lightcyan","lavender","plum"],["lightgray","pink","bisque","moccasin","khaki","lightgreen","lightseagreen","lightskyblue","cornflowerblue","violet"],["silver","lightcoral","sandybrown","orange","palegoldenrod","chartreuse","mediumturquoise","skyblue","mediumslateblue","orchid"],["gray","red","orangered","darkorange","yellow","limegreen","darkseagreen","royalblue","slateblue","mediumorchid"],["dimgray","crimson","chocolate","coral","gold","forestgreen","seagreen","blue","blueviolet","darkorchid"],["darkslategray","firebrick","saddlebrown","sienna","olive","green","darkcyan","mediumblue","darkslateblue","darkmagenta"],["black","darkred","maroon","brown","darkolivegreen","darkgreen","midnightblue","navy","indigo","purple"]],"3x4":[["white","lime","green","blue"],["silver","yellow","fuchsia","navy"],["gray","red","purple","black"]]},_imagePaths:{"7x10":dojo.moduleUrl("dijit.themes","a11y/colors7x10.png"),"3x4":dojo.moduleUrl("dijit.themes","a11y/colors3x4.png")},_paletteCoords:{"leftOffset":3,"topOffset":3,"cWidth":20,"cHeight":20},templateString:"<div class=\"dijitInline dijitColorPalette\">\n\t<div class=\"dijitColorPaletteInner\" dojoAttachPoint=\"divNode\" waiRole=\"grid\" tabIndex=\"${tabIndex}\">\n\t\t<img class=\"dijitColorPaletteUnder\" dojoAttachPoint=\"imageNode\" waiRole=\"presentation\">\n\t</div>\t\n</div>\n",_paletteDims:{"7x10":{"width":"206px","height":"145px"},"3x4":{"width":"86px","height":"64px"}},tabIndex:"0",postCreate:function(){
dojo.mixin(this.divNode.style,this._paletteDims[this.palette]);
this.imageNode.setAttribute("src",this._imagePaths[this.palette]);
var _1=this._palettes[this.palette];
this.domNode.style.position="relative";
this._cellNodes=[];
this.colorNames=dojo.i18n.getLocalization("dojo","colors",this.lang);
var _2=this._blankGif,_3=new dojo.Color(),_4=this._paletteCoords;
for(var _5=0;_5<_1.length;_5++){
for(var _6=0;_6<_1[_5].length;_6++){
var _7=_1[_5][_6],_8=_3.setColor(dojo.Color.named[_7]);
var _9=dojo.create("span",{"class":"dijitPaletteCell","tabindex":"-1",title:this.colorNames[_7],style:{top:_4.topOffset+(_5*_4.cHeight)+"px",left:_4.leftOffset+(_6*_4.cWidth)+"px"}});
var _a=dojo.create("img",{src:_2,"class":"dijitPaletteImg",alt:this.colorNames[_7]},_9);
_a.color=_8.toHex();
var _b=_a.style;
_b.color=_b.backgroundColor=_a.color;
dojo.forEach(["Dijitclick","MouseEnter","Focus","Blur"],function(_c){
this.connect(_9,"on"+_c.toLowerCase(),"_onCell"+_c);
},this);
dojo.place(_9,this.divNode);
dijit.setWaiRole(_9,"gridcell");
_9.index=this._cellNodes.length;
this._cellNodes.push(_9);
}
}
this._xDim=_1[0].length;
this._yDim=_1.length;
this.connect(this.divNode,"onfocus","_onDivNodeFocus");
var _d={UP_ARROW:-this._xDim,DOWN_ARROW:this._xDim,RIGHT_ARROW:1,LEFT_ARROW:-1};
for(var _e in _d){
this._connects.push(dijit.typematic.addKeyListener(this.domNode,{charOrCode:dojo.keys[_e],ctrlKey:false,altKey:false,shiftKey:false},this,function(){
var _f=_d[_e];
return function(_10){
this._navigateByKey(_f,_10);
};
}(),this.timeoutChangeRate,this.defaultTimeout));
}
},focus:function(){
this._focusFirst();
},onChange:function(_11){
},_focusFirst:function(){
this._currentFocus=0;
var _12=this._cellNodes[this._currentFocus];
window.setTimeout(function(){
dijit.focus(_12);
},0);
},_onDivNodeFocus:function(evt){
if(evt.target===this.divNode){
this._focusFirst();
}
},_onFocus:function(){
dojo.attr(this.divNode,"tabindex","-1");
},_onBlur:function(){
this._removeCellHighlight(this._currentFocus);
dojo.attr(this.divNode,"tabindex",this.tabIndex);
},_onCellDijitclick:function(evt){
var _15=evt.currentTarget;
if(this._currentFocus!=_15.index){
this._currentFocus=_15.index;
window.setTimeout(function(){
dijit.focus(_15);
},0);
}
this._selectColor(_15);
dojo.stopEvent(evt);
},_onCellMouseEnter:function(evt){
var _17=evt.currentTarget;
this._setCurrent(_17);
window.setTimeout(function(){
dijit.focus(_17);
},0);
},_onCellFocus:function(evt){
this._setCurrent(evt.currentTarget);
},_setCurrent:function(_19){
this._removeCellHighlight(this._currentFocus);
this._currentFocus=_19.index;
dojo.addClass(_19,"dijitPaletteCellHighlight");
},_onCellBlur:function(evt){
this._removeCellHighlight(this._currentFocus);
},_removeCellHighlight:function(_1b){
dojo.removeClass(this._cellNodes[_1b],"dijitPaletteCellHighlight");
},_selectColor:function(_1c){
var img=_1c.getElementsByTagName("img")[0];
this.onChange(this.value=img.color);
},_navigateByKey:function(_1e,_1f){
if(_1f==-1){
return;
}
var _20=this._currentFocus+_1e;
if(_20<this._cellNodes.length&&_20>-1){
var _21=this._cellNodes[_20];
_21.focus();
}
}});
}
