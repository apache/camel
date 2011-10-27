/**
 * Dual licensed under the Apache License 2.0 and the MIT license.
 * $Revision$ $Date: 2009-05-10 13:06:45 +1000 (Sun, 10 May 2009) $
 */

dojo.provide("dojox.cometd.reload");
dojo.require("dojox.cometd");

dojo.require("dojo.cookie");
dojo.require("org.cometd.ReloadExtension");

// Remap cometd COOKIE functions to dojo cookie functions
org.cometd.COOKIE.set = dojo.cookie;
org.cometd.COOKIE.get = dojo.cookie;

dojox.cometd.registerExtension('reload', new org.cometd.ReloadExtension());


