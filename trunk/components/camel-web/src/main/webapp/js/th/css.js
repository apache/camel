/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is jquery-css-parser.
 *
 * The Initial Developer of the Original Code is Daniel Wachsstock.
 * Portions created by the Initial Developer are Copyright (C) 2009
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *     Bespin Team (bespin@mozilla.com)
 *
 * ORIGINAL MIT-licensed CODE LICENSE HEADER FOLLOWS:

// jQuery based CSS parser
// documentation: http://youngisrael-stl.org/wordpress/2009/01/16/jquery-css-parser/
// Version: 1.0
// Copyright (c) 2009 Daniel Wachsstock
// MIT license:
// Permission is hereby granted, free of charge, to any person
// obtaining a copy of this software and associated documentation
// files (the "Software"), to deal in the Software without
// restriction, including without limitation the rights to use,
// copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following
// conditions:

// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.

// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
// OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
// HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.
 * ***** END LICENSE BLOCK ***** */


dojo.provide("th.css"); 

dojo.declare("th.css.CSSParser", null, {
    parse: function(str, ret) {
        // parses the passed stylesheet into an object with properties containing objects with the attribute names and values
        if (!ret) ret = {};          
        
        dojo.forEach(this.munge(str, false).split('`b%'), function(css){              
            css = css.split('%b`'); // css[0] is the selector; css[1] is the index in munged for the cssText  
            if (css.length < 2) return; // invalid css
            css[0] = this.restore(css[0]);
            var obj = ret[css[0]] || {};
            ret[css[0]] = dojo.mixin(obj, this.parsedeclarations(css[1]));
        }, this);  
         
        return ret;
    },

    // replace strings and brace-surrounded blocks with %s`number`s% and %b`number`b%. By successively taking out the innermost
    // blocks, we ensure that we're matching braces. No way to do this with just regular expressions. Obviously, this assumes no one
    // would use %s` in the real world.
    // Turns out this is similar to the method that Dean Edwards used for his CSS parser in IE7.js (http://code.google.com/p/ie7-js/)
    REbraces: /{[^{}]*}/,
    
    REfull: /\[[^\[\]]*\]|{[^{}]*}|\([^()]*\)|function(\s+\w+)?(\s*%b`\d+`b%){2}/, // match pairs of parentheses, brackets, and braces and function definitions.
    
    REatcomment: /\/\*@((?:[^\*]|\*[^\/])*)\*\//g, // comments of the form /*@ text */ have text parsed
    // we have to combine the comments and the strings because comments can contain string delimiters and strings can contain comment delimiters
    // var REcomment = /\/\*(?:[^\*]|\*[^\/])*\*\/|<!--|-->/g; // other comments are stripped. (this is a simplification of real SGML comments (see http://htmlhelp.com/reference/wilbur/misc/comment.html) , but it's what real browsers use)
    // var REstring = /\\.|"(?:[^\\\"]|\\.|\\\n)*"|'(?:[^\\\']|\\.|\\\n)*'/g; // match escaped characters and strings
    
    REcomment_string:
      /(?:\/\*(?:[^\*]|\*[^\/])*\*\/)|(\\.|"(?:[^\\\"]|\\.|\\\n)*"|'(?:[^\\\']|\\.|\\\n)*')/g,
    
    REmunged: /%\w`(\d+)`\w%/,
    
    uid: 0, // unique id number
    
    munged: {}, // strings that were removed by the parser so they don't mess up searching for specific characters

    munge: function(str, full) {
        str = str
            .replace(this.REatcomment, '$1') // strip /*@ comments but leave the text (to let invalid CSS through)
            .replace(this.REcomment_string, dojo.hitch(this, function (s, string) { // strip strings and escaped characters, leaving munged markers, and strip comments
                if (!string) return '';
                var replacement = '%s`'+(++this.uid)+'`s%';
                this.munged[this.uid] = string.replace(/^\\/, ''); // strip the backslash now
                return replacement;
            }));
       
        // need a loop here rather than .replace since we need to replace nested braces
        var RE = full ? this.REfull : this.REbraces;
        while (match = RE.exec(str)) {
            replacement = '%b`'+(++this.uid)+'`b%';
            this.munged[this.uid] = match[0];
            str = str.replace(RE, replacement);
        }           
        return str;
    },

    restore: function(str) {
        if (str === undefined) return str;
        while (match = this.REmunged.exec(str)) {
            str = str.replace(this.REmunged, this.munged[match[1]]);
        }
        return dojo.trim(str);
    },

    parsedeclarations: function(index){ // take a string from the munged array and parse it into an object of property: value pairs
        var str = this.munged[index].replace(/(?:^\s*[{'"]\s*)|(?:\s*([^\\])[}'"]\s*$)/g, '$1'); // find the string and remove the surrounding braces or quotes
        str = this.munge(str); // make sure any internal braces or strings are escaped
        var parsed = {};   
        dojo.forEach(str.split(';'), function (decl) {
            decl = decl.split(':');
            if (decl.length < 2) return;
            parsed[this.restore(decl[0])] = this.restore(decl[1]);
        }, this);
        return parsed;
    }
});