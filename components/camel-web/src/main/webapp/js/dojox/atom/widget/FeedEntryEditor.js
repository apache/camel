/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.atom.widget.FeedEntryEditor"]){
dojo._hasResource["dojox.atom.widget.FeedEntryEditor"]=true;
dojo.provide("dojox.atom.widget.FeedEntryEditor");
dojo.require("dojox.atom.widget.FeedEntryViewer");
dojo.require("dijit._Widget");
dojo.require("dijit._Templated");
dojo.require("dijit._Container");
dojo.require("dijit.Editor");
dojo.require("dijit.form.TextBox");
dojo.require("dijit.form.SimpleTextarea");
dojo.requireLocalization("dojox.atom.widget","FeedEntryEditor",null,"ROOT,cs,de,es,fr,hu,it,ja,ko,pl,pt,ru,zh,zh-tw");
dojo.requireLocalization("dojox.atom.widget","PeopleEditor",null,"ROOT,cs,de,es,fr,hu,it,ja,ko,pl,pt,ru,zh,zh-tw");
dojo.experimental("dojox.atom.widget.FeedEntryEditor");
dojo.declare("dojox.atom.widget.FeedEntryEditor",dojox.atom.widget.FeedEntryViewer,{_contentEditor:null,_oldContent:null,_setObject:null,enableEdit:false,_contentEditorCreator:null,_editors:{},entryNewButton:null,_editable:false,templateString:"<div class=\"feedEntryViewer\">\n    <table border=\"0\" width=\"100%\" class=\"feedEntryViewerMenuTable\" dojoAttachPoint=\"feedEntryViewerMenu\" style=\"display: none;\">\n        <tr width=\"100%\"  dojoAttachPoint=\"entryCheckBoxDisplayOptions\">\n        \t<td align=\"left\" dojoAttachPoint=\"entryNewButton\">\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"doNew\" dojoAttachEvent=\"onclick:_toggleNew\"></span>\n        \t</td>\n            <td align=\"left\" dojoAttachPoint=\"entryEditButton\" style=\"display: none;\">\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"edit\" dojoAttachEvent=\"onclick:_toggleEdit\"></span>\n            </td>\n            <td align=\"left\" dojoAttachPoint=\"entrySaveCancelButtons\" style=\"display: none;\">\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"save\" dojoAttachEvent=\"onclick:saveEdits\"></span>\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"cancel\" dojoAttachEvent=\"onclick:cancelEdits\"></span>\n            </td>\n            <td align=\"right\">\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"displayOptions\" dojoAttachEvent=\"onclick:_toggleOptions\"></span>\n            </td>\n        </tr>\n        <tr class=\"feedEntryViewerDisplayCheckbox\" dojoAttachPoint=\"entryCheckBoxRow\" width=\"100%\" style=\"display: none;\">\n            <td dojoAttachPoint=\"feedEntryCelltitle\">\n                <input type=\"checkbox\" name=\"title\" value=\"Title\" dojoAttachPoint=\"feedEntryCheckBoxTitle\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelTitle\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellauthors\">\n                <input type=\"checkbox\" name=\"authors\" value=\"Authors\" dojoAttachPoint=\"feedEntryCheckBoxAuthors\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelAuthors\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellcontributors\">\n                <input type=\"checkbox\" name=\"contributors\" value=\"Contributors\" dojoAttachPoint=\"feedEntryCheckBoxContributors\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelContributors\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellid\">\n                <input type=\"checkbox\" name=\"id\" value=\"Id\" dojoAttachPoint=\"feedEntryCheckBoxId\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelId\"></label>\n            </td>\n            <td rowspan=\"2\" align=\"right\">\n                <span class=\"feedEntryViewerMenu\" dojoAttachPoint=\"close\" dojoAttachEvent=\"onclick:_toggleOptions\"></span>\n            </td>\n\t\t</tr>\n\t\t<tr class=\"feedEntryViewerDisplayCheckbox\" dojoAttachPoint=\"entryCheckBoxRow2\" width=\"100%\" style=\"display: none;\">\n            <td dojoAttachPoint=\"feedEntryCellupdated\">\n                <input type=\"checkbox\" name=\"updated\" value=\"Updated\" dojoAttachPoint=\"feedEntryCheckBoxUpdated\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelUpdated\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellsummary\">\n                <input type=\"checkbox\" name=\"summary\" value=\"Summary\" dojoAttachPoint=\"feedEntryCheckBoxSummary\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelSummary\"></label>\n            </td>\n            <td dojoAttachPoint=\"feedEntryCellcontent\">\n                <input type=\"checkbox\" name=\"content\" value=\"Content\" dojoAttachPoint=\"feedEntryCheckBoxContent\" dojoAttachEvent=\"onclick:_toggleCheckbox\"/>\n\t\t\t\t<label for=\"title\" dojoAttachPoint=\"feedEntryCheckBoxLabelContent\"></label>\n            </td>\n        </tr>\n    </table>\n    \n    <table class=\"feedEntryViewerContainer\" border=\"0\" width=\"100%\">\n        <tr class=\"feedEntryViewerTitle\" dojoAttachPoint=\"entryTitleRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryTitleHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td>\n                        \t<select dojoAttachPoint=\"entryTitleSelect\" dojoAttachEvent=\"onchange:_switchEditor\" style=\"display: none\">\n                        \t\t<option value=\"text\">Text</option>\n\t\t\t\t\t\t\t\t<option value=\"html\">HTML</option>\n\t\t\t\t\t\t\t\t<option value=\"xhtml\">XHTML</option>\n                        \t</select>\n                        </td>\n                    </tr>\n                    <tr>\n                        <td colspan=\"2\" dojoAttachPoint=\"entryTitleNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n\n        <tr class=\"feedEntryViewerAuthor\" dojoAttachPoint=\"entryAuthorRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryAuthorHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryAuthorNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n\n        <tr class=\"feedEntryViewerContributor\" dojoAttachPoint=\"entryContributorRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryContributorHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryContributorNode\" class=\"feedEntryViewerContributorNames\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n        \n        <tr class=\"feedEntryViewerId\" dojoAttachPoint=\"entryIdRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryIdHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryIdNode\" class=\"feedEntryViewerIdText\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    \n        <tr class=\"feedEntryViewerUpdated\" dojoAttachPoint=\"entryUpdatedRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryUpdatedHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryUpdatedNode\" class=\"feedEntryViewerUpdatedText\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    \n        <tr class=\"feedEntryViewerSummary\" dojoAttachPoint=\"entrySummaryRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\" colspan=\"2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entrySummaryHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td>\n                        \t<select dojoAttachPoint=\"entrySummarySelect\" dojoAttachEvent=\"onchange:_switchEditor\" style=\"display: none\">\n                        \t\t<option value=\"text\">Text</option>\n\t\t\t\t\t\t\t\t<option value=\"html\">HTML</option>\n\t\t\t\t\t\t\t\t<option value=\"xhtml\">XHTML</option>\n                        \t</select>\n                        </td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entrySummaryNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    \n        <tr class=\"feedEntryViewerContent\" dojoAttachPoint=\"entryContentRow\" style=\"display: none;\">\n            <td>\n                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n                    <tr class=\"graphic-tab-lgray\">\n\t\t\t\t\t\t<td class=\"lp2\">\n\t\t\t\t\t\t\t<span class=\"lp\" dojoAttachPoint=\"entryContentHeader\"></span>\n\t\t\t\t\t\t</td>\n                    </tr>\n                    <tr>\n                        <td>\n                        \t<select dojoAttachPoint=\"entryContentSelect\" dojoAttachEvent=\"onchange:_switchEditor\" style=\"display: none\">\n                        \t\t<option value=\"text\">Text</option>\n\t\t\t\t\t\t\t\t<option value=\"html\">HTML</option>\n\t\t\t\t\t\t\t\t<option value=\"xhtml\">XHTML</option>\n                        \t</select>\n                        </td>\n                    </tr>\n                    <tr>\n                        <td dojoAttachPoint=\"entryContentNode\">\n                        </td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    </table>\n</div>\n",postCreate:function(){
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
_1=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryEditor");
this.doNew.innerHTML=_1.doNew;
this.edit.innerHTML=_1.edit;
this.save.innerHTML=_1.save;
this.cancel.innerHTML=_1.cancel;
},setEntry:function(_2,_3,_4){
if(this._entry!==_2){
this._editMode=false;
_4=false;
}else{
_4=true;
}
dojox.atom.widget.FeedEntryEditor.superclass.setEntry.call(this,_2,_3);
this._editable=this._isEditable(_2);
if(!_4&&!this._editable){
dojo.style(this.entryEditButton,"display","none");
dojo.style(this.entrySaveCancelButtons,"display","none");
}
if(this._editable&&this.enableEdit){
if(!_4){
dojo.style(this.entryEditButton,"display","");
if(this.enableMenuFade&&this.entrySaveCancelButton){
dojo.fadeOut({node:this.entrySaveCancelButton,duration:250}).play();
}
}
}
},_toggleEdit:function(){
if(this._editable&&this.enableEdit){
dojo.style(this.entryEditButton,"display","none");
dojo.style(this.entrySaveCancelButtons,"display","");
this._editMode=true;
this.setEntry(this._entry,this._feed,true);
}
},_handleEvent:function(_5){
if(_5.source!=this&&_5.action=="delete"&&_5.entry&&_5.entry==this._entry){
dojo.style(this.entryEditButton,"display","none");
}
dojox.atom.widget.FeedEntryEditor.superclass._handleEvent.call(this,_5);
},_isEditable:function(_6){
var _7=false;
if(_6&&_6!==null&&_6.links&&_6.links!==null){
for(var x in _6.links){
if(_6.links[x].rel&&_6.links[x].rel=="edit"){
_7=true;
break;
}
}
}
return _7;
},setTitle:function(_9,_a,_b){
if(!_a){
dojox.atom.widget.FeedEntryEditor.superclass.setTitle.call(this,_9,_a,_b);
if(_b.title&&_b.title.value&&_b.title.value!==null){
this.setFieldValidity("title",true);
}
}else{
if(_b.title&&_b.title.value&&_b.title.value!==null){
if(!this._toLoad){
this._toLoad=[];
}
this.entryTitleSelect.value=_b.title.type;
var _c=this._createEditor(_9,_b.title,true,_b.title.type==="html"||_b.title.type==="xhtml");
_c.name="title";
this._toLoad.push(_c);
this.setFieldValidity("titleedit",true);
this.setFieldValidity("title",true);
}
}
},setAuthors:function(_d,_e,_f){
if(!_e){
dojox.atom.widget.FeedEntryEditor.superclass.setAuthors.call(this,_d,_e,_f);
if(_f.authors&&_f.authors.length>0){
this.setFieldValidity("authors",true);
}
}else{
if(_f.authors&&_f.authors.length>0){
this._editors.authors=this._createPeopleEditor(this.entryAuthorNode,{data:_f.authors,name:"Author"});
this.setFieldValidity("authors",true);
}
}
},setContributors:function(_10,_11,_12){
if(!_11){
dojox.atom.widget.FeedEntryEditor.superclass.setContributors.call(this,_10,_11,_12);
if(_12.contributors&&_12.contributors.length>0){
this.setFieldValidity("contributors",true);
}
}else{
if(_12.contributors&&_12.contributors.length>0){
this._editors.contributors=this._createPeopleEditor(this.entryContributorNode,{data:_12.contributors,name:"Contributor"});
this.setFieldValidity("contributors",true);
}
}
},setId:function(_13,_14,_15){
if(!_14){
dojox.atom.widget.FeedEntryEditor.superclass.setId.call(this,_13,_14,_15);
if(_15.id&&_15.id!==null){
this.setFieldValidity("id",true);
}
}else{
if(_15.id&&_15.id!==null){
this._editors.id=this._createEditor(_13,_15.id);
this.setFieldValidity("id",true);
}
}
},setUpdated:function(_16,_17,_18){
if(!_17){
dojox.atom.widget.FeedEntryEditor.superclass.setUpdated.call(this,_16,_17,_18);
if(_18.updated&&_18.updated!==null){
this.setFieldValidity("updated",true);
}
}else{
if(_18.updated&&_18.updated!==null){
this._editors.updated=this._createEditor(_16,_18.updated);
this.setFieldValidity("updated",true);
}
}
},setSummary:function(_19,_1a,_1b){
if(!_1a){
dojox.atom.widget.FeedEntryEditor.superclass.setSummary.call(this,_19,_1a,_1b);
if(_1b.summary&&_1b.summary.value&&_1b.summary.value!==null){
this.setFieldValidity("summary",true);
}
}else{
if(_1b.summary&&_1b.summary.value&&_1b.summary.value!==null){
if(!this._toLoad){
this._toLoad=[];
}
this.entrySummarySelect.value=_1b.summary.type;
var _1c=this._createEditor(_19,_1b.summary,true,_1b.summary.type==="html"||_1b.summary.type==="xhtml");
_1c.name="summary";
this._toLoad.push(_1c);
this.setFieldValidity("summaryedit",true);
this.setFieldValidity("summary",true);
}
}
},setContent:function(_1d,_1e,_1f){
if(!_1e){
dojox.atom.widget.FeedEntryEditor.superclass.setContent.call(this,_1d,_1e,_1f);
if(_1f.content&&_1f.content.value&&_1f.content.value!==null){
this.setFieldValidity("content",true);
}
}else{
if(_1f.content&&_1f.content.value&&_1f.content.value!==null){
if(!this._toLoad){
this._toLoad=[];
}
this.entryContentSelect.value=_1f.content.type;
var _20=this._createEditor(_1d,_1f.content,true,_1f.content.type==="html"||_1f.content.type==="xhtml");
_20.name="content";
this._toLoad.push(_20);
this.setFieldValidity("contentedit",true);
this.setFieldValidity("content",true);
}
}
},_createEditor:function(_21,_22,_23,rte){
var _25;
var box;
if(!_22){
if(rte){
return {anchorNode:_21,entryValue:"",editor:null,generateEditor:function(){
var _27=document.createElement("div");
_27.innerHTML=this.entryValue;
this.anchorNode.appendChild(_27);
var _28=new dijit.Editor({},_27);
this.editor=_28;
return _28;
}};
}
if(_23){
_25=document.createElement("textarea");
_21.appendChild(_25);
dojo.style(_25,"width","90%");
box=new dijit.form.SimpleTextarea({},_25);
}else{
_25=document.createElement("input");
_21.appendChild(_25);
dojo.style(_25,"width","95%");
box=new dijit.form.TextBox({},_25);
}
box.attr("value","");
return box;
}
var _29;
if(_22.value!==undefined){
_29=_22.value;
}else{
if(_22.attr){
_29=_22.attr("value");
}else{
_29=_22;
}
}
if(rte){
if(_29.indexOf("<")!=-1){
_29=_29.replace(/</g,"&lt;");
}
return {anchorNode:_21,entryValue:_29,editor:null,generateEditor:function(){
var _2a=document.createElement("div");
_2a.innerHTML=this.entryValue;
this.anchorNode.appendChild(_2a);
var _2b=new dijit.Editor({},_2a);
this.editor=_2b;
return _2b;
}};
}
if(_23){
_25=document.createElement("textarea");
_21.appendChild(_25);
dojo.style(_25,"width","90%");
box=new dijit.form.SimpleTextarea({},_25);
}else{
_25=document.createElement("input");
_21.appendChild(_25);
dojo.style(_25,"width","95%");
box=new dijit.form.TextBox({},_25);
}
box.attr("value",_29);
return box;
},_switchEditor:function(_2c){
var _2d=null;
var _2e=null;
var _2f=null;
if(dojo.isIE){
_2e=_2c.srcElement;
}else{
_2e=_2c.target;
}
if(_2e===this.entryTitleSelect){
_2f=this.entryTitleNode;
_2d="title";
}else{
if(_2e===this.entrySummarySelect){
_2f=this.entrySummaryNode;
_2d="summary";
}else{
_2f=this.entryContentNode;
_2d="content";
}
}
var _30=this._editors[_2d];
var _31;
var _32;
if(_2e.value==="text"){
if(_30.declaredClass==="dijit.Editor"){
_32=_30.attr("value",false);
_30.close(false,true);
_30.destroy();
while(_2f.firstChild){
dojo.destroy(_2f.firstChild);
}
_31=this._createEditor(_2f,{value:_32},true,false);
this._editors[_2d]=_31;
}
}else{
if(_30.declaredClass!="dijit.Editor"){
_32=_30.attr("value");
_30.destroy();
while(_2f.firstChild){
dojo.destroy(_2f.firstChild);
}
_31=this._createEditor(_2f,{value:_32},true,true);
_31=dojo.hitch(_31,_31.generateEditor)();
this._editors[_2d]=_31;
}
}
},_createPeopleEditor:function(_33,_34){
var _35=document.createElement("div");
_33.appendChild(_35);
return new dojox.atom.widget.PeopleEditor(_34,_35);
},saveEdits:function(){
dojo.style(this.entrySaveCancelButtons,"display","none");
dojo.style(this.entryEditButton,"display","");
dojo.style(this.entryNewButton,"display","");
var _36=false;
var _37;
var i;
var _39;
var _3a;
var _3b;
var _3c;
if(!this._new){
_3a=this.getEntry();
if(this._editors.title&&(this._editors.title.attr("value")!=_3a.title.value||this.entryTitleSelect.value!=_3a.title.type)){
_37=this._editors.title.attr("value");
if(this.entryTitleSelect.value==="xhtml"){
_37=this._enforceXhtml(_37);
if(_37.indexOf("<div xmlns=\"http://www.w3.org/1999/xhtml\">")!==0){
_37="<div xmlns=\"http://www.w3.org/1999/xhtml\">"+_37+"</div>";
}
}
_3a.title=new dojox.atom.io.model.Content("title",_37,null,this.entryTitleSelect.value);
_36=true;
}
if(this._editors.id.attr("value")!=_3a.id){
_3a.id=this._editors.id.attr("value");
_36=true;
}
if(this._editors.summary&&(this._editors.summary.attr("value")!=_3a.summary.value||this.entrySummarySelect.value!=_3a.summary.type)){
_37=this._editors.summary.attr("value");
if(this.entrySummarySelect.value==="xhtml"){
_37=this._enforceXhtml(_37);
if(_37.indexOf("<div xmlns=\"http://www.w3.org/1999/xhtml\">")!==0){
_37="<div xmlns=\"http://www.w3.org/1999/xhtml\">"+_37+"</div>";
}
}
_3a.summary=new dojox.atom.io.model.Content("summary",_37,null,this.entrySummarySelect.value);
_36=true;
}
if(this._editors.content&&(this._editors.content.attr("value")!=_3a.content.value||this.entryContentSelect.value!=_3a.content.type)){
_37=this._editors.content.attr("value");
if(this.entryContentSelect.value==="xhtml"){
_37=this._enforceXhtml(_37);
if(_37.indexOf("<div xmlns=\"http://www.w3.org/1999/xhtml\">")!==0){
_37="<div xmlns=\"http://www.w3.org/1999/xhtml\">"+_37+"</div>";
}
}
_3a.content=new dojox.atom.io.model.Content("content",_37,null,this.entryContentSelect.value);
_36=true;
}
if(this._editors.authors){
if(_36){
_3a.authors=[];
_3b=this._editors.authors.getValues();
for(i in _3b){
if(_3b[i].name||_3b[i].email||_3b[i].uri){
_3a.addAuthor(_3b[i].name,_3b[i].email,_3b[i].uri);
}
}
}else{
var _3d=_3a.authors;
var _3e=function(_3f,_40,uri){
for(i in _3d){
if(_3d[i].name===_3f&&_3d[i].email===_40&&_3d[i].uri===uri){
return true;
}
}
return false;
};
_3b=this._editors.authors.getValues();
_39=false;
for(i in _3b){
if(!_3e(_3b[i].name,_3b[i].email,_3b[i].uri)){
_39=true;
break;
}
}
if(_39){
_3a.authors=[];
for(i in _3b){
if(_3b[i].name||_3b[i].email||_3b[i].uri){
_3a.addAuthor(_3b[i].name,_3b[i].email,_3b[i].uri);
}
}
_36=true;
}
}
}
if(this._editors.contributors){
if(_36){
_3a.contributors=[];
_3c=this._editors.contributors.getValues();
for(i in _3c){
if(_3c[i].name||_3c[i].email||_3c[i].uri){
_3a.addAuthor(_3c[i].name,_3c[i].email,_3c[i].uri);
}
}
}else{
var _42=_3a.contributors;
var _43=function(_44,_45,uri){
for(i in _42){
if(_42[i].name===_44&&_42[i].email===_45&&_42[i].uri===uri){
return true;
}
}
return false;
};
_3c=this._editors.contributors.getValues();
_39=false;
for(i in _3c){
if(_43(_3c[i].name,_3c[i].email,_3c[i].uri)){
_39=true;
break;
}
}
if(_39){
_3a.contributors=[];
for(i in _3c){
if(_3c[i].name||_3c[i].email||_3c[i].uri){
_3a.addContributor(_3c[i].name,_3c[i].email,_3c[i].uri);
}
}
_36=true;
}
}
}
if(_36){
dojo.publish(this.entrySelectionTopic,[{action:"update",source:this,entry:_3a,callback:this._handleSave}]);
}
}else{
this._new=false;
_3a=new dojox.atom.io.model.Entry();
_37=this._editors.title.attr("value");
if(this.entryTitleSelect.value==="xhtml"){
_37=this._enforceXhtml(_37);
_37="<div xmlns=\"http://www.w3.org/1999/xhtml\">"+_37+"</div>";
}
_3a.setTitle(_37,this.entryTitleSelect.value);
_3a.id=this._editors.id.attr("value");
_3b=this._editors.authors.getValues();
for(i in _3b){
if(_3b[i].name||_3b[i].email||_3b[i].uri){
_3a.addAuthor(_3b[i].name,_3b[i].email,_3b[i].uri);
}
}
_3c=this._editors.contributors.getValues();
for(i in _3c){
if(_3c[i].name||_3c[i].email||_3c[i].uri){
_3a.addContributor(_3c[i].name,_3c[i].email,_3c[i].uri);
}
}
_37=this._editors.summary.attr("value");
if(this.entrySummarySelect.value==="xhtml"){
_37=this._enforceXhtml(_37);
_37="<div xmlns=\"http://www.w3.org/1999/xhtml\">"+_37+"</div>";
}
_3a.summary=new dojox.atom.io.model.Content("summary",_37,null,this.entrySummarySelect.value);
_37=this._editors.content.attr("value");
if(this.entryContentSelect.value==="xhtml"){
_37=this._enforceXhtml(_37);
_37="<div xmlns=\"http://www.w3.org/1999/xhtml\">"+_37+"</div>";
}
_3a.content=new dojox.atom.io.model.Content("content",_37,null,this.entryContentSelect.value);
dojo.style(this.entryNewButton,"display","");
dojo.publish(this.entrySelectionTopic,[{action:"post",source:this,entry:_3a}]);
}
this._editMode=false;
this.setEntry(_3a,this._feed,true);
},_handleSave:function(_47,_48){
this._editMode=false;
this.clear();
this.setEntry(_47,this.getFeed(),true);
},cancelEdits:function(){
this._new=false;
dojo.style(this.entrySaveCancelButtons,"display","none");
if(this._editable){
dojo.style(this.entryEditButton,"display","");
}
dojo.style(this.entryNewButton,"display","");
this._editMode=false;
this.clearEditors();
this.setEntry(this.getEntry(),this.getFeed(),true);
},clear:function(){
this._editable=false;
this.clearEditors();
dojox.atom.widget.FeedEntryEditor.superclass.clear.apply(this);
if(this._contentEditor){
this._contentEditor=this._setObject=this._oldContent=this._contentEditorCreator=null;
this._editors={};
}
},clearEditors:function(){
for(var key in this._editors){
if(this._editors[key].declaredClass==="dijit.Editor"){
this._editors[key].close(false,true);
}
this._editors[key].destroy();
}
this._editors={};
},_enforceXhtml:function(_4a){
var _4b=null;
if(_4a){
var _4c=/<br>/g;
_4b=_4a.replace(_4c,"<br/>");
_4b=this._closeTag(_4b,"hr");
_4b=this._closeTag(_4b,"img");
}
return _4b;
},_closeTag:function(_4d,tag){
var _4f="<"+tag;
var _50=_4d.indexOf(_4f);
if(_50!==-1){
while(_50!==-1){
var _51="";
var _52=false;
for(var i=0;i<_4d.length;i++){
var c=_4d.charAt(i);
if(i<=_50||_52){
_51+=c;
}else{
if(c===">"){
_51+="/";
_52=true;
}
_51+=c;
}
}
_4d=_51;
_50=_4d.indexOf(_4f,_50+1);
}
}
return _4d;
},_toggleNew:function(){
dojo.style(this.entryNewButton,"display","none");
dojo.style(this.entryEditButton,"display","none");
dojo.style(this.entrySaveCancelButtons,"display","");
this.entrySummarySelect.value="text";
this.entryContentSelect.value="text";
this.entryTitleSelect.value="text";
this.clearNodes();
this._new=true;
var _55=dojo.i18n.getLocalization("dojox.atom.widget","FeedEntryViewer");
var _56=new dojox.atom.widget.EntryHeader({title:_55.title});
this.entryTitleHeader.appendChild(_56.domNode);
this._editors.title=this._createEditor(this.entryTitleNode,null);
this.setFieldValidity("title",true);
var _57=new dojox.atom.widget.EntryHeader({title:_55.authors});
this.entryAuthorHeader.appendChild(_57.domNode);
this._editors.authors=this._createPeopleEditor(this.entryAuthorNode,{name:"Author"});
this.setFieldValidity("authors",true);
var _58=new dojox.atom.widget.EntryHeader({title:_55.contributors});
this.entryContributorHeader.appendChild(_58.domNode);
this._editors.contributors=this._createPeopleEditor(this.entryContributorNode,{name:"Contributor"});
this.setFieldValidity("contributors",true);
var _59=new dojox.atom.widget.EntryHeader({title:_55.id});
this.entryIdHeader.appendChild(_59.domNode);
this._editors.id=this._createEditor(this.entryIdNode,null);
this.setFieldValidity("id",true);
var _5a=new dojox.atom.widget.EntryHeader({title:_55.updated});
this.entryUpdatedHeader.appendChild(_5a.domNode);
this._editors.updated=this._createEditor(this.entryUpdatedNode,null);
this.setFieldValidity("updated",true);
var _5b=new dojox.atom.widget.EntryHeader({title:_55.summary});
this.entrySummaryHeader.appendChild(_5b.domNode);
this._editors.summary=this._createEditor(this.entrySummaryNode,null,true);
this.setFieldValidity("summaryedit",true);
this.setFieldValidity("summary",true);
var _5c=new dojox.atom.widget.EntryHeader({title:_55.content});
this.entryContentHeader.appendChild(_5c.domNode);
this._editors.content=this._createEditor(this.entryContentNode,null,true);
this.setFieldValidity("contentedit",true);
this.setFieldValidity("content",true);
this._displaySections();
},_displaySections:function(){
dojo.style(this.entrySummarySelect,"display","none");
dojo.style(this.entryContentSelect,"display","none");
dojo.style(this.entryTitleSelect,"display","none");
if(this.isFieldValid("contentedit")){
dojo.style(this.entryContentSelect,"display","");
}
if(this.isFieldValid("summaryedit")){
dojo.style(this.entrySummarySelect,"display","");
}
if(this.isFieldValid("titleedit")){
dojo.style(this.entryTitleSelect,"display","");
}
dojox.atom.widget.FeedEntryEditor.superclass._displaySections.apply(this);
if(this._toLoad){
for(var i in this._toLoad){
var _5e;
if(this._toLoad[i].generateEditor){
_5e=dojo.hitch(this._toLoad[i],this._toLoad[i].generateEditor)();
}else{
_5e=this._toLoad[i];
}
this._editors[this._toLoad[i].name]=_5e;
this._toLoad[i]=null;
}
this._toLoad=null;
}
}});
dojo.declare("dojox.atom.widget.PeopleEditor",[dijit._Widget,dijit._Templated,dijit._Container],{templateString:"<div class=\"peopleEditor\">\n\t<table style=\"width: 100%\">\n\t\t<tbody dojoAttachPoint=\"peopleEditorEditors\"></tbody>\n\t</table>\n\t<span class=\"peopleEditorButton\" dojoAttachPoint=\"peopleEditorButton\" dojoAttachEvent=\"onclick:_add\"></span>\n</div>\n",_rows:[],_editors:[],_index:0,_numRows:0,postCreate:function(){
var _5f=dojo.i18n.getLocalization("dojox.atom.widget","PeopleEditor");
if(this.name){
if(this.name=="Author"){
this.peopleEditorButton.appendChild(document.createTextNode("["+_5f.addAuthor+"]"));
}else{
if(this.name=="Contributor"){
this.peopleEditorButton.appendChild(document.createTextNode("["+_5f.addContributor+"]"));
}
}
}else{
this.peopleEditorButton.appendChild(document.createTextNode("["+_5f.add+"]"));
}
this._editors=[];
if(!this.data||this.data.length===0){
this._createEditors(null,null,null,0,this.name);
this._index=1;
}else{
for(var i in this.data){
this._createEditors(this.data[i].name,this.data[i].email,this.data[i].uri,i);
this._index++;
this._numRows++;
}
}
},destroy:function(){
for(var key in this._editors){
for(var _62 in this._editors[key]){
this._editors[key][_62].destroy();
}
}
this._editors=[];
},_createEditors:function(_63,_64,uri,_66,_67){
var row=document.createElement("tr");
this.peopleEditorEditors.appendChild(row);
row.id="removeRow"+_66;
var _69=document.createElement("td");
_69.setAttribute("align","right");
row.appendChild(_69);
_69.colSpan=2;
if(this._numRows>0){
var hr=document.createElement("hr");
_69.appendChild(hr);
hr.id="hr"+_66;
}
row=document.createElement("span");
_69.appendChild(row);
row.className="peopleEditorButton";
dojo.style(row,"font-size","x-small");
dojo.connect(row,"onclick",this,"_removeEditor");
row.id="remove"+_66;
_69=document.createTextNode("[X]");
row.appendChild(_69);
row=document.createElement("tr");
this.peopleEditorEditors.appendChild(row);
row.id="editorsRow"+_66;
var _6b=document.createElement("td");
row.appendChild(_6b);
dojo.style(_6b,"width","20%");
_69=document.createElement("td");
row.appendChild(_69);
row=document.createElement("table");
_6b.appendChild(row);
dojo.style(row,"width","100%");
_6b=document.createElement("tbody");
row.appendChild(_6b);
row=document.createElement("table");
_69.appendChild(row);
dojo.style(row,"width","100%");
_69=document.createElement("tbody");
row.appendChild(_69);
this._editors[_66]=[];
this._editors[_66].push(this._createEditor(_63,_67+"name"+_66,"Name:",_6b,_69));
this._editors[_66].push(this._createEditor(_64,_67+"email"+_66,"Email:",_6b,_69));
this._editors[_66].push(this._createEditor(uri,_67+"uri"+_66,"URI:",_6b,_69));
},_createEditor:function(_6c,id,_6e,_6f,_70){
var row=document.createElement("tr");
_6f.appendChild(row);
var _72=document.createElement("label");
_72.setAttribute("for",id);
_72.appendChild(document.createTextNode(_6e));
_6f=document.createElement("td");
_6f.appendChild(_72);
row.appendChild(_6f);
row=document.createElement("tr");
_70.appendChild(row);
_70=document.createElement("td");
row.appendChild(_70);
var _73=document.createElement("input");
_73.setAttribute("id",id);
_70.appendChild(_73);
dojo.style(_73,"width","95%");
var box=new dijit.form.TextBox({},_73);
box.attr("value",_6c);
return box;
},_removeEditor:function(_75){
var _76=null;
if(dojo.isIE){
_76=_75.srcElement;
}else{
_76=_75.target;
}
var id=_76.id;
id=id.substring(6);
for(var key in this._editors[id]){
this._editors[id][key].destroy();
}
var _79=dojo.byId("editorsRow"+id);
var _7a=_79.parentNode;
_7a.removeChild(_79);
_79=dojo.byId("removeRow"+id);
_7a=_79.parentNode;
_7a.removeChild(_79);
this._numRows--;
if(this._numRows===1&&_7a.firstChild.firstChild.firstChild.tagName.toLowerCase()==="hr"){
_79=_7a.firstChild.firstChild;
_79.removeChild(_79.firstChild);
}
this._editors[id]=null;
},_add:function(){
this._createEditors(null,null,null,this._index);
this._index++;
this._numRows++;
},getValues:function(){
var _7b=[];
for(var i in this._editors){
if(this._editors[i]){
_7b.push({name:this._editors[i][0].attr("value"),email:this._editors[i][1].attr("value"),uri:this._editors[i][2].attr("value")});
}
}
return _7b;
}});
}
