/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.encoding.compression.splay"]){
dojo._hasResource["dojox.encoding.compression.splay"]=true;
dojo.provide("dojox.encoding.compression.splay");
dojo.require("dojox.encoding.bits");
dojox.encoding.compression.Splay=function(n){
this.up=new Array(2*n+1);
this.left=new Array(n);
this.right=new Array(n);
this.reset();
};
dojo.extend(dojox.encoding.compression.Splay,{reset:function(){
for(var i=1;i<this.up.length;this.up[i]=Math.floor((i-1)/2),++i){
}
for(var i=0;i<this.left.length;this.left[i]=2*i+1,this.right[i]=2*i+2,++i){
}
},splay:function(i){
var a=i+this.left.length;
do{
var c=this.up[a];
if(c){
var d=this.up[c];
var b=this.left[d];
if(c==b){
b=this.right[d];
this.right[d]=a;
}else{
this.left[d]=a;
}
this[a==this.left[c]?"left":"right"][c]=b;
this.up[a]=d;
this.up[b]=c;
a=d;
}else{
a=c;
}
}while(a);
},encode:function(_8,_9){
var s=[],a=_8+this.left.length;
do{
s.push(this.right[this.up[a]]==a);
a=this.up[a];
}while(a);
this.splay(_8);
var l=s.length;
while(s.length){
_9.putBits(s.pop()?1:0,1);
}
return l;
},decode:function(_d){
var a=0;
do{
a=this[_d.getBits(1)?"right":"left"][a];
}while(a<this.left.length);
a-=this.left.length;
this.splay(a);
return a;
}});
}
