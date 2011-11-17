/**
 * Dual licensed under the Apache License 2.0 and the MIT license.
 * $Revision$ $Date: 2009-05-10 13:06:45 +1000 (Sun, 10 May 2009) $
 */

dojo.provide("dojox.cometd.ack");
dojo.require("dojox.cometd");

dojo.require("org.cometd.AckExtension");

dojox.cometd.registerExtension('ack', new org.cometd.AckExtension());