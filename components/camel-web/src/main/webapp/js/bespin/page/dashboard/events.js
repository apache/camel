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
 
dojo.provide("bespin.page.dashboard.events");

// After a project is imported or created, do a list
bespin.subscribe("bespin:project:imported", function(event) {
    bespin.page.dashboard.refreshProjects(); // get projects
});

bespin.subscribe("bespin:project:set", function(event) {
    bespin.get('editSession').project = event.project; // set it in the session
    
    if (!event.fromDashboardItemSelected) {
        // selects the project in the tree and fire the itemselected event    
        bespin.page.dashboard.tree.lists[0].selectItemByText(event.project);
        bespin.page.dashboard.tree.itemSelected({thComponent: bespin.page.dashboard.tree.lists[0], item: bespin.page.dashboard.tree.lists[0].selected});
    }
});

bespin.subscribe("bespin:project:create", function(event) {
    bespin.page.dashboard.refreshProjects(); // get projects
});

bespin.subscribe("bespin:project:delete", function(event) {
    bespin.page.dashboard.refreshProjects(); // get projects
});

// ** {{{ Event: bespin:session:status }}} **
// 
// Observe a request for session status
bespin.subscribe("bespin:session:status", function(event) {
    var editSession = bespin.get('editSession');
    var msg = 'Hey ' + editSession.username;
    
    if (editSession.project) {
        msg += ', you appear to be highlighting the project ' + editSession.project;
    } else {
        msg += ", you haven't select a project yet.";
    }
    
    bespin.publish("bespin:cmdline:showinfo", { msg: msg });
});

// ** {{{ Event: bespin:editor:newfile }}} **
// 
// Observe a request for a new file to be created
bespin.subscribe("bespin:editor:newfile", function(event) {
    var project = event.project;
    if (!project) {
        bespin.publish("bespin:cmdline:showinfo", { msg: 'The new file action requires a project' });
        return;
    }
    
    var newfilename = event.newfilename || "new.txt";
    
    bespin.util.navigate.editor(project, newfilename);
});
