/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.storage.AirDBStorageProvider"]){
dojo._hasResource["dojox.storage.AirDBStorageProvider"]=true;
dojo.provide("dojox.storage.AirDBStorageProvider");
dojo.require("dojox.storage.manager");
dojo.require("dojox.storage.Provider");
if(dojo.isAIR){
(function(){
if(!_1){
var _1={};
}
_1.File=window.runtime.flash.filesystem.File;
_1.SQLConnection=window.runtime.flash.data.SQLConnection;
_1.SQLStatement=window.runtime.flash.data.SQLStatement;
dojo.declare("dojox.storage.AirDBStorageProvider",[dojox.storage.Provider],{DATABASE_FILE:"dojo.db",TABLE_NAME:"__DOJO_STORAGE",initialized:false,_db:null,initialize:function(){
this.initialized=false;
try{
this._db=new _1.SQLConnection();
this._db.open(_1.File.applicationStorageDirectory.resolvePath(this.DATABASE_FILE));
this._sql("CREATE TABLE IF NOT EXISTS "+this.TABLE_NAME+"(namespace TEXT, key TEXT, value TEXT)");
this._sql("CREATE UNIQUE INDEX IF NOT EXISTS namespace_key_index ON "+this.TABLE_NAME+" (namespace, key)");
this.initialized=true;
}
catch(e){

}
dojox.storage.manager.loaded();
},_sql:function(_2,_3){
var _4=new _1.SQLStatement();
_4.sqlConnection=this._db;
_4.text=_2;
if(_3){
for(var _5 in _3){
_4.parameters[_5]=_3[_5];
}
}
_4.execute();
return _4.getResult();
},_beginTransaction:function(){
this._db.begin();
},_commitTransaction:function(){
this._db.commit();
},isAvailable:function(){
return true;
},put:function(_6,_7,_8,_9){
if(this.isValidKey(_6)==false){
throw new Error("Invalid key given: "+_6);
}
_9=_9||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_9)==false){
throw new Error("Invalid namespace given: "+_9);
}
try{
this._sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = :namespace AND key = :key",{":namespace":_9,":key":_6});
this._sql("INSERT INTO "+this.TABLE_NAME+" VALUES (:namespace, :key, :value)",{":namespace":_9,":key":_6,":value":_7});
}
catch(e){

_8(this.FAILED,_6,e.toString());
return;
}
if(_8){
_8(this.SUCCESS,_6,null,_9);
}
},get:function(_a,_b){
if(this.isValidKey(_a)==false){
throw new Error("Invalid key given: "+_a);
}
_b=_b||this.DEFAULT_NAMESPACE;
var _c=this._sql("SELECT * FROM "+this.TABLE_NAME+" WHERE namespace = :namespace AND key = :key",{":namespace":_b,":key":_a});
if(_c.data&&_c.data.length){
return _c.data[0].value;
}
return null;
},getNamespaces:function(){
var _d=[this.DEFAULT_NAMESPACE];
var rs=this._sql("SELECT namespace FROM "+this.TABLE_NAME+" DESC GROUP BY namespace");
if(rs.data){
for(var i=0;i<rs.data.length;i++){
if(rs.data[i].namespace!=this.DEFAULT_NAMESPACE){
_d.push(rs.data[i].namespace);
}
}
}
return _d;
},getKeys:function(_10){
_10=_10||this.DEFAULT_NAMESPACE;
if(this.isValidKey(_10)==false){
throw new Error("Invalid namespace given: "+_10);
}
var _11=[];
var rs=this._sql("SELECT key FROM "+this.TABLE_NAME+" WHERE namespace = :namespace",{":namespace":_10});
if(rs.data){
for(var i=0;i<rs.data.length;i++){
_11.push(rs.data[i].key);
}
}
return _11;
},clear:function(_14){
if(this.isValidKey(_14)==false){
throw new Error("Invalid namespace given: "+_14);
}
this._sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = :namespace",{":namespace":_14});
},remove:function(key,_16){
_16=_16||this.DEFAULT_NAMESPACE;
this._sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = :namespace AND key = :key",{":namespace":_16,":key":key});
},putMultiple:function(_17,_18,_19,_1a){
if(this.isValidKeyArray(_17)===false||!_18 instanceof Array||_17.length!=_18.length){
throw new Error("Invalid arguments: keys = ["+_17+"], values = ["+_18+"]");
}
if(_1a==null||typeof _1a=="undefined"){
_1a=this.DEFAULT_NAMESPACE;
}
if(this.isValidKey(_1a)==false){
throw new Error("Invalid namespace given: "+_1a);
}
this._statusHandler=_19;
try{
this._beginTransaction();
for(var i=0;i<_17.length;i++){
this._sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = :namespace AND key = :key",{":namespace":_1a,":key":_17[i]});
this._sql("INSERT INTO "+this.TABLE_NAME+" VALUES (:namespace, :key, :value)",{":namespace":_1a,":key":_17[i],":value":_18[i]});
}
this._commitTransaction();
}
catch(e){

if(_19){
_19(this.FAILED,_17,e.toString(),_1a);
}
return;
}
if(_19){
_19(this.SUCCESS,_17,null);
}
},getMultiple:function(_1c,_1d){
if(this.isValidKeyArray(_1c)===false){
throw new Error("Invalid key array given: "+_1c);
}
if(_1d==null||typeof _1d=="undefined"){
_1d=this.DEFAULT_NAMESPACE;
}
if(this.isValidKey(_1d)==false){
throw new Error("Invalid namespace given: "+_1d);
}
var _1e=[];
for(var i=0;i<_1c.length;i++){
var _20=this._sql("SELECT * FROM "+this.TABLE_NAME+" WHERE namespace = :namespace AND key = :key",{":namespace":_1d,":key":_1c[i]});
_1e[i]=_20.data&&_20.data.length?_20.data[0].value:null;
}
return _1e;
},removeMultiple:function(_21,_22){
_22=_22||this.DEFAULT_NAMESPACE;
this._beginTransaction();
for(var i=0;i<_21.length;i++){
this._sql("DELETE FROM "+this.TABLE_NAME+" WHERE namespace = namespace = :namespace AND key = :key",{":namespace":_22,":key":_21[i]});
}
this._commitTransaction();
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
}});
dojox.storage.manager.register("dojox.storage.AirDBStorageProvider",new dojox.storage.AirDBStorageProvider());
dojox.storage.manager.initialize();
})();
}
}
