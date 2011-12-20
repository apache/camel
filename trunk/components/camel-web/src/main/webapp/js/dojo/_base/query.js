/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojo._base.query"]){
dojo._hasResource["dojo._base.query"]=true;
if(typeof dojo!="undefined"){
dojo.provide("dojo._base.query");
dojo.require("dojo._base.NodeList");
dojo.require("dojo._base.lang");
}
(function(d){
var _2=d.trim;
var _3=d.forEach;
d._queryListCtor=d.NodeList;
var _4=d.isString;
var _5=function(){
return d.doc;
};
var _6=!!_5().firstChild["children"]?"children":"childNodes";
var _7=">~+";
var _8=false;
var _9=function(){
return true;
};
var _a=function(_b){
if(_7.indexOf(_b.slice(-1))>=0){
_b+=" * ";
}else{
_b+=" ";
}
var ts=function(s,e){
return _2(_b.slice(s,e));
};
var _f=[];
var _10=-1,_11=-1,_12=-1,_13=-1,_14=-1,_15=-1,_16=-1,lc="",cc="",_19;
var x=0,ql=_b.length,_1c=null,_cp=null;
var _1e=function(){
if(_16>=0){
var tv=(_16==x)?null:ts(_16,x);
_1c[(_7.indexOf(tv)<0)?"tag":"oper"]=tv;
_16=-1;
}
};
var _20=function(){
if(_15>=0){
_1c.id=ts(_15,x).replace(/\\/g,"");
_15=-1;
}
};
var _21=function(){
if(_14>=0){
_1c.classes.push(ts(_14+1,x).replace(/\\/g,""));
_14=-1;
}
};
var _22=function(){
_20();
_1e();
_21();
};
var _23=function(){
_22();
if(_13>=0){
_1c.pseudos.push({name:ts(_13+1,x)});
}
_1c.loops=(_1c.pseudos.length||_1c.attrs.length||_1c.classes.length);
_1c.oquery=_1c.query=ts(_19,x);
_1c.otag=_1c.tag=(_1c["oper"])?null:(_1c.tag||"*");
if(_1c.tag){
_1c.tag=_1c.tag.toUpperCase();
}
if(_f.length&&(_f[_f.length-1].oper)){
_1c.infixOper=_f.pop();
_1c.query=_1c.infixOper.query+" "+_1c.query;
}
_f.push(_1c);
_1c=null;
};
for(;lc=cc,cc=_b.charAt(x),x<ql;x++){
if(lc=="\\"){
continue;
}
if(!_1c){
_19=x;
_1c={query:null,pseudos:[],attrs:[],classes:[],tag:null,oper:null,id:null,getTag:function(){
return (_8)?this.otag:this.tag;
}};
_16=x;
}
if(_10>=0){
if(cc=="]"){
if(!_cp.attr){
_cp.attr=ts(_10+1,x);
}else{
_cp.matchFor=ts((_12||_10+1),x);
}
var cmf=_cp.matchFor;
if(cmf){
if((cmf.charAt(0)=="\"")||(cmf.charAt(0)=="'")){
_cp.matchFor=cmf.slice(1,-1);
}
}
_1c.attrs.push(_cp);
_cp=null;
_10=_12=-1;
}else{
if(cc=="="){
var _25=("|~^$*".indexOf(lc)>=0)?lc:"";
_cp.type=_25+cc;
_cp.attr=ts(_10+1,x-_25.length);
_12=x+1;
}
}
}else{
if(_11>=0){
if(cc==")"){
if(_13>=0){
_cp.value=ts(_11+1,x);
}
_13=_11=-1;
}
}else{
if(cc=="#"){
_22();
_15=x+1;
}else{
if(cc=="."){
_22();
_14=x;
}else{
if(cc==":"){
_22();
_13=x;
}else{
if(cc=="["){
_22();
_10=x;
_cp={};
}else{
if(cc=="("){
if(_13>=0){
_cp={name:ts(_13+1,x),value:null};
_1c.pseudos.push(_cp);
}
_11=x;
}else{
if((cc==" ")&&(lc!=cc)){
_23();
}
}
}
}
}
}
}
}
}
return _f;
};
var _26=function(_27,_28){
if(!_27){
return _28;
}
if(!_28){
return _27;
}
return function(){
return _27.apply(window,arguments)&&_28.apply(window,arguments);
};
};
var _29=function(i,arr){
var r=arr||[];
if(i){
r.push(i);
}
return r;
};
var _2d=function(n){
return (1==n.nodeType);
};
var _2f="";
var _30=function(_31,_32){
if(!_31){
return _2f;
}
if(_32=="class"){
return _31.className||_2f;
}
if(_32=="for"){
return _31.htmlFor||_2f;
}
if(_32=="style"){
return _31.style.cssText||_2f;
}
return (_8?_31.getAttribute(_32):_31.getAttribute(_32,2))||_2f;
};
var _33={"*=":function(_34,_35){
return function(_36){
return (_30(_36,_34).indexOf(_35)>=0);
};
},"^=":function(_37,_38){
return function(_39){
return (_30(_39,_37).indexOf(_38)==0);
};
},"$=":function(_3a,_3b){
var _3c=" "+_3b;
return function(_3d){
var ea=" "+_30(_3d,_3a);
return (ea.lastIndexOf(_3b)==(ea.length-_3b.length));
};
},"~=":function(_3f,_40){
var _41=" "+_40+" ";
return function(_42){
var ea=" "+_30(_42,_3f)+" ";
return (ea.indexOf(_41)>=0);
};
},"|=":function(_44,_45){
var _46=" "+_45+"-";
return function(_47){
var ea=" "+_30(_47,_44);
return ((ea==_45)||(ea.indexOf(_46)==0));
};
},"=":function(_49,_4a){
return function(_4b){
return (_30(_4b,_49)==_4a);
};
}};
var _4c=(typeof _5().firstChild.nextElementSibling=="undefined");
var _ns=!_4c?"nextElementSibling":"nextSibling";
var _ps=!_4c?"previousElementSibling":"previousSibling";
var _4f=(_4c?_2d:_9);
var _50=function(_51){
while(_51=_51[_ps]){
if(_4f(_51)){
return false;
}
}
return true;
};
var _52=function(_53){
while(_53=_53[_ns]){
if(_4f(_53)){
return false;
}
}
return true;
};
var _54=function(_55){
var _56=_55.parentNode;
var te,x=0,i=0,_5a=_56[_6],ret=-1,ci=parseInt(_55["_cidx"]||-1),cl=parseInt(_56["_clen"]||-1);
if(!_5a){
return -1;
}
if(ci>=0&&cl>=0&&cl==_5a.length){
return ci;
}
_56["_clen"]=_5a.length;
while(te=_5a[x++]){
if(_4f(te)){
i++;
if(_55===te){
ret=i;
}
te["_cix"]=i;
}
}
return ret;
};
var _5e={"checked":function(_5f,_60){
return function(_61){
return !!d.attr(_61,"checked");
};
},"first-child":function(){
return _50;
},"last-child":function(){
return _52;
},"only-child":function(_62,_63){
return function(_64){
if(!_50(_64)){
return false;
}
if(!_52(_64)){
return false;
}
return true;
};
},"empty":function(_65,_66){
return function(_67){
var cn=_67.childNodes;
var cnl=_67.childNodes.length;
for(var x=cnl-1;x>=0;x--){
var nt=cn[x].nodeType;
if((nt===1)||(nt==3)){
return false;
}
}
return true;
};
},"contains":function(_6c,_6d){
var cz=_6d.charAt(0);
if(cz=="\""||cz=="'"){
_6d=_6d.slice(1,-1);
}
return function(_6f){
return (_6f.innerHTML.indexOf(_6d)>=0);
};
},"not":function(_70,_71){
var ntf=_73(_a(_71)[0]);
return function(_74){
return (!ntf(_74));
};
},"nth-child":function(_75,_76){
var pi=parseInt;
if(_76=="odd"){
_76="2n+1";
}else{
if(_76=="even"){
_76="2n";
}
}
if(_76.indexOf("n")!=-1){
var _78=_76.split("n",2);
var _79=_78[0]?((_78[0]=="-")?-1:pi(_78[0])):1;
var idx=_78[1]?pi(_78[1]):0;
var lb=0,ub=-1;
if(_79>0){
if(idx<0){
idx=(idx%_79)&&(_79+(idx%_79));
}else{
if(idx>0){
if(idx>=_79){
lb=idx-idx%_79;
}
idx=idx%_79;
}
}
}else{
if(_79<0){
_79*=-1;
if(idx>0){
ub=idx;
idx=idx%_79;
}
}
}
if(_79>0){
return function(_7d){
var i=_54(_7d);
return (i>=lb)&&(ub<0||i<=ub)&&((i%_79)==idx);
};
}else{
_76=idx;
}
}
var _7f=pi(_76);
return function(_80){
return (_54(_80)==_7f);
};
}};
var _81=(d.isIE)?function(_82){
var clc=_82.toLowerCase();
if(clc=="class"){
_82="className";
}
return function(_84){
return (_8?_84.getAttribute(_82):_84[_82]||_84[clc]);
};
}:function(_85){
return function(_86){
return (_86&&_86.getAttribute&&_86.hasAttribute(_85));
};
};
var _73=function(_87,_88){
if(!_87){
return _9;
}
_88=_88||{};
var ff=null;
if(!("el" in _88)){
ff=_26(ff,_2d);
}
if(!("tag" in _88)){
if(_87.tag!="*"){
ff=_26(ff,function(_8a){
return (_8a&&(_8a.tagName==_87.getTag()));
});
}
}
if(!("classes" in _88)){
_3(_87.classes,function(_8b,idx,arr){
var re=new RegExp("(?:^|\\s)"+_8b+"(?:\\s|$)");
ff=_26(ff,function(_8f){
return re.test(_8f.className);
});
ff.count=idx;
});
}
if(!("pseudos" in _88)){
_3(_87.pseudos,function(_90){
var pn=_90.name;
if(_5e[pn]){
ff=_26(ff,_5e[pn](pn,_90.value));
}
});
}
if(!("attrs" in _88)){
_3(_87.attrs,function(_92){
var _93;
var a=_92.attr;
if(_92.type&&_33[_92.type]){
_93=_33[_92.type](a,_92.matchFor);
}else{
if(a.length){
_93=_81(a);
}
}
if(_93){
ff=_26(ff,_93);
}
});
}
if(!("id" in _88)){
if(_87.id){
ff=_26(ff,function(_95){
return (!!_95&&(_95.id==_87.id));
});
}
}
if(!ff){
if(!("default" in _88)){
ff=_9;
}
}
return ff;
};
var _96=function(_97){
return function(_98,ret,bag){
while(_98=_98[_ns]){
if(_4c&&(!_2d(_98))){
continue;
}
if((!bag||_9b(_98,bag))&&_97(_98)){
ret.push(_98);
}
break;
}
return ret;
};
};
var _9c=function(_9d){
return function(_9e,ret,bag){
var te=_9e[_ns];
while(te){
if(_4f(te)){
if(bag&&!_9b(te,bag)){
break;
}
if(_9d(te)){
ret.push(te);
}
}
te=te[_ns];
}
return ret;
};
};
var _a2=function(_a3){
_a3=_a3||_9;
return function(_a4,ret,bag){
var te,x=0,_a9=_a4[_6];
while(te=_a9[x++]){
if(_4f(te)&&(!bag||_9b(te,bag))&&(_a3(te,x))){
ret.push(te);
}
}
return ret;
};
};
var _aa=function(_ab,_ac){
var pn=_ab.parentNode;
while(pn){
if(pn==_ac){
break;
}
pn=pn.parentNode;
}
return !!pn;
};
var _ae={};
var _af=function(_b0){
var _b1=_ae[_b0.query];
if(_b1){
return _b1;
}
var io=_b0.infixOper;
var _b3=(io?io.oper:"");
var _b4=_73(_b0,{el:1});
var qt=_b0.tag;
var _b6=("*"==qt);
if(!_b3){
if(_b0.id){
filerFunc=(!_b0.loops&&!qt)?_9:_73(_b0,{el:1,id:1});
_b1=function(_b7,arr){
var te=d.byId(_b0.id,(_b7.ownerDocument||_b7));
if(!_b4(te)){
return;
}
if(9==_b7.nodeType){
return _29(te,arr);
}else{
if(_aa(te,_b7)){
return _29(te,arr);
}
}
};
}else{
if(_5()["getElementsByClassName"]&&_b0.classes.length){
_b4=_73(_b0,{el:1,classes:1,id:1});
var _ba=_b0.classes.join(" ");
_b1=function(_bb,arr){
var ret=_29(0,arr),te,x=0;
var _c0=_bb.getElementsByClassName(_ba);
while((te=_c0[x++])){
if(_b4(te,_bb)){
ret.push(te);
}
}
return ret;
};
}else{
_b4=_73(_b0,{el:1,tag:1,id:1});
_b1=function(_c1,arr){
var ret=_29(0,arr),te,x=0;
var _c6=_c1.getElementsByTagName(_b0.getTag());
while((te=_c6[x++])){
if(_b4(te,_c1)){
ret.push(te);
}
}
return ret;
};
}
}
}else{
var _c7={el:1};
if(_b6){
_c7.tag=1;
}
_b4=_73(_b0,_c7);
if("+"==_b3){
_b1=_96(_b4);
}else{
if("~"==_b3){
_b1=_9c(_b4);
}else{
if(">"==_b3){
_b1=_a2(_b4);
}
}
}
}
return _ae[_b0.query]=_b1;
};
var _c8=function(_c9,_ca){
var _cb=_29(_c9),qp,x,te,qpl=_ca.length,bag,ret;
for(var i=0;i<qpl;i++){
ret=[];
qp=_ca[i];
x=_cb.length-1;
if(x>0){
bag={};
ret.nozip=true;
}
var gef=_af(qp);
while(te=_cb[x--]){
gef(te,ret,bag);
}
if(!ret.length){
break;
}
_cb=ret;
}
return ret;
};
var _d4={},_d5={};
var _d6=function(_d7){
var _d8=_a(_2(_d7));
if(_d8.length==1){
var tef=_af(_d8[0]);
return function(_da){
var r=tef(_da,new d._queryListCtor());
if(r){
r.nozip=true;
}
return r;
};
}
return function(_dc){
return _c8(_dc,_d8);
};
};
var nua=navigator.userAgent;
var wk="WebKit/";
var _df=(d.isWebKit&&(nua.indexOf(wk)>0)&&(parseFloat(nua.split(wk)[1])>528));
var _e0=d.isIE?"commentStrip":"nozip";
var qsa="querySelectorAll";
var _e2=(!!_5()[qsa]&&(!d.isSafari||(d.isSafari>3.1)||_df));
var _e3=function(_e4,_e5){
if(_e2){
var _e6=_d5[_e4];
if(_e6&&!_e5){
return _e6;
}
}
var _e7=_d4[_e4];
if(_e7){
return _e7;
}
var qcz=_e4.charAt(0);
var _e9=(-1==_e4.indexOf(" "));
if((qcz=="#")&&(_e9)&&(!/[.:\[\(]/.test(_e4))){
_e5=true;
}
var _ea=(_e2&&(!_e5)&&(_7.indexOf(qcz)==-1)&&(!d.isIE||(_e4.indexOf(":")==-1))&&(_e4.indexOf(":contains")==-1)&&(_e4.indexOf("|=")==-1)&&true);
if(_ea){
var tq=(_7.indexOf(_e4.charAt(_e4.length-1))>=0)?(_e4+" *"):_e4;
return _d5[_e4]=function(_ec){
try{
if(!((9==_ec.nodeType)||_e9)){
throw "";
}
var r=_ec[qsa](tq);
r[_e0]=true;
return r;
}
catch(e){
return _e3(_e4,true)(_ec);
}
};
}else{
var _ee=_e4.split(/\s*,\s*/);
return _d4[_e4]=((_ee.length<2)?_d6(_e4):function(_ef){
var _f0=0;
var ret=[];
var tp;
while((tp=_ee[_f0++])){
ret=ret.concat(_d6(tp)(_ef));
}
return ret;
});
}
};
var _f3=0;
var _f4=d.isIE?function(_f5){
if(_8){
return (_f5.getAttribute("_uid")||_f5.setAttribute("_uid",++_f3)||_f3);
}else{
return _f5.uniqueID;
}
}:function(_f6){
return (_f6._uid||(_f6._uid=++_f3));
};
var _9b=function(_f7,bag){
if(!bag){
return 1;
}
var id=_f4(_f7);
if(!bag[id]){
return bag[id]=1;
}
return 0;
};
var _fa="_zipIdx";
var _fb=function(arr){
if(arr&&arr.nozip){
return (d._queryListCtor._wrap)?d._queryListCtor._wrap(arr):arr;
}
var ret=new d._queryListCtor();
if(!arr||!arr.length){
return ret;
}
if(arr[0]){
ret.push(arr[0]);
}
if(arr.length<2){
return ret;
}
_f3++;
if(d.isIE&&_8){
var _fe=_f3+"";
arr[0].setAttribute(_fa,_fe);
for(var x=1,te;te=arr[x];x++){
if(arr[x].getAttribute(_fa)!=_fe){
ret.push(te);
}
te.setAttribute(_fa,_fe);
}
}else{
if(d.isIE&&arr.commentStrip){
try{
for(var x=1,te;te=arr[x];x++){
if(_2d(te)){
ret.push(te);
}
}
}
catch(e){
}
}else{
if(arr[0]){
arr[0][_fa]=_f3;
}
for(var x=1,te;te=arr[x];x++){
if(arr[x][_fa]!=_f3){
ret.push(te);
}
te[_fa]=_f3;
}
}
}
return ret;
};
d.query=function(_101,root){
if(!_101){
return new d._queryListCtor();
}
if(_101.constructor==d._queryListCtor){
return _101;
}
if(!_4(_101)){
return new d._queryListCtor(_101);
}
if(_4(root)){
root=d.byId(root);
if(!root){
return new d._queryListCtor();
}
}
root=root||_5();
var od=root.ownerDocument||root.documentElement;
_8=(root.contentType&&root.contentType=="application/xml")||(d.isOpera&&root.doctype)||(!!od)&&(d.isIE?od.xml:(root.xmlVersion||od.xmlVersion));
var r=_e3(_101)(root);
if(r&&r.nozip&&!d._queryListCtor._wrap){
return r;
}
return _fb(r);
};
d.query.pseudos=_5e;
d._filterQueryResult=function(_105,_106){
var _107=new d._queryListCtor();
var _108=_73(_a(_106)[0]);
for(var x=0,te;te=_105[x];x++){
if(_108(te)){
_107.push(te);
}
}
return _107;
};
})(this["queryPortability"]||this["acme"]||dojo);
}
