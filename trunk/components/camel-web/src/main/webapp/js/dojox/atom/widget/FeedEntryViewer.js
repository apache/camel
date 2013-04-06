/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.atom.widget.FeedEntryViewer"]){
dojo._hasResource["dojox.atom.widget.FeedEntryViewer"]=true;
dojo.provide("dojox.atom.widget.FeedEntryViewer");
dojo.require("dojo.fx");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._Container");
dojo.require("dijit.layout.ContentPane");
dojo.require("dojox.atom.io.Connection");
dojo.requireLocalization("dojox.atom.widget","FeedEntryViewer",null,"ROOT,cs,de,es,fr,hu,it,ja,ko,pl,pt,ru,zh,zh-tw");
dojo.experimental("dojox.atom.widget.FeedEntryViewer");
dojo.declare("dojox.atom.widget.FeedEntryViewer",[dijit._Widget,dijit._Templated,dijit._Container],{entrySelectionTopic:"",_validEntryFields:{},displayEntrySections:"",_displayEntrySections:null,enableMenu:false,enableMenuFade:false,_optionButtonDisplayed:true,templateString:"<div class=\"feedEntryViewer\">\n    <table border=\"0\" width=\"100%\" class=\"feedEntryViewerMenuTable\" dojoAttachPoint=\"feedEntryViewerMenu\" style=\"display: none;\">\n        <tr width=\"100%\"  dojoAttachPoint=\"entryCheckBoxDisplayOptions\">\n            <td align=\"right\">\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"displayOptions\" dojoAttachEvent=\"onclick:_toggleOptions\"></span>\n            </td>\n        </tr>\n        <tr class=\"feedEntryViewerDisplayCheckbox\" dojoAttachPoint=\"entryCheckBoxRow\" width=\"100%\" style=\"display: none;\">\n            <td dojoAttachPoint=\"feedEntryCelltitle\">\n                <input type=\"checkbox\" name=\"title\" value=\"Title\" dojoAttachPoint=\"feedEntryCheckBoxTitle\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelTitle\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellauthors\">\n                <input type=\"checkbox\" name=\"authors\" value=\"Authors\" dojoAttachPoint=\"feedEntryCheckBoxAuthors\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelAuthors\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellcontributors\">\n                <input type=\"checkbox\" name=\"contributors\" value=\"Contributors\" dojoAttachPoint=\"feedEntryCheckBoxContributors\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelContributors\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellid\">\n                <input type=\"checkbox\" name=\"id\" value=\"Id\" dojoAttachPoint=\"feedEntryCheckBoxId\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelId\"></label>\n            </td>\n            <td rowspan=\"2\" align=\"right\">\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"close\" dojoAttachEvent=\"onclick:_toggleOptions\"></span>\n            </td>\n\t\t</tr>\n\t\t<tr class=\"feedEntryViewerDisplayCheckbox\" dojoAttachPoint=\"entryCheckBoxRow2\" width=\"100%\" style=\"display: none;\">\n            <td dojoAttachPoint=\"feedEntryCellupdated\">\n                <input type=\"checkbox\" name=\"updated\" value=\"Updated\" dojoAttachPoint=\"feedEntryCheckBoxUpdated\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelUpdated\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellsummary\">\n                <input type=\"checkbox\" name=\"summary\" value=\"Summary\" dojoAttachPoint=\"feedEntryCheckBoxSummary\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelSummary\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellcontent\">\n                <input type=\"checkbox\" name=\"content\" value=\"Content\" dojoAttachPoint=\"feedEntryCheckBoxContent\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelContent\"></label>\n            </td>\n        </tr>\n    </table>\n    \n    <table class=\"feedEntryViewerContainer\" border=\"0\" width=\"100%\">\n        <tr class=\"feedEntryViewerTitle\" dojoAttachPoint=\"entryTitleRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryTitleHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryTitleNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n\n        <tr class=\"feedEntryViewerAuthor\" dojoAttachPoint=\"entryAuthorRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryAuthorHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryAuthorNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n\n        <tr class=\"feedEntryViewerContributor\" dojoAttachPoint=\"entryContributorRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryContributorHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryContributorNode\" class=\"feedEntryViewerContributorNames\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n        \n        <tr class=\"feedEntryViewerId\" dojoAttachPoint=\"entryIdRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryIdHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryIdNode\" class=\"feedEntryViewerIdText\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    \n        <tr class=\"feedEntryViewerUpdated\" dojoAttachPoint=\"entryUpdatedRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryUpdatedHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryUpdatedNode\" class=\"feedEntryViewerUpdatedText\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    \n        <tr class=\"feedEntryViewerSummary\" dojoAttachPoint=\"entrySummaryRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entrySummaryHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entrySummaryNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    \n        <tr class=\"feedEntryViewerContent\" dojoAttachPoint=\"entryContentRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryContentHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryContentNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    </table>\n</div>\n",_entry:null,_feed:null,_editMode:false,postCreate:function(){
if(this.entrySelectionTopic!==""){
this._subscriptions=[dojo.subscribe(this.entrySelectionTopic,this,"_handleEvent")];
}
var _1=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
this.displayOptions.innerHTML=_1.displayOptions;
this.feedEntryCheckBoxLabelTitle.innerHTML=_1.title;
this.feedEntryCheckBoxLabelAuthors.innerHTML=_1.authors;
this.feedEntryCheckBoxLabelContributors.innerHTML=_1.contributors;
this.feedEntryCheckBoxLabelId.innerHTML=_1.id;
this.close.innerHTML=_1.close;
this.feedEntryCheckBoxLabelUpdated.innerHTML=_1.updated;
this.feedEntryCheckBoxLabelSummary.innerHTML=_1.summary;
this.feedEntryCheckBoxLabelContent.innerHTML=_1.content;
},startup:function(){
if(this.displayEntrySections===""){
this._displayEntrySections=["title","authors","contributors","summary","content","id","updated"];
}else{
this._displayEntrySections=this.displayEntrySections.split(",");
}
this._setDisplaySectionsCheckboxes();
if(this.enableMenu){
dojo.style(this.feedEntryViewerMenu,"display","");
if(this.entryCheckBoxRow&&this.entryCheckBoxRow2){
if(this.enableMenuFade){
dojo.fadeOut({node:this.entryCheckBoxRow,duration:250}).play();
dojo.fadeOut({node:this.entryCheckBoxRow2,duration:250}).play();
}
}
}
},clear:function(){
this.destroyDescendants();
this._entry=null;
this._feed=null;
this.clearNodes();
},clearNodes:function(){
dojo.forEach(["entryTitleRow","entryAuthorRow","entryContributorRow","entrySummaryRow","entryContentRow","entryIdRow","entryUpdatedRow"],function(_2){
dojo.style(this[_2],"display","none");
},this);
dojo.forEach(["entryTitleNode","entryTitleHeader","entryAuthorHeader","entryContributorHeader","entryContributorNode","entrySummaryHeader","entrySummaryNode","entryContentHeader","entryContentNode","entryIdNode","entryIdHeader","entryUpdatedHeader","entryUpdatedNode"],function(_3){
while(this[_3].firstChild){
dojo.destroy(this[_3].firstChild);
}
},this);
},setEntry:function(_4,_5,_6){
this.clear();
this._validEntryFields={};
this._entry=_4;
this._feed=_5;
if(_4!==null){
if(this.entryTitleHeader){
this.setTitleHeader(this.entryTitleHeader,_4);
}
if(this.entryTitleNode){
this.setTitle(this.entryTitleNode,this._editMode,_4);
}
if(this.entryAuthorHeader){
this.setAuthorsHeader(this.entryAuthorHeader,_4);
}
if(this.entryAuthorNode){
this.setAuthors(this.entryAuthorNode,this._editMode,_4);
}
if(this.entryContributorHeader){
this.setContributorsHeader(this.entryContributorHeader,_4);
}
if(this.entryContributorNode){
this.setContributors(this.entryContributorNode,this._editMode,_4);
}
if(this.entryIdHeader){
this.setIdHeader(this.entryIdHeader,_4);
}
if(this.entryIdNode){
this.setId(this.entryIdNode,this._editMode,_4);
}
if(this.entryUpdatedHeader){
this.setUpdatedHeader(this.entryUpdatedHeader,_4);
}
if(this.entryUpdatedNode){
this.setUpdated(this.entryUpdatedNode,this._editMode,_4);
}
if(this.entrySummaryHeader){
this.setSummaryHeader(this.entrySummaryHeader,_4);
}
if(this.entrySummaryNode){
this.setSummary(this.entrySummaryNode,this._editMode,_4);
}
if(this.entryContentHeader){
this.setContentHeader(this.entryContentHeader,_4);
}
if(this.entryContentNode){
this.setContent(this.entryContentNode,this._editMode,_4);
}
}
this._displaySections();
},setTitleHeader:function(_7,_8){
if(_8.title&&_8.title.value&&_8.title.value!==null){
var _9=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _a=new dojox.atom.widget.EntryHeader({title:_9.title});
_7.appendChild(_a.domNode);
}
},setTitle:function(_b,_c,_d){
if(_d.title&&_d.title.value&&_d.title.value!==null){
if(_d.title.type=="text"){
var _e=document.createTextNode(_d.title.value);
_b.appendChild(_e);
}else{
var _f=document.createElement("span");
var _10=new dijit.layout.ContentPane({refreshOnShow:true,executeScripts:false},_f);
_10.attr("content",_d.title.value);
_b.appendChild(_10.domNode);
}
this.setFieldValidity("title",true);
}
},setAuthorsHeader:function(_11,_12){
if(_12.authors&&_12.authors.length>0){
var _13=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _14=new dojox.atom.widget.EntryHeader({title:_13.authors});
_11.appendChild(_14.domNode);
}
},setAuthors:function(_15,_16,_17){
if(_17.authors&&_17.authors.length>0){
for(var i in _17.authors){
if(_17.authors[i].name){
var _19=_15;
if(_17.authors[i].uri){
var _1a=document.createElement("a");
_19.appendChild(_1a);
_1a.href=_17.authors[i].uri;
_19=_1a;
}
var _1b=_17.authors[i].name;
if(_17.authors[i].email){
_1b=_1b+" ("+_17.authors[i].email+")";
}
var _1c=document.createTextNode(_1b);
_19.appendChild(_1c);
var _1d=document.createElement("br");
_15.appendChild(_1d);
this.setFieldValidity("authors",true);
}
}
}
},setContributorsHeader:function(_1e,_1f){
if(_1f.contributors&&_1f.contributors.length>0){
var _20=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _21=new dojox.atom.widget.EntryHeader({title:_20.contributors});
_1e.appendChild(_21.domNode);
}
},setContributors:function(_22,_23,_24){
if(_24.contributors&&_24.contributors.length>0){
for(var i in _24.contributors){
var _26=document.createTextNode(_24.contributors[i].name);
_22.appendChild(_26);
var _27=document.createElement("br");
_22.appendChild(_27);
this.setFieldValidity("contributors",true);
}
}
},setIdHeader:function(_28,_29){
if(_29.id&&_29.id!==null){
var _2a=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _2b=new dojox.atom.widget.EntryHeader({title:_2a.id});
_28.appendChild(_2b.domNode);
}
},setId:function(_2c,_2d,_2e){
if(_2e.id&&_2e.id!==null){
var _2f=document.createTextNode(_2e.id);
_2c.appendChild(_2f);
this.setFieldValidity("id",true);
}
},setUpdatedHeader:function(_30,_31){
if(_31.updated&&_31.updated!==null){
var _32=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _33=new dojox.atom.widget.EntryHeader({title:_32.updated});
_30.appendChild(_33.domNode);
}
},setUpdated:function(_34,_35,_36){
if(_36.updated&&_36.updated!==null){
var _37=document.createTextNode(_36.updated);
_34.appendChild(_37);
this.setFieldValidity("updated",true);
}
},setSummaryHeader:function(_38,_39){
if(_39.summary&&_39.summary.value&&_39.summary.value!==null){
var _3a=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _3b=new dojox.atom.widget.EntryHeader({title:_3a.summary});
_38.appendChild(_3b.domNode);
}
},setSummary:function(_3c,_3d,_3e){
if(_3e.summary&&_3e.summary.value&&_3e.summary.value!==null){
var _3f=document.createElement("span");
var _40=new dijit.layout.ContentPane({refreshOnShow:true,executeScripts:false},_3f);
_40.attr("content",_3e.summary.value);
_3c.appendChild(_40.domNode);
this.setFieldValidity("summary",true);
}
},setContentHeader:function(_41,_42){
if(_42.content&&_42.content.value&&_42.content.value!==null){
var _43=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _44=new dojox.atom.widget.EntryHeader({title:_43.content});
_41.appendChild(_44.domNode);
}
},setContent:function(_45,_46,_47){
if(_47.content&&_47.content.value&&_47.content.value!==null){
var _48=document.createElement("span");
var _49=new dijit.layout.ContentPane({refreshOnShow:true,executeScripts:false},_48);
_49.attr("content",_47.content.value);
_45.appendChild(_49.domNode);
this.setFieldValidity("content",true);
}
},_displaySections:function(){
dojo.style(this.entryTitleRow,"display","none");
dojo.style(this.entryAuthorRow,"display","none");
dojo.style(this.entryContributorRow,"display","none");
dojo.style(this.entrySummaryRow,"display","none");
dojo.style(this.entryContentRow,"display","none");
dojo.style(this.entryIdRow,"display","none");
dojo.style(this.entryUpdatedRow,"display","none");
for(var i in this._displayEntrySections){
var _4b=this._displayEntrySections[i].toLowerCase();
if(_4b==="title"&&this.isFieldValid("title")){
dojo.style(this.entryTitleRow,"display","");
}
if(_4b==="authors"&&this.isFieldValid("authors")){
dojo.style(this.entryAuthorRow,"display","");
}
if(_4b==="contributors"&&this.isFieldValid("contributors")){
dojo.style(this.entryContributorRow,"display","");
}
if(_4b==="summary"&&this.isFieldValid("summary")){
dojo.style(this.entrySummaryRow,"display","");
}
if(_4b==="content"&&this.isFieldValid("content")){
dojo.style(this.entryContentRow,"display","");
}
if(_4b==="id"&&this.isFieldValid("id")){
dojo.style(this.entryIdRow,"display","");
}
if(_4b==="updated"&&this.isFieldValid("updated")){
dojo.style(this.entryUpdatedRow,"display","");
}
}
},setDisplaySections:function(_4c){
if(_4c!==null){
this._displayEntrySections=_4c;
this._displaySections();
}else{
this._displayEntrySections=["title","authors","contributors","summary","content","id","updated"];
}
},_setDisplaySectionsCheckboxes:function(){
var _4d=["title","authors","contributors","summary","content","id","updated"];
for(var i in _4d){
if(dojo.indexOf(this._displayEntrySections,_4d[i])==-1){
dojo.style(this["feedEntryCell"+_4d[i]],"display","none");
}else{
this["feedEntryCheckBox"+_4d[i].substring(0,1).toUpperCase()+_4d[i].substring(1)].checked=true;
}
}
},_readDisplaySections:function(){
var _4f=[];
if(this.feedEntryCheckBoxTitle.checked){
_4f.push("title");
}
if(this.feedEntryCheckBoxAuthors.checked){
_4f.push("authors");
}
if(this.feedEntryCheckBoxContributors.checked){
_4f.push("contributors");
}
if(this.feedEntryCheckBoxSummary.checked){
_4f.push("summary");
}
if(this.feedEntryCheckBoxContent.checked){
_4f.push("content");
}
if(this.feedEntryCheckBoxId.checked){
_4f.push("id");
}
if(this.feedEntryCheckBoxUpdated.checked){
_4f.push("updated");
}
this._displayEntrySections=_4f;
},_toggleCheckbox:function(_50){
if(_50.checked){
_50.checked=false;
}else{
_50.checked=true;
}
this._readDisplaySections();
this._displaySections();
},_toggleOptions:function(_51){
if(this.enableMenu){
var _52=null;
var _53;
var _54;
if(this._optionButtonDisplayed){
if(this.enableMenuFade){
_53=dojo.fadeOut({node:this.entryCheckBoxDisplayOptions,duration:250});
dojo.connect(_53,"onEnd",this,function(){
dojo.style(this.entryCheckBoxDisplayOptions,"display","none");
dojo.style(this.entryCheckBoxRow,"display","");
dojo.style(this.entryCheckBoxRow2,"display","");
dojo.fadeIn({node:this.entryCheckBoxRow,duration:250}).play();
dojo.fadeIn({node:this.entryCheckBoxRow2,duration:250}).play();
});
_53.play();
}else{
dojo.style(this.entryCheckBoxDisplayOptions,"display","none");
dojo.style(this.entryCheckBoxRow,"display","");
dojo.style(this.entryCheckBoxRow2,"display","");
}
this._optionButtonDisplayed=false;
}else{
if(this.enableMenuFade){
_53=dojo.fadeOut({node:this.entryCheckBoxRow,duration:250});
_54=dojo.fadeOut({node:this.entryCheckBoxRow2,duration:250});
dojo.connect(_53,"onEnd",this,function(){
dojo.style(this.entryCheckBoxRow,"display","none");
dojo.style(this.entryCheckBoxRow2,"display","none");
dojo.style(this.entryCheckBoxDisplayOptions,"display","");
dojo.fadeIn({node:this.entryCheckBoxDisplayOptions,duration:250}).play();
});
_53.play();
_54.play();
}else{
dojo.style(this.entryCheckBoxRow,"display","none");
dojo.style(this.entryCheckBoxRow2,"display","none");
dojo.style(this.entryCheckBoxDisplayOptions,"display","");
}
this._optionButtonDisplayed=true;
}
}
},_handleEvent:function(_55){
if(_55.source!=this){
if(_55.action=="set"&&_55.entry){
this.setEntry(_55.entry,_55.feed);
}else{
if(_55.action=="delete"&&_55.entry&&_55.entry==this._entry){
this.clear();
}
}
}
},setFieldValidity:function(_56,_57){
if(_56){
var _58=_56.toLowerCase();
this._validEntryFields[_56]=_57;
}
},isFieldValid:function(_59){
return this._validEntryFields[_59.toLowerCase()];
},getEntry:function(){
return this._entry;
},getFeed:function(){
return this._feed;
},destroy:function(){
this.clear();
dojo.forEach(this._subscriptions,dojo.unsubscribe);
}});
dojo.declare("dojox.atom.widget.EntryHeader",[dijit._Widget,dijit._Templated,dijit._Container],{title:"",templateString:"<span dojoAttachPoint=\"entryHeaderNode\" class=\"entryHeaderNode\"></span>\n",postCreate:function(){
this.setListHeader();
},setListHeader:function(_5a){
this.clear();
if(_5a){
this.title=_5a;
}
var _5b=document.createTextNode(this.title);
this.entryHeaderNode.appendChild(_5b);
},clear:function(){
this.destroyDescendants();
if(this.entryHeaderNode){
for(var i=0;i<this.entryHeaderNode.childNodes.length;i++){
this.entryHeaderNode.removeChild(this.entryHeaderNode.childNodes[i]);
}
}
},destroy:function(){
this.clear();
}});
}
