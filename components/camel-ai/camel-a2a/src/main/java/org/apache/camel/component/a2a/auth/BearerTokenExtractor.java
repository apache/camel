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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.support.OAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared bearer token extraction and validation for all bearer-family A2A security scheme handlers (http/bearer,
 * oauth2, openIdConnect). Delegates to the camel-oauth SPI ({@link OAuthHelper#validateOAuthToken}) when an
 * {@code oauthProfile} is configured; falls back to static token comparison when a {@code bearerToken} is configured;
 * fails closed otherwise.
 */
final class BearerTokenExtractor {

    static final String OAUTH_TOKEN_VALIDATION_RESULT = "CamelOAuthTokenValidationResult";
    private static final Logger LOG = LoggerFactory.getLogger(BearerTokenExtractor.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenExtractor() {
    }

    static String extractBearerToken(Exchange exchange) {
        String authHeader = exchange.getMessage().getHeader("Authorization", String.class);
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new SecurityException("Missing or invalid Authorization header: expected Bearer token");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new SecurityException("Empty Bearer token");
        }
        return token;
    }

    static A2AUserProfile validateBearerToken(
            Exchange exchange, String token, A2AConfiguration config, String schemeName) {

        if (config.getOauthProfile() != null) {
            return validateViaOAuthProfile(exchange, token, config.getOauthProfile(), schemeName);
        }

        if (config.getBearerToken() != null) {
            return validateViaStaticToken(token, config.getBearerToken(), schemeName);
        }

        throw new SecurityException(
                "No oauthProfile or bearerToken configured for " + schemeName + " token validation — cannot validate");
    }

    private static A2AUserProfile validateViaOAuthProfile(
            Exchange exchange, String token, String oauthProfile, String schemeName) {
        OAuthTokenValidationResult result;
        try {
            result = OAuthHelper.validateOAuthToken(exchange.getContext(), oauthProfile, token);
        } catch (Exception e) {
            LOG.warn("OAuth token validation infrastructure failure for profile '{}': {}", oauthProfile, e.getMessage());
            LOG.debug("OAuth validation failure details", e);
            throw new SecurityException("Token validation service unavailable: " + e.getMessage());
        }

        if (!result.isValid()) {
            throw new SecurityException(
                    schemeName + " token validation failed: " + result.getError());
        }

        exchange.setProperty(OAUTH_TOKEN_VALIDATION_RESULT, result);

        return A2AUserProfile.fromOAuthResult(schemeName, result);
    }

    private static A2AUserProfile validateViaStaticToken(
            String token, String expectedToken, String schemeName) {
        if (!MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid " + schemeName + " token");
        }
        return A2AUserProfile.forScheme(schemeName);
    }
}
