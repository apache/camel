dojo.provide("bespin.editor.cursor");

// ** {{{ bespin.editor.CursorManager }}} **
//
// Handles the position of the cursor, hiding the complexity of translating between screen and model positions and so forth
dojo.declare("bespin.editor.CursorManager", null, {
    constructor: function(editor) {
        this.editor = editor;
        this.position = { col: 0, row: 0 };
    },

    getScreenPosition: function() {
        return this.position;
    },

    getModelPosition: function() {
        var pos = this.position;

        var line = this.editor.model.getRowArray(pos.row);
        var tabspaces = 0;
        var curCol = 0;
        for (var i = 0; i < line.length; i++) {
            if (line[i].charCodeAt(0) == 9) {
                var toInsert = this.editor.tabstop - (curCol % this.editor.tabstop);
                curCol += toInsert - 1;
                tabspaces += toInsert - 1;
            }
            curCol++;
            if (curCol >= pos.col) break;
        }
        if (tabspaces > 0) {
            return { col: pos.col = pos.col - tabspaces, row: pos.row };
        } else {
            return bespin.editor.utils.copyPos(pos);
        }
    },

    moveToLineStart: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        var line = this.editor.ui.getRowString(this.editor.cursorManager.getScreenPosition().row);
        var match = /^(\s+).*/.exec(line);
        var leadingWhitespaceLength = 0;

        // Check to see if there is leading white space and move to the first text if that is the case
        if (match && match.length == 2) {
            leadingWhitespaceLength = match[1].length;
        }

        if (this.position.col == 0) {
            this.moveCursor({ col:  leadingWhitespaceLength });
        } else if (this.position.col == leadingWhitespaceLength) {
            this.moveCursor({ col: 0 });
        } else {
            this.moveCursor({ col: leadingWhitespaceLength });
        }

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) };
    },

    moveToLineEnd: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        this.moveCursor({ col: this.editor.ui.getRowScreenLength(oldPos.row) });

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) };
    },

    moveToTop: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        this.editor.cursorManager.moveCursor({ col: 0, row: 0 });

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) };
    },

    moveToBottom: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        var row = this.editor.model.getRowCount() - 1;
        this.editor.cursorManager.moveCursor({ row: row, col: this.editor.ui.getRowScreenLength(row)});

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) };
    },

    moveUp: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        this.moveCursor({ col: oldPos.col, row: oldPos.row - 1 });

        if (bespin.get("settings").isOn(bespin.get("settings").get('strictlines')) && this.position.col > this.editor.ui.getRowScreenLength(this.position.row)) {
            this.moveToLineEnd();
        }

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) };
    },

    moveDown: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        this.moveCursor({ row: Math.max(0, oldPos.row + 1) });

        if (bespin.get("settings").isOn(bespin.get("settings").get('strictlines')) && this.position.col > this.editor.ui.getRowScreenLength(this.position.row)) {
            this.moveToLineEnd();
        }

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) }
    },

    moveLeft: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        // start of the line so move up
        if (bespin.get("settings").isOn(bespin.get("settings").get('strictlines')) && (this.position.col == 0)) {
            this.moveUp();
            if (oldPos.row > 0) this.moveToLineEnd();
        } else {
            this.moveCursor({ col: Math.max(0, oldPos.col - 1), row: oldPos.row });
        }

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) }
    },

    moveRight: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        // end of the line, so go to the start of the next line
        if (bespin.get("settings").isOn(bespin.get("settings").get('strictlines')) && (this.position.col >= this.editor.ui.getRowScreenLength(this.position.row))) {
            this.moveDown();
            if (oldPos.row < this.editor.model.getRowCount() - 1) this.moveToLineStart();
        } else {
            this.moveCursor({ col: this.position.col + 1 });
        }

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) }
    },

    movePageUp: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        this.moveCursor({ row: Math.max(this.editor.ui.firstVisibleRow - this.editor.ui.visibleRows, 0)});

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) }
    },

    movePageDown: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        this.moveCursor({ row: Math.min(this.position.row + this.editor.ui.visibleRows, this.editor.model.getRowCount() - 1)});

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) }
    },

    smartMoveLeft: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        var row = this.editor.ui.getRowString(oldPos.row);

        var c, charCode;

        if (this.position.col == 0) { // -- at the start to move up and to the end
            this.moveUp();
            this.moveToLineEnd();
        } else {
            // Short circuit if cursor is ahead of actual spaces in model
            if (row.length < this.position.col) this.moveToLineEnd();

            var newcol = this.position.col;

            // This slurps up trailing spaces
            var wasSpaces = false;
            while (newcol > 0) {
                newcol--;

                c = row.charAt(newcol);
                charCode = c.charCodeAt(0);
                if (charCode == 32 /*space*/) {
                    wasSpaces = true;
                } else {
                    newcol++;
                    break;
                }
            }

            // This jumps to stop words
            if (!wasSpaces) {
                while (newcol > 0) {
                    newcol--;
                    c = row.charAt(newcol);
                    charCode = c.charCodeAt(0);
                    if ( (charCode < 65) || (charCode > 122) ) { // if you get to an alpha you are done
                        if (newcol != this.position.col - 1) newcol++; // right next to a stop char, move back one
                        break;
                    }
                }
            }

            this.moveCursor({ col: newcol });
        }

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) }
    },

    smartMoveRight: function() {
        var oldPos = bespin.editor.utils.copyPos(this.position);

        var row = this.editor.ui.getRowString(oldPos.row);

        if (row.length <= this.position.col) { // -- at the edge so go to the next line
            this.moveDown();
            this.moveToLineStart();
        } else {
            var c, charCode;

            var newcol = this.position.col;

            // This slurps up leading spaces
            var wasSpaces = false;
            while (newcol < row.length) {
                c = row[newcol];
                charCode = c.charCodeAt(0);
                if (charCode == 32 /*space*/) {
                    wasSpaces = true;
                    newcol++;
                } else {
                    break;
                }
            }

            // This jumps to stop words
            if (!wasSpaces) {
                while (newcol < row.length) {
                    newcol++;

                    if (row.length == newcol) { // one more to go
                        this.moveToLineEnd();
                        newcol = -1;
                        break;
                    }

                    c = row[newcol];
                    charCode = c.charCodeAt(0);

                    if ( (charCode < 65) || (charCode > 122) ) {
                        break;
                    }
                }
            }

            if (newcol != -1) this.moveCursor({ col: newcol });
        }

        return { oldPos: oldPos, newPos: bespin.editor.utils.copyPos(this.position) }
    },

    moveCursor: function(newpos) {
        if (!newpos) return; // guard against a bad position (certain redo did this)
        if (newpos.col === undefined) newpos.col = this.position.col;
        if (newpos.row === undefined) newpos.row = this.position.row;

        var oldpos = this.position;

        var row = Math.min(newpos.row, this.editor.model.getRowCount() - 1); // last row if you go over
        if (row < 0) row = 0; // can't move negative off screen

        var invalid = this.isInvalidCursorPosition(row, newpos.col);
        if (invalid) {
            console.log("invalid position: " + invalid.left + ", " + invalid.right);
            if (oldpos.col < newpos.col) {
                newpos.col = invalid.right;
            } else if (oldpos.col > newpos.col) {
                newpos.col = invalid.left;
            } else {
                // default
                newpos.col = invalid.left;
            }
        }

        this.position = { row: row, col: newpos.col };
    },

    // Pass in a screen position; returns undefined if the postion is valid, otherwise returns closest left and right valid positions
    isInvalidCursorPosition: function(row, col) {
        var rowArray = this.editor.model.getRowArray(row);

        // we need to track the cursor position separately because we're stepping through the array, not the row string
        var curCol = 0;
        for (var i = 0; i < rowArray.length; i++) {
            if (rowArray[i].charCodeAt(0) == 9) {
                // if current character in the array is a tab, work out the white space between here and the tab stop
                var toInsert = this.editor.tabstop - (curCol % this.editor.tabstop);

                // if the passed column is in the whitespace between the tab and the tab stop, it's an invalid position
                if (col > curCol && col < (curCol + toInsert)) {
                    return { left: curCol, right: curCol + toInsert };
                }

                curCol += toInsert - 1;
            }
            curCol++;
        }

        return undefined;
    }
});
