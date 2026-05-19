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
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.component.a2a.model.SecurityScheme;
import org.apache.camel.support.OAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for HTTP bearer token authentication ({@code type=http, scheme=bearer}). On the producer side, resolves a
 * token via OAuth profile or static configuration. On the consumer side, validates the Bearer token via the camel-oauth
 * SPI when an {@code oauthProfile} is configured. Falls back to static comparison against {@code bearerToken} config
 * when no profile is set. Fails closed when neither is available.
 */
public class HttpBearerSchemeHandler implements A2ASecuritySchemeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpBearerSchemeHandler.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public String schemeType() {
        return "http";
    }

    @Override
    public void applyCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config, CamelContext context) {
        if (config.getOauthProfile() != null) {
            try {
                String token = OAuthHelper.resolveOAuthToken(context, config.getOauthProfile());
                exchange.getMessage().setHeader("Authorization", BEARER_PREFIX + token);
                return;
            } catch (Exception e) {
                LOG.debug("OAuth profile '{}' resolution failed, falling back to static bearer token: {}",
                        config.getOauthProfile(), e.getMessage());
            }
        }

        if (config.getBearerToken() != null) {
            exchange.getMessage().setHeader("Authorization", BEARER_PREFIX + config.getBearerToken());
        }
    }

    @Override
    public A2AUserProfile validateCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config) {
        String token = BearerTokenExtractor.extractBearerToken(exchange);
        return BearerTokenExtractor.validateBearerToken(exchange, token, config, "bearer");
    }
}
