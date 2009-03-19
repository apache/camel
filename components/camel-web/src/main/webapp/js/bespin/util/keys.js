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

dojo.provide("bespin.util.keys");

// = Key Helpers =
//
// Helpful code to deal with key handling and processing
// Consists of two core pieces:
//
// * {{{bespin.util.keys.Key}}} is a map of keys to key codes
// * {{{bespin.util.keys.fillArguments}}} converts a string "CTRL A" to its key and modifier
//
// TODO: Having the keys in the same scope as the method is really bad :)

// ** {{{ bespin.util.keys.Key }}} **
//
// Alpha keys, and special keys (ENTER, BACKSPACE) have key codes that our code needs to check.
// This gives you a way to say Key.ENTER when matching a key code instead of "13"

bespin.util.keys.Key = {

// -- Numbers
  ZERO: 48,
  ONE: 49,
  TWO: 50,
  THREE: 51,
  FOUR: 52,
  FIVE: 53,
  SIX: 54,
  SEVEN: 55,
  EIGHT: 56,
  NINE: 57,
  
// -- Alphabet
  A: 65,
  B: 66,
  C: 67,
  D: 68,
  E: 69,
  F: 70,
  G: 71,
  H: 72,
  I: 73,
  J: 74,
  K: 75,
  L: 76,
  M: 77,
  N: 78,
  O: 79,
  P: 80,
  Q: 81,
  R: 82,
  S: 83,
  T: 84,
  U: 85,
  V: 86,
  W: 87,
  X: 88,
  Y: 89,
  Z: 90,

// -- Special Keys
  BACKSPACE: 8,
  TAB: 9,
  ENTER: 13,
  ESCAPE: 27,
  END: 35,
  HOME: 36,
  ARROW_LEFT: 37,
  ARROW_UP: 38,
  ARROW_RIGHT: 39,
  ARROW_DOWN: 40,
  DELETE: 46,
  PAGE_UP: 33,
  PAGE_DOWN: 34,
  PLUS: 61,
  MINUS: 109,
  TILDE: 192
};

// ** {{{ bespin.util.keys.fillArguments }}} **
//
// Fill out the arguments for action, key, modifiers
//
// {{{string}}} can be something like "CTRL S"
// {{{args}}} is the args that you want to modify. This is common as you may already have args.action.

bespin.util.keys.fillArguments = function(string, args) {
    var keys = string.split(' ');
    args = args || {};
    
    var modifiers = [];   
    dojo.forEach(keys, function(key) {
       if (key.length > 1) { // more than just an alpha/numeric
           modifiers.push(key);
       } else {
           args.key = key;
       }
    });

    if (modifiers.length == 0) { // none if that is true
        args.modifiers = "none";
    } else {
        args.modifiers = modifiers.join(',');
    }
    
    return args;
};

// ** {{{ bespin.util.keys.PassThroughCharCodes }}} **
//
// Cache the character codes that we want to pass through to the browser
// Should map to list below
bespin.util.keys.PassThroughCharCodes = dojo.map(["k", "l", "n", "o", "t", "w", "+", "-", "~", 
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"], function(item){ return item.charCodeAt(0); });

// ** {{{ bespin.util.keys.PassThroughKeyCodes }}} **
//
// Cache the key codes that we want to pass through to the browser
// Should map to list above 
bespin.util.keys.PassThroughKeyCodes = (function() {
    var Key = bespin.util.keys.Key;
    return [Key.C, Key.X, Key.V, Key.K, Key.L, Key.N, Key.O, Key.T, Key.W, Key.PLUS, Key.MINUS, Key.TILDE,
            Key.ZERO, Key.ONE, Key.TWO, Key.THREE, Key.FOUR, Key.FIVE, Key.SIX, Key.SEVEN, Key.EIGHT, Key.NINE];   
})();

// ** {{{ bespin.util.keys.passThroughToBrowser }}} **
//
// Given the event, return true if you want to allow the event to pass through to the browser.
// For example, allow Apple-L to go to location, Apple-K for search. Apple-# for a tab.
//
// {{{e}}} Event that came into an {{{onkeydown}}} handler

bespin.util.keys.passThroughToBrowser = function(e) {
    var Key = bespin.util.keys.Key;
    
    if (!e.ctrlKey) { // let normal characters through
        return true;
    } else if (e.metaKey || e.altKey) { // Apple or Alt key
        if (e.type == "keypress") {   
            if (dojo.some(bespin.util.keys.PassThroughCharCodes, function(item) { return (item == e.charCode); })) return true;  
        } else {
            if (dojo.some(bespin.util.keys.PassThroughKeyCodes, function(item) { return (item == e.keyCode); })) return true; 
        }
    }
                                                 
    return false;
};
