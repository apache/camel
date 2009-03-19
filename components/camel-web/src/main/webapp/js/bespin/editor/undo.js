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

dojo.provide("bespin.editor.undo");

// = Undo Handling =
//
// Handle the undo/redo queues for the editor

// ** {{{ bespin.editor.UndoManager }}} **
//
// Run the undo/redo stack
dojo.declare("bespin.editor.UndoManager", null, {  
    constructor: function(editor) {
        this.editor = editor;
        this.undoStack = [];
        this.redoStack = [];
        this.syncHelper = undefined;
    },

    maxUndoLength: 100,

    canUndo: function() {
        return this.undoStack.length > 0;
    },

    undo: function() {
        if (this.undoStack.length == 0) return;
        var item = this.undoStack.pop();

        this.editor.cursorManager.moveCursor(item.undoOp.pos);
        item.undo();
        this.redoStack.push(item);

        if (this.syncHelper) this.syncHelper.undo();
    },

    redo: function() {
        if (this.redoStack.length == 0) return;
        var item = this.redoStack.pop();

        this.editor.cursorManager.moveCursor(item.redoOp.pos);
        item.redo();
        this.undoStack.push(item);

        if (this.syncHelper) this.syncHelper.redo();
    },

    addUndoOperation: function(item) {
        if (item.undoOp.queued) return;

        if (this.redoStack.length > 0) this.redoStack = [];

        while (this.undoStack.length + 1 > this.maxUndoLength) {
            this.undoStack.shift();
        }
        this.undoStack.push(item);
        item.editor = this.editor;

        // prevent undo operations from placing themselves back in the undo stack
        item.undoOp.queued = true;
        item.redoOp.queued = true;

        if (this.syncHelper) this.syncHelper.queueUndoOp(item);
    }
});

// ** {{{ bespin.editor.UndoManager }}} **
//
// The core operation contains two edit operations; one for undoing an operation, and the other for redoing it 
dojo.declare("bespin.editor.UndoItem", null, {
    constructor: function(undoOp, redoOp) {
        this.undoOp = undoOp;
        this.redoOp = redoOp;
    },

    undo: function() {
        this.editor.ui.actions[this.undoOp.action](this.undoOp);
    },

    redo: function() {
        this.editor.ui.actions[this.redoOp.action](this.redoOp);
    }
});