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
package org.apache.camel.component.platform.http.spi;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.support.OAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform HTTP security handler that validates incoming OAuth 2.0 Bearer tokens with a Camel OAuth profile.
 *
 * @since 4.21
 */
public final class OAuthPlatformHttpSecurityHandler implements PlatformHttpSecurityHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthPlatformHttpSecurityHandler.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String BEARER = "Bearer";
    private static final int MAX_BEARER_TOKEN_LENGTH = 8192;

    private final String oauthProfile;
    private final OAuthTokenValidationFactory validationFactory;

    public OAuthPlatformHttpSecurityHandler(String oauthProfile) {
        this(oauthProfile, null);
    }

    public OAuthPlatformHttpSecurityHandler(String oauthProfile, OAuthTokenValidationFactory validationFactory) {
        this.oauthProfile = oauthProfile;
        this.validationFactory = validationFactory;
    }

    /**
     * Gets the OAuth profile used to validate incoming Bearer tokens.
     */
    public String getOauthProfile() {
        return oauthProfile;
    }

    @Override
    public Processor wrapProcessor(PlatformHttpEndpoint endpoint, Processor processor) {
        return exchange -> {
            if (authenticate(endpoint, exchange)) {
                processor.process(exchange);
            }
        };
    }

    @Override
    public boolean authenticate(PlatformHttpEndpoint endpoint, Exchange exchange) {
        String token = extractBearerToken(exchange.getMessage().getHeader(AUTHORIZATION, String.class));
        exchange.getMessage().removeHeader(AUTHORIZATION);
        if (token == null) {
            reject(exchange, 401);
            return false;
        }

        OAuthTokenValidationResult result;
        try {
            result = validationFactory != null
                    ? validationFactory.validateToken(exchange.getContext(), oauthProfile, token)
                    : OAuthHelper.validateOAuthToken(exchange.getContext(), oauthProfile, token);
        } catch (Exception e) {
            LOG.debug("OAuth token validation failed due to infrastructure error", e);
            reject(exchange, 503);
            return false;
        }

        if (!result.isValid()) {
            LOG.debug("OAuth token rejected: {}", result.getError());
            reject(exchange, 401);
            return false;
        }

        exchange.setProperty(PlatformHttpConstants.OAUTH_TOKEN_VALIDATION_RESULT, result);
        return true;
    }

    private static String extractBearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        if (authorization.length() <= BEARER.length()
                || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        int tokenStart = BEARER.length();
        if (authorization.charAt(tokenStart) != ' ') {
            return null;
        }
        while (tokenStart < authorization.length() && authorization.charAt(tokenStart) == ' ') {
            tokenStart++;
        }
        if (tokenStart == authorization.length()) {
            return null;
        }

        String token = authorization.substring(tokenStart);
        if (token.length() > MAX_BEARER_TOKEN_LENGTH || !isValidBearerToken(token)) {
            return null;
        }
        return token;
    }

    private static boolean isValidBearerToken(String token) {
        if (token.charAt(0) == '=') {
            return false;
        }
        boolean padding = false;
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch == '=') {
                padding = true;
                continue;
            }
            if (padding || !isBearerTokenCharacter(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBearerTokenCharacter(char ch) {
        return ch >= 'A' && ch <= 'Z'
                || ch >= 'a' && ch <= 'z'
                || ch >= '0' && ch <= '9'
                || ch == '-'
                || ch == '.'
                || ch == '_'
                || ch == '~'
                || ch == '+'
                || ch == '/';
    }

    private static void reject(Exchange exchange, int statusCode) {
        exchange.getMessage().removeHeader(AUTHORIZATION);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        if (statusCode == 401) {
            exchange.getMessage().setHeader(WWW_AUTHENTICATE, BEARER);
        }
        exchange.getMessage().setBody(statusCode == 503 ? "Service Unavailable" : "Unauthorized");
        exchange.setRouteStop(true);
    }
}
