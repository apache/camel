/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.sql._base"]){
dojo._hasResource["dojox.sql._base"]=true;
dojo.provide("dojox.sql._base");
dojo.require("dojox.sql._crypto");
dojo.mixin(dojox.sql,{dbName:null,debug:(dojo.exists("dojox.sql.debug")?dojox.sql.debug:false),open:function(_1){
if(this._dbOpen&&(!_1||_1==this.dbName)){
return;
}
if(!this.dbName){
this.dbName="dot_store_"+window.location.href.replace(/[^0-9A-Za-z_]/g,"_");
if(this.dbName.length>63){
this.dbName=this.dbName.substring(0,63);
}
}
if(!_1){
_1=this.dbName;
}
try{
this._initDb();
this.db.open(_1);
this._dbOpen=true;
}
catch(exp){
throw exp.message||exp;
}
},close:function(_2){
if(dojo.isIE){
return;
}
if(!this._dbOpen&&(!_2||_2==this.dbName)){
return;
}
if(!_2){
_2=this.dbName;
}
try{
this.db.close(_2);
this._dbOpen=false;
}
catch(exp){
throw exp.message||exp;
}
},_exec:function(_3){
try{
this._initDb();
if(!this._dbOpen){
this.open();
this._autoClose=true;
}
var _4=null;
var _5=null;
var _6=null;
var _7=dojo._toArray(_3);
_4=_7.splice(0,1)[0];
if(this._needsEncrypt(_4)||this._needsDecrypt(_4)){
_5=_7.splice(_7.length-1,1)[0];
_6=_7.splice(_7.length-1,1)[0];
}
if(this.debug){
this._printDebugSQL(_4,_7);
}
var _8;
if(this._needsEncrypt(_4)){
_8=new dojox.sql._SQLCrypto("encrypt",_4,_6,_7,_5);
return null;
}else{
if(this._needsDecrypt(_4)){
_8=new dojox.sql._SQLCrypto("decrypt",_4,_6,_7,_5);
return null;
}
}
var rs=this.db.execute(_4,_7);
rs=this._normalizeResults(rs);
if(this._autoClose){
this.close();
}
return rs;
}
catch(exp){
exp=exp.message||exp;

if(this._autoClose){
try{
this.close();
}
catch(e){

}
}
throw exp;
}
return null;
},_initDb:function(){
if(!this.db){
try{
this.db=google.gears.factory.create("beta.database","1.0");
}
catch(exp){
dojo.setObject("google.gears.denied",true);
if(dojox.off){
dojox.off.onFrameworkEvent("coreOperationFailed");
}
throw "Google Gears must be allowed to run";
}
}
},_printDebugSQL:function(_a,_b){
var _c="dojox.sql(\""+_a+"\"";
for(var i=0;i<_b.length;i++){
if(typeof _b[i]=="string"){
_c+=", \""+_b[i]+"\"";
}else{
_c+=", "+_b[i];
}
}
_c+=")";

},_normalizeResults:function(rs){
var _f=[];
if(!rs){
return [];
}
while(rs.isValidRow()){
var row={};
for(var i=0;i<rs.fieldCount();i++){
var _12=rs.fieldName(i);
var _13=rs.field(i);
row[_12]=_13;
}
_f.push(row);
rs.next();
}
rs.close();
return _f;
},_needsEncrypt:function(sql){
return /encrypt\([^\)]*\)/i.test(sql);
},_needsDecrypt:function(sql){
return /decrypt\([^\)]*\)/i.test(sql);
}});
dojo.declare("dojox.sql._SQLCrypto",null,{constructor:function(_16,sql,_18,_19,_1a){
if(_16=="encrypt"){
this._execEncryptSQL(sql,_18,_19,_1a);
}else{
this._execDecryptSQL(sql,_18,_19,_1a);
}
},_execEncryptSQL:function(sql,_1c,_1d,_1e){
var _1f=this._stripCryptoSQL(sql);
var _20=this._flagEncryptedArgs(sql,_1d);
var _21=this;
this._encrypt(_1f,_1c,_1d,_20,function(_22){
var _23=false;
var _24=[];
var exp=null;
try{
_24=dojox.sql.db.execute(_1f,_22);
}
catch(execError){
_23=true;
exp=execError.message||execError;
}
if(exp!=null){
if(dojox.sql._autoClose){
try{
dojox.sql.close();
}
catch(e){
}
}
_1e(null,true,exp.toString());
return;
}
_24=dojox.sql._normalizeResults(_24);
if(dojox.sql._autoClose){
dojox.sql.close();
}
if(dojox.sql._needsDecrypt(sql)){
var _26=_21._determineDecryptedColumns(sql);
_21._decrypt(_24,_26,_1c,function(_27){
_1e(_27,false,null);
});
}else{
_1e(_24,false,null);
}
});
},_execDecryptSQL:function(sql,_29,_2a,_2b){
var _2c=this._stripCryptoSQL(sql);
var _2d=this._determineDecryptedColumns(sql);
var _2e=false;
var _2f=[];
var exp=null;
try{
_2f=dojox.sql.db.execute(_2c,_2a);
}
catch(execError){
_2e=true;
exp=execError.message||execError;
}
if(exp!=null){
if(dojox.sql._autoClose){
try{
dojox.sql.close();
}
catch(e){
}
}
_2b(_2f,true,exp.toString());
return;
}
_2f=dojox.sql._normalizeResults(_2f);
if(dojox.sql._autoClose){
dojox.sql.close();
}
this._decrypt(_2f,_2d,_29,function(_31){
_2b(_31,false,null);
});
},_encrypt:function(sql,_33,_34,_35,_36){
this._totalCrypto=0;
this._finishedCrypto=0;
this._finishedSpawningCrypto=false;
this._finalArgs=_34;
for(var i=0;i<_34.length;i++){
if(_35[i]){
var _38=_34[i];
var _39=i;
this._totalCrypto++;
dojox.sql._crypto.encrypt(_38,_33,dojo.hitch(this,function(_3a){
this._finalArgs[_39]=_3a;
this._finishedCrypto++;
if(this._finishedCrypto>=this._totalCrypto&&this._finishedSpawningCrypto){
_36(this._finalArgs);
}
}));
}
}
this._finishedSpawningCrypto=true;
},_decrypt:function(_3b,_3c,_3d,_3e){
this._totalCrypto=0;
this._finishedCrypto=0;
this._finishedSpawningCrypto=false;
this._finalResultSet=_3b;
for(var i=0;i<_3b.length;i++){
var row=_3b[i];
for(var _41 in row){
if(_3c=="*"||_3c[_41]){
this._totalCrypto++;
var _42=row[_41];
this._decryptSingleColumn(_41,_42,_3d,i,function(_43){
_3e(_43);
});
}
}
}
this._finishedSpawningCrypto=true;
},_stripCryptoSQL:function(sql){
sql=sql.replace(/DECRYPT\(\*\)/ig,"*");
var _45=sql.match(/ENCRYPT\([^\)]*\)/ig);
if(_45!=null){
for(var i=0;i<_45.length;i++){
var _47=_45[i];
var _48=_47.match(/ENCRYPT\(([^\)]*)\)/i)[1];
sql=sql.replace(_47,_48);
}
}
_45=sql.match(/DECRYPT\([^\)]*\)/ig);
if(_45!=null){
for(i=0;i<_45.length;i++){
var _49=_45[i];
var _4a=_49.match(/DECRYPT\(([^\)]*)\)/i)[1];
sql=sql.replace(_49,_4a);
}
}
return sql;
},_flagEncryptedArgs:function(sql,_4c){
var _4d=new RegExp(/([\"][^\"]*\?[^\"]*[\"])|([\'][^\']*\?[^\']*[\'])|(\?)/ig);
var _4e;
var _4f=0;
var _50=[];
while((_4e=_4d.exec(sql))!=null){
var _51=RegExp.lastMatch+"";
if(/^[\"\']/.test(_51)){
continue;
}
var _52=false;
if(/ENCRYPT\([^\)]*$/i.test(RegExp.leftContext)){
_52=true;
}
_50[_4f]=_52;
_4f++;
}
return _50;
},_determineDecryptedColumns:function(sql){
var _54={};
if(/DECRYPT\(\*\)/i.test(sql)){
_54="*";
}else{
var _55=/DECRYPT\((?:\s*\w*\s*\,?)*\)/ig;
var _56=_55.exec(sql);
while(_56){
var _57=new String(RegExp.lastMatch);
var _58=_57.replace(/DECRYPT\(/i,"");
_58=_58.replace(/\)/,"");
_58=_58.split(/\s*,\s*/);
dojo.forEach(_58,function(_59){
if(/\s*\w* AS (\w*)/i.test(_59)){
_59=_59.match(/\s*\w* AS (\w*)/i)[1];
}
_54[_59]=true;
});
_56=_55.exec(sql);
}
}
return _54;
},_decryptSingleColumn:function(_5a,_5b,_5c,_5d,_5e){
dojox.sql._crypto.decrypt(_5b,_5c,dojo.hitch(this,function(_5f){
this._finalResultSet[_5d][_5a]=_5f;
this._finishedCrypto++;
if(this._finishedCrypto>=this._totalCrypto&&this._finishedSpawningCrypto){
_5e(this._finalResultSet);
}
}));
}});
(function(){
var _60=dojox.sql;
dojox.sql=new Function("return dojox.sql._exec(arguments);");
dojo.mixin(dojox.sql,_60);
})();
}
