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
package org.apache.camel.http.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationConfigResolver;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.support.OAuthHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared support for HTTP consumers that validate incoming OAuth 2.0 Bearer tokens with a Camel OAuth profile.
 *
 * @since 4.21
 */
public final class OAuthHttpSecuritySupport {

    public static final String OAUTH_TOKEN_VALIDATION_RESULT = "CamelOAuthTokenValidationResult";

    private static final Logger LOG = LoggerFactory.getLogger(OAuthHttpSecuritySupport.class);
    static final String AUTHORIZATION = "Authorization";
    static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    static final String BEARER = "Bearer";
    static final int BAD_REQUEST = 400;
    static final int UNAUTHORIZED = 401;
    static final int SERVICE_UNAVAILABLE = 503;
    static final String BAD_REQUEST_BODY = "Bad Request";
    static final String UNAUTHORIZED_BODY = "Unauthorized";
    static final String SERVICE_UNAVAILABLE_BODY = "Service Unavailable";
    private static final int MAX_BEARER_TOKEN_LENGTH = 8192;
    private static final int MAX_BEARER_TOKEN_LEADING_SPACES = 16;
    private static final int MAX_AUTHORIZATION_HEADER_LENGTH
            = BEARER.length() + MAX_BEARER_TOKEN_LEADING_SPACES + MAX_BEARER_TOKEN_LENGTH;
    private static final String INVALID_REQUEST_CHALLENGE = BEARER + " error=\"invalid_request\"";
    private static final String INVALID_TOKEN_CHALLENGE = BEARER + " error=\"invalid_token\"";

    private static final long INFRASTRUCTURE_WARN_INTERVAL_MILLIS = 30_000L;

    private final String oauthProfile;
    private final OAuthTokenValidationFactory validationFactory;
    private final OAuthTokenValidationConfig validationConfig;
    private final AtomicLong lastInfrastructureWarnMillis = new AtomicLong();

    /**
     * Creates support that resolves the validation factory lazily through {@link OAuthHelper}.
     *
     * @param oauthProfile the OAuth profile name
     */
    public OAuthHttpSecuritySupport(String oauthProfile) {
        this(oauthProfile, null, null);
    }

    /**
     * Creates support with a pre-resolved validation factory.
     *
     * @param oauthProfile      the OAuth profile name
     * @param validationFactory the validation factory, or null to resolve through {@link OAuthHelper}
     */
    public OAuthHttpSecuritySupport(String oauthProfile, OAuthTokenValidationFactory validationFactory) {
        this(oauthProfile, validationFactory, null);
    }

    private OAuthHttpSecuritySupport(
                                     String oauthProfile, OAuthTokenValidationFactory validationFactory,
                                     OAuthTokenValidationConfig validationConfig) {
        this.oauthProfile = Objects.requireNonNull(oauthProfile, "oauthProfile");
        this.validationFactory = validationFactory;
        this.validationConfig = validationConfig;
    }

    /**
     * Creates support for the given profile and validates static profile configuration eagerly.
     *
     * @param  camelContext     the Camel context used to resolve the validation factory
     * @param  oauthProfile     the OAuth profile name; null or empty disables OAuth support
     * @return                  a support instance, or null when no profile is configured
     * @throws RuntimeException if the validation factory cannot be resolved or profile configuration is invalid
     */
    public static OAuthHttpSecuritySupport create(CamelContext camelContext, String oauthProfile) {
        if (ObjectHelper.isEmpty(oauthProfile)) {
            return null;
        }

        OAuthTokenValidationFactory factory = OAuthHelper.resolveOAuthTokenValidationFactory(camelContext, oauthProfile);
        factory.validateConfiguration(camelContext, oauthProfile);
        OAuthTokenValidationConfig config = OAuthTokenValidationConfigResolver.resolveProfileConfig(camelContext, oauthProfile);
        return new OAuthHttpSecuritySupport(oauthProfile, factory, config);
    }

    /**
     * Gets the OAuth profile name.
     *
     * @return the configured OAuth profile name
     */
    public String getOauthProfile() {
        return oauthProfile;
    }

    /**
     * Validates the current exchange message as an HTTP request and applies the authentication result or rejection
     * response to the exchange.
     *
     * @param  exchange the exchange to authenticate
     * @return          true when the request is authenticated, false when the request was rejected
     */
    public boolean authenticate(Exchange exchange) {
        Validation validation = validate(exchange.getContext(), authorizationHeaderValues(exchange.getMessage()));
        removeAuthorizationHeader(exchange.getMessage());
        if (validation.isAuthenticated()) {
            exchange.setProperty(OAUTH_TOKEN_VALIDATION_RESULT, validation.getValidationResult());
            return true;
        }

        reject(exchange, validation);
        return false;
    }

    /**
     * Validates an HTTP {@code Authorization} header value.
     *
     * @param  camelContext  the Camel context
     * @param  authorization the raw {@code Authorization} header value, or null
     * @return               validation state containing either a token validation result or an HTTP rejection status
     */
    public Validation validate(CamelContext camelContext, String authorization) {
        return validate(camelContext, authorization == null ? List.of() : List.of(authorization));
    }

    /**
     * Validates one or more HTTP {@code Authorization} header values.
     *
     * @param  camelContext   the Camel context
     * @param  authorizations the raw {@code Authorization} header values
     * @return                validation state containing either a token validation result or an HTTP rejection status
     */
    public Validation validate(CamelContext camelContext, Collection<String> authorizations) {
        BearerToken bearerToken = extractBearerToken(authorizations);
        if (bearerToken.isMissing()) {
            return Validation.unauthorized();
        }
        if (bearerToken.isMalformed()) {
            return Validation.invalidRequest();
        }

        OAuthTokenValidationResult result;
        try {
            result = validationFactory != null
                    ? validateToken(camelContext, bearerToken.token())
                    : OAuthHelper.validateOAuthToken(camelContext, oauthProfile, bearerToken.token());
            result = Objects.requireNonNull(result, "validationResult");
        } catch (Exception e) {
            logInfrastructureFailure(e);
            return Validation.serviceUnavailable();
        }

        if (!result.isValid()) {
            LOG.debug("OAuth token rejected: {}", result.getError());
            return Validation.invalidToken();
        }

        return Validation.authenticated(result);
    }

    private OAuthTokenValidationResult validateToken(CamelContext camelContext, String token) {
        if (validationConfig != null) {
            return validationFactory.validateToken(validationConfig, token);
        }
        return validationFactory.validateToken(camelContext, oauthProfile, token);
    }

    /**
     * Logs validator infrastructure failures at WARN, rate-limited per support instance, so operators can tell an
     * identity-provider outage apart from other 503 sources without per-request log noise.
     */
    private void logInfrastructureFailure(Exception e) {
        long now = System.currentTimeMillis();
        long last = lastInfrastructureWarnMillis.get();
        if (now - last >= INFRASTRUCTURE_WARN_INTERVAL_MILLIS && lastInfrastructureWarnMillis.compareAndSet(last, now)) {
            LOG.warn("OAuth token validation for profile {} failed due to infrastructure error; "
                     + "requests are rejected with HTTP 503: {}",
                    oauthProfile, e.toString());
            LOG.debug("OAuth token validation infrastructure failure details", e);
        } else {
            LOG.debug("OAuth token validation failed due to infrastructure error", e);
        }
    }

    /**
     * Stores an authenticated token validation result on the exchange.
     *
     * @param exchange the exchange to update
     * @param result   the authenticated token validation result
     */
    public static void applyAuthenticatedResult(Exchange exchange, OAuthTokenValidationResult result) {
        exchange.setProperty(OAUTH_TOKEN_VALIDATION_RESULT, result);
    }

    /**
     * Removes all Camel message headers named {@code Authorization}, ignoring case.
     *
     * @param message the message to update
     */
    public static void removeAuthorizationHeader(Message message) {
        List<String> names = new ArrayList<>();
        for (String name : message.getHeaders().keySet()) {
            if (AUTHORIZATION.equalsIgnoreCase(name)) {
                names.add(name);
            }
        }
        for (String name : names) {
            message.removeHeader(name);
        }
    }

    /**
     * Applies a rejection response and stops route processing.
     *
     * @param exchange   the exchange to reject
     * @param validation the failed validation state
     */
    public static void reject(Exchange exchange, Validation validation) {
        reject(exchange, validation.getRejectionStatusCode(), validation.getWwwAuthenticate(), validation.getResponseBody());
    }

    private static void reject(Exchange exchange, int statusCode, String wwwAuthenticate, String responseBody) {
        removeAuthorizationHeader(exchange.getMessage());
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
        if (wwwAuthenticate != null) {
            exchange.getMessage().setHeader(WWW_AUTHENTICATE, wwwAuthenticate);
        }
        exchange.getMessage().setBody(responseBody);
        exchange.setRouteStop(true);
    }

    /**
     * Extracts an RFC 6750 Bearer token from an {@code Authorization} header.
     *
     * @param  authorization the raw {@code Authorization} header value, or null
     * @return               the token value, or null when the header is missing or malformed
     */
    static String extractBearerToken(String authorization) {
        BearerToken bearerToken = extractBearerToken(authorization == null ? List.of() : List.of(authorization));
        return bearerToken.authenticated() ? bearerToken.token() : null;
    }

    private static BearerToken extractBearerToken(Collection<String> authorizations) {
        if (authorizations == null || authorizations.isEmpty()) {
            return BearerToken.missingToken();
        }
        if (authorizations.size() != 1) {
            return BearerToken.malformedToken();
        }
        String authorization = authorizations.iterator().next();
        if (authorization == null) {
            return BearerToken.missingToken();
        }
        if (authorization.length() > MAX_AUTHORIZATION_HEADER_LENGTH) {
            return BearerToken.malformedToken();
        }
        if (authorization.length() <= BEARER.length()
                || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return BearerToken.malformedToken();
        }
        int tokenStart = BEARER.length();
        if (authorization.charAt(tokenStart) != ' ') {
            return BearerToken.malformedToken();
        }
        int leadingSpaces = 0;
        while (tokenStart < authorization.length() && authorization.charAt(tokenStart) == ' ') {
            tokenStart++;
            leadingSpaces++;
            if (leadingSpaces > MAX_BEARER_TOKEN_LEADING_SPACES) {
                return BearerToken.malformedToken();
            }
        }
        if (tokenStart == authorization.length()) {
            return BearerToken.malformedToken();
        }
        if (authorization.length() - tokenStart > MAX_BEARER_TOKEN_LENGTH) {
            return BearerToken.malformedToken();
        }

        String token = authorization.substring(tokenStart);
        if (!isValidBearerToken(token)) {
            return BearerToken.malformedToken();
        }
        return BearerToken.authenticated(token);
    }

    private static Collection<String> authorizationHeaderValues(Message message) {
        List<String> values = new ArrayList<>();
        for (String name : message.getHeaders().keySet()) {
            if (AUTHORIZATION.equalsIgnoreCase(name)) {
                addHeaderValue(values, message.getHeader(name));
            }
        }
        return values;
    }

    private static void addHeaderValue(List<String> values, Object value) {
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.add(item == null ? null : item.toString());
            }
        } else if (value != null) {
            values.add(value.toString());
        }
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

    private static final class BearerToken {

        private final String token;
        private final boolean missing;
        private final boolean malformed;

        private BearerToken(String token, boolean missing, boolean malformed) {
            this.token = token;
            this.missing = missing;
            this.malformed = malformed;
        }

        static BearerToken authenticated(String token) {
            return new BearerToken(token, false, false);
        }

        static BearerToken missingToken() {
            return new BearerToken(null, true, false);
        }

        static BearerToken malformedToken() {
            return new BearerToken(null, false, true);
        }

        boolean authenticated() {
            return token != null;
        }

        String token() {
            return token;
        }

        boolean isMissing() {
            return missing;
        }

        boolean isMalformed() {
            return malformed;
        }
    }

    /**
     * OAuth validation state for an incoming HTTP request.
     *
     * @since 4.21
     */
    public static final class Validation {

        private final Integer rejectionStatusCode;
        private final String wwwAuthenticate;
        private final String responseBody;
        private final OAuthTokenValidationResult validationResult;

        private Validation(
                           Integer rejectionStatusCode, String wwwAuthenticate, String responseBody,
                           OAuthTokenValidationResult validationResult) {
            this.rejectionStatusCode = rejectionStatusCode;
            this.wwwAuthenticate = wwwAuthenticate;
            this.responseBody = responseBody;
            this.validationResult = validationResult;
        }

        /**
         * Creates authenticated validation state.
         */
        public static Validation authenticated(OAuthTokenValidationResult validationResult) {
            return new Validation(null, null, null, Objects.requireNonNull(validationResult, "validationResult"));
        }

        /**
         * Creates bad-request validation state for malformed OAuth request syntax.
         */
        public static Validation invalidRequest() {
            return new Validation(BAD_REQUEST, INVALID_REQUEST_CHALLENGE, BAD_REQUEST_BODY, null);
        }

        /**
         * Creates unauthorized validation state.
         */
        public static Validation unauthorized() {
            return new Validation(UNAUTHORIZED, BEARER, UNAUTHORIZED_BODY, null);
        }

        /**
         * Creates unauthorized validation state for invalid tokens.
         */
        public static Validation invalidToken() {
            return new Validation(UNAUTHORIZED, INVALID_TOKEN_CHALLENGE, UNAUTHORIZED_BODY, null);
        }

        /**
         * Creates service-unavailable validation state for validation infrastructure failures.
         */
        public static Validation serviceUnavailable() {
            return new Validation(SERVICE_UNAVAILABLE, null, SERVICE_UNAVAILABLE_BODY, null);
        }

        /**
         * Whether validation succeeded.
         */
        public boolean isAuthenticated() {
            return validationResult != null;
        }

        /**
         * Gets the HTTP rejection status code.
         *
         * @throws IllegalStateException when {@link #isAuthenticated()} is true
         */
        public int getRejectionStatusCode() {
            if (rejectionStatusCode == null) {
                throw new IllegalStateException("Authenticated validation does not have an HTTP rejection status code");
            }
            return rejectionStatusCode;
        }

        /**
         * Gets the {@code WWW-Authenticate} challenge value, or null when none should be sent.
         */
        public String getWwwAuthenticate() {
            return wwwAuthenticate;
        }

        /**
         * Gets the rejection response body, or null for authenticated requests.
         */
        public String getResponseBody() {
            return responseBody;
        }

        /**
         * Gets the token validation result for authenticated requests, or null for rejected requests.
         */
        public OAuthTokenValidationResult getValidationResult() {
            return validationResult;
        }
    }
}
