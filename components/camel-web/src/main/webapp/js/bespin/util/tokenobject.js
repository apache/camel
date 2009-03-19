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

// = bespin.util.TokenObject =
//
// Given a string, make a token object that holds positions and has name access
//
// Examples,
//
// * args = new bespin.util.TokenObject(userString, { params: command.takes.order.join(' ') });
//
// * var test = new bespin.util.TokenObject(document.getElementById("input").value, { 
//	     splitBy: document.getElementById("regex").value,
//	     params: document.getElementById("params").value
// });
//
// * var test = new bespin.util.TokenObject("male 'Dion Almaer'", {
//    params: 'gender name'
// })

dojo.provide("bespin.util.tokenobject");

dojo.declare("bespin.util.TokenObject", null, { 
    constructor: function(input, options) {
        this._input = input;
        this._options = options;
        this._splitterRegex = new RegExp(this._options.splitBy || '\\s+');
        this._pieces = this.tokenize(input.split(this._splitterRegex));

        if (this._options.params) { // -- create a hash for name based access
            this._nametoindex = {};
            var namedparams = this._options.params.split(' ');
            for (var x = 0; x < namedparams.length; x++) {
                this._nametoindex[namedparams[x]] = x;

                if (!this._options['noshortcutvalues']) { // side step if you really don't want this
                    this[namedparams[x]] = this._pieces[x];
                }
            }

        }
    },
    
    // Split up the input taking into account ' and "
    tokenize: function(incoming) {
        var tokens = [];
        
        var nextToken;
        while (nextToken = incoming.shift()) {
            if (nextToken[0] == '"' || nextToken[0] == "'") { // it's quoting time
                var eaten = [ nextToken.substring(1, nextToken.length) ];
                var eataway;
                while (eataway = incoming.shift()) {
                    if (eataway[eataway.length - 1] == '"' || eataway[eataway.length - 1] == "'") { // end quoting time
                        eaten.push(eataway.substring(0, eataway.length - 1));
                        break;
                    } else {
                        eaten.push(eataway);
                    }
                }
                tokens.push(eaten.join(' '));
            } else {
                tokens.push(nextToken);
            }
        }
        
        return tokens;
    },
    
    param: function(index) {
        return (typeof index == "number") ? this._pieces[index] : this._pieces[this._nametoindex[index]];
    },

    length: function() {
        return this._pieces.length;
    }
});