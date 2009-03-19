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

dojo.require("bespin.util.util");

dojo.provide("bespin.editor.events");

// ** {{{ bespin.editor.Events }}} **
//
// Handle custom events aimed at, and for the editor
dojo.declare("bespin.editor.Events", null, {
    constructor: function(editor) {
        bespin.subscribe("bespin:editor:openfile:opensuccess", function(event) {
            var file = event.file;

            editor.model.insertDocument(file.content);
            editor.cursorManager.moveCursor({ row: 0, col: 0 });
        });

        // -- fire an event here and you can run any editor action
        bespin.subscribe("bespin:editor:doaction", function(event) {
            var action = event.action;
            var args   = event.args || bespin.editor.utils.buildArgs();

            if (action) editor.ui.actions[action](args);
        });

        // -- fire an event to setup any new or replace actions
        bespin.subscribe("bespin:editor:setaction", function(event) {
            var action = event.action;
            var code   = event.code;
            if (action && dojo.isFunction(code)) editor.ui.actions[action] = code;
        });

        // -- add key listeners
        // e.g. bindkey ctrl b moveCursorLeft
        bespin.subscribe("bespin:editor:bindkey", function(event) {
            var modifiers = event.modifiers || '';
            if (!event.key) return;

            var keyCode = bespin.util.keys.Key[event.key.toUpperCase()];

            // -- try an editor action first, else fire away at the event bus
            var action = editor.ui.actions[event.action] || event.action;

            if (keyCode && action) {
                if (event.selectable) { // register the selectable binding to (e.g. SHIFT + what you passed in)
                    editor.editorKeyListener.bindKeyStringSelectable(modifiers, keyCode, action);
                } else {
                    editor.editorKeyListener.bindKeyString(modifiers, keyCode, action);
                }
            }
        });
        
        // ** {{{ Event: bespin:editor:openfile }}} **
        // 
        // Observe a request for a file to be opened and start the cycle:
        //
        // * Send event that you are opening up something (openbefore)
        // * Ask the file system to load a file (loadFile)
        // * If the file is loaded send an opensuccess event
        // * If the file fails to load, send an openfail event
        bespin.subscribe("bespin:editor:openfile", function(event) {
            var filename = event.filename;
            var editSession = bespin.get('editSession');
            var files = bespin.get('files');

            var project  = event.project || editSession.project;

            if (editSession.checkSameFile(project, filename)) return; // short circuit

            bespin.publish("bespin:editor:openfile:openbefore", { project: project, filename: filename });

            files.loadFile(project, filename, function(file) {
                if (!file) {
                    bespin.publish("bespin:editor:openfile:openfail", { project: project, filename: filename });
                } else {
                    bespin.publish("bespin:editor:openfile:opensuccess", { project: project, file: file });
                }
            });
        });

        // ** {{{ Event: bespin:editor:forceopenfile }}} **
        // 
        // Open an existing file, or create a new one.
        bespin.subscribe("bespin:editor:forceopenfile", function(event) {
            var filename = event.filename;
            var project  = event.project;
            var content  = event.content || " ";

            var editSession = bespin.get('editSession');

            if (editSession) {
                if (!project) project = editSession.project;
                if (editSession.checkSameFile(project, filename)) return; // short circuit
            }

            if (!project) return; // short circuit

            bespin.get('files').forceOpenFile(project, filename, content);
        });

        // ** {{{ Event: bespin:editor:newfile }}} **
        // 
        // Observe a request for a new file to be created
        bespin.subscribe("bespin:editor:newfile", function(event) {
            var project = event.project || bespin.get('editSession').project; 
            var newfilename = event.newfilename || "new.txt";

            bespin.get('files').newFile(project, newfilename, function() {
                bespin.publish("bespin:editor:openfile:opensuccess", { file: {
                    name: newfilename,
                    content: " ",
                    timestamp: new Date().getTime()
                }});
            });        
        });

        // ** {{{ Event: bespin:editor:savefile }}} **
        // 
        // Observe a request for a file to be saved and start the cycle:
        //
        // * Send event that you are about to save the file (savebefore)
        // * Get the last operation from the sync helper if it is up and running
        // * Ask the file system to save the file
        // * Change the page title to have the new filename
        // * Tell the command line to show the fact that the file is saved
        //
        // TODO: Need to actually check saved status and know if the save worked

        bespin.subscribe("bespin:editor:savefile", function(event) {
            var project = event.project || bespin.get('editSession').project; 
            var filename = event.filename || bespin.get('editSession').path; // default to what you have

            bespin.publish("bespin:editor:openfile:savebefore", { filename: filename });

            var file = {
                name: filename,
                content: editor.model.getDocument(),
                timestamp: new Date().getTime()
            };

            if (editor.undoManager.syncHelper) { // only if ops are on
                file.lastOp = editor.undoManager.syncHelper.lastOp;
            }

            bespin.get('files').saveFile(project, file); // it will save asynchronously.
            // TODO: Here we need to add in closure to detect errors and thus fire different success / error

            bespin.publish("bespin:editor:titlechange", { filename: filename });

            bespin.publish("bespin:cmdline:showinfo", { msg: 'Saved file: ' + file.name, autohide: true });
        });


        // == Shell Events: Header, Chrome, etc ==
        //
        // ** {{{ Event: bespin:editor:openfile:opensuccess }}} **
        // 
        // When a file is opened successfully change the project and file status area.
        // Then change the window title, and change the URL hash area
        bespin.subscribe("bespin:editor:openfile:opensuccess", function(event) {
            var project = event.project || bespin.get('editSession').project; 
            var filename = event.file.name;

            bespin.publish("bespin:editor:titlechange", { filename: filename });

            bespin.publish("bespin:url:change", { project: project, path: filename });
        });

        // ** {{{ Event: bespin:editor:urlchange }}} **
        // 
        // Observe a urlchange event and then... change the location hash
        bespin.subscribe("bespin:url:change", function(event) {
            var hashArguments = dojo.queryToObject(location.hash.substring(1));
            hashArguments.project = event.project;
            hashArguments.path    = event.path;

            // window.location.hash = dojo.objectToQuery() is not doing the right thing...
            var pairs = [];
            for (var name in hashArguments) {
                var value = hashArguments[name];
                pairs.push(name + '=' + value);
            }
            window.location.hash = pairs.join("&");
        });

        // ** {{{ Event: bespin:url:changed }}} **
        // 
        // Observe a request for session status
        bespin.subscribe("bespin:url:changed", function(event) {
            bespin.publish("bespin:editor:openfile", { filename: event.now.get('path') });
        });

        // ** {{{ Event: bespin:session:status }}} **
        // 
        // Observe a request for session status
        bespin.subscribe("bespin:session:status", function(event) {
            var editSession = bespin.get('editSession');
            var file = editSession.path || 'a new scratch file';
            self.showInfo('Hey ' + editSession.username + ', you are editing ' + file + ' in project ' + editSession.project);
        });

        // ** {{{ Event: bespin:cmdline:focus }}} **
        // 
        // If the command line is in focus, unset focus from the editor
        bespin.subscribe("bespin:cmdline:focus", function(event) {
            editor.setFocus(false);
        });

        // ** {{{ Event: bespin:cmdline:blur }}} **
        // 
        // If the command line is blurred, take control in the editor
        bespin.subscribe("bespin:cmdline:blur", function(event) {
            editor.setFocus(false);
        });
    }
});
