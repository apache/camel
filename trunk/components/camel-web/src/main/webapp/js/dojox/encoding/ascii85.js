/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.encoding.ascii85"]){
dojo._hasResource["dojox.encoding.ascii85"]=true;
dojo.provide("dojox.encoding.ascii85");
(function(){
var c=function(_2,_3,_4){
var i,j,n,b=[0,0,0,0,0];
for(i=0;i<_3;i+=4){
n=((_2[i]*256+_2[i+1])*256+_2[i+2])*256+_2[i+3];
if(!n){
_4.push("z");
}else{
for(j=0;j<5;b[j++]=n%85+33,n=Math.floor(n/85)){
}
}
_4.push(String.fromCharCode(b[4],b[3],b[2],b[1],b[0]));
}
};
dojox.encoding.ascii85.encode=function(_9){
var _a=[],_b=_9.length%4,_c=_9.length-_b;
c(_9,_c,_a);
if(_b){
var t=_9.slice(_c);
while(t.length<4){
t.push(0);
}
c(t,4,_a);
var x=_a.pop();
if(x=="z"){
x="!!!!!";
}
_a.push(x.substr(0,_b+1));
}
return _a.join("");
};
dojox.encoding.ascii85.decode=function(_f){
var n=_f.length,r=[],b=[0,0,0,0,0],i,j,t,x,y,d;
for(i=0;i<n;++i){
if(_f.charAt(i)=="z"){
r.push(0,0,0,0);
continue;
}
for(j=0;j<5;++j){
b[j]=_f.charCodeAt(i+j)-33;
}
d=n-i;
if(d<5){
for(j=d;j<4;b[++j]=0){
}
b[d]=85;
}
t=(((b[0]*85+b[1])*85+b[2])*85+b[3])*85+b[4];
x=t&255;
t>>>=8;
y=t&255;
t>>>=8;
r.push(t>>>8,t&255,y,x);
for(j=d;j<5;++j,r.pop()){
}
i+=4;
}
return r;
};
})();
}
