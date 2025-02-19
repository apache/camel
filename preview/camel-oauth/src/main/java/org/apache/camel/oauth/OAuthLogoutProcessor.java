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

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_LOGOUT_REDIRECT_URI;
import static org.apache.camel.oauth.OAuthProperties.getProperty;

public class OAuthLogoutProcessor extends AbstractOAuthProcessor {

    @Override
    public void process(Exchange exchange) {
        var context = exchange.getContext();

        findOAuth(context).ifPresent(oauth -> {

            var maybeSession = oauth.getSession(exchange);
            maybeSession.flatMap(OAuthSession::getUserProfile).ifPresent(user -> {

                maybeSession.get().removeUserProfile();

                var postLogoutUrl = getProperty(exchange, CAMEL_OAUTH_LOGOUT_REDIRECT_URI)
                        .orElse(null);

                var params = new OAuthLogoutParams()
                        .setRedirectUri(postLogoutUrl)
                        .setUser(user);

                var logoutUrl = oauth.buildLogoutRequestUrl(params);

                log.info("OAuth logout: {}", logoutUrl);
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 302);
                exchange.getMessage().setHeader("Location", logoutUrl);
                exchange.getMessage().setBody("");
            });
        });
    }
}
