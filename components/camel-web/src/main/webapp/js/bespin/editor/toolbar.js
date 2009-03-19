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

dojo.provide("bespin.editor.toolbar");

// = Toolbar =
//
// The editor has the notion of a toolbar which are components that can drive the editor from outside of itself
// Such examples are collaboration views, file browser, undo/redo, cut/copy/paste and more.

dojo.declare("bespin.editor.Toolbar", null, {
    DEFAULT_TOOLBAR: ["collaboration", "files", "dashboard", "target_browsers", "save",
                      "close", "undo", "redo", "preview", "fontsize"],
    FONT_SIZES: {
        1: 8,  // small
        2: 10, // medium
        3: 14  // large
    },

    showCollab: false,
    showFiles: false,
    showTarget: false,

    showCollabHotCounter: 0,

    constructor: function(editor, opts) {
        this.editor = editor || bespin.get('editor');
        this.currentFontSize = 2;

        if (opts.setupDefault) this.setupDefault();
    },

    setup: function(type, el) {
        if (dojo.isFunction(this.components[type])) this.components[type](this, el);
    },

    /*
     * Go through the default list and try to hitch onto the DOM element
     */
    setupDefault: function() {
        dojo.forEach(this.DEFAULT_TOOLBAR, dojo.hitch(this, function(item) {
            var item_el = dojo.byId("toolbar_" + item);
            if (item_el) {
                this.setup(item, item_el);
            }
        }));
    },

    components: {
        collaboration: function(toolbar, el) {
            var collab = dojo.byId(el) || dojo.byId("toolbar_collaboration");
            dojo.connect(collab, 'click', function() {
                toolbar.showCollab = !toolbar.showCollab;
                collab.src = "images/" + ( (toolbar.showCollab) ? "icn_collab_on.png" : (toolbar.showCollabHotCounter == 0) ? "icn_collab_off.png" : "icn_collab_watching.png" );
                bespin.page.editor.recalcLayout();
            });
            dojo.connect(collab, 'mouseover', function() {
                collab.style.cursor = "pointer";
                collab.src = "images/icn_collab_on.png";
            });
            dojo.connect(collab, 'mouseout', function() {
                collab.style.cursor = "default";
                collab.src = "images/icn_collab_off.png";
            });
        },

        files: function(toolbar, el) {
            var files = dojo.byId(el) || dojo.byId("toolbar_files");
            dojo.connect(files, 'click', function() {
                toolbar._showFiles = !toolbar._showFiles;
                files.src = "images/" + ( (toolbar._showFiles) ? "icn_files_on.png" : "icn_files_off.png" );
                bespin.page.editor.recalcLayout();
            });
            dojo.connect(files, 'mouseover', function() {
                files.style.cursor = "pointer";
                files.src = "images/icn_files_on.png";
            });
            dojo.connect(files, 'mouseout', function() {
                files.style.cursor = "default";
                files.src = "images/icn_files_off.png";
            });
        },

        dashboard: function(toolbar, el) {
            var dashboard = dojo.byId(el) || dojo.byId("toolbar_dashboard");
            dojo.connect(dashboard, 'mouseover', function() {
                dashboard.style.cursor = "pointer";
                dashboard.src = "images/icn_dashboard_on.png";
            });
            dojo.connect(dashboard, 'mouseout', function() {
                dashboard.style.cursor = "default";
                dashboard.src = "images/icn_dashboard_off.png";
            });
        },

        target_browsers: function(toolbar, el) {
            var target = dojo.byId(el) || dojo.byId("toolbar_target_browsers");
            dojo.connect(target, 'click', function() {
                toolbar._showTarget = !toolbar._showTarget;
                target.src = "images/" + ( (toolbar._showTarget) ? "icn_target_on.png" : "icn_target_off.png" );
                bespin.page.editor.recalcLayout();
            });
            dojo.connect(target, 'mouseover', function() {
                target.style.cursor = "pointer";
                target.src = "images/icn_target_on.png";
            });
            dojo.connect(target, 'mouseout', function() {
                target.style.cursor = "default";
                target.src = "images/icn_target_off.png";
            });
        },

        save: function(toolbar, el) {
            var save = dojo.byId(el) || dojo.byId("toolbar_save");
            dojo.connect(save, 'mouseover', function() {
                save.src = "images/icn_save_on.png";
            });

            dojo.connect(save, 'mouseout', function() {
                save.src = "images/icn_save.png";
            });

            dojo.connect(save, 'click', function() {
                bespin.publish("bespin:editor:savefile");
            });
        },

        close: function(toolbar, el) {
            var close = dojo.byId(el) || dojo.byId("toolbar_close");
            dojo.connect(close, 'mouseover', function() {
                close.src = "images/icn_close_on.png";
            });

            dojo.connect(close, 'mouseout', function() {
                close.src = "images/icn_close.png";
            });

            dojo.connect(close, 'click', function() {
                bespin.publish("bespin:editor:closefile");
            });
        },

        undo: function(toolbar, el) {
            var undo = dojo.byId(el) || dojo.byId("toolbar_undo");
            dojo.connect(undo, 'mouseover', function() {
                undo.src = "images/icn_undo_on.png";
            });

            dojo.connect(undo, 'mouseout', function() {
                undo.src = "images/icn_undo.png";
            });

            dojo.connect(undo, 'click', function() {
                toolbar.editor.ui.actions.undo();
            });
        },

        redo: function(toolbar, el) {
            var redo = dojo.byId(el) || dojo.byId("toolbar_undo");

            dojo.connect(redo, 'mouseover', function() {
                redo.src = "images/icn_redo_on.png";
            });

            dojo.connect(redo, 'mouseout', function() {
                redo.src = "images/icn_redo.png";
            });

            dojo.connect(redo, 'click', function() {
                toolbar.editor.ui.actions.redo();
            });
        },

        preview: function(toolbar, el) {
            var preview = dojo.byId(el) || dojo.byId("toolbar_preview");

            dojo.connect(preview, 'mouseover', function() {
                preview.src = "images/icn_preview_on.png";
            });

            dojo.connect(preview, 'mouseout', function() {
                preview.src = "images/icn_preview.png";
            });

            dojo.connect(preview, 'click', function() {
                bespin.publish("bespin:editor:preview"); // use default file
            });
        },

        fontsize: function(toolbar, el) {
            var fontsize = dojo.byId(el) || dojo.byId("toolbar_fontsize");

            dojo.connect(fontsize, 'mouseover', function() {
                fontsize.src = "images/icn_fontsize_on.png";
            });

            dojo.connect(fontsize, 'mouseout', function() {
                fontsize.src = "images/icn_fontsize.png";
            });

            // Change the font size between the small, medium, and large settings
            dojo.connect(fontsize, 'click', function() {
                toolbar.currentFontSize = (toolbar.currentFontSize > 2) ? 1 : toolbar.currentFontSize + 1;
                bespin.publish("bespin:settings:set:fontsize", [{ value: toolbar.FONT_SIZES[toolbar.currentFontSize] }]);
            });
        }
    }
});