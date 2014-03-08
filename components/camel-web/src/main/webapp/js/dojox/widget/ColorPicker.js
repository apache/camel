/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.ColorPicker"]){
dojo._hasResource["dojox.widget.ColorPicker"]=true;
dojo.provide("dojox.widget.ColorPicker");
dojo.experimental("dojox.widget.ColorPicker");
dojo.require("dijit.form._FormWidget");
dojo.require("dojo.dnd.move");
dojo.require("dojo.fx");
dojo.require("dojox.color");
(function(d){
var _2=function(_3){
return _3;
};
dojo.declare("dojox.widget.ColorPicker",dijit.form._FormWidget,{showRgb:true,showHsv:true,showHex:true,webSafe:true,animatePoint:true,slideDuration:250,liveUpdate:false,PICKER_HUE_H:150,PICKER_SAT_VAL_H:150,PICKER_SAT_VAL_W:150,value:"#ffffff",_underlay:d.moduleUrl("dojox.widget","ColorPicker/images/underlay.png"),templateString:"<div class=\"dojoxColorPicker\" dojoAttachEvent=\"onkeypress: _handleKey\">\n\t<div class=\"dojoxColorPickerBox\">\n\t\t<div dojoAttachPoint=\"cursorNode\" tabIndex=\"0\" class=\"dojoxColorPickerPoint\"></div>\n\t\t<img dojoAttachPoint=\"colorUnderlay\" dojoAttachEvent=\"onclick: _setPoint\" class=\"dojoxColorPickerUnderlay\" src=\"${_underlay}\">\n\t</div>\n\t<div class=\"dojoxHuePicker\">\n\t\t<div dojoAttachPoint=\"hueCursorNode\" tabIndex=\"0\" class=\"dojoxHuePickerPoint\"></div>\n\t\t<div dojoAttachPoint=\"hueNode\" class=\"dojoxHuePickerUnderlay\" dojoAttachEvent=\"onclick: _setHuePoint\"></div>\n\t</div>\n\t<div dojoAttachPoint=\"previewNode\" class=\"dojoxColorPickerPreview\"></div>\n\t<div dojoAttachPoint=\"safePreviewNode\" class=\"dojoxColorPickerWebSafePreview\"></div>\n\t<div class=\"dojoxColorPickerOptional\" dojoAttachEvent=\"onchange: _colorInputChange\">\n\t\t<div class=\"dijitInline dojoxColorPickerRgb\" dojoAttachPoint=\"rgbNode\">\n\t\t\t<table>\n\t\t\t<tr><td>r</td><td><input dojoAttachPoint=\"Rval\" size=\"1\"></td></tr>\n\t\t\t<tr><td>g</td><td><input dojoAttachPoint=\"Gval\" size=\"1\"></td></tr>\n\t\t\t<tr><td>b</td><td><input dojoAttachPoint=\"Bval\" size=\"1\"></td></tr>\n\t\t\t</table>\n\t\t</div>\n\t\t<div class=\"dijitInline dojoxColorPickerHsv\" dojoAttachPoint=\"hsvNode\">\n\t\t\t<table>\n\t\t\t<tr><td>h</td><td><input dojoAttachPoint=\"Hval\"size=\"1\"> &deg;</td></tr>\n\t\t\t<tr><td>s</td><td><input dojoAttachPoint=\"Sval\" size=\"1\"> %</td></tr>\n\t\t\t<tr><td>v</td><td><input dojoAttachPoint=\"Vval\" size=\"1\"> %</td></tr>\n\t\t\t</table>\n\t\t</div>\n\t\t<div class=\"dojoxColorPickerHex\" dojoAttachPoint=\"hexNode\">\t\n\t\t\thex: <input dojoAttachPoint=\"hexCode, focusNode, valueNode\" size=\"6\" class=\"dojoxColorPickerHexCode\">\n\t\t</div>\n\t</div>\n</div>\n",buildRendering:function(){
this.inherited(arguments);
if(d.isIE<7){
this.colorUnderlay.style.filter="progid:DXImageTransform.Microsoft.AlphaImageLoader(src='"+this._underlay+"', sizingMethod='scale')";
this.colorUnderlay.src=this._blankGif.toString();
}
if(!this.showRgb){
this.rgbNode.style.display="none";
}
if(!this.showHsv){
this.hsvNode.style.display="none";
}
if(!this.showHex){
this.hexNode.style.display="none";
}
if(!this.webSafe){
this.safePreviewNode.style.visibility="hidden";
}
this._offset=0;
var _4=d.marginBox(this.cursorNode);
var _5=d.marginBox(this.hueCursorNode);
this._shift={hue:{x:Math.round(_5.w/2)-1,y:Math.round(_5.h/2)-1},picker:{x:Math.floor(_4.w/2),y:Math.floor(_4.h/2)}};
this.PICKER_HUE_H=d.coords(this.hueNode).h;
var cu=d.coords(this.colorUnderlay);
this.PICKER_SAT_VAL_H=cu.h;
this.PICKER_SAT_VAL_W=cu.w;
var ox=this._shift.picker.x;
var oy=this._shift.picker.y;
this._mover=new d.dnd.move.boxConstrainedMoveable(this.cursorNode,{box:{t:0-oy,l:0-ox,w:this.PICKER_SAT_VAL_W,h:this.PICKER_SAT_VAL_H}});
this._hueMover=new d.dnd.move.boxConstrainedMoveable(this.hueCursorNode,{box:{t:0-this._shift.hue.y,l:0,w:0,h:this.PICKER_HUE_H}});
d.subscribe("/dnd/move/stop",d.hitch(this,"_clearTimer"));
d.subscribe("/dnd/move/start",d.hitch(this,"_setTimer"));
},_setValueAttr:function(_9){
this.setColor(_9,true);
},setColor:function(_a,_b){
var _c=dojox.color.fromString(_a);
this._updatePickerLocations(_c);
this._updateColorInputs(_c);
this._updateValue(_c,_b);
},_setTimer:function(_d){
dijit.focus(_d.node);
d.setSelectable(this.domNode,false);
this._timer=setInterval(d.hitch(this,"_updateColor"),45);
},_clearTimer:function(_e){
clearInterval(this._timer);
this._timer=null;
this.onChange(this.value);
d.setSelectable(this.domNode,true);
},_setHue:function(h){
d.style(this.colorUnderlay,"backgroundColor",dojox.color.fromHsv(h,100,100).toHex());
},_updateColor:function(){
var _10=d.style(this.hueCursorNode,"top")+this._shift.hue.y,_11=d.style(this.cursorNode,"top")+this._shift.picker.y,_12=d.style(this.cursorNode,"left")+this._shift.picker.x,h=Math.round(360-(_10/this.PICKER_HUE_H*360)),col=dojox.color.fromHsv(h,_12/this.PICKER_SAT_VAL_W*100,100-(_11/this.PICKER_SAT_VAL_H*100));
this._updateColorInputs(col);
this._updateValue(col,true);
if(h!=this._hue){
this._setHue(h);
}
},_colorInputChange:function(e){
var col,_17=false;
switch(e.target){
case this.hexCode:
col=dojox.color.fromString(e.target.value);
_17=true;
break;
case this.Rval:
case this.Gval:
case this.Bval:
col=dojox.color.fromArray([this.Rval.value,this.Gval.value,this.Bval.value]);
_17=true;
break;
case this.Hval:
case this.Sval:
case this.Vval:
col=dojox.color.fromHsv(this.Hval.value,this.Sval.value,this.Vval.value);
_17=true;
break;
}
if(_17){
this._updatePickerLocations(col);
this._updateColorInputs(col);
this._updateValue(col,true);
}
},_updateValue:function(col,_19){
var hex=col.toHex();
this.value=this.valueNode.value=hex;
if(_19&&(!this._timer||this.liveUpdate)){
this.onChange(hex);
}
},_updatePickerLocations:function(col){
var hsv=col.toHsv(),_1d=Math.round(this.PICKER_HUE_H-hsv.h/360*this.PICKER_HUE_H-this._shift.hue.y),_1e=Math.round(hsv.s/100*this.PICKER_SAT_VAL_W-this._shift.picker.x),_1f=Math.round(this.PICKER_SAT_VAL_H-hsv.v/100*this.PICKER_SAT_VAL_H-this._shift.picker.y);
if(this.animatePoint){
d.fx.slideTo({node:this.hueCursorNode,duration:this.slideDuration,top:_1d,left:0}).play();
d.fx.slideTo({node:this.cursorNode,duration:this.slideDuration,top:_1f,left:_1e}).play();
}else{
d.style(this.hueCursorNode,"top",_1d+"px");
d.style(this.cursorNode,{left:_1e+"px",top:_1f+"px"});
}
if(hsv.h!=this._hue){
this._setHue(hsv.h);
}
},_updateColorInputs:function(col){
var hex=col.toHex();
if(this.showRgb){
this.Rval.value=col.r;
this.Gval.value=col.g;
this.Bval.value=col.b;
}
if(this.showHsv){
var hsv=col.toHsv();
this.Hval.value=Math.round((hsv.h));
this.Sval.value=Math.round(hsv.s);
this.Vval.value=Math.round(hsv.v);
}
if(this.showHex){
this.hexCode.value=hex;
}
this.previewNode.style.backgroundColor=hex;
if(this.webSafe){
this.safePreviewNode.style.backgroundColor=_2(hex);
}
},_setHuePoint:function(evt){
var _24=evt.layerY-this._shift.hue.y;
if(this.animatePoint){
d.fx.slideTo({node:this.hueCursorNode,duration:this.slideDuration,top:_24,left:0,onEnd:d.hitch(this,"_updateColor",true)}).play();
}else{
d.style(this.hueCursorNode,"top",_24+"px");
this._updateColor(false);
}
},_setPoint:function(evt){
var _26=evt.layerY-this._shift.picker.y,_27=evt.layerX-this._shift.picker.x;
if(evt){
dijit.focus(evt.target);
}
if(this.animatePoint){
d.fx.slideTo({node:this.cursorNode,duration:this.slideDuration,top:_26,left:_27,onEnd:d.hitch(this,"_updateColor",true)}).play();
}else{
d.style(this.cursorNode,{left:_27+"px",top:_26+"px"});
this._updateColor(false);
}
},_handleKey:function(e){
}});
})(dojo);
}
