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

// = CSS Syntax Highlighting Implementation =
//
// You can guess what this does. ;-)

dojo.provide("bespin.syntax.css");


// ** {{{ bespin.syntax.CSSSyntaxEngine }}} **
//
// Tracks syntax highlighting data on a per-line basis. This is a quick-and-dirty implementation that
// does the right thing for key/value pairs, #000000, and the like.
// Doesn't actually grok the zones of "propertykey: propertyvalue" as it should.

bespin.syntax.Constants = {
    C_STYLE_COMMENT: "c-comment",
    STRING: "string",
    KEYWORD: "keyword",
    PUNCTUATION: "punctuation",
    OTHER: "plain",
    NAME: "attribute-name",
    VALUE: "attribute-value",
    IMPORTANT: "important",
    COLOR: "color",
    SIZES: "sizes",
    ID: "cssid",
    COLOR_OR_ID: "color_or_id"
};

dojo.declare("bespin.syntax.CSSSyntaxEngine", null, {
    keywords: ['ascent', 'azimuth', 'background-attachment', 'background-color', 'background-image', 'background-position',
        'background-repeat', 'background', 'baseline', 'bbox', 'border-collapse', 'border-color', 'border-spacing', 'border-style',
        'border-top', 'border-right', 'border-bottom', 'border-left', 'border-top-color', 'border-right-color', 'border-bottom-color',
        'border-left-color', 'border-top-style', 'border-right-style', 'border-bottom-style', 'border-left-style', 'border-top-width',
        'border-right-width', 'border-bottom-width', 'border-left-width', 'border-width', 'border', 'cap-height', 'caption-side', 'centerline',
        'clear', 'clip', 'color', 'content', 'counter-increment', 'counter-reset', 'cue-after', 'cue-before', 'cue', 'cursor', 'definition-src',
        'descent', 'direction', 'display', 'elevation', 'empty-cells', 'float', 'font-size-adjust', 'font-family', 'font-size', 'font-stretch',
        'font-style', 'font-variant', 'font-weight', 'font', 'height', 'letter-spacing', 'line-height', 'list-style-image', 'list-style-position',
        'list-style-type', 'list-style', 'margin-top', 'margin-right', 'margin-bottom', 'margin-left', 'margin', 'marker-offset', 'marks', 'mathline',
        'max-height','max-width', 'min-height', 'min-width', 'orphans', 'outline-color', 'outline-style', 'outline-width', 'outline', 'overflow',
        'padding-top', 'padding-right', 'padding-bottom', 'padding-left', 'padding', 'page', 'page-break-after', 'page-break-before',
        'page-break-inside', 'pause', 'pause-after', 'pause-before', 'pitch', 'pitch-range', 'play-during', 'position',
        'quotes', 'richness', 'size', 'slope', 'src', 'speak-header', 'speak-numeral', 'speak-punctuation', 'speak', 'speech-rate', 'stemh', 'stemv',
        'stress', 'table-layout', 'text-align', 'text-decoration', 'text-indent', 'text-shadow', 'text-transform', 'unicode-bidi', 'unicode-range',
        'units-per-em', 'vertical-align', 'visibility', 'voice-family', 'volume', 'white-space', 'widows', 'width', 'widths', 'word-spacing',
        'x-height', 'z-index'],

    values: ['above', 'absolute', 'all', 'always', 'aqua', 'armenian', 'attr', 'aural', 'auto', 'avoid', 'baseline', 'behind', 'below',
        'bidi-override', 'black', 'blink', 'block', 'blue', 'bold', 'bolder', 'both', 'bottom', 'braille', 'capitalize', 'caption',
        'center', 'center-left', 'center-right', 'circle', 'close-quote', 'code', 'collapse', 'compact', 'condensed',
        'continuous', 'counter', 'counters', 'crop', 'cross', 'crosshair', 'cursive', 'dashed', 'decimal', 'decimal-leading-zero', 'default',
        'digits', 'disc', 'dotted', 'double', 'embed', 'embossed', 'e-resize', 'expanded', 'extra-condensed', 'extra-expanded', 'fantasy',
        'far-left', 'far-right', 'fast', 'faster', 'fixed', 'format', 'fuchsia', 'gray', 'green', 'groove', 'handheld', 'hebrew', 'help',
        'hidden', 'hide', 'high', 'higher', 'icon', 'inline-table', 'inline', 'inset', 'inside', 'invert',
        'italic', 'justify', 'landscape', 'large', 'larger', 'left-side', 'left', 'leftwards', 'level', 'lighter', 'lime', 'line-through',
        'list-item', 'local', 'loud', 'lower-alpha', 'lowercase', 'lower-greek', 'lower-latin', 'lower-roman', 'lower', 'low', 'ltr', 'marker',
        'maroon', 'medium', 'message-box', 'middle', 'mix', 'move', 'narrower', 'navy', 'ne-resize', 'no-close-quote', 'none', 'no-open-quote',
        'no-repeat', 'normal', 'nowrap', 'n-resize', 'nw-resize', 'oblique', 'olive', 'once', 'open-quote', 'outset', 'outside', 'overline',
        'pointer', 'portrait', 'pre', 'print', 'projection', 'purple', 'red', 'relative', 'repeat', 'repeat-x', 'repeat-y', 'rgb', 'ridge',
        'right', 'right-side', 'rightwards', 'rtl', 'run-in', 'screen', 'scroll', 'semi-condensed', 'semi-expanded', 'separate', 'se-resize',
        'show', 'silent', 'silver', 'slower', 'slow', 'small', 'small-caps', 'small-caption', 'smaller', 'soft', 'solid', 'speech', 'spell-out',
        'square', 's-resize', 'static', 'status-bar', 'sub', 'super', 'sw-resize', 'table-caption', 'table-cell', 'table-column',
        'table-column-group', 'table-footer-group', 'table-header-group', 'table-row', 'table-row-group', 'teal', 'text-bottom', 'text-top',
        'thick', 'thin', 'top', 'transparent', 'tty', 'tv', 'ultra-condensed', 'ultra-expanded', 'underline', 'upper-alpha', 'uppercase',
        'upper-latin', 'upper-roman', 'url', 'visible', 'wait', 'white', 'wider', 'w-resize', 'x-fast', 'x-high', 'x-large', 'x-loud', 'x-low',
        'x-slow', 'x-small', 'x-soft', 'xx-large', 'xx-small', 'yellow',
        'monospace', 'tahoma', 'verdana', 'arial', 'helvetica', 'sans-serif', 'serif'],

    sizeRegex: "(?:em|pt|px|%)",

    important: '!important',

    punctuation: '{ } / + * . , ; ( ) ? : = " \''.split(" "),

    highlight: function(line, meta) {
        var K = bespin.syntax.Constants;    // aliasing the constants for shorter reference ;-)

        var regions = {};  // contains the individual style types as keys, with array of start/stop positions as value

        if (!meta) meta = {}; // may not get it first time

        // current state, maintained as we parse through each character in the line; values at any time should be consistent
        var currentStyle = (meta.inMultilineComment) ? K.C_STYLE_COMMENT : undefined;
        var currentRegion = {}; // should always have a start property for a non-blank buffer
        var buffer = "";

        // these properties are related to the parser state above but are special cases
        var stringChar = "";    // the character used to start the current string
        var multiline = meta.inMultilineComment;  // this line contains an unterminated multi-line comment

        for (var i = 0; i < line.length; i++) {
            var c = line.charAt(i);

            // check if we're in a comment and whether this character ends the comment
            if (currentStyle == K.C_STYLE_COMMENT) {
                if (c == "/" && /\*$/.test(buffer)) { // has the c-style comment just ended?
                    currentRegion.stop = i + 1; // get the final / too
                    this.addRegion(regions, currentStyle, currentRegion);
                    currentRegion = {};
                    currentStyle = undefined;
                    multiline = false;
                    buffer = "";
                } else {
                    if (buffer == "") currentRegion = { start: i };
                    buffer += c;
                }

                continue;
            }

            // check if we have a hash character
            if (this.isHashChar(c)) {
                 currentStyle = K.COLOR_OR_ID;

                 if (buffer == "") currentRegion = { start: i };
                 buffer += c;

                 continue;
            }


            if (this.isWhiteSpaceOrPunctuation(c)) {
                // check if we're in a string
                if (currentStyle == K.STRING) {
                    // if this is not an unescaped end quote (either a single quote or double quote to match how the string started) then keep going
                    if ( ! (c == stringChar && !/\\$/.test(buffer))) {
                        if (buffer == "") currentRegion = { start: i };
                        buffer += c;
                        continue;
                    }
                }

                if (currentStyle == K.COLOR_OR_ID) {
                    // if this is not an unescaped end quote (either a single quote or double quote to match how the string started) then keep going
                    if (buffer.match(/#[0-9AaBbCcDdEeFf]{3,6}/)) {
                        currentStyle = K.COLOR;
                    } else {
                        currentStyle = K.OTHER;
                    }
                    currentRegion.stop = i;

                    this.addRegion(regions, currentStyle, currentRegion);

                    currentRegion = { start: i }; // clear
                    stringChar = "";
                    buffer = c;

                    currentStyle = undefined;

                    continue;
                }

                // if the buffer is full, add it to the regions
                if (buffer != "") {
                    currentRegion.stop = i;

                    // if this is a string, we're all set to add it; if not, figure out if its a keyword, value, or important
                    if (currentStyle != K.STRING) {
                        if (this.keywords.indexOf(buffer) != -1) {
                            // the buffer contains a keyword
                            currentStyle = K.KEYWORD;
                        } else if (this.values.indexOf(buffer.toLowerCase()) != -1) {
                            currentStyle = K.VALUE;
                        } else if (buffer.match(this.sizeRegex)) {
                            currentStyle = K.SIZE;
                        } else if (this.important.indexOf(buffer) != -1) {
                            currentStyle = K.IMPORTANT;
                        } else {
                            currentStyle = K.OTHER;
                        }
                    }
                    this.addRegion(regions, currentStyle, currentRegion);
                    currentRegion = {};
                    stringChar = "";
                    buffer = "";
                    // i don't clear the current style here so I can check if it was a string below
                }

                if (this.isPunctuation(c)) {
                    if (c == "*" && i > 0 && (line.charAt(i - 1) == "/")) {
                        // remove the previous region in the punctuation bucket, which is a forward slash
                        regions[K.PUNCTUATION].pop();

                        // we are in a c-style comment
                        multiline = true;
                        currentStyle = K.C_STYLE_COMMENT;
                        currentRegion = { start: i - 1 };
                        buffer = "/*";
                        continue;
                    }

                    // add an ad-hoc region for just this one punctuation character
                    this.addRegion(regions, K.PUNCTUATION, { start: i, stop: i + 1 });
                }

                // find out if the current quote is the end or the beginning of the string
                if ((c == "'" || c == '"') && (currentStyle != K.STRING)) {
                    currentStyle = K.STRING;
                    stringChar = c;
                } else {
                    currentStyle = undefined;
                }

                continue;
            }

            if (buffer == "") currentRegion = { start: i };
            buffer += c;
        }

        // check for a trailing character inside of a string or a comment
        if (buffer != "") {
            if (!currentStyle) currentStyle = K.OTHER;
            currentRegion.stop = line.length;
            this.addRegion(regions, currentStyle, currentRegion);
        }

        return { regions: regions, meta: { inMultilineComment: multiline } };
    },

    addRegion: function(regions, type, data) {
        if (!regions[type]) regions[type] = [];
        regions[type].push(data);
    },

    isHashChar: function(ch) {
        return ch == "#";
    },

    isWhiteSpaceOrPunctuation: function(ch) {
        return this.isPunctuation(ch) || this.isWhiteSpace(ch);
    },

    isPunctuation: function(ch) {
        return this.punctuation.indexOf(ch) != -1;
    },

    isWhiteSpace: function(ch) {
        return ch == " ";
    }
});

// Register this puppy
bespin.syntax.EngineResolver.register(new bespin.syntax.CSSSyntaxEngine(), ['css']);
