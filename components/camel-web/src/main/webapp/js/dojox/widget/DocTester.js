/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.DocTester"]){
dojo._hasResource["dojox.widget.DocTester"]=true;
dojo.provide("dojox.widget.DocTester");
dojo.require("dojo.string");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojox.form.BusyButton");
dojo.require("dojox.testing.DocTest");
dojo.declare("dojox.widget.DocTester",[dijit._Widget,dijit._Templated],{templateString:"<div dojoAttachPoint=\"domNode\" class=\"dojoxDocTester\">\n\t<div dojoAttachPoint=\"containerNode\"></div>\n\t<button dojoType=\"dojox.form.BusyButton\" busyLabel=\"Testing...\" dojoAttachPoint=\"runButtonNode\">Run tests</button>\n\t<button dojoType=\"dijit.form.Button\" dojoAttachPoint=\"resetButtonNode\" style=\"display:none;\">Reset</button>\n\t<span>\n\t\t<span dojoAttachPoint=\"numTestsNode\">0</span> tests,\n\t\t<span dojoAttachPoint=\"numTestsOkNode\">0</span> passed,\n\t\t<span dojoAttachPoint=\"numTestsNokNode\">0</span> failed\n\t</span>\n</div>\n",widgetsInTemplate:true,_fillContent:function(_1){
var _2=_1.innerHTML;
this.doctests=new dojox.testing.DocTest();
this.tests=this.doctests.getTestsFromString(this._unescapeHtml(_2));
var _3=dojo.map(this.tests,"return item.line-1");
var _4=_2.split("\n");
var _5="<div class=\"actualResult\">FAILED, actual result was: <span class=\"result\"></span></div>";
var _6="<pre class=\"testCase testNum0 odd\">";
for(var i=0;i<_4.length;i++){
var _8=dojo.indexOf(_3,i);
if(_8>0&&_8!=-1){
var _9=_8%2?"even":"odd";
_6+=_5;
_6+="</pre><pre class=\"testCase testNum"+_8+" "+_9+"\">";
}
_6+=_4[i].replace(/^\s+/,"")+"\n";
}
_6+=_5+"</pre>";
this.containerNode.innerHTML=_6;
},postCreate:function(){
this.inherited("postCreate",arguments);
dojo.connect(this.runButtonNode,"onClick",dojo.hitch(this,"runTests"));
dojo.connect(this.resetButtonNode,"onClick",dojo.hitch(this,"reset"));
this.numTestsNode.innerHTML=this.tests.length;
},runTests:function(){
var _a={ok:0,nok:0};
for(var i=0;i<this.tests.length;i++){
var _c=this.doctests.runTest(this.tests[i].commands,this.tests[i].expectedResult);
dojo.query(".testNum"+i,this.domNode).addClass(_c.success?"resultOk":"resultNok");
if(!_c.success){
_a.nok++;
this.numTestsNokNode.innerHTML=_a.nok;
var _d=dojo.query(".testNum"+i+" .actualResult",this.domNode)[0];
dojo.style(_d,"display","inline");
dojo.query(".result",_d)[0].innerHTML=dojo.toJson(_c.actualResult);
}else{
_a.ok++;
this.numTestsOkNode.innerHTML=_a.ok;
}
}
this.runButtonNode.cancel();
dojo.style(this.runButtonNode.domNode,"display","none");
dojo.style(this.resetButtonNode.domNode,"display","");
},reset:function(){
dojo.style(this.runButtonNode.domNode,"display","");
dojo.style(this.resetButtonNode.domNode,"display","none");
this.numTestsOkNode.innerHTML="0";
this.numTestsNokNode.innerHTML="0";
dojo.query(".actualResult",this.domNode).style("display","none");
dojo.query(".testCase",this.domNode).removeClass("resultOk").removeClass("resultNok");
},_unescapeHtml:function(_e){
_e=String(_e).replace(/&amp;/gm,"&").replace(/&lt;/gm,"<").replace(/&gt;/gm,">").replace(/&quot;/gm,"\"");
return _e;
}});
}
