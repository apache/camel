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

// = Session =
//
// This session module provides functionality that both stores session information
// and handle collaboration.
//
// This module includes:
//
// * {{{ bespin.client.session.EditSession }}}: Wraps a file edit session
// * {{{ bespin.client.session.SyncHelper }}}: Deals with syncing edits back to the server

dojo.provide("bespin.client.session");

// ** {{{ bespin.client.session.EditSession }}} **
//
// EditSession represents a file edit session with the Bespin back-end server. It is responsible for
// sending changes to the server as well as receiving changes from the server and mutating the document
// model with received changes.

dojo.declare("bespin.client.session.EditSession", null, {
    constructor: function(editor) {        
        this.editor = editor;
        this.collaborate = false;
    },

    setUserinfo: function(userinfo) {
        this.username = userinfo.username;
        this.amountUsed = userinfo.amountUsed;
        this.quota = userinfo.quota;
    },

    checkSameFile: function(project, path) {
        return ((this.project == project) && (this.path == path));
    },

    startSession: function(project, path, username) {
        this.project = project;
        this.path = path;
        if (!this.username) this.username = username;

        if (this.collaborate) {
            // Extreme suckage: This starts a sync session with the server.
            // All it's really doing is talking to a sync service which is totally
            // separate from the file storage API. They need merging, and then we
            // should be able to ditch whatever loads the text into the editor
            mobwrite.share(bespin.get('editSession'));
        }
    },

    stopSession: function() {
        this.project = undefined;
        this.path = undefined;
    }
});