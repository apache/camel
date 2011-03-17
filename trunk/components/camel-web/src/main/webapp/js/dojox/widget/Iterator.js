/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.Iterator"]){
dojo._hasResource["dojox.widget.Iterator"]=true;
dojo.provide("dojox.widget.Iterator");
dojo.require("dijit.Declaration");
dojo.experimental("dojox.widget.Iterator");
dojo.declare("dojox.widget.Iterator",[dijit.Declaration],{constructor:(function(){
var _1=0;
return function(){
this.attrs=[];
this.children=[];
this.widgetClass="dojox.widget.Iterator._classes._"+(_1++);
};
})(),start:0,fetchMax:1000,query:{name:"*"},attrs:[],defaultValue:"",widgetCtor:null,dataValues:[],data:null,store:null,_srcIndex:0,_srcParent:null,_setSrcIndex:function(s){
this._srcIndex=0;
this._srcParent=s.parentNode;
var ts=s;
while(ts.previousSibling){
this._srcIndex++;
ts=ts.previousSibling;
}
},postscript:function(p,s){
this._setSrcIndex(s);
this.inherited("postscript",arguments);
var wc=this.widgetCtor=dojo.getObject(this.widgetClass);
this.attrs=dojo.map(wc.prototype.templateString.match(/\$\{([^\s\:\}]+)(?:\:([^\s\:\}]+))?\}/g),function(s){
return s.slice(2,-1);
});
dojo.forEach(this.attrs,function(m){
wc.prototype[m]="";
});
this.update();
},clear:function(){
if(this.children.length){
this._setSrcIndex(this.children[0].domNode);
}
dojo.forEach(this.children,"item.destroy();");
this.children=[];
},update:function(){
if(this.store){
this.fetch();
}else{
this.onDataAvailable(this.data||this.dataValues);
}
},_addItem:function(_9,_a){
if(dojo.isString(_9)){
_9={value:_9};
}
var _b=new this.widgetCtor(_9);
this.children.push(_b);
dojo.place(_b.domNode,this._srcParent,this._srcIndex+_a);
},getAttrValuesObj:function(_c){
var _d={};
if(dojo.isString(_c)){
dojo.forEach(this.attrs,function(_e){
_d[_e]=(_e=="value")?_c:this.defaultValue;
},this);
}else{
dojo.forEach(this.attrs,function(_f){
if(this.store){
_d[_f]=this.store.getValue(_c,_f)||this.defaultValue;
}else{
_d[_f]=_c[_f]||this.defaultValue;
}
},this);
}
return _d;
},onDataAvailable:function(_10){
this.clear();
dojo.forEach(_10,function(_11,idx){
this._addItem(this.getAttrValuesObj(_11),idx);
},this);
},fetch:function(_13,_14,end){
this.store.fetch({query:_13||this.query,start:_14||this.start,count:end||this.fetchMax,onComplete:dojo.hitch(this,"onDataAvailable")});
}});
dojox.widget.Iterator._classes={};
}
