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

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_USER_PROFILE;

/**
 * Authenticates the bearer token of the request, as sent in its {@code Authorization} header (RFC 6750 section 2.1).
 * <p>
 * Bearer authentication is per request: the authenticated {@link UserProfile} is set as the
 * {@link OAuth#CAMEL_OAUTH_USER_PROFILE} exchange property, and no {@link OAuthSession} is created or updated.
 */
public class OAuthBearerTokenProcessor extends AbstractOAuthProcessor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void process(Exchange exchange) {
        var context = exchange.getContext();
        var msg = exchange.getMessage();

        logRequestHeaders(procName, msg);

        // Validate Authorization header
        //
        var authHeaders = HttpRequestHeaders.values(msg, "Authorization");
        if (authHeaders.isEmpty()) {
            log.error("No Authorization header in request");
            rejectUnauthorized(exchange, "Authorization header");
            return;
        }

        if (authHeaders.size() > 1) {
            log.error("Invalid Authorization header: expected exactly one, got {}", authHeaders.size());
            rejectUnauthorized(exchange, "Invalid Authorization header");
            return;
        }

        var toks = authHeaders.get(0).split(" ");
        if (toks.length != 2 || !"Bearer".equals(toks[0])) {
            log.error("Invalid Authorization header");
            rejectUnauthorized(exchange, "Invalid Authorization header");
            return;
        }

        // Find or create the OAuth instance
        //
        var oauth = findOAuth(context).orElseGet(() -> {
            var factory = OAuthFactory.lookupFactory(context);
            return factory.createOAuth();
        });

        // Authenticate the bearer's access token
        //
        var access_token = toks[1];
        var userProfile = oauth.authenticate(new TokenCredentials(access_token));

        // The token authenticates this request only, so the profile goes on the exchange and not into an OAuthSession
        //
        exchange.setProperty(CAMEL_OAUTH_USER_PROFILE, userProfile);

        log.info("Authenticated {}", userProfile.subject());
        userProfile.logDetails();
    }
}
