/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


dojo.require("dojox.gfx");
dojo.requireIf(dojox.gfx.renderer=="svg","dojox.gfx.svg_attach");
dojo.requireIf(dojox.gfx.renderer=="vml","dojox.gfx.vml_attach");
dojo.requireIf(dojox.gfx.renderer=="silverlight","dojox.gfx.silverlight_attach");
dojo.requireIf(dojox.gfx.renderer=="canvas","dojox.gfx.canvas_attach");
