/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.functional.util"]){
dojo._hasResource["dojox.lang.functional.util"]=true;
dojo.provide("dojox.lang.functional.util");
dojo.require("dojox.lang.functional.lambda");
(function(){
var df=dojox.lang.functional;
dojo.mixin(df,{inlineLambda:function(_2,_3,_4){
var s=df.rawLambda(_2);
if(_4){
df.forEach(s.args,_4);
}
var ap=typeof _3=="string",n=ap?s.args.length:Math.min(s.args.length,_3.length),a=new Array(4*n+4),i,j=1;
for(i=0;i<n;++i){
a[j++]=s.args[i];
a[j++]="=";
a[j++]=ap?_3+"["+i+"]":_3[i];
a[j++]=",";
}
a[0]="(";
a[j++]="(";
a[j++]=s.body;
a[j]="))";
return a.join("");
}});
})();
}
