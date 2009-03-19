/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * See the License for the specific language governing rights and
 * limitations under the License.
 *
 * The Original Code is Bespin.
 *
 * The Initial Developer of the Original Code is Mozilla.
 * Portions created by the Initial Developer are Copyright (C) 2009
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bespin Team (bespin@mozilla.com)
 *
 * ***** END LICENSE BLOCK ***** */

dojo.provide("bespin.util.canvas");

// = Canvas Shim =
//
// Make canvas work the same on the different browsers and their quirks

// ** {{{ bespin.util.fixcanvas.fix }}} **
//
// Take the context and clean it up

dojo.mixin(bespin.util.canvas, {
    fix: function(ctx) {
        // * upgrade Firefox 3.0.x text rendering to HTML 5 standard
        if (!ctx.fillText && ctx.mozDrawText) {
            ctx.fillText = function(textToDraw, x, y, maxWidth) {
                ctx.translate(x, y);
                ctx.mozTextStyle = ctx.font;
                ctx.mozDrawText(textToDraw);
                ctx.translate(-x, -y);
            };
        }

        // * Setup measureText
        if (!ctx.measureText && ctx.mozMeasureText) {
            ctx.measureText = function(text) {
                if (ctx.font) ctx.mozTextStyle = ctx.font;
                var width = ctx.mozMeasureText(text);
                return { width: width };
            };
        }

        // * Setup html5MeasureText
        if (ctx.measureText && !ctx.html5MeasureText) {
            ctx.html5MeasureText = ctx.measureText;
            ctx.measureText = function(text) {
                var textMetrics = ctx.html5MeasureText(text);

                // fake it 'til you make it
                textMetrics.ascent = ctx.html5MeasureText("m").width;

                return textMetrics;
            };
        }

        // * for other browsers, no-op away
        if (!ctx.fillText) {
            ctx.fillText = function() {};
        }

        if (!ctx.measureText) {
            ctx.measureText = function() { return 10; };
        }

        return ctx;
    }
});
