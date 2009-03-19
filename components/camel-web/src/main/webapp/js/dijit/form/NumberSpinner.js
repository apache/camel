/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form.NumberSpinner"]){
dojo._hasResource["dijit.form.NumberSpinner"]=true;
dojo.provide("dijit.form.NumberSpinner");
dojo.require("dijit.form._Spinner");
dojo.require("dijit.form.NumberTextBox");
dojo.declare("dijit.form.NumberSpinner",[dijit.form._Spinner,dijit.form.NumberTextBoxMixin],{required:true,adjust:function(_1,_2){
var tc=this.constraints,v=isNaN(_1),_5=!isNaN(tc.max),_6=!isNaN(tc.min);
if(v&&_2!=0){
_1=(_2>0)?_6?tc.min:_5?tc.max:0:_5?this.constraints.max:_6?tc.min:0;
}
var _7=_1+_2;
if(v||isNaN(_7)){
return _1;
}
if(_5&&(_7>tc.max)){
_7=tc.max;
}
if(_6&&(_7<tc.min)){
_7=tc.min;
}
return _7;
},_onKeyPress:function(e){
if((e.charOrCode==dojo.keys.HOME||e.charOrCode==dojo.keys.END)&&!e.ctrlKey&&!e.altKey){
var _9=this.constraints[(e.charOrCode==dojo.keys.HOME?"min":"max")];
if(_9){
this._setValueAttr(_9,true);
}
dojo.stopEvent(e);
}
}});
}
