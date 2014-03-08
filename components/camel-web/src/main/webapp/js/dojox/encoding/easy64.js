/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.encoding.easy64"]){
dojo._hasResource["dojox.encoding.easy64"]=true;
dojo.provide("dojox.encoding.easy64");
(function(){
var c=function(_2,_3,_4){
for(var i=0;i<_3;i+=3){
_4.push(String.fromCharCode((_2[i]>>>2)+33),String.fromCharCode(((_2[i]&3)<<4)+(_2[i+1]>>>4)+33),String.fromCharCode(((_2[i+1]&15)<<2)+(_2[i+2]>>>6)+33),String.fromCharCode((_2[i+2]&63)+33));
}
};
dojox.encoding.easy64.encode=function(_6){
var _7=[],_8=_6.length%3,_9=_6.length-_8;
c(_6,_9,_7);
if(_8){
var t=_6.slice(_9);
while(t.length<3){
t.push(0);
}
c(t,3,_7);
for(var i=3;i>_8;_7.pop(),--i){
}
}
return _7.join("");
};
dojox.encoding.easy64.decode=function(_c){
var n=_c.length,r=[],b=[0,0,0,0],i,j,d;
for(i=0;i<n;i+=4){
for(j=0;j<4;++j){
b[j]=_c.charCodeAt(i+j)-33;
}
d=n-i;
for(j=d;j<4;b[++j]=0){
}
r.push((b[0]<<2)+(b[1]>>>4),((b[1]&15)<<4)+(b[2]>>>2),((b[2]&3)<<6)+b[3]);
for(j=d;j<4;++j,r.pop()){
}
}
return r;
};
})();
}
