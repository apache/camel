/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx.flip"]){
dojo._hasResource["dojox.fx.flip"]=true;
dojo.provide("dojox.fx.flip");
dojo.experimental("dojox.fx.flip");
dojo.require("dojo.fx");
(function(){
var _1="border",_2="Width",_3="Height",_4="Top",_5="Right",_6="Left",_7="Bottom";
dojox.fx.flip=function(_8){
var _9=dojo.create("div"),_a=_8.node=dojo.byId(_8.node),s=_a.style,_c=null,hs=null,pn=null,_f=_8.lightColor||"#dddddd",_10=_8.darkColor||"#555555",_11=dojo.style(_a,"backgroundColor"),_12=_8.endColor||_11,_13={},_14=[],_15=_8.duration?_8.duration/2:250,dir=_8.dir||"left",_17=0.9,_18="transparent",_19=_8.whichAnim,_1a=_8.axis||"center",_1b=_8.depth;
var _1c=function(_1d){
return ((new dojo.Color(_1d)).toHex()==="#000000")?"#000001":_1d;
};
if(dojo.isIE<7){
_12=_1c(_12);
_f=_1c(_f);
_10=_1c(_10);
_11=_1c(_11);
_18="black";
_9.style.filter="chroma(color='#000000')";
}
var _1e=(function(n){
return function(){
var ret=dojo.coords(n,true);
_c={top:ret.y,left:ret.x,width:ret.w,height:ret.h};
};
})(_a);
_1e();
hs={position:"absolute",top:_c["top"]+"px",left:_c["left"]+"px",height:"0",width:"0",zIndex:_8.zIndex||(s.zIndex||0),border:"0 solid "+_18,fontSize:"0",visibility:"hidden"};
var _21=[{},{top:_c["top"],left:_c["left"]}];
var _22={left:[_6,_5,_4,_7,_2,_3,"end"+_3+"Min",_6,"end"+_3+"Max"],right:[_5,_6,_4,_7,_2,_3,"end"+_3+"Min",_6,"end"+_3+"Max"],top:[_4,_7,_6,_5,_3,_2,"end"+_2+"Min",_4,"end"+_2+"Max"],bottom:[_7,_4,_6,_5,_3,_2,"end"+_2+"Min",_4,"end"+_2+"Max"]};
pn=_22[dir];
if(typeof _1b!="undefined"){
_1b=Math.max(0,Math.min(1,_1b))/2;
_17=0.4+(0.5-_1b);
}else{
_17=Math.min(0.9,Math.max(0.4,_c[pn[5].toLowerCase()]/_c[pn[4].toLowerCase()]));
}
var p0=_21[0];
for(var i=4;i<6;i++){
if(_1a=="center"||_1a=="cube"){
_c["end"+pn[i]+"Min"]=_c[pn[i].toLowerCase()]*_17;
_c["end"+pn[i]+"Max"]=_c[pn[i].toLowerCase()]/_17;
}else{
if(_1a=="shortside"){
_c["end"+pn[i]+"Min"]=_c[pn[i].toLowerCase()];
_c["end"+pn[i]+"Max"]=_c[pn[i].toLowerCase()]/_17;
}else{
if(_1a=="longside"){
_c["end"+pn[i]+"Min"]=_c[pn[i].toLowerCase()]*_17;
_c["end"+pn[i]+"Max"]=_c[pn[i].toLowerCase()];
}
}
}
}
if(_1a=="center"){
p0[pn[2].toLowerCase()]=_c[pn[2].toLowerCase()]-(_c[pn[8]]-_c[pn[6]])/4;
}else{
if(_1a=="shortside"){
p0[pn[2].toLowerCase()]=_c[pn[2].toLowerCase()]-(_c[pn[8]]-_c[pn[6]])/2;
}
}
_13[pn[5].toLowerCase()]=_c[pn[5].toLowerCase()]+"px";
_13[pn[4].toLowerCase()]="0";
_13[_1+pn[1]+_2]=_c[pn[4].toLowerCase()]+"px";
_13[_1+pn[1]+"Color"]=_11;
p0[_1+pn[1]+_2]=0;
p0[_1+pn[1]+"Color"]=_10;
p0[_1+pn[2]+_2]=p0[_1+pn[3]+_2]=_1a!="cube"?(_c["end"+pn[5]+"Max"]-_c["end"+pn[5]+"Min"])/2:_c[pn[6]]/2;
p0[pn[7].toLowerCase()]=_c[pn[7].toLowerCase()]+_c[pn[4].toLowerCase()]/2+(_8.shift||0);
p0[pn[5].toLowerCase()]=_c[pn[6]];
var p1=_21[1];
p1[_1+pn[0]+"Color"]={start:_f,end:_12};
p1[_1+pn[0]+_2]=_c[pn[4].toLowerCase()];
p1[_1+pn[2]+_2]=0;
p1[_1+pn[3]+_2]=0;
p1[pn[5].toLowerCase()]={start:_c[pn[6]],end:_c[pn[5].toLowerCase()]};
dojo.mixin(hs,_13);
dojo.style(_9,hs);
dojo.body().appendChild(_9);
var _26=function(){
dojo.destroy(_9);
s.backgroundColor=_12;
s.visibility="visible";
};
if(_19=="last"){
for(i in p0){
p0[i]={start:p0[i]};
}
p0[_1+pn[1]+"Color"]={start:_10,end:_12};
p1=p0;
}
if(!_19||_19=="first"){
_14.push(dojo.animateProperty({node:_9,duration:_15,properties:p0}));
}
if(!_19||_19=="last"){
_14.push(dojo.animateProperty({node:_9,duration:_15,properties:p1,onEnd:_26}));
}
dojo.connect(_14[0],"play",function(){
_9.style.visibility="visible";
s.visibility="hidden";
});
return dojo.fx.chain(_14);
};
dojox.fx.flipCube=function(_27){
var _28=[],mb=dojo.marginBox(_27.node),_2a=mb.w/2,_2b=mb.h/2,_2c={top:{pName:"height",args:[{whichAnim:"first",dir:"top",shift:-_2b},{whichAnim:"last",dir:"bottom",shift:_2b}]},right:{pName:"width",args:[{whichAnim:"first",dir:"right",shift:_2a},{whichAnim:"last",dir:"left",shift:-_2a}]},bottom:{pName:"height",args:[{whichAnim:"first",dir:"bottom",shift:_2b},{whichAnim:"last",dir:"top",shift:-_2b}]},left:{pName:"width",args:[{whichAnim:"first",dir:"left",shift:-_2a},{whichAnim:"last",dir:"right",shift:_2a}]}};
var d=_2c[_27.dir||"left"],p=d.args;
_27.duration=_27.duration?_27.duration*2:500;
_27.depth=0.8;
_27.axis="cube";
for(var i=p.length-1;i>=0;i--){
dojo.mixin(_27,p[i]);
_28.push(dojox.fx.flip(_27));
}
return dojo.fx.combine(_28);
};
dojox.fx.flipPage=function(_30){
var n=_30.node,_32=dojo.coords(n,true),x=_32.x,y=_32.y,w=_32.w,h=_32.h,_37=dojo.style(n,"backgroundColor"),_38=_30.lightColor||"#dddddd",_39=_30.darkColor,_3a=dojo.create("div"),_3b=[],hn=[],dir=_30.dir||"right",pn={left:["left","right","x","w"],top:["top","bottom","y","h"],right:["left","left","x","w"],bottom:["top","top","y","h"]},_3f={right:[1,-1],left:[-1,1],top:[-1,1],bottom:[1,-1]};
dojo.style(_3a,{position:"absolute",width:w+"px",height:h+"px",top:y+"px",left:x+"px",visibility:"hidden"});
var hs=[];
for(var i=0;i<2;i++){
var r=i%2,d=r?pn[dir][1]:dir,wa=r?"last":"first",_45=r?_37:_38,_46=r?_45:_30.startColor||n.style.backgroundColor;
hn[i]=dojo.clone(_3a);
var _47=function(x){
return function(){
dojo.destroy(hn[x]);
};
}(i);
dojo.body().appendChild(hn[i]);
hs[i]={backgroundColor:r?_46:_37};
hs[i][pn[dir][0]]=_32[pn[dir][2]]+_3f[dir][0]*i*_32[pn[dir][3]]+"px";
dojo.style(hn[i],hs[i]);
_3b.push(dojox.fx.flip({node:hn[i],dir:d,axis:"shortside",depth:_30.depth,duration:_30.duration/2,shift:_3f[dir][i]*_32[pn[dir][3]]/2,darkColor:_39,lightColor:_38,whichAnim:wa,endColor:_45}));
dojo.connect(_3b[i],"onEnd",_47);
}
return dojo.fx.chain(_3b);
};
dojox.fx.flipGrid=function(_49){
var _4a=_49.rows||4,_4b=_49.cols||4,_4c=[],_4d=dojo.create("div"),n=_49.node,_4f=dojo.coords(n,true),x=_4f.x,y=_4f.y,nw=_4f.w,nh=_4f.h,w=_4f.w/_4b,h=_4f.h/_4a,_56=[];
dojo.style(_4d,{position:"absolute",width:w+"px",height:h+"px",backgroundColor:dojo.style(n,"backgroundColor")});
for(var i=0;i<_4a;i++){
var r=i%2,d=r?"right":"left",_5a=r?1:-1;
var cn=dojo.clone(n);
dojo.style(cn,{position:"absolute",width:nw+"px",height:nh+"px",top:y+"px",left:x+"px",clip:"rect("+i*h+"px,"+nw+"px,"+nh+"px,0)"});
dojo.body().appendChild(cn);
_4c[i]=[];
for(var j=0;j<_4b;j++){
var hn=dojo.clone(_4d),l=r?j:_4b-(j+1);
var _5f=function(xn,_61,_62){
return function(){
if(!(_61%2)){
dojo.style(xn,{clip:"rect("+_61*h+"px,"+(nw-(_62+1)*w)+"px,"+((_61+1)*h)+"px,0px)"});
}else{
dojo.style(xn,{clip:"rect("+_61*h+"px,"+nw+"px,"+((_61+1)*h)+"px,"+((_62+1)*w)+"px)"});
}
};
}(cn,i,j);
dojo.body().appendChild(hn);
dojo.style(hn,{left:x+l*w+"px",top:y+i*h+"px",visibility:"hidden"});
var a=dojox.fx.flipPage({node:hn,dir:d,duration:_49.duration||900,shift:_5a*w/2,depth:0.2,darkColor:_49.darkColor,lightColor:_49.lightColor,startColor:_49.startColor||_49.node.style.backgroundColor}),_64=function(xn){
return function(){
dojo.destroy(xn);
};
}(hn);
dojo.connect(a,"play",this,_5f);
dojo.connect(a,"play",this,_64);
_4c[i].push(a);
}
_56.push(dojo.fx.chain(_4c[i]));
}
dojo.connect(_56[0],"play",function(){
dojo.style(n,{visibility:"hidden"});
});
return dojo.fx.combine(_56);
};
})();
}
