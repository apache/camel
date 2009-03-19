/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.collections.BinaryTree"]){
dojo._hasResource["dojox.collections.BinaryTree"]=true;
dojo.provide("dojox.collections.BinaryTree");
dojo.require("dojox.collections._base");
dojox.collections.BinaryTree=function(_1){
function _2(_3,_4,_5){
this.value=_3||null;
this.right=_4||null;
this.left=_5||null;
this.clone=function(){
var c=new _2();
if(this.value.value){
c.value=this.value.clone();
}else{
c.value=this.value;
}
if(this.left!=null){
c.left=this.left.clone();
}
if(this.right!=null){
c.right=this.right.clone();
}
return c;
};
this.compare=function(n){
if(this.value>n.value){
return 1;
}
if(this.value<n.value){
return -1;
}
return 0;
};
this.compareData=function(d){
if(this.value>d){
return 1;
}
if(this.value<d){
return -1;
}
return 0;
};
};
function _9(_a,a){
if(_a){
_9(_a.left,a);
a.push(_a.value);
_9(_a.right,a);
}
};
function _c(_d,_e){
var s="";
if(_d){
s=_d.value.toString()+_e;
s+=_c(_d.left,_e);
s+=_c(_d.right,_e);
}
return s;
};
function _10(_11,sep){
var s="";
if(_11){
s=_10(_11.left,sep);
s+=_11.value.toString()+sep;
s+=_10(_11.right,sep);
}
return s;
};
function _14(_15,sep){
var s="";
if(_15){
s=_14(_15.left,sep);
s+=_14(_15.right,sep);
s+=_15.value.toString()+sep;
}
return s;
};
function _18(_19,_1a){
if(!_19){
return null;
}
var i=_19.compareData(_1a);
if(i==0){
return _19;
}
if(i>0){
return _18(_19.left,_1a);
}else{
return _18(_19.right,_1a);
}
};
this.add=function(_1c){
var n=new _2(_1c);
var i;
var _1f=_20;
var _21=null;
while(_1f){
i=_1f.compare(n);
if(i==0){
return;
}
_21=_1f;
if(i>0){
_1f=_1f.left;
}else{
_1f=_1f.right;
}
}
this.count++;
if(!_21){
_20=n;
}else{
i=_21.compare(n);
if(i>0){
_21.left=n;
}else{
_21.right=n;
}
}
};
this.clear=function(){
_20=null;
this.count=0;
};
this.clone=function(){
var c=new dojox.collections.BinaryTree();
var itr=this.getIterator();
while(!itr.atEnd()){
c.add(itr.get());
}
return c;
};
this.contains=function(_24){
return this.search(_24)!=null;
};
this.deleteData=function(_25){
var _26=_20;
var _27=null;
var i=_26.compareData(_25);
while(i!=0&&_26!=null){
if(i>0){
_27=_26;
_26=_26.left;
}else{
if(i<0){
_27=_26;
_26=_26.right;
}
}
i=_26.compareData(_25);
}
if(!_26){
return;
}
this.count--;
if(!_26.right){
if(!_27){
_20=_26.left;
}else{
i=_27.compare(_26);
if(i>0){
_27.left=_26.left;
}else{
if(i<0){
_27.right=_26.left;
}
}
}
}else{
if(!_26.right.left){
if(!_27){
_20=_26.right;
}else{
i=_27.compare(_26);
if(i>0){
_27.left=_26.right;
}else{
if(i<0){
_27.right=_26.right;
}
}
}
}else{
var _29=_26.right.left;
var _2a=_26.right;
while(_29.left!=null){
_2a=_29;
_29=_29.left;
}
_2a.left=_29.right;
_29.left=_26.left;
_29.right=_26.right;
if(!_27){
_20=_29;
}else{
i=_27.compare(_26);
if(i>0){
_27.left=_29;
}else{
if(i<0){
_27.right=_29;
}
}
}
}
}
};
this.getIterator=function(){
var a=[];
_9(_20,a);
return new dojox.collections.Iterator(a);
};
this.search=function(_2c){
return _18(_20,_2c);
};
this.toString=function(_2d,sep){
if(!_2d){
_2d=dojox.collections.BinaryTree.TraversalMethods.Inorder;
}
if(!sep){
sep=",";
}
var s="";
switch(_2d){
case dojox.collections.BinaryTree.TraversalMethods.Preorder:
s=_c(_20,sep);
break;
case dojox.collections.BinaryTree.TraversalMethods.Inorder:
s=_10(_20,sep);
break;
case dojox.collections.BinaryTree.TraversalMethods.Postorder:
s=_14(_20,sep);
break;
}
if(s.length==0){
return "";
}else{
return s.substring(0,s.length-sep.length);
}
};
this.count=0;
var _20=this.root=null;
if(_1){
this.add(_1);
}
};
dojox.collections.BinaryTree.TraversalMethods={Preorder:1,Inorder:2,Postorder:3};
}
