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

dojo.provide("bespin.page.editor.init");

// = Bootstrap =
//
// This file is the editor bootstrap code that is loaded via script src
// from /editor.html.
//
// It handles setting up the objects that are to be used on the editor
// and deals with layout changes.

// ** {{{ Globals }}}
//
// One day we will get rid of all of these bar the core bespin object.

// pieces in the scene
(function() {
    var projectLabel;
    var fileLabel;
    var scene;

    dojo.mixin(bespin.page.editor, {
        // ** {{{ whenLoggedIn(userinfo) }}} **
        //
        // * {{{userinfo}}} is an object containing user specific info (project etc)
        //
        // Save the users magic project into the session
        whenLoggedIn: function(userinfo) {
            bespin.get('editSession').setUserinfo(userinfo);

            bespin.register('settings', new bespin.client.settings.Core());
            bespin.register('commandLine', new bespin.cmd.commandline.Interface('command', bespin.cmd.editorcommands.Commands));
        },

        // ** {{{ whenNotLoggedIn() }}} **
        //
        // Send the user back to the front page as they aren't logged in.
        // The server should stop this from happening, but JUST in case.
        whenNotLoggedIn: function() {
            bespin.util.navigate.home(); // go back
        },

        // ** {{{ recalcLayout() }}} **
        //
        // When a change to the UI is needed due to opening or closing a feature
        // (e.g. file view, session view) move the items around
        recalcLayout: function() {
            var subheader = dojo.byId("subheader");
            var footer = dojo.byId("footer");
            var editor = dojo.byId("editor");
            var files = dojo.byId("files");
            var collab = dojo.byId("collab");
            var target = dojo.byId("target_browsers");

            var move = [ subheader, footer, editor ];

            if (bespin.get('toolbar').showFiles) {
                files.style.display = "block";
                dojo.forEach(move, function(item) { item.style.left = "201px"; });
            } else {
                files.style.display = "none";
                dojo.forEach(move, function(item) { item.style.left = "0"; });
            }

            move.pop();   // editor shouldn't have its right-hand side set

            if (bespin.get('toolbar').showCollab) {
                collab.style.display = "block";
                dojo.forEach(move, function(item) { item.style.right = "201px"; });
            } else {
                collab.style.display = "none";
                dojo.forEach(move, function(item) { item.style.right = "0"; });
            }

            if (bespin.get('toolbar').showTarget) {
                target.style.display = "block";
            } else {
                target.style.display = "none";
            }

            this.doResize();
        },

        // ** {{{ doResize() }}} **
        //
        // When a user resizes the window, deal with resizing the canvas and repaint
        doResize: function() {
            var d = dojo.coords('status');
            dojo.attr('projectLabel', { width: d.w, height: d.h });

            bespin.get('editor').paint();
        }
    })

    // ** {{{ window.load time }}} **
    //
    // Loads and configures the objects that the editor needs
    dojo.addOnLoad(function() {
        bespin.register('quickopen', new bespin.editor.quickopen.API());
        var editor = bespin.register('editor', new bespin.editor.API('editor'));
        var editSession = bespin.register('editSession', new bespin.client.session.EditSession(editor));
        var server = bespin.register('server', new bespin.client.Server());
        var files = bespin.register('files', new bespin.client.FileSystem());

        bespin.register('toolbar', new bespin.editor.Toolbar(editor, { setupDefault: true }));

        // Force a login just in case the user session isn't around
        server.currentuser(bespin.page.editor.whenLoggedIn, bespin.page.editor.whenNotLoggedIn);

        // Set the version info
        bespin.displayVersion();

        // Get going when settings are loaded
        bespin.subscribe("bespin:settings:loaded", function(event) {
            bespin.get('settings').loadSession();  // load the last file or what is passed in
            bespin.page.editor.doResize();
        });

        dojo.connect(window, 'resize', bespin.page.editor, "doResize");

        scene = new th.Scene(dojo.byId("projectLabel"));

        var panel = new th.components.Panel();
        scene.root.add(panel);

        projectLabel = new th.components.Label({ style: {
            color: "white",
            font: "12pt Calibri, Arial, sans-serif"
        }});
        var symbolThingie = new th.components.Label({ text: ":", style: {
            color: "gray",
            font: "12pt Calibri, Arial, sans-serif"
        }});
        fileLabel = new th.components.Label({ style: {
            color: "white",
            font: "12pt Calibri, Arial, sans-serif"
        }});

        panel.add([ projectLabel, symbolThingie, fileLabel ]);
        panel.layout = function() {
            var d = this.d();

            var x = 0;
            for (var i = 0; i < 2; i++) {
                var width = this.children[i].getPreferredWidth(d.b.h);
                this.children[i].bounds = { x: x, y: 0, width: width, height: d.b.h };
                x += width;
            }

            this.children[2].bounds = { x: x, y: 0, width: d.b.w - d.i.w - x, height: d.b.h };
        };

        scene.render();
    });

    // ** {{{ Event: bespin:editor:openfile:opensuccess }}} **
    // 
    // When a file is opened successfully change the project and file status area.
    // Then change the window title, and change the URL hash area
    bespin.subscribe("bespin:editor:openfile:opensuccess", function(event) {
        var project = event.project || bespin.get('editSession').project; 
        var filename = event.file.name;

        projectLabel.attributes.text = bespin.get('editSession').project;
        fileLabel.attributes.text = filename;
        scene.render();
    });
})();
