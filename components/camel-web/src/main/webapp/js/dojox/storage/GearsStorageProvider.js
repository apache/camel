/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.storage.GearsStorageProvider"]){
dojo._hasResource["dojox.storage.GearsStorageProvider"]=true;
dojo.provide("dojox.storage.GearsStorageProvider");
dojo.require("dojo.gears");
dojo.require("dojox.storage.Provider");
dojo.require("dojox.storage.manager");
dojo.require("dojox.sql");
if(dojo.gears.available){
(function(){
dojo.declare("dojox.storage.GearsStorageProvider",dojox.storage.Provider,{constructor:function(){
},TABLE_NAME:"__DOJO_STORAGE",initialized:false,_available:null,_storageReady:false,initialize:function(){
if(dojo.config["disableGearsStorage"]==true){
return;
}
this.TABLE_NAME="__DOJO_STORAGE";
this.initialized=true;
dojox.storage.manager.loaded();
},isAvailable:function(){
return this._available=dojo.gears.available;
},put:function(_1,_2,_3,_4){
this._initStorage();
if(!this.isValidKey(_1)){
throw new Error("Invalid key given: "+_1);
}
_4=_4||this.DEFAULT_NAMESPACE;
if(!this.isValidKey(_4)){
throw new Error("Invalid namespace given: "+_1);
}
if(dojo.isString(_2)){
_2="string:"+_2;
}else{
_2=dojo.toJson(_2);
}
try{
dojox.sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = ? AND key = ?",_4,_1);
dojox.sql("INSERT INTO "+this.TABLE_NAME+" VALUES (?, ?, ?)",_4,_1,_2);
}
catch(e){

_3(this.FAILED,_1,e.toString(),_4);
return;
}
if(_3){
_3(dojox.storage.SUCCESS,_1,null,_4);
}
},get:function(_5,_6){
this._initStorage();
if(!this.isValidKey(_5)){
throw new Error("Invalid key given: "+_5);
}
_6=_6||this.DEFAULT_NAMESPACE;
if(!this.isValidKey(_6)){
throw new Error("Invalid namespace given: "+_5);
}
var _7=dojox.sql("SELECT * FROM "+this.TABLE_NAME+" WHERE namespace = ? AND "+" key = ?",_6,_5);
if(!_7.length){
return null;
}else{
_7=_7[0].value;
}
if(dojo.isString(_7)&&(/^string:/.test(_7))){
_7=_7.substring("string:".length);
}else{
_7=dojo.fromJson(_7);
}
return _7;
},getNamespaces:function(){
this._initStorage();
var _8=[dojox.storage.DEFAULT_NAMESPACE];
var rs=dojox.sql("SELECT namespace FROM "+this.TABLE_NAME+" DESC GROUP BY namespace");
for(var i=0;i<rs.length;i++){
if(rs[i].namespace!=dojox.storage.DEFAULT_NAMESPACE){
_8.push(rs[i].namespace);
}
}
return _8;
},getKeys:function(_b){
this._initStorage();
_b=_b||this.DEFAULT_NAMESPACE;
if(!this.isValidKey(_b)){
throw new Error("Invalid namespace given: "+_b);
}
var rs=dojox.sql("SELECT key FROM "+this.TABLE_NAME+" WHERE namespace = ?",_b);
var _d=[];
for(var i=0;i<rs.length;i++){
_d.push(rs[i].key);
}
return _d;
},clear:function(_f){
this._initStorage();
_f=_f||this.DEFAULT_NAMESPACE;
if(!this.isValidKey(_f)){
throw new Error("Invalid namespace given: "+_f);
}
dojox.sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = ?",_f);
},remove:function(key,_11){
this._initStorage();
if(!this.isValidKey(key)){
throw new Error("Invalid key given: "+key);
}
_11=_11||this.DEFAULT_NAMESPACE;
if(!this.isValidKey(_11)){
throw new Error("Invalid namespace given: "+key);
}
dojox.sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = ? AND"+" key = ?",_11,key);
},putMultiple:function(_12,_13,_14,_15){
this._initStorage();
if(!this.isValidKeyArray(_12)||!_13 instanceof Array||_12.length!=_13.length){
throw new Error("Invalid arguments: keys = ["+_12+"], values = ["+_13+"]");
}
if(_15==null||typeof _15=="undefined"){
_15=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_15)){
throw new Error("Invalid namespace given: "+_15);
}
this._statusHandler=_14;
try{
dojox.sql.open();
dojox.sql.db.execute("BEGIN TRANSACTION");
var _16="REPLACE INTO "+this.TABLE_NAME+" VALUES (?, ?, ?)";
for(var i=0;i<_12.length;i++){
var _18=_13[i];
if(dojo.isString(_18)){
_18="string:"+_18;
}else{
_18=dojo.toJson(_18);
}
dojox.sql.db.execute(_16,[_15,_12[i],_18]);
}
dojox.sql.db.execute("COMMIT TRANSACTION");
dojox.sql.close();
}
catch(e){

if(_14){
_14(this.FAILED,_12,e.toString(),_15);
}
return;
}
if(_14){
_14(dojox.storage.SUCCESS,_12,null,_15);
}
},getMultiple:function(_19,_1a){
this._initStorage();
if(!this.isValidKeyArray(_19)){
throw new ("Invalid key array given: "+_19);
}
if(_1a==null||typeof _1a=="undefined"){
_1a=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_1a)){
throw new Error("Invalid namespace given: "+_1a);
}
var _1b="SELECT * FROM "+this.TABLE_NAME+" WHERE namespace = ? AND "+" key = ?";
var _1c=[];
for(var i=0;i<_19.length;i++){
var _1e=dojox.sql(_1b,_1a,_19[i]);
if(!_1e.length){
_1c[i]=null;
}else{
_1e=_1e[0].value;
if(dojo.isString(_1e)&&(/^string:/.test(_1e))){
_1c[i]=_1e.substring("string:".length);
}else{
_1c[i]=dojo.fromJson(_1e);
}
}
}
return _1c;
},removeMultiple:function(_1f,_20){
this._initStorage();
if(!this.isValidKeyArray(_1f)){
throw new Error("Invalid arguments: keys = ["+_1f+"]");
}
if(_20==null||typeof _20=="undefined"){
_20=dojox.storage.DEFAULT_NAMESPACE;
}
if(!this.isValidKey(_20)){
throw new Error("Invalid namespace given: "+_20);
}
dojox.sql.open();
dojox.sql.db.execute("BEGIN TRANSACTION");
var _21="DELETE FROM "+this.TABLE_NAME+" WHERE namespace = ? AND key = ?";
for(var i=0;i<_1f.length;i++){
dojox.sql.db.execute(_21,[_20,_1f[i]]);
}
dojox.sql.db.execute("COMMIT TRANSACTION");
dojox.sql.close();
},isPermanent:function(){
return true;
},getMaximumSize:function(){
return this.SIZE_NO_LIMIT;
},hasSettingsUI:function(){
return false;
},showSettingsUI:function(){
throw new Error(this.declaredClass+" does not support a storage settings user-interface");
},hideSettingsUI:function(){
throw new Error(this.declaredClass+" does not support a storage settings user-interface");
},_initStorage:function(){
if(this._storageReady){
return;
}
if(!google.gears.factory.hasPermission){
var _23=null;
var _24=null;
var msg="This site would like to use Google Gears to enable "+"enhanced functionality.";
var _26=google.gears.factory.getPermission(_23,_24,msg);
if(!_26){
throw new Error("You must give permission to use Gears in order to "+"store data");
}
}
try{
dojox.sql("CREATE TABLE IF NOT EXISTS "+this.TABLE_NAME+"( "+" namespace TEXT, "+" key TEXT, "+" value TEXT "+")");
dojox.sql("CREATE UNIQUE INDEX IF NOT EXISTS namespace_key_index"+" ON "+this.TABLE_NAME+" (namespace, key)");
}
catch(e){

throw new Error("Unable to create storage tables for Gears in "+"Dojo Storage");
}
this._storageReady=true;
}});
dojox.storage.manager.register("dojox.storage.GearsStorageProvider",new dojox.storage.GearsStorageProvider());
})();
}
}
