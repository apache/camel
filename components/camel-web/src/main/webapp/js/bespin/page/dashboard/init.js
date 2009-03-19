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

dojo.provide("bespin.page.dashboard.init");  

// = Dashboard =
//
// This file is the dashboard code that is loaded via script src
// from /dashboard.html.

(function() {
    var heightDiff;
    var projects;
    var scene;
    var tree;
    var infoPanel;
    var currentProject;
    var go = bespin.util.navigate; // short cut static method 
    var bd = bespin.page.dashboard;

    var server;
    var settings;
    var editSession;
    var files;
    var commandLine;
    
    dojo.mixin(bespin.page.dashboard, {
        tree: null,
        lastSelectedPath: null,
        
        sizeCanvas: function(canvas) {
            if (!heightDiff) {
                heightDiff = dojo.byId("header").clientHeight + dojo.byId("subheader").clientHeight + dojo.byId("footer").clientHeight;
            }
            var height = window.innerHeight - heightDiff + 11;
            dojo.attr(canvas, { width: window.innerWidth, height: height });
        },
        
        loggedIn: function(userinfo)  {
            editSession.setUserinfo(userinfo);

            server.list(null, null, bd.displayProjects); // get projects
            server.listOpen(bd.displaySessions); // get sessions
        },

        notLoggedIn: function(xhr) {
            go.home();
        },

        prepareFilesForTree: function(files) {
            if (files.length == 0) return [];

            var fdata = [];
            for (var i = 0; i < files.length; i++) {
                var name = files[i].name;
                if (/\/$/.test(name)) {
                    name = name.substring(0, name.length - 1);
                    var contents = bd.fetchFiles;
                    fdata.push({ name: name, contents: contents });
                } else {
                    fdata.push({ name: name });
                }
            }

            return fdata;
        },

        getFilePath: function(treePath) {
            var filepath = "";

            for (var i = 0; i < treePath.length; i++) {
                if (treePath[i] && treePath[i].name) {
                    filepath += treePath[i].name + ((i < treePath.length - 1) ? "/" : "");
                }
            }
            return filepath;
        },
        
        fetchFiles: function(path, tree) {            
            var filepath = bd.getFilePath(path);

            server.list(filepath, null, function(files) {
                tree.updateData(path[path.length - 1], bd.prepareFilesForTree(files));
                tree.render();
            });
        },

        displaySessions: function(sessions) {
            infoPanel.removeAll();

            for (var project in sessions) {
                for (var file in sessions[project]) {
                    var lastSlash = file.lastIndexOf("/");
                    var path = (lastSlash == -1) ? "" : file.substring(0, lastSlash);
                    var name = (lastSlash == -1) ? file : file.substring(lastSlash + 1);

                    var panel = new bespin.page.dashboard.components.BespinSessionPanel({ filename: name, project: project, path: path });
                    infoPanel.add(panel);
                    panel.bus.bind("dblclick", panel, function(e) {
                        var newTab = e.shiftKey;
                        go.editor(e.thComponent.session.project, e.thComponent.session.path + (e.thComponent.session.path != '' ? '/' : '' ) + e.thComponent.session.filename, newTab);
                    });
                }
            }
            infoPanel.render();
        },

        restorePath: function(newPath) {            
            bd.lastSelectedPath = bd.lastSelectedPath || '';
            newPath = newPath || '';
            var oldPath = bd.lastSelectedPath;
            bd.lastSelectedPath = newPath;
                        
            if (newPath == oldPath && newPath != '') return;     // the path has not changed

            newPath = newPath.split('/');
            oldPath = oldPath.split('/');
            currentProject = newPath[0];

            tree.lists[0].selectItemByText(newPath[0]);    // this also perform a rendering of the project.list
            scene.renderAllowed = false;

            var sameLevel = 1;  // the value is 1 and not 0, as the first list (the project list) is not affected!
            while (sameLevel < Math.min(newPath.length, oldPath.length) && newPath[sameLevel] == oldPath[sameLevel] && newPath[sameLevel] != '') {
                sameLevel ++;
            }
                                                                                                  
            var fakePath = new Array(newPath.length);
            for (var x = 1; x < newPath.length; x++) {
                var fakeItem = new Object();
                fakeItem.name = newPath[x];
                if (x != newPath.length - 1) {
                    fakeItem.contents = 'fake';   
                }
                if (x > bd.tree.lists.length - 1) {
                   bd.tree.showChildren(null, new Array(fakeItem)); 
                }  
                if (newPath[x] != '') {
                    bd.tree.lists[x].selectItemByText(newPath[x]);   
                }
                fakePath[x] = fakeItem;
            }
            
            if (newPath.length <= bd.tree.lists.length) {
                bd.tree.removeListsFrom(newPath.length);
            }
                                            
            var contentsPath = new Array(newPath.length);
            var countSetupPaths = sameLevel;

            // this function should stay here, as this funciton is accessing "pathContents" and "countSetupPaths"
            var displayFetchedFiles = function(files) {
                // "this" is the callbackData object!
                var contents =  bd.prepareFilesForTree(files);
                if (this.index != 0) {
                    contentsPath[this.index] = contents;
                }
                
                bd.tree.replaceList(this.index, contents);
                bd.tree.lists[this.index].selectItemByText(fakePath[this.index].name);
                countSetupPaths ++;
                
                if (countSetupPaths == newPath.length) {
                    for (var x = 0; x < newPath.length - 1; x++) {
                        // when the path is not restored from the root, then there are contents without contents!
                        if (contentsPath[x + 1]) {
                            bd.tree.lists[x].selected.contents = contentsPath[x + 1];                            
                        }
                    }
                }
            }
            
            // get the data for the lists
            for (var x = sameLevel; x < newPath.length; x++) {                                                
                var selected = bd.tree.lists[x - 1].selected;
                if (selected && selected.contents && dojo.isArray(selected.contents)) {
                    // restore filelist from local memory (the filelists was ones fetched)
                    if (x > bd.tree.lists.length - 1) {
                        bd.tree.showChildren(null, selected.contents)
                    } else {
                        bd.tree.replaceList(x, selected.contents);
                    }
                    bd.tree.lists[x].selectItemByText(fakePath[x].name);                        
                    countSetupPaths ++;
                } else {
                    // load filelist form server                                                            
                    var filepath = currentProject + "/" + bd.getFilePath(fakePath.slice(1, x));
                    server.list(filepath, null, dojo.hitch({index: x}, displayFetchedFiles));                    
                }
            }
            
            // deselect lists if needed
            for (var x = newPath.length; x < tree.lists.length; x++) {
                delete tree.lists[x].selected;
            }
            
            scene.renderAllowed = true;
            scene.render();
        },

        displayProjects: function(projectItems) {
            for (var i = 0; i < projectItems.length; i++) {
                projectItems[i] = { name: projectItems[i].name.substring(0, projectItems[i].name.length - 1) , contents: bd.fetchFiles};
            }
            tree.replaceList(0, projectItems);
                                    
            // Restore the last selected file
            var path =  (new bespin.client.settings.URL()).get('path');
            if (!bd.lastSelectedPath) {
                bd.restorePath(path);
            } else {
                scene.render();                
            }
        },

        refreshProjects: function() {
            server.list(null, null, bd.displayProjects);
        }
    }); 
    
    dojo.connect(window, "resize", function() {
        bd.sizeCanvas(dojo.byId("canvas"));
    });
    
    dojo.addOnLoad(function() {
        bd.sizeCanvas(dojo.byId("canvas"));

        dojo.forEach(['subheader', 'header'], function(i) { dojo.setSelectable(i, false); });

        bespin.displayVersion(); // display the version on the page

        scene = new th.Scene(dojo.byId("canvas"));  

        tree = new th.components.HorizontalTree({ style: {
            backgroundColor: "rgb(76, 74, 65)",
            backgroundColorOdd: "rgb(82, 80, 71)",
            font: "9pt Tahoma",
            color: "white",
            scrollTopImage: dojo.byId("vscroll_track_top"),
            scrollMiddleImage: dojo.byId("vscroll_track_middle"),
            scrollBottomImage: dojo.byId("vscroll_track_bottom"),
            scrollHandleTopImage: dojo.byId("vscroll_top"),
            scrollHandleMiddleImage: dojo.byId("vscroll_middle"),
            scrollHandleBottomImage: dojo.byId("vscroll_bottom"),
            scrollUpArrow: dojo.byId("vscroll_up_arrow"),
            scrollDownArrow: dojo.byId("vscroll_down_arrow")
        }});

        bd.tree = tree;
        
        var renderer = new th.components.Label({ style: { border: new th.borders.EmptyBorder({ size: 3 }) } });
        renderer.old_paint = renderer.paint;
        renderer.paint = function(ctx) {
            var d = this.d();

            if (this.selected) {
                ctx.fillStyle = "rgb(177, 112, 20)";
                ctx.fillRect(0, 0, d.b.w, 1);

                var gradient = ctx.createLinearGradient(0, 0, 0, d.b.h);
                gradient.addColorStop(0, "rgb(172, 102, 1)");
                gradient.addColorStop(1, "rgb(219, 129, 1)");
                ctx.fillStyle = gradient;
                ctx.fillRect(0, 1, d.b.w, d.b.h - 2);

                ctx.fillStyle = "rgb(160, 95, 1)";
                ctx.fillRect(0, d.b.h - 1, d.b.w, 1);
            }

            if (this.item.contents) {
                renderer.styleContext(ctx);
                var metrics = ctx.measureText(">");
                ctx.fillText(">", d.b.w - metrics.width - 5, d.b.h / 2 + (metrics.ascent / 2) - 1);
            }

            this.old_paint(ctx);
        };
        tree.renderer = renderer;
        
        var projectLabel = new th.components.Label({ text: "Projects", style: { color: "white", font: "8pt Tahoma" } });
        projectLabel.oldPaint = projectLabel.paint;
        projectLabel.paint = function(ctx) {
            var d = this.d();

            ctx.fillStyle = "rgb(51, 50, 46)";
            ctx.fillRect(0, 0, d.b.w, 1);

            ctx.fillStyle = "black";
            ctx.fillRect(0, d.b.h - 1, d.b.w, 1);

            var gradient = ctx.createLinearGradient(0, 1, 0, d.b.h - 2);
            gradient.addColorStop(0, "rgb(39, 38, 33)");
            gradient.addColorStop(1, "rgb(22, 22, 19)");
            ctx.fillStyle = gradient;
            ctx.fillRect(0, 1, d.b.w, d.b.h - 2);

            this.oldPaint(ctx);
        };
        
        // add the the former project tree
        bd.tree.showChildren(null, [{name: '<Projects>'}]);
        tree.lists[0].label = projectLabel; 
        tree.lists[0].label.height = 16;
        tree.lists[0].allowDeselection = false;
        tree.lists[0].style = { backgroundColor: "rgb(61, 59, 52)", color: "white", font: "8pt Tahoma" };

        var topPanel = new th.components.Panel();
        topPanel.add([ tree ]);
        topPanel.layout = function() {
            var d = this.d();
            tree.bounds = { x: d.i.l, y: d.i.t, width: d.b.w - d.i.w, height: d.b.h - d.i.h };
        };

        infoPanel = new th.components.ExpandingInfoPanel({ style: { backgroundColor: "rgb(61, 59, 52)" } });

        var splitPanel = new th.components.SplitPanel({ id: "splitPanel", attributes: {
            orientation: th.VERTICAL,
            regions: [ { size: "75%", contents: topPanel }, { size: "25%", contents: infoPanel } ]
        } });

        splitPanel.attributes.regions[0].label = new th.components.Label({
                text: "Open Sessions",
                style: {
                    color: "white",
                    font: "9pt Tahoma"
                },
                border: new th.borders.EmptyBorder({ size: 4 })
        });

        scene.root.add(splitPanel);

        scene.render();

        scene.bus.bind("dblclick", tree, function(e) {
            var newTab = e.shiftKey;
            var path = tree.getSelectedPath();
            if (path.length == 0 || path[path.length - 1].contents) return; // don't allow directories either
            go.editor(currentProject, bd.getFilePath(path.slice(1, path.length)), newTab);
        });

        scene.bus.bind("itemSelected", tree, function(e) {
            var pathSelected = tree.getSelectedPath(true);
            bespin.page.dashboard.lastSelectedPath = pathSelected;
            location.hash = '#path=' + pathSelected;
        })

        scene.bus.bind("itemselected", tree.lists[0], function(e) {
            currentProject = e.item.name;
            bespin.publish("bespin:project:set", { project: currentProject, suppressPopup: true, fromDashboardItemSelected: true });
        });

        // setup the command line
        server = bespin.register('server', new bespin.client.Server());
        settings = bespin.register('settings', new bespin.client.settings.Core());
        editSession = bespin.register('editSession', new bespin.client.session.EditSession());
        files = bespin.register('files', new bespin.client.FileSystem());
        commandLine = bespin.register('commandLine', new bespin.cmd.commandline.Interface('command', bespin.cmd.dashboardcommands.Commands));

        // Handle jumping to the command line
        dojo.connect(document, "onkeypress", function(e) {
            var handled = commandLine.handleCommandLineFocus(e);
            if (handled) return false;
        });

        // get logged in name; if not logged in, display an error of some kind
        server.currentuser(bd.loggedIn, bd.notLoggedIn);   
        
        // provide history for the dashboard
        bespin.subscribe("bespin:url:changed", function(e) {
            var pathSelected =  (new bespin.client.settings.URL()).get('path');
            bespin.page.dashboard.restorePath(pathSelected);
        });
    });
})();