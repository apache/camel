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

dojo.provide("bespin.editor.quickopen");

dojo.declare("bespin.editor.quickopen.Panel", th.components.Panel, {
    constructor: function(parms) {
        if (!parms) parms = {};

        this.list = new th.components.List({ allowDeselection: false, style: { backgroundColor: "#D5D0C0", color: "black", font: "8pt Tahoma" } });

        var renderer = new th.components.Label({ style: { border: new th.borders.EmptyBorder({ size: 3 }) } });
        renderer.old_paint = renderer.paint;
        renderer.paint = function(ctx) {
            var d = this.d();

            if (this.selected) {
                ctx.fillStyle = "#DDAC7C";
                ctx.fillRect(0, 0, d.b.w, d.b.h);
            }

            this.old_paint(ctx);
        };
        var list = this.list;
        list.renderer = renderer;
        list.oldGetRenderer = list.getRenderer;
        list.getRenderer = function(rctx) {
            var label = list.oldGetRenderer(rctx);
            label.attributes.text = rctx.item.name;
            return label;
        }
        
        this.scrollbar = new th.components.Scrollbar({ attributes: { orientation: th.HORIZONTAL } });
        this.scrollbar.style.backgroundColor = "#413D35";
        this.scrollbar.loadImages('../images/','dash_vscroll');
        this.scrollbar.scrollable = list;

        this.pathLabel = new th.components.Label({ style: { backgroundColor: "#D5D0C0", color: "black", font: "8pt Tahoma" }, text: "Select item!" });
        this.add([ this.list, this.scrollbar, this.pathLabel]);

        // this is a closed container
        delete this.add;
        delete this.remove;
    },

    layout: function() {
        var d = this.d();

        var y = d.i.t;

        lh = this.pathLabel.getPreferredHeight(d.b.w);

        y = 28;
        var sw = 14;
        var sh = d.b.h - d.i.b - y - lh;
        var innerWidth = d.b.w - d.i.w - sw;
        
        this.list.bounds = { x: d.i.l, y: y, width: innerWidth + sw, height: sh - 1 };
        this.pathLabel.bounds = { x: d.i.l, y: y + sh, width: innerWidth + sw, height: lh };
    },
    
    paintSelf: function(ctx) {        
        ctx.fillStyle = "#86857F";
        ctx.fillRect(0, 0, 220, 28);
        
        ctx.lineWidth = 2;
        ctx.strokeStyle = "black";
        ctx.beginPath();              
        ctx.moveTo(0, 28);
        ctx.lineTo(220, 28);
        var y = this.list.bounds.y + this.list.bounds.height + 1;
        ctx.moveTo(0, y);
        ctx.lineTo(220, y);
        ctx.closePath();
        ctx.stroke();
    }
});

dojo.declare("bespin.editor.quickopen.API", null, {
    constructor: function() {                
        this.lastText = '';
        this.requestFinished = true;
        this.preformNewRequest = false;
        
        // create the Window!
        this.panel = new bespin.editor.quickopen.Panel();
        this.window = new th.Window({
            title: 'Find Files', 
            top: 100, 
            left: 200, 
            width: 220, 
            height: 270, 
            userPanel: this.panel,
            containerId: 'quickopen',
            closeOnClickOutside: true, 
        });
        this.window.centerUp(); // center the window, but more a "human" center

        // add the input field to the window
        var input = document.createElement("input");
        input.type = "text";
        input.id = 'quickopen_text';
        this.window.container.appendChild(input);
        this.input = input;
        
        // item selected in the list => show full path in label
        this.window.scene.bus.bind("itemselected", this.panel.list, dojo.hitch(this, function(e) {
            this.panel.pathLabel.attributes.text = e.item.filename;
            this.window.layoutAndRender();
        }));
        
        // item double clicked => load this file
        this.window.scene.bus.bind("dblclick", this.panel.list, dojo.hitch(this, function(e) {
            var item = this.panel.list.selected;
            if (!item)  return;
            
            // save the current file and load up the new one
            bespin.publish("bespin:editor:savefile", {});
            bespin.publish("bespin:editor:openfile", { filename: item.filename });
            
            // adds the new opened file to the top of the openSessionFiles
            if (this.openSessionFiles.indexOf(item.filename) != -1) {
                this.openSessionFiles.splice(this.openSessionFiles.indexOf(item.filename), 1)
            }                
            this.openSessionFiles.unshift(item.filename);
            
            this.toggle();
        }));
                
        // handle ARROW_UP and ARROW_DOWN to select items in the list and other stuff
        dojo.connect(window, "keydown", dojo.hitch(this, function(e) {
            if (!this.window.isVisible) return;
            
            var key = bespin.util.keys.Key;
            
            if (e.keyCode == key.ARROW_UP) {
                this.panel.list.moveSelectionUp();
                dojo.stopEvent(e);
            } else if (e.keyCode == key.ARROW_DOWN) {
                this.panel.list.moveSelectionDown();
                dojo.stopEvent(e);
            } else if (e.keyCode == key.ENTER) {
                this.window.scene.bus.fire("dblclick", {}, this.panel.list);     
            } else if (e.keyCode == 'O'.charCodeAt() && e.altKey) {
                this.toggle();
                dojo.stopEvent(e);
            }
        }));
        
        // look at the seachinput => has it changed?
        dojo.connect(this.input, "keyup", dojo.hitch(this, function() {            
            if (this.lastText != this.input.value) {
                // the text has changed!
                if (this.requestFinished) {
                    this.requestFinished = false;
                    bespin.get('server').searchFiles(bespin.get('editSession').project, this.input.value, this.displayResult);
                } else {
                    this.preformNewRequest = true;
                }
                
                this.lastText = this.input.value;
            }
        }));
        
        // load the current opend files at startup
        bespin.subscribe('bespin:settings:loaded', function() {            
            bespin.get('server').listOpen(bespin.get('quickopen').displaySessions);
        });
    },
    
    toggle: function() {
        var quickopen = bespin.get('quickopen');
        quickopen.window.toggle();
        
        if (quickopen.window.isVisible) {
            quickopen.showFiles(quickopen.openSessionFiles);
            quickopen.input.value = '';
            quickopen.input.focus();
        } else {
            quickopen.lastText = '';
            quickopen.input.blur();
        }
    },
    
    showFiles: function(files, sortFiles) {
        var items = new Array();
        var quickopen = bespin.get('quickopen'); 
        var file;
        sortFiles = sortFiles || false;
                
        files = files.slice(0, 12);
                
        for (var x = 0; x < files.length; x++) {                
            file = files[x];
            var lastSlash = file.lastIndexOf("/");
            var path = (lastSlash == -1) ? "" : file.substring(0, lastSlash);
            var name = (lastSlash == -1) ? file : file.substring(lastSlash + 1);

            items.push({name: name, filename: file});                
        }
        
        if (sortFiles) {
            items.sort(function(a, b) {
                var x = a.name.toLowerCase();
                var y = b.name.toLowerCase();
                return ((x < y) ? -1 : ((x > y) ? 1 : 0));
            });
        }
        
        quickopen.panel.list.items = items;
        if (items.length != 0) {
            quickopen.panel.list.selectItemByText(items[0].name);
            quickopen.panel.pathLabel.attributes.text = items[0].filename;
        }
        quickopen.window.layoutAndRender();
    },
    
    displayResult: function(files) {
        var quickopen = bespin.get('quickopen');
        quickopen.showFiles(files);
        
        quickopen.requestFinished = true;
        
        if (quickopen.preformNewRequest) {
            quickopen.requestFinished = false;
            quickopen.preformNewRequest = false;
            bespin.get('server').searchFiles(bespin.get('editSession').project, quickopen.input.value, quickopen.displayResult);
        }
    },
    
    displaySessions: function(sessions) {
        var quickopen =  bespin.get('quickopen');        
        var currentProject = bespin.get('editSession').project;
        var currentFile = bespin.get('editSession').path;
        var items = new Array();

        var files = sessions[currentProject];
        for (var file in files) {
            if (currentFile == file) {
                currentFile = false;
            }
            items.push(file);
        }
        
        if (currentFile) {
            items.push(currentFile);
        }
        
        quickopen.showFiles(items, true);
        quickopen.openSessionFiles = items;                        
    },
    
    handleKeys: function(e) {
        if (this.window.isVisible) return true; // in the command line!

        if (e.keyCode == 'O'.charCodeAt() && e.altKey) { // send to command line
            bespin.get('quickopen').toggle();

            dojo.stopEvent(e);
            return true;
        }
    }
});
