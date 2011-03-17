/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.data.GoogleFeedStore"]){
dojo._hasResource["dojox.data.GoogleFeedStore"]=true;
dojo.provide("dojox.data.GoogleFeedStore");
dojo.experimental("dojox.data.GoogleFeedStore");
dojo.require("dojox.data.GoogleSearchStore");
dojo.declare("dojox.data.GoogleFeedStore",dojox.data.GoogleSearchStore,{_type:"",_googleUrl:"http://ajax.googleapis.com/ajax/services/feed/load",_attributes:["title","link","author","published","content","summary","categories"],_queryAttr:"url",_processItem:function(_1,_2){
this.inherited(arguments);
_1["summary"]=_1["contentSnippet"];
_1["published"]=_1["publishedDate"];
},_getItems:function(_3){
return _3["feed"]&&_3.feed[["entries"]]?_3.feed[["entries"]]:null;
},_createContent:function(_4,_5,_6){
var cb=this.inherited(arguments);
cb.num=(_6.count||10)+(_6.start||0);
return cb;
}});
}
