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

dojo.provide("bespin.user.utils");

dojo.require("bespin.util.webpieces");

// = Utility functions for the Bespin front page =
//
// Dealing with login on the front page of the site and beyond

dojo.mixin(bespin.user.utils, {
    whenLoginSucceeded: function() {
        bespin.util.navigate.dashboard();
    },

    whenLoginFailed: function() {
        bespin.util.webpieces.showStatus("Sorry, login didn't work. Try again? Caps lock on?");
    },

    whenUsernameInUse: function() {
        bespin.util.webpieces.showStatus("The username is taken. Please choose another.");
    },

    whenAlreadyLoggedIn: function(userinfo) {
        dojo.byId('display_username').innerHTML = userinfo.username;
        dojo.style('logged_in', 'display', 'block');
    },

    whenNotAlreadyLoggedIn: function() {
        dojo.style('not_logged_in', 'display', 'block');
    },

    // make sure that the browser can do our wicked shizzle
    checkBrowserAbility: function() {
        if (typeof dojo.byId('testcanvas').getContext != "function") return false; // no canvas

        var ctx = dojo.byId('testcanvas').getContext("2d");

        if (ctx.fillText || ctx.mozDrawText) {
            return true; // you need text support my friend
        } else {
            return false;
        }
    },

    showingBrowserCompatScreen: function() {
       if (!this.checkBrowserAbility()) { // if you don't have the ability
            bespin.util.webpieces.showCenterPopup(dojo.byId('browser_not_compat'));

            return true;
        } else {
            return false;
        }
    },

    validateEmail: function(str) {
        var filter=/^([\w-+_]+(?:\.[\w-+_]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$/i;
        return filter.test(str);
    }
});
