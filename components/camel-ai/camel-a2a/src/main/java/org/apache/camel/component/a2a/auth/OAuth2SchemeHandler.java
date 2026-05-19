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
package org.apache.camel.component.a2a.auth;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.component.a2a.model.SecurityScheme;
import org.apache.camel.support.OAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for OAuth 2.0 authentication ({@code type=oauth2}). On the producer side, acquires a token via the
 * {@code camel-oauth} SPI using the configured OAuth profile. On the consumer side, validates the Bearer token via
 * {@link OAuthHelper#validateOAuthToken} for full JWT/introspection-based validation.
 */
public class OAuth2SchemeHandler implements A2ASecuritySchemeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2SchemeHandler.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public String schemeType() {
        return "oauth2";
    }

    @Override
    public void applyCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config, CamelContext context) {
        if (config.getOauthProfile() == null) {
            LOG.warn("OAuth2 scheme selected but no oauthProfile configured");
            return;
        }
        try {
            String token = OAuthHelper.resolveOAuthToken(context, config.getOauthProfile());
            exchange.getMessage().setHeader("Authorization", BEARER_PREFIX + token);
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Failed to acquire OAuth2 token for profile '"
                                            + config.getOauthProfile() + "': " + e.getMessage(),
                    e);
        }
    }

    @Override
    public A2AUserProfile validateCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config) {
        String token = BearerTokenExtractor.extractBearerToken(exchange);
        return BearerTokenExtractor.validateBearerToken(exchange, token, config, "oauth2");
    }
}
