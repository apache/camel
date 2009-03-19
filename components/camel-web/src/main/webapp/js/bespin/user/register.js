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
 
dojo.provide("bespin.user.register"); 

// Login, logout and registration functions for the Bespin front page.

(function() {
    var server = bespin.get('server') || bespin.register('server', new bespin.client.Server());
    var utils = bespin.user.utils;
    var webpieces = bespin.util.webpieces;
    
    dojo.mixin(bespin.user, {
        login: function() {
            if (utils.showingBrowserCompatScreen()) return;

            if (dojo.byId("username").value && dojo.byId("password").value) {
                // try to find the httpSessionId
                var cookies = document.cookie.split(';');
                var foundValue = "";
                for (var i = 0; i < cookies.length; i++) {
                    var cookie = cookies[i];
                    while (cookie.charAt(0) == ' ') cookie = cookie.substring(1, cookie.length);
                    if (cookie.indexOf("anticsrf=") == 0) {
                        foundValue = cookie.substring(dwr.engine._sessionCookieName.length + 1, cookie.length);
                        break;
                    }
                }

                server.login(dojo.byId("username").value, dojo.byId("password").value, 
                    foundValue, 
                    utils.whenLoginSucceeded,
                    utils.whenLoginFailed);
            } else {
                webpieces.showStatus("Please give me both a username and a password");
            }
        },

        logout: function() {
            server.logout(); 
            dojo.style('logged_in', 'display', 'none');
            dojo.style('not_logged_in', 'display', 'block');
        }    
    }); 
    
    dojo.mixin(bespin.user.register, {
        checkUsername: function() {
            var username_error = [];
            var username = dojo.byId("register_username").value;
            if (username.length < 4) {
                username_error.push("Usernames must be at least 4 characters long");   
            }
            if (/[<>| '"]/.test(username)) {
                username_error.push("Usernames must not contain any of: <>| '\"");   
            }
            dojo.byId('register_username_error').innerHTML = username_error.join(", ");
        },
        checkPassword: function() {
            dojo.byId('register_password_error').innerHTML = (dojo.byId('register_password').value.length < 6) ? "Passwords must be at least 6 characters long" : "";
        },
        checkConfirm: function() {
            dojo.byId('register_confirm_error').innerHTML = (dojo.byId('register_password').value != dojo.byId('register_confirm').value) ? "Passwords do not match" : "";
        },
        checkEmail: function() {
            dojo.byId('register_email_error').innerHTML = (!utils.validateEmail(dojo.byId('register_email').value)) ? "Invalid email address" : "";
        },
        showForm: function() {
            if (utils.showingBrowserCompatScreen()) return;
            dojo.style('logged_in', 'display', 'none');
            dojo.style('not_logged_in', 'display', 'none');
            dojo.style('overlay', 'display', 'block');
            dojo.style('centerpopup', 'display', 'block');            
            webpieces.showCenterPopup(dojo.byId('centerpopup'));  
        },
        hideForm: function() {
            webpieces.hideCenterPopup(dojo.byId('centerpopup'));
            server.currentuser(utils.whenAlreadyLoggedIn, utils.whenNotAlreadyLoggedIn);
        },
        send: function() {
            this.hideForm();
            server.signup(dojo.byId("register_username").value, 
                dojo.byId("register_password").value, 
                dojo.byId('register_email').value, 
                utils.whenLoginSucceeded, 
                utils.whenLoginFailed, 
                utils.whenUsernameInUse);
        },
        cancel: function() { 
            this.hideForm();
        }
    });
})();