/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.oauth;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_REDIRECT_URI;
import static org.apache.camel.oauth.OAuthProperties.getRequiredProperty;

public class OAuthCodeFlowCallback extends AbstractOAuthProcessor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void process(Exchange exchange) {
        var context = exchange.getContext();
        var msg = exchange.getMessage();

        logRequestHeaders(procName, msg);

        // Validate auth callback request headers/parameters
        //
        var authCode = msg.getHeader("code", String.class);
        if (authCode == null) {
            log.error("Authorization code is missing in the request");
            msg.setHeader("CamelHttpResponseCode", 400);
            msg.setBody("Authorization code missing");
            return;
        }

        // Require an active OAuthSession
        //
        var oauth = findOAuthOrThrow(context);
        var session = oauth.getSession(exchange).orElseThrow();

        // Exchange the authorization code for access/refresh/id tokens
        //
        String redirectUri = getRequiredProperty(exchange.getContext(), CAMEL_OAUTH_REDIRECT_URI);
        var userProfile = oauth.authenticate(new AuthCodeCredentials()
                .setRedirectUri(redirectUri)
                .setCode(authCode));

        session.putUserProfile(userProfile);
        userProfile.logDetails("Authenticated");

        var postLoginUrl = (String) session.removeValue("OAuthPostLoginUrl").orElse(null);
        if (postLoginUrl == null) {
            postLoginUrl = getRequiredProperty(exchange.getContext(), CAMEL_OAUTH_REDIRECT_URI);
            var lastSlashIdx = postLoginUrl.lastIndexOf('/');
            postLoginUrl = postLoginUrl.substring(0, lastSlashIdx + 1);
        }

        setSessionCookie(msg, session);
        sendRedirect(msg, postLoginUrl);

        log.info("{} - Done", procName);
    }
}
