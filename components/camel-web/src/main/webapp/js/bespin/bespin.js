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

// = Bespin =
//
// This is the root of it all. The {{{ bespin }}} namespace.
// All of the JavaScript for Bespin will be placed in this namespace later.
//
// {{{ bespin.versionNumber }}} is the core version of the Bespin system
// {{{ bespin.apiVersion }}} is the version number of the API (to ensure that the
//                          client and server are talking the same language)
// {{{ bespin.displayVersion }}} is a function that sets innerHTML on the element given, with the Bespin version info
//
// {{{ bespin.publish }}} maps onto dojo.publish but lets us abstract away for the future
// {{{ bespin.subscribe }}} maps onto dojo.subscribe but lets us abstract away for the future
// {{{ bespin.unsubscribe }}} maps onto dojo.unsubscribe but lets us abstract away for the future
//
// {{{ bespin.register }}} is the way to attach components into the bespin system for others to get out
// {{{ bespin.get }}} allows you to get registered components out
// {{{ bespin.withComponent }}} maps onto dojo.subscribe but lets us abstract away for the future

dojo.provide("bespin.bespin");
    
dojo.mixin(bespin, {
    // BEGIN VERSION BLOCK
    versionNumber: 'tip',
    versionCodename: 'DEVELOPMENT MODE',
    apiVersion: 'dev',
    // END VERSION BLOCK
    
    defaultTabSize: 4,
    userSettingsProject: "BespinSettings",

    // == Methods for tying to the event bus

    // ** {{{ publish }}} **
    //
    // Given a topic and a set of parameters, publish onto the bus
    publish: function(topic, args) {
        dojo.publish(topic, dojo.isArray(args) ? args : [ args || {} ]);
    },

    // ** {{{ subscribe }}} **
    //
    // Given a topic and a function, subscribe to the event
    subscribe: dojo.subscribe,

    // ** {{{ unsubscribe }}} **
    //
    // Unsubscribe the functions from the topic
    unsubscribe: dojo.unsubscribe,

    // == Methods for registering components with the main system
    registeredComponents: {},

    // ** {{{ register }}} **
    //
    // Given an id and an object, register it inside of Bespin
    register: function(id, object) {
        bespin.publish("bespin:component:register", { id: id, object: object });

        this.registeredComponents[id] = object;
        
        return object;
    },

    // ** {{{ get }}} **
    //
    // Given an id return the component
    get: function(id) {
        return this.registeredComponents[id];
    },

    // ** {{{ withComponent }}} **
    //
    // Given an id, and function to run, execute it if the component is available
    withComponent: function(id, func) {
        var component = this.get(id);
        if (component) {
            return func(component);
        }
    },

    // ** {{{ displayVersion }}} **
    //
    // Given an HTML element
    displayVersion: function(el) {
        var el = dojo.byId(el) || dojo.byId("version");
        if (!el) return;
        el.innerHTML = '<a href="https://wiki.mozilla.org/Labs/Bespin/ReleaseNotes" title="Read the release notes">Version <span class="versionnumber">' + this.versionNumber + '</span> "' + this.versionCodename + '"</a>';
    }
});
