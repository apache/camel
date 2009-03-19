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

dojo.provide("bespin.util.path");

// = Path =
//
// Deal with paths that are sent into Bespin

dojo.mixin(bespin.util.path, {
    // ** {{{ bespin.util.path.combine }}} **
    //
    // Take the given arguments and combine them with one path seperator
    //
    // * combine("foo", "bar") -> foo/bar
    // * combine(" foo/", "/bar  ") -> foo/bar
    combine: function() {
        var args = Array.prototype.slice.call(arguments); // clone to a true array

        var path = args.join('/');
        path = path.replace(/\/\/+/g, '/');
        path = path.replace(/^\s+|\s+$/g, '');
        return path;
    },

    // ** {{{ bespin.util.path.directory }}} **
    //
    // Given a {{{path}}} return the directory
    //
    // * directory("/path/to/directory/file.txt") -> /path/to/directory/
    // * directory("/path/to/directory/") -> /path/to/directory/
    // * directory("foo.txt") -> ""
    directory: function(path) {
        var dirs = path.split('/');
        if (dirs.length == 1) { // no directory so return blank
            return "";
        } else if ((dirs.length == 2) && dirs[dirs.length -1] == "") { // a complete directory so return it
            return path;
        } else {
            return dirs.slice(0, dirs.length - 1).join('/');
        }
    },

    // ** {{{ bespin.util.path.makeDirectory }}} **
    //
    // Given a {{{path}}} make sure that it returns as a directory 
    // (As in, ends with a '/')
    //
    // * makeDirectory("/path/to/directory") -> /path/to/directory/
    // * makeDirectory("/path/to/directory/") -> /path/to/directory/
    makeDirectory: function(path) {
        if (!/\/$/.test(path)) path += '/';
        return path;
    },

    // ** {{{ bespin.util.path.combineAsDirectory }}} **
    //
    // Take the given arguments and combine them with one path seperator and
    // then make sure that you end up with a directory
    //
    // * combine("foo", "bar") -> foo/bar/
    // * combine(" foo/", "/bar  ") -> foo/bar/
    combineAsDirectory: function() {
        return this.makeDirectory(this.combine.apply(this, arguments));
    },

    // ** {{{ bespin.util.path.escape }}} **
    //
    // This function doubles down and calls {{{combine}}} and then escapes the output
    escape: function() {
        return escape(this.combine.apply(this, arguments));
    }
});