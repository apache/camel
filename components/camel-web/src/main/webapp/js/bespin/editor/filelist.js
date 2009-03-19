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

dojo.provide("bespin.editor.filelist");

dojo.declare("bespin.editor.filelist.FilePanel", th.components.Panel, {
    constructor: function(parms) {
        if (!parms) parms = {};

        this.fileLabel = new th.components.Label({ text: "Open Sessions", style: { color: "white", font: "8pt Tahoma" } });
        this.fileLabel.oldPaint = this.fileLabel.paint;
        this.fileLabel.paint = function(ctx) {
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

        this.list = new th.components.List({ allowDeselection: false, style: { backgroundColor: "rgb(61, 59, 52)", color: "white", font: "8pt Tahoma" } });

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
        var list = this.list;
        list.renderer = renderer;
        list.oldGetRenderer = list.getRenderer;
        list.getRenderer = function(rctx) {
            var label = list.oldGetRenderer(rctx);
            label.attributes.text = rctx.item.name;
            return label;
        }
        
        var splitter = new th.components.Splitter({ attributes: { orientation: th.HORIZONTAL }, scrollbar: new th.components.Scrollbar() });
        splitter.scrollbar.scrollable = list;
        splitter.scrollbar.opaque = false;
        this.splitter = splitter;

        this.add([ this.fileLabel, this.list, this.splitter]);

        this.bus.bind("dragstart", this.splitter, this.ondragstart, this);
        this.bus.bind("drag", this.splitter, this.ondrag, this);
        this.bus.bind("dragstop", this.splitter, this.ondragstop, this);

        // this is a closed container
        delete this.add;
        delete this.remove;
    },

    ondragstart: function(e) {
        this.startWidth = this.prefWidth;
    },

    ondrag: function(e) {
        var delta = e.currentPos.x - e.startPos.x;
        this.prefWidth = this.startWidth + delta;
        bespin.bootstrap.doResize();
    },

    ondragstop: function(e) {
        delete this.startWidth;
        bespin.get('settings').set('ui:filelist:width', this.prefWidth);
    },

    getPreferredWidth: function(height) {
        return this.prefWidth || 150;
    },

    layout: function() {
        var d = this.d();

        var y = d.i.t;
        var lh = this.fileLabel.getPreferredHeight(d.b.w);
        this.fileLabel.bounds = { y: y, x: d.i.l, height: lh, width: d.b.w };
        y += lh;

        var sw = this.splitter.getPreferredWidth()
        this.splitter.bounds = { x: d.b.w - d.i.r - sw, height: d.b.h - d.i.b - y, y: y, width: sw };

        var innerWidth = d.b.w - d.i.w - sw;
 
        this.list.bounds = { x: d.i.l, y: y, width: innerWidth, height: this.splitter.bounds.height };
    }
});

dojo.declare("bespin.editor.filelist.UI", null, {
    constructor: function(container) {
        this.container = dojo.byId(container);

        dojo.byId(container).innerHTML = "<canvas id='canvas_filelist' moz-opaque='true' tabindex='-1'></canvas>"+dojo.byId(container).innerHTML;        
        this.canvas = dojo.byId(container).firstChild;
        while (this.canvas && this.canvas.nodeType != 1) this.canvas = this.canvas.nextSibling;
        
        var scene = new th.Scene(dojo.byId("canvas_filelist"));  
        this.filePanel = new bespin.editor.filelist.FilePanel();
        scene.root.add(this.filePanel);        
        this.scene = scene;
        
        var _this = this;
        
        scene.bus.bind("dblclick", this.filePanel.list, function(e) {
            var item = _this.filePanel.list.selected;
            
            if (!item)  return;
            
            bespin.publish("bespin:editor:savefile", {});
            bespin.publish("bespin:editor:openfile", { filename: item.filename });
        });
        
        this.filePanel.prefWidth = 165;
                        
        bespin.subscribe('bespin:settings:loaded', dojo.hitch(this, function() {            
            bespin.get('server').listOpen(this.displaySessions);
        }));
        
/*      bespin.subscribe('bespin:settings:loaded', dojo.hitch(this, function() {
            var width = bespin.get('settings').get('ui:filelist:width') || 200;
            this.filePanel.prefWidth = width;  
        }));*/
    },
    
    displaySessions: function(sessions) {        
        var currentProject = bespin.get('editSession').project;
        var currentFile = bespin.get('editSession').path;
        var items = new Array();
        
        for (var project in sessions) {
            if (project != currentProject) continue;
                        
            for (var file in sessions[project]) {                
                var lastSlash = file.lastIndexOf("/");
                var path = (lastSlash == -1) ? "" : file.substring(0, lastSlash);
                var name = (lastSlash == -1) ? file : file.substring(lastSlash + 1);

                if (currentFile == file) {
                    currentFile = false;
                }

                items.push({name: name, filename: file, project: project });                
            }
            
            if (currentFile) {
                var lastSlash = currentFile.lastIndexOf("/");
                var path = (lastSlash == -1) ? "" : currentFile.substring(0, lastSlash);
                var name = (lastSlash == -1) ? currentFile : currentFile.substring(lastSlash + 1);
                items.push({name: name, filename: file, project: project });                
            }
            
            items.sort(function(a, b) {
                var x = a.name.toLowerCase();
                var y = b.name.toLowerCase();
                return ((x < y) ? -1 : ((x > y) ? 1 : 0));
            });
            
            bespin.get('filelist').filePanel.list.items = items;
            
            break;
        }
    },
    
    fitAndRepaint: function() {
        dojo.attr(this.canvas, {width: this.filePanel.prefWidth, height: window.innerHeight - 95});
        this.scene.layout();
        this.scene.render();
    },
    
    getWidth: function() {
        return this.filePanel.prefWidth;
    }
});
