/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.highlight._base"]){
dojo._hasResource["dojox.highlight._base"]=true;
dojo.provide("dojox.highlight._base");
(function(){
var dh=dojox.highlight,_2="\\b(0x[A-Za-z0-9]+|\\d+(\\.\\d+)?)";
dh.constants={IDENT_RE:"[a-zA-Z][a-zA-Z0-9_]*",UNDERSCORE_IDENT_RE:"[a-zA-Z_][a-zA-Z0-9_]*",NUMBER_RE:"\\b\\d+(\\.\\d+)?",C_NUMBER_RE:_2,APOS_STRING_MODE:{className:"string",begin:"'",end:"'",illegal:"\\n",contains:["escape"],relevance:0},QUOTE_STRING_MODE:{className:"string",begin:"\"",end:"\"",illegal:"\\n",contains:["escape"],relevance:0},BACKSLASH_ESCAPE:{className:"escape",begin:"\\\\.",end:"^",relevance:0},C_LINE_COMMENT_MODE:{className:"comment",begin:"//",end:"$",relevance:0},C_BLOCK_COMMENT_MODE:{className:"comment",begin:"/\\*",end:"\\*/"},HASH_COMMENT_MODE:{className:"comment",begin:"#",end:"$"},C_NUMBER_MODE:{className:"number",begin:_2,end:"^",relevance:0}};
function _3(_4){
return _4.replace(/&/gm,"&amp;").replace(/</gm,"&lt;").replace(/>/gm,"&gt;");
};
function _5(_6){
return dojo.every(_6.childNodes,function(_7){
return _7.nodeType==3||String(_7.nodeName).toLowerCase()=="br";
});
};
function _8(_9){
var _a=[];
dojo.forEach(_9.childNodes,function(_b){
if(_b.nodeType==3){
_a.push(_b.nodeValue);
}else{
if(String(_b.nodeName).toLowerCase()=="br"){
_a.push("\n");
}else{
throw "Complex markup";
}
}
});
return _a.join("");
};
function _c(_d){
if(!_d.keywordGroups){
for(var _e in _d.keywords){
var kw=_d.keywords[_e];
if(kw instanceof Object){
_d.keywordGroups=_d.keywords;
}else{
_d.keywordGroups={keyword:_d.keywords};
}
break;
}
}
};
function _10(_11){
if(_11.defaultMode&&_11.modes){
_c(_11.defaultMode);
dojo.forEach(_11.modes,_c);
}
};
var _12=function(_13,_14){
this.langName=_13;
this.lang=dh.languages[_13];
this.modes=[this.lang.defaultMode];
this.relevance=0;
this.keywordCount=0;
this.result=[];
if(!this.lang.defaultMode.illegalRe){
this.buildRes();
_10(this.lang);
}
try{
this.highlight(_14);
this.result=this.result.join("");
}
catch(e){
if(e=="Illegal"){
this.relevance=0;
this.keywordCount=0;
this.partialResult=this.result.join("");
this.result=_3(_14);
}else{
throw e;
}
}
};
dojo.extend(_12,{buildRes:function(){
dojo.forEach(this.lang.modes,function(_15){
if(_15.begin){
_15.beginRe=this.langRe("^"+_15.begin);
}
if(_15.end){
_15.endRe=this.langRe("^"+_15.end);
}
if(_15.illegal){
_15.illegalRe=this.langRe("^(?:"+_15.illegal+")");
}
},this);
this.lang.defaultMode.illegalRe=this.langRe("^(?:"+this.lang.defaultMode.illegal+")");
},subMode:function(_16){
var _17=this.modes[this.modes.length-1].contains;
if(_17){
var _18=this.lang.modes;
for(var i=0;i<_17.length;++i){
var _1a=_17[i];
for(var j=0;j<_18.length;++j){
var _1c=_18[j];
if(_1c.className==_1a&&_1c.beginRe.test(_16)){
return _1c;
}
}
}
}
return null;
},endOfMode:function(_1d){
for(var i=this.modes.length-1;i>=0;--i){
var _1f=this.modes[i];
if(_1f.end&&_1f.endRe.test(_1d)){
return this.modes.length-i;
}
if(!_1f.endsWithParent){
break;
}
}
return 0;
},isIllegal:function(_20){
var _21=this.modes[this.modes.length-1].illegalRe;
return _21&&_21.test(_20);
},langRe:function(_22,_23){
var _24="m"+(this.lang.case_insensitive?"i":"")+(_23?"g":"");
return new RegExp(_22,_24);
},buildTerminators:function(){
var _25=this.modes[this.modes.length-1],_26={};
if(_25.contains){
dojo.forEach(this.lang.modes,function(_27){
if(dojo.indexOf(_25.contains,_27.className)>=0){
_26[_27.begin]=1;
}
});
}
for(var i=this.modes.length-1;i>=0;--i){
var m=this.modes[i];
if(m.end){
_26[m.end]=1;
}
if(!m.endsWithParent){
break;
}
}
if(_25.illegal){
_26[_25.illegal]=1;
}
var t=[];
for(i in _26){
t.push(i);
}
_25.terminatorsRe=this.langRe("("+t.join("|")+")");
},eatModeChunk:function(_2b,_2c){
var _2d=this.modes[this.modes.length-1];
if(!_2d.terminatorsRe){
this.buildTerminators();
}
_2b=_2b.substr(_2c);
var _2e=_2d.terminatorsRe.exec(_2b);
if(!_2e){
return {buffer:_2b,lexeme:"",end:true};
}
return {buffer:_2e.index?_2b.substr(0,_2e.index):"",lexeme:_2e[0],end:false};
},keywordMatch:function(_2f,_30){
var _31=_30[0];
if(this.lang.case_insensitive){
_31=_31.toLowerCase();
}
for(var _32 in _2f.keywordGroups){
if(_31 in _2f.keywordGroups[_32]){
return _32;
}
}
return "";
},buildLexemes:function(_33){
var _34={};
dojo.forEach(_33.lexems,function(_35){
_34[_35]=1;
});
var t=[];
for(var i in _34){
t.push(i);
}
_33.lexemsRe=this.langRe("("+t.join("|")+")",true);
},processKeywords:function(_38){
var _39=this.modes[this.modes.length-1];
if(!_39.keywords||!_39.lexems){
return _3(_38);
}
if(!_39.lexemsRe){
this.buildLexemes(_39);
}
_39.lexemsRe.lastIndex=0;
var _3a=[],_3b=0,_3c=_39.lexemsRe.exec(_38);
while(_3c){
_3a.push(_3(_38.substr(_3b,_3c.index-_3b)));
var _3d=this.keywordMatch(_39,_3c);
if(_3d){
++this.keywordCount;
_3a.push("<span class=\""+_3d+"\">"+_3(_3c[0])+"</span>");
}else{
_3a.push(_3(_3c[0]));
}
_3b=_39.lexemsRe.lastIndex;
_3c=_39.lexemsRe.exec(_38);
}
_3a.push(_3(_38.substr(_3b,_38.length-_3b)));
return _3a.join("");
},processModeInfo:function(_3e,_3f,end){
var _41=this.modes[this.modes.length-1];
if(end){
this.result.push(this.processKeywords(_41.buffer+_3e));
return;
}
if(this.isIllegal(_3f)){
throw "Illegal";
}
var _42=this.subMode(_3f);
if(_42){
_41.buffer+=_3e;
this.result.push(this.processKeywords(_41.buffer));
if(_42.excludeBegin){
this.result.push(_3f+"<span class=\""+_42.className+"\">");
_42.buffer="";
}else{
this.result.push("<span class=\""+_42.className+"\">");
_42.buffer=_3f;
}
this.modes.push(_42);
this.relevance+=typeof _42.relevance=="number"?_42.relevance:1;
return;
}
var _43=this.endOfMode(_3f);
if(_43){
_41.buffer+=_3e;
if(_41.excludeEnd){
this.result.push(this.processKeywords(_41.buffer)+"</span>"+_3f);
}else{
this.result.push(this.processKeywords(_41.buffer+_3f)+"</span>");
}
while(_43>1){
this.result.push("</span>");
--_43;
this.modes.pop();
}
this.modes.pop();
this.modes[this.modes.length-1].buffer="";
return;
}
},highlight:function(_44){
var _45=0;
this.lang.defaultMode.buffer="";
do{
var _46=this.eatModeChunk(_44,_45);
this.processModeInfo(_46.buffer,_46.lexeme,_46.end);
_45+=_46.buffer.length+_46.lexeme.length;
}while(!_46.end);
if(this.modes.length>1){
throw "Illegal";
}
}});
function _47(_48,_49,_4a){
if(String(_48.tagName).toLowerCase()=="code"&&String(_48.parentNode.tagName).toLowerCase()=="pre"){
var _4b=document.createElement("div"),_4c=_48.parentNode.parentNode;
_4b.innerHTML="<pre><code class=\""+_49+"\">"+_4a+"</code></pre>";
_4c.replaceChild(_4b.firstChild,_48.parentNode);
}else{
_48.className=_49;
_48.innerHTML=_4a;
}
};
function _4d(_4e,str){
var _50=new _12(_4e,str);
return {result:_50.result,langName:_4e,partialResult:_50.partialResult};
};
function _51(_52,_53){
var _54=_4d(_53,_8(_52));
_47(_52,_52.className,_54.result);
};
function _55(str){
var _57="",_58="",_59=2,_5a=str;
for(var key in dh.languages){
if(!dh.languages[key].defaultMode){
continue;
}
var _5c=new _12(key,_5a),_5d=_5c.keywordCount+_5c.relevance,_5e=0;
if(!_57||_5d>_5e){
_5e=_5d;
_57=_5c.result;
_58=_5c.langName;
}
}
return {result:_57,langName:_58};
};
function _5f(_60){
var _61=_55(_8(_60));
if(_61.result){
_47(_60,_61.langName,_61.result);
}
};
dojox.highlight.processString=function(str,_63){
return _63?_4d(_63,str):_55(str);
};
dojox.highlight.init=function(_64){
_64=dojo.byId(_64);
if(dojo.hasClass(_64,"no-highlight")){
return;
}
if(!_5(_64)){
return;
}
var _65=_64.className.split(/\s+/),_66=dojo.some(_65,function(_67){
if(_67.charAt(0)!="_"&&dh.languages[_67]){
_51(_64,_67);
return true;
}
return false;
});
if(!_66){
_5f(_64);
}
};
dh.Code=function(p,n){
dh.init(n);
};
})();
}
