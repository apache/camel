/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.widget.Pager"]){
dojo._hasResource["dojox.widget.Pager"]=true;
dojo.provide("dojox.widget.Pager");
dojo.experimental("dojox.widget.Pager");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dojo.fx");
dojo.declare("dojox.widget.Pager",[dijit._Widget,dijit._Templated],{templateString:"<div dojoAttachPoint=\"pagerContainer\" tabIndex=\"0\" dojoAttachEvent=\"onkeypress: _handleKey, onfocus: _a11yStyle, onblur:_a11yStyle\" class=\"${orientation}PagerContainer\">\n    <div class=\"pagerContainer\">\n\t\t<div dojoAttachPoint=\"pagerContainerStatus\" class=\"${orientation}PagerStatus\"></div>\n\t\t<div dojoAttachPoint=\"pagerContainerPager\" class=\"${orientation}PagerPager\">\n\t\t\t<div tabIndex=\"0\" dojoAttachPoint=\"pagerNext\" class=\"pagerIconContainer\" dojoAttachEvent=\"onclick: _pagerNext\"><img dojoAttachPoint=\"pagerIconNext\" src=\"${iconNext}\" alt=\"Next\" /></div>\n\t\t\t<div tabIndex=\"0\" dojoAttachPoint=\"pagerPrevious\" class=\"pagerIconContainer\" dojoAttachEvent=\"onclick: _pagerPrevious\"><img dojoAttachPoint=\"pagerIconPrevious\" src=\"${iconPrevious}\" alt=\"Previous\" /></div>\n\t\t</div>\n\t\t<div dojoAttachPoint=\"pagerContainerView\" class=\"${orientation}PagerView\">\n\t\t    <div dojoAttachPoint=\"pagerItemContainer\"><ul dojoAttachPoint=\"pagerItems\" class=\"pagerItems\"></ul></div>\n\t\t</div>\n    </div>\n\t<div dojoAttachPoint=\"containerNode\" style=\"display:none\"></div>\n</div>\n",iconPage:dojo.moduleUrl("dojox.widget","Pager/images/pageInactive.png"),iconPageActive:dojo.moduleUrl("dojox.widget","Pager/images/pageActive.png"),store:null,orientation:"horizontal",statusPos:"leading",pagerPos:"center",duration:500,itemSpace:2,resizeChildren:true,itemClass:"dojox.widget._PagerItem",itemsPage:3,postMixInProperties:function(){
var h=(this.orientation=="horizontal");
dojo.mixin(this,{_totalPages:0,_currentPage:1,dirClass:"pager"+(h?"Horizontal":"Vertical"),iconNext:dojo.moduleUrl("dojox.widget","Pager/images/"+(h?"h":"v")+"Next.png"),iconPrevious:dojo.moduleUrl("dojox.widget","Pager/images/"+(h?"h":"v")+"Previous.png")});
},postCreate:function(){
this.inherited(arguments);
this.store.fetch({onComplete:dojo.hitch(this,"_init")});
},_a11yStyle:function(e){
dojo[(e.type=="focus"?"addClass":"removeClass")](e.target,"pagerFocus");
},_handleKey:function(e){
var dk=dojo.keys;
var _5=(e.charCode==dk.SPACE?dk.SPACE:e.keyCode);
switch(_5){
case dk.UP_ARROW:
case dk.RIGHT_ARROW:
case 110:
case 78:
e.preventDefault();
this._pagerNext();
break;
case dk.DOWN_ARROW:
case dk.LEFT_ARROW:
case 112:
case 80:
e.preventDefault();
this._pagerPrevious();
break;
case dk.ENTER:
switch(e.target){
case this.pagerNext:
this._pagerNext();
break;
case this.pagerPrevious:
this._pagerPrevious();
break;
}
break;
}
},_init:function(_6){
this.items=_6;
this._renderPages();
this._renderStatus();
this._renderPager();
},_renderPages:function(){
var _7=this.pagerContainerView;
var _h=(this.orientation=="horizontal");
var _9=dojo.style;
if(_h){
var _a=dojo.marginBox(this.pagerContainerPager).h;
var _b=dojo.marginBox(this.pagerContainerStatus).h;
if(this.pagerPos!="center"){
var _c=_a+_b;
}else{
var _c=_b;
var _d=this.pagerIconNext.width;
var _e=_9(_7,"width");
var _f=_e-(2*_d);
_9(_7,{width:_f+"px",marginLeft:this.pagerIconNext.width+"px",marginRight:this.pagerIconNext.width+"px"});
}
var _10=_9(this.pagerContainer,"height")-_c;
_9(this.pagerContainerView,"height",_10+"px");
var _11=Math.floor(_9(_7,"width")/this.itemsPage);
if(this.statusPos=="trailing"){
if(this.pagerPos!="center"){
_9(_7,"marginTop",_a+"px");
}
_9(_7,"marginBottom",_b+"px");
}else{
_9(_7,"marginTop",_b+"px");
if(this.pagerPos!="center"){
_9(_7,"marginTop",_a+"px");
}
}
}else{
var _12=dojo.marginBox(this.pagerContainerPager).w;
var _13=dojo.marginBox(this.pagerContainerStatus).w;
var _14=_9(this.pagerContainer,"width");
if(this.pagerPos!="center"){
var _15=_12+_13;
}else{
var _15=_13;
var _16=this.pagerIconNext.height;
var _17=_9(_7,"height");
var _18=_17-(2*_16);
_9(_7,{height:_18+"px",marginTop:this.pagerIconNext.height+"px",marginBottom:this.pagerIconNext.height+"px"});
}
var _19=_9(this.pagerContainer,"width")-_15;
_9(_7,"width",_19+"px");
var _11=Math.floor(_9(_7,"height")/this.itemsPage);
if(this.statusPos=="trailing"){
if(this.pagerPos!="center"){
_9(_7,"marginLeft",_12+"px");
}
_9(_7,"marginRight",_13+"px");
}else{
_9(_7,"marginLeft",_13+"px");
if(this.pagerPos!="center"){
_9(_7,"marginRight",_12+"px");
}
}
}
var _1a=dojo.getObject(this.itemClass);
var _1b="padding"+(_h?"Left":"Top");
var _1c="padding"+(_h?"Right":"Bottom");
dojo.forEach(this.items,function(_1d,cnt){
var _1f=dojo.create("div",{innerHTML:_1d.content});
var _20=new _1a({id:this.id+"-item-"+(cnt+1)},_1f);
this.pagerItems.appendChild(_20.domNode);
var _21={};
_21[(_h?"width":"height")]=(_11-this.itemSpace)+"px";
var p=(_h?"height":"width");
_21[p]=_9(_7,p)+"px";
_9(_20.containerNode,_21);
if(this.resizeChildren){
_20.resizeChildren();
}
_20.parseChildren();
_9(_20.domNode,"position","absolute");
if(cnt<this.itemsPage){
var pos=(cnt)*_11;
var _24=(_h?"left":"top");
var dir=(_h?"top":"left");
_9(_20.domNode,dir,"0px");
_9(_20.domNode,_24,pos+"px");
}else{
_9(_20.domNode,"top","-1000px");
_9(_20.domNode,"left","-1000px");
}
_9(_20.domNode,_1c,(this.itemSpace/2)+"px");
_9(_20.domNode,_1b,(this.itemSpace/2)+"px");
},this);
},_renderPager:function(){
var tcp=this.pagerContainerPager;
var _27="0px";
var _h=(this.orientation=="horizontal");
if(_h){
if(this.statusPos=="center"){
}else{
if(this.statusPos=="trailing"){
dojo.style(tcp,"top",_27);
}else{
dojo.style(tcp,"bottom",_27);
}
}
dojo.style(this.pagerNext,"right",_27);
dojo.style(this.pagerPrevious,"left",_27);
}else{
if(this.statusPos=="trailing"){
dojo.style(tcp,"left",_27);
}else{
dojo.style(tcp,"right",_27);
}
dojo.style(this.pagerNext,"bottom",_27);
dojo.style(this.pagerPrevious,"top",_27);
}
},_renderStatus:function(){
this._totalPages=Math.ceil(this.items.length/this.itemsPage);
this.iconWidth=0;
this.iconHeight=0;
this.iconsLoaded=0;
this._iconConnects=[];
for(var i=1;i<=this._totalPages;i++){
var _2a=new Image();
var _2b=i;
dojo.connect(_2a,"onclick",dojo.hitch(this,function(_2c){
this._pagerSkip(_2c);
},_2b));
this._iconConnects[_2b]=dojo.connect(_2a,"onload",dojo.hitch(this,function(_2d){
this.iconWidth+=_2a.width;
this.iconHeight+=_2a.height;
this.iconsLoaded++;
if(this._totalPages==this.iconsLoaded){
if(this.orientation=="horizontal"){
if(this.statusPos=="trailing"){
if(this.pagerPos=="center"){
var _2e=dojo.style(this.pagerContainer,"height");
var _2f=dojo.style(this.pagerContainerStatus,"height");
dojo.style(this.pagerContainerPager,"top",((_2e/2)-(_2f/2))+"px");
}
dojo.style(this.pagerContainerStatus,"bottom","0px");
}else{
if(this.pagerPos=="center"){
var _2e=dojo.style(this.pagerContainer,"height");
var _2f=dojo.style(this.pagerContainerStatus,"height");
dojo.style(this.pagerContainerPager,"bottom",((_2e/2)-(_2f/2))+"px");
}
dojo.style(this.pagerContainerStatus,"top","0px");
}
var _30=(dojo.style(this.pagerContainer,"width")/2)-(this.iconWidth/2);
dojo.style(this.pagerContainerStatus,"paddingLeft",_30+"px");
}else{
if(this.statusPos=="trailing"){
if(this.pagerPos=="center"){
var _31=dojo.style(this.pagerContainer,"width");
var _32=dojo.style(this.pagerContainerStatus,"width");
dojo.style(this.pagerContainerPager,"left",((_31/2)-(_32/2))+"px");
}
dojo.style(this.pagerContainerStatus,"right","0px");
}else{
if(this.pagerPos=="center"){
var _31=dojo.style(this.pagerContainer,"width");
var _32=dojo.style(this.pagerContainerStatus,"width");
dojo.style(this.pagerContainerPager,"right",((_31/2)-(_32/2))+"px");
}
dojo.style(this.pagerContainerStatus,"left","0px");
}
var _30=(dojo.style(this.pagerContainer,"height")/2)-(this.iconHeight/2);
dojo.style(this.pagerContainerStatus,"paddingTop",_30+"px");
}
}
dojo.disconnect(this._iconConnects[_2d]);
},_2b));
if(i==this._currentPage){
_2a.src=this.iconPageActive;
}else{
_2a.src=this.iconPage;
}
var _2b=i;
dojo.addClass(_2a,this.orientation+"PagerIcon");
dojo.attr(_2a,"id",this.id+"-status-"+i);
this.pagerContainerStatus.appendChild(_2a);
if(this.orientation=="vertical"){
dojo.style(_2a,"display","block");
}
}
},_pagerSkip:function(_33){
if(this._currentPage==_33){
return;
}else{
var _34;
var _35;
if(_33<this._currentPage){
_34=this._currentPage-_33;
_35=(this._totalPages+_33)-this._currentPage;
}else{
_34=(this._totalPages+this._currentPage)-_33;
_35=_33-this._currentPage;
}
var b=(_35>_34);
this._toScroll=(b?_34:_35);
var cmd=(b?"_pagerPrevious":"_pagerNext");
var _38=this.connect(this,"onScrollEnd",function(){
this._toScroll--;
if(this._toScroll<1){
this.disconnect(_38);
}else{
this[cmd]();
}
});
this[cmd]();
}
},_pagerNext:function(){
if(this._anim){
return;
}
var _39=[];
for(var i=this._currentPage*this.itemsPage;i>(this._currentPage-1)*this.itemsPage;i--){
if(!dojo.byId(this.id+"-item-"+i)){
continue;
}
var _3b=dojo.byId(this.id+"-item-"+i);
var _3c=dojo.marginBox(_3b);
if(this.orientation=="horizontal"){
var _3d=_3c.l-(this.itemsPage*_3c.w);
_39.push(dojo.fx.slideTo({node:_3b,left:_3d,duration:this.duration}));
}else{
var _3d=_3c.t-(this.itemsPage*_3c.h);
_39.push(dojo.fx.slideTo({node:_3b,top:_3d,duration:this.duration}));
}
}
var _3e=this._currentPage;
if(this._currentPage==this._totalPages){
this._currentPage=1;
}else{
this._currentPage++;
}
var cnt=this.itemsPage;
for(var i=this._currentPage*this.itemsPage;i>(this._currentPage-1)*this.itemsPage;i--){
if(dojo.byId(this.id+"-item-"+i)){
var _3b=dojo.byId(this.id+"-item-"+i);
var _3c=dojo.marginBox(_3b);
if(this.orientation=="horizontal"){
var _40=(dojo.style(this.pagerContainerView,"width")+((cnt-1)*_3c.w))-1;
dojo.style(_3b,"left",_40+"px");
dojo.style(_3b,"top","0px");
var _3d=_40-(this.itemsPage*_3c.w);
_39.push(dojo.fx.slideTo({node:_3b,left:_3d,duration:this.duration}));
}else{
_40=(dojo.style(this.pagerContainerView,"height")+((cnt-1)*_3c.h))-1;
dojo.style(_3b,"top",_40+"px");
dojo.style(_3b,"left","0px");
var _3d=_40-(this.itemsPage*_3c.h);
_39.push(dojo.fx.slideTo({node:_3b,top:_3d,duration:this.duration}));
}
}
cnt--;
}
this._anim=dojo.fx.combine(_39);
var _41=this.connect(this._anim,"onEnd",function(){
delete this._anim;
this.onScrollEnd();
this.disconnect(_41);
});
this._anim.play();
dojo.byId(this.id+"-status-"+_3e).src=this.iconPage;
dojo.byId(this.id+"-status-"+this._currentPage).src=this.iconPageActive;
},_pagerPrevious:function(){
if(this._anim){
return;
}
var _42=[];
for(var i=this._currentPage*this.itemsPage;i>(this._currentPage-1)*this.itemsPage;i--){
if(!dojo.byId(this.id+"-item-"+i)){
continue;
}
var _44=dojo.byId(this.id+"-item-"+i);
var _45=dojo.marginBox(_44);
if(this.orientation=="horizontal"){
var _46=dojo.style(_44,"left")+(this.itemsPage*_45.w);
_42.push(dojo.fx.slideTo({node:_44,left:_46,duration:this.duration}));
}else{
var _46=dojo.style(_44,"top")+(this.itemsPage*_45.h);
_42.push(dojo.fx.slideTo({node:_44,top:_46,duration:this.duration}));
}
}
var _47=this._currentPage;
if(this._currentPage==1){
this._currentPage=this._totalPages;
}else{
this._currentPage--;
}
var cnt=this.itemsPage;
var j=1;
for(var i=this._currentPage*this.itemsPage;i>(this._currentPage-1)*this.itemsPage;i--){
if(dojo.byId(this.id+"-item-"+i)){
var _44=dojo.byId(this.id+"-item-"+i);
var _45=dojo.marginBox(_44);
if(this.orientation=="horizontal"){
var _4a=-(j*_45.w)+1;
dojo.style(_44,"left",_4a+"px");
dojo.style(_44,"top","0px");
var _46=((cnt-1)*_45.w);
_42.push(dojo.fx.slideTo({node:_44,left:_46,duration:this.duration}));
var _46=_4a+(this.itemsPage*_45.w);
_42.push(dojo.fx.slideTo({node:_44,left:_46,duration:this.duration}));
}else{
_4a=-((j*_45.h)+1);
dojo.style(_44,"top",_4a+"px");
dojo.style(_44,"left","0px");
var _46=((cnt-1)*_45.h);
_42.push(dojo.fx.slideTo({node:_44,top:_46,duration:this.duration}));
}
}
cnt--;
j++;
}
this._anim=dojo.fx.combine(_42);
var _4b=dojo.connect(this._anim,"onEnd",dojo.hitch(this,function(){
delete this._anim;
this.onScrollEnd();
dojo.disconnect(_4b);
}));
this._anim.play();
dojo.byId(this.id+"-status-"+_47).src=this.iconPage;
dojo.byId(this.id+"-status-"+this._currentPage).src=this.iconPageActive;
},onScrollEnd:function(){
}});
dojo.declare("dojox.widget._PagerItem",[dijit._Widget,dijit._Templated],{templateString:"<li class=\"pagerItem\" dojoAttachPoint=\"containerNode\"></li>",resizeChildren:function(){
var box=dojo.marginBox(this.containerNode);
dojo.style(this.containerNode.firstChild,{width:box.w+"px",height:box.h+"px"});
},parseChildren:function(){
dojo.parser.parse(this.containerNode);
}});
}
