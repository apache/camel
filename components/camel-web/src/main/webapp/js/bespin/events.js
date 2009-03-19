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
 
dojo.provide("bespin.events");

dojo.require("bespin.util.util");

// = Event Bus =
//
// Global home for event watching where it doesn't fit using the pattern
// of custom events tied to components themselves such as:
//
// * {{{bespin.cmd.commandline.Events}}}
// * {{{bespin.client.settings.Events}}}

// ** {{{ Event: bespin:editor:titlechange }}} **
// 
// Observe a title change event and then... change the document.title!
bespin.subscribe("bespin:editor:titlechange", function(event) {
    var title;
    if (event.filename) title = event.filename + ' - editing with Bespin';
    else if (event.title) title = event.title;
    else title = 'Bespin &raquo; Code in the Cloud';

    document.title = title;
});

// ** {{{ Event: bespin:editor:evalfile }}} **
// 
// Load up the given file and try to run it
bespin.subscribe("bespin:editor:evalfile", function(event) {
    var project  = event.project;
    var filename = event.filename;
    var scope    = event.scope || bespin.events.defaultScope();

    if (!project || !filename) {
        bespin.get('commandLine').showInfo("Please, I need a project and filename to evaulate");
        return;
    }

    bespin.get('files').loadFile(project, filename, function(file) {
        with (scope) { // wow, using with. crazy.
            try {
                bespin.publish("bespin:cmdline:suppressinfo");
                eval(file.content);
                bespin.publish("bespin:cmdline:unsuppressinfo");
            } catch (e) {
                bespin.get('commandLine').showInfo("There is a error trying to run " + filename + " in project " + project + ":<br><br>" + e);
            }
        }
    }, true);
});

// ** {{{ Event: bespin:editor:preview }}} **
// 
// Preview the given file in a browser context
bespin.subscribe("bespin:editor:preview", function(event) {
    var editSession = bespin.get('editSession');
    var filename = event.filename || editSession.path;  // default to current page
    var project  = event.project  || editSession.project; 

    // Make sure to save the file first
    bespin.publish("bespin:editor:savefile", {
        filename: filename
    });

    if (filename) {
        window.open(bespin.util.path.combine("preview/at", project, filename));
    }
});

// ** {{{ Event: bespin:editor:closefile }}} **
// 
// Close the given file (wrt the session)
bespin.subscribe("bespin:editor:closefile", function(event) {
    var editSession = bespin.get('editSession');
    var filename = event.filename || editSession.path;  // default to current page
    var project  = event.project  || editSession.project;   
    
    bespin.get('files').closeFile(project, filename, function() {
        bespin.publish("bespin:editor:closedfile", { filename: filename }); 
        
        // if the current file, move on to a new one
        if (filename == editSession.path) bespin.publish("bespin:editor:newfile");    

        bespin.publish("bespin:cmdline:showinfo", { msg: 'Closed file: ' + filename });
    });
});

// ** {{{ Event: bespin:editor:config:run }}} **
//
// Load and execute the user's config file
bespin.subscribe("bespin:editor:config:run", function(event) {
    bespin.publish("bespin:editor:evalfile", {
        project: bespin.userSettingsProject,
        filename: "config.js"
    });
});

// ** {{{ Event: bespin:editor:config:edit }}} **
// 
// Open the users special config file
bespin.subscribe("bespin:editor:config:edit", function(event) {
    if (!bespin.userSettingsProject) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "You don't seem to have a user project. Sorry." });
        return;
    }

    bespin.publish("bespin:editor:openfile", {
        project: bespin.userSettingsProject,
        filename: "config.js"
    });
});

// ** {{{ Event: bespin:cmdline:executed }}} **
// 
// Set the last command in the status window
bespin.subscribe("bespin:cmdline:executed", function(event) {
    var commandname = event.command.name;
    var args        = event.args;

    dojo.byId('message').innerHTML = "last cmd: <span title='" + commandname + " " + args + "'>" + commandname + "</span>"; // set the status message area
});

// ** {{{ Event: bespin:commands:load }}} **
// 
// Create a new command in your special command directory
bespin.subscribe("bespin:commands:load", function(event) {
    var commandname = event.commandname;
    
    if (!commandname) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "Please pass me a command name to load." });
        return;
    }

    bespin.get('files').loadFile(bespin.userSettingsProject, "commands/" + commandname + ".js", function(file) {
        try {
            eval('bespin.get("commandLine").addCommands([' + file.content + '])');
        } catch (e) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Something is wrong about the command:<br><br>" + e });
        }
    }, true);
});

// ** {{{ Event: bespin:commands:edit }}} **
// 
// Edit the given command
bespin.subscribe("bespin:commands:edit", function(event) {
    var commandname = event.commandname;
    
    if (!bespin.userSettingsProject) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "You don't seem to have a user project. Sorry." });
        return;
    }

    if (!commandname) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "Please pass me a command name to edit." });
        return;
    }
    
    bespin.publish("bespin:editor:forceopenfile", {
        project: bespin.userSettingsProject,
        filename: "commands/" + commandname + ".js",
        content: "{\n    name: '" + commandname + "',\n    takes: [YOUR_ARGUMENTS_HERE],\n    preview: 'execute any editor action',\n    execute: function(self, args) {\n\n    }\n}"
    });
});

// ** {{{ Event: bespin:commands:list }}} **
// 
// List the custom commands that a user has
bespin.subscribe("bespin:commands:list", function(event) {
    if (!bespin.userSettingsProject) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "You don't seem to have a user project. Sorry." });
        return;
    }

    bespin.get('server').list(bespin.userSettingsProject, 'commands/', function(commands) {
        var output;
        
        if (!commands || commands.length < 1) {
            output = "You haven't installed any custom commands.<br>Want to <a href='https://wiki.mozilla.org/Labs/Bespin/Roadmap/Commands'>learn how?</a>";
        } else {
            output = "<u>Your Custom Commands</u><br/><br/>";
            
            output += dojo.map(dojo.filter(commands, function(file) {
                return bespin.util.endsWith(file.name, '\\.js');
            }), function(c) { return c.name.replace(/\.js$/, ''); }).join("<br>");
        }
        
        bespin.publish("bespin:cmdline:showinfo", { msg: output });
    });
});

// ** {{{ Event: bespin:commands:delete }}} **
// 
// Delete the named command
bespin.subscribe("bespin:commands:delete", function(event) {
    var commandname = event.commandname;
    
    var editSession = bespin.get('editSession');
    var files = bespin.get('files');

    if (!bespin.userSettingsProject) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "You don't seem to have a user project. Sorry." });
        return;
    }

    if (!commandname) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "Please pass me a command name to delete." });
        return;
    }

    var commandpath = "commands/" + commandname + ".js";
    
    files.removeFile(bespin.userSettingsProject, commandpath, function() {
        if (editSession.checkSameFile(bespin.userSettingsProject, commandpath)) bespin.get('editor').model.clear(); // only clear if deleting the same file
        bespin.publish("bespin:cmdline:showinfo", { msg: 'Removed command: ' + commandname, autohide: true });
    }, function(xhr) {
        bespin.publish("bespin:cmdline:showinfo", { 
            msg: "Wasn't able to remove the command <b>" + commandname + "</b><br/><em>Error</em> (probably doesn't exist): " + xhr.responseText, 
            autohide: true 
        });
    });
});

// ** {{{ Event: bespin:directory:create }}} **
// 
// Create a new directory
bespin.subscribe("bespin:directory:create", function(event) {
    var editSession = bespin.get('editSession');
    var files = bespin.get('files');

    var project = event.project || editSession.project;
    var path = event.path || '';
    
    files.makeDirectory(project, path, function() {
        if (path == '') bespin.publish("bespin:project:set", { project: project });
        bespin.publish("bespin:cmdline:showinfo", { 
            msg: 'Successfully created directory: [project=' + project + ', path=' + path + ']', autohide: true });
    }, function() {
        bespin.publish("bespin:cmdline:showinfo", {
            msg: 'Unable to delete directory: [project=' + project + ', path=' + path + ']' + project, autohide: true });
    });
});

// ** {{{ Event: bespin:directory:delete }}} **
// 
// Delete a directory
bespin.subscribe("bespin:directory:delete", function(event) {
    var editSession = bespin.get('editSession');
    var files = bespin.get('files');

    var project = event.project || editSession.project;
    var path = event.path || '';
    
    if (project == bespin.userSettingsProject && path == '/') return; // don't delete the settings project
    
    files.removeDirectory(project, path, function() {
        if (path == '/') bespin.publish("bespin:project:set", { project: '' }); // reset
        bespin.publish("bespin:cmdline:showinfo", { 
            msg: 'Successfully deleted directory: [project=' + project + ', path=' + path + ']', autohide: true });
    }, function() {
        bespin.publish("bespin:cmdline:showinfo", {
            msg: 'Unable to delete directory: [project=' + project + ', path=' + path + ']', autohide: true });
    });
});

// ** {{{ Event: bespin:project:create }}} **
// 
// Create a new project
bespin.subscribe("bespin:project:create", function(event) {
    var project = event.project || bespin.get('editSession').project;
    
    bespin.publish("bespin:directory:create", { project: project });
});

// ** {{{ Event: bespin:project:delete }}} **
// 
// Delete a project
bespin.subscribe("bespin:project:delete", function(event) {
    var project = event.project;
    if (!project || project == bespin.userSettingsProject) return; // don't delete the settings project
    
    bespin.publish("bespin:directory:delete", { project: project });
});

// ** {{{ Event: bespin:project:rename }}} **
// 
// Rename a project
bespin.subscribe("bespin:project:rename", function(event) {
    var currentProject = event.currentProject;
    var newProject = event.newProject;
    if ( (!currentProject || !newProject) || (currentProject == newProject) ) return;
    
    bespin.get('server').renameProject(currentProject, newProject, {
        call: function() {
            bespin.publish("bespin:project:set", { project: newProject });
        },
        onFailure: function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: 'Unable to rename project from ' + currentProject + " to " + newProject + "<br><br><em>Are you sure that the " + currentProject + " project exists?</em>", autohide: true });
        }
    });
});


// ** {{{ Event: bespin:project:import }}} **
// 
// Import a project
bespin.subscribe("bespin:project:import", function(event) {
    var project = event.project;
    var url = event.url;

    bespin.get('server').importProject(project, url, { call: function() {
        bespin.publish("bespin:cmdline:showinfo", { msg: "Project " + project + " imported from:<br><br>" + url, autohide: true });
    }, onFailure: function(xhr) {
        bespin.publish("bespin:cmdline:showinfo", { msg: "Unable to import " + project + " from:<br><br>" + url + ".<br><br>Maybe due to: " + xhr.responseText });
    }});
});



// == Events
// 
// ** {{{ bespin.events }}} **
//
// Helpers for the event subsystem

// ** {{{ bespin.events.toFire }}} **
//
// Given an {{{eventString}}} parse out the arguments and configure an event object
//
// Example events:
//
// * {{{bespin:cmdline:execute;name=ls,args=bespin}}}
// * {{{bespin:cmdline:execute}}} 
    
dojo.mixin(bespin.events, {
    toFire: function(eventString) {
        var event = {};
        if (!eventString.indexOf(';')) { // just a plain command with no args
            event.name = eventString;
        } else { // split up the args
            var pieces = eventString.split(';');
            event.name = pieces[0];
            event.args = bespin.util.queryToObject(pieces[1], ',');
        }
        return event;
    }
});

// ** {{{ bespin.events.defaultScope }}} **
//
// Return a default scope to be used for evaluation files
bespin.events.defaultScope = function() {
    if (bespin.events._defaultScope) return bespin.events._defaultScope;
    
    var scope = {
        bespin: bespin,
        include: function(file) {
            bespin.publish("bespin:editor:evalfile", {
                project: bespin.userSettingsProject,
                filename: file
            });
        },
        tryTocopyComponent: function(id) {
            bespin.withComponent(id, dojo.hitch(this, function(component) {
                this.id = component;
            }));
        },
        require: dojo.require,
        publish: bespin.publish,
        subscribe: bespin.subscribe
    };

    bespin.withComponent('commandLine', function(commandLine) {
        scope.commandLine = commandLine;
        scope.execute = function(cmd) {
            commandLine.executeCommand(cmd);
        };
    });

    scope.tryTocopyComponent('editor');
    scope.tryTocopyComponent('editSession');
    scope.tryTocopyComponent('files');
    scope.tryTocopyComponent('server');
    scope.tryTocopyComponent('toolbar');

    bespin.events._defaultScope = scope; // setup the short circuit

    return bespin.events._defaultScope;
};

// ** {{{ Event: bespin:network:followers }}} **
// Get a list of our followers
bespin.subscribe("bespin:network:followers", function() {
    bespin.get('server').followers({
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Following " + data });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to retrieve followers. Maybe due to: " + xhr.responseText });
        }
    });
});

// ** {{{ Event: bespin:network:follow }}} **
// Add to the list of users that we follow
bespin.subscribe("bespin:network:follow", function(usernames) {
    bespin.get('server').follow(usernames, {
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Following " + data });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to retrieve followers. Maybe due to: " + xhr.responseText });
        }
    });
});

// ** {{{ Event: bespin:network:unfollow }}} **
// Remove users from the list that we follow
bespin.subscribe("bespin:network:unfollow", function(usernames) {
    bespin.get('server').unfollow(usernames, {
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Following " + data });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to retrieve followers. Maybe due to: " + xhr.responseText });
        }
    });
});

// ** {{{ Event: bespin:groups:list:all }}} **
// Get a list of our groups
bespin.subscribe("bespin:groups:list:all", function() {
    bespin.get('server').groupListAll({
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Known groups " + data });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to retrieve groups. Maybe due to: " + xhr.responseText });
        }
    });
});

// ** {{{ Event: bespin:groups:list }}} **
// Get a list of group members
bespin.subscribe("bespin:groups:list", function(group) {
    bespin.get('server').groupList(group, {
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Members of " + group + ": " + data });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to retrieve group members. Maybe due to: " + xhr.responseText });
        }
    });
});

// ** {{{ Event: bespin:groups:remove:all }}} **
// Remove a group and all its members
bespin.subscribe("bespin:groups:remove:all", function(group) {
    bespin.get('server').groupRemoveAll(group, {
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Removed group " + group });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to retrieve group members. Maybe due to: " + xhr.responseText });
        }
    });
});

// ** {{{ Event: bespin:groups:add }}} **
// Add to members of a group
bespin.subscribe("bespin:groups:add", function(group, users) {
    bespin.get('server').groupAdd(group, users, {
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Members of " + group + ": " + data });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to add to group members. Maybe due to: " + xhr.responseText });
        }
    });
});

// ** {{{ Event: bespin:groups:remove }}} **
// Add to members of a group
bespin.subscribe("bespin:groups:remove", function(group, users) {
    bespin.get('server').groupRemove(group, users, {
        call:function(data) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Members of " + group + ": " + data });
        },
        onFailure:function(xhr) {
            bespin.publish("bespin:cmdline:showinfo", { msg: "Failed to remove to group members. Maybe due to: " + xhr.responseText });
        }
    });
});
