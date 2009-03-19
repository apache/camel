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

dojo.provide("bespin.util.navigate");

dojo.require("bespin.client.settings");

// = Navigate =
//
// Simple wrapper to force navigation to a project URL without all using location.href

// ** {{{ bespin.util.navigate }}} **
//
// new up an object that will return public methods and hide private ones

(function() {

    // ** {{{ Yup, you can be private }}} **
    //
    // Generic location changer

    var go = function(url, newTab) {
        if (newTab) {
            window.open(url, "_blank");
        } else {
            location.href = url;
        }
    };


    // ** {{{ Public }}} **
    //
    // Simple methods to construct URLs within Bespin and go to them

    dojo.mixin(bespin.util.navigate, {
        dashboard: function(newTab) {
            var pathSelected = (new bespin.client.settings.URL()).get('fromDashboardPath')
            if (pathSelected) {
                go("dashboard.html#path="+pathSelected, newTab);    // this contains the pathSelected parameter!
            } else {
                go("dashboard.html", newTab);
            }
        },

        home: function(newTab) {
            go("index.html", newTab);
        },

        quickEdit: function(newTab) {
    		go("editor.html#new=true", newTab);
    	},

        editor: function(project, path, newTab) {
            var url = "editor.html#";
            var args = [];

            if (project) args.push("project=" + project);
            if (path) args.push("path=" + path);
            var selectedPath = bespin.page.dashboard.tree.getSelectedPath(true);
            if (selectedPath)   args.push('fromDashboardPath='+selectedPath);

            if (args.length > 0) url += args.join("&");

            go(url, newTab);
        }
    });
})();
