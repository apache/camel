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

import java.util.Optional;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_REDIRECT_URI;
import static org.apache.camel.oauth.OAuthProperties.getRequiredProperty;

public class OAuthCodeFlowProcessor extends AbstractOAuthProcessor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void process(Exchange exchange) {
        var context = exchange.getContext();
        var msg = exchange.getMessage();

        logRequestHeaders(procName, msg);

        // Find or create the OAuth instance
        //
        var oauth = findOAuth(context).orElseGet(() -> {
            var factory = OAuthFactory.lookupFactory(context);
            return factory.createOAuth();
        });

        // Get or create the OAuthSession
        //
        var session = oauth.getSession(exchange)
                .or(() -> Optional.of(oauth.createSession(exchange)))
                .get();

        // Authenticate an existing UserProfile from the OAuthSession
        //
        if (session.getUserProfile().isPresent()) {
            authenticateExistingUserProfile(oauth, session);
        }

        // Fallback to the authorization code flow
        //
        if (session.getUserProfile().isEmpty()) {

            var postLoginUrl = msg.getHeader(Exchange.HTTP_URL, String.class);
            session.putValue("OAuthPostLoginUrl", postLoginUrl);

            var redirectUri = getRequiredProperty(exchange.getContext(), CAMEL_OAUTH_REDIRECT_URI);
            var params = new OAuthCodeFlowParams().setRedirectUri(redirectUri);
            var authRequestUrl = oauth.buildCodeFlowAuthRequestUrl(params);

            setSessionCookie(msg, session);
            sendRedirect(msg, authRequestUrl);
        }

        log.info("{} - Done", procName);
    }
}
