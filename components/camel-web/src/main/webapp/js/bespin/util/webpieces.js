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

dojo.provide("bespin.util.webpieces");

// = Utility functions for Web snippets =
//
// There are little widgets and components that we want to reuse

dojo.mixin(bespin.util.webpieces, {
    // -- Center Popup
    showCenterPopup: function(el) {
        this.showOverlay();
        dojo.style(el, 'display', 'block');

        // retrieve required dimensions
        var elDims = dojo.coords(el);
        var browserDims = dijit.getViewport();
        
        // calculate the center of the page using the browser and element dimensions
        var y = (browserDims.h - elDims.h) / 2;
        var x = (browserDims.w - elDims.w) / 2;

        // set the style of the element so it is centered
        dojo.style(el, {
            position: 'absolute',
            top: y + 'px',
            left: x + 'px'
        });
    },

    hideCenterPopup: function(el) {
        dojo.style(el, 'display', 'none');
        this.hideOverlay();
    },

    // -- Overlay
    
    showOverlay: function() {
        dojo.style('overlay', 'display', 'block');
    },

    hideOverlay: function() {
        dojo.style('overlay', 'display', 'none');
    },

    // take the overlay and make sure it stretches on the entire height of the screen
    fillScreenOverlay: function() {
	    var coords = dojo.coords(document.body);
	    
	    if (coords.h) {
            dojo.style(dojo.byId('overlay'), 'height', coords.h + "px");
        }
    },

    // -- Status
    showStatus: function(msg) {
        dojo.byId("status").innerHTML = msg;
        dojo.style('status', 'display', 'block');
    }

});
