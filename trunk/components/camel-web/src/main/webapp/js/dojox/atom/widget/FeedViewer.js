/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.atom.widget.FeedViewer"]){
dojo._hasResource["dojox.atom.widget.FeedViewer"]=true;
dojo.provide("dojox.atom.widget.FeedViewer");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._Container");
dojo.require("dojox.atom.io.Connection");
dojo.requireLocalization("dojox.atom.widget","FeedViewerEntry",null,"ROOT,cs,de,es,fr,hu,it,ja,ko,pl,pt,ru,zh,zh-tw");
dojo.experimental("dojox.atom.widget.FeedViewer");
dojo.declare("dojox.atom.widget.FeedViewer",[dijit._Widget,dijit._Templated,dijit._Container],{feedViewerTableBody:null,feedViewerTable:null,entrySelectionTopic:"",url:"",xmethod:false,localSaveOnly:false,templateString:"<div class=\"feedViewerContainer\" dojoAttachPoint=\"feedViewerContainerNode\">\n\t<table cellspacing=\"0\" cellpadding=\"0\" class=\"feedViewerTable\">\n\t\t<tbody dojoAttachPoint=\"feedViewerTableBody\" class=\"feedViewerTableBody\">\n\t\t</tbody>\n\t</table>\n</div>\n",_feed:null,_currentSelection:null,_includeFilters:null,alertsEnabled:false,postCreate:function(){
this._includeFilters=[];
if(this.entrySelectionTopic!==""){
this._subscriptions=[dojo.subscribe(this.entrySelectionTopic,this,"_handleEvent")];
}
this.atomIO=new dojox.atom.io.Connection();
this.childWidgets=[];
},startup:function(){
this.containerNode=this.feedViewerTableBody;
var _1=this.getDescendants();
for(var i in _1){
var _3=_1[i];
if(_3&&_3.isFilter){
this._includeFilters.push(new dojox.atom.widget.FeedViewer.CategoryIncludeFilter(_3.scheme,_3.term,_3.label));
_3.destroy();
}
}
if(this.url!==""){
this.setFeedFromUrl(this.url);
}
},clear:function(){
this.destroyDescendants();
},setFeedFromUrl:function(_4){
if(_4!==""){
if(this._isRelativeURL(_4)){
var _5="";
if(_4.charAt(0)!=="/"){
_5=this._calculateBaseURL(window.location.href,true);
}else{
_5=this._calculateBaseURL(window.location.href,false);
}
this.url=_5+_4;
}
this.atomIO.getFeed(_4,dojo.hitch(this,this.setFeed));
}
},setFeed:function(_6){
this._feed=_6;
this.clear();
var _7=function(a,b){
var _a=this._displayDateForEntry(a);
var _b=this._displayDateForEntry(b);
if(_a>_b){
return -1;
}
if(_a<_b){
return 1;
}
return 0;
};
var _c=function(_d){
var _e=_d.split(",");
_e.pop();
return _e.join(",");
};
var _f=_6.entries.sort(dojo.hitch(this,_7));
if(_6){
var _10=null;
for(var i=0;i<_f.length;i++){
var _12=_f[i];
if(this._isFilterAccepted(_12)){
var _13=this._displayDateForEntry(_12);
var _14="";
if(_13!==null){
_14=_c(_13.toLocaleString());
if(_14===""){
_14=""+(_13.getMonth()+1)+"/"+_13.getDate()+"/"+_13.getFullYear();
}
}
if((_10===null)||(_10!=_14)){
this.appendGrouping(_14);
_10=_14;
}
this.appendEntry(_12);
}
}
}
},_displayDateForEntry:function(_15){
if(_15.updated){
return _15.updated;
}
if(_15.modified){
return _15.modified;
}
if(_15.issued){
return _15.issued;
}
return new Date();
},appendGrouping:function(_16){
var _17=new dojox.atom.widget.FeedViewerGrouping({});
_17.setText(_16);
this.addChild(_17);
this.childWidgets.push(_17);
},appendEntry:function(_18){
var _19=new dojox.atom.widget.FeedViewerEntry({"xmethod":this.xmethod});
_19.setTitle(_18.title.value);
_19.setTime(this._displayDateForEntry(_18).toLocaleTimeString());
_19.entrySelectionTopic=this.entrySelectionTopic;
_19.feed=this;
this.addChild(_19);
this.childWidgets.push(_19);
this.connect(_19,"onClick","_rowSelected");
_18.domNode=_19.entryNode;
_18._entryWidget=_19;
_19.entry=_18;
},deleteEntry:function(_1a){
if(!this.localSaveOnly){
this.atomIO.deleteEntry(_1a.entry,dojo.hitch(this,this._removeEntry,_1a),null,this.xmethod);
}else{
this._removeEntry(_1a,true);
}
dojo.publish(this.entrySelectionTopic,[{action:"delete",source:this,entry:_1a.entry}]);
},_removeEntry:function(_1b,_1c){
if(_1c){
var idx=dojo.indexOf(this.childWidgets,_1b);
var _1e=this.childWidgets[idx-1];
var _1f=this.childWidgets[idx+1];
if(_1e.declaredClass==="dojox.atom.widget.FeedViewerGrouping"&&(_1f===undefined||_1f.declaredClass==="dojox.atom.widget.FeedViewerGrouping")){
_1e.destroy();
}
_1b.destroy();
}else{
}
},_rowSelected:function(evt){
var _21=evt.target;
while(_21){
if(_21.attributes){
var _22=_21.attributes.getNamedItem("widgetid");
if(_22&&_22.value.indexOf("FeedViewerEntry")!=-1){
break;
}
}
_21=_21.parentNode;
}
for(var i=0;i<this._feed.entries.length;i++){
var _24=this._feed.entries[i];
if((_21===_24.domNode)&&(this._currentSelection!==_24)){
dojo.addClass(_24.domNode,"feedViewerEntrySelected");
dojo.removeClass(_24._entryWidget.timeNode,"feedViewerEntryUpdated");
dojo.addClass(_24._entryWidget.timeNode,"feedViewerEntryUpdatedSelected");
this.onEntrySelected(_24);
if(this.entrySelectionTopic!==""){
dojo.publish(this.entrySelectionTopic,[{action:"set",source:this,feed:this._feed,entry:_24}]);
}
if(this._isEditable(_24)){
_24._entryWidget.enableDelete();
}
this._deselectCurrentSelection();
this._currentSelection=_24;
break;
}else{
if((_21===_24.domNode)&&(this._currentSelection===_24)){
dojo.publish(this.entrySelectionTopic,[{action:"delete",source:this,entry:_24}]);
this._deselectCurrentSelection();
break;
}
}
}
},_deselectCurrentSelection:function(){
if(this._currentSelection){
dojo.addClass(this._currentSelection._entryWidget.timeNode,"feedViewerEntryUpdated");
dojo.removeClass(this._currentSelection.domNode,"feedViewerEntrySelected");
dojo.removeClass(this._currentSelection._entryWidget.timeNode,"feedViewerEntryUpdatedSelected");
this._currentSelection._entryWidget.disableDelete();
this._currentSelection=null;
}
},_isEditable:function(_25){
var _26=false;
if(_25&&_25!==null&&_25.links&&_25.links!==null){
for(var x in _25.links){
if(_25.links[x].rel&&_25.links[x].rel=="edit"){
_26=true;
break;
}
}
}
return _26;
},onEntrySelected:function(_28){
},_isRelativeURL:function(url){
function _2a(url){
var _2c=false;
if(url.indexOf("file://")===0){
_2c=true;
}
return _2c;
};
function _2d(url){
var _2f=false;
if(url.indexOf("http://")===0){
_2f=true;
}
return _2f;
};
var _30=false;
if(url!==null){
if(!_2a(url)&&!_2d(url)){
_30=true;
}
}
return _30;
},_calculateBaseURL:function(_31,_32){
var _33=null;
if(_31!==null){
var _34=_31.indexOf("?");
if(_34!=-1){
_31=_31.substring(0,_34);
}
if(_32){
_34=_31.lastIndexOf("/");
if((_34>0)&&(_34<_31.length)&&(_34!==(_31.length-1))){
_33=_31.substring(0,(_34+1));
}else{
_33=_31;
}
}else{
_34=_31.indexOf("://");
if(_34>0){
_34=_34+3;
var _35=_31.substring(0,_34);
var _36=_31.substring(_34,_31.length);
_34=_36.indexOf("/");
if((_34<_36.length)&&(_34>0)){
_33=_35+_36.substring(0,_34);
}else{
_33=_35+_36;
}
}
}
}
return _33;
},_isFilterAccepted:function(_37){
var _38=false;
if(this._includeFilters&&(this._includeFilters.length>0)){
for(var i=0;i<this._includeFilters.length;i++){
var _3a=this._includeFilters[i];
if(_3a.match(_37)){
_38=true;
break;
}
}
}else{
_38=true;
}
return _38;
},addCategoryIncludeFilter:function(_3b){
if(_3b){
var _3c=_3b.scheme;
var _3d=_3b.term;
var _3e=_3b.label;
var _3f=true;
if(!_3c){
_3c=null;
}
if(!_3d){
_3c=null;
}
if(!_3e){
_3c=null;
}
if(this._includeFilters&&this._includeFilters.length>0){
for(var i=0;i<this._includeFilters.length;i++){
var _41=this._includeFilters[i];
if((_41.term===_3d)&&(_41.scheme===_3c)&&(_41.label===_3e)){
_3f=false;
break;
}
}
}
if(_3f){
this._includeFilters.push(dojox.atom.widget.FeedViewer.CategoryIncludeFilter(_3c,_3d,_3e));
}
}
},removeCategoryIncludeFilter:function(_42){
if(_42){
var _43=_42.scheme;
var _44=_42.term;
var _45=_42.label;
if(!_43){
_43=null;
}
if(!_44){
_43=null;
}
if(!_45){
_43=null;
}
var _46=[];
if(this._includeFilters&&this._includeFilters.length>0){
for(var i=0;i<this._includeFilters.length;i++){
var _48=this._includeFilters[i];
if(!((_48.term===_44)&&(_48.scheme===_43)&&(_48.label===_45))){
_46.push(_48);
}
}
this._includeFilters=_46;
}
}
},_handleEvent:function(_49){
if(_49.source!=this){
if(_49.action=="update"&&_49.entry){
var evt=_49;
if(!this.localSaveOnly){
this.atomIO.updateEntry(evt.entry,dojo.hitch(evt.source,evt.callback),null,true);
}
this._currentSelection._entryWidget.setTime(this._displayDateForEntry(evt.entry).toLocaleTimeString());
this._currentSelection._entryWidget.setTitle(evt.entry.title.value);
}else{
if(_49.action=="post"&&_49.entry){
if(!this.localSaveOnly){
this.atomIO.addEntry(_49.entry,this.url,dojo.hitch(this,this._addEntry));
}else{
this._addEntry(_49.entry);
}
}
}
}
},_addEntry:function(_4b){
this._feed.addEntry(_4b);
this.setFeed(this._feed);
dojo.publish(this.entrySelectionTopic,[{action:"set",source:this,feed:this._feed,entry:_4b}]);
},destroy:function(){
this.clear();
dojo.forEach(this._subscriptions,dojo.unsubscribe);
}});
dojo.declare("dojox.atom.widget.FeedViewerEntry",[dijit._Widget,dijit._Templated],{templateString:"<tr class=\"feedViewerEntry\" dojoAttachPoint=\"entryNode\" dojoAttachEvent=\"onclick:onClick\">\n    <td class=\"feedViewerEntryUpdated\" dojoAttachPoint=\"timeNode\">\n    </td>\n    <td>\n        <table border=\"0\" width=\"100%\" dojoAttachPoint=\"titleRow\">\n            <tr padding=\"0\" border=\"0\">\n                <td class=\"feedViewerEntryTitle\" dojoAttachPoint=\"titleNode\">\n                </td>\n                <td class=\"feedViewerEntryDelete\" align=\"right\">\n                    <span dojoAttachPoint=\"deleteButton\" dojoAttachEvent=\"onclick:deleteEntry\" class=\"feedViewerDeleteButton\" style=\"display:none;\">[delete]</span>\n                </td>\n            <tr>\n        </table>\n    </td>\n</tr>\n",entryNode:null,timeNode:null,deleteButton:null,entry:null,feed:null,postCreate:function(){
var _4c=dojo.i18n.getLocalization("dojox.atom.widget","FeedViewerEntry");
this.deleteButton.innerHTML=_4c.deleteButton;
},setTitle:function(_4d){
if(this.titleNode.lastChild){
this.titleNode.removeChild(this.titleNode.lastChild);
}
var _4e=document.createElement("div");
_4e.innerHTML=_4d;
this.titleNode.appendChild(_4e);
},setTime:function(_4f){
if(this.timeNode.lastChild){
this.timeNode.removeChild(this.timeNode.lastChild);
}
var _50=document.createTextNode(_4f);
this.timeNode.appendChild(_50);
},enableDelete:function(){
if(this.deleteButton!==null){
this.deleteButton.style.display="inline";
}
},disableDelete:function(){
if(this.deleteButton!==null){
this.deleteButton.style.display="none";
}
},deleteEntry:function(_51){
_51.preventDefault();
_51.stopPropagation();
this.feed.deleteEntry(this);
},onClick:function(e){
}});
dojo.declare("dojox.atom.widget.FeedViewerGrouping",[dijit._Widget,dijit._Templated],{templateString:"<tr dojoAttachPoint=\"groupingNode\" class=\"feedViewerGrouping\">\n\t<td colspan=\"2\" dojoAttachPoint=\"titleNode\" class=\"feedViewerGroupingTitle\">\n\t</td>\n</tr>\n",groupingNode:null,titleNode:null,setText:function(_53){
if(this.titleNode.lastChild){
this.titleNode.removeChild(this.titleNode.lastChild);
}
var _54=document.createTextNode(_53);
this.titleNode.appendChild(_54);
}});
dojo.declare("dojox.atom.widget.AtomEntryCategoryFilter",[dijit._Widget,dijit._Templated],{scheme:"",term:"",label:"",isFilter:true});
dojo.declare("dojox.atom.widget.FeedViewer.CategoryIncludeFilter",null,{constructor:function(_55,_56,_57){
this.scheme=_55;
this.term=_56;
this.label=_57;
},match:function(_58){
var _59=false;
if(_58!==null){
var _5a=_58.categories;
if(_5a!==null){
for(var i=0;i<_5a.length;i++){
var _5c=_5a[i];
if(this.scheme!==""){
if(this.scheme!==_5c.scheme){
break;
}
}
if(this.term!==""){
if(this.term!==_5c.term){
break;
}
}
if(this.label!==""){
if(this.label!==_5c.label){
break;
}
}
_59=true;
}
}
}
return _59;
}});
}
