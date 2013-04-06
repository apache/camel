//  ***** BEGIN LICENSE BLOCK *****
// Version: MPL 1.1
// 
// The contents of this file are subject to the Mozilla Public License  
// Version
// 1.1 (the "License"); you may not use this file except in compliance  
// with
// the License. You may obtain a copy of the License at
// http://www.mozilla.org/MPL/
// 
// Software distributed under the License is distributed on an "AS IS"  
// basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied. See the  
// License
// for the specific language governing rights and limitations under the
// License.
// 
// The Original Code is Bespin.
// 
// The Initial Developer of the Original Code is Mozilla.
// Portions created by the Initial Developer are Copyright (C) 2009
// the Initial Developer. All Rights Reserved.
// 
// Contributor(s):
// 
// ***** END LICENSE BLOCK *****
// 

dojo.provide("th.borders");

dojo.declare("th.borders.SimpleBorder", th.Border, {
    getInsets: function() {
        return { left: 1, right: 1, top: 1, bottom: 1 };
    },

    paint: function(ctx) {
        var b = this.component.bounds;
        ctx.strokeStyle = this.style.color;
        ctx.strokeRect(0, 0, b.width, b.height);
    }
}); 

dojo.declare("th.borders.EmptyBorder", th.Border, {
    constructor: function(parms) {
        if (!parms) parms = {};
        
        if (parms.size) {
            this.insets = { left: parms.size, right: parms.size, top: parms.size, bottom: parms.size };
        } else {
            this.insets = parms.insets;
        }
    },

    getInsets: function() {
        return this.insets;
    }
});