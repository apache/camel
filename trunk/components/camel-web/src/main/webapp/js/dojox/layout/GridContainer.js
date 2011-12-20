/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.layout.GridContainer"]){
dojo._hasResource["dojox.layout.GridContainer"]=true;
dojo.provide("dojox.layout.GridContainer");
dojo.experimental("dojox.layout.GridContainer");
dojo.require("dijit._base.focus");
dojo.require("dijit._Templated");
dojo.require("dijit._Container");
dojo.require("dijit._Contained");
dojo.require("dojo.dnd.move");
dojo.require("dojox.layout.dnd.PlottedDnd");
dojo.requireLocalization("dojox.layout","GridContainer",null,"ROOT,en,fr");
dojo.declare("dojox.layout.GridContainer",[dijit._Widget,dijit._Templated,dijit._Container,dijit._Contained],{templateString:"<div id=\"${id}\" class=\"gridContainer\" dojoAttachPoint=\"containerNode\" tabIndex=\"0\" dojoAttachEvent=\"onkeypress:_selectFocus\">\n\t<table class=\"gridContainerTable\" dojoAttachPoint=\"gridContainerTable\" cellspacing=\"0\" cellpadding=\"0\">\n\t\t<tbody class=\"gridContainerBody\">\n\t\t\t<tr class=\"gridContainerRow\" dojoAttachPoint=\"gridNode\"></tr>\n\t\t</tbody>\n\t</table>\n</div>\n",isContainer:true,i18n:null,isAutoOrganized:true,isRightFixed:false,isLeftFixed:false,hasResizableColumns:true,nbZones:1,opacity:1,minColWidth:20,minChildWidth:150,acceptTypes:[],mode:"right",allowAutoScroll:false,timeDisplayPopup:1500,isOffset:false,offsetDrag:{},withHandles:false,handleClasses:[],_draggedWidget:null,_isResized:false,_activeGrip:null,_oldwidth:0,_oldheight:0,_a11yOn:false,_canDisplayPopup:true,constructor:function(_1,_2){
this.acceptTypes=_1["acceptTypes"]||["dijit.layout.ContentPane"];
this.dragOffset=_1["dragOffset"]||{x:0,y:0};
},postMixInProperties:function(){
this.i18n=dojo.i18n.getLocalization("dojox.layout","GridContainer");
},_createCells:function(){
if(this.nbZones===0){
this.nbZones=1;
}
var _3=100/this.nbZones;
if(dojo.isIE&&dojo.marginBox(this.gridNode).height){
var _4=document.createTextNode(" ");
this.gridNode.appendChild(_4);
}
var _5=[];
this.cell=[];
var i=0;
while(i<this.nbZones){
var _7=dojo.doc.createElement("td");
dojo.addClass(_7,"gridContainerZone");
_7.id=this.id+"_dz"+i;
_7.style.width=_3+"%";
var _8=this.gridNode.appendChild(_7);
this.cell[i]=_8;
i++;
}
},startup:function(){
this.inherited(arguments);
this._createCells();
if(this.usepref!==true){
this[(this.isAutoOrganized?"_organizeServices":"_organizeServicesManually")]();
}else{
return;
}
this.init();
dojo.forEach(this.getChildren(),function(_9){
!_9.started&&!_9._started&&_9.startup();
});
},init:function(){
this.grid=this._createGrid();
this.connect(dojo.global,"onresize","onResized");
this.connect(this,"onDndDrop","_placeGrips");
this.dropHandler=dojo.subscribe("/dnd/drop",this,"_placeGrips");
this._oldwidth=this.domNode.offsetWidth;
if(this.hasResizableColumns){
this._initPlaceGrips();
this._placeGrips();
}
},destroy:function(){
for(var i=0;i<this.handleDndStart;i++){
dojo.disconnect(this.handleDndStart[i]);
}
dojo.unsubscribe(this.dropHandler);
this.inherited(arguments);
},onResized:function(){
if(this.hasResizableColumns){
this._placeGrips();
this._oldwidth=this.domNode.offsetWidth;
this._oldheight=this.domNode.offsetHeight;
}
},_organizeServices:function(){
var _b=this.nbZones;
var _c=this.getChildren().length;
var _d=Math.floor(_c/_b);
var _e=_c%_b;
var i=0;
for(var z=0;z<_b;z++){
for(var r=0;r<_d;r++){
this._insertService(z,i++,0,true);
}
if(_e>0){
try{
this._insertService(z,i++,0,true);
}
catch(e){
console.error("Unable to insert service in grid container",e,this.getChildren());
}
_e--;
}else{
if(_d===0){
break;
}
}
}
},_organizeServicesManually:function(){
var _12=this.getChildren();
for(var i=0;i<_12.length;i++){
try{
this._insertService(_12[i].column-1,i,0,true);
}
catch(e){
console.error("Unable to insert service in grid container",e,_12[i]);
}
}
},_insertService:function(z,p,i,_17){
var _18=this.cell[z];
var _19=_18.childNodes.length;
var _1a=this.getChildren()[(i?i:0)];
if(typeof (p)=="undefined"||p>_19){
p=_19;
}
var _1b=dojo.place(_1a.domNode,_18,p);
_1a.domNode.setAttribute("tabIndex",0);
if(!_1a.dragRestriction){
dojo.addClass(_1a.domNode,"dojoDndItem");
}
if(!_1a.domNode.getAttribute("dndType")){
_1a.domNode.setAttribute("dndType",_1a.declaredClass);
}
dojox.layout.dnd._setGcDndHandle(_1a,this.withHandles,this.handleClasses,_17);
if(this.hasResizableColumns){
if(_1a.onLoad){
this.connect(_1a,"onLoad","_placeGrips");
}
if(_1a.onExecError){
this.connect(_1a,"onExecError","_placeGrips");
}
if(_1a.onUnLoad){
this.connect(_1a,"onUnLoad","_placeGrips");
}
}
this._placeGrips();
return _1a.id;
},addService:function(_1c,z,p){
_1c.domNode.id=_1c.id;
this.addChild(_1c);
if(p<=0){
p=0;
}
var _1f=this._insertService(z,p);
this.grid[z].setItem(_1c.id,{data:_1c.domNode,type:[_1c.domNode.getAttribute("dndType")]});
return _1f;
},_createGrid:function(){
var _20=[];
var i=0;
this.tabDZ=[];
while(i<this.nbZones){
var _22=this.cell[i];
this.tabDZ[i]=this._createZone(_22);
if(this.hasResizableColumns&&i!=(this.nbZones-1)){
this._createGrip(this.tabDZ[i]);
}
_20.push(this.tabDZ[i]);
i++;
}
if(this.hasResizableColumns){
this.handleDndStart=[];
for(var j=0;j<this.tabDZ.length;j++){
var dz=this.tabDZ[j];
var _25=this;
this.handleDndStart.push(dojo.connect(dz,"onDndStart",dz,function(_26){
if(_26==this){
_25.handleDndInsertNodes=[];
for(i=0;i<_25.tabDZ.length;i++){
_25.handleDndInsertNodes.push(dojo.connect(_25.tabDZ[i],"insertNodes",_25,function(){
_25._disconnectDnd();
}));
}
_25.handleDndInsertNodes.push(dojo.connect(dz,"onDndCancel",_25,_25._disconnectDnd));
_25.onResized();
}
}));
}
}
return _20;
},_disconnectDnd:function(){
dojo.forEach(this.handleDndInsertNodes,dojo.disconnect);
setTimeout(dojo.hitch(this,"onResized"),0);
},_createZone:function(_27){
var dz=null;
dz=new dojox.layout.dnd.PlottedDnd(_27.id,{accept:this.acceptTypes,withHandles:this.withHandles,handleClasses:this.handleClasses,singular:true,hideSource:true,opacity:this.opacity,dom:this.domNode,allowAutoScroll:this.allowAutoScroll,isOffset:this.isOffset,offsetDrag:this.offsetDrag});
this.connect(dz,"insertDashedZone","_placeGrips");
this.connect(dz,"deleteDashedZone","_placeGrips");
return dz;
},_createGrip:function(dz){
var _2a=document.createElement("div");
_2a.className="gridContainerGrip";
_2a.setAttribute("tabIndex","0");
var _2b=this;
this.onMouseOver=this.connect(_2a,"onmouseover",function(e){
var _2d=false;
for(var i=0;i<_2b.grid.length-1;i++){
if(dojo.hasClass(_2b.grid[i].grip,"gridContainerGripShow")){
_2d=true;
break;
}
}
if(!_2d){
dojo.removeClass(e.target,"gridContainerGrip");
dojo.addClass(e.target,"gridContainerGripShow");
}
});
this.connect(_2a,"onmouseout",function(e){
if(!_2b._isResized){
dojo.removeClass(e.target,"gridContainerGripShow");
dojo.addClass(e.target,"gridContainerGrip");
}
});
this.connect(_2a,"onmousedown",function(e){
_2b._a11yOn=false;
_2b._activeGrip=e.target;
_2b.resizeColumnOn(e);
});
this.domNode.appendChild(_2a);
dz.grip=_2a;
},_initPlaceGrips:function(){
var dcs=dojo.getComputedStyle(this.domNode);
var gcs=dojo.getComputedStyle(this.gridContainerTable);
this._x=parseInt(dcs.paddingLeft);
this._topGrip=parseInt(dcs.paddingTop);
if(dojo.isIE||gcs.borderCollapse!="collapse"){
var ex=dojo._getBorderExtents(this.gridContainerTable);
this._x+=ex.l;
this._topGrip+=ex.t;
}
this._topGrip+="px";
dojo.forEach(this.grid,function(_34){
if(_34.grip){
var _35=_34.grip;
if(!dojo.isIE){
_34.pad=dojo._getPadBorderExtents(_34.node).w;
}
_35.style.top=this._topGrip;
}
},this);
},_placeGrips:function(){
var _36;
if(this.allowAutoScroll){
_36=this.gridNode.scrollHeight;
}else{
_36=dojo.contentBox(this.gridNode).h;
}
var _37=this._x;
dojo.forEach(this.grid,function(_38){
if(_38.grip){
var _39=_38.grip;
_37+=dojo[(dojo.isIE?"marginBox":"contentBox")](_38.node).w+(dojo.isIE?0:_38.pad);
dojo.style(_39,{left:_37+"px",height:_36+"px"});
}
},this);
},_getZoneByIndex:function(n){
return this.grid[(n>=0&&n<this.grid.length?n:0)];
},getIndexZone:function(_3b){
for(var z=0;z<this.grid.length;z++){
if(this.grid[z].domNode==_3b){
return z;
}
}
return -1;
},resizeColumnOn:function(e){
var k=dojo.keys;
if(this._a11yOn&&e.keyCode!=k.LEFT_ARROW&&e.keyCode!=k.RIGHT_ARROW){
return;
}
e.preventDefault();
dojo.body().style.cursor="ew-resize";
this._isResized=true;
this.initX=e.pageX;
var _3f=[];
for(var i=0;i<this.grid.length;i++){
_3f[i]=dojo.contentBox(this.grid[i].node).w;
}
this.oldTabSize=_3f;
for(var i=0;i<this.grid.length;i++){
if(this._activeGrip==this.grid[i].grip){
this.currentColumn=this.grid[i].node;
this.currentColumnWidth=_3f[i];
this.nextColumn=this.currentColumn.nextSibling;
this.nextColumnWidth=_3f[i+1];
}
this.grid[i].node.style.width=_3f[i]+"px";
}
var _41=function(_42,_43){
var _44=0;
var _45=0;
dojo.forEach(_42,function(_46){
if(_46.nodeType==1){
var _47=dojo.getComputedStyle(_46);
var _48=(dojo.isIE?_43:parseInt(_47.minWidth));
_45=_48+parseInt(_47.marginLeft)+parseInt(_47.marginRight);
if(_44<_45){
_44=_45;
}
}
});
return _44;
};
var _49=_41(this.currentColumn.childNodes,this.minChildWidth);
var _4a=_41(this.nextColumn.childNodes,this.minChildWidth);
var _4b=Math.round((dojo.marginBox(this.gridContainerTable).w*this.minColWidth)/100);
this.currentMinCol=_49;
this.nextMinCol=_4a;
if(_4b>this.currentMinCol){
this.currentMinCol=_4b;
}
if(_4b>this.nextMinCol){
this.nextMinCol=_4b;
}
if(this._a11yOn){
this.connectResizeColumnMove=this.connect(dojo.doc,"onkeypress","resizeColumnMove");
}else{
this.connectResizeColumnMove=this.connect(dojo.doc,"onmousemove","resizeColumnMove");
this.connectResizeColumnOff=this.connect(document,"onmouseup","resizeColumnOff");
}
},resizeColumnMove:function(e){
var d=0;
if(this._a11yOn){
var k=dojo.keys;
switch(e.keyCode){
case k.LEFT_ARROW:
d=-10;
break;
case k.RIGHT_ARROW:
d=10;
break;
}
}else{
e.preventDefault();
d=e.pageX-this.initX;
}
if(d==0){
return;
}
if(!(this.currentColumnWidth+d<this.currentMinCol||this.nextColumnWidth-d<this.nextMinCol)){
this.currentColumnWidth+=d;
this.nextColumnWidth-=d;
this.initX=e.pageX;
this.currentColumn.style["width"]=this.currentColumnWidth+"px";
this.nextColumn.style["width"]=this.nextColumnWidth+"px";
this._activeGrip.style.left=parseInt(this._activeGrip.style.left)+d+"px";
this._placeGrips();
}
if(this._a11yOn){
this.resizeColumnOff(e);
}
},resizeColumnOff:function(e){
dojo.body().style.cursor="default";
if(this._a11yOn){
this.disconnect(this.connectResizeColumnMove);
this._a11yOn=false;
}else{
this.disconnect(this.connectResizeColumnMove);
this.disconnect(this.connectResizeColumnOff);
}
var _50=[];
var _51=[];
var _52=this.gridContainerTable.clientWidth;
for(var i=0;i<this.grid.length;i++){
var _cb=dojo.contentBox(this.grid[i].node);
if(dojo.isIE){
_50[i]=dojo.marginBox(this.grid[i].node).w;
_51[i]=_cb.w;
}else{
_50[i]=_cb.w;
_51=_50;
}
}
var _55=false;
for(var i=0;i<_51.length;i++){
if(_51[i]!=this.oldTabSize[i]){
_55=true;
break;
}
}
if(_55){
var mul=dojo.isIE?100:10000;
for(var i=0;i<this.grid.length;i++){
this.grid[i].node.style.width=Math.round((100*mul*_50[i])/_52)/mul+"%";
}
this._placeGrips();
}
if(this._activeGrip){
dojo.removeClass(this._activeGrip,"gridContainerGripShow");
dojo.addClass(this._activeGrip,"gridContainerGrip");
}
this._isResized=false;
},setColumns:function(_57){
if(_57>0){
var _58=this.grid.length-_57;
if(_58>0){
var _59=[];
var _5a,_5b,end;
if(this.mode=="right"){
end=(this.isLeftFixed&&this.grid.length>0)?1:0;
_5b=this.grid.length-(this.isRightFixed?2:1);
for(var z=_5b;z>=end;z--){
var _5e=0;
var _5a=this.grid[z].node;
for(var j=0;j<_5a.childNodes.length;j++){
if(_5a.childNodes[j].nodeType==1&&!(_5a.childNodes[j].id=="")){
_5e++;
break;
}
}
if(_5e==0){
_59[_59.length]=z;
}
if(_59.length>=_58){
this._deleteColumn(_59);
break;
}
}
if(_59.length<_58){
console.error(this.i18n.err_onSetNbColsRightMode);
}
}else{
if(this.isLeftFixed&&this.grid.length>0){
_5b=1;
}else{
_5b=0;
}
if(this.isRightFixed){
end=this.grid.length-1;
}else{
end=this.grid.length;
}
for(var z=_5b;z<end;z++){
var _5e=0;
var _5a=this.grid[z].node;
for(var j=0;j<_5a.childNodes.length;j++){
if(_5a.childNodes[j].nodeType==1&&!(_5a.childNodes[j].id=="")){
_5e++;
break;
}
}
if(_5e==0){
_59[_59.length]=z;
}
if(_59.length>=_58){
this._deleteColumn(_59);
break;
}
}
if(_59.length<_58){
alert(this.i18n.err_onSetNbColsLeftMode);
}
}
}else{
if(_58<0){
this._addColumn(Math.abs(_58));
}
}
this._initPlaceGrips();
this._placeGrips();
}
},_addColumn:function(_60){
var _61;
if(this.hasResizableColumns&&!this.isRightFixed&&this.mode=="right"){
_61=this.grid[this.grid.length-1];
this._createGrip(_61);
}
for(var i=0;i<_60;i++){
_61=dojo.doc.createElement("td");
dojo.addClass(_61,"gridContainerZone");
_61.id=this.id+"_dz"+this.nbZones;
var dz;
if(this.mode=="right"){
if(this.isRightFixed){
this.grid[this.grid.length-1].node.parentNode.insertBefore(_61,this.grid[this.grid.length-1].node);
dz=this._createZone(_61);
this.tabDZ.splice(this.tabDZ.length-1,0,dz);
this.grid.splice(this.grid.length-1,0,dz);
this.cell.splice(this.cell.length-1,0,_61);
}else{
var _64=this.gridNode.appendChild(_61);
dz=this._createZone(_61);
this.tabDZ.push(dz);
this.grid.push(dz);
this.cell.push(_61);
}
}else{
if(this.isLeftFixed){
(this.grid.length==1)?this.grid[0].node.parentNode.appendChild(_61,this.grid[0].node):this.grid[1].node.parentNode.insertBefore(_61,this.grid[1].node);
dz=this._createZone(_61);
this.tabDZ.splice(1,0,dz);
this.grid.splice(1,0,dz);
this.cell.splice(1,0,_61);
}else{
this.grid[this.grid.length-this.nbZones].node.parentNode.insertBefore(_61,this.grid[this.grid.length-this.nbZones].node);
dz=this._createZone(_61);
this.tabDZ.splice(this.tabDZ.length-this.nbZones,0,dz);
this.grid.splice(this.grid.length-this.nbZones,0,dz);
this.cell.splice(this.cell.length-this.nbZones,0,_61);
}
}
if(this.hasResizableColumns){
var _65=this;
var _66=dojo.connect(dz,"onDndStart",dz,function(_67){
if(_67==this){
_65.handleDndInsertNodes=[];
for(var o=0;o<_65.tabDZ.length;o++){
_65.handleDndInsertNodes.push(dojo.connect(_65.tabDZ[o],"insertNodes",_65,function(){
_65._disconnectDnd();
}));
}
_65.handleDndInsertNodes.push(dojo.connect(dz,"onDndCancel",_65,_65._disconnectDnd));
_65.onResized();
}
});
if(this.mode=="right"){
if(this.isRightFixed){
this.handleDndStart.splice(this.handleDndStart.length-1,0,_66);
}else{
this.handleDndStart.push(_66);
}
}else{
if(this.isLeftFixed){
this.handleDndStart.splice(1,0,_66);
}else{
this.handleDndStart.splice(this.handleDndStart.length-this.nbZones,0,_66);
}
}
this._createGrip(dz);
}
this.nbZones++;
}
this._updateColumnsWidth();
},_deleteColumn:function(_69){
var _6a,_6b,_6c;
_6c=0;
for(var i=0;i<_69.length;i++){
var idx=_69[i];
if(this.mode=="right"){
_6a=this.grid[idx];
}else{
_6a=this.grid[idx-_6c];
}
for(var j=0;j<_6a.node.childNodes.length;j++){
if(_6a.node.childNodes[j].nodeType!=1){
continue;
}
_6b=dijit.byId(_6a.node.childNodes[j].id);
for(var x=0;x<this.getChildren().length;x++){
if(this.getChildren()[x]===_6b){
this.getChildren().splice(x,1);
break;
}
}
}
_6a.node.parentNode.removeChild(_6a.node);
if(this.mode=="right"){
if(this.hasResizableColumns){
dojo.disconnect(this.handleDndStart[idx]);
}
this.grid.splice(idx,1);
this.tabDZ.splice(idx,1);
this.cell.splice(idx,1);
}else{
if(this.hasResizableColumns){
dojo.disconnect(this.handleDndStart[idx-_6c]);
}
this.grid.splice(idx-_6c,1);
this.tabDZ.splice(idx-_6c,1);
this.cell.splice(idx-_6c,1);
}
this.nbZones--;
_6c++;
if(_6a.grip){
this.domNode.removeChild(_6a.grip);
}
}
this._updateColumnsWidth();
},_updateColumnsWidth:function(){
var _71=100/this.nbZones;
var _72;
for(var z=0;z<this.grid.length;z++){
_72=this.grid[z].node;
_72.style.width=_71+"%";
}
},_selectFocus:function(_74){
var e=_74.keyCode;
var _76=null;
var _77=dijit.getFocus();
var _78=_77.node;
var k=dojo.keys;
var _7a=(e==k.UP_ARROW||e==k.LEFT_ARROW)?"lastChild":"firstChild";
var pos=(e==k.UP_ARROW||e==k.LEFT_ARROW)?"previousSibling":"nextSibling";
if(_78==this.containerNode){
switch(e){
case k.DOWN_ARROW:
case k.RIGHT_ARROW:
for(var i=0;i<this.gridNode.childNodes.length;i++){
_76=this.gridNode.childNodes[i].firstChild;
var _7d=false;
while(!_7d){
if(_76!=null){
if(_76.style.display!=="none"){
dijit.focus(_76);
dojo.stopEvent(_74);
_7d=true;
}else{
_76=_76[pos];
}
}else{
break;
}
}
if(_7d){
break;
}
}
break;
case k.UP_ARROW:
case k.LEFT_ARROW:
for(var i=this.gridNode.childNodes.length-1;i>=0;i--){
_76=this.gridNode.childNodes[i].lastChild;
var _7d=false;
while(!_7d){
if(_76!=null){
if(_76.style.display!=="none"){
dijit.focus(_76);
dojo.stopEvent(_74);
_7d=true;
}else{
_76=_76[pos];
}
}else{
break;
}
}
if(_7d){
break;
}
}
break;
}
}else{
if(_78.parentNode.parentNode==this.gridNode){
switch(e){
case k.UP_ARROW:
case k.DOWN_ARROW:
dojo.stopEvent(_74);
var _7e=0;
dojo.forEach(_78.parentNode.childNodes,function(_7f){
if(_7f.style.display!=="none"){
_7e++;
}
});
if(_7e==1){
return;
}
var _7d=false;
_76=_78[pos];
while(!_7d){
if(_76==null){
_76=_78.parentNode[_7a];
if(_76.style.display!=="none"){
_7d=true;
}else{
_76=_76[pos];
}
}else{
if(_76.style.display!=="none"){
_7d=true;
}else{
_76=_76[pos];
}
}
}
if(_74.shiftKey){
if(dijit.byNode(_78).dragRestriction){
return;
}
var _80=_78.getAttribute("dndtype");
var _81=false;
for(var i=0;i<this.acceptTypes.length;i++){
if(_80==this.acceptTypes[i]){
var _81=true;
break;
}
}
if(_81){
var _82=_78.parentNode;
var _83=_82.firstChild;
var _84=_82.lastChild;
while(_83.style.display=="none"||_84.style.display=="none"){
if(_83.style.display=="none"){
_83=_83.nextSibling;
}
if(_84.style.display=="none"){
_84=_84.previousSibling;
}
}
if(e==k.UP_ARROW){
var r=_82.removeChild(_78);
if(r==_83){
_82.appendChild(r);
}else{
_82.insertBefore(r,_76);
}
r.setAttribute("tabIndex","0");
dijit.focus(r);
}else{
if(_78==_84){
var r=_82.removeChild(_78);
_82.insertBefore(r,_76);
r.setAttribute("tabIndex","0");
dijit.focus(r);
}else{
var r=_82.removeChild(_76);
_82.insertBefore(r,_78);
_78.setAttribute("tabIndex","0");
dijit.focus(_78);
}
}
}else{
this._displayPopup();
}
}else{
dijit.focus(_76);
}
break;
case k.RIGHT_ARROW:
case k.LEFT_ARROW:
dojo.stopEvent(_74);
if(_74.shiftKey){
if(dijit.byNode(_78).dragRestriction){
return;
}
var z=0;
if(_78.parentNode[pos]==null){
if(e==k.LEFT_ARROW){
var z=this.gridNode.childNodes.length-1;
}
}else{
if(_78.parentNode[pos].nodeType==3){
z=this.gridNode.childNodes.length-2;
}else{
for(var i=0;i<this.gridNode.childNodes.length;i++){
if(_78.parentNode[pos]==this.gridNode.childNodes[i]){
break;
}
z++;
}
}
}
var _80=_78.getAttribute("dndtype");
var _81=false;
for(var i=0;i<this.acceptTypes.length;i++){
if(_80==this.acceptTypes[i]){
_81=true;
break;
}
}
if(_81){
var _87=_78.parentNode;
var _88=dijit.byNode(_78);
var r=_87.removeChild(_78);
var _89=(e==k.RIGHT_ARROW?0:this.gridNode.childNodes[z].length);
this.addService(_88,z,_89);
r.setAttribute("tabIndex","0");
dijit.focus(r);
this._placeGrips();
}else{
this._displayPopup();
}
}else{
var _8a=_78.parentNode;
while(_76===null){
if(_8a[pos]!==null&&_8a[pos].nodeType!==3){
_8a=_8a[pos];
}else{
if(pos==="previousSibling"){
_8a=_8a.parentNode.childNodes[_8a.parentNode.childNodes.length-1];
}else{
_8a=_8a.parentNode.childNodes[0];
}
}
var _7d=false;
var _8b=_8a[_7a];
while(!_7d){
if(_8b!=null){
if(_8b.style.display!=="none"){
_76=_8b;
_7d=true;
}else{
_8b=_8b[pos];
}
}else{
break;
}
}
}
dijit.focus(_76);
}
break;
}
}else{
if(dojo.hasClass(_78,"gridContainerGrip")||dojo.hasClass(_78,"gridContainerGripShow")){
this._activeGrip=_74.target;
this._a11yOn=true;
this.resizeColumnOn(_74);
}
}
}
},_displayPopup:function(){
if(this._canDisplayPopup){
var _8c=dojo.doc.createElement("div");
dojo.addClass(_8c,"gridContainerPopup");
_8c.innerHTML=this.i18n.alertPopup;
var _8d=this.containerNode.appendChild(_8c);
this._canDisplayPopup=false;
setTimeout(dojo.hitch(this,function(){
this.containerNode.removeChild(_8d);
dojo.destroy(_8d);
this._canDisplayPopup=true;
}),this.timeDisplayPopup);
}
}});
dojo.extend(dijit._Widget,{dragRestriction:false,column:"1",group:""});
}
