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

public class OAuthBearerTokenProcessor extends AbstractOAuthProcessor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void process(Exchange exchange) {
        var context = exchange.getContext();
        var msg = exchange.getMessage();

        logRequestHeaders(procName, msg);

        // Validate Authorization header
        //
        var authHeader = msg.getHeader("Authorization", String.class);
        if (authHeader == null) {
            log.error("No Authorization header in request");
            msg.setHeader("CamelHttpResponseCode", 400);
            msg.setBody("Authorization header");
            return;
        }

        var toks = authHeader.split(" ");
        if (toks.length != 2 || !"Bearer".equals(toks[0])) {
            log.error("Invalid Authorization header: {}", authHeader);
            msg.setHeader("CamelHttpResponseCode", 400);
            msg.setBody("Invalid Authorization header");
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

        // Get or create the OAuthSession
        //
        var session = oauth.getOrCreateSession(exchange);
        session.putUserProfile(userProfile);

        log.info("Authenticated {}", userProfile.subject());
        userProfile.logDetails();
    }
}
