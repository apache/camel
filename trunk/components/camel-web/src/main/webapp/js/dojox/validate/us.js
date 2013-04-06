/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.validate.us"]){
dojo._hasResource["dojox.validate.us"]=true;
dojo.provide("dojox.validate.us");
dojo.require("dojox.validate._base");
dojox.validate.us.isState=function(_1,_2){
var re=new RegExp("^"+dojox.validate.regexp.us.state(_2)+"$","i");
return re.test(_1);
};
dojox.validate.us.isPhoneNumber=function(_4){
var _5={format:["###-###-####","(###) ###-####","(###) ### ####","###.###.####","###/###-####","### ### ####","###-###-#### x#???","(###) ###-#### x#???","(###) ### #### x#???","###.###.#### x#???","###/###-#### x#???","### ### #### x#???","##########"]};
return dojox.validate.isNumberFormat(_4,_5);
};
dojox.validate.us.isSocialSecurityNumber=function(_6){
var _7={format:["###-##-####","### ## ####","#########"]};
return dojox.validate.isNumberFormat(_6,_7);
};
dojox.validate.us.isZipCode=function(_8){
var _9={format:["#####-####","##### ####","#########","#####"]};
return dojox.validate.isNumberFormat(_8,_9);
};
}
